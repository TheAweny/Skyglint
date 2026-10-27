package dev.skyglint.features.mining

import dev.skyglint.core.*
import java.util.UUID

/** Exact server timers become compact counters; only successfully represented UUIDs are claimed. */
class MiningEventModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.mining.events"
    private data class Observed(val event: MiningEventPatterns.Event, val signature: String, val deadline: Long, val interval: Long)
    private val bosses = linkedMapOf<UUID, Observed>()
    private val endedSignatures = mutableMapOf<UUID, String>()
    private var bossesAt = Long.MIN_VALUE
    private var tab: Observed? = null
    private var tabAt = Long.MIN_VALUE
    private var endedTabSignature: String? = null
    private var contextAt = Long.MIN_VALUE
    private var inMining = false
    private var contextMode: String? = null
    private val announcedUntil = mutableMapOf<MiningEventPatterns.Type, Long>()
    private var occurrence = 0L

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            val mode = signal.mode?.lowercase(java.util.Locale.ROOT)
            val enabled = mode?.let { it in MODES } ?: (signal.location in LOCATIONS)
            if (!enabled) { reset(); return removed() }
            val nextMode = mode ?: signal.location
            if (contextMode != nextMode) reset()
            contextMode = nextMode
            inMining = true; contextAt = now
            return publish(now)
        }
        if (!inMining || !fresh(contextAt, now)) { reset(); return removed() }
        when (signal) {
            is NormalizedSignal.BossSnapshot -> {
                val observedBosses = signal.bosses.take(64)
                val ids = observedBosses.mapTo(mutableSetOf()) { it.id }
                bosses.keys.retainAll(ids); endedSignatures.keys.retainAll(ids)
                observedBosses.forEach { boss ->
                    val event = MiningEventPatterns.parseBoss(boss.title)
                    val signature = MiningEventPatterns.signature(boss.title)
                    if (event == null || endedSignatures[boss.id] == signature) { bosses.remove(boss.id); return@forEach }
                    endedSignatures.remove(boss.id)
                    val prior = bosses[boss.id]
                    bosses[boss.id] = synchronize(prior, event, signature, now)
                }
                bossesAt = now
            }
            is NormalizedSignal.TabSnapshot -> {
                val event = MiningEventPatterns.parseTab(signal.lines)
                if (event == null) {
                    tab = null; tabAt = Long.MIN_VALUE
                    if (MiningEventPatterns.tabNotAnnounced(signal.lines)) endedTabSignature = null
                }
                else {
                    val signature = "${event.type.id}:${event.remainingMillis}"
                    if (signature != endedTabSignature) {
                        endedTabSignature = null
                        val prior = tab
                        tab = synchronize(prior, event, signature, now)
                        tabAt = now
                    }
                }
            }
            is NormalizedSignal.Chat -> {
                val ended = MiningEventPatterns.endedType(signal.text) ?: return emptyList()
                announcedUntil.remove(ended)
                if (tab?.event?.type == ended) { endedTabSignature = tab?.signature; tab = null; tabAt = Long.MIN_VALUE }
                bosses.filterValues { it.event.type == ended }.forEach { (id, observation) ->
                    endedSignatures[id] = observation.signature
                }
                bosses.entries.removeIf { it.value.event.type == ended }
            }
            else -> return emptyList()
        }
        return publish(now)
    }

    private fun publish(now: Long): List<CoreEvent> {
        val freshBosses = if (fresh(bossesAt, now)) bosses.filterValues { now < it.deadline } else emptyMap()
        val byType = freshBosses.values.groupBy { it.event.type }.mapValues { (_, observations) -> observations.minBy { it.deadline } }.toMutableMap()
        tab?.takeIf { fresh(tabAt, now) && now < it.deadline && it.event.type !in byType }?.let { byType[it.event.type] = it }
        if (byType.isEmpty()) return removed()
        val metrics = byType.entries.sortedBy { it.key.ordinal }.map { (type, observation) ->
            val seconds = (observation.deadline - now + 999) / 1_000
            val timer = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
            WidgetMetric("event:${type.id}", type.title, "${type.shortLabel} · $timer", type.icon, 80,
                detail = observation.event.location,
                countdown = CountdownSnapshot(observation.deadline, observation.interval.coerceAtLeast(1)),
                replacedBossIds = freshBosses.filterValues { it.event.type == type }.keys.toSet())
        }
        val widget = CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Mining events", priority = 45,
            expiresAt = minOf(now + LEASE, byType.values.minOf { it.deadline }),
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics),
            replacedBossIds = freshBosses.keys.toSet()))
        return buildList {
            add(widget)
            byType.forEach { (type, observation) ->
                if (announcedUntil[type]?.let { it > now } == true) {
                    announcedUntil[type] = observation.deadline
                    return@forEach
                }
                announcedUntil[type] = observation.deadline
                add(CoreEvent.NoticeObserved(NoticeRequest("$id:intro:${type.id}:${occurrence++}", type.title,
                    priority = 40, freshnessMillis = LEASE,
                    item = ItemVisualHint(vanillaItemId = type.icon),
                    countdown = CountdownSnapshot(observation.deadline, observation.interval),
                    target = NoticeMetricTarget(WIDGET_ID, "event:${type.id}"))))
            }
        }
    }

    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    /** Normal one-second server updates validate the same clock, without rebuilding presentation. */
    private fun synchronize(prior: Observed?, event: MiningEventPatterns.Event, signature: String, now: Long): Observed {
        if (prior == null || prior.event.type != event.type)
            return Observed(event, signature, now + event.remainingMillis, interval(event))
        if (prior.signature == signature) return prior
        val serverDeadline = now + event.remainingMillis
        val drift = serverDeadline - prior.deadline
        return Observed(event, signature,
            if (kotlin.math.abs(drift) <= 1_500) prior.deadline else serverDeadline,
            maxOf(prior.interval, interval(event)))
    }
    private fun interval(event: MiningEventPatterns.Event) = maxOf(1L, event.remainingMillis,
        TemporaryEventCatalog.byId(event.type.id)?.durationMillis ?: 0L)
    private fun fresh(at: Long, now: Long): Boolean = at != Long.MIN_VALUE && now >= at && now - at < LEASE
    private fun reset() { bosses.clear(); endedSignatures.clear(); bossesAt = Long.MIN_VALUE; tab = null; tabAt = Long.MIN_VALUE; endedTabSignature = null; contextAt = Long.MIN_VALUE; inMining = false; contextMode = null; announcedUntil.clear() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
    companion object {
        const val WIDGET_ID = "skyglint.mining.events.current"
        private const val LEASE = 5_000L
        private val MODES = setOf("mining_3", "crystal_hollows")
        private val LOCATIONS = setOf("Dwarven Mines", "The Dwarven Mines", "Crystal Hollows", "The Crystal Hollows")
    }
}
