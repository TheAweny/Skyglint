package dev.skyglint.core

import kotlin.test.*

class SkillLevelUpPolicyTest {
    private fun core() = CoreController().apply { enterSkyBlock("fixture", 0) }

    @Test fun `explicit skill level up announces native icon and discards old level XP`() {
        val core = core()
        core.onActionBar("+1 Mining (99%)", 1)
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 20, ProgressSnapshot(99.0, 100.0, .99)))), 2)
        assertEquals(1, core.onChat("SKILL LEVEL UP Mining XX➜XXI", 3).size)
        val snapshot = core.snapshot(3)
        assertEquals("Skill level up!", snapshot.activeNotice?.title)
        assertEquals("Mining · Level 21", snapshot.activeNotice?.detail)
        assertEquals("minecraft:diamond_pickaxe", snapshot.activeNotice?.item?.vanillaItemId)
        assertEquals(60, snapshot.activeNotice?.priority)
        val skill = assertIs<ProgressionView.SkillView>(snapshot.progression)
        assertEquals(21, skill.level)
        assertNull(skill.progress)
    }

    @Test fun `five second grace rejects old level snapshots and accepts new progress immediately`() {
        val core = core()
        core.onActionBar("+1 Mining (99%)", 1)
        core.onChat("SKILL LEVEL UP Mining XX➜XXI", 2)
        assertTrue(core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 20,
            ProgressSnapshot(null, null, .99)))), 3).isEmpty())
        assertEquals(21, assertIs<ProgressionView.SkillView>(core.snapshot(3).progression).level)
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 21, ProgressSnapshot(null, null, .01)))), 4)
        assertEquals(.01, assertIs<ProgressionView.SkillView>(core.snapshot(4).progression).progress?.fraction)
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 20, ProgressSnapshot(null, null, .99)))), 5_002)
        assertEquals(20, assertIs<ProgressionView.SkillView>(core.snapshot(5_002).progression).level)
    }

    @Test fun `snapshot increase and rollover do not manufacture notice or activity`() {
        val core = core()
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 20, ProgressSnapshot(null, null, .99)))), 1)
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 21, ProgressSnapshot(null, null, .01)))), 2)
        assertNull(core.snapshot(2).activeNotice)
        assertNull(core.snapshot(2).progression)
        core.onChat("SKILL LEVEL UP Farming I➜II", 3)
        assertNull(core.snapshot(3).progression)
    }

    @Test fun `repeat dedupe is independent across skills and resets with session`() {
        val core = core()
        assertEquals(1, core.onChat("SKILL LEVEL UP Mining XX➜XXI", 1).size)
        assertTrue(core.onChat("SKILL LEVEL UP Mining XX➜XXI", 5_000).isEmpty())
        assertEquals(1, core.onChat("SKILL LEVEL UP Farming XX➜XXI", 5_001).size)
        core.enterSkyBlock("new", 6_000)
        assertEquals(1, core.onChat("SKILL LEVEL UP Mining XX➜XXI", 6_001).size)
    }

    @Test fun `all current skills get native item hints and SkyBlock priority stays higher`() {
        for (skill in Skill.entries) {
            val core = core()
            core.onChat("SKILL LEVEL UP ${skill.displayName} I➜II", 1)
            assertTrue(core.snapshot(1).activeNotice?.item?.vanillaItemId?.startsWith("minecraft:") == true)
        }
        val core = core()
        core.onChat("SKYBLOCK LEVEL UP Level 100", 1)
        core.onChat("SKILL LEVEL UP Mining XX➜XXI", 2)
        assertEquals(100, core.snapshot(3).activeNotice?.priority)
        assertEquals("Skill level up!", core.snapshot(4_001).activeNotice?.title)
    }

    @Test fun `already observed new progress survives confirmation and lower stale confirmation is rejected`() {
        val core = core()
        core.onActionBar("+1 Mining (1%)", 1)
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 21, ProgressSnapshot(null, null, .01)))), 2)
        core.onChat("SKILL LEVEL UP Mining XX➜XXI", 3)
        assertEquals(.01, assertIs<ProgressionView.SkillView>(core.snapshot(3).progression).progress?.fraction)
        assertTrue(core.onChat("SKILL LEVEL UP Mining XIX➜XX", 4).isEmpty())
        assertTrue(core.apply(listOf(CoreEvent.SkillLevelUp(Skill.MINING, 0)), 5).isEmpty())
    }
}
