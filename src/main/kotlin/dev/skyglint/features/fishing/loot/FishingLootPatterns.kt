package dev.skyglint.features.fishing.loot

import dev.skyglint.core.FishingCatchQuality
import dev.skyglint.core.FishingLootCategory
import dev.skyglint.core.RewardObservation
import dev.skyglint.core.SkyBlockParser
import dev.skyglint.core.TrophyFishGrade

/**
 * Confirmed personal catch messages only; no inventory difference or nearby item is a catch.
 * Source fixtures and their limits are listed in SOURCE-NOTES.md beside this parser.
 * RewardPatterns owns dispatch so every catch enters RewardObserved exactly once.
 */
object FishingLootPatterns {
    private const val NAME = "[\\p{L}\\p{N}][\\p{L}\\p{N} '\\-’&]{0,95}"
    private const val QUALITY = "(GOOD|GREAT|OUTSTANDING)"
    private const val ICON = "[\\uE025⛃]"
    private const val TREASURE = "$QUALITY CATCH!"
    private val coins = Regex("$ICON $TREASURE You caught ([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{1,15}) Coins!")
    private val pet = Regex("(?:$ICON )?$TREASURE You caught a \\[Lvl 1] ($NAME)!")
    private val shard = Regex("$ICON $TREASURE You caught(?: an?)? ($NAME Shard)(?: x([0-9]{1,9}))?!")
    private val foundItem = Regex("(?:$ICON )?$TREASURE You found an? ($NAME)\\.")
    private val caughtItem = Regex("$ICON $TREASURE You caught an? ($NAME)!")
    private val junk = Regex("(?:$ICON )?$QUALITY JUNK CATCH! You caught an? ($NAME)!")
    private val trophy = Regex("\\uE02A TROPHY FISH! You caught ($NAME) (BRONZE|SILVER|GOLD|DIAMOND)!")
    private val quantitySuffix = Regex(" x-?[0-9]+[!.]$")
    private val colors = mapOf('f' to "COMMON", 'a' to "UNCOMMON", '9' to "RARE", '5' to "EPIC",
        '6' to "LEGENDARY", 'd' to "MYTHIC", 'b' to "DIVINE")

    fun observe(raw: String): RewardObservation? {
        if (raw.length > 2048 || raw.any(Char::isISOControl)) return null
        val line = SkyBlockParser.clean(raw)
        if (line.length > 256) return null
        // A malformed quantity must not fall back to a different item whose name includes that quantity.
        if (quantitySuffix.containsMatchIn(line) && !shard.matches(line)) return null
        trophy.matchEntire(line)?.let {
            val name = it.groupValues[1].trim()
            return RewardObservation(name, "TROPHY FISH!", "fishing_trophy", amount = 1,
                rarity = itemSpanRarity(raw, name), fishingCategory = FishingLootCategory.TROPHY,
                trophyGrade = TrophyFishGrade.valueOf(it.groupValues[2]))
        }
        junk.matchEntire(line)?.let {
            return item(raw, it.groupValues[2], it.groupValues[1], "fishing_junk_item", FishingLootCategory.JUNK)
        }
        coins.matchEntire(line)?.let {
            val amount = it.groupValues[2].replace(",", "").toLongOrNull()?.takeIf { n -> n in 1..MAX_COINS } ?: return null
            return RewardObservation("Coins", "${it.groupValues[1]} CATCH!", "fishing_treasure_coins", amount = amount,
                fishingCategory = FishingLootCategory.TREASURE, catchQuality = FishingCatchQuality.valueOf(it.groupValues[1]))
        }
        pet.matchEntire(line)?.let {
            return item(raw, it.groupValues[2], it.groupValues[1], "fishing_treasure_pet")
        }
        shard.matchEntire(line)?.let {
            val amount = if (it.groupValues[3].isEmpty()) 1L else it.groupValues[3].toLongOrNull() ?: return null
            if (amount !in 1..MAX_ITEMS) return null
            return item(raw, it.groupValues[2], it.groupValues[1], "fishing_treasure_shard").copy(amount = amount)
        }
        foundItem.matchEntire(line)?.let {
            return item(raw, it.groupValues[2], it.groupValues[1], "fishing_treasure_item")
        }
        caughtItem.matchEntire(line)?.let {
            return item(raw, it.groupValues[2], it.groupValues[1], "fishing_treasure_item")
        }
        return null
    }

    private fun item(raw: String, observedName: String, quality: String, sourceId: String,
                     category: FishingLootCategory = FishingLootCategory.TREASURE): RewardObservation {
        val name = observedName.trim()
        val title = "$quality ${if (category == FishingLootCategory.JUNK) "JUNK " else ""}CATCH!"
        return RewardObservation(name, title, sourceId,
            vanillaItemId = if (name == "Enchanted Book") "minecraft:enchanted_book" else null,
            rarity = itemSpanRarity(raw, name), amount = 1,
            fishingCategory = category, catchQuality = FishingCatchQuality.valueOf(quality))
    }

    /** Only an explicit color immediately on the item name, after the server's catch verb. */
    private fun itemSpanRarity(raw: String, name: String): String? {
        val start = listOf(raw.indexOf("You caught"), raw.indexOf("You found")).filter { it >= 0 }.minOrNull() ?: return null
        val itemPart = raw.substring(start)
        val exact = Regex("§([0-9a-f])(?:§[klmno])*${Regex.escape(name)}(?=§|[!. ]|$)", RegexOption.IGNORE_CASE)
        return exact.find(itemPart)?.groupValues?.get(1)?.firstOrNull()?.lowercaseChar()?.let(colors::get)
    }

    private const val MAX_COINS = 1_000_000_000_000L
    private const val MAX_ITEMS = 1_000_000L
}
