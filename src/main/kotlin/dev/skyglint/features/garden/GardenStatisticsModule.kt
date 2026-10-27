package dev.skyglint.features.garden

import dev.skyglint.core.*

/** Observed farming statistics, not a speculative crops/hour or earnings estimator. */
class GardenStatisticsModule(clock: () -> Long = { System.nanoTime() / 1_000_000 }) :
    GardenWidgetModule("skyglint.garden.statistics", clock, 30) {
    private val fortune = Regex("Farming Fortune: \\uE051([0-9]{1,6})")
    private val level = Regex("Garden Level: ([0-9]{1,3})")
    private val copper = Regex("Copper: ([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{1,12})")
    private var metrics: List<WidgetMetric> = emptyList()
    private var observedAt = Long.MIN_VALUE
    private var sidebarPlot: String? = null
    private var sidebarAt = Long.MIN_VALUE
    override fun clearState() { metrics = emptyList(); observedAt = Long.MIN_VALUE; sidebarPlot = null; sidebarAt = Long.MIN_VALUE }
    override fun observeContext(): List<CoreEvent> {
        val fresh = metrics.takeIf { observedAt != Long.MIN_VALUE && clock() - observedAt in 0..4_000 }.orEmpty()
        val plot = sidebarPlot.takeIf { sidebarAt != Long.MIN_VALUE && clock() - sidebarAt in 0..3_999 }
        val fallback = location?.takeUnless { it in setOf("Garden", "The Garden") ||
            (sidebarAt != Long.MIN_VALUE && GardenSignals.plotLocation(it) != null) }
        return widget("Garden", plot ?: fallback, lifetime = if (plot != null) 4_000 - (clock() - sidebarAt) else 4_000,
            presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:wheat", fresh))
    }
    override fun observe(signal: NormalizedSignal): List<CoreEvent> {
        if (signal is NormalizedSignal.SidebarSnapshot) {
            val lines = GardenSignals.lines(signal.lines)
            val plot = lines.firstNotNullOfOrNull(GardenSignals::plotLocation)
            if (plot != null || lines.any(GardenSignals::isGardenArea)) {
                sidebarPlot = plot
                sidebarAt = clock()
            }
            return observeContext()
        }
        if (signal !is NormalizedSignal.TabSnapshot) return emptyList()
        val lines = GardenSignals.lines(signal.lines)
        val ff = lines.firstNotNullOfOrNull { fortune.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        val gardenLevel = lines.firstNotNullOfOrNull { level.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        val copperCount = lines.firstNotNullOfOrNull { copper.matchEntire(it)?.groupValues?.get(1)?.let { value -> GardenSignals.count(value) } }
        metrics = listOfNotNull(
            ff?.let { WidgetMetric("farming_fortune", "Farming Fortune", it.toString(), "minecraft:golden_hoe", 30, group = "garden:stats", referenceOnly = true) },
            gardenLevel?.let { WidgetMetric("garden_level", "Garden Level", it.toString(), "minecraft:wheat", 5, referenceOnly = true) },
            copperCount?.let { WidgetMetric("copper", "Copper", it.toString(), "minecraft:copper_ingot", 10, group = "currency", referenceOnly = true) })
        observedAt = clock()
        return observeContext()
    }
}
