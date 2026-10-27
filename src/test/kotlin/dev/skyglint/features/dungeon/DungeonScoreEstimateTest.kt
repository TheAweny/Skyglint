package dev.skyglint.features.dungeon

import dev.skyglint.core.*
import kotlin.test.*

class DungeonScoreEstimateTest {
    private fun input() = DungeonScoreEstimate.Input("F7", 100, 120, 20, 100.0, 5, 0, 0)

    @Test fun `confirmed clear base retains unknown bonus interval and explicit estimate marker`() {
        val result = assertNotNull(DungeonScoreEstimate.calculate(input()))
        assertEquals(304, result.minimum) // rounded secret display could be 99.5%
        assertEquals(319, result.maximum) // crypt5, Paul10, mimic2, prince1, bat1
        assertEquals(20, result.minimumRooms)
        assertEquals(20, result.maximumRooms)
        assertEquals("~304–319 · S+", result.caption)
    }
    @Test fun `rounded room percentage retains every possible integer total instead of picking one`() {
        val result = assertNotNull(DungeonScoreEstimate.calculate(input().copy(rooms = 1, cleared = 7)))
        assertEquals(14, result.minimumRooms)
        assertEquals(15, result.maximumRooms)
        val inconsistent = input().copy(rooms = 1, cleared = 99)
        assertNull(DungeonScoreEstimate.calculate(inconsistent))
    }
    @Test fun `incomplete puzzles and deaths cost points with Spirit left unknown`() {
        val base = assertNotNull(DungeonScoreEstimate.calculate(input()))
        val failed = assertNotNull(DungeonScoreEstimate.calculate(input().copy(deaths = 1, puzzlePenalty = 10)))
        assertEquals(base.minimum - 12, failed.minimum)
        assertEquals(base.maximum - 11, failed.maximum)
    }
    @Test fun `time allowance and floor specific secrets requirements affect bounded result`() {
        val f7 = assertNotNull(DungeonScoreEstimate.calculate(input().copy(secretsPercent = 70.0)))
        val f5 = assertNotNull(DungeonScoreEstimate.calculate(input().copy(floor = "F5", secretsPercent = 70.0)))
        assertTrue(f5.minimum > f7.minimum)
        val late = assertNotNull(DungeonScoreEstimate.calculate(input().copy(elapsedSeconds = 1_008)))
        assertEquals(10, assertNotNull(DungeonScoreEstimate.calculate(input())).minimum - late.minimum)
        assertTrue(assertNotNull(DungeonScoreEstimate.calculate(input().copy(floor = "E"))).maximum < 230)
    }
    @Test fun `invalid incomplete zero and nonfinite base never make a score`() {
        for (value in listOf(input().copy(cleared = 0), input().copy(rooms = 0), input().copy(deaths = -1),
            input().copy(floor = "F999"), input().copy(puzzlePenalty = 1), input().copy(secretsPercent = Double.NaN)))
            assertNull(DungeonScoreEstimate.calculate(value), value.toString())
    }
    @Test fun `complete contiguous puzzle section required including repeated unknown names`() {
        assertEquals(DungeonPatterns.Puzzles(2, 0, 2), DungeonPatterns.stats(listOf("Puzzles: (2)", "???: [✦]", "???: [✦]")).puzzles)
        assertEquals(DungeonPatterns.Puzzles(2, 1, 1), DungeonPatterns.stats(listOf("Puzzles: (2)", "Blaze: [✔]", "Water: [✖] (Player)")).puzzles)
        for (body in listOf(listOf("Puzzles: (2)", "Blaze: [✔]"), listOf("Puzzles: (2)", "Blaze: [✔]", "Crypts: 3"),
            listOf("Puzzles: (1)", "Blaze: [?]"), listOf("Puzzles: (2)", "Blaze: [✔]", "Blaze: [✔]")))
            assertNull(DungeonPatterns.stats(body).puzzles, body.toString())
    }
    @Test fun `module suppresses estimate if any base input missing and preserves actual score precedence`() {
        var now = 0L
        val module = DungeonModule { now }
        fun List<CoreEvent>.run() = filterIsInstance<CoreEvent.WidgetObserved>().single { it.widget.id == DungeonModule.RUN_ID }.widget
        module.parse(NormalizedSignal.ContextSnapshot("The Catacombs (F7)", "dungeon"))
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Catacombs (F7)", "Cleared: 100% (305)", "Time Elapsed: 2m 0s")))
        val base = listOf("Secrets Found: 100%", "Crypts: 5", "Completed Rooms: 20", "Team Deaths: 0", "Puzzles: (0)")
        val complete = module.parse(NormalizedSignal.TabSnapshot(base)).run()
        assertEquals("305 · S+", complete.presentation!!.metrics.single { it.id == "score" }.value)
        assertTrue(complete.presentation!!.metrics.none { it.id == "forecast" })
        val fallback = module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Catacombs (F7)", "Cleared: 100%", "Time Elapsed: 2m 0s"))).run()
        assertTrue(fallback.presentation!!.metrics.none { it.id == "score" })
        assertEquals("~304–319 · S+", fallback.presentation!!.metrics.single { it.id == "forecast" }.value)
        for (missing in base.indices) {
            val partial = module.parse(NormalizedSignal.TabSnapshot(base.filterIndexed { index, _ -> index != missing })).run()
            assertTrue(partial.presentation!!.metrics.none { it.id == "forecast" })
        }
        module.parse(NormalizedSignal.TabSnapshot(base))
        val observed = module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Catacombs (F7)", "Cleared: 100% (305)", "Time Elapsed: 2m 0s"))).run()
        assertTrue(observed.presentation!!.metrics.none { it.id == "forecast" })
        val final = module.parse(NormalizedSignal.Chat("Team Score: 307 (S+)")).run()
        assertTrue(final.presentation!!.metrics.none { it.id == "forecast" })
        val lateSidebar = module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Catacombs (F7)", "Cleared: 100% (305)", "Time Elapsed: 2m 0s"))).run()
        assertEquals("307 · S+", lateSidebar.presentation!!.metrics.single { it.id == "score" }.value)
        now = 5_001
        assertTrue(module.parse(NormalizedSignal.ContextSnapshot(null, "dungeon")).run().presentation!!.metrics.none { it.id == "forecast" })
    }
}
