package dev.skyglint.ui

import dev.skyglint.ui.presentation.EventVisuals
import kotlin.test.*

class EventVisualsTest {
    @Test fun `spooky festival is an orange pumpkin rather than a generic star or rarity color`() {
        val visual = EventVisuals.resolve("Spooky Festival")
        assertEquals("minecraft:carved_pumpkin", visual.icon)
        assertEquals("Spooky Festival", visual.shortName)
        val red = visual.accent ushr 16 and 255
        val green = visual.accent ushr 8 and 255
        val blue = visual.accent and 255
        assertTrue(red > green && green > blue)
        assertEquals(255, visual.accent ushr 24)
    }
    @Test fun `source exact aliases normalize case whitespace legacy styles and apostrophes`() {
        assertEquals(EventVisuals.resolve("Spooky Festival"), EventVisuals.resolve(" §6§lSPOOKY\u00a0  FESTIVAL "))
        assertEquals(EventVisuals.resolve("Fishing Festival"), EventVisuals.resolve("Bonus Fishing Festival"))
        assertEquals(EventVisuals.resolve("Mining Fiesta"), EventVisuals.resolve("Bonus Mining Fiesta"))
        assertEquals(EventVisuals.resolve("Hoppity's Hunt"), EventVisuals.resolve("Hoppity’s Hunt"))
        assertEquals(EventVisuals.resolve("Jerry's Workshop Opens"), EventVisuals.resolve("Jerry's Workshop"))
    }
    @Test fun `different source event themes have distinct icon and accent pairs`() {
        val names = listOf("Spooky Festival", "Season of Jerry", "Jerry's Workshop Opens", "Fishing Festival", "Mining Fiesta",
            "Mythological Ritual", "Traveling Zoo", "Dark Auction", "New Year Celebration", "Bingo", "Carnival",
            "Hoppity's Hunt", "Year of the Seal", "Jacob's Farming Contest", "Mayor Jerry", "Cult of the Fallen Star",
            "Election Booth Opens", "Election Over!")
        val themes = names.map(EventVisuals::resolve)
        assertEquals(names.size, themes.map { it.icon }.toSet().size)
        assertEquals(names.size, themes.map { it.accent }.toSet().size)
        assertTrue(themes.none { it.icon == "minecraft:clock" })
    }
    @Test fun `unknown names keep their title and neutral clock without substring guesses`() {
        for (name in listOf("Future Event", "Spooky Festival Rewards", "Not a Mining Fiesta", "Super Dark Auction")) {
            val visual = EventVisuals.resolve(name)
            assertEquals("minecraft:clock", visual.icon)
            assertEquals(name, visual.shortName)
        }
        assertEquals("Event", EventVisuals.resolve(" §r ").shortName)
        assertEquals(80, EventVisuals.resolve("X".repeat(200)).shortName.length)
    }
}
