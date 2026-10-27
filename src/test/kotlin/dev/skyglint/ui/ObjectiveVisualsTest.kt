package dev.skyglint.ui

import dev.skyglint.ui.presentation.IdleLocationPolicy
import dev.skyglint.ui.presentation.ObjectiveVisuals
import kotlin.test.*

class ObjectiveVisualsTest {
    @Test fun `known live objective forms remove only the exact presentation prefix and choose relevant icons`() {
        val craft = assertNotNull(ObjectiveVisuals.resolve("§fObjective: §eCraft a workbench"))
        assertEquals("Craft a workbench", craft.title)
        assertEquals("minecraft:crafting_table", craft.icon)
        for (person in listOf("the Pearl Dealer", "the Bartender", "Shaggy", "Elle")) {
            val talk = assertNotNull(ObjectiveVisuals.resolve("Objective: Talk to $person"))
            assertEquals("Talk to $person", talk.title)
            assertEquals("minecraft:villager_spawn_egg", talk.icon)
        }
        val travel = assertNotNull(ObjectiveVisuals.resolve("Objective: Travel to the Crimson Isle"))
        assertEquals("Travel to the Crimson Isle", travel.title)
        assertEquals("minecraft:compass", travel.icon)
    }

    @Test fun `unknown bossbars and embedded objective text do not get reclassified`() {
        for (title in listOf("Superior Dragon", "Guesting: <fixture>", "Boss Objective: Damage the dragon",
            "objective: Craft a workbench", "Objective:Craft a workbench", "Objective: ", "Objective:"))
            assertNull(ObjectiveVisuals.resolve(title), title)
    }

    @Test fun `unmapped task keeps complete server task text with a neutral paper icon`() {
        val observed = assertNotNull(ObjectiveVisuals.resolve("Objective: Collect 10 Wheat"))
        assertEquals("Collect 10 Wheat", observed.title)
        assertEquals("minecraft:paper", observed.icon)
        assertEquals("minecraft:paper", ObjectiveVisuals.resolve("Objective: Craft an unknown future item")?.icon)
        assertEquals("minecraft:paper", ObjectiveVisuals.resolve("Objective: Talk to [unknown marker]")?.icon)
    }

    @Test fun `invalid multiline and oversized values are rejected without creating a fabricated quest`() {
        assertNull(ObjectiveVisuals.resolve("Objective: Talk to Elle\nOTHER LINE"))
        assertNull(ObjectiveVisuals.resolve("Objective: " + "a".repeat(257)))
        assertNull(ObjectiveVisuals.resolve("Objective: \u0000"))
        assertNotNull(ObjectiveVisuals.resolve("Objective: " + "a".repeat(256)))
    }

    @Test fun `idle policy hides redundant own island only and preserves useful public locations`() {
        assertNull(IdleLocationPolicy.caption("§aYour Island"))
        assertNull(IdleLocationPolicy.caption("Private Island"))
        assertNull(IdleLocationPolicy.caption(" your island "))
        assertNull(IdleLocationPolicy.caption(null))
        assertNull(IdleLocationPolicy.caption(" "))
        assertEquals("The Garden", IdleLocationPolicy.caption("The Garden"))
        assertEquals("Village", IdleLocationPolicy.caption("§bVillage"))
        assertEquals("Plot - 4", IdleLocationPolicy.caption("Plot - 4"))
        assertEquals("Island", IdleLocationPolicy.caption("Island"))
        assertEquals("Someone's Island", IdleLocationPolicy.caption("Someone's Island"))
    }
}
