package dev.skyglint.features.winter

import kotlin.test.*

/** Contract fixtures; these are synthetic structured packets, not claims of a live server capture. */
class JerryGiftPilePatternsTest {
    @Test fun `actual captured reverse order preserves the targeted A rather than assigning its color to E`() {
        val raw = "§aⒺ       §aⒹ       §aⒸ       §aⒷ       §eⒶ       "
        val points = assertNotNull(JerryGiftPilePatterns.points(raw))
        assertEquals(listOf("E", "D", "C", "B", "A"), points.map { it.letter })
        assertEquals(0xFFFFFF55.toInt(), points.last().color)
        assertEquals("D", assertNotNull(JerryGiftPilePatterns.points("§aⒺ §cⓍ §aⒸ §aⒷ §eⒶ"))[1].letter)
    }
    @Test fun `five positional markers preserve colors and destroyed identity without percentages`() {
        val points = assertNotNull(JerryGiftPilePatterns.points("§aⒶ §eⒷ §cⒸ §fⒹ §cⓍ"))
        assertEquals(listOf("A", "B", "C", "D", "E"), points.map { it.letter })
        assertEquals(listOf(false, false, false, false, true), points.map { it.destroyed })
        assertEquals(listOf(0xFF55FF55.toInt(), 0xFFFFFF55.toInt(), 0xFFFF5555.toInt(), -1, 0xFFFF5555.toInt()), points.map { it.color })
    }

    @Test fun `style controls do not erase inherited palette and explicit reset makes status unknown`() {
        assertNotNull(JerryGiftPilePatterns.points("§a§lⒶ Ⓑ Ⓒ Ⓓ Ⓔ"))
        assertNull(JerryGiftPilePatterns.points("§aⒶ Ⓑ §rⒸ Ⓓ Ⓔ"))
        assertNotNull(JerryGiftPilePatterns.points("§rⓍ Ⓧ Ⓧ Ⓧ Ⓧ"))
    }

    @Test fun `unrecognized missing duplicated reordered and unrelated titles remain generic bosses`() {
        for (text in listOf("Ⓐ Ⓑ Ⓒ Ⓓ Ⓔ", "§aⒶ Ⓑ Ⓒ Ⓓ", "§aⒶ Ⓐ Ⓒ Ⓓ Ⓔ",
            "§aⒷ Ⓐ Ⓒ Ⓓ Ⓔ", "§aⒶ Ⓑ Ⓒ Ⓓ Ⓔ Ⓧ", "§aQuest Ⓐ Ⓑ Ⓒ Ⓓ Ⓔ",
            "§aⒶ Ⓑ\nⒸ Ⓓ Ⓔ", "§aⒶ Ⓑ Ⓒ Ⓓ Ⓔ§")) assertNull(JerryGiftPilePatterns.points(text), text)
        assertNotNull(JerryGiftPilePatterns.points("§fGift Piles: §aⒶ Ⓑ Ⓒ Ⓓ Ⓔ"))
    }
}
