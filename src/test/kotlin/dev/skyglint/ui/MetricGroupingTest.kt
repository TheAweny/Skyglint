package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class MetricGroupingTest {
    private fun widget(id: String, metrics: List<WidgetMetric>, expiry: Long? = null) = ModuleWidget(id, "Statistics",
        expiresAt = expiry, presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics))
    private fun snapshot(vararg widgets: ModuleWidget) = HudSnapshot(true, "group-test", emptyList(), null, null, null,
        widgets = widgets.toList())

    @Test fun `reference statistics retain domain grouping without duplicating scoreboard in ordinary HUD`() {
        val tab = widget("skyglint.garden.statistics.current", listOf(
            WidgetMetric("copper", "Copper", "12", "minecraft:copper_ingot", 10, group = "currency"),
            WidgetMetric("farming_fortune", "Fortune", "1234", "minecraft:golden_hoe", 30, group = "garden:stats", referenceOnly = true)))
        val sidebar = widget("skyglint.currencies.current", listOf(
            WidgetMetric("coins", "Coins", "554022360.5", "minecraft:gold_nugget", 20, group = "currency"),
            WidgetMetric("copper", "Copper", "345", "minecraft:copper_ingot", 17, group = "currency")))
        val result = HudComposition().compose(snapshot(tab, sidebar), 0)
        assertTrue(result.isEmpty())
        val grouped = MetricGrouping.select(tab.presentation!!.metrics.map { tab to it } +
            sidebar.presentation!!.metrics.map { sidebar to it })
        val money = grouped.single { it.id == "group:currency" }
        assertEquals(listOf("554022360.5", "345"), money.metrics.map { it.second.value })
        assertEquals("1234", grouped.single { it.id == "group:garden:stats" }.metrics.single().second.value)
    }

    @Test fun `block cap does not silently remove currencies within a selected block`() {
        val balances = widget("money", (1..10).map { WidgetMetric("currency$it", "Currency $it", "$it",
            "minecraft:emerald", 30, group = "currency") })
        val unrelated = widget("helpers", (1..6).map { WidgetMetric("helper$it", "Helper", "$it", priority = 40) })
        val blocks = MetricGrouping.select((balances.presentation!!.metrics.map { balances to it }) +
            unrelated.presentation!!.metrics.map { unrelated to it })
        assertEquals(7, blocks.size)
        assertEquals(10, blocks.single { it.id == "group:currency" }.metrics.size)
    }

    @Test fun `fresh source wins duplicate and its expiry returns to fresh fallback without zero invention`() {
        val fallback = widget("garden", listOf(WidgetMetric("copper", "Copper", "12", priority = 10, group = "currency")), 10_000)
        val authoritative = widget("sidebar", listOf(WidgetMetric("copper", "Copper", "345", priority = 17, group = "currency")), 4_000)
        val composer = HudComposition()
        fun selected(now: Long) = listOf(fallback, authoritative).filter { now < it.expiresAt!! }.flatMap { owner ->
            owner.presentation!!.metrics.map { owner to it } }.let { MetricGrouping.select(it).single().metrics.single().second.value }
        assertEquals("345", selected(0))
        assertEquals("12", selected(4_000))
        assertTrue(composer.compose(snapshot(fallback, authoritative), 0).isEmpty())
        assertTrue(composer.compose(snapshot(fallback, authoritative), 10_000).isEmpty())
    }

    @Test fun `currency only helper preserves idle and skyblock and Jacob shares one progress block`() {
        val money = widget("skyglint.currencies.current", listOf(WidgetMetric("bits", "Bits", "12", priority = 19, group = "currency")))
        val idle = snapshot(money).copy(location = "Village", progression = ProgressionView.SkyBlock(42, null))
        val elements = HudComposition().compose(idle, 0)
        assertEquals("Village", elements.single { it.id == "main" }.title)
        assertTrue(elements.any { it.id == "skyblock" })
        assertTrue(elements.none { it.id == "group:currency" })
        val contest = ContextPreviewTimeline(0, false).snapshot(8_000).copy(activeNotice = null)
        val group = HudComposition().compose(contest, 8_000).single { it.id == "group:jacob:progress" }
        assertEquals(listOf("18m17s", "8264"), group.parts.map { it.text })
    }
}
