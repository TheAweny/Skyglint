package dev.skyglint.preview

import dev.skyglint.core.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewTimelineTest {
    @Test fun `script uses real priority queue and restores newest boss and skill observations`() {
        val timeline = PreviewTimeline(1_000_000)
        assertEquals(88.0, assertIs<ProgressionView.SkyBlock>(timeline.snapshot(1_000_000).progression).progress!!.current)
        assertEquals(Skill.MINING, assertIs<ProgressionView.SkillView>(timeline.snapshot(1_000_500).progression).skill)
        assertEquals("Enchanted Book", timeline.snapshot(1_002_000).activeNotice!!.detail)
        val level = timeline.snapshot(1_003_100)
        assertEquals(100, level.activeNotice!!.priority)
        assertEquals(1_002_600, level.activeNotice!!.observedAt)
        assertEquals("Enchanted Book", timeline.snapshot(1_006_600).activeNotice!!.detail)
        assertEquals("Sorrow", timeline.snapshot(1_010_000).activeNotice!!.detail)
        val restored = timeline.snapshot(1_014_000)
        assertNull(restored.activeNotice)
        assertEquals(.50f, restored.bosses.single().progress)
        assertEquals(Skill.FARMING, assertIs<ProgressionView.SkillView>(restored.progression).skill)
        assertIs<ProgressionView.SkyBlock>(timeline.snapshot(1_019_000).progression)
    }

    @Test fun `loop resets preview state without changing independent live controller`() {
        val live = CoreController().apply {
            enterSkyBlock("real-session", 0)
            onActionBar("+1 Fishing (20%)", 1)
        }
        val timeline = PreviewTimeline(100_000)
        timeline.snapshot(114_000)
        val loop = timeline.snapshot(124_000)
        assertEquals("SYNTHETIC_SEQUENCE", loop.sessionId)
        assertNull(loop.activeNotice)
        assertEquals(.64f, loop.bosses.single().progress)
        assertIs<ProgressionView.SkyBlock>(loop.progression)
        assertEquals("Enchanted Book", timeline.snapshot(126_000).activeNotice!!.detail)
        val actual = live.snapshot(2)
        assertEquals("real-session", actual.sessionId)
        assertEquals(Skill.FISHING, assertIs<ProgressionView.SkillView>(actual.progression).skill)
        assertTrue(actual.bosses.isEmpty())
        assertNull(actual.activeNotice)
    }
}
