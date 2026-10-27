package dev.skyglint.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillActivityPolicyTest {
    @Test fun `single positive fishing gain remains active during a slow thirty second cast`() {
        val core = CoreController().apply { enterSkyBlock("fishing", 0) }
        core.onTabLines(listOf("SB Level: [100] 20 XP"), 1)
        core.onActionBar("+3 Fishing (50%)", 2)
        assertEquals(Skill.FISHING, assertIs<ProgressionView.SkillView>(core.snapshot(30_002).progression).skill)
        assertIs<ProgressionView.SkillView>(core.snapshot(45_001).progression)
        assertIs<ProgressionView.SkyBlock>(core.snapshot(45_002).progression)
    }

    @Test fun `real observed cadence extends lease and still expires during idle`() {
        val policy = SkillActivityPolicy()
        policy.observe(Skill.MINING, 1.0, 0, 10_000)
        assertNull(policy.active(10_000, 10_000))
        policy.observe(Skill.MINING, 1.0, 20_000, 10_000)
        assertEquals(73_000, policy.active(20_000, 10_000)!!.expiresAt)
        assertEquals(Skill.MINING, policy.active(65_000, 10_000)!!.skill)
        assertNull(policy.active(73_000, 10_000))
        policy.observe(Skill.MINING, 1.0, 200_000, 10_000)
        assertEquals(210_000, policy.active(200_000, 10_000)!!.expiresAt)
    }

    @Test fun `cadence uses recent bounded samples rather than one extreme gap forever`() {
        val policy = SkillActivityPolicy()
        for (time in listOf(0L, 60_000, 61_000, 62_000, 63_000, 64_000)) {
            policy.observe(Skill.COMBAT, 1.0, time, 10_000)
        }
        assertEquals(74_000, policy.active(64_000, 10_000)!!.expiresAt)
        val slow = SkillActivityPolicy()
        slow.observe(Skill.FISHING, 1.0, 0, 10_000)
        slow.observe(Skill.FISHING, 1.0, 80_000, 10_000)
        assertEquals(200_000, slow.active(80_000, 10_000)!!.expiresAt)
        assertNull(slow.active(200_000, 10_000))
    }

    @Test fun `incidental pet XP updates data without stealing primary skill or renewing its activity`() {
        val core = CoreController().apply { enterSkyBlock("fishing", 0) }
        core.onActionBar("+3 Fishing (50%)", 1)
        core.onActionBar("+1 Taming (20%)", 10_000)
        core.onActionBar("+1 Taming (21%)", 20_000)
        val view = assertIs<ProgressionView.SkillView>(core.snapshot(30_000).progression)
        assertEquals(Skill.FISHING, view.skill)
        assertNull(core.snapshot(45_001).progression)
        core.onActionBar("+10 Mining (12%)", 45_002)
        assertEquals(Skill.MINING, assertIs<ProgressionView.SkillView>(core.snapshot(45_002).progression).skill)
    }

    @Test fun `snapshots and contextual location never create activity or refresh its time`() {
        val core = CoreController().apply { enterSkyBlock("island", 0) }
        core.apply(listOf(CoreEvent.ContextObserved("Fishing Pond", "hub")), 1)
        core.onTabLines(listOf("Fishing 20: 50%"), 2)
        assertNull(core.snapshot(30_000).progression)
        assertEquals("Fishing Pond", core.snapshot(30_000).location)
        core.onActionBar("+1 Fishing (51%)", 30_001)
        core.apply(listOf(CoreEvent.ContextObserved("Fisherman's Hut", "hub")), 60_000)
        assertIs<ProgressionView.SkillView>(core.snapshot(70_000).progression)
        assertNull(core.snapshot(75_001).progression)
    }

    @Test fun `context mismatch only shortens old lease and fresh real activity can override context`() {
        val core = CoreController().apply { enterSkyBlock("island", 0) }
        core.apply(listOf(CoreEvent.ContextObserved("Fishing Pond", "hub")), 1)
        core.onActionBar("+1 Fishing (50%)", 2)
        core.apply(listOf(CoreEvent.ContextObserved("Garden", "garden")), 5_000)
        assertIs<ProgressionView.SkillView>(core.snapshot(14_999).progression)
        assertNull(core.snapshot(15_000).progression)
        core.onActionBar("+1 Fishing (51%)", 15_001)
        assertEquals(Skill.FISHING, assertIs<ProgressionView.SkillView>(core.snapshot(45_000).progression).skill)
    }

    @Test fun `reset removes cadence focus and location and invalid gains never activate zero progress`() {
        val core = CoreController().apply { enterSkyBlock("first", 0) }
        core.apply(listOf(CoreEvent.ContextObserved("Fishing Pond", "hub")), 1)
        core.onActionBar("+1 Fishing (50%)", 2)
        core.onActionBar("+1 Fishing (51%)", 30_002)
        core.enterSkyBlock("second", 30_003)
        assertNull(core.snapshot(30_003).progression)
        assertNull(core.snapshot(30_003).location)
        assertTrue(core.apply(listOf(CoreEvent.SkillGain(Skill.FISHING, 0.0, null, "zero")), 30_004).isEmpty())
        assertTrue(core.apply(listOf(CoreEvent.SkillGain(Skill.FISHING, Double.NaN, null, "invalid")), 30_005).isEmpty())
        assertNull(core.snapshot(30_005).progression)
        core.apply(listOf(CoreEvent.SkillGain(Skill.MINING, 1.0, null, "unknown progress")), 30_006)
        assertNull(assertIs<ProgressionView.SkillView>(core.snapshot(30_006).progression).progress)
        assertNull(core.snapshot(40_006).progression)
    }

    @Test fun `configured minimum is bounded and config changes apply without rewriting observations`() {
        val policy = SkillActivityPolicy()
        assertFalse(policy.observe(Skill.MINING, -1.0, 0, 10_000))
        policy.observe(Skill.MINING, 1.0, 1, 120_000)
        assertEquals(120_001, policy.active(1, 120_000)!!.expiresAt)
        assertNull(policy.active(10_001, 10_000))
        assertEquals(10_001, policy.active(1, 1_000)!!.expiresAt)
    }
}
