package dev.skyglint.features.winter

import dev.skyglint.core.*
import java.util.Locale
import kotlin.math.abs

/** Winter defense is active only when the server sidebar supplies Wave or Next Wave evidence. */
class JerryDefenseModule(private val clock: () -> Long) : SignalModule {
    override val id = WIDGET_ID
    private var winter = false
    private var contextAt = Long.MIN_VALUE
    private var sidebarAt = Long.MIN_VALUE
    private var observed: JerryDefensePatterns.Sidebar? = null
    private var nextWaveDeadline: Long? = null
    private var published = false
    private var bossAt = Long.MIN_VALUE
    private var giftBar: BossBarState? = null

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.ContextSnapshot -> {
                if (signal.mode?.lowercase(Locale.ROOT) != "winter") return removeAndReset()
                winter = true
                contextAt = now
            }
            is NormalizedSignal.SidebarSnapshot -> {
                if (!winter || !fresh(contextAt, now)) return removeAndReset()
                val next = JerryDefensePatterns.sidebar(signal.lines)
                if (!next.active) { giftBar = null; bossAt = Long.MIN_VALUE }
                val previous = observed
                val remaining = next.nextWaveInMillis
                val prior = nextWaveDeadline
                nextWaveDeadline = if (remaining == null) null else {
                    val proposed = now + remaining
                    if (previous?.nextWaveInMillis == remaining && prior != null && previous.wave == next.wave) prior
                    else if (prior != null && previous?.wave == next.wave && abs(proposed - prior) <= 1_500) prior
                    else proposed
                }
                observed = next
                sidebarAt = now
            }
            is NormalizedSignal.BossSnapshot -> {
                if (!winter || !fresh(contextAt, now)) return removeAndReset()
                // Ambiguous duplicate point bars stay with the ordinary UUID-preserving renderer.
                giftBar = signal.bosses.filter { JerryGiftPilePatterns.points(it.formattedTitle) != null }.singleOrNull()
                bossAt = now
            }
            else -> return if (!fresh(contextAt, now) || !fresh(sidebarAt, now)) publish(now) else emptyList()
        }
        return publish(now)
    }

    private fun publish(now: Long): List<CoreEvent> {
        val state = observed?.takeIf { winter && fresh(contextAt, now) && fresh(sidebarAt, now) && it.active }
        if (state == null) {
            if (!fresh(contextAt, now) || !fresh(sidebarAt, now)) {
                observed = null; sidebarAt = Long.MIN_VALUE; nextWaveDeadline = null
                if (published) { giftBar = null; bossAt = Long.MIN_VALUE }
            }
            return removePublished()
        }
        val bar = giftBar?.takeIf { fresh(bossAt, now, BOSS_LEASE) }
        val points = bar?.let { JerryGiftPilePatterns.points(it.formattedTitle) }.orEmpty()
        val expiry = minOf(contextAt + LEASE, sidebarAt + LEASE, if (bar == null) Long.MAX_VALUE else bossAt + BOSS_LEASE)
        val metrics = buildList {
            state.cubesRemaining?.let { add(WidgetMetric("cubes_remaining", "Magma cubes left", it.toString(),
                "minecraft:magma_cream", priority = 75, group = GROUP)) }
            if (state.nextWaveSoon) add(WidgetMetric("next_wave", "Next wave", "Soon!", "minecraft:clock",
                priority = 60, group = GROUP))
            else nextWaveDeadline?.let { deadline ->
                // The observed countdown describes the next wave, never a complete defense lifetime.
                val countdown = CountdownSnapshot(deadline, state.nextWaveInMillis!!.coerceAtLeast(1))
                add(WidgetMetric("next_wave", "Next wave", "", "minecraft:clock", priority = 60,
                    countdown = countdown, format = WidgetMetricFormat.REMAINING_TIME))
            }
        }
        published = true
        return listOf(
            CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Jerry Defense", state.wave?.let { "Wave $it" } ?: "Next wave",
                progress = bar?.progress?.takeIf { it.isFinite() && it in 0f..1f }?.let { ProgressSnapshot(null, null, it.toDouble()) },
                priority = 70, expiresAt = expiry, presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:snowball",
                    points.map { point -> WidgetMetric("gift_pile:${point.letter}", point.letter,
                        if (point.destroyed) "destroyed" else "present", referenceOnly = true, accent = point.color) }),
                replacedBossIds = bar?.let { setOf(it.id) }.orEmpty())),
            if (metrics.isNotEmpty()) CoreEvent.WidgetObserved(ModuleWidget(COUNTERS_ID, "Jerry Defense", expiresAt = expiry,
                presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics)))
            else CoreEvent.WidgetRemoved(COUNTERS_ID),
        )
    }

    private fun fresh(at: Long, now: Long, lease: Long = LEASE) = at != Long.MIN_VALUE && now >= at && now - at < lease
    private fun removePublished(): List<CoreEvent> {
        if (!published) return emptyList()
        published = false
        return listOf(CoreEvent.WidgetRemoved(WIDGET_ID), CoreEvent.WidgetRemoved(COUNTERS_ID))
    }
    private fun removeAndReset(): List<CoreEvent> { val events = removePublished(); reset(); return events }
    private fun reset() { winter = false; contextAt = Long.MIN_VALUE; sidebarAt = Long.MIN_VALUE; observed = null; nextWaveDeadline = null; published = false; giftBar = null; bossAt = Long.MIN_VALUE }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
    companion object {
        const val WIDGET_ID = "skyglint.winter.defense"
        const val COUNTERS_ID = "$WIDGET_ID.counters"
        const val GROUP = "winter:defense"
        private const val LEASE = 5_000L
        private const val BOSS_LEASE = 3_000L
    }
}
