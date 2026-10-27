package dev.skyglint.features.fishing.encounters

import dev.skyglint.core.*
import java.util.Locale

/** Exact personal catch announcements only. A catch supplies neither a living entity nor loot. */
class FishingEncounterModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.fishing.encounters"
    private var mode: String? = null
    private var contextAt = Long.MIN_VALUE
    private var doubleHookAt: Long? = null
    private var sequence = 0L
    private val recent = linkedMapOf<String, Long>()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            val nextMode = signal.mode?.lowercase(Locale.ROOT)
            if (nextMode != mode) reset()
            mode = nextMode
            contextAt = now
            return emptyList()
        }
        if (signal !is NormalizedSignal.Chat) return emptyList()
        if (mode !in FISHING_MODES || contextAt == Long.MIN_VALUE || now < contextAt || now - contextAt >= CONTEXT_LEASE) {
            doubleHookAt = null
            return emptyList()
        }
        val hookAt = doubleHookAt
        // Only the immediately following chat line can use this token. An ordinary catch, unknown
        // message or malformed line must not donate its Double Hook to a later rare catch.
        doubleHookAt = null
        val line = FishingEncounterPatterns.cleanLine(signal.text) ?: return emptyList()
        if (FishingEncounterPatterns.doubleHook(line)) {
            doubleHookAt = now
            return emptyList()
        }
        val observed = FishingEncounterPatterns.parseCatch(line) ?: return emptyList()
        recent.entries.removeIf { now < it.value || now - it.value >= DEDUPE_WINDOW }
        if (observed.id in recent) return emptyList()
        recent[observed.id] = now
        while (recent.size > 32) recent.remove(recent.keys.first())
        val doubleHook = hookAt != null && now >= hookAt && now - hookAt <= DOUBLE_HOOK_WINDOW
        val title = "${observed.name} caught" + if (doubleHook) " · Double Hook" else ""
        return listOf(CoreEvent.NoticeObserved(NoticeRequest(
            "$id:${observed.id}:${++sequence}", title,
            priority = 55, durationMillis = 4_000, freshnessMillis = 8_000,
            item = ItemVisualHint(vanillaItemId = "minecraft:fishing_rod"),
        )))
    }

    private fun reset() {
        mode = null
        contextAt = Long.MIN_VALUE
        doubleHookAt = null
        recent.clear()
    }

    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        private const val CONTEXT_LEASE = 5_000L
        private const val DEDUPE_WINDOW = 500L
        private const val DOUBLE_HOOK_WINDOW = 100L
        // Official island API names, pinned SkyHanni IslandType.kt; no location guessing.
        private val FISHING_MODES = setOf("dynamic", "hub", "winter", "farming_1", "mining_3", "crystal_hollows",
            "fishing_1", "lotus_atoll", "foraging_1", "foraging_2", "foraging_3", "combat_1", "crimson_isle")
    }
}
