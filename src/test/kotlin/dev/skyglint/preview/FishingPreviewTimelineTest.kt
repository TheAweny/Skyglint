package dev.skyglint.preview

import dev.skyglint.core.*

import dev.skyglint.features.fishing.bait.FishingBaitModule
import java.util.UUID
import kotlin.test.*

class FishingPreviewTimelineTest {
    @Test fun `selected healthy bait contracts to item while low depleted unknown and refill follow observed lore`() {
        val preview = FishingPreviewTimeline(0)
        val healthy = bait(preview.snapshot(1_200))!!.presentation!!.metrics.single()
        assertEquals("bait_selected", healthy.id)
        assertEquals("256", healthy.value)
        assertEquals("@fishing_bait", healthy.icon)
        assertEquals(WidgetMetricFormat.ITEM, healthy.format)
        assertEquals(2_000L, healthy.expandedUntil)
        assertNull(healthy.accent)
        val contracted = bait(preview.snapshot(2_400))!!.presentation!!.metrics.single()
        assertEquals(healthy, contracted)
        assertTrue(contracted.expandedUntil!! <= 2_400)
        assertEquals("12", bait(preview.snapshot(4_400))?.presentation?.metrics?.single()?.value)
        val empty = bait(preview.snapshot(7_400))!!.presentation!!.metrics.single()
        assertEquals("0", empty.value)
        assertTrue(empty.detail!!.contains("Depleted"))
        assertNull(bait(preview.snapshot(10_400))) // Missing preview is unknown, not zero.
        val refill = bait(preview.snapshot(13_400))!!.presentation!!.metrics.single()
        assertEquals("256", refill.value) // Explicit 256 is healthy, not a stack cap of 64.
        assertTrue(refill.detail!!.contains("256 remaining"))
        assertEquals(14_000L, refill.expandedUntil) // Long unknown gap requires a fresh introduction.
        assertNull(refill.accent)
        assertNull(preview.snapshot(13_400).activeNotice)
    }

    @Test fun `explicit sack disabled and enabled confirmations form one additive helper`() {
        val preview = FishingPreviewTimeline(0)
        val disabledMetrics = bait(preview.snapshot(16_400))!!.presentation!!.metrics
        assertEquals(2, disabledMetrics.size)
        assertEquals("256", disabledMetrics.single { it.id == "bait_selected" }.value)
        val disabled = disabledMetrics.single { it.id == "bait_sack_disabled" }
        assertEquals("bait_sack_disabled", disabled.id)
        assertEquals("", disabled.value)
        assertEquals("@sack_disabled", disabled.icon)
        assertEquals(WidgetMetricFormat.ITEM, disabled.format)
        assertNull(preview.snapshot(16_400).activeNotice)
        assertEquals("bait_selected", bait(preview.snapshot(19_000))!!.presentation!!.metrics.single().id)
    }

    @Test fun `rare double hook is a catch notice with no fabricated reward`() {
        val snapshot = FishingPreviewTimeline(0).snapshot(21_400)
        val notice = snapshot.activeNotice!!
        assertEquals("Water Hydra caught · Double Hook", notice.title)
        assertNull(notice.reward)
        assertNull(notice.countdown)
        assertEquals("8", bait(snapshot)!!.presentation!!.metrics.single().value)
    }

    @Test fun `real reward pipeline preserves junk category trophy grade quantity and name rarity separately`() {
        val preview = FishingPreviewTimeline(0)
        val rusty = preview.snapshot(25_400).activeNotice!!.reward!!
        assertEquals("Rusty Coin", rusty.itemName)
        assertEquals(FishingLootCategory.JUNK, rusty.fishingCategory)
        assertEquals(FishingCatchQuality.GOOD, rusty.catchQuality)
        assertEquals("UNCOMMON", rusty.rarity)
        assertEquals(1L, rusty.amount)
        val boot = preview.snapshot(29_400).activeNotice!!.reward!!
        assertEquals("Old Leather Boot", boot.itemName)
        assertEquals(FishingLootCategory.JUNK, boot.fishingCategory)
        assertEquals("EPIC", boot.rarity)
        val trophy = preview.snapshot(33_400).activeNotice!!.reward!!
        assertEquals("Blobfish", trophy.itemName)
        assertEquals(FishingLootCategory.TROPHY, trophy.fishingCategory)
        assertEquals(TrophyFishGrade.DIAMOND, trophy.trophyGrade)
        assertEquals("COMMON", trophy.rarity)
        val shard = preview.snapshot(37_400).activeNotice!!.reward!!
        assertEquals("Water Snake Shard", shard.itemName)
        assertEquals(3L, shard.amount)
        assertEquals(FishingLootCategory.TREASURE, shard.fishingCategory)
    }

    @Test fun `warning and notice phases preserve underlying boss fishing and independent pet progress`() {
        val preview = FishingPreviewTimeline(0)
        for (at in listOf(1_200L, 2_400L, 4_400L, 7_400L, 10_400L, 13_400L, 16_400L, 19_000L,
            21_400L, 25_400L, 29_400L, 33_400L, 37_400L)) {
            val snapshot = preview.snapshot(at)
            val skill = assertIs<ProgressionView.SkillView>(snapshot.progression)
            assertEquals(Skill.FISHING, skill.skill)
            assertEquals(1.1, skill.gain)
            assertEquals(.4839, skill.progress?.fraction)
            assertNull(skill.progress?.current)
            assertEquals(UUID(0, 912), snapshot.bosses.single().id)
            assertEquals(.64f, snapshot.bosses.single().progress)
            assertEquals("Seal", snapshot.pet?.name)
            assertEquals(.72, snapshot.pet?.progress?.fraction)
            assertNull(snapshot.notice)
            assertTrue(snapshot.widgets.all { it.presentation?.role == WidgetRole.COUNTERS })
        }
    }

    @Test fun `sparse and frequent play agree and backwards or cycle seeking clears old loot and warnings`() {
        val played = FishingPreviewTimeline(1_000)
        for (at in 1_000L..38_400L step 100) played.snapshot(at)
        assertEquals(played.snapshot(38_400), FishingPreviewTimeline(1_000).snapshot(38_400))
        assertEquals(FishingPreviewTimeline(1_000).snapshot(8_400), played.snapshot(8_400))
        val restarted = played.snapshot(41_000)
        assertEquals("SYNTHETIC_FISHING:1", restarted.sessionId)
        assertNull(restarted.activeNotice)
        val selected = bait(restarted)!!.presentation!!.metrics.single()
        assertEquals("bait_selected", selected.id)
        assertEquals("256", selected.value)
        assertEquals(43_000L, selected.expandedUntil)
        assertEquals(FishingPreviewTimeline(1_000).snapshot(1_000), played.snapshot(1_000))
        assertEquals(FishingPreviewTimeline(1_000).snapshot(1_000), played.snapshot(0))
    }

    private fun bait(snapshot: HudSnapshot) = snapshot.widgets.singleOrNull { it.id == FishingBaitModule.WIDGET_ID }
}
