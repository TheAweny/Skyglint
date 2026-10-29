package dev.skyglint.features.winter

import dev.skyglint.core.*
import java.util.Locale
import java.util.UUID

/**
 * A server-owned Reindrake bar is presence, not proof of a kill, loot, elapsed lifetime or hits.
 * Summon announcements remain owned by TemporaryOccurrenceModule and never create this widget.
 */
class ReindrakeModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.winter.reindrake"
    private var winter = false
    private var contextAt = Long.MIN_VALUE
    private var bossesAt = Long.MIN_VALUE
    private val observed = linkedMapOf<UUID, BossBarState>()
    private val published = linkedSetOf<UUID>()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            val mode = signal.mode?.lowercase(Locale.ROOT)
            // An explicit other mode wins over a similarly named location. No coordinate fallback.
            if (mode != "winter") return removeAndReset()
            winter = true
            contextAt = now
            return publish(now)
        }
        if (!winter || !fresh(contextAt, now, CONTEXT_LEASE)) return removeAndReset()
        if (signal is NormalizedSignal.BossSnapshot) {
            // This is a complete copied UUID map. Absence or an unrecognized renamed title retires
            // only our former widget; the generic boss adapter still owns the original server bar.
            observed.clear()
            signal.bosses.asSequence().filter(::recognized).take(MAX_REINDRAKES).forEach { observed[it.id] = it }
            bossesAt = now
            return publish(now)
        }
        // Other signals can expire a source, but cannot make its old snapshot fresh again.
        return if (!fresh(bossesAt, now, BOSS_LEASE)) publish(now) else emptyList()
    }

    private fun publish(now: Long): List<CoreEvent> {
        val visible = observed.takeIf { fresh(bossesAt, now, BOSS_LEASE) && fresh(contextAt, now, CONTEXT_LEASE) }
            ?: emptyMap()
        val result = mutableListOf<CoreEvent>()
        (published - visible.keys).forEach { result += CoreEvent.WidgetRemoved(widgetId(it)) }
        visible.forEach { (uuid, bar) ->
            // No verified health/count title grammar is available in the pinned upstream sources.
            // Keep only the actual packet fill, explicitly unlabelled as absolute HP or hit count.
            val progress = bar.progress.takeIf { it.isFinite() && it in 0f..1f }
                ?.let { ProgressSnapshot(null, null, it.toDouble()) }
            result += CoreEvent.WidgetObserved(ModuleWidget(
                id = widgetId(uuid), title = "Reindrake",
                progress = progress, priority = 70,
                expiresAt = minOf(bossesAt + BOSS_LEASE, contextAt + CONTEXT_LEASE),
                presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:snowball"),
                replacedBossIds = setOf(uuid),
            ))
        }
        published.clear()
        published += visible.keys
        if (visible.isEmpty()) observed.clear()
        return result
    }

    private fun recognized(bar: BossBarState): Boolean = bar.title.length <= 64 &&
        bar.title.none(Char::isISOControl) && SkyBlockParser.clean(bar.title) == "Reindrake"

    private fun fresh(at: Long, now: Long, lease: Long) = at != Long.MIN_VALUE && now >= at && now - at < lease
    private fun widgetId(uuid: UUID) = "$id:$uuid"
    private fun removeAndReset(): List<CoreEvent> {
        val events = published.map { CoreEvent.WidgetRemoved(widgetId(it)) }
        reset()
        return events
    }
    private fun reset() { winter = false; contextAt = Long.MIN_VALUE; bossesAt = Long.MIN_VALUE; observed.clear(); published.clear() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        private const val CONTEXT_LEASE = 5_000L
        private const val BOSS_LEASE = 3_000L
        private const val MAX_REINDRAKES = 32
    }
}
