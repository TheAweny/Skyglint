package dev.skyglint.preview

import dev.skyglint.core.*

import java.util.UUID
import kotlin.test.*

class SlayerPetPreviewTimelineTest {
    @Test fun `welcome waits for settlement then freezes only observed level pet and location pages`() {
        val preview = SlayerPetPreviewTimeline(0)
        assertNull(preview.snapshot(0).activeNotice)
        assertNull(preview.snapshot(1_499).activeNotice)
        val welcome = preview.snapshot(1_500).activeNotice!!
        assertEquals("Welcome to SkyBlock", welcome.title)
        assertEquals(listOf("SkyBlock · Level 287", "Tiger · Lv. 36", "The End"), welcome.pages.map { it.title })
        assertEquals(listOf("@player", "@pet:Tiger:false", "minecraft:compass"), welcome.pages.map { it.icon })
        for (at in listOf(2_700L, 4_700L, 6_600L)) assertEquals(welcome, preview.snapshot(at).activeNotice)
        assertNull(preview.snapshot(7_900).activeNotice)
        assertNull(preview.snapshot(27_500).activeNotice)
    }

    @Test fun `Slayer advances from observed explicit XP to fight and a short confirmed slain notice`() {
        val preview = SlayerPetPreviewTimeline(0)
        val quest = slayer(preview.snapshot(9_000))
        assertTrue(quest.title.contains("Voidgloom Seraph IV"))
        assertTrue(quest.detail!!.contains("Combat XP"))
        assertEquals(1_200.0 / 21_000.0, quest.progress!!.fraction!!, 0.00001)
        val progressed = slayer(preview.snapshot(11_500))
        assertEquals(3_900.0 / 21_000.0, progressed.progress!!.fraction!!, 0.00001)
        val fight = slayer(preview.snapshot(14_000))
        assertTrue(listOfNotNull(fight.title, fight.detail).joinToString(" ").contains("Slay the boss"))
        val slain = preview.snapshot(17_000)
        assertTrue(slain.widgets.none { it.id == SLAYER_WIDGET })
        assertEquals("Voidgloom Seraph IV slain", slain.activeNotice?.title)
        assertNull(preview.snapshot(21_000).activeNotice)
    }

    @Test fun `manual and automatic pet switches resist stale tab and contract after three seconds`() {
        val preview = SlayerPetPreviewTimeline(0)
        val manual = preview.snapshot(22_000)
        assertEquals(PetSnapshot("Rabbit", null), manual.pet)
        assertEquals(PetTransition(22_000, false), manual.petTransition)
        assertEquals(manual.pet, preview.snapshot(22_500).pet)
        assertNull(preview.snapshot(24_999).pet!!.progress)
        assertNotNull(preview.snapshot(24_999).petTransition)
        assertNull(preview.snapshot(25_000).petTransition)
        val refreshed = preview.snapshot(27_500)
        assertEquals("Rabbit", refreshed.pet?.name)
        assertEquals(50, refreshed.pet?.level)
        assertEquals(.42, refreshed.pet?.progress?.fraction)
        val automatic = preview.snapshot(29_000)
        assertEquals(PetSnapshot("Tiger", 85), automatic.pet)
        assertEquals(PetTransition(29_000, true), automatic.petTransition)
        assertEquals(automatic.pet, preview.snapshot(29_500).pet)
        assertNull(preview.snapshot(32_000).petTransition)
        assertEquals(.56, preview.snapshot(34_500).pet?.progress?.fraction)
    }

    @Test fun `pet transitions preserve unknown boss and independent SkyBlock snapshot without level up`() {
        val preview = SlayerPetPreviewTimeline(0)
        for (at in listOf(22_000L, 22_500L, 25_000L, 29_000L, 29_500L, 32_000L, 35_000L)) {
            val snapshot = preview.snapshot(at)
            assertEquals(UUID(0, 911), snapshot.bosses.single().id)
            assertEquals(.64f, snapshot.bosses.single().progress)
            val skyBlock = assertIs<ProgressionView.SkyBlock>(snapshot.progression)
            assertEquals(287, skyBlock.level)
            assertEquals(.88, skyBlock.progress?.fraction)
            assertNull(snapshot.notice)
            assertNull(snapshot.activeNotice)
        }
    }

    @Test fun `sparse seeking backwards and each new cycle explicitly replay welcome and identical state`() {
        val played = SlayerPetPreviewTimeline(1_000)
        for (at in 1_000L..36_000L step 100) played.snapshot(at)
        assertEquals(played.snapshot(36_000), SlayerPetPreviewTimeline(1_000).snapshot(36_000))
        assertEquals(SlayerPetPreviewTimeline(1_000).snapshot(5_700), played.snapshot(5_700))
        val reset = played.snapshot(42_500)
        assertEquals("SYNTHETIC_SLAYER_PETS:1", reset.sessionId)
        assertEquals("Welcome to SkyBlock", reset.activeNotice?.title)
        assertEquals(42_500L, reset.activeNotice?.observedAt)
        assertNull(reset.petTransition)
        assertEquals("Tiger", reset.pet?.name)
        assertTrue(reset.bosses.isEmpty())
        assertEquals(SlayerPetPreviewTimeline(1_000).snapshot(1_000), played.snapshot(1_000))
    }

    private fun slayer(snapshot: HudSnapshot) = snapshot.widgets.single { it.id == SLAYER_WIDGET }
    private companion object { const val SLAYER_WIDGET = "skyglint.slayer.quest" }
}
