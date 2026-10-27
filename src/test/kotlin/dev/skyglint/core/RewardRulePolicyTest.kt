package dev.skyglint.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardRulePolicyTest {
    private fun reward(name: String, source: String = "rare_drop") = RewardObservation(name, "Drop found", source)

    @Test fun `explicit filtered mode suppresses exact frequent names without guessing other items value`() {
        val policy = RewardRulePolicy(RewardMode.FILTERED)
        for (name in listOf("Ender Pearl", "Enchanted Ender Pearl", "Eye of Ender", "Rotten Flesh",
            "Enchanted Rotten Flesh", "Enchanted End Stone", "Crystal Fragment")) {
            assertFalse(policy.evaluate(reward(name)).shown, name)
        }
        for (name in listOf("Summoning Eye", "Enchanted Book", "Sorrow", "Baby Yeti", "Future Item",
            "Refined Crystal Fragment", "Rotten Flesh Talisman")) {
            assertTrue(policy.evaluate(reward(name)).shown, name)
        }
        assertEquals("unfiltered_item", policy.evaluate(reward("Future Item")).reason)
    }

    @Test fun `formatting normalization is exact and coin default requires confirmed coin source`() {
        val policy = RewardRulePolicy(RewardMode.FILTERED)
        assertFalse(policy.evaluate(reward("§aENCHANTED\u00a0ENDER  PEARL")).shown)
        assertFalse(policy.evaluate(reward("Coins", "fishing_treasure_coins")).shown)
        assertTrue(policy.evaluate(reward("Coins", "custom_reward")).shown)
        assertTrue(policy.evaluate(reward("Coin Talisman")).shown)
        assertFalse(policy.evaluate(reward("Player\nReward")).shown)
        assertEquals("invalid_item_name", policy.evaluate(reward(" ")).reason)
    }

    @Test fun `explicit per item choices override both filtered and all modes and reset restores defaults`() {
        val choices = mapOf("Enchanted Ender Pearl" to RewardOverride.SHOW, "Sorrow" to RewardOverride.HIDE)
        for (mode in RewardMode.entries) {
            val policy = RewardRulePolicy(mode, choices)
            assertEquals("item_override_show", policy.evaluate(reward("Enchanted Ender Pearl")).reason)
            assertTrue(policy.evaluate(reward("Enchanted Ender Pearl")).shown)
            assertFalse(policy.evaluate(reward("Sorrow")).shown)
        }
        assertTrue(RewardRulePolicy(RewardMode.ALL).evaluate(reward("Rotten Flesh")).shown)
        assertFalse(RewardRulePolicy(RewardMode.FILTERED).evaluate(reward("Rotten Flesh")).shown)
        assertTrue(RewardRulePolicy().evaluate(reward("Sorrow")).shown)
    }

    @Test fun `hidden observations retain diagnostic decisions without entering presentation queue`() {
        val core = CoreController().apply { rewardPolicy = RewardRulePolicy(RewardMode.FILTERED); enterSkyBlock("session", 0) }
        val hidden = CoreEvent.RewardObserved(reward("Enchanted Ender Pearl"))
        val result = core.apply(listOf(hidden), 1).single() as CoreEvent.RewardObserved
        assertFalse(result.decision!!.shown)
        assertFalse(result.decision!!.queued)
        assertEquals("frequent_item_default", result.decision!!.reason)
        assertEquals(hidden.observation, result.observation)
        assertNull(core.snapshot(2).activeNotice)
        core.rewardPolicy = RewardRulePolicy.fromConfig("filtered", mapOf("Enchanted Ender Pearl" to "show"))
        val shown = core.apply(listOf(hidden), 3).single() as CoreEvent.RewardObserved
        assertTrue(shown.decision!!.shown)
        assertTrue(shown.decision!!.queued)
        assertEquals("Enchanted Ender Pearl", core.snapshot(4).activeNotice!!.detail)
    }

    @Test fun `metadata survives accepted rewards and duplicate batch observations record queue decisions independently`() {
        val core = CoreController().apply { enterSkyBlock("session", 0) }
        val event = CoreEvent.RewardObserved(RewardObservation("Enchanted Book", "Great catch!", "fishing_treasure_item",
            "minecraft:enchanted_book", 208, "EPIC", 1))
        val result = core.apply(listOf(event, event), 1).map { it as CoreEvent.RewardObserved }
        assertEquals(2, result.size)
        assertTrue(result[0].decision!!.queued)
        assertFalse(result[1].decision!!.queued)
        assertTrue(result[1].decision!!.shown)
        val notice = core.snapshot(2).activeNotice!!
        assertEquals("Great catch!", notice.title)
        assertEquals("Enchanted Book", notice.item!!.itemName)
        assertEquals("minecraft:enchanted_book", notice.item!!.vanillaItemId)
        assertEquals(208, notice.magicFind)
        assertEquals("EPIC", notice.rarity)
        assertEquals(1, result[0].observation.amount)
    }

    @Test fun `all rewards are shown by default regardless of rarity or frequent source`() {
        val policy = RewardRulePolicy()
        assertEquals(RewardMode.ALL, policy.mode)
        for (name in listOf("Ender Pearl", "Enchanted Ender Pearl", "Eye of Ender", "Rotten Flesh",
            "Enchanted Rotten Flesh", "Enchanted End Stone", "Crystal Fragment", "Summoning Eye")) {
            assertTrue(policy.evaluate(reward(name)).shown, name)
            assertEquals("all_rewards_mode", policy.evaluate(reward(name)).reason)
        }
        assertTrue(policy.evaluate(reward("Coins", "fishing_treasure_coins")).shown)
        for (rarity in listOf(null, "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")) {
            assertTrue(policy.evaluate(reward("Any Item").copy(rarity = rarity)).shown)
        }
        assertEquals(RewardMode.ALL, RewardRulePolicy.fromConfig("unknown", emptyMap()).mode)
        assertEquals(RewardMode.FILTERED, RewardRulePolicy.fromConfig("FILTERED", emptyMap()).mode)
    }
}
