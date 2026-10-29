package dev.skyglint.features.mining

import dev.skyglint.core.*
import java.util.UUID
import kotlin.test.*

class MiningEventModuleTest {
    private var time = 0L
    private val module = MiningEventModule { time }
    private val powderId = UUID(0, 1)
    private val unknownId = UUID(0, 2)
    private fun context(mode: String? = "mining_3", location: String? = null) = module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun boss(id: UUID = powderId, title: String = "PASSIVE EVENT 2X POWDER RUNNING FOR 02:14") = BossBarState(id, title, .5f)
    private fun bosses(vararg bars: BossBarState) = module.parse(NormalizedSignal.BossSnapshot(bars.toList()))
    private fun tab(vararg lines: String) = module.parse(NormalizedSignal.TabSnapshot(lines.toList()))
    private fun List<CoreEvent>.widget(): ModuleWidget? = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget
    private fun ModuleWidget.value(id: String = "event:double_powder") = presentation!!.metrics.single { it.id == id }.value

    @Test fun `boss counter claims only exact represented UUID and full snapshot remove releases immediately`() {
        context()
        val observed = bosses(boss(), boss(unknownId, "Unrecognized future mining event")).widget()!!
        assertEquals(setOf(powderId), observed.replacedBossIds)
        assertEquals("×2 · 2:14", observed.value())
        assertEquals(WidgetRole.COUNTERS, observed.presentation?.role)
        assertTrue(bosses(boss(unknownId, "Unrecognized future mining event")).all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `identical boss replay refreshes source lease but never pushes its deadline forward`() {
        context()
        bosses(boss())
        time = 3_000
        assertEquals("×2 · 2:11", bosses(boss()).widget()!!.value())
        time = 6_000
        context()
        assertEquals("×2 · 2:08", bosses(boss()).widget()!!.value())
        time = 134_000
        context()
        val awaitingZero = bosses(boss()).widget()!!.presentation!!.metrics.single()
        assertEquals("0:00", awaitingZero.countdown!!.caption(time))
        assertEquals("Double Powder", awaitingZero.label)
        time += 5_000
        context()
        assertTrue(bosses(boss()).all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `changed actual timer anchors new observed remaining time without delta accounting`() {
        context(); bosses(boss())
        time = 2_000
        assertEquals("×2 · 2:10", bosses(boss(title = "PASSIVE EVENT 2X POWDER RUNNING FOR 02:10")).widget()!!.value())
    }
    @Test fun `boss source wins same event tab source but independent second event remains represented`() {
        context(); bosses(boss())
        time = 1_000
        val same = tab("Mining Event: 2x Powder", "Ends in: 15m").widget()!!
        assertEquals("×2 · 2:13", same.value())
        val second = tab("Mining Event: Better Together", "Ends in: 4m").widget()!!
        assertEquals(2, second.presentation!!.metrics.size)
        assertEquals(setOf(powderId), second.replacedBossIds)
        assertEquals("Together · 4:00", second.value("event:better_together"))
        assertEquals(1, tab("Mining Event: Not announced").widget()!!.presentation!!.metrics.size)
    }
    @Test fun `tab only countdown is bounded stale and never claims a UUID`() {
        context()
        val first = tab("Mining Event: 2x Powder", "Ends in: 5m 30s").widget()!!
        assertTrue(first.replacedBossIds.isEmpty())
        time = 1_000
        assertEquals("×2 · 5:29", tab("Mining Event: 2x Powder", "Ends in: 5m 30s").widget()!!.value())
        time = 6_000
        assertTrue(context().all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `mode gate excludes mining two and hub while known sidebar location fallback works`() {
        context("mining_2")
        assertNull(bosses(boss()).widget())
        context("hub", "Dwarven Mines")
        assertNull(bosses(boss()).widget())
        context(null, "Dwarven Mines")
        assertNotNull(bosses(boss()).widget())
        assertTrue(context("hub").all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `end chat prevents an unchanged stale boss replay from resurrecting the event`() {
        context(); bosses(boss())
        time = 1_000
        val ending = module.parse(NormalizedSignal.Chat("2X POWDER ENDED!")).widget()!!.presentation!!.metrics.single()
        assertEquals("Double Powder ended", ending.label)
        time = 1_001
        assertNull(bosses(boss()).widget()!!.presentation!!.metrics.single().countdown)
        assertNotNull(bosses(boss(title = "PASSIVE EVENT 2X POWDER RUNNING FOR 15:00")).widget())
        module.onSessionEnded()
        assertNull(bosses(boss()).widget())
    }
    @Test fun `end chat also suppresses an identical stale tab replay until the server changes it`() {
        context()
        tab("Mining Event: 2x Powder", "Ends in: 5m 30s")
        module.parse(NormalizedSignal.Chat("2X POWDER ENDED!"))
        assertEquals("Double Powder ended", tab("Mining Event: 2x Powder", "Ends in: 5m 30s")
            .widget()!!.presentation!!.metrics.single().label)
        assertNotNull(tab("Mining Event: 2x Powder", "Ends in: 15m").widget())
    }
    @Test fun `two known UUID bars of the same type share one metric while both IDs are preserved as claims`() {
        context()
        val widget = bosses(boss(), boss(unknownId, "PASSIVE EVENT 2X POWDER RUNNING FOR 01:14")).widget()!!
        assertEquals(1, widget.presentation!!.metrics.size)
        assertEquals(setOf(powderId, unknownId), widget.replacedBossIds)
        assertEquals("×2 · 1:14", widget.value())
    }
    @Test fun `normal second updates keep one monotonic timer interval despite packet arrival jitter`() {
        context()
        val first = bosses(boss()).widget()!!.presentation!!.metrics.single().countdown!!
        for (second in 1..20) {
            time = second * 1_000L + if (second % 2 == 0) 220 else 50
            context()
            val left = 134 - second
            val title = "PASSIVE EVENT 2X POWDER RUNNING FOR ${left / 60}:${(left % 60).toString().padStart(2, '0')}"
            val metric = bosses(boss(title = title)).widget()!!.presentation!!.metrics.single()
            assertEquals(first.deadline, metric.countdown!!.deadline)
            assertEquals(first.intervalMillis, metric.countdown!!.intervalMillis)
            assertEquals(setOf(powderId), metric.replacedBossIds)
        }
    }
    @Test fun `island mode switch clears old event sources even without a new boss packet`() {
        context(); bosses(boss())
        time = 1_000
        assertNull(context("crystal_hollows").widget())
        assertNotNull(bosses(boss()).widget())
    }

    @Test fun `Crystal Nucleus objective multiplex keeps local clock without claiming objective or restarting intro`() {
        context("crystal_hollows", "Crystal Nucleus")
        val initial = bosses(boss(title = "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 03:46"))
        val deadline = initial.widget()!!.presentation!!.metrics.single().countdown!!
        assertEquals(1, initial.filterIsInstance<CoreEvent.NoticeObserved>().size)
        for (second in 1..20) {
            time = second * 1_000L
            context("crystal_hollows", "Crystal Nucleus")
            val gap = bosses(boss(title = "Objective: Talk to the Goblin King"))
            val retained = gap.widget()!!.presentation!!.metrics.single()
            assertEquals(deadline, retained.countdown)
            assertTrue(retained.replacedBossIds.isEmpty())
            assertTrue(gap.filterIsInstance<CoreEvent.NoticeObserved>().isEmpty())
            time += 100
            val left = 226 - second
            val refreshed = bosses(boss(title = "PASSIVE EVENT BETTER TOGETHER RUNNING FOR ${left / 60}:${(left % 60).toString().padStart(2, '0')}"))
            val metric = refreshed.widget()!!.presentation!!.metrics.single()
            assertEquals(deadline, metric.countdown)
            assertEquals(setOf(powderId), metric.replacedBossIds)
            assertTrue(refreshed.filterIsInstance<CoreEvent.NoticeObserved>().isEmpty())
        }
    }

    @Test fun `nonmatching same UUID title cannot renew retained event source lease`() {
        context("crystal_hollows", "Crystal Nucleus")
        bosses(boss())
        for (second in 1..5) {
            time = second * 1_000L
            context("crystal_hollows", "Crystal Nucleus")
            val retained = bosses(boss(title = "Future unknown boss 42"))
            if (second < 5) assertTrue(retained.widget()!!.replacedBossIds.isEmpty())
            else assertNull(retained.widget())
        }
    }

    @Test fun `observed zero expands same metric once and repeated zero or objective never extends ending`() {
        context("crystal_hollows", "Crystal Nucleus")
        bosses(boss(title = "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 00:02"))
        time = 2_000; context("crystal_hollows", "Crystal Nucleus")
        val zero = bosses(boss(title = "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 00:00"))
        val ending = zero.widget()!!.presentation!!.metrics.single()
        assertEquals("event:better_together", ending.id)
        assertEquals("Better Together ended", ending.label)
        assertNull(ending.countdown)
        assertEquals(setOf(powderId), ending.replacedBossIds)
        assertEquals(4_400L, ending.expandedUntil)
        assertTrue(zero.filterIsInstance<CoreEvent.NoticeObserved>().isEmpty())
        time = 2_600
        val objective = bosses(boss(title = "Objective: Talk to the Goblin King")).widget()!!.presentation!!.metrics.single()
        assertEquals(ending.expandedUntil, objective.expandedUntil)
        assertTrue(objective.replacedBossIds.isEmpty())
        time = 2_700
        assertEquals(ending.expandedUntil, bosses(boss(title = "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 00:00"))
            .widget()!!.presentation!!.metrics.single().expandedUntil)
        time = 3_000
        assertEquals(ending.expandedUntil, module.parse(NormalizedSignal.Chat("BETTER TOGETHER ENDED!"))
            .widget()!!.presentation!!.metrics.single().expandedUntil)
        time = 3_100
        assertNotNull(bosses().widget()) // The confirmed ending outlives server bar removal briefly.
        time = 4_400
        assertNull(context("crystal_hollows", "Crystal Nucleus").widget())
    }

    @Test fun `each passive ends by its own title and zero never introduces a new running event`() {
        for (type in listOf("2X POWDER" to "Double Powder", "BETTER TOGETHER" to "Better Together", "GONE WITH THE WIND" to "Gone with the Wind")) {
            module.onSessionStarted("new")
            context()
            val events = bosses(boss(title = "PASSIVE EVENT ${type.first} RUNNING FOR 00:00"))
            assertEquals("${type.second} ended", events.widget()!!.presentation!!.metrics.single().label)
            assertTrue(events.filterIsInstance<CoreEvent.NoticeObserved>().isEmpty())
        }
    }

    @Test fun `source loss and session change never manufacture a completion message`() {
        context("crystal_hollows", "Crystal Nucleus"); bosses(boss())
        time = 500
        assertNull(bosses().widget())
        assertNull(module.parse(NormalizedSignal.Chat("2X POWDER ENDED!")).widget())
        bosses(boss(title = "PASSIVE EVENT 2X POWDER RUNNING FOR 00:00"))
        assertNull(context("mining_3").widget())
        module.onSessionEnded()
        context()
        assertNull(context().widget())
    }

    @Test fun `late end chat cannot resurrect a source whose lease already expired`() {
        context(); bosses(boss())
        time = 6_000; context()
        assertNull(module.parse(NormalizedSignal.Chat("2X POWDER ENDED!")).widget())
    }

    @Test fun `delayed first alternate source cannot replace confirmed ending with another introduction`() {
        context()
        val first = bosses(boss(title = "PASSIVE EVENT 2X POWDER RUNNING FOR 00:00"))
            .widget()!!.presentation!!.metrics.single()
        time = 100
        val tabReplay = tab("Mining Event: 2x Powder", "Ends in: 1s")
        assertEquals(first.expandedUntil, tabReplay.widget()!!.presentation!!.metrics.single().expandedUntil)
        assertEquals("Double Powder ended", tabReplay.widget()!!.presentation!!.metrics.single().label)
        assertTrue(tabReplay.filterIsInstance<CoreEvent.NoticeObserved>().isEmpty())
        module.onSessionEnded(); context()
        val tabZero = tab("Mining Event: Better Together", "Ends in: 0s").widget()!!.presentation!!.metrics.single()
        time = 200
        val bossReplay = bosses(boss(title = "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 00:01"))
        assertEquals(tabZero.expandedUntil, bossReplay.widget()!!.presentation!!.metrics.single().expandedUntil)
        assertEquals("Better Together ended", bossReplay.widget()!!.presentation!!.metrics.single().label)
        assertTrue(bossReplay.filterIsInstance<CoreEvent.NoticeObserved>().isEmpty())
    }

}
