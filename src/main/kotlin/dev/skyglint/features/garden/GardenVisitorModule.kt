package dev.skyglint.features.garden

import dev.skyglint.core.*

/** Queue snapshots do not manufacture visitor arrival/acceptance events. */
class GardenVisitorModule(clock: () -> Long = { System.nanoTime() / 1_000_000 }) :
    GardenWidgetModule("skyglint.garden.visitors", clock, 40) {
    private val visitors = Regex("Visitors: \\(([0-9]{1,3})\\)")
    private val next = Regex("Next Visitor: (.{1,30})")
    private val arrival = Regex("([\\p{L}\\p{N}][\\p{L}\\p{N} .’'\\-]{0,79}) has arrived on your Garden!")
    override fun observe(signal: NormalizedSignal): List<CoreEvent> {
        if (signal is NormalizedSignal.Chat) {
            val match = arrival.matchEntire(SkyBlockParser.clean(signal.text)) ?: return emptyList()
            val visitor = match.groupValues[1]
            return listOf(CoreEvent.NoticeObserved(NoticeRequest("skyglint.garden.visitor_arrived:$visitor",
                "Visitor arrived", visitor, priority = 40)))
        }
        if (signal !is NormalizedSignal.TabSnapshot) return emptyList()
        val lines = GardenSignals.lines(signal.lines)
        val count = lines.firstNotNullOfOrNull { visitors.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: return removed()
        if (count == 0) return removed()
        val nextText = lines.firstNotNullOfOrNull { next.matchEntire(it)?.groupValues?.get(1) }?.let {
            if (it == "Queue Full!") "Queue full" else GardenSignals.time(it)?.let { time -> "Next $time" }
        }
        val metrics = listOf(WidgetMetric("visitors", "Visitors", count.toString(), "minecraft:emerald", 40)) +
            listOfNotNull(nextText?.let { WidgetMetric("next_visitor", "Next Visitor", it.removePrefix("Next "), "minecraft:clock", 20) })
        return widget("Visitors", presentation = WidgetPresentation(WidgetRole.COUNTERS, "minecraft:emerald", metrics))
    }
}
