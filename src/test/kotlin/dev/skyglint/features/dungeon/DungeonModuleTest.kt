package dev.skyglint.features.dungeon

import dev.skyglint.core.*
import kotlin.test.*

class DungeonModuleTest {
    private var time = 0L
    private val module = DungeonModule { time }
    private fun context(mode: String? = "dungeon", location: String? = null) = module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun sidebar(vararg lines: String) = module.parse(NormalizedSignal.SidebarSnapshot(lines.toList()))
    private fun tab(vararg lines: String) = module.parse(NormalizedSignal.TabSnapshot(lines.toList()))
    private fun chat(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun List<CoreEvent>.widget(id: String = DungeonModule.RUN_ID) = filterIsInstance<CoreEvent.WidgetObserved>().firstOrNull { it.widget.id == id }?.widget

    @Test fun `related dungeon values carry bounded semantic groups across source widgets`() {
        context()
        sidebar("The Catacombs (F7)", "Cleared: 63% (210)", "Time Elapsed: 4m 12s")
        val events = tab("Secrets Found: 72.5%", "Crypts: 4", "Completed Rooms: 18", "Team Deaths: 1",
            "Puzzles: (2)", "Blaze: [✔]", "Water: [✦]")
        val metrics = events.filterIsInstance<CoreEvent.WidgetObserved>().flatMap { it.widget.presentation!!.metrics }
        val groups = metrics.filter { it.group != null }.groupBy { it.group }
        assertEquals(setOf("score", "secrets"), groups.getValue("dungeon:run").map { it.id }.toSet())
        assertEquals(setOf("cleared", "time"), groups.getValue("dungeon:progress").map { it.id }.toSet())
        assertEquals(setOf("crypts", "deaths", "rooms"), groups.getValue("dungeon:exploration").map { it.id }.toSet())
        assertTrue(groups.values.all { it.size <= 3 })
        assertNull(metrics.single { it.id == "puzzles" }.group)
        assertEquals("210 · B", metrics.single { it.id == "score" }.value)
        assertTrue(metrics.none { it.id == "forecast" })
    }

    @Test fun `group tags preserve authoritative final score and never merge an estimate with it`() {
        context()
        sidebar("The Catacombs (F7)", "Cleared: 63%", "Time Elapsed: 4m 12s")
        val before = tab("Secrets Found: 72.5%", "Crypts: 4", "Completed Rooms: 19", "Team Deaths: 1",
            "Puzzles: (2)", "Blaze: [✔]", "Water: [✦]").widget()!!.presentation!!.metrics
        assertEquals("dungeon:run", before.single { it.id == "forecast" }.group)
        val final = chat("Team Score: 301 (S+)").widget()!!.presentation!!.metrics
        assertEquals("301 · S+", final.single { it.id == "score" }.value)
        assertEquals("dungeon:run", final.single { it.id == "score" }.group)
        assertTrue(final.none { it.id == "forecast" })
    }

    @Test fun `official mode allows incomplete context without inventing floor progress or score`() {
        val widget = context().widget()!!
        assertEquals("Catacombs", widget.title)
        assertNull(widget.detail)
        assertNull(widget.progress)
        assertEquals(WidgetRole.PRIMARY, widget.presentation?.role)
        assertTrue(widget.presentation!!.metrics.isEmpty())
        assertEquals(5_000L, widget.expiresAt)
        assertTrue(context("hub").all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `exact sidebar floor allows fallback but lobby and inventory titles never activate dungeon`() {
        assertNull(sidebar("The Catacombs", "Cleared: 40%").widget())
        assertNull(module.parse(NormalizedSignal.MenuSnapshot("The Catacombs (F7)", emptyList())).widget())
        val widget = sidebar(" §7⏣ §bThe Catacombs (F7)", "Cleared: 63% (200)", "Time Elapsed: §a01m 17s").widget()!!
        assertEquals("Catacombs F7", widget.title)
        assertNull(widget.detail)
        assertEquals(mapOf("score" to "200 · B", "cleared" to "63%", "time" to "1:17"), widget.presentation!!.metrics.associate { it.id to it.value })
        assertEquals(.63, widget.progress!!.fraction)
        context("hub")
        assertNull(sidebar("⏣ The Catacombs (F7)").widget())
        assertEquals("Catacombs", context().widget()?.title)
    }
    @Test fun `tab observed stats remain independent and absent values are never zero`() {
        context()
        sidebar("\uE067 The Catacombs (M7)")
        val widget = tab("Secrets Found: 45.5%", "Crypts: 3", "Completed Rooms: 12", "Team Deaths: 1").widget(DungeonModule.STATS_ID)!!
        assertEquals("Secrets 45.5% · Crypts 3 · Deaths 1 · Rooms 12", widget.detail)
        assertEquals(WidgetRole.STATS, widget.presentation?.role)
        val metrics = widget.presentation!!.metrics.associateBy { it.id }
        assertEquals("45.5%", metrics.getValue("secrets").value)
        assertEquals("minecraft:ender_pearl", metrics.getValue("secrets").icon)
        assertEquals("minecraft:skeleton_skull", metrics.getValue("deaths").icon)
        assertEquals(80, metrics.getValue("deaths").priority)
        assertEquals(setOf("secrets", "crypts", "deaths", "rooms"), metrics.keys)
        assertNull(tab("Health: 100").widget(DungeonModule.STATS_ID))
        assertNull(tab("Secrets Found: 999%", "Crypts: -1", "Completed Rooms: none", "Team Deaths: 3x").widget(DungeonModule.STATS_ID))
    }
    @Test fun `stale stats expire even when context continues and stale context cannot be renewed by chat`() {
        context(); sidebar("⏣ The Catacombs (F7)")
        tab("Crypts: 3")
        time = 4_000
        assertEquals(5_000L, context().widget(DungeonModule.STATS_ID)?.expiresAt)
        time = 5_001
        val refreshed = context()
        assertNull(refreshed.widget(DungeonModule.STATS_ID))
        assertNotNull(refreshed.widget())
        time = 10_002
        assertTrue(chat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!").isEmpty())
    }
    @Test fun `source confirmed boss phases require matching floor and plain boss words cannot complete run`() {
        context(); sidebar("⏣ The Catacombs (F7)")
        assertTrue(chat("Party > Player: [BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!").isEmpty())
        assertTrue(chat("[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.").isEmpty())
        assertEquals("Maxor", chat("§c[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!").widget()?.detail)
        assertEquals("Storm", chat("[BOSS] Storm: Pathetic Maxor, just like expected.").widget()?.detail)
        assertEquals("Goldor", chat("[BOSS] Goldor: Who dares trespass into my domain?").widget()?.detail)
        assertTrue(chat("[BOSS] Necron: All this, for nothing...").isEmpty())
        assertTrue(chat("☠ Defeated Bonzo in 12s").isEmpty())
        assertEquals("Completed", chat(" ☠ Defeated Necron in 8m 12s (NEW RECORD!) ").widget()?.detail)
    }
    @Test fun `explicit room start and blood completion are observations not percentages or inventory loot`() {
        context()
        assertEquals("Exploring", chat("[NPC] Mort: Here, I found this map when I first entered the dungeon.").widget()?.detail)
        assertEquals("Blood room", chat("The BLOOD DOOR has been opened!").widget()?.detail)
        assertEquals("Blood cleared", chat("[BOSS] The Watcher: You have proven yourself. You may pass.").widget()?.detail)
        val widget = sidebar("The Catacombs (F1)", "Cleared: 100%").widget()!!
        assertEquals("Blood cleared", widget.detail)
        assertEquals("100%", widget.presentation!!.metrics.single { it.id == "cleared" }.value)
        assertNull(module.parse(NormalizedSignal.MenuSnapshot("Reward Chest", emptyList())).widget())
    }
    @Test fun `floor change and session lifecycle clear phase and stats`() {
        context(); sidebar("The Catacombs (F7)"); tab("Crypts: 3")
        chat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")
        val nextFloor = sidebar("The Catacombs (F1)")
        assertEquals("Catacombs F1", nextFloor.widget()?.title)
        assertNull(nextFloor.widget()?.detail)
        assertNull(nextFloor.widget(DungeonModule.STATS_ID))
        module.onSessionEnded()
        assertTrue(chat("The BLOOD DOOR has been opened!").isEmpty())
        assertNull(context().widget()?.detail)
        module.onSessionStarted("new")
        assertNull(tab("Crypts: 8").widget())
    }
    @Test fun `malformed floor clear duration and trailing unrelated text do not become data`() {
        assertNull(sidebar("The Catacombs (F999)", "Cleared: -1%", "Time Elapsed: 2m 99s").widget())
        context()
        val widget = sidebar("The Catacombs (M7)", "Cleared: 101%", "Time Elapsed: 2:01").widget()!!
        assertEquals("Catacombs M7", widget.title)
        assertNull(widget.detail)
        assertNull(widget.progress)
        assertTrue(widget.presentation!!.metrics.isEmpty())
    }

    @Test fun `run caption contains only phase while all observed numbers remain separate metrics`() {
        context()
        sidebar("The Catacombs (F7)", "Cleared: 63%", "Time Elapsed: 4m 12s")
        val events = tab("Secrets Found: 72.5%", "Crypts: 4", "Completed Rooms: 18", "Team Deaths: 1")
        val run = events.widget()!!
        assertEquals("Catacombs F7", run.title)
        assertNull(run.detail)
        assertEquals(setOf("cleared", "time"), run.presentation!!.metrics.map { it.id }.toSet())
        val phase = chat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!").widget()!!
        assertEquals("Maxor", phase.detail)
        assertFalse(phase.detail!!.contains("63"))
        assertFalse(phase.detail!!.contains("4:12"))
        assertEquals(.63, phase.progress!!.fraction)
        assertEquals(4, events.widget(DungeonModule.STATS_ID)!!.presentation!!.metrics.size)
    }
}
