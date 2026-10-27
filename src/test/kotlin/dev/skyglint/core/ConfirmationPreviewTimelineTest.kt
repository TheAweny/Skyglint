package dev.skyglint.core

import dev.skyglint.features.session.ServerClosingModule
import java.util.UUID
import kotlin.test.*

class ConfirmationPreviewTimelineTest {
    @Test fun `closing introduction becomes additive text timer and last minute warns once`() {
        val preview = ConfirmationPreviewTimeline(0)
        val initial = preview.snapshot(0)
        assertEquals("Server closing", initial.activeNotice?.title)
        val firstMetric = timer(initial)
        assertEquals(WidgetMetricFormat.REMAINING_TIME, firstMetric.format)
        assertEquals("1:06", firstMetric.countdown?.caption(0))
        assertEquals(0xFFF28E8E.toInt(), firstMetric.accent)
        val ordinary = preview.snapshot(4_500)
        assertNull(ordinary.activeNotice)
        assertEquals(firstMetric.id, timer(ordinary).id)
        assertEquals("1:02", timer(ordinary).countdown?.caption(4_500))
        assertEquals(0, timer(ordinary).priority)
        val urgent = preview.snapshot(6_000)
        assertEquals("Server closing", urgent.activeNotice?.title)
        assertTrue(urgent.activeNotice!!.id.endsWith(":urgent"))
        assertEquals("1:00", timer(urgent).countdown?.caption(6_000))
        assertEquals(95, timer(urgent).priority)
        assertEquals(firstMetric.id, timer(urgent).id)
        val helper = preview.snapshot(10_500)
        assertNull(helper.activeNotice)
        assertEquals("0:56", timer(helper).countdown?.caption(10_500))
        assertIs<ProgressionView.SkyBlock>(helper.progression)
        assertTrue(preview.snapshot(12_000).widgets.none { it.id == ServerClosingModule.WIDGET_ID })
    }

    @Test fun `exact fresh Trevor assignments confirm completion and timeout without persistent quest`() {
        val preview = ConfirmationPreviewTimeline(0)
        assertNull(preview.snapshot(11_500).activeNotice)
        val completed = preview.snapshot(12_000)
        assertEquals("Trevor hunt completed", completed.activeNotice?.title)
        assertEquals("minecraft:compass", completed.activeNotice?.item?.vanillaItemId)
        assertEquals("Desert Settlement", completed.location)
        assertNull(preview.snapshot(16_000).activeNotice)
        assertNull(preview.snapshot(16_500).activeNotice)
        val timeout = preview.snapshot(17_000)
        assertEquals("Trevor hunt timed out", timeout.activeNotice?.title)
        assertEquals("minecraft:clock", timeout.activeNotice?.item?.vanillaItemId)
        assertNull(preview.snapshot(21_000).activeNotice)
        assertTrue(timeout.widgets.none { it.id.startsWith("skyglint.trevor") })
    }

    @Test fun `Bestiary requires final border and carries only complete observed description and milestone`() {
        val preview = ConfirmationPreviewTimeline(0)
        for (at in 21_500L..21_900L step 100) assertNull(preview.snapshot(at).activeNotice)
        val confirmed = preview.snapshot(22_000).activeNotice!!
        assertEquals("Bestiary updated", confirmed.title)
        assertEquals("Observed family II · Milestone 12 → 13", confirmed.detail)
        assertEquals("minecraft:nether_star", confirmed.item?.vanillaItemId)
        assertNull(confirmed.rarity)
        assertNull(confirmed.magicFind)
        assertNull(preview.snapshot(26_000).activeNotice)
    }

    @Test fun `all confirmations preserve unknown boss pet and independent original progression`() {
        val preview = ConfirmationPreviewTimeline(0)
        for (at in listOf(0L, 4_500, 6_000, 10_500, 12_000, 17_000, 22_000, 27_000)) {
            val snapshot = preview.snapshot(at)
            assertEquals(UUID(0, 910), snapshot.bosses.single().id)
            assertEquals("Unknown server objective", snapshot.bosses.single().title)
            assertEquals(.64f, snapshot.bosses.single().progress)
            assertEquals(PetSnapshot("Seal", 70, ProgressSnapshot(null, null, .72)), snapshot.pet)
            if (at < 10_000) {
                val skill = assertIs<ProgressionView.SkillView>(snapshot.progression)
                assertEquals(Skill.FARMING, skill.skill)
                assertEquals(.4839, skill.progress!!.fraction)
            } else {
                val skyBlock = assertIs<ProgressionView.SkyBlock>(snapshot.progression)
                assertEquals(287, skyBlock.level)
                assertEquals(ProgressSnapshot(88.0, 100.0, .88), skyBlock.progress)
            }
            assertNull(snapshot.notice)
        }
    }

    @Test fun `seeking without rendered intermediate frames matches played fixtures and resets each cycle`() {
        val played = ConfirmationPreviewTimeline(1_000)
        for (at in 1_000L..24_000L step 100) played.snapshot(at)
        assertEquals(played.snapshot(24_000), ConfirmationPreviewTimeline(1_000).snapshot(24_000))
        val reset = played.snapshot(31_000)
        assertEquals("SYNTHETIC_CONFIRMATIONS:1", reset.sessionId)
        assertEquals("Server closing", reset.activeNotice?.title)
        assertEquals("1:06", timer(reset).countdown?.caption(31_000))
        assertEquals(0L, reset.activeNotice?.presentationOffsetMillis)
        assertIs<ProgressionView.SkillView>(reset.progression)
        assertEquals(ConfirmationPreviewTimeline(1_000).snapshot(1_000), played.snapshot(1_000))
    }

    private fun timer(snapshot: HudSnapshot) = snapshot.widgets.single { it.id == ServerClosingModule.WIDGET_ID }
        .presentation!!.metrics.single()
}
