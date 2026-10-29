package dev.skyglint.features.winter

import dev.skyglint.features.winter.JerryDefenseResultPatterns.Field
import kotlin.test.*

class JerryDefenseResultPatternsTest {
    @Test fun `captured wave three result block yields typed independent fields with observed metadata`() {
        val lines = listOf("WAVE 3 - YOU WON!", "Snowballs Hit: 0", "Snowballs Thrown: 0", "Snowball Accuracy: N/A",
            "Total Damage: 732 (NEW RECORD!) (Position #4)", "Magma Cube Damage: 0", "Snow Cannonball Hits: 65 (NEW RECORD!)",
            "White Gift x34", "Volcanic Rock x5", "Red Gift x8", "Green Gift x30", "Collect your Gifts at the Gift piles!")
        val expected = listOf(Field.Header(3), Field.Stat("snowballs_hit", "Snowballs Hit", "0"),
            Field.Stat("snowballs_thrown", "Snowballs Thrown", "0"), Field.Stat("snowball_accuracy", "Snowball Accuracy", "N/A"),
            Field.Stat("total_damage", "Total Damage", "732 (NEW RECORD!) (Position #4)"),
            Field.Stat("magma_cube_damage", "Magma Cube Damage", "0"),
            Field.Stat("snow_cannonball_hits", "Snow Cannonball Hits", "65 (NEW RECORD!)"),
            Field.Gift("White Gift", 34), Field.Gift("Volcanic Rock", 5), Field.Gift("Red Gift", 8), Field.Gift("Green Gift", 30), Field.End)
        assertEquals(expected, lines.map(JerryDefenseResultPatterns::line))
    }

    @Test fun `formatting indentation repeated spaces and nonbreaking spaces normalize without accepting chat prefixes`() {
        assertEquals(Field.Header(3), JerryDefenseResultPatterns.line("     §6§lWAVE   §e3 - YOU WON!    "))
        assertEquals(Field.Gift("White Gift", 34), JerryDefenseResultPatterns.line("  §fWhite\u00a0Gift   x34 "))
        assertEquals(Field.Stat("total_damage", "Total Damage", "12,345 (Position #4)"),
            JerryDefenseResultPatterns.line(" §aTotal Damage: 12345   (Position #4)"))
        assertNull(JerryDefenseResultPatterns.line("[MVP+] Player: WAVE 3 - YOU WON!"))
        assertNull(JerryDefenseResultPatterns.line("[CHAT] WAVE 3 - YOU WON!"))
    }

    @Test fun `numeric captions normalize grouping and accuracy preserves unknown separately from zero`() {
        assertEquals(Field.Stat("snowballs_hit", "Snowballs Hit", "1,234,567"), JerryDefenseResultPatterns.line("Snowballs Hit: 1234567"))
        assertEquals(Field.Stat("snowballs_thrown", "Snowballs Thrown", "1,234,567"), JerryDefenseResultPatterns.line("Snowballs Thrown: 1,234,567"))
        assertEquals(Field.Stat("snowball_accuracy", "Snowball Accuracy", "0%"), JerryDefenseResultPatterns.line("Snowball Accuracy: 0.00%"))
        assertEquals(Field.Stat("snowball_accuracy", "Snowball Accuracy", "99.75%"), JerryDefenseResultPatterns.line("Snowball Accuracy: 99.75%"))
        assertEquals(Field.Stat("snowball_accuracy", "Snowball Accuracy", "100%"), JerryDefenseResultPatterns.line("Snowball Accuracy: 100%"))
        assertEquals("N/A", (JerryDefenseResultPatterns.line("Snowball Accuracy: N/A") as Field.Stat).value)
        assertEquals(Field.Gift("Green Gift", 0), JerryDefenseResultPatterns.line("Green Gift x0"))
    }

    @Test fun `wave start protocol is separate from confirmed result header and never guesses loss`() {
        assertEquals(3, JerryDefenseResultPatterns.startedWave("  §cDEFEND JERRY'S WORKSHOP - WAVE 3  "))
        assertNull(JerryDefenseResultPatterns.line("DEFEND JERRY'S WORKSHOP - WAVE 3"))
        assertNull(JerryDefenseResultPatterns.startedWave("WAVE 3 - YOU WON!"))
        for (text in listOf("WAVE 3 - YOU LOST!", "Wave 3", "YOU WON!", "Magma Cubes Left: 0", "Health: 100%"))
            assertNull(JerryDefenseResultPatterns.line(text), text)
    }

    @Test fun `negative malformed overflowing or out of bounds values are unknown`() {
        for (text in listOf("WAVE 0 - YOU WON!", "WAVE 1001 - YOU WON!", "Snowballs Hit: -1", "Snowballs Hit: +1",
            "Snowballs Hit: 1,23", "Snowballs Hit: 1,,234", "Snowballs Hit: 1000000000001", "Snowballs Hit: 99999999999999999999999",
            "Magma Cube Damage: 1.5", "White Gift x1000000001", "Red Gift x-3", "Red Gift x1,00", "Snowball Accuracy: 100.01%",
            "Snowball Accuracy: -1%", "Snowball Accuracy: 1.234%", "Total Damage: 1 (Position #0)",
            "Total Damage: 1 (Position #1000001)", "Snowballs Hit: 1 (NEW RECORD!)", "Magma Cube Damage: 0 (Position #4)"))
            assertNull(JerryDefenseResultPatterns.line(text), text)
        for (text in listOf("DEFEND JERRY'S WORKSHOP - WAVE 0", "DEFEND JERRY'S WORKSHOP - WAVE 1001"))
            assertNull(JerryDefenseResultPatterns.startedWave(text), text)
    }

    @Test fun `partial unknown gift names suffixes multiline and excessive input are rejected`() {
        for (text in listOf("", "Snowballs Hit:", "White Gift", "White Gift x", "Blue Gift x4", "Volcanic Rock x5 bonus",
            "Total Damage: 732 (Position #4) (NEW RECORD!)", "Snow Cannonball Hits: 65 (Position #4)",
            "Collect your Gifts at the Gift piles! extra", "WAVE 3 - YOU WON!\nWhite Gift x34", "White Gift x34\r",
            "\tWhite Gift x34", " ".repeat(1_025))) assertNull(JerryDefenseResultPatterns.line(text), text.take(100))
        assertNull(JerryDefenseResultPatterns.startedWave("DEFEND JERRY'S WORKSHOP - WAVE 3\n"))
    }
}
