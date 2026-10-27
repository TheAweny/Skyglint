package dev.skyglint.features.dungeon

import dev.skyglint.core.*
import kotlin.test.*

class DungeonScoreTest {
    @Test fun `live score is the explicit parenthetical number not clear percentage`() {
        val observed = assertNotNull(DungeonScore.fromSidebar("§7Cleared: §a63% §7(§b210§7)"))
        assertEquals(210, observed.score)
        assertEquals("B", observed.rank)
        assertEquals("210 · B", observed.caption)
        assertFalse(observed.final)
        assertNull(DungeonScore.fromSidebar("Cleared: 63%"))
        assertNull(DungeonScore.fromSidebar("Cleared: 101% (210)"))
        assertNull(DungeonScore.fromSidebar("Cleared: 63% (999)"))
        assertNull(DungeonScore.fromSidebar("Player: Cleared: 63% (210)"))
    }

    @Test fun `all rank boundaries follow observed score thresholds`() {
        for ((score, rank) in listOf(0 to "D", 99 to "D", 100 to "C", 159 to "C", 160 to "B", 229 to "B",
            230 to "A", 269 to "A", 270 to "S", 299 to "S", 300 to "S+", 317 to "S+"))
            assertEquals(rank, DungeonScore.rank(score), score.toString())
        assertNull(DungeonScore.rank(-1)); assertNull(DungeonScore.rank(401))
    }

    @Test fun `completion takes exact server team score and preserves its explicit final rank`() {
        val observed = assertNotNull(DungeonScore.fromCompletion("  §r§fTeam Score: §e301 §r§7(§6S+§7)"))
        assertEquals(301, observed.score)
        assertEquals("S+", observed.rank)
        assertTrue(observed.final)
        for (line in listOf("TOTAL SCORE: 301", "Score: 301", "Dungeon Score: 301", "Player: Team Score: 301 (S+)",
            "Team Score: 301 (SS)", "Team Score: 999 (S+)", "Team Score: 301 (S+)\nchat"))
            assertNull(DungeonScore.fromCompletion(line), line)
    }

    @Test fun `module publishes direct live score and final score survives stale sidebar until lifecycle reset`() {
        var now = 0L
        val module = DungeonModule { now }
        fun List<CoreEvent>.run() = filterIsInstance<CoreEvent.WidgetObserved>().single { it.widget.id == DungeonModule.RUN_ID }.widget
        module.parse(NormalizedSignal.ContextSnapshot("The Catacombs (F7)", "dungeon"))
        val run = module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Catacombs (F7)", "Cleared: 63% (210)"))).run()
        val metric = run.presentation!!.metrics.single { it.id == "score" }
        assertEquals("210 · B", metric.value); assertEquals(100, metric.priority)
        assertEquals("minecraft:nether_star", metric.icon)
        now = 1_000
        val final = module.parse(NormalizedSignal.Chat("Team Score: 301 (S+)")).run()
        assertEquals("Completed", final.detail)
        assertEquals("301 · S+", final.presentation!!.metrics.single { it.id == "score" }.value)
        now = 6_000
        val refreshed = module.parse(NormalizedSignal.ContextSnapshot("The Catacombs (F7)", "dungeon")).run()
        assertEquals("301 · S+", refreshed.presentation!!.metrics.single { it.id == "score" }.value)
        module.parse(NormalizedSignal.ContextSnapshot("Village", "hub"))
        assertTrue(module.parse(NormalizedSignal.ContextSnapshot(null, "dungeon")).run().presentation!!.metrics.none { it.id == "score" })
    }

    @Test fun `unknown current score never becomes an assumed score or rank`() {
        var now = 0L
        val module = DungeonModule { now }
        assertTrue(module.parse(NormalizedSignal.Chat("Team Score: 301 (S+)")).isEmpty())
        module.parse(NormalizedSignal.ContextSnapshot(null, "dungeon"))
        val events = module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Catacombs (F7)", "Cleared: 63%")))
        val run = events.filterIsInstance<CoreEvent.WidgetObserved>().single { it.widget.id == DungeonModule.RUN_ID }.widget
        assertTrue(run.presentation!!.metrics.none { it.id == "score" })
        now = 6_000
        assertTrue(module.parse(NormalizedSignal.Chat("Team Score: 301 (S+)")).isEmpty())
    }
}
