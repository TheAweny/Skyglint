package dev.skyglint.features.mining

import dev.skyglint.core.*
import kotlin.test.*

class NucleusActivityModuleTest {
    @Test fun `identical actual packet presence keeps search alive without replaying XP detectors`() {
        var time = 0L
        val core = CoreController().apply {
            dev.skyglint.features.ContextModules.create { time }.forEach(modules::register)
            enterSkyBlock("presence", time)
        }
        // The first text may precede the selected-tool snapshot. A repeated received packet must recover it.
        core.onActionBar("+10 Mining (50%) TREASURE: 12.3m", time)
        for (second in 0..25) {
            time = second * 1_000L
            core.ingest(NormalizedSignal.ContextSnapshot("Mines of Divan", "crystal_hollows"), time)
            core.ingest(NormalizedSignal.HeldItemSnapshot(MenuItemSnapshot(0, "Detector", emptyList(), "DWARVEN_METAL_DETECTOR")), time)
            core.ingest(NormalizedSignal.InventorySnapshot(emptyList()), time)
            val events = core.ingest(NormalizedSignal.ActionBarPresence("+10 Mining (50%) TREASURE: 12.3m"), time)
            assertTrue(events.none { it is CoreEvent.SkillGain || it is CoreEvent.SkyBlockGain })
            assertTrue(core.snapshot(time).widgets.any { it.id == NucleusActivityModule.WIDGET_ID })
        }
        // Merely keeping context/tool fresh cannot manufacture continued server search activity.
        time = 41_000
        core.ingest(NormalizedSignal.ContextSnapshot("Mines of Divan", "crystal_hollows"), time)
        core.ingest(NormalizedSignal.HeldItemSnapshot(MenuItemSnapshot(0, "Detector", emptyList(), "DWARVEN_METAL_DETECTOR")), time)
        assertTrue(core.snapshot(time).widgets.none { it.id == NucleusActivityModule.WIDGET_ID })
    }
    private var time = 0L
    private val module = NucleusActivityModule { time }
    private fun context(location: String = "Mines of Divan", mode: String = "crystal_hollows") =
        module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun held(id: String? = "DWARVEN_METAL_DETECTOR") = module.parse(NormalizedSignal.HeldItemSnapshot(
        id?.let { MenuItemSnapshot(0, "Metal Detector", emptyList(), it) }))
    private fun inventory(vararg ids: String) = module.parse(NormalizedSignal.InventorySnapshot(
        ids.mapIndexed { slot, id -> MenuItemSnapshot(slot, "observed item", emptyList(), id) }))
    private fun chat(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun search() = module.parse(NormalizedSignal.ActionBar("1,200❤     450❈ Defense     §3§lTREASURE: §b79.2m§r"))
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget
    private fun List<CoreEvent>.notice() = filterIsInstance<CoreEvent.NoticeObserved>().singleOrNull()?.request

    @Test fun `location snapshots and selected detector do not manufacture search activity`() {
        assertNull(context().widget()); assertNull(held().widget()); assertNull(inventory().widget())
        val widget = search().widget()!!
        assertEquals("Divan tools", widget.title)
        assertEquals("0 carried · ?/4 returned", widget.detail)
        assertTrue(widget.presentation!!.metrics.isEmpty())
        assertNull(widget.progress)
    }

    @Test fun `carried tools use exact inventory IDs and count four distinct types not item copies`() {
        context(); held()
        inventory("DWARVEN_DIAMOND_AXE", "DWARVEN_GOLD_HAMMER", "DWARVEN_GOLD_HAMMER", "DIAMOND_AXE")
        assertEquals("2 carried · ?/4 returned", search().widget()!!.detail)
        assertEquals("4 carried · ?/4 returned", inventory(*NucleusPatterns.Tool.entries.map { it.skyBlockId }.toTypedArray()).widget()!!.detail)
    }

    @Test fun `treasure discovery does not claim item pickup or increment inventory`() {
        context(); held(); inventory(); search()
        val result = chat("§aYou found §r§cScavenged Diamond Axe §r§awith your §r§cMetal Detector§r§a!")
        assertEquals("Scavenged Diamond Axe found", result.notice()!!.title)
        assertEquals("0 carried · ?/4 returned", result.widget()!!.detail)
        assertNull(result.notice()!!.item)
        assertEquals("1 carried · ?/4 returned", inventory("DWARVEN_DIAMOND_AXE").widget()!!.detail)
    }

    @Test fun `handed tools require exact matching NPC return and remain a lower bound until all known`() {
        context(); held(); inventory("DWARVEN_LAPIS_SWORD"); search()
        val returned = "[NPC] Keeper of Diamond: Excellent! You have returned the Scavenged Diamond Axe to its rightful place!"
        assertEquals("1 carried · ≥1/4 returned", chat(returned).widget()!!.detail)
        assertEquals("1 carried · ≥1/4 returned", chat(returned).widget()!!.detail)
        assertEquals("1 carried · ≥1/4 returned", chat(returned.replace("Keeper of Diamond", "Keeper of Gold")).widget()!!.detail)
        assertEquals("1 carried · ≥1/4 returned", chat("[VIP] Player: $returned").widget()!!.detail)
    }

    @Test fun `NPC all tools confirmation ends search but does not invent a Jade crystal acquisition`() {
        context(); held(); inventory(); search()
        val result = chat("§e[NPC] §6Keeper of Lapis§f: §rYou found all of the items! Behold... the §aJade Crystal§r!")
        assertEquals("All Divan tools returned", result.notice()!!.title)
        assertNull(result.widget())
        assertTrue(result.filterIsInstance<CoreEvent.WidgetRemoved>().any { it.id == NucleusActivityModule.WIDGET_ID })
        assertNull(search().widget())
    }

    @Test fun `search counts expire independently and held switch removes search immediately`() {
        context(); held(); inventory("DWARVEN_DIAMOND_AXE"); search()
        time = 4_999; context(); held()
        time = 5_000
        assertEquals("? carried · ?/4 returned", context().widget()!!.detail)
        assertNull(held("TITANIUM_DRILL_4").widget())
        assertNull(held().widget()) // switching back alone is not a fresh search
        assertNotNull(search().widget())
        time = 10_001
        assertNull(context().widget()) // held source is stale
    }

    @Test fun `search activity expires while continuously revalidating inventory and context`() {
        context(); held(); inventory(); search()
        for (step in 1..15) {
            time = step * 1_000L; context(); held(); inventory()
        }
        assertNull(context().widget())
        assertNotNull(search().widget())
    }

    @Test fun `official mode area and session lifecycle gate and clear tool observations`() {
        context(); held(); inventory("DWARVEN_DIAMOND_AXE"); search()
        assertNull(context("Jungle").widget()); assertNull(search().widget())
        context(); assertNull(held().widget())
        assertNotNull(search().widget())
        assertNull(context("Mines of Divan", "hub").widget())
        assertNull(chat("✦ You placed the Jade Crystal!").notice())
        context(); held()
        assertEquals("? carried · ?/4 returned", search().widget()!!.detail)
        module.onSessionEnded(); module.onSessionStarted("new")
        context(); held()
        assertEquals("? carried · ?/4 returned", search().widget()!!.detail)
    }

    @Test fun `crystal found pairs only explicit server count with one known name within bounded window`() {
        context("Jungle")
        assertNull(chat("Amethyst Crystal").notice())
        assertNull(chat("§f                       §r§5§l✦ CRYSTAL FOUND §r§7(1§r§7/5§r§7)").notice())
        val notice = chat("§f                              §r§5Amethyst Crystal").notice()!!
        assertEquals("Amethyst Crystal found · 1/5", notice.title)
        assertEquals(3_500L, notice.durationMillis)
        assertNull(notice.item)
        assertEquals("minecraft:purple_dye", notice.pages.single().icon)
        assertNull(chat("Amethyst Crystal").notice())
        chat("✦ CRYSTAL FOUND (2/5)"); time = 2_000
        assertNull(chat("Sapphire Crystal").notice())
    }

    @Test fun `placement uses observed crystal and never persists a nucleus dashboard`() {
        context("Crystal Nucleus")
        val events = chat("§5§l✦ §r§dYou placed the §r§bSapphire Crystal§r§d!")
        assertEquals("Sapphire Crystal placed", events.notice()!!.title)
        assertTrue(events.none { it is CoreEvent.WidgetObserved })
        assertNull(chat("✦ You placed the Ruby Crystal!").notice())
        assertNull(chat("[MVP+] Player: ✦ You placed the Sapphire Crystal!").notice())
        assertNull(chat("✦ You placed the Sapphire Crystal!").notice()) // duplicate bounded
        time = 6_000; context("Crystal Nucleus")
        assertNotNull(chat("✦ You placed the Sapphire Crystal!").notice())
    }

    @Test fun `complete run requires explicit bundle header and fresh CH mode not five invented placements`() {
        context("Crystal Nucleus")
        for (crystal in NucleusPatterns.Crystal.entries) chat("✦ You placed the ${crystal.label} Crystal!")
        assertNull(chat("Pick it up near the Nucleus Vault!").notice())
        assertEquals("Nucleus run complete!", chat("  §r§5§lCRYSTAL NUCLEUS LOOT BUNDLE").notice()!!.title)
        assertNull(chat("CRYSTAL NUCLEUS LOOT BUNDLE").notice())
        time = 5_000
        assertNull(chat("✦ You placed the Jade Crystal!").notice())
    }

    @Test fun `malformed or unrecognized crystal and detector observations are ignored`() {
        context(); held(); inventory()
        assertNull(module.parse(NormalizedSignal.ActionBar("TREASURE: unknown")).widget())
        assertNull(chat("✦ CRYSTAL FOUND (6/5)").notice())
        assertNull(chat("Jade Crystal").notice())
        assertNull(chat("You found Scavenged Diamond Axe with your Metal Detector! copied").notice())
        assertNull(chat("You found Flawed Jade Gemstone x4 with your Metal Detector!").notice())
        assertNull(chat("[NPC] Keeper of Gold: You found all of the items! Behold... the Ruby Crystal!").notice())
    }
}
