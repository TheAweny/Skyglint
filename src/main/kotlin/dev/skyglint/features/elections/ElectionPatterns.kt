package dev.skyglint.features.elections

import dev.skyglint.core.SkyBlockParser

/** The source-confirmed tab widget exposes numeric shares; sidebar bars alone do not. */
internal object ElectionPatterns {
    data class Candidate(val name: String, val percent: Int)
    data class Observation(val closed: Boolean, val candidates: List<Candidate>, val confirmedTopTwo: Boolean = false)
    private val header = Regex("Election: ([^\\r\\n]{1,64})")
    private val year = Regex("Year ([0-9]{1,6}) Votes")
    private val candidate = Regex("([A-Za-z][A-Za-z -]{0,39}): \\|{1,100} \\(([0-9]{1,3})%\\)")

    fun sidebarYear(lines: List<String>): Int? = lines.take(64).firstNotNullOfOrNull {
        year.matchEntire(SkyBlockParser.clean(it))?.groupValues?.get(1)?.toIntOrNull()?.takeIf { value -> value in 1..100_001 }
    }

    fun tab(lines: List<String>): Observation? {
        val values = lines.take(256).map(SkyBlockParser::clean)
        val index = values.indexOfFirst { header.matches(it) }
        if (index < 0) return null
        val status = header.matchEntire(values[index])!!.groupValues[1]
        if (status == "Over!") return Observation(true, emptyList())
        val candidates = mutableListOf<Candidate>()
        var malformed = false
        for (line in values.drop(index + 1).take(13)) {
            if (line.isEmpty()) break
            val match = candidate.matchEntire(line)
            if (match == null) {
                // A new tab widget ends the block. A malformed vote row invalidates this snapshot.
                if ('|' in line || Regex("[A-Za-z][A-Za-z -]{0,39}: .*%.*").matches(line)) malformed = true
                break
            }
            val share = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..100 }
            if (share == null) { malformed = true; break }
            candidates += Candidate(match.groupValues[1], share)
        }
        if (malformed || candidates.size !in 2..12 || candidates.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet().size != candidates.size)
            return Observation(false, emptyList())
        // Real tab widgets can expose only the first three rows, so a total below 100 is valid.
        // Rounded shares may exceed 100 slightly; a larger excess is inconsistent data.
        val total = candidates.sumOf { it.percent }
        val roundingAllowance = candidates.size / 2.0
        if (total > 100 + roundingAllowance)
            return Observation(false, emptyList())
        val ordered = candidates.sortedWith(compareByDescending<Candidate> { it.percent }.thenBy { it.name })
        // Unseen candidates could own the whole remainder. Claim top-two positions only when
        // even that conservative upper bound cannot reach the second observed share.
        val maximumUnseenShare = (100 - total + roundingAllowance).coerceAtLeast(0.0)
        val minimumSecondShare = ordered[1].percent - 0.5
        return Observation(false, ordered, maximumUnseenShare < minimumSecondShare)
    }
}
