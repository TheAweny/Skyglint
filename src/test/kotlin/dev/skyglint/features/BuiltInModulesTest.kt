package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class BuiltInModulesTest {
    @Test fun `feature installation preserves baseline confirmations once and keeps XP out of freshness replay`() {
        val registry = ModuleRegistry.defaults()
        val contextualIds = BuiltInModules.registerFeatures(registry, { 0L })
        val xp = NormalizedSignal.ActionBar("+1 Mining (50%)   +10 SkyBlock XP (Bestiary) (25/100)")
        val observed = registry.process(xp)
        assertEquals(1, observed.filterIsInstance<CoreEvent.SkillGain>().size)
        assertEquals(1, observed.filterIsInstance<CoreEvent.SkyBlockGain>().size)
        // Context modules may legitimately retire their absent widgets; they must never replay gains.
        assertNoProgressOrRewardReplay(registry.processSelected(xp, contextualIds))
        assertEquals(listOf(CoreEvent.SkillLevelUp(Skill.MINING, 21)),
            registry.process(NormalizedSignal.Chat("SKILL LEVEL UP Mining XX➜XXI")).filterIsInstance<CoreEvent.SkillLevelUp>())
        assertEquals(listOf(CoreEvent.PetLevelUp("Squid", 19)),
            registry.process(NormalizedSignal.Chat("Your Squid leveled up to level 19!")).filterIsInstance<CoreEvent.PetLevelUp>())
        assertNoProgressOrRewardReplay(registry.processSelected(NormalizedSignal.Chat("Your Squid leveled up to level 19!"), contextualIds))
        val reward = NormalizedSignal.Chat("RARE DROP! Summoning Eye")
        assertEquals(1, registry.process(reward).filterIsInstance<CoreEvent.RewardObserved>().size)
        assertNoProgressOrRewardReplay(registry.processSelected(reward, contextualIds))
    }

    @Test fun `installed stateful features cannot carry a quest into a new session`() {
        var time = 0L
        val core = CoreController()
        BuiltInModules.registerFeatures(core.modules, { time })
        core.enterSkyBlock("first", time)
        core.ingest(NormalizedSignal.ContextSnapshot("The End", "combat_3"), time)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Slayer Quest", "Voidgloom Seraph IV", "Slay the boss!")), time)
        assertTrue(core.snapshot(time).widgets.any { it.title.contains("Voidgloom") })
        time = 1_000
        core.enterSkyBlock("second", time)
        core.ingest(NormalizedSignal.ContextSnapshot("The End", "combat_3"), time)
        assertTrue(core.snapshot(time).widgets.none { it.title.contains("Voidgloom") })
    }

    @Test fun `compatibility level up adapters retain the same strict confirmation rules as defaults`() {
        for (signal in listOf(
            NormalizedSignal.Chat("SKILL LEVEL UP Mining XX➜XXI"),
            NormalizedSignal.Chat("Your Squid leveled up to level 19!"),
            NormalizedSignal.Chat("Your Squid leveled up to level 19!\n"),
            NormalizedSignal.ActionBar("+1 Mining (0%)"),
            NormalizedSignal.TabSnapshot(listOf("Pet:", "[Lvl 19] Squid")),
        )) {
            val compatibility = PetLevelUpModule().parse(signal) + SkillLevelUpModule().parse(signal)
            val baseline = ModuleRegistry.defaults().process(signal).filter {
                it is CoreEvent.SkillLevelUp || it is CoreEvent.PetLevelUp
            }
            assertEquals(compatibility, baseline)
        }
    }

    private fun assertNoProgressOrRewardReplay(events: List<CoreEvent>) {
        assertTrue(events.none { it is CoreEvent.SkillGain || it is CoreEvent.SkyBlockGain ||
            it is CoreEvent.SkillLevelUp || it is CoreEvent.SkyBlockLevelUp || it is CoreEvent.PetLevelUp ||
            it is CoreEvent.RewardObserved }, "Freshness selection replayed progression or a reward: $events")
    }
}
