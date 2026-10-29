package dev.skyglint.features.winter

import dev.skyglint.core.*
import dev.skyglint.features.winter.JerryDefenseResultPatterns.Field

/** Collect one bounded, explicitly framed server result. Claimable rewards are not inventory receipts. */
class JerryDefenseResultModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.winter.defense.results"
    private data class Result(val wave: Int, val startedAt: Long, val key: String,
                              val stats: MutableMap<String, Field.Stat> = linkedMapOf(),
                              val gifts: MutableMap<String, Field.Gift> = linkedMapOf(), var conflict: Boolean = false)
    private var contextAt = Long.MIN_VALUE
    private var winter = false
    private var pending: Result? = null
    private var attempt = 0L
    private var sequence = 0L
    private var lastStart: Pair<Int, Long>? = null
    private val finished = linkedSetOf<String>()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            winter = signal.mode?.equals("winter", true) == true
            contextAt = now
            if (!winter) pending = null
            return emptyList()
        }
        if (!winter || contextAt == Long.MIN_VALUE || now < contextAt || now - contextAt >= 5_000) {
            pending = null
            return emptyList()
        }
        pending?.takeIf { now < it.startedAt || now - it.startedAt >= 8_000 }?.let { pending = null }
        if (signal !is NormalizedSignal.Chat || signal.text.length > 12_000) return emptyList()
        val lines = signal.text.lines()
        if (lines.size > 24) return emptyList()
        return buildList {
            lines.forEach { raw ->
                JerryDefenseResultPatterns.startedWave(raw)?.let { wave ->
                    val prior = lastStart
                    if (prior == null || prior.first != wave || now < prior.second || now - prior.second >= 5_000) {
                        attempt++
                        pending = null
                        lastStart = wave to now
                    }
                }
                when (val field = JerryDefenseResultPatterns.line(raw)) {
                    is Field.Header -> {
                        val key = "$attempt:${field.wave}"
                        if (key in finished || lastStart?.first?.let { it != field.wave } == true) pending = null
                        else if (pending?.key != key) pending = Result(field.wave, now, key)
                    }
                    is Field.Stat -> pending?.let { result ->
                        val old = result.stats.putIfAbsent(field.key, field)
                        if (old != null && old != field) result.conflict = true
                    }
                    is Field.Gift -> pending?.let { result ->
                        val old = result.gifts.putIfAbsent(field.name, field)
                        if (old != null && old != field) result.conflict = true
                    }
                    Field.End -> pending?.let { result ->
                        pending = null
                        if (!result.conflict && (result.stats.isNotEmpty() || result.gifts.isNotEmpty())) {
                            finished += result.key
                            if (finished.size > 64) finished.remove(finished.first())
                            addAll(events(result))
                        }
                    }
                    null -> Unit
                }
            }
        }
    }

    private fun events(result: Result): List<CoreEvent> = buildList {
        val source = "jerry_defense_claimable:${result.key}:${++sequence}"
        val pages = buildList {
            result.stats["total_damage"]?.let { add(NoticePage("Damage · ${compact(it.value)}", "minecraft:iron_sword")) }
            result.stats["magma_cube_damage"]?.let { add(NoticePage("Cube damage · ${it.value}", "minecraft:magma_cream")) }
            result.stats["snow_cannonball_hits"]?.let { add(NoticePage("Cannonballs · ${compact(it.value)}", "minecraft:snowball")) }
            val hits = result.stats["snowballs_hit"]?.value
            val thrown = result.stats["snowballs_thrown"]?.value
            val accuracy = result.stats["snowball_accuracy"]?.value
            if (hits != null || thrown != null || accuracy != null) add(NoticePage(
                listOfNotNull(if (hits != null && thrown != null) "Snowballs · $hits/$thrown" else
                    hits?.let { "Hits · $it" } ?: thrown?.let { "Thrown · $it" }, accuracy?.let { "Accuracy $it" }).joinToString(" · "),
                "minecraft:snowball"))
        }
        if (pages.isNotEmpty()) add(CoreEvent.NoticeObserved(NoticeRequest(
            id = "skyglint.winter.defense.result:${result.key}:$sequence", title = "Wave ${result.wave} · Results",
            detail = result.stats.values.joinToString(" · ") { "${it.label}: ${it.value}" },
            pages = pages, priority = 40, durationMillis = 1_000L + pages.size * 1_800L,
            freshnessMillis = 60_000,
        )))
        // Existing reward policy and independent per-item overrides still own the display decision.
        result.gifts.values.filter { it.amount > 0 }.forEach { gift ->
            add(CoreEvent.RewardObserved(RewardObservation(gift.name,
                "Jerry Defense · Wave ${result.wave} · Collect at gift piles", source,
                amount = gift.amount, rarity = gift.rarity, delivery = RewardDelivery.CLAIMABLE)))
        }
    }

    private fun compact(value: String) = value.replace(" (NEW RECORD!)", " · Record")
        .replace(Regex(" \\(Position #([0-9]+)\\)"), " · #$1")
    private fun reset() { contextAt = Long.MIN_VALUE; winter = false; pending = null; attempt = 0; lastStart = null; finished.clear() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
}
