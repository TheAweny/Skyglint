package dev.skyglint.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class LevelUpPreviewTimelineTest {
    @Test fun `pet confirmation takes over before low progress returns`() {
        val timeline = LevelUpPreviewTimeline(0, true)
        assertNull(timeline.snapshot(0).activeNotice)
        assertEquals("Pet level up!", timeline.snapshot(1_000).activeNotice?.title)
        val after = timeline.snapshot(5_100)
        assertNull(after.activeNotice)
        assertEquals(71, after.pet?.level)
        assertEquals(.004, after.pet?.progress?.fraction)
    }
    @Test fun `skill confirmation uses native detector and releases current state after notice`() {
        val timeline = LevelUpPreviewTimeline(0, false)
        assertNull(timeline.snapshot(0).activeNotice)
        assertEquals("Skill level up!", timeline.snapshot(1_000).activeNotice?.title)
        val after = timeline.snapshot(5_100)
        assertNull(after.activeNotice)
        assertEquals(21, assertIs<ProgressionView.SkillView>(after.progression).level)
    }
}
