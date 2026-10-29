package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class RewardCarouselPresentationTest {
    @Test fun `same origin loot is one carousel with stable identity and individually colored names plus neutral tails`() {
        val rewards = listOf(RewardObservation("Short", "Drop", "burst", rarity = "EPIC", amount = 3),
            RewardObservation("A considerably longer reward", "Drop", "burst", magicFind = 123, amount = 12))
        val core = CoreController().apply { enterSkyBlock("test", 0) }
        core.apply(rewards.map { CoreEvent.RewardObserved(it) }, 0)
        val state = core.snapshot(0)
        assertEquals(rewards, state.activeNotice!!.loot)
        val compose = HudComposition()
        val first = compose.compose(state, 0).single()
        val second = compose.compose(core.snapshot(1_800), 1_800).single()
        assertEquals(HudElementKind.CAROUSEL, first.kind)
        assertEquals("Received", first.title)
        assertEquals(first.identity, second.identity)
        assertEquals(first.parts, second.parts)
        assertEquals(first.visibleContentKey(0), second.visibleContentKey(0))
        assertEquals(0, first.carouselIndex)
        assertEquals(1, second.carouselIndex)
        assertEquals("Short", first.parts[0].text)
        assertEquals(" · ×3", first.parts[0].tail)
        assertTrue(first.parts[0].colored)
        assertEquals(RarityPalette.color("EPIC"), first.parts[0].accent)
        assertFalse(first.parts[1].colored)
        assertEquals(" · ×12 · +123% MF", first.parts[1].tail)
        assertTrue(first.parts.none { it.text.contains("EPIC") })
    }

    @Test fun `priority notice resumes carousel at its elapsed page instead of restarting loot`() {
        val core = CoreController().apply { enterSkyBlock("test", 0) }
        val rewards = (1..4).map { RewardObservation("Item $it", "Drop", "one", amount = it.toLong()) }
        core.apply(rewards.map { CoreEvent.RewardObserved(it) }, 0)
        core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("urgent", "Urgent", priority = 90, durationMillis = 1_000))), 2_000)
        val state = core.snapshot(3_000)
        assertEquals(2_000, state.activeNotice!!.presentationOffsetMillis)
        val shown = HudComposition().compose(state, 3_000).single()
        assertEquals(HudElementKind.CAROUSEL, shown.kind)
        assertEquals(1, shown.carouselIndex)
        assertEquals("Item 2", shown.parts[shown.carouselIndex].text)
    }
}
