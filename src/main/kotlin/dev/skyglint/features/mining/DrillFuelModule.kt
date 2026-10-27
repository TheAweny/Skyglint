package dev.skyglint.features.mining

import dev.skyglint.core.*

/** A selected low-fuel tool contributes a helper; it never takes over the main row or notice queue. */
class DrillFuelModule(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : SignalModule {
    override val id = "skyglint.mining.drill_fuel"

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.HeldItemSnapshot) return emptyList()
        val fuel = MiningToolPatterns.fuel(signal.item)
        if (fuel == null || fuel.fraction >= .5) return listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
        val value = if (fuel.current == 0L) "Empty" else "${(fuel.fraction * 1_000).toInt() / 10.0}%"
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(
            id = WIDGET_ID,
            title = if (fuel.current == 0L) "Empty drill" else "Drill fuel",
            progress = ProgressSnapshot(fuel.current.toDouble(), fuel.capacity.toDouble(), fuel.fraction),
            priority = 90,
            expiresAt = clock() + 3_000,
            presentation = WidgetPresentation(WidgetRole.COUNTERS, "minecraft:coal", listOf(
                WidgetMetric("fuel", if (fuel.current == 0L) "Empty drill" else "Drill fuel", value,
                    "minecraft:coal", 110, "${fuel.toolName} · ${fuel.ratio}"))),
        )))
    }

    companion object { const val WIDGET_ID = "skyglint.mining.drill_fuel.current" }
}
