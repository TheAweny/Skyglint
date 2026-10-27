package dev.skyglint.features.mining

import dev.skyglint.core.*
import java.util.Locale

/** Short occurrence notices; no mob/world scans, speculative timers, or persistent dashboard. */
class RandomMiningEventModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.mining.random_events"
    private var mode: String? = null
    private var contextAt = Long.MIN_VALUE
    private var sequence = 0L
    private val recent = linkedMapOf<RandomMiningEventPatterns.Observation, Long>()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            val next = signal.mode?.lowercase(Locale.ROOT)
            if (next != mode) reset()
            mode = next?.takeIf { it in MODES }
            contextAt = now
            return emptyList()
        }
        if (signal !is NormalizedSignal.Chat || contextAt == Long.MIN_VALUE || now < contextAt || now - contextAt >= CONTEXT_LEASE)
            return emptyList()
        val observation = RandomMiningEventPatterns.parse(signal.text) ?: return emptyList()
        if (mode !in observation.type.modes) return emptyList()
        recent.entries.removeIf { now < it.value || now - it.value >= DEDUPE_WINDOW }
        if (observation in recent) return emptyList()
        recent[observation] = now
        if (recent.size > 32) recent.remove(recent.keys.first())
        val type = observation.type
        return listOf(CoreEvent.NoticeObserved(NoticeRequest(
            "$id:${type.id}:${++sequence}", type.title, detail = observation.location,
            priority = 55, durationMillis = 4_000, freshnessMillis = 8_000,
            item = ItemVisualHint(vanillaItemId = type.icon),
        )))
    }

    private fun reset() { mode = null; contextAt = Long.MIN_VALUE; recent.clear() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        private const val CONTEXT_LEASE = 5_000L
        private const val DEDUPE_WINDOW = 2_000L
        private val MODES = setOf("mining_3", "crystal_hollows", "mineshaft")
    }
}
