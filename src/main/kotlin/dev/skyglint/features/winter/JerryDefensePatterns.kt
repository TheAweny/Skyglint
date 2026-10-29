package dev.skyglint.features.winter

import dev.skyglint.core.SkyBlockParser

/** Exact sidebar protocol facts from pinned SkyHanni ScoreboardPattern; no chat or boss-title guesses. */
object JerryDefensePatterns {
    data class Sidebar(
        val eventStartsInMillis: Long? = null,
        val wave: Int? = null,
        val nextWaveInMillis: Long? = null,
        val nextWaveSoon: Boolean = false,
        val cubesRemaining: Int? = null,
    ) {
        val active: Boolean get() = wave != null || nextWaveInMillis != null || nextWaveSoon
    }

    private val start = Regex("Event Start: ([0-9:]{1,8})")
    private val wave = Regex("Wave ([0-9]{1,4})")
    private val next = Regex("Next Wave: ([0-9:]{1,8}|Soon!)")
    private val cubes = Regex("Magma Cubes Left: (-?[0-9]{1,7})")

    fun sidebar(raw: List<String>): Sidebar {
        val lines = raw.asSequence().take(100).filter { it.length <= 160 && it.none(Char::isISOControl) }
            .map(SkyBlockParser::clean).toList()
        val nextWave = unique(lines.mapNotNull { next.matchEntire(it)?.groupValues?.get(1) })
        return Sidebar(
            eventStartsInMillis = unique(lines.mapNotNull { start.matchEntire(it)?.groupValues?.get(1) })?.let(::duration),
            wave = unique(lines.mapNotNull { wave.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { n -> n in 1..1_000 } }),
            nextWaveInMillis = nextWave?.takeUnless { it == "Soon!" }?.let(::duration),
            nextWaveSoon = nextWave == "Soon!",
            // The server has been observed to publish negative counts. That is unknown, never zero.
            cubesRemaining = unique(lines.mapNotNull { cubes.matchEntire(it)?.groupValues?.get(1) })?.toIntOrNull()?.takeIf { it in 0..1_000_000 },
        )
    }

    private fun <T> unique(values: List<T>): T? = values.distinct().singleOrNull()
    private fun duration(text: String): Long? {
        val parts = text.split(':')
        if (parts.size !in 1..3 || parts.any { it.isEmpty() || it.length > 3 }) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.drop(1).any { it !in 0..59 }) return null
        val seconds = numbers.fold(0L) { total, n -> total * 60 + n }
        return seconds.takeIf { it in 0..86_400 }?.times(1_000)
    }
}
