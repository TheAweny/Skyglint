package dev.skyglint.features.garden

import dev.skyglint.core.*

/** Absolute source counts only: spawn/kill chat never adds/subtracts from an old total. */
class GardenPestModule(clock: () -> Long = { System.nanoTime() / 1_000_000 }) :
    GardenWidgetModule("skyglint.garden.pests", clock, 75) {
    private val total = Regex("(?:. )?The Garden [\\uE018\\uE07F] x([0-9]{1,3})")
    private val plot = Regex("(?:. )?Plot - ([A-Za-z0-9 ]{1,12}) [\\uE018\\uE07F] x([0-9]{1,3})")
    private val plots = Regex("Plots: ([0-9]{1,2}(?:, [0-9]{1,2}){0,23})")
    private val noPests = Regex("[⏣\\uE067] The Garden")
    private data class Observed(val scope: String?, val metrics: List<WidgetMetric>, val at: Long)
    private var sidebar: Observed? = null
    private var tab: Observed? = null
    private var zeroAt = Long.MIN_VALUE
    override fun clearState() { sidebar = null; tab = null; zeroAt = Long.MIN_VALUE }
    private fun current(): List<CoreEvent> {
        val valid = { observation: Observed? -> observation?.takeIf { clock() - it.at in 0..3_999 } }
        val best = valid(sidebar) ?: valid(tab) ?: return removed()
        return widget("Pests", best.scope, presentation = WidgetPresentation(WidgetRole.COUNTERS, "minecraft:spider_eye", best.metrics))
    }
    override fun observe(signal: NormalizedSignal): List<CoreEvent> {
        when (signal) {
            is NormalizedSignal.SidebarSnapshot -> {
                val lines = GardenSignals.lines(signal.lines)
                if (lines.any(noPests::matches)) {
                    clearState(); zeroAt = clock(); return removed()
                }
                lines.firstNotNullOfOrNull { total.matchEntire(it) }?.let {
                    val count = it.groupValues[1].toInt()
                    zeroAt = if (count == 0) clock() else Long.MIN_VALUE
                    sidebar = if (count == 0) null else Observed("Garden",
                        listOf(WidgetMetric("pests", "Pests", count.toString(), "minecraft:spider_eye", 75)), clock())
                    return if (count == 0) { tab = null; removed() } else current()
                }
                lines.firstNotNullOfOrNull { plot.matchEntire(it) }?.let {
                    val count = it.groupValues[2].toInt()
                    zeroAt = if (count == 0) clock() else Long.MIN_VALUE
                    val scope = "Plot ${it.groupValues[1]}"
                    sidebar = if (count == 0) null else Observed(scope,
                        listOf(WidgetMetric("pests", "Pests · $scope", count.toString(), "minecraft:spider_eye", 75)), clock())
                    return if (count == 0) { tab = null; removed() } else current()
                }
                // A complete sidebar without a recognized pest-count line cannot refresh stale counts.
                sidebar = null
                return current()
            }
            is NormalizedSignal.TabSnapshot -> {
                if (zeroAt != Long.MIN_VALUE && clock() - zeroAt in 0..3_999) { tab = null; return removed() }
                val lines = GardenSignals.lines(signal.lines)
                if ("Pests:" !in lines) { tab = null; return current() }
                val section = GardenSignals.section(lines, "Pests:")
                val list = section.firstNotNullOfOrNull { plots.matchEntire(it)?.groupValues?.get(1) }
                tab = list?.let {
                    val count = it.split(", ").distinct().size
                    Observed("Infested plots", listOf(
                        WidgetMetric("infested_plots", "Infested plots", count.toString(), "minecraft:map", 75),
                        WidgetMetric("plot_list", "Plots", it, "minecraft:map", 0)), clock())
                }
                return current()
            }
            is NormalizedSignal.Chat -> if (SkyBlockParser.clean(signal.text) ==
                "There are not any Pests on your Garden right now! Keep farming!") { clearState(); zeroAt = clock(); return removed() }
            else -> Unit
        }
        return emptyList()
    }
}
