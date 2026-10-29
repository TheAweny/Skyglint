package dev.skyglint.features.winter

import dev.skyglint.core.SkyBlockParser
import java.math.BigDecimal
import java.util.Locale

/** Exact observed personal result lines. No state, outcome inference, reward emission or gameplay mutation. */
object JerryDefenseResultPatterns {
    sealed interface Field {
        data class Header(val wave: Int) : Field
        data class Stat(val key: String, val label: String, val value: String) : Field
        data class Gift(val name: String, val amount: Long, val rarity: String? = null) : Field
        data object End : Field
    }

    private const val NUMBER = "(?:[1-9][0-9]{0,2}(?:,[0-9]{3}){1,4}|[0-9]{1,13})"
    private const val MAX_STAT = 1_000_000_000_000L
    private val header = Regex("WAVE ([0-9]{1,4}) - YOU WON!")
    private val started = Regex("DEFEND JERRY'S WORKSHOP - WAVE ([0-9]{1,4})")
    private val stat = Regex("(Snowballs Hit|Snowballs Thrown|Total Damage|Magma Cube Damage|Snow Cannonball Hits): ($NUMBER)(.*)")
    private val damageSuffix = Regex("(?: \\(NEW RECORD!\\))?(?: \\(Position #([0-9]{1,7})\\))?")
    private val gift = Regex("(White Gift|Volcanic Rock|Red Gift|Green Gift) x($NUMBER)")
    private val accuracy = Regex("Snowball Accuracy: (N/A|[0-9]{1,3}(?:\\.[0-9]{1,2})?%)")
    private val spaces = Regex(" +")
    private val keys = mapOf("Snowballs Hit" to "snowballs_hit", "Snowballs Thrown" to "snowballs_thrown",
        "Total Damage" to "total_damage", "Magma Cube Damage" to "magma_cube_damage", "Snow Cannonball Hits" to "snow_cannonball_hits")

    fun line(raw: String): Field? {
        val text = clean(raw) ?: return null
        header.matchEntire(text)?.let { match ->
            return wave(match.groupValues[1])?.let { Field.Header(it) }
        }
        if (text == "Collect your Gifts at the Gift piles!") return Field.End
        gift.matchEntire(text)?.let { match ->
            val amount = number(match.groupValues[2], 1_000_000_000) ?: return null
            val name = match.groupValues[1]
            val itemColor = Regex("§([0-9a-f])(?:§[k-or])*${Regex.escape(name)}(?:§| |$)", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.get(1)?.lowercase()
            val rarity = mapOf("f" to "COMMON", "a" to "UNCOMMON", "9" to "RARE", "5" to "EPIC", "6" to "LEGENDARY", "d" to "MYTHIC")[itemColor]
            return Field.Gift(name, amount, rarity)
        }
        accuracy.matchEntire(text)?.let { match ->
            val value = match.groupValues[1]
            if (value == "N/A") return Field.Stat("snowball_accuracy", "Snowball Accuracy", value)
            val percent = value.removeSuffix("%").toBigDecimalOrNull() ?: return null
            if (percent < BigDecimal.ZERO || percent > BigDecimal(100)) return null
            return Field.Stat("snowball_accuracy", "Snowball Accuracy", percent.stripTrailingZeros().toPlainString() + "%")
        }
        stat.matchEntire(text)?.let { match ->
            val label = match.groupValues[1]
            val amount = number(match.groupValues[2], MAX_STAT) ?: return null
            val suffix = match.groupValues[3]
            val normalizedSuffix = when (label) {
                "Total Damage" -> {
                    val parsed = damageSuffix.matchEntire(suffix) ?: return null
                    val position = parsed.groupValues[1].takeIf { it.isNotEmpty() }
                    if (position != null && position.toIntOrNull()?.takeIf { it in 1..1_000_000 } == null) return null
                    suffix
                }
                "Snow Cannonball Hits" -> suffix.takeIf { it.isEmpty() || it == " (NEW RECORD!)" } ?: return null
                else -> suffix.takeIf(String::isEmpty) ?: return null
            }
            return Field.Stat(keys.getValue(label), label, String.format(Locale.ROOT, "%,d", amount) + normalizedSuffix)
        }
        return null
    }

    fun startedWave(raw: String): Int? = clean(raw)?.let { text ->
        started.matchEntire(text)?.groupValues?.get(1)?.let(::wave)
    }

    private fun wave(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..1_000 }
    private fun number(text: String, maximum: Long): Long? = text.replace(",", "").toLongOrNull()?.takeIf { it in 0..maximum }
    private fun clean(raw: String): String? {
        if (raw.length > 1_024 || raw.any(Char::isISOControl)) return null
        return spaces.replace(SkyBlockParser.clean(raw), " ")
    }
}
