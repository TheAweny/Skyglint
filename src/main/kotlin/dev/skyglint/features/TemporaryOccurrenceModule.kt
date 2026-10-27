package dev.skyglint.features

import dev.skyglint.core.*
import java.util.Locale

/** Explicit system announcements only; no inferred event duration, respawn timer or entity scan. */
class TemporaryOccurrenceModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.temporary_occurrences"
    private var contextAt = Long.MIN_VALUE
    private var mode: String? = null
    private var location: String? = null
    private var sequence = 0L
    private val recent = linkedMapOf<String, Long>()

    // SkyHanni 893cc826: TheGreatSpook.primalFearSpawnPattern and WinterApi.reindrakeSpawnPattern.
    private val reindrake = Regex("WOAH! .{1,100} summoned (a Reindrake|TWO Reindrakes) from the depths!")

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            val nextMode = signal.mode?.lowercase(Locale.ROOT)
            if (mode != nextMode) recent.clear()
            mode = nextMode
            location = signal.location
            contextAt = now
            return emptyList()
        }
        if (signal !is NormalizedSignal.Chat || contextAt == Long.MIN_VALUE || now < contextAt || now - contextAt >= 5_000)
            return emptyList()
        if (signal.text.length > 256 || signal.text.any(Char::isISOControl)) return emptyList()
        val clean = SkyBlockParser.clean(signal.text)
        val notice = when {
            clean == "FEAR. A Primal Fear has been summoned!" -> {
                // The exact server announcement confirms this occurrence itself, even without a seasonal forecast.
                Triple("primal_fear", "Primal Fear summoned!", "minecraft:carved_pumpkin")
            }
            mode == "winter" || (mode == null && location == "Jerry's Workshop") -> {
                val match = reindrake.matchEntire(clean) ?: return emptyList()
                Triple("reindrake", if (match.groupValues[1] == "TWO Reindrakes") "2 Reindrakes summoned!" else "Reindrake summoned!",
                    "minecraft:snowball")
            }
            else -> return emptyList()
        }
        recent.entries.removeIf { now - it.value >= DEDUPE_MILLIS || now < it.value }
        if (clean in recent) return emptyList()
        recent[clean] = now
        while (recent.size > 32) recent.remove(recent.keys.first())
        return listOf(CoreEvent.NoticeObserved(NoticeRequest("skyglint.occurrence.${notice.first}:${sequence++}", notice.second,
            priority = 50, durationMillis = 4_000, freshnessMillis = 5_000,
            item = ItemVisualHint(vanillaItemId = notice.third))))
    }

    private fun reset() { contextAt = Long.MIN_VALUE; mode = null; location = null; sequence = 0; recent.clear() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
    companion object { private const val DEDUPE_MILLIS = 5_000L }
}
