package dev.skyglint.core

import kotlin.test.*

class PetLevelUpPolicyTest {
    private fun core() = CoreController().apply { enterSkyBlock("fixture", 0) }
    private fun pet(level: Int, fraction: Double = .9, name: String = "Squid", skin: Boolean = false) =
        PetSnapshot(name, level, ProgressSnapshot(null, null, fraction), skin)

    @Test fun `explicit chat announces with item hint and reconciles level without invented zero XP`() {
        val core = core()
        core.apply(listOf(CoreEvent.PetObserved(pet(18, .99))), 1)
        assertEquals(1, core.onChat("Your Squid leveled up to level 19!", 2).size)
        val snapshot = core.snapshot(2)
        val notice = assertNotNull(snapshot.activeNotice)
        assertEquals("Pet level up!", notice.title)
        assertEquals("Squid · Level 19", notice.detail)
        assertEquals(60, notice.priority)
        assertEquals("Squid", notice.item?.itemName)
        assertEquals(19, snapshot.pet?.level)
        assertNull(snapshot.pet?.progress)
        assertNull(core.snapshot(4_002).activeNotice)
    }

    @Test fun `a late old level snapshot cannot undo fresh confirmation during grace`() {
        val core = core()
        core.apply(listOf(CoreEvent.PetObserved(pet(18))), 1)
        core.onChat("Your Squid leveled up to level 19!", 2)
        assertTrue(core.apply(listOf(CoreEvent.PetObserved(pet(18))), 1_000).isEmpty())
        assertEquals(19, core.snapshot(1_000).pet?.level)
        assertNull(core.snapshot(1_000).pet?.progress)
        core.apply(listOf(CoreEvent.PetObserved(pet(19, .02))), 1_001)
        assertEquals(.02, core.snapshot(1_001).pet?.progress?.fraction)
        core.apply(listOf(CoreEvent.PetObserved(pet(18))), 1_002)
        assertEquals(.02, core.snapshot(1_002).pet?.progress?.fraction)
        core.apply(listOf(CoreEvent.PetObserved(pet(18))), 5_002)
        assertEquals(18, core.snapshot(5_002).pet?.level) // No permanent level floor for another same-name pet.
    }

    @Test fun `a changed pet or skin and explicit unequip are not blocked by the confirmation`() {
        for (replacement in listOf(pet(12, name = "Seal"), pet(12, skin = true), null)) {
            val core = core()
            core.apply(listOf(CoreEvent.PetObserved(pet(18))), 1)
            core.onChat("Your Squid leveled up to level 19!", 2)
            core.apply(listOf(CoreEvent.PetObserved(replacement)), 3)
            assertEquals(replacement, core.snapshot(3).pet)
        }
    }

    @Test fun `snapshots and percentage rollover cannot manufacture a level up`() {
        val core = core()
        core.apply(listOf(CoreEvent.PetObserved(pet(18, .99))), 1)
        core.apply(listOf(CoreEvent.PetObserved(pet(19, .01))), 2)
        assertNull(core.snapshot(2).activeNotice)
        core.apply(listOf(CoreEvent.PetObserved(pet(19, 0.0))), 3)
        assertNull(core.snapshot(3).activeNotice)
    }

    @Test fun `repeats coalesce by pet and level and clear with session lifecycle`() {
        val core = core()
        assertEquals(1, core.onChat("Your Squid leveled up to level 19!", 1).size)
        assertTrue(core.onChat("Your Squid leveled up to level 19!", 5_000).isEmpty())
        assertNull(core.snapshot(5_000).activeNotice)
        assertEquals(1, core.onChat("Your Squid leveled up to level 20!", 5_001).size)
        core.enterSkyBlock("new-fixture", 6_000)
        assertEquals(1, core.onChat("Your Squid leveled up to level 19!", 6_001).size)
    }

    @Test fun `an inactive exp share pet may announce without replacing the active pet`() {
        val core = core()
        val active = pet(70, name = "Seal")
        core.apply(listOf(CoreEvent.PetObserved(active)), 1)
        core.onChat("Your Squid leveled up to level 19!", 2)
        assertEquals(active, core.snapshot(2).pet)
        assertEquals("Squid · Level 19", core.snapshot(2).activeNotice?.detail)
    }

    @Test fun `SkyBlock celebration stays above pet announcement and queued pet is still shown`() {
        val core = core()
        core.onChat("SKYBLOCK LEVEL UP Level 100", 1)
        core.onChat("Your Squid leveled up to level 19!", 2)
        assertEquals(100, core.snapshot(3).activeNotice?.priority)
        assertEquals("Pet level up!", core.snapshot(4_001).activeNotice?.title)
        assertEquals(60, core.snapshot(4_001).activeNotice?.priority)
    }

    @Test fun `max level and earlier authoritative new level retain observed progress`() {
        val core = core()
        core.apply(listOf(CoreEvent.PetObserved(pet(100, 1.0))), 1)
        core.onChat("Your Squid leveled up to level 100!", 2)
        assertEquals(1.0, core.snapshot(2).pet?.progress?.fraction)
        assertEquals("Squid · Level 100", core.snapshot(2).activeNotice?.detail)
        assertTrue(core.onChat("Your Squid leveled up to level 99!", 3).isEmpty())
    }

    @Test fun `invalid public domain events and absent session never announce`() {
        val core = core()
        assertTrue(core.apply(listOf(CoreEvent.PetLevelUp("", 10), CoreEvent.PetLevelUp("Squid", 0)), 1).isEmpty())
        val absent = CoreController()
        assertTrue(absent.onChat("Your Squid leveled up to level 19!", 1).isEmpty())
    }
}
