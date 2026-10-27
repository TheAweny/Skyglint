package dev.skyglint.features

import dev.skyglint.core.*

/** A full server level-up header contains both the skill and the explicit old -> new transition. */
class SkillLevelUpModule : SignalModule {
    override val id = "skyglint.skill_level_up"
    private val header = Regex("SKILL LEVEL UP ([A-Za-z]+) ([0-9IVXLCDM]{1,9})\\s*➜\\s*([0-9IVXLCDM]{1,9})")

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.Chat || signal.text.length > 512 ||
            signal.text.any { it == '\n' || it == '\r' || it == '\u0000' }) return emptyList()
        val match = header.matchEntire(SkyBlockParser.clean(signal.text)) ?: return emptyList()
        val skill = Skill.fromName(match.groupValues[1]) ?: return emptyList()
        val previous = level(match.groupValues[2], allowZero = true) ?: return emptyList()
        val next = level(match.groupValues[3], allowZero = false) ?: return emptyList()
        if (next <= previous) return emptyList()
        return listOf(CoreEvent.SkillLevelUp(skill, next))
    }

    private fun level(token: String, allowZero: Boolean): Int? {
        token.toIntOrNull()?.let { return it.takeIf { value -> value in (if (allowZero) 0 else 1)..1_000 } }
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0
        for (index in token.indices) {
            val current = values[token[index]] ?: return null
            val next = token.getOrNull(index + 1)?.let(values::get) ?: 0
            total += if (current < next) -current else current
        }
        if (total !in 1..1_000) return null
        var remainder = total
        val canonical = buildString {
            for ((value, numeral) in listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C",
                90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")) {
                while (remainder >= value) { append(numeral); remainder -= value }
            }
        }
        return total.takeIf { canonical == token }
    }
}
