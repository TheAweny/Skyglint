package dev.skyglint.features.elections

import dev.skyglint.core.*
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

/** Hub subarea context only. No HTTP, NPC interaction, vote action, or inferred minister selection. */
class ElectionModule(private val clock: () -> Long, settleMillis: Long = 500, private val sourceWaitMillis: Long = 12_000) : SignalModule {
    override val id = "skyglint.elections"
    private var inArea = false
    private var contextAt = Long.MIN_VALUE
    private var observed: ElectionPatterns.Observation? = null
    private var observedAt = Long.MIN_VALUE
    private var api: ElectionApiPayload.Payload? = null
    private var apiAt = Long.MIN_VALUE
    private var sidebarYear: Int? = null
    private var sidebarAt = Long.MIN_VALUE
    private var closedAt = Long.MIN_VALUE
    private var enteredAt = Long.MIN_VALUE
    private var publicApiAllowed = true
    private data class PoolKey(val source: String, val year: Int?, val names: List<String>)
    private val ready = StableDataGate<PoolKey>(settleMillis)
    private var shown: ModuleWidget? = null
    private var shownKey: PoolKey? = null

    init { require(sourceWaitMillis >= 0) }

    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.ContextSnapshot -> {
                val wasActive = active(now)
                val location = signal.location?.let(SkyBlockParser::clean)
                inArea = signal.mode?.lowercase(Locale.ROOT) == "hub" && location in AREAS
                contextAt = now
                if (!inArea) { enteredAt = Long.MIN_VALUE; ready.clear(); shown = null; return removed() }
                if (!wasActive) { enteredAt = now; ready.clear(); shown = null }
            }
            is NormalizedSignal.ElectionSourceAvailability -> {
                if (publicApiAllowed != signal.publicApiAllowed) {
                    publicApiAllowed = signal.publicApiAllowed
                    ready.clear(); shown = null; shownKey = null
                    api = null; apiAt = Long.MIN_VALUE; closedAt = Long.MIN_VALUE
                    observed = null; observedAt = Long.MIN_VALUE; sidebarYear = null; sidebarAt = Long.MIN_VALUE
                    if (inArea) enteredAt = now
                }
                if (!active(now)) return removed()
            }
            is NormalizedSignal.TabSnapshot -> {
                if (!active(now)) return removed()
                observed = ElectionPatterns.tab(signal.lines)
                observedAt = now
            }
            is NormalizedSignal.SidebarSnapshot -> {
                if (!active(now)) return removed()
                ElectionPatterns.sidebarYear(signal.lines)?.let { year -> sidebarYear = year; sidebarAt = now }
            }
            is NormalizedSignal.ElectionSnapshot -> {
                if (!active(now)) return removed()
                if (publicApiAllowed) {
                    api = if (signal.ageMillis in 0..API_LEASE) ElectionApiPayload.validated(signal.year, signal.candidates) else null
                    apiAt = if (api != null) now - signal.ageMillis else Long.MIN_VALUE
                    if (api != null) closedAt = Long.MIN_VALUE
                }
            }
            is NormalizedSignal.ElectionClosed -> {
                if (!active(now)) return removed()
                if (publicApiAllowed && signal.ageMillis in 0 until API_LEASE) {
                    closedAt = now - signal.ageMillis
                    api = null; apiAt = Long.MIN_VALUE; observed = null; observedAt = Long.MIN_VALUE
                }
            }
            else -> return emptyList()
        }
        if (!active(now)) return removed()
        if (closedAt != Long.MIN_VALUE && now - closedAt in 0 until API_LEASE) {
            ready.clear(); shown = null
            return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Election", "Closed", priority = 65,
                expiresAt = minOf(contextAt + CONTEXT_LEASE, closedAt + API_LEASE),
                presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:lectern"))))
        }
        val source = observed?.takeIf { observedAt != Long.MIN_VALUE && now - observedAt in 0 until VOTE_LEASE }
        val knownYear = sidebarYear.takeIf { sidebarAt != Long.MIN_VALUE && now - sidebarAt in 0 until API_LEASE }
        val apiSource = api?.takeIf { publicApiAllowed && apiAt != Long.MIN_VALUE && now - apiAt in 0 until API_LEASE &&
            (knownYear == null || knownYear == it.year + 1) && source?.closed != true }
        if (apiSource != null) {
            val key = PoolKey("api", apiSource.year + 1, apiSource.candidates.map { it.name.lowercase(Locale.ROOT) }.sorted())
            return if (ready.ready(key, now)) commit(apiWidget(apiSource), key) else loading(now, retainShown = true, key = key)
        }
        if (source?.closed == true) {
            ready.clear(); shown = null
            return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Election", "Closed", priority = 65,
                expiresAt = minOf(contextAt + CONTEXT_LEASE, observedAt + VOTE_LEASE),
                presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:lectern"))))
        }
        val candidates = source?.candidates.orEmpty()
        if (candidates.isEmpty() || (publicApiAllowed && enteredAt != Long.MIN_VALUE && now - enteredAt < sourceWaitMillis)) {
            ready.clear()
            shown = null
            return loading()
        }
        val key = PoolKey("tab", knownYear, candidates.map { it.name.lowercase(Locale.ROOT) }.sorted())
        if (!ready.ready(key, now)) return loading(now, retainShown = true, key = key)
        val ranked = source?.confirmedTopTwo == true
        val tied = ranked && candidates.size >= 2 && candidates[0].percent == candidates[1].percent
        val metrics = candidates.mapIndexed { index, candidate ->
            val confirmed = ranked && index < 2
            val equal = candidates.count { it.percent == candidate.percent } > 1
            WidgetMetric("candidate:${candidate.name.lowercase(Locale.ROOT)}",
                when { confirmed && equal -> "Tie · ${candidate.name}"; confirmed -> "${index + 1}. ${candidate.name}"
                    else -> "Observed · ${candidate.name}" },
                "${candidate.name} ${candidate.percent}%", candidateIcon(candidate.name), 70 - index)
        }
        val detail = when {
            source?.closed == true -> "Closed"
            tied -> "Voting · Tie"
            metrics.isNotEmpty() -> if (ranked) "Voting" else "Observed"
            else -> null
        }
        val expiry = minOf(contextAt + CONTEXT_LEASE, if (source != null) observedAt + VOTE_LEASE else Long.MAX_VALUE)
        return commit(listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Election", detail, priority = 65,
            expiresAt = expiry, presentation = WidgetPresentation(WidgetRole.INLINE, "minecraft:lectern", metrics)))), key)
    }

    private fun apiWidget(source: ElectionApiPayload.Payload): List<CoreEvent> {
        val total = source.candidates.sumOf { it.votes }
        val metrics = source.candidates.mapIndexed { index, candidate ->
            val share = BigDecimal.valueOf(candidate.votes).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toPlainString()
            val tied = source.candidates.count { it.votes == candidate.votes } > 1
            WidgetMetric("candidate:${candidate.name.lowercase(Locale.ROOT)}",
                if (tied) "Tie · ${candidate.name}" else "${index + 1}. ${candidate.name}",
                "${candidate.name} $share%", candidateIcon(candidate.name), 70 - index,
                detail = "Votes: ${candidate.votes} · Total: $total")
        }
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Election", "Voting", priority = 65,
            expiresAt = minOf(contextAt + CONTEXT_LEASE, apiAt + API_LEASE),
            presentation = WidgetPresentation(WidgetRole.INLINE, "minecraft:lectern", metrics))))
    }

    private fun active(now: Long): Boolean = inArea && contextAt != Long.MIN_VALUE && now >= contextAt && now - contextAt < CONTEXT_LEASE
    /** Public name reference only; the Fabric boundary owns head assets and native profile caching. */
    private fun candidateIcon(name: String) = "@candidate:${name.lowercase(Locale.ROOT)}"
    /** Context-only marker reserves no visible caption or speculative candidate geometry. */
    private fun loading(now: Long = clock(), retainShown: Boolean = false, key: PoolKey? = null): List<CoreEvent> {
        val compatibleYear = shownKey?.year == null || key?.year == null || shownKey?.year == key?.year
        shown?.takeIf { retainShown && compatibleYear && it.expiresAt?.let { expiry -> now < expiry } != false }?.let {
            return listOf(CoreEvent.WidgetObserved(it))
        }
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Election", priority = 65,
            expiresAt = contextAt + CONTEXT_LEASE, presentation = WidgetPresentation(WidgetRole.COUNTERS))))
    }
    private fun commit(events: List<CoreEvent>, key: PoolKey): List<CoreEvent> {
        shown = events.filterIsInstance<CoreEvent.WidgetObserved>().single().widget
        shownKey = key
        return events
    }
    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun reset() {
        inArea = false; contextAt = Long.MIN_VALUE; observed = null; observedAt = Long.MIN_VALUE
        api = null; apiAt = Long.MIN_VALUE; sidebarYear = null; sidebarAt = Long.MIN_VALUE
        closedAt = Long.MIN_VALUE
        enteredAt = Long.MIN_VALUE; publicApiAllowed = true; ready.clear(); shown = null; shownKey = null
    }

    companion object {
        const val WIDGET_ID = "skyglint.elections.current"
        private val AREAS = setOf("Community Center", "Election Room", "Election Booth")
        private const val CONTEXT_LEASE = 5_000L
        private const val VOTE_LEASE = 30_000L
        private const val API_LEASE = 360_000L
    }
}
