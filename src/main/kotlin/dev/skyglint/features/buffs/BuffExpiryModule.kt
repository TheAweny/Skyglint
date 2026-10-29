package dev.skyglint.features.buffs

import dev.skyglint.core.*
import dev.skyglint.features.buffs.BuffExpiryPatterns.Buff
import dev.skyglint.features.buffs.BuffExpiryPatterns.Status
import java.util.Locale

/** Observed remaining-time warnings and explicit inactive notices; never a local expiry prediction. */
class BuffExpiryModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.buffs.expiry"
    private val statuses = mutableMapOf<Buff, Status>()
    private val consumedAt = mutableMapOf<Buff, Long>()
    private val recentConsumes = mutableMapOf<Buff, Pair<String, Long>>()
    private class WarningCycle(var minimum: Long, var maximum: Long, var lastRemaining: Long, var observedAt: Long,
                               val shown: MutableSet<Int> = mutableSetOf())
    private val warningCycles = mutableMapOf<Buff, WarningCycle>()
    private val consumeBaseline = mutableMapOf<Buff, Long?>()
    private var mode: String? = null
    private var contextAt = Long.MIN_VALUE
    private var sequence = 0L

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            mode = signal.mode?.lowercase(Locale.ROOT)
            contextAt = now
            return emptyList()
        }
        val observations = when (signal) {
            is NormalizedSignal.TabSnapshot -> BuffExpiryPatterns.tab(signal.lines)
            is NormalizedSignal.MenuSnapshot -> BuffExpiryPatterns.menu(signal)
            is NormalizedSignal.Chat -> listOfNotNull(BuffExpiryPatterns.chat(signal.text))
            else -> return emptyList()
        }
        return observations.mapNotNull { observation ->
            val buff = observation.buff
            if (observation.consumed && signal is NormalizedSignal.Chat) {
                val previous = recentConsumes[buff]
                if (previous != null && previous.first == signal.text && now >= previous.second && now - previous.second < 1_500)
                    return@mapNotNull null
                recentConsumes[buff] = signal.text to now
                consumedAt[buff] = now
                consumeBaseline[buff] = warningCycles[buff]?.minimum
            }
            if (observation.status == Status.INACTIVE) {
                // God Potion's play-time can be suspended outside ordinary islands. Neither elapsed
                // wall time nor an effects-disabled context is evidence that its duration expired.
                if (buff == Buff.GOD_POTION && !ordinaryContext(now)) return@mapNotNull null
                // A just-consumed item's old tab/menu can lag behind its explicit consume message.
                if (consumedAt[buff]?.let { now < it || now - it < CONSUME_GRACE } == true) return@mapNotNull null
            }
            val previous = statuses.put(buff, observation.status)
            if (observation.status == Status.ACTIVE) {
                if (observation.consumed || (buff == Buff.GOD_POTION && !ordinaryContext(now))) return@mapNotNull null
                val remaining = observation.remainingMillis ?: return@mapNotNull null
                return@mapNotNull warning(buff, remaining, previous, now)
            }
            if (previous != Status.ACTIVE) return@mapNotNull null
            CoreEvent.NoticeObserved(NoticeRequest(
                id = "$id:${buff.key}:${++sequence}", title = "${buff.title} no longer active",
                priority = 55, durationMillis = 4_000, freshnessMillis = 8_000,
                item = ItemVisualHint(vanillaItemId = buff.icon),
            ))
        }
    }

    private fun warning(buff: Buff, remaining: Long, previous: Status?, now: Long): CoreEvent? {
        var cycle = warningCycles[buff]
        if (consumedAt[buff]?.let { now < it || now - it < CONSUME_GRACE } == true) return null
        if (consumeBaseline.containsKey(buff)) {
            val baseline = consumeBaseline[buff]
            // An exact consumption message arms a refresh, but its old footer must not reset warnings.
            if (baseline != null && (remaining <= baseline || remaining - baseline <= REFILL_MARGIN)) return null
            warningCycles.remove(buff)
            consumeBaseline.remove(buff)
            cycle = null
        }
        if (cycle == null || previous == Status.INACTIVE || (remaining > cycle.maximum && remaining - cycle.maximum > REFILL_MARGIN)) {
            cycle = WarningCycle(remaining, remaining, remaining, now)
            warningCycles[buff] = cycle
        } else {
            // A newly refilled timer must not immediately fall back to the old cached low footer.
            // Elapsed time is only a plausibility bound; it never generates or advances a warning.
            val elapsed = if (now >= cycle.observedAt) now - cycle.observedAt else return null
            val drop = cycle.lastRemaining - remaining
            if (drop > REFILL_MARGIN && drop - REFILL_MARGIN > elapsed) return null
            // Replaying a previously seen higher footer, or ordinary timer jitter, cannot rearm a cycle.
            cycle.minimum = minOf(cycle.minimum, remaining)
            cycle.maximum = maxOf(cycle.maximum, remaining)
            cycle.lastRemaining = remaining
            cycle.observedAt = now
        }
        val eligible = STAGES.filter { remaining <= it * 60_000L && it !in cycle.shown }
        val stage = eligible.minOrNull() ?: return null
        cycle.shown += STAGES.filter { it >= stage }
        return CoreEvent.NoticeObserved(NoticeRequest(
            id = "$id:warning:${buff.key}:$stage:${++sequence}", title = "${buff.title} expiring soon",
            detail = "${DurationText.countdown(remaining)} remaining",
            priority = if (stage == 1) 54 else 45, durationMillis = 4_000, freshnessMillis = 8_000,
            item = ItemVisualHint(vanillaItemId = buff.icon),
        ))
    }

    private fun ordinaryContext(now: Long) = mode in ORDINARY_MODES && contextAt != Long.MIN_VALUE &&
        now >= contextAt && now - contextAt < CONTEXT_LEASE

    private fun reset() {
        statuses.clear(); consumedAt.clear(); recentConsumes.clear(); warningCycles.clear(); consumeBaseline.clear()
        mode = null; contextAt = Long.MIN_VALUE
    }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        private const val CONTEXT_LEASE = 5_000L
        private const val CONSUME_GRACE = 3_000L
        private const val REFILL_MARGIN = 60_000L
        private val STAGES = listOf(30, 10, 1)
        // Official island API modes already used by the project; unknown/dungeon/rift contexts are conservative.
        private val ORDINARY_MODES = setOf("dynamic", "hub", "garden", "winter", "farming_1", "mining_1", "mining_2", "mining_3",
            "crystal_hollows", "fishing_1", "lotus_atoll", "foraging_1", "foraging_2", "foraging_3", "combat_1", "combat_2", "combat_3", "crimson_isle")
    }
}
