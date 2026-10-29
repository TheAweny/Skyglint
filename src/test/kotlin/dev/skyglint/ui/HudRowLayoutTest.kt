package dev.skyglint.ui

import dev.skyglint.ui.layout.HudRowLayout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudRowLayoutTest {
    @Test fun `growing and departing bodies reserve exactly their visible width`() {
        val row = HudRowLayout.place(listOf(
            HudRowLayout.Item("main", 120.0, 1.0, true),
            HudRowLayout.Item("skill", 15.0, .5, true),
            HudRowLayout.Item("pet", 30.0, 1.0, true),
        ))
        assertEquals(listOf(0.0, 125.0, 142.5), row.placements.map { it.bounds.x })
        assertEquals(172.5, row.width)
        val bounds = row.placements[1].bounds.transform(10.0, 12.0, .75)
        assertTrue(bounds.contains(103.75, 12.0))
        assertFalse(bounds.contains(115.0, 12.0))
        assertFalse(bounds.contains(103.75, 34.5))
    }

    @Test fun `gone elements add neither body nor gap and slot identity stays independent of content`() {
        val row = HudRowLayout.place(listOf(
            HudRowLayout.Item("main", 80.0, 1.0, true),
            HudRowLayout.Item("skill", 0.0, 0.0, false),
        ))
        assertEquals(80.0, row.width)
        assertEquals("main", row.placements.first().slotId)
        assertThrows(IllegalArgumentException::class.java) {
            HudRowLayout.place(listOf(HudRowLayout.Item("main", 1.0, 1.0, true), HudRowLayout.Item("main", 1.0, 1.0, true)))
        }
    }
}
