package dev.skyglint.features.winter

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.HudElementKind
import kotlin.test.*

class JerryDefenseResultModuleTest {
    private var now = 0L
    private val module = JerryDefenseResultModule { now }
    private val block = listOf("WAVE 3 - YOU WON!", "Snowballs Hit: 0", "Snowballs Thrown: 0",
        "Snowball Accuracy: N/A", "Total Damage: 732 (NEW RECORD!) (Position #4)",
        "Magma Cube Damage: 0", "Snow Cannonball Hits: 65 (NEW RECORD!)", "",
        "White Gift x34", "Volcanic Rock x5", "Red Gift x8", "Green Gift x30", "",
        "Collect your Gifts at the Gift piles!")
    private fun context(mode: String? = "winter") = module.parse(NormalizedSignal.ContextSnapshot("Jerry's Workshop", mode))
    private fun chat(line: String) = module.parse(NormalizedSignal.Chat(line))
    private fun result() = block.flatMap(::chat)

    @Test fun `captured real block produces server statistics and four claimable rewards without inventory receipt`() {
        context()
        val events = result()
        val stats = events.filterIsInstance<CoreEvent.NoticeObserved>().single().request
        assertEquals(4, stats.pages.size)
        assertTrue(stats.pages.any { "N/A" in it.title })
        assertTrue(stats.pages.any { "#4" in it.title && "Record" in it.title })
        val rewards = events.filterIsInstance<CoreEvent.RewardObserved>().map { it.observation }
        assertEquals(mapOf("White Gift" to 34L, "Volcanic Rock" to 5L, "Red Gift" to 8L, "Green Gift" to 30L), rewards.associate { it.itemName to it.amount })
        assertTrue(rewards.all { it.delivery == RewardDelivery.CLAIMABLE && it.rarity == null })
        assertTrue(events.none { it is CoreEvent.WidgetObserved || it is CoreEvent.SkillGain })
        assertTrue(result().isEmpty())
    }

    @Test fun `individual fields empty partial stale or wrong context do not manufacture a completed result`() {
        context()
        assertTrue(chat("White Gift x34").isEmpty())
        assertTrue(chat("Collect your Gifts at the Gift piles!").isEmpty())
        assertTrue(chat("WAVE 3 - YOU WON!").isEmpty())
        chat("White Gift x34")
        now = 8_000
        context()
        assertTrue(chat("Collect your Gifts at the Gift piles!").isEmpty())
        module.onSessionEnded()
        assertTrue(result().isEmpty())
        context("hub")
        assertTrue(result().isEmpty())
    }

    @Test fun `framed multiline packet works and conflicting fields invalidate the block`() {
        context()
        assertEquals(5, chat(block.joinToString("\n")).size)
        module.onSessionStarted("new")
        context()
        chat("WAVE 3 - YOU WON!"); chat("White Gift x34"); chat("White Gift x35")
        assertTrue(chat("Collect your Gifts at the Gift piles!").isEmpty())
        chat("WAVE 3 - YOU WON!"); chat("White Gift x34"); chat("White Gift x34")
        assertEquals(1, chat("Collect your Gifts at the Gift piles!").size)
    }

    @Test fun `new explicit wave attempt rearms same wave while repeated delivery does not`() {
        context(); result()
        assertTrue(result().isEmpty())
        chat("DEFEND JERRY'S WORKSHOP - WAVE 3")
        assertEquals(5, result().size)
        chat("DEFEND JERRY'S WORKSHOP - WAVE 3")
        assertTrue(result().isEmpty())
        module.onSessionStarted("another server"); context()
        assertEquals(5, result().size)
    }

    @Test fun `starting the next wave cannot replay a previous wave reward block`() {
        context(); result()
        chat("DEFEND JERRY'S WORKSHOP - WAVE 4")
        assertTrue(result().isEmpty())
        assertEquals(5, block.map { it.replace("WAVE 3", "WAVE 4") }.flatMap(::chat).size)
    }

    @Test fun `claimable loot follows existing item policy and all surviving items plus statistics remain queued`() {
        context()
        val core = CoreController().apply {
            enterSkyBlock("test", now)
            rewardPolicy = RewardRulePolicy(overrides = mapOf("white gift" to RewardOverride.HIDE))
        }
        val accepted = core.apply(result(), now).filterIsInstance<CoreEvent.RewardObserved>()
        assertFalse(accepted.single { it.observation.itemName == "White Gift" }.decision!!.shown)
        assertEquals(3, accepted.count { it.decision!!.queued })
        val composer = HudComposition()
        val lootFrames = listOf(0L, 1_800L, 3_600L).map { at ->
            val snapshot = core.snapshot(at)
            val notice = snapshot.activeNotice!!
            assertEquals(listOf("Volcanic Rock", "Red Gift", "Green Gift"), notice.loot.map { it.itemName })
            assertEquals(listOf(5L, 8L, 30L), notice.loot.map { it.amount })
            assertEquals(5_400L, notice.expiresAt)
            composer.compose(snapshot, at).single()
        }
        assertTrue(lootFrames.all { it.kind == HudElementKind.CAROUSEL && it.title == "Available" && it.prefix == null })
        assertEquals(listOf("Volcanic Rock", "Red Gift", "Green Gift"), lootFrames.map { it.parts[it.carouselIndex].text })
        assertEquals(listOf(" · ×5", " · ×8", " · ×30"), lootFrames.map { it.parts[it.carouselIndex].tail })
        assertTrue(lootFrames.all { frame -> frame.parts.none { it.colored } })
        val statsFrames = listOf(6_400L, 8_200L, 10_000L, 11_800L).map { at ->
            val snapshot = core.snapshot(at)
            assertTrue(snapshot.activeNotice!!.id.startsWith("skyglint.winter.defense.result:"))
            assertTrue(snapshot.activeNotice!!.loot.isEmpty())
            composer.compose(snapshot, at).single().title
        }
        assertEquals(4, statsFrames.toSet().size)
        assertTrue(statsFrames.any { "732" in it && "#4" in it })
        assertTrue(statsFrames.any { "N/A" in it })
        assertNull(core.snapshot(13_600).activeNotice)
    }

    @Test fun `disabled results hide statistics and rewards while preserving policy decisions and dedupe`() {
        context()
        val core = CoreController().apply { enterSkyBlock("test", now); noticePreferences = NoticePreferences(showJerryResults = false) }
        val accepted = core.apply(result(), now).filterIsInstance<CoreEvent.RewardObserved>()
        assertEquals(4, accepted.size)
        assertTrue(accepted.all { it.decision!!.shown && !it.decision.queued })
        assertNull(core.snapshot(now).activeNotice)
        core.noticePreferences = NoticePreferences()
        assertTrue(core.apply(result(), now).isEmpty())
        assertNull(core.snapshot(now).activeNotice)
    }

    @Test fun `observed rarity colors only the claimable item and absent fields remain absent`() {
        context()
        chat("WAVE 3 - YOU WON!"); chat("§aGreen Gift §fx30")
        val reward = assertIs<CoreEvent.RewardObserved>(chat("Collect your Gifts at the Gift piles!").single()).observation
        assertEquals("UNCOMMON", reward.rarity)
        val core = CoreController().apply { enterSkyBlock("test", now) }
        core.apply(listOf(CoreEvent.RewardObserved(reward)), now)
        val element = HudComposition().compose(core.snapshot(now), now).single()
        assertEquals("Green Gift", element.title)
        assertEquals("Green Gift", element.coloredText)
        assertEquals("×30", element.value)
        assertFalse(element.title.contains("UNCOMMON"))
    }
}
