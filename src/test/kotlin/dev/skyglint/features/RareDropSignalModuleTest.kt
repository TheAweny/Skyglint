package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class RareDropSignalModuleTest {
    private val module = RareDropSignalModule()
    private fun parse(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun reward(text: String) = assertIs<CoreEvent.RewardObserved>(parse(text).single()).observation

    @Test fun `confirmed colored book pet and ghost fixtures preserve source metadata without deciding value`() {
        val book = reward("§6§lRARE DROP! §r§fEnchanted Book §r§b(+§r§b208% §r§b✯ Magic Find§r§b)")
        assertEquals("Enchanted Book", book.itemName)
        assertEquals("minecraft:enchanted_book", book.vanillaItemId)
        assertEquals(208, book.magicFind)
        assertNull(book.rarity)
        val pet = reward("§6§lPET DROP! §r§5Baby Yeti §r§b(+§r§b168 §r§b✯ Magic Find§r§b)")
        assertEquals("Baby Yeti", pet.itemName)
        assertEquals(168, pet.magicFind)
        assertEquals("EPIC", pet.rarity)
        assertEquals("Sorrow", reward("RARE DROP! Sorrow (+210% \uE01A Magic Find)").itemName)
        assertEquals("pet_drop", reward("PET DROP! Slug (+1300☘)").sourceId)
        assertNull(reward("PET DROP! Slug (+1300☘)").magicFind)
    }

    @Test fun `summoning eye is observed distinctly from ordinary eyes pearls and frequent materials`() {
        val summoning = reward("§6§lRARE DROP! §r§5Summoning Eye")
        assertEquals("Summoning Eye", summoning.itemName)
        assertEquals("RARE DROP!", summoning.sourceTitle)
        for (name in listOf("Eye of Ender", "Ender Pearl", "Enchanted Ender Pearl", "Rotten Flesh", "Enchanted Rotten Flesh")) {
            val observed = reward("RARE DROP! $name")
            assertEquals(name, observed.itemName)
            assertNotEquals(summoning.itemName, observed.itemName)
        }
        assertTrue(parse("A special Zealot has spawned nearby!").isEmpty())
        assertTrue(parse("You recovered a Summoning Eye!").isEmpty())
        assertTrue(parse("You placed a Summoning Eye!").isEmpty())
    }

    @Test fun `generic rare source observes unknown names but never asserts that the item is valuable`() {
        val event = assertIs<CoreEvent.RewardObserved>(parse("RARE DROP! Unverified Future Item (+100 ✯ Magic Find)").single())
        assertEquals("Unverified Future Item", event.observation.itemName)
        assertEquals("rare_drop", event.observation.sourceId)
        assertNull(event.decision)
        assertNull(event.observation.rarity)
    }

    @Test fun `source confirmed fishing messages keep their theme and expose coins separately`() {
        val bait = reward("§6§lGOOD CATCH! §r§bYou found a §r§fFish Bait§r§b.")
        assertEquals("GOOD CATCH!", bait.sourceTitle)
        assertEquals("Fish Bait", bait.itemName)
        assertEquals("fishing_treasure_item", bait.sourceId)
        val pet = reward("§6\uE025 §r§6§lGREAT CATCH! §r§fYou caught a §r§7[Lvl 1] §r§aSquid§r§f!")
        assertEquals("GREAT CATCH!", pet.sourceTitle)
        assertEquals("Squid", pet.itemName)
        assertEquals("UNCOMMON", pet.rarity)
        val shard = reward("\uE025 GREAT CATCH! You caught Giant Water Bug Shard x3!")
        assertEquals("Giant Water Bug Shard", shard.itemName)
        assertEquals(3L, shard.amount)
        val coins = reward("\uE025 GOOD CATCH! You caught 36,064 Coins!")
        assertEquals("Coins", coins.itemName)
        assertEquals(36_064L, coins.amount)
        assertEquals("fishing_treasure_coins", coins.sourceId)
    }

    @Test fun `invalid suffixes another players rewards spawn announcements and ordinary chat are rejected`() {
        for (line in listOf(
            "[MVP+] Player: RARE DROP! Enchanted Book",
            "Guild > Player: GOOD CATCH! You found a Fish Bait.",
            "Player has obtained [Lvl 1] Bal!",
            "RARE DROP! Enchanted Book (from a shop)",
            "RARE DROP! You dug out a Griffin Feather!",
            "RARE DROP! Enchanted Book\nRARE DROP! Crystal Fragment",
            "RARE DROP! Enchanted Book (+999999999999999999999 ✯ Magic Find)",
            "RARE DROP! Enchanted Book (+100001 ✯ Magic Find)",
            "\uE025 GOOD CATCH! You caught -3 Coins!",
            "\uE025 GOOD CATCH! You caught 36,64 Coins!",
            "\uE025 GREAT CATCH! You caught Giant Water Bug Shard x0!",
        )) assertTrue(parse(line).isEmpty(), line)
        assertTrue(module.parse(NormalizedSignal.ActionBar("RARE DROP! Summoning Eye")).isEmpty())
    }
}
