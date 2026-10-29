package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.features.ContextModules
import dev.skyglint.features.RareDropSignalModule
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class FishingPresentationTest {
    private var now = 0L
    private fun core() = CoreController().apply {
        ContextModules.create { now }.forEach(modules::register)
        modules.register(RareDropSignalModule())
        enterSkyBlock("synthetic-fishing", now)
        ingest(NormalizedSignal.ContextSnapshot("Backwater Bayou", "fishing_1"), now)
        apply(listOf(CoreEvent.ContextObserved("Backwater Bayou", "fishing_1"),
            CoreEvent.SkyBlockObserved(SkyBlockSnapshot(287, ProgressSnapshot(88.0, 100.0, .88))),
            CoreEvent.PetObserved(PetSnapshot("Squid", 70, ProgressSnapshot(null, null, .72)))), now)
        updateBoss(java.util.UUID(0, 914), "Unknown fishing objective", .6f, now = now)
    }
    private fun bait(count: String?) = NormalizedSignal.FishingBaitSnapshot(count?.let {
        MenuItemSnapshot(8, "Fish Bait", listOf("Bait Remaining: $it", "COMMON BAIT"), "FISH_BAIT")
    }, holdingRod = true)

    @Test fun `Junk is shown by default with independent item rarity and muted source metadata`() {
        val core = core()
        core.onChat("§6⛃ §6GOOD JUNK CATCH! §fYou caught a §aRusty Coin§f!", now)
        val state = core.snapshot(now)
        assertEquals(FishingLootCategory.JUNK, state.activeNotice?.reward?.fishingCategory)
        val shown = HudComposition().compose(state, now).single()
        assertEquals("Rusty Coin", shown.title)
        assertEquals("Received · ", shown.prefix)
        assertEquals("Junk", shown.value)
        assertEquals("GOOD JUNK CATCH!", shown.subtitle)
        assertEquals(RarityPalette.color("UNCOMMON"), shown.accent)
        assertEquals("Rusty Coin", shown.coloredText)
        assertFalse(shown.title.contains("UNCOMMON"))
        assertFalse(shown.verticalTransition)
    }

    @Test fun `trophy grade shard quantity and coin amount survive the reward queue`() {
        for ((message, title, tail) in listOf(
            Triple("\uE02A TROPHY FISH! You caught Lavahorse DIAMOND!", "Lavahorse", "Diamond"),
            Triple("\uE025 GREAT CATCH! You caught Water Snake Shard x3!", "Water Snake Shard", "×3"),
            Triple("\uE025 GOOD CATCH! You caught 36,064 Coins!", "Coins", "×36,064"))) {
            val core = core()
            core.onChat(message, now)
            val shown = HudComposition().compose(core.snapshot(now), now).single()
            assertEquals(title, shown.title)
            assertEquals(tail, shown.value)
        }
    }

    @Test fun `exact Junk override is respected and filtered mode does not invent cheap Junk`() {
        val core = core()
        core.rewardPolicy = RewardRulePolicy(RewardMode.FILTERED)
        core.onChat("⛃ GOOD JUNK CATCH! You caught a Rusty Coin!", now)
        assertEquals("Rusty Coin", core.snapshot(now).activeNotice?.item?.itemName)
        core.enterSkyBlock("other", ++now)
        core.rewardPolicy = RewardRulePolicy(overrides = mapOf("Rusty Coin" to RewardOverride.HIDE))
        val event = assertIs<CoreEvent.RewardObserved>(core.onChat("⛃ GOOD JUNK CATCH! You caught a Rusty Coin!", now).single())
        assertEquals("item_override_hide", event.decision?.reason)
        assertNull(core.snapshot(now).activeNotice)
    }

    @Test fun `low bait is additive group with selected name source count and semantic color`() {
        val core = core()
        core.ingest(bait("12"), now)
        val composer = HudComposition()
        val shown = composer.compose(core.snapshot(now), now)
        val helper = shown.single { it.id == "group:fishing:bait" }
        assertEquals(HudElementKind.MAIN, helper.kind)
        assertEquals("Fish Bait", helper.title)
        assertEquals("12", helper.value)
        assertEquals("@fishing_bait", helper.icon)
        assertEquals(0xFFFFD479.toInt(), helper.accent)
        assertEquals(setOf("main", "skyblock", "pet", "group:fishing:bait"), shown.map { it.id }.toSet())
        assertNull(core.snapshot(now).activeNotice)
        now = 1_000
        core.ingest(bait("0"), now)
        assertEquals(0xFFF28E8E.toInt(), composer.compose(core.snapshot(now), now)
            .single { it.id == helper.id }.accent)
        now = 2_000
        core.ingest(bait("2,048"), now)
        val healthy = composer.compose(core.snapshot(now), now).single { it.id == helper.id }
        assertEquals(HudElementKind.COUNTER, healthy.kind)
        assertNull(healthy.progress)
        assertEquals("2048", healthy.value)
        assertTrue(healthy.liveValue)
        assertTrue(healthy.subtitle!!.contains("2048 remaining"))
        core.ingest(bait(null), ++now)
        assertTrue(composer.compose(core.snapshot(now), now).none { it.id == helper.id })
    }

    @Test fun `healthy large bait counts remain exact during introduction and settled live updates`() {
        val core = core()
        val composer = HudComposition()
        core.ingest(bait("10,000"), now)
        val intro = composer.compose(core.snapshot(now), now).single { it.id == "group:fishing:bait" }
        assertEquals(HudElementKind.MAIN, intro.kind)
        assertEquals("Fish Bait", intro.title)
        assertEquals("10000", intro.value)
        assertEquals("@fishing_bait", intro.icon)
        assertTrue(intro.liveValue)
        assertNull(intro.progress)

        now = 1_000
        core.ingest(bait("9,999"), now)
        val introUpdate = composer.compose(core.snapshot(now), now).single { it.id == intro.id }
        assertEquals(HudElementKind.MAIN, introUpdate.kind)
        assertEquals("9999", introUpdate.value)
        assertEquals(intro.visibleContentKey(0), introUpdate.visibleContentKey(0))
        assertEquals("9999", intro.displayValue(introUpdate))

        now = 2_000
        core.ingest(bait("10,000"), now)
        val settled = composer.compose(core.snapshot(now), now).single { it.id == intro.id }
        assertEquals(HudElementKind.COUNTER, settled.kind)
        assertEquals("10000", settled.value)
        assertTrue(settled.liveValue)
        assertNull(settled.progress)

        now = 2_001
        core.ingest(bait("9,999"), now)
        val settledUpdate = composer.compose(core.snapshot(now), now).single { it.id == intro.id }
        assertEquals(HudElementKind.COUNTER, settledUpdate.kind)
        assertEquals("9999", settledUpdate.value)
        assertEquals(settled.visibleContentKey(0), settledUpdate.visibleContentKey(0))
        assertEquals("9999", settled.displayValue(settledUpdate))
    }

    @Test fun `rare catch preempts nothing permanently and bait returns with current underlying XP`() {
        val core = core()
        core.ingest(bait("12"), now)
        core.onActionBar("+10 Fishing (72%)", now)
        core.onChat("The Water Hydra has come to test your strength.", now)
        val first = HudComposition().compose(core.snapshot(now), now).single()
        assertEquals("Water Hydra caught", first.title)
        assertNull(first.prefix)
        assertNull(core.snapshot(now).activeNotice?.reward)
        now = 4_000
        core.ingest(bait("10"), now)
        val shown = HudComposition().compose(core.snapshot(now), now)
        assertEquals("Unknown fishing objective", shown.single { it.id == "main" }.title)
        assertTrue(shown.any { it.id == "skill" } && shown.any { it.id == "pet" })
        assertEquals("10", shown.single { it.id == "group:fishing:bait" }.value)
    }

    @Test fun `disabled sack alone is a small item circle with toggle meaning only in tooltip`() {
        val core = core()
        core.ingest(bait(null), now)
        core.onChat("Use Baits From Sacks are now disabled!", now)
        val shown = HudComposition().compose(core.snapshot(now), now)
        val helper = shown.single { it.id == "group:fishing:bait" }
        assertEquals(HudElementKind.ICON, helper.kind)
        assertEquals("@sack_disabled", helper.icon)
        assertNull(helper.value)
        assertNull(helper.progress)
        assertTrue(helper.parts.isEmpty())
        assertTrue(helper.subtitle!!.contains("Using baits from sacks is disabled"))
        assertTrue(helper.subtitle!!.contains("contents unknown"))
        assertEquals(setOf("main", "skyblock", "pet", helper.id), shown.map { it.id }.toSet())
        assertNull(core.snapshot(now).activeNotice)
        core.onChat("Use Baits From Sacks are now enabled!", ++now)
        assertTrue(HudComposition().compose(core.snapshot(now), now).none { it.id == helper.id })
    }

    @Test fun `selected bait and disabled sack share a compact strip with no warning caption`() {
        for ((count, expectedValue, expectedAccent) in listOf(
            Triple("64", "64", 0xFFA6D9AD.toInt()),
            Triple("12", "12", 0xFFFFD479.toInt()),
            Triple("0", "0", 0xFFF28E8E.toInt()))) {
            val core = core()
            core.ingest(bait(count), now)
            core.onChat("Use Baits From Sacks are now disabled!", now)
            val helper = HudComposition().compose(core.snapshot(now), now).single { it.id == "group:fishing:bait" }
            assertEquals(HudElementKind.STRIP, helper.kind)
            assertEquals("", helper.title)
            assertNull(helper.value)
            assertNull(helper.progress)
            assertEquals(listOf("@fishing_bait", "@sack_disabled"), helper.parts.map { it.icon })
            assertEquals(listOf(expectedValue, ""), helper.parts.map { it.text })
            assertEquals(expectedAccent, helper.parts.first().accent)
            assertEquals(0xFFD3B071.toInt(), helper.parts.last().accent)
            assertTrue(helper.subtitle!!.contains("Using baits from sacks is disabled"))
            assertTrue(helper.subtitle!!.contains("$count remaining"))
            assertFalse(helper.subtitle!!.contains("Bait Sack: "))
            assertNull(core.snapshot(now).activeNotice)
        }
    }

    @Test fun `changing a healthy bait count in a sack strip keeps its content transition stable`() {
        val core = core()
        core.ingest(bait("256"), now)
        core.onChat("Use Baits From Sacks are now disabled!", now)
        val first = HudComposition().compose(core.snapshot(now), now).single { it.id == "group:fishing:bait" }
        now = 1_000
        core.ingest(bait("255"), now)
        val second = HudComposition().compose(core.snapshot(now), now).single { it.id == first.id }
        assertEquals("256", first.parts.first().text)
        assertEquals("255", second.parts.first().text)
        assertEquals(first.visibleContentKey(0), second.visibleContentKey(0))
    }
}
