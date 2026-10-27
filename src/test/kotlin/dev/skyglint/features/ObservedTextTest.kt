package dev.skyglint.features

import kotlin.test.*
import org.junit.jupiter.api.Test

class ObservedTextTest {
    @Test fun `location requires sidebar area symbol`() {
        assertEquals("The Garden", ObservedText.location(listOf("§7\uE067 §cThe Garde§v§cn §4§l\uE07F§7 x8")))
        assertEquals("The Garden", ObservedText.location(listOf("\uE067 The Garden \uE018 x3")))
        assertEquals("Plot - 4", ObservedText.location(listOf("\uE067 Plot - 4 \uE07F x1")))
        assertEquals("Your Island", ObservedText.location(listOf(" §7⏣ §aYour Island")))
        assertEquals("Wizard Tower", ObservedText.location(listOf(" §5ф §dWizard Tower")))
        assertNull(ObservedText.location(listOf("Purse: 100", "Your Island")))
    }
    @Test fun `rarity is an explicit terminal lore label`() {
        assertEquals("EPIC", ObservedText.rarity(listOf("Something", "§5EPIC SWORD")))
        assertEquals("VERY SPECIAL", ObservedText.rarity(listOf("§cVERY SPECIAL")))
        assertNull(ObservedText.rarity(listOf("RARE DROP!", "Chance for an EPIC drop")))
    }
}
