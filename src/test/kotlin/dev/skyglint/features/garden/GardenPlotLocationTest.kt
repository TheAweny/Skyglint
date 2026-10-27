package dev.skyglint.features.garden

import dev.skyglint.core.*
import kotlin.test.*

class GardenPlotLocationTest {
    private var time = 0L
    private val module = GardenStatisticsModule { time }
    // Exact user log 2026-10-27, mini77CS: team uniqueness codes occur inside both words and numbers.
    private val area = " §7\uE067 §cThe Garde§v§cn §4§l\uE07F§7 x8"
    private fun plot(number: Int, pests: Int) = "   §aPlot §7- §b§u§b$number §4§l\uE07F§7 x$pests"
    private fun List<CoreEvent>.view() = filterIsInstance<CoreEvent.WidgetObserved>().single().widget
    private fun context(location: String? = "The Garden", mode: String? = "garden") =
        module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun sidebar(vararg lines: String) = module.parse(NormalizedSignal.SidebarSnapshot(lines.toList()))

    @Test fun `live separate plot line wins over generic Garden context and strips pest suffix`() {
        context()
        val first = sidebar(area, plot(4, 1)).view()
        assertEquals("Garden", first.title)
        assertEquals("Plot - 4", first.detail)
        assertTrue(first.presentation!!.metrics.isEmpty()) // local pest number is never a Garden statistic
        time = 250
        assertEquals("Plot - 4", context().view().detail)
        time = 1_000
        assertEquals("Plot - 8", sidebar(area, plot(8, 2)).view().detail)
        assertEquals("Plot - 8", context("Garden").view().detail)
        assertEquals("Plot - 8", module.parse(NormalizedSignal.TabSnapshot(listOf("Copper: 103"))).view().detail)
    }
    @Test fun `explicit complete Garden sidebar without Plot immediately clears old Plot`() {
        context("Plot - 4")
        sidebar(area, plot(4, 1))
        val cleared = sidebar(area, "Purse: 554,022,360").view()
        assertNull(cleared.detail)
        assertNull(context("Plot - 4").view().detail) // generic/old context cannot restore deleted sidebar location
        assertNull(context().view().detail)
        assertEquals("Plot - 16", sidebar(area, "Plot - 16").view().detail)
        assertNull(sidebar("§7\uE067 §aThe Garden").view().detail)
    }
    @Test fun `unrelated snapshots and context refresh cannot extend Plot lease`() {
        context()
        sidebar(area, plot(4, 1))
        time = 3_999
        val almostExpired = sidebar("Purse: 123").view()
        assertEquals("Plot - 4", almostExpired.detail)
        assertEquals(4_000L, almostExpired.expiresAt)
        time = 4_000
        assertNull(context().view().detail)
        assertNull(context("Plot - 4").view().detail)
        assertEquals("Plot - 8", sidebar(area, plot(8, 2)).view().detail)
    }
    @Test fun `outside Garden and session reset cannot leak old sidebar Plot`() {
        context(); sidebar(area, plot(4, 1))
        assertTrue(context("Village", "hub").single() is CoreEvent.WidgetRemoved)
        assertTrue(sidebar(area, plot(8, 2)).isEmpty())
        assertNull(context().view().detail)
        sidebar(area, plot(4, 1)); module.onSessionEnded()
        assertTrue(sidebar(area, plot(8, 2)).isEmpty())
        assertNull(context().view().detail)
    }
    @Test fun `exact plot form avoids unrelated text and supports named plots without pests`() {
        context()
        assertEquals("Plot - Cocoa Farm", sidebar(area, "Plot - Cocoa Farm").view().detail)
        assertNull(sidebar(area, "Player: Plot - 4", "Plot - ").view().detail)
        assertNull(sidebar(area, "Plot - 4\nother line").view().detail)
        assertNull(sidebar(area, "Plot - 4 \uE07F xinvalid").view().detail)
    }
}
