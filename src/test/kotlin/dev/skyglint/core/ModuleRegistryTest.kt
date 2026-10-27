package dev.skyglint.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModuleRegistryTest {
    @Test fun `a contributor registers a detector without changing controller dispatch`() {
        val controller = CoreController()
        controller.modules.register(object : SignalModule {
            override val id = "example.menu_snapshot"
            override fun parse(signal: NormalizedSignal): List<CoreEvent> =
                if (signal is NormalizedSignal.MenuSnapshot && signal.title == "SkyBlock Menu") {
                    listOf(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(200, ProgressSnapshot(25.0, 100.0, .25))))
                } else emptyList()
        })
        controller.enterSkyBlock("test-session", 0)
        val events = controller.ingest(NormalizedSignal.MenuSnapshot("SkyBlock Menu", emptyList()), 1)
        assertEquals(1, events.size)
        assertEquals(25.0, assertIs<ProgressionView.SkyBlock>(controller.snapshot(1).progression).progress!!.current)
    }

    @Test fun `one failed detector reports once while later detectors still execute in order`() {
        val failures = mutableListOf<ModuleFailure>()
        val order = mutableListOf<String>()
        val registry = ModuleRegistry { failures += it }
        registry.register(object : SignalModule {
            override val id = "first"
            override fun parse(signal: NormalizedSignal): List<CoreEvent> {
                order += id
                throw IllegalArgumentException("bad fixture")
            }
        })
        registry.register(object : SignalModule {
            override val id = "second"
            override fun parse(signal: NormalizedSignal): List<CoreEvent> {
                order += id
                return listOf(CoreEvent.PetObserved(null))
            }
        })
        assertEquals(listOf(CoreEvent.PetObserved(null)), registry.process(NormalizedSignal.Chat("input")))
        assertEquals(listOf("first", "second"), order)
        assertEquals("first", failures.single().moduleId)
        assertIs<IllegalArgumentException>(failures.single().cause)
    }

    @Test fun `duplicate and blank module ids are rejected immediately`() {
        val module = object : SignalModule {
            override val id = "example"
            override fun parse(signal: NormalizedSignal) = emptyList<CoreEvent>()
        }
        val registry = ModuleRegistry().register(module)
        assertFailsWith<IllegalArgumentException> { registry.register(module) }
        assertEquals(listOf("example"), registry.ids)
        assertFailsWith<IllegalArgumentException> {
            registry.register(object : SignalModule {
                override val id = " "
                override fun parse(signal: NormalizedSignal) = emptyList<CoreEvent>()
            })
        }
    }

    @Test fun `tab signals own an immutable copy of input lines`() {
        val callerLines = mutableListOf("Pet:", "[Lvl 100] Flying Fish")
        val signal = NormalizedSignal.TabSnapshot(callerLines)
        callerLines.clear()
        assertEquals(2, signal.lines.size)
        assertFailsWith<UnsupportedOperationException> { (signal.lines as MutableList<String>).clear() }
    }

    @Test fun `registry does not conceal JVM errors as a detector failure`() {
        val registry = ModuleRegistry().register(object : SignalModule {
            override val id = "fatal"
            override fun parse(signal: NormalizedSignal): List<CoreEvent> = throw AssertionError("fatal fixture")
        })
        assertFailsWith<AssertionError> { registry.process(NormalizedSignal.Chat("input")) }
    }

    @Test fun `default modules preserve all public parser behavior without duplicate emissions`() {
        val events = ModuleRegistry.defaults().process(NormalizedSignal.ActionBar("+1 Mining (50%)   +10 SkyBlock XP (Bestiary) (25/100)"))
        assertEquals(2, events.size)
        assertIs<CoreEvent.SkillGain>(events[0])
        assertIs<CoreEvent.SkyBlockGain>(events[1])
        assertTrue(ModuleRegistry.defaults().process(NormalizedSignal.Chat("ordinary chat")).isEmpty())
    }

    @Test fun `contributor widget can reach HUD without adding a reducer event branch`() {
        val controller = CoreController()
        controller.modules.register(object : SignalModule {
            override val id = "example.widget"
            override fun parse(signal: NormalizedSignal) = if (signal is NormalizedSignal.Chat && signal.text == "fixture") {
                listOf(CoreEvent.WidgetObserved(ModuleWidget("example.status", "Fixture status", "Observed", priority = 5)))
            } else emptyList()
        })
        controller.enterSkyBlock("session", 0)
        controller.onChat("fixture", 1)
        assertEquals("Fixture status", controller.snapshot(1).widgets.single().title)
    }

    @Test fun `widgets expire exactly at deadline and explicit removal affects one id`() {
        val controller = CoreController()
        controller.enterSkyBlock("session", 0)
        controller.apply(listOf(
            CoreEvent.WidgetObserved(ModuleWidget("example.expiring", "Expires", expiresAt = 100)),
            CoreEvent.WidgetObserved(ModuleWidget("example.persistent", "Remains")),
        ), 1)
        assertEquals(2, controller.snapshot(99).widgets.size)
        assertEquals(listOf("example.persistent"), controller.snapshot(100).widgets.map { it.id })
        controller.apply(listOf(CoreEvent.WidgetRemoved("example.persistent")), 101)
        assertTrue(controller.snapshot(101).widgets.isEmpty())
        assertTrue(controller.apply(listOf(CoreEvent.WidgetObserved(ModuleWidget("example.old", "Old", expiresAt = 100))), 102).isEmpty())
    }

    @Test fun `widget priorities are descending with stable ties and updates by id`() {
        val controller = CoreController()
        controller.enterSkyBlock("session", 0)
        controller.apply(listOf(
            CoreEvent.WidgetObserved(ModuleWidget("example.first", "First", priority = 1)),
            CoreEvent.WidgetObserved(ModuleWidget("example.urgent", "Urgent", priority = 10)),
            CoreEvent.WidgetObserved(ModuleWidget("example.second", "Second", priority = 1)),
        ), 1)
        assertEquals(listOf("example.urgent", "example.first", "example.second"), controller.snapshot(1).widgets.map { it.id })
        controller.apply(listOf(CoreEvent.WidgetObserved(ModuleWidget("example.first", "Updated", priority = 1))), 2)
        assertEquals(listOf("Urgent", "Updated", "Second"), controller.snapshot(2).widgets.map { it.title })
        controller.enterSkyBlock("next-session", 3)
        assertTrue(controller.snapshot(3).widgets.isEmpty())
    }

    @Test fun `stateful contributor hooks follow actual sessions and reset exactly once`() {
        val calls = mutableListOf<String>()
        val controller = CoreController()
        controller.modules.register(object : SignalModule {
            override val id = "example.stateful"
            override fun parse(signal: NormalizedSignal) = emptyList<CoreEvent>()
            override fun onSessionStarted(sessionId: String) { calls += "start:$sessionId" }
            override fun onSessionEnded() { calls += "end" }
        })
        controller.leaveSkyBlock()
        assertTrue(calls.isEmpty())
        controller.enterSkyBlock("a", 1)
        controller.enterSkyBlock("a", 2)
        controller.enterSkyBlock("b", 3)
        controller.leaveSkyBlock()
        controller.leaveSkyBlock()
        assertEquals(listOf("start:a", "end", "start:b", "end"), calls)
    }

    @Test fun `failed lifecycle hook reports failure while other modules still receive lifecycle`() {
        val failures = mutableListOf<ModuleFailure>()
        val calls = mutableListOf<String>()
        val registry = ModuleRegistry { failures += it }
        registry.register(object : SignalModule {
            override val id = "example.broken_hook"
            override fun parse(signal: NormalizedSignal) = emptyList<CoreEvent>()
            override fun onSessionStarted(sessionId: String) { throw IllegalStateException("start fixture") }
            override fun onSessionEnded() { throw IllegalArgumentException("end fixture") }
        })
        registry.sessionStarted("a")
        // A module registered during a live session receives that session immediately.
        registry.register(object : SignalModule {
            override val id = "example.good_hook"
            override fun parse(signal: NormalizedSignal) = emptyList<CoreEvent>()
            override fun onSessionStarted(sessionId: String) { calls += sessionId }
            override fun onSessionEnded() { calls += "end" }
        })
        registry.sessionEnded()
        assertEquals(listOf("a", "end"), calls)
        assertEquals(listOf("SessionStarted", "SessionEnded"), failures.map { it.signalType })
        assertEquals(listOf("example.broken_hook", "example.broken_hook"), failures.map { it.moduleId })
    }
}
