package dev.skyglint.features.fishing.bait

import dev.skyglint.core.*
import kotlin.test.*

class FishingBaitModuleTest {
    private var now = 0L
    private val module = FishingBaitModule { now }
    private fun preview(count: String = "16", slot: Int = 8, id: String? = "FISH_BAIT",
                        name: String = "§fFish Bait", type: String = "COMMON BAIT") =
        MenuItemSnapshot(slot, name, listOf("§7Bait Remaining: §e$count", type), id)
    private fun snapshot(item: MenuItemSnapshot?, holdingRod: Boolean = true) =
        NormalizedSignal.FishingBaitSnapshot(item, holdingRod)
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().single().widget

    @Test fun `selected preview is an item helper with numbers and colors only when low or explicitly empty`() {
        for (count in listOf("16", "1", "0")) {
            val events = module.parse(snapshot(preview(count)))
            assertEquals(1, events.size)
            val widget = events.widget()
            assertEquals(WidgetRole.COUNTERS, widget.presentation!!.role)
            assertNull(widget.progress) // count is known; capacity is not.
            val metric = widget.presentation!!.metrics.single()
            assertEquals("Fish Bait", metric.label)
            assertEquals("bait_selected", metric.id)
            assertEquals(count, metric.value)
            assertEquals("@fishing_bait", metric.icon)
            assertEquals(WidgetMetricFormat.ITEM, metric.format)
            assertEquals(2_000L, metric.expandedUntil)
            assertEquals(FishingBaitModule.GROUP, metric.group)
            assertEquals(3_000L, widget.expiresAt)
            assertNotNull(metric.accent)
            assertEquals(count == "0", metric.detail!!.contains("Depleted"))
            assertTrue(events.none { it is CoreEvent.NoticeObserved || it is CoreEvent.SkillGain || it is CoreEvent.RewardObserved })
        }
        for (count in listOf("17", "10,000")) {
            val metric = module.parse(snapshot(preview(count))).widget().presentation!!.metrics.single()
            assertEquals("", metric.value)
            assertNull(metric.accent)
            assertTrue(metric.detail!!.contains(count.replace(",", "")))
        }
        assertEquals(listOf(CoreEvent.WidgetRemoved(FishingBaitModule.WIDGET_ID)), module.parse(snapshot(null)))
    }

    @Test fun `inventory or bag contents and catch messages never establish selected bait`() {
        val ordinary = preview()
        for (signal in listOf(NormalizedSignal.InventorySnapshot(listOf(ordinary)),
            NormalizedSignal.MenuSnapshot("Fishing Bag", listOf(ordinary)),
            NormalizedSignal.HeldItemSnapshot(ordinary), NormalizedSignal.Chat("GOOD CATCH! You found a Fish Bait."),
            NormalizedSignal.Chat("You have ran out of Fish Bait!"),
            NormalizedSignal.ActionBar("+100 Fishing (1,000/2,000)"))) {
            assertTrue(module.parse(signal).isEmpty())
        }
        assertEquals(listOf(CoreEvent.WidgetRemoved(FishingBaitModule.WIDGET_ID)), module.parse(snapshot(ordinary, false)))
    }

    @Test fun `source marker slot category identity and exact integer all required`() {
        // SkyHanni FishingApi's literal server-lore regression fixture.
        assertEquals(49L, FishingBaitPatterns.selected(preview("49"))!!.remaining)
        for (count in listOf("-1", "1.5", "1k", "99,99", "01,001", "1,000,000,000", "1000000000", "16 more", "?", ""))
            assertNull(FishingBaitPatterns.selected(preview(count)), count)
        for (item in listOf(preview(slot = 7), preview(id = null), preview(id = "fish_bait"),
            preview(type = "COMMON FISHING ROD"), preview(name = ""), preview(name = "x".repeat(257)),
            MenuItemSnapshot(8, "Fish Bait", listOf("COMMON BAIT"), "FISH_BAIT"),
            MenuItemSnapshot(8, "Fish Bait", listOf("Bait Remaining: 16", "Bait Remaining: 17", "COMMON BAIT"), "FISH_BAIT"),
            MenuItemSnapshot(8, "Fish Bait", listOf("Bait Remaining: 16", "Bait Remaining: ?", "COMMON BAIT"), "FISH_BAIT"),
            MenuItemSnapshot(8, "Fish Bait", listOf("x".repeat(1_025), "Bait Remaining: 16", "COMMON BAIT"), "FISH_BAIT")))
            assertNull(FishingBaitPatterns.selected(item), item.name)
        assertEquals(1_234L, FishingBaitPatterns.selected(preview("1,234"))!!.remaining)
        for (tier in listOf("BRONZE", "SILVER", "GOLD", "DIAMOND")) for (number in 1..2) {
            val item = preview(id = "OBFUSCATED_FISH_${number}_$tier", name = "Obfuscated-$number $tier", type = "COMMON TROPHY FISH")
            assertNotNull(FishingBaitPatterns.selected(item))
        }
        assertNull(FishingBaitPatterns.selected(preview(id = "OBFUSCATED_FISH_3_GOLD", type = "RARE TROPHY FISH")))
    }

    @Test fun `repeated source counts never burn bait and refill switch unknown expiry session clear helper`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        core.enterSkyBlock("bait-fixture", now)
        repeat(8) {
            now = it * 1_000L
            core.ingest(snapshot(preview("7")), now)
            assertEquals("7", core.snapshot(now).widgets.single().presentation!!.metrics.single().value)
            assertEquals(2_000L, core.snapshot(now).widgets.single().presentation!!.metrics.single().expandedUntil)
            assertNull(core.snapshot(now).activeNotice)
            assertNull(core.snapshot(now).progression)
        }
        assertTrue(core.snapshot(now + 3_000).widgets.isEmpty())
        for (replacement in listOf(snapshot(null), snapshot(preview(), false), snapshot(preview("unknown")))) {
            core.ingest(snapshot(preview("7")), ++now)
            assertEquals(1, core.snapshot(now).widgets.size)
            core.ingest(replacement, ++now)
            assertTrue(core.snapshot(now).widgets.isEmpty())
        }
        core.ingest(snapshot(preview("17")), ++now)
        assertEquals("", core.snapshot(now).widgets.single().presentation!!.metrics.single().value)
        core.ingest(snapshot(preview("50", id = "HOT_BAIT", name = "Hot Bait", type = "UNCOMMON BAIT")), ++now)
        val switched = core.snapshot(now).widgets.single().presentation!!.metrics.single()
        assertEquals("Hot Bait", switched.label)
        assertEquals("", switched.value)
        assertNull(switched.accent)
        core.ingest(snapshot(preview("0")), ++now)
        core.leaveSkyBlock()
        core.enterSkyBlock("next-island", ++now)
        assertTrue(core.snapshot(now).widgets.isEmpty())
    }

    @Test fun `exact disabled confirmation remains separate from known selected count and enabled removes only it`() {
        assertTrue(module.parse(NormalizedSignal.Chat("Party > Test: Use Baits From Sacks are now disabled!")).isEmpty())
        module.parse(snapshot(preview()))
        val disabled = module.parse(NormalizedSignal.Chat("§cUse Baits From Sacks are now disabled!")).widget()
        assertEquals(setOf("bait_selected", "bait_sack_disabled"), disabled.presentation!!.metrics.map { it.id }.toSet())
        assertTrue(disabled.presentation!!.metrics.all { it.group == FishingBaitModule.GROUP })
        val sack = disabled.presentation!!.metrics.single { it.id == "bait_sack_disabled" }
        assertEquals("", sack.value)
        assertEquals("@sack_disabled", sack.icon)
        assertEquals(WidgetMetricFormat.ITEM, sack.format)
        assertNull(sack.expandedUntil)
        assertTrue(sack.detail!!.contains("Using baits from sacks is disabled"))
        assertTrue(sack.detail!!.contains("contents unknown"))
        assertEquals("16", disabled.presentation!!.metrics.single { it.id == "bait_selected" }.value)
        val enabled = module.parse(NormalizedSignal.Chat("Use Baits From Sacks are now enabled!")).widget()
        assertEquals("bait_selected", enabled.presentation!!.metrics.single().id)
        module.parse(NormalizedSignal.Chat("Use Baits From Sacks are now disabled!"))
        assertEquals(listOf(CoreEvent.WidgetRemoved(FishingBaitModule.WIDGET_ID)), module.parse(snapshot(preview(), false)))
        // The toggle is exact session state, while holding a rod has its own short source lease.
        assertEquals(setOf("bait_selected", "bait_sack_disabled"), module.parse(snapshot(preview("64"))).widget()
            .presentation!!.metrics.map { it.id }.toSet())
        now += 3_000
        assertEquals(listOf(CoreEvent.WidgetRemoved(FishingBaitModule.WIDGET_ID)),
            module.parse(NormalizedSignal.Chat("Use Baits From Sacks are now disabled!")))
        module.onSessionEnded()
        module.onSessionStarted("new")
        assertEquals("bait_selected", module.parse(snapshot(preview("64"))).widget().presentation!!.metrics.single().id)
    }

    @Test fun `disabled sack remains an icon without implying an empty selected bait`() {
        module.parse(snapshot(null))
        val sack = module.parse(NormalizedSignal.Chat("Use Baits From Sacks are now disabled!"))
            .widget().presentation!!.metrics.single()
        assertEquals("bait_sack_disabled", sack.id)
        assertEquals("", sack.value)
        assertEquals("@sack_disabled", sack.icon)
        assertEquals(WidgetMetricFormat.ITEM, sack.format)
        assertFalse(sack.detail!!.contains("Depleted"))
        assertEquals(listOf(CoreEvent.WidgetRemoved(FishingBaitModule.WIDGET_ID)),
            module.parse(NormalizedSignal.Chat("Use Baits From Sacks are now enabled!")))
    }

    @Test fun `selection intro does not renew on counts or brief unknown gaps but renews on true change expiry and rod switch`() {
        fun selected(count: String = "64") = module.parse(snapshot(preview(count))).widget().presentation!!.metrics.single()
        assertEquals(2_000L, selected().expandedUntil)
        now = 1_000
        assertEquals(2_000L, selected("63").expandedUntil)
        now = 2_000
        assertEquals(listOf(CoreEvent.WidgetRemoved(FishingBaitModule.WIDGET_ID)), module.parse(snapshot(null)))
        now = 3_500 // 2.5 seconds since the last valid preview, still the same selection.
        assertEquals(2_000L, selected("62").expandedUntil)
        now = 4_000
        val changed = module.parse(snapshot(preview("64", id = "HOT_BAIT", name = "Hot Bait", type = "UNCOMMON BAIT")))
            .widget().presentation!!.metrics.single()
        assertEquals(6_000L, changed.expandedUntil)
        now = 4_100
        assertEquals(6_100L, selected().expandedUntil) // Real change back to Fish Bait.
        now = 7_101
        assertEquals(9_101L, selected().expandedUntil) // Original source lease expired.
        now = 7_200
        module.parse(snapshot(null, false))
        now = 7_300
        assertEquals(9_300L, selected().expandedUntil)
        module.onSessionEnded()
        module.onSessionStarted("next")
        now = 7_400
        assertEquals(9_400L, selected().expandedUntil)
    }

    @Test fun `bait helpers preserve independently observed boss progression and pet`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        core.enterSkyBlock("composition", now)
        val boss = java.util.UUID.randomUUID()
        core.updateBoss(boss, "Unknown Boss", .7f, "RED", "PROGRESS", now)
        core.apply(listOf(CoreEvent.SkillGain(Skill.FISHING, 100.0, null, "fixture"),
            CoreEvent.PetObserved(PetSnapshot("Dolphin", 100))), now)
        core.ingest(snapshot(preview("1")), now)
        val view = core.snapshot(now)
        assertEquals(boss, view.bosses.single().id)
        assertEquals("Dolphin", view.pet!!.name)
        assertEquals(Skill.FISHING, (view.progression as ProgressionView.SkillView).skill)
        assertNull(view.activeNotice)
        assertTrue(view.widgets.single().replacedBossIds.isEmpty())
    }
}
