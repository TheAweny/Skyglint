package dev.skyglint.features

import dev.skyglint.core.RewardObservation
import dev.skyglint.core.SkyBlockParser
import dev.skyglint.features.fishing.loot.FishingLootPatterns

/** Grammar of observed server messages. Recognition is deliberately separate from notification policy. */
object RewardPatterns {
    private const val NAME = "[\\p{L}\\p{N}][\\p{L}\\p{N} '\\-’&]{0,95}"
    private const val MF = "(?: \\(\\+([0-9]{1,12})%? [✯\\uE01A] Magic Find\\))?"
    private val rare = Regex("RARE DROP! ($NAME)$MF")
    private val pet = Regex("PET DROP! ($NAME)(?: \\(\\+([0-9]{1,12})%? [✯\\uE01A] Magic Find\\)| \\(\\+[0-9]+(?:\\.[0-9]+)?[☘\\uE02B]\\))?")
    private val colors = mapOf('f' to "COMMON", 'a' to "UNCOMMON", '9' to "RARE", '5' to "EPIC",
        '6' to "LEGENDARY", 'd' to "MYTHIC", 'b' to "DIVINE")
    private val fallbackIds = mapOf("Enchanted Book" to "minecraft:enchanted_book")

    fun observe(raw: String): RewardObservation? {
        if (raw.length > 2048 || raw.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        val line = SkyBlockParser.clean(raw)
        if (line.length > 256) return null
        rare.matchEntire(line)?.let { match ->
            val mf = magicFind(match.groupValues[2]) ?: if (match.groupValues[2].isNotEmpty()) return null else null
            val name = match.groupValues[1].trim()
            return RewardObservation(name, "RARE DROP!", "rare_drop", fallbackIds[name], mf)
        }
        pet.matchEntire(line)?.let { match ->
            val mf = magicFind(match.groupValues[2]) ?: if (match.groupValues[2].isNotEmpty()) return null else null
            val name = match.groupValues[1].trim()
            return RewardObservation(name, "PET DROP!", "pet_drop", magicFind = mf,
                rarity = observedRarity(raw, name))
        }
        return FishingLootPatterns.observe(raw)
    }

    private fun magicFind(text: String): Int? = text.toIntOrNull()?.takeIf { it in 0..100_000 }

    /** Colored pet names encode rarity; ordinary item-name color alone does not establish item rarity. */
    private fun observedRarity(raw: String, name: String): String? {
        val exact = Regex("§([0-9a-f])${Regex.escape(name)}(?:§|[!. ]|$)", RegexOption.IGNORE_CASE)
        return exact.find(raw)?.groupValues?.get(1)?.firstOrNull()?.lowercaseChar()?.let(colors::get)
    }
}
