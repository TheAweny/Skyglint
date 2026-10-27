package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class SlayerPetCompositionTest {
    @Test fun `quest preparation and fight retain distinct progress semantics without false HP`() {
        val preview = SlayerPetPreviewTimeline(0)
        val composer = HudComposition()
        val preparation = composer.compose(preview.snapshot(9_000), 9_000)
        val quest = preparation.single { it.kind == HudElementKind.MAIN }
        assertEquals("Voidgloom Seraph IV · 1.2k/21k Combat XP", quest.title)
        assertEquals(1_200.0 / 21_000, quest.progress!!, .000001)
        assertTrue(preparation.any { it.id == "pet" })
        val fight = composer.compose(preview.snapshot(14_000), 14_000).single { it.kind == HudElementKind.MAIN }
        assertEquals("Voidgloom Seraph IV · Slay the boss!", fight.title)
        assertNull(fight.progress)
    }

    @Test fun `Slayer preserves and rotates multiple unknown server UUIDs in the ordinary row`() {
        val snapshot = SlayerPetPreviewTimeline(0).snapshot(14_000)
        val bosses = listOf(BossBarState(java.util.UUID(0, 1), "Unknown boss A", .5f),
            BossBarState(java.util.UUID(0, 2), "Unknown boss B", .4f))
        val state = snapshot.copy(bosses = bosses, widgets = snapshot.widgets.map { it.copy(expiresAt = null) })
        val composer = HudComposition()
        val mains = listOf(14_000L, 20_000, 26_000).map { at -> composer.compose(state, at)
            .single { it.id == "main" }.title }
        assertTrue(mains.first().contains("Slay the boss!"))
        assertEquals(listOf("Unknown boss A", "Unknown boss B"), mains.drop(1))
    }

    @Test fun `pet introduction still respects dungeon election and user pet visibility`() {
        val snapshot = SlayerPetPreviewTimeline(0).snapshot(29_500)
        val composer = HudComposition()
        assertTrue(composer.compose(snapshot, 29_500, showPets = false).none { it.id == "pet" })
        assertTrue(composer.compose(snapshot.copy(location = "Community Center"), 29_500).none { it.id == "pet" })
        val dungeon = ContextPreviewTimeline(0, true).snapshot(0).copy(petTransition = PetTransition(0, true))
        assertTrue(composer.compose(dungeon, 500).none { it.id == "pet" })
    }
}
