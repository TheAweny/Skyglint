package dev.skyglint.features.fishing.bait

import dev.skyglint.core.*

/** Selected server bait preview and small additive warnings, independent of fishing XP. */
class FishingBaitModule(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : SignalModule {
    override val id = "skyglint.fishing.bait"
    private var selected: FishingBaitPatterns.Selected? = null
    private var holdingRod = false
    private var previewAt = Long.MIN_VALUE
    private var sackEnabled: Boolean? = null
    private var selectedId: String? = null
    private var selectedAt = Long.MIN_VALUE
    private var expandedUntil: Long? = null

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.FishingBaitSnapshot -> {
                holdingRod = signal.holdingRod
                selected = if (holdingRod) FishingBaitPatterns.selected(signal.item) else null
                previewAt = now
                if (!holdingRod || (selectedAt != Long.MIN_VALUE && now - selectedAt !in 0..LEASE)) clearIdentity()
                selected?.let { bait ->
                    if (selectedId != bait.itemId) expandedUntil = now + INTRO
                    selectedId = bait.itemId
                    selectedAt = now
                }
            }
            is NormalizedSignal.Chat -> {
                sackEnabled = FishingBaitPatterns.sackEnabled(signal.text) ?: return emptyList()
            }
            else -> return emptyList()
        }
        return publish(now)
    }

    private fun publish(now: Long): List<CoreEvent> {
        if (!holdingRod || previewAt == Long.MIN_VALUE || now - previewAt !in 0 until LEASE) return removed()
        val metrics = buildList {
            selected?.let { bait ->
                add(WidgetMetric("bait_selected", bait.name, bait.remaining.toString(), "@fishing_bait",
                    priority = 100, detail = "Selected bait · ${bait.name} · ${bait.remaining} remaining" +
                        when { bait.remaining == 0L -> " · Depleted"; bait.remaining <= LOW_THRESHOLD -> " · Low"; else -> "" },
                    group = GROUP, accent = when { bait.remaining == 0L -> RED; bait.remaining <= LOW_THRESHOLD -> YELLOW; else -> null },
                    format = WidgetMetricFormat.ITEM, expandedUntil = expandedUntil))
            }
            if (sackEnabled == false) add(WidgetMetric("bait_sack_disabled", "Bait Sack", "",
                "@sack_disabled", priority = 95, detail = "Using baits from sacks is disabled · Server confirmed · Sack contents unknown",
                group = GROUP, accent = MUTED_AMBER, format = WidgetMetricFormat.ITEM))
        }
        if (metrics.isEmpty()) return removed()
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Fishing bait", priority = 85,
            expiresAt = previewAt + LEASE,
            presentation = WidgetPresentation(WidgetRole.COUNTERS, "minecraft:fishing_rod", metrics))))
    }

    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun clearIdentity() { selectedId = null; selectedAt = Long.MIN_VALUE; expandedUntil = null }
    private fun reset() { selected = null; holdingRod = false; previewAt = Long.MIN_VALUE; sackEnabled = null; clearIdentity() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        const val WIDGET_ID = "skyglint.fishing.bait.current"
        const val GROUP = "fishing:bait"
        private const val LOW_THRESHOLD = 16L
        private const val LEASE = 3_000L
        private const val INTRO = 2_000L
        private val YELLOW = 0xFFFFD479.toInt()
        private val MUTED_AMBER = 0xFFD3B071.toInt()
        private val RED = 0xFFF28E8E.toInt()
    }
}
