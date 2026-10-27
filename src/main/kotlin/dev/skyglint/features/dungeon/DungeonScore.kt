package dev.skyglint.features.dungeon

import dev.skyglint.core.SkyBlockParser

/** Server-observed score only. Clear percentage is a separate value and never a score estimate. */
internal object DungeonScore {
    enum class Confidence { SERVER_OBSERVED, FINAL }
    data class Observation(val score: Int, val rank: String, val confidence: Confidence = Confidence.SERVER_OBSERVED) {
        val final: Boolean get() = confidence == Confidence.FINAL
        val caption: String get() = "$score · $rank"
    }
    private val cleared = Regex("Cleared: ([0-9]{1,3})% \\(([0-9]{1,3})\\)")
    private val teamScore = Regex("Team Score: ([0-9]{1,3}) \\((S\\+|S|A|B|C|D)\\)")

    fun fromSidebar(text: String): Observation? {
        val match = cleared.matchEntire(SkyBlockParser.clean(text)) ?: return null
        match.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
        val score = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..400 } ?: return null
        return Observation(score, rank(score) ?: return null)
    }

    fun fromCompletion(text: String): Observation? {
        if (text.any { it == '\n' || it == '\r' } || text.length > 128) return null
        val match = teamScore.matchEntire(SkyBlockParser.clean(text)) ?: return null
        val score = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..400 } ?: return null
        // Both values are supplied by the server; preserve the explicitly reported final rank.
        return Observation(score, match.groupValues[2], Confidence.FINAL)
    }

    fun rank(score: Int): String? = when {
        score !in 0..400 -> null
        score < 100 -> "D"
        score < 160 -> "C"
        score < 230 -> "B"
        score < 270 -> "A"
        score < 300 -> "S"
        else -> "S+"
    }
}
