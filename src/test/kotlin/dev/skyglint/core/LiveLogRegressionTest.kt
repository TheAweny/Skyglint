package dev.skyglint.core

import dev.skyglint.features.RareDropSignalModule
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Minimal server-only observations from the user's 2026-10-27 capture; no identifiers or full tab. */
class LiveLogRegressionTest {
    @Test fun `objectives and guest bars retain separate UUIDs without implying completion`() {
        val core = CoreController().apply { enterSkyBlock("fixture", 0) }
        val titles = listOf("Craft a workbench", "Talk to the Pearl Dealer", "Talk to the Bartender",
            "Talk to Shaggy", "Travel to the Crimson Isle", "Talk to Elle")
        titles.forEachIndexed { index, title -> core.updateBoss(UUID(0, index.toLong()),
            "§fObjective: §e$title", 1f, "YELLOW", now = index.toLong()) }
        core.updateBoss(UUID(0, 99), "Guesting: <fixture>", 1f, "YELLOW", now = 7)
        val snapshot = core.snapshot(8)
        assertEquals(7, snapshot.bosses.size)
        assertEquals("§fObjective: §eTalk to the Pearl Dealer", snapshot.bosses[1].title)
        assertNull(snapshot.activeNotice)
        assertNull(snapshot.progression)
    }
    @Test fun `repeated combat segment with changed health does not renew or double gain`() {
        val core = CoreController().apply { enterSkyBlock("fixture", 0) }
        assertEquals(1, core.onActionBar("100/100❤   +106.9 Combat (25.26%)   100/100✎ Mana", 1).size)
        assertTrue(core.onActionBar("90/100❤   +106.9 Combat (25.26%)   90/100✎ Mana", 500).isEmpty())
        val fresh = core.onActionBar("90/100❤   +106.9 Combat (25.29%)   90/100✎ Mana", 1000)
        assertEquals(1, fresh.size)
        assertEquals(.2529, assertIs<ProgressionView.SkillView>(core.snapshot(1000).progression).progress!!.fraction, .000001)
    }
    @Test fun `four live pearl notifications now stay diagnostic without interrupting HUD`() {
        val core = CoreController().apply {
            rewardPolicy = RewardRulePolicy(RewardMode.FILTERED)
            modules.register(RareDropSignalModule())
            enterSkyBlock("fixture", 0)
        }
        repeat(4) { index ->
            val time = index * 60_000L
            val result = assertIs<CoreEvent.RewardObserved>(core.onChat("RARE DROP! Enchanted Ender Pearl", time).single())
            assertEquals(false, result.decision?.shown)
            assertEquals("frequent_item_default", result.decision?.reason)
            assertNull(core.snapshot(time).activeNotice)
        }
    }
}
