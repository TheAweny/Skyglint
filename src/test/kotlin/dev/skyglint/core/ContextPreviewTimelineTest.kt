package dev.skyglint.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class ContextPreviewTimelineTest {
    @Test fun `Garden has distinct live data widgets and Jacob takes precedence when active`() {
        val timeline = ContextPreviewTimeline(0, false)
        val ordinary = timeline.snapshot(0)
        assertEquals(5, ordinary.widgets.size)
        assertTrue(ordinary.widgets.any { it.id == "skyglint.currencies.current" })
        assertTrue(ordinary.widgets.any { it.id == "skyglint.garden.visitors.current" && it.presentation?.metrics?.any { metric -> metric.id == "visitors" && metric.value == "3" } == true })
        assertTrue(ordinary.widgets.any { it.id == "skyglint.garden.milestone.current" && it.progress?.fraction == .977 })
        val contest = timeline.snapshot(8_000)
        assertEquals("skyglint.garden.jacob.current", contest.widgets.first().id)
        assertEquals(listOf("Carrot", "Pumpkin", "Wheat"), contest.activeNotice!!.pages.map { it.title })
        assertEquals("Objective: Talk to the Bartender", contest.bosses.single().title) // Preserved beneath contextual selection.
    }
    @Test fun `Dungeon phases and stats pass through actual detectors without Garden leakage`() {
        val timeline = ContextPreviewTimeline(0, true)
        val initial = timeline.snapshot(0)
        assertEquals(setOf("skyglint.dungeon.run", "skyglint.dungeon.stats"), initial.widgets.map { it.id }.toSet())
        assertTrue(initial.widgets.first().presentation!!.metrics.any { it.id == "cleared" && it.value == "63%" })
        assertTrue(timeline.snapshot(4_000).widgets.first().detail!!.contains("Maxor"))
        assertTrue(timeline.snapshot(14_000).widgets.first().detail!!.contains("Storm"))
        assertTrue(timeline.snapshot(24_000).widgets.none { it.detail?.contains("Storm") == true })
    }
    @Test fun `copied sidebar input cannot be mutated by the adapter afterwards`() {
        val lines = mutableListOf("The Catacombs (F7)")
        val snapshot = NormalizedSignal.SidebarSnapshot(lines)
        lines.clear()
        assertEquals(listOf("The Catacombs (F7)"), snapshot.lines)
    }
}
