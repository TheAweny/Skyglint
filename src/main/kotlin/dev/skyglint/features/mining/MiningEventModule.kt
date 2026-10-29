package dev.skyglint.features.mining

import dev.skyglint.core.*
import java.util.UUID

/** Exact server timers become compact counters; only successfully represented UUIDs are claimed. */
class MiningEventModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.mining.events"
    private data class Observed(val event: MiningEventPatterns.Event, val signature: String, val deadline: Long, val interval: Long, val observedAt: Long)
    private val bosses = linkedMapOf<UUID, Observed>()
    private data class Finished(val type: MiningEventPatterns.Type, val deadline: Long, val signature: String)
    private data class Ending(val until: Long)
    private val endedSignatures = mutableMapOf<UUID, Finished>()
    private var currentBossTypes = emptyMap<UUID, MiningEventPatterns.Type>()
    private val endings = mutableMapOf<MiningEventPatterns.Type, Ending>()
    private var bossesAt = Long.MIN_VALUE
    private var tab: Observed? = null
    private var tabAt = Long.MIN_VALUE
    private var endedTabSignature: Finished? = null
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
                currentBossTypes = observedBosses.mapNotNull { boss ->
                    MiningEventPatterns.parseBoss(boss.title)?.let { boss.id to it.type }
                }.toMap()
                observedBosses.forEach { boss ->
                    val event = MiningEventPatterns.parseBoss(boss.title)
                    // Hypixel multiplexes an Objective and an event on the SAME UUID.
                    // Keep the observed clock briefly; claim only the currently recognized event title.
                    if (event == null) return@forEach
                    val signature = MiningEventPatterns.signature(boss.title)
                    val finished = endedSignatures[boss.id]
                    if (finished != null && replay(finished, event, signature, now)) return@forEach
                    if (finished == null && event.remainingMillis > 0 && endings[event.type]?.let { now < it.until } == true)
                        return@forEach // A first delayed alternate source cannot undo confirmed completion.
                    endedSignatures.remove(boss.id)
                    if (event.remainingMillis == 0L) {
                        finish(event.type, now)
                        endedSignatures[boss.id] = Finished(event.type, now, signature)
                        return@forEach
                    }
                    endings.remove(event.type)
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
                    val delayedAlternate = endedTabSignature == null && event.remainingMillis > 0 &&
                        endings[event.type]?.let { now < it.until } == true
                    if (!delayedAlternate && endedTabSignature?.let { replay(it, event, signature, now) } != true) {
                        endedTabSignature = null
                        if (event.remainingMillis == 0L) {
                            finish(event.type, now)
                            endedTabSignature = Finished(event.type, now, signature)
                        } else {
                            endings.remove(event.type)
                            tab = synchronize(tab, event, signature, now)
                            tabAt = now
                        }
                    }
                }
            }
            is NormalizedSignal.Chat -> {
                val ended = MiningEventPatterns.endedType(signal.text) ?: return emptyList()
                // Only a current observed event can be completed by a matching system message.
                if (bosses.values.any { it.event.type == ended && usable(it, now) } ||
                    tab?.let { it.event.type == ended && fresh(tabAt, now) && usable(it, now) } == true || endings.containsKey(ended))
                    finish(ended, now)
            }
            else -> return emptyList()
        }
        return publish(now)
    }

    private fun publish(now: Long): List<CoreEvent> {
        endings.entries.removeIf { now >= it.value.until }
        val freshBosses = bosses.filterValues { usable(it, now) }
        val byType = freshBosses.values.groupBy { it.event.type }.mapValues { (_, observations) -> observations.minBy { it.deadline } }.toMutableMap()
        tab?.takeIf { fresh(tabAt, now) && now < it.deadline + LEASE && it.event.type !in byType }?.let { byType[it.event.type] = it }
        val types = (byType.keys + endings.keys).sortedBy { it.ordinal }
        if (types.isEmpty()) return removed()
        fun claims(type: MiningEventPatterns.Type) = if (fresh(bossesAt, now))
            currentBossTypes.filterValues { it == type }.keys.toSet() else emptySet()
        val metrics = types.map { type ->
            val ending = endings[type]
            if (ending != null) WidgetMetric("event:${type.id}",
                "${type.title} ended", "", type.icon, 80,
                detail = type.title, replacedBossIds = claims(type), expandedUntil = ending.until)
            else {
                val observation = byType.getValue(type)
                val countdown = CountdownSnapshot(observation.deadline, observation.interval.coerceAtLeast(1))
                WidgetMetric("event:${type.id}", type.title, "${type.shortLabel} · ${countdown.caption(now)}", type.icon, 80,
                    detail = observation.event.location, countdown = countdown, replacedBossIds = claims(type))
            }
        }
        val expiry = (byType.values.map { minOf(it.observedAt + LEASE, it.deadline + LEASE) } + endings.values.map { it.until }).min()
        val widget = CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Mining events", priority = 45,
            expiresAt = expiry,
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics),
            replacedBossIds = metrics.flatMapTo(mutableSetOf()) { it.replacedBossIds }))
        return buildList {
            add(widget)
            byType.forEach { (type, observation) ->
                if (observation.deadline <= now || type in endings) return@forEach
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
            return Observed(event, signature, now + event.remainingMillis, interval(event), now)
        if (prior.signature == signature) return prior.copy(observedAt = now)
        val serverDeadline = now + event.remainingMillis
        val drift = serverDeadline - prior.deadline
        return Observed(event, signature,
            if (kotlin.math.abs(drift) <= 1_500) prior.deadline else serverDeadline,
            maxOf(prior.interval, interval(event)), now)
    }
    private fun replay(finished: Finished, event: MiningEventPatterns.Event, signature: String, now: Long) =
        finished.type == event.type && (event.remainingMillis == 0L || finished.signature == signature ||
            now + event.remainingMillis <= finished.deadline + 1_500)

    /** A zero server timer or explicit end message completes presentation, never mere source loss. */
    private fun finish(type: MiningEventPatterns.Type, now: Long) {
        endings.putIfAbsent(type, Ending(now + END_PRESENTATION))
        bosses.filterValues { it.event.type == type }.forEach { (id, observation) ->
            endedSignatures[id] = Finished(type, observation.deadline, observation.signature)
        }
        bosses.entries.removeIf { it.value.event.type == type }
        tab?.takeIf { it.event.type == type }?.let { observation ->
            endedTabSignature = Finished(type, observation.deadline, observation.signature)
            tab = null; tabAt = Long.MIN_VALUE
        }
        announcedUntil.remove(type)
    }
    private fun usable(observation: Observed, now: Long) = fresh(observation.observedAt, now) && now < observation.deadline + LEASE
    private fun interval(event: MiningEventPatterns.Event) = maxOf(1L, event.remainingMillis,
        TemporaryEventCatalog.byId(event.type.id)?.durationMillis ?: 0L)
    private fun fresh(at: Long, now: Long): Boolean = at != Long.MIN_VALUE && now >= at && now - at < LEASE
    private fun reset() { bosses.clear(); endedSignatures.clear(); currentBossTypes = emptyMap(); endings.clear(); bossesAt = Long.MIN_VALUE; tab = null; tabAt = Long.MIN_VALUE; endedTabSignature = null; contextAt = Long.MIN_VALUE; inMining = false; contextMode = null; announcedUntil.clear() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
    companion object {
        const val WIDGET_ID = "skyglint.mining.events.current"
        private const val LEASE = 5_000L
        private const val END_PRESENTATION = 2_400L
        private val MODES = setOf("mining_3", "crystal_hollows")
        private val LOCATIONS = setOf("Dwarven Mines", "The Dwarven Mines", "Crystal Hollows", "The Crystal Hollows")
    }
}
