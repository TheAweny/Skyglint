package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class RewardPatternsTest {
    private fun reward(raw: String) = assertNotNull(RewardPatterns.observe(raw))

    @Test fun `junk category preserves independent catch quality and explicit item rarity`() {
        val rusty = reward("§6⛃ §r§6§lGOOD JUNK CATCH! §r§fYou caught a §r§aRusty Coin§r§f!")
        assertEquals("Rusty Coin", rusty.itemName)
        assertEquals("GOOD JUNK CATCH!", rusty.sourceTitle)
        assertEquals("fishing_junk_item", rusty.sourceId)
        assertEquals(FishingLootCategory.JUNK, rusty.fishingCategory)
        assertEquals(FishingCatchQuality.GOOD, rusty.catchQuality)
        assertEquals("UNCOMMON", rusty.rarity)
        assertEquals(1L, rusty.amount)
        assertNull(rusty.trophyGrade)
        for ((quality, item, rarity) in listOf(Triple("GREAT", "Busted Belt Buckle", "RARE"),
            Triple("OUTSTANDING", "Old Leather Boot", "EPIC"), Triple("OUTSTANDING", "Moby-Duck", "RARE"))) {
            val color = if (rarity == "EPIC") "5" else "9"
            val observed = reward("§6\uE025 §6${quality} JUNK CATCH! §fYou caught an §$color$item§f!")
            assertEquals(FishingCatchQuality.valueOf(quality), observed.catchQuality)
            assertEquals(FishingLootCategory.JUNK, observed.fishingCategory)
            assertEquals(rarity, observed.rarity)
        }
        // Uncolored exact form is still evidence of the catch, never evidence of item rarity or value.
        assertNull(reward("⛃ OUTSTANDING JUNK CATCH! You caught an Old Leather Boot!").rarity)
    }

    @Test fun `current catch item forms and historical bait forms retain exact names`() {
        val bait = reward("§6§lGOOD CATCH! §r§bYou found a §r§fFish Bait§r§b.")
        assertEquals("COMMON", bait.rarity)
        assertEquals(FishingLootCategory.TREASURE, bait.fishingCategory)
        assertEquals(FishingCatchQuality.GOOD, bait.catchQuality)
        val item = reward("⛃ GREAT CATCH! You caught an Enchanted Book!")
        assertEquals("Enchanted Book", item.itemName)
        assertEquals("minecraft:enchanted_book", item.vanillaItemId)
        assertNull(item.rarity)
        // Catch heading color / quality never sets the item's rarity.
        assertNull(reward("§6⛃ §6OUTSTANDING CATCH! You caught a Future Item!").rarity)
    }

    @Test fun `pet shards and coins keep their observed amounts without inventory arithmetic`() {
        val pet = reward("§6\uE025 §r§6§lGREAT CATCH! §r§fYou caught a §r§7[Lvl 1] §r§aSquid§r§f!")
        assertEquals("Squid", pet.itemName)
        assertEquals("UNCOMMON", pet.rarity)
        assertEquals("fishing_treasure_pet", pet.sourceId)
        val shard = reward("\uE025 GOOD CATCH! You caught Water Snake Shard x3!")
        assertEquals("Water Snake Shard", shard.itemName)
        assertEquals(3L, shard.amount)
        assertEquals(1L, reward("\uE025 GOOD CATCH! You caught a Water Snake Shard!").amount)
        val coins = reward("\uE025 GOOD CATCH! You caught 36,064 Coins!")
        assertEquals(36_064L, coins.amount)
        assertEquals("fishing_treasure_coins", coins.sourceId)
        assertNull(coins.rarity)
    }

    @Test fun `trophy grade remains separate from item rarity and treasure quality`() {
        val bronze = reward("§6\uE02A §r§6§lTROPHY FISH! §r§fYou caught §r§fBlobfish §r§8§lBRONZE§r§f!")
        assertEquals("Blobfish", bronze.itemName)
        assertEquals(TrophyFishGrade.BRONZE, bronze.trophyGrade)
        assertEquals(FishingLootCategory.TROPHY, bronze.fishingCategory)
        assertEquals("COMMON", bronze.rarity)
        assertNull(bronze.catchQuality)
        val silver = reward("§6\uE02A §r§6§lTROPHY FISH! §r§fYou caught §r§9Lavahorse §r§7§lSILVER§r§f!")
        assertEquals("RARE", silver.rarity)
        assertEquals(TrophyFishGrade.SILVER, silver.trophyGrade)
        val obfuscated = reward("§6\uE02A §6TROPHY FISH! §fYou caught §f§kObfuscated 1 §6§lGOLD§f!")
        assertEquals("Obfuscated 1", obfuscated.itemName)
        assertEquals("COMMON", obfuscated.rarity)
        assertEquals(TrophyFishGrade.GOLD, obfuscated.trophyGrade)
        assertNull(reward("\uE02A TROPHY FISH! You caught Blobfish DIAMOND!").rarity)
    }

    @Test fun `bounded exact grammar rejects spoofed or incomplete catches and unsupported ordinary catch guesses`() {
        for (line in listOf(
            "[MVP+] Other: ⛃ GOOD JUNK CATCH! You caught a Rusty Coin!",
            "Guild > Other: \uE02A TROPHY FISH! You caught Blobfish GOLD!",
            "⛃ GOOD JUNK CATCH! You caught a Rusty Coin! bonus",
            "⛃ GOOD JUNK CATCH! You caught a Rusty Coin!\n",
            "⛃ GOOD JUNK CATCH! You caught a Rusty Coin!\t",
            "⛃ GOOD JUNK CATCH! You caught a Rusty Coin x0!",
            "\uE025 GREAT CATCH! You caught a Water Snake Shard x0!",
            "\uE025 GREAT CATCH! You caught a Water Snake Shard x-1!",
            "\uE025 GREAT CATCH! You caught a Water Snake Shard x999999999999999999999!",
            "\uE025 GOOD CATCH! You caught 1,23 Coins!",
            "\uE025 GOOD CATCH! You caught 0 Coins!",
            "\uE025 GOOD CATCH! You caught 999999999999999999999 Coins!",
            "\uE02A TROPHY FISH! You caught Blobfish MYTHIC!",
            "You caught a Raw Cod!", "You caught a Sea Walker.",
            "JUNK! You caught a Rusty Coin!", "GOOD CATCH! You caught a " + "a".repeat(97) + "!",
        )) assertNull(RewardPatterns.observe(line), line)
    }

    @Test fun `one server catch uses the existing reward route and all rewards default includes junk`() {
        val module = RareDropSignalModule()
        val signal = NormalizedSignal.Chat("⛃ GOOD JUNK CATCH! You caught a Rusty Coin!")
        val event = assertIs<CoreEvent.RewardObserved>(module.parse(signal).single())
        assertTrue(RewardRulePolicy().evaluate(event.observation).shown)
        assertTrue(RewardRulePolicy(RewardMode.FILTERED).evaluate(event.observation).shown)
        assertFalse(RewardRulePolicy(RewardMode.ALL, mapOf("Rusty Coin" to RewardOverride.HIDE)).evaluate(event.observation).shown)
        assertTrue(module.parse(NormalizedSignal.ActionBar(signal.text)).isEmpty())
        // Parsing is stateless: identical actual server catch packets remain distinct observations.
        assertEquals(event, module.parse(signal).single())
    }
}
