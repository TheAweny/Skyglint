package dev.skyglint.core

import dev.skyglint.features.ContextModules
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class PetSwitchPolicyTest {
    @Test fun `actual selected menu confirms unknown manual pet and can clear selection`() {
        val core = CoreController().apply { enterSkyBlock("test", 0) }
        core.apply(listOf(CoreEvent.PetSwitched(PetSnapshot("Tiger", null))), 0)
        core.apply(listOf(CoreEvent.PetObserved(PetSnapshot("Seal", 70), authoritative = true)), 50)
        assertEquals(PetSnapshot("Tiger", null), core.snapshot(50).pet)
        val selected = PetSnapshot("Tiger", 20, ProgressSnapshot(null, null, .2))
        core.apply(listOf(CoreEvent.PetObserved(selected, authoritative = true)), 100)
        assertEquals(selected, core.snapshot(100).pet)
        assertTrue(core.apply(listOf(CoreEvent.PetSwitched(PetSnapshot("Tiger", null))), 150).isEmpty())
        assertEquals(selected, core.snapshot(150).pet)
        core.apply(listOf(CoreEvent.PetObserved(PetSnapshot("Tiger", 70))), 200)
        assertEquals(selected, core.snapshot(200).pet)
        core.apply(listOf(CoreEvent.PetObserved(null, authoritative = true)), 300)
        assertNull(core.snapshot(300).pet)
        assertNull(core.snapshot(300).petTransition)
    }
    @Test fun `manual same species switch discards old level XP and ignores stale tab during authority`() {
        val core = CoreController().apply { enterSkyBlock("test", 0) }
        val old = PetSnapshot("Tiger", 70, ProgressSnapshot(null, null, .8))
        core.apply(listOf(CoreEvent.PetObserved(old)), 0)
        core.apply(listOf(CoreEvent.PetSwitched(PetSnapshot("Tiger", null))), 100)
        assertNull(core.snapshot(100).pet?.level)
        assertNull(core.snapshot(100).pet?.progress)
        core.apply(listOf(CoreEvent.PetObserved(old)), 1_000)
        assertNull(core.snapshot(1_000).pet?.level)
        core.apply(listOf(CoreEvent.PetObserved(null)), 2_000)
        assertNotNull(core.snapshot(2_000).pet)
        core.apply(listOf(CoreEvent.PetObserved(PetSnapshot("Tiger", 20))), 5_100)
        assertEquals(20, core.snapshot(5_100).pet?.level)
    }

    @Test fun `confirmed switch expands only pet while boss and independent skill remain`() {
        var now = 0L
        val core = CoreController().apply {
            ContextModules.create { now }.forEach(modules::register)
            enterSkyBlock("test", now)
            updateBoss(java.util.UUID(0, 819), "Unknown boss", .5f, now = now)
            onActionBar("+1 Combat (10%)", now)
        }
        now = 100
        core.onChat("Autopet equipped your [Lvl 70] Tiger! VIEW RULE", now)
        val composer = HudComposition()
        val shown = composer.compose(core.snapshot(now), now)
        val pet = shown.single { it.id == "pet" }
        assertEquals(HudElementKind.MAIN, pet.kind)
        assertEquals("Tiger · Lv. 70", pet.title)
        assertTrue(shown.any { it.id == "main" } && shown.any { it.id == "skill" })
        assertNull(core.snapshot(now).activeNotice)
        now = 3_100
        val circle = composer.compose(core.snapshot(now), now).single { it.id == "pet" }
        assertEquals(HudElementKind.PROGRESS, circle.kind)
        assertEquals(pet.identity, circle.identity)
        assertNull(circle.progress)
    }

    @Test fun `duplicates cannot extend intro or block later true switch and confirmed level up`() {
        val core = CoreController().apply { enterSkyBlock("test", 0) }
        val switched = CoreEvent.PetSwitched(PetSnapshot("Tiger", 70), true)
        core.apply(listOf(switched), 0)
        assertTrue(core.apply(listOf(switched), 2_000).isEmpty())
        assertNull(core.snapshot(3_000).petTransition)
        core.onChat("Your Tiger leveled up to level 71!", 3_100)
        assertEquals(71, core.snapshot(3_100).pet?.level)
        core.apply(listOf(CoreEvent.PetObserved(PetSnapshot("Tiger", 70))), 3_200)
        assertEquals(71, core.snapshot(3_200).pet?.level)
        core.apply(listOf(CoreEvent.PetSwitched(PetSnapshot("Seal", null))), 4_000)
        assertEquals("Seal", core.snapshot(4_000).pet?.name)
        core.enterSkyBlock("new", 5_000)
        assertNull(core.snapshot(5_000).petTransition)
        assertNull(core.snapshot(5_000).pet)
    }
}
