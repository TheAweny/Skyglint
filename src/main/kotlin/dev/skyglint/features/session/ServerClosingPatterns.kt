package dev.skyglint.features.session

import dev.skyglint.core.SkyBlockParser

/** Protocol evidence: SkyHanni 893cc826 ServerRestartTitle.kt:22, not its implementation. */
object ServerClosingPatterns {
    data class Observation(val server: String, val remainingMillis: Long)
    private val closing = Regex("Server closing: ([0-9]{1,3}):([0-5][0-9]) ([A-Za-z][A-Za-z0-9]{1,31})")

    fun parse(lines: List<String>): Observation? {
        val candidates = lines.take(64).filter { it.length <= 256 && it.none(Char::isISOControl) }
            .map(SkyBlockParser::clean).filter { it.startsWith("Server closing:") }
        if (candidates.size != 1) return null
        val match = closing.matchEntire(candidates.single()) ?: return null
        val seconds = match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()
        return Observation(match.groupValues[3], seconds * 1_000)
    }
}
