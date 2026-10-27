package dev.skyglint.features.mining

import kotlin.test.*

class RandomMiningEventPatternsTest {
    @Test fun `exact colored random spawn announcements retain their observed identity`() {
        assertEquals(RandomMiningEventPatterns.Type.GOLDEN_GOBLIN,
            RandomMiningEventPatterns.parse("§6A Golden Goblin has spawned!")?.type)
        assertEquals(RandomMiningEventPatterns.Type.DIAMOND_GOBLIN,
            RandomMiningEventPatterns.parse("§6A §r§bDiamond Goblin §r§6has spawned!")?.type)
        assertEquals(RandomMiningEventPatterns.Type.MINESHAFT_PORTAL,
            RandomMiningEventPatterns.parse("§5§lWOW! §r§aYou found a §r§bGlacite Mineshaft §r§aportal!")?.type)
        val star = RandomMiningEventPatterns.parse("§5✯ A Fallen Star has crashed at Royal Mines! Nearby ore and Powder drops are amplified!")!!
        assertEquals(RandomMiningEventPatterns.Type.FALLEN_STAR, star.type)
        assertEquals("Royal Mines", star.location)
    }

    @Test fun `powder ghast accepts both split and bundled server messages`() {
        val location = "Find the Powder Ghast near the Forge Basin!"
        val expected = RandomMiningEventPatterns.Observation(RandomMiningEventPatterns.Type.POWDER_GHAST, "Forge Basin")
        assertEquals(expected, RandomMiningEventPatterns.parse(location))
        assertEquals(expected, RandomMiningEventPatterns.parse("The sound of pickaxes clashing against the rock has attracted the attention of the POWDER GHAST!\n$location"))
    }

    @Test fun `unknown areas player-like prefixes malformed and unverified timed messages stay unknown`() {
        listOf(
            "Player: A Golden Goblin has spawned!", "A Golden Goblin has spawned! extra",
            "Find the Powder Ghast near the Hub!", "Find the Powder Ghast near the Forge Basin!\nextra",
            "✯ A Fallen Star has crashed at Hub! Nearby ore and Powder drops are amplified!",
            "A Golden Goblin\u0000 has spawned!", "A Golden Goblin has spawned!".repeat(100),
            "FORTUNATE FREEZING STARTED!", "Snowstorm started!",
        ).forEach { assertNull(RandomMiningEventPatterns.parse(it), it) }
    }
}
