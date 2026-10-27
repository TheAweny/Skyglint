package dev.skyglint.features.garden

import dev.skyglint.core.*

class GardenMilestoneModule(clock: () -> Long = { System.nanoTime() / 1_000_000 }) :
    GardenWidgetModule("skyglint.garden.milestone", clock, 45) {
    private val milestone = Regex("(${GardenSignals.cropPattern}) ([0-9]{1,3}): ([0-9]{1,3}(?:\\.[0-9]{1,3})?%|MAX)")
    private val confirmed = Regex("GARDEN MILESTONE (${GardenSignals.cropPattern}) ([0-9]{1,3})➜([0-9]{1,3})")
    override fun observe(signal: NormalizedSignal): List<CoreEvent> {
        if (signal is NormalizedSignal.TabSnapshot) {
            val lines = GardenSignals.lines(signal.lines)
            if ("Crop Milestones:" !in lines) return removed()
            val records = GardenSignals.section(lines, "Crop Milestones:").mapNotNull { milestone.matchEntire(it) }
            // The first displayed crop is the server's choice; other snapshots do not imply active harvesting.
            val match = records.firstOrNull() ?: return removed()
            val value = match.groupValues[3]
            val fraction = if (value == "MAX") 1.0 else value.removeSuffix("%").toDoubleOrNull()?.div(100)
            if (fraction == null || fraction !in 0.0..1.0) return removed()
            val name = match.groupValues[1]
            val icon = GardenSignals.icon(name)
            return widget(name, "Milestone", ProgressSnapshot(null, null, fraction),
                presentation = WidgetPresentation(WidgetRole.PROGRESSION, icon,
                    listOf(WidgetMetric("tier", "Milestone Tier", match.groupValues[2], icon, 45))))
        }
        if (signal is NormalizedSignal.Chat) {
            val match = confirmed.matchEntire(SkyBlockParser.clean(signal.text)) ?: return emptyList()
            val before = match.groupValues[2].toInt(); val after = match.groupValues[3].toInt()
            if (after <= before) return emptyList()
            return listOf(CoreEvent.NoticeObserved(NoticeRequest(
                "skyglint.garden.milestone_up:${match.groupValues[1]}:$after", "Garden milestone!",
                "${match.groupValues[1]} · Tier $after", priority = 55)))
        }
        return emptyList()
    }
}
