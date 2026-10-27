package dev.skyglint.features.mining

import dev.skyglint.core.*
import kotlin.test.*

class RandomMiningEventModuleTest {
    private var now = 0L
    private val module = RandomMiningEventModule { now }
    private fun context(mode: String?) = module.parse(NormalizedSignal.ContextSnapshot("Dwarven Mines", mode))
    private fun chat(text: String = "A Golden Goblin has spawned!") = module.parse(NormalizedSignal.Chat(text))

    @Test fun `spawn creates only a bounded short notice with an icon and no reward prefix or timer`() {
        context("mining_3")
        val notice = chat().single() as CoreEvent.NoticeObserved
        assertEquals("Golden Goblin spawned", notice.request.title)
        assertEquals(4_000, notice.request.durationMillis)
        assertEquals(8_000, notice.request.freshnessMillis)
        assertNull(notice.request.item?.itemName)
        assertEquals("minecraft:gold_nugget", notice.request.item?.vanillaItemId)
        assertTrue(module.parse(NormalizedSignal.BossSnapshot(emptyList())).isEmpty())
    }

    @Test fun `same occurrence burst is coalesced without suppressing later genuine spawns`() {
        context("crystal_hollows")
        val first = (chat().single() as CoreEvent.NoticeObserved).request.id
        now = 500
        assertTrue(chat().isEmpty())
        assertEquals(1, chat("A Diamond Goblin has spawned!").size)
        now = 2_000
        assertNotEquals(first, (chat().single() as CoreEvent.NoticeObserved).request.id)
    }

    @Test fun `official mode and fresh context are mandatory and different islands have distinct eligibility`() {
        assertTrue(chat().isEmpty())
        context(null); assertTrue(chat().isEmpty())
        context("hub"); assertTrue(chat().isEmpty())
        context("mining_2"); assertTrue(chat().isEmpty())
        context("mineshaft"); assertTrue(chat().isEmpty())
        context("crystal_hollows")
        assertTrue(chat("WOW! You found a Glacite Mineshaft portal!").isEmpty())
        assertTrue(chat("Find the Powder Ghast near the Forge Basin!").isEmpty())
        context("mining_3")
        assertEquals(1, chat("WOW! You found a Glacite Mineshaft portal!").size)
        now = 5_000
        assertTrue(chat().isEmpty())
    }

    @Test fun `server lifecycle clears context and duplicate memory and subarea updates preserve dedupe`() {
        context("mining_3"); assertEquals(1, chat().size)
        module.parse(NormalizedSignal.ContextSnapshot("Royal Mines", "mining_3"))
        assertTrue(chat().isEmpty())
        module.onSessionEnded()
        assertTrue(chat().isEmpty())
        module.onSessionStarted("new-server")
        assertTrue(chat().isEmpty())
        context("mining_3"); assertEquals(1, chat().size)
    }
}
