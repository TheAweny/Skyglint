package dev.skyglint.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class EventPreviewTimelineTest {
    @Test fun `active and future events never occupy ordinary HUD permanently`() {
        val timeline = EventPreviewTimeline(0)
        assertEquals("Event active", timeline.snapshot(0).activeNotice?.title)
        assertNull(timeline.snapshot(4_000).activeNotice)
        assertEquals("Starts in 5 min", timeline.snapshot(7_000).activeNotice?.title)
        assertNull(timeline.snapshot(11_000).activeNotice)
        assertEquals("Event started", timeline.snapshot(15_000).activeNotice?.title)
        val after = timeline.snapshot(19_000)
        assertNull(after.activeNotice)
        assertEquals("Objective: Talk to the Bartender", after.bosses.single().title)
        assertEquals("Village", after.location)
    }
}
