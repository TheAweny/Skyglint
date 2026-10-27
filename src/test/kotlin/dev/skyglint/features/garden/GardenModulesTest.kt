package dev.skyglint.features.garden

import dev.skyglint.core.*
import kotlin.test.*

class GardenModulesTest {
    @Test fun `walking between plots does not cancel an active Jacob contest`() {
        val module = JacobContestModule { time }
        module.parse(NormalizedSignal.ContextSnapshot("Plot - 4", "garden"))
        widget(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Jacob's Contest", "○ Carrot 18m17s", "Collected 8,264"))))
        assertTrue(module.parse(NormalizedSignal.ContextSnapshot("Plot - 5", "garden")).isEmpty())
        removed(module.parse(NormalizedSignal.ContextSnapshot("Village", "hub")))
    }
    @Test fun `explicit zero Garden sidebar defeats stale tab plots without confusing bare missing source`() {
        val module = GardenPestModule { time }; enable(module)
        val plots = NormalizedSignal.TabSnapshot(listOf("Pests:", "Plots: 4, 12"))
        widget(module.parse(plots))
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("\uE067 The Garden"))))
        removed(module.parse(plots))
        time += 1000
        widget(module.parse(NormalizedSignal.SidebarSnapshot(listOf("\uE067 The Garden \uE018 x2"))))
    }
    @Test fun `explicit x0 Garden and plot counts keep cached tab plots suppressed for the zero lease`() {
        listOf("\uE067 The Garden \uE018 x0", "Plot - 4 \uE07F x0").forEach { zeroLine ->
            val module = GardenPestModule { time }; enable(module)
            val plots = NormalizedSignal.TabSnapshot(listOf("Pests:", "Plots: 4, 12"))
            widget(module.parse(plots))
            removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf(zeroLine))))
            removed(module.parse(plots))
            time += 1_000
            // Missing source is not a new positive observation and cannot revoke the recent zero.
            removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Garden"))))
            removed(module.parse(plots))
            time += 2_999
            removed(module.parse(plots))
            time += 1
            assertEquals("2", metric(widget(module.parse(plots)), "infested_plots").value)
        }
    }
    @Test fun `fresh positive pest count replaces explicit zero without waiting for its lease`() {
        val module = GardenPestModule { time }; enable(module)
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("\uE067 The Garden \uE018 x0"))))
        time += 100
        assertEquals("3", metric(widget(module.parse(NormalizedSignal.SidebarSnapshot(
            listOf("\uE067 The Garden \uE018 x3")))), "pests").value)
        assertEquals("3", metric(widget(module.parse(NormalizedSignal.TabSnapshot(
            listOf("Pests:", "Plots: 4")))), "pests").value)
    }
    @Test fun `zero visitors removes the helper even when next arrival remains known`() {
        val module = GardenVisitorModule { time }; enable(module)
        widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Visitors: (2)", "Next Visitor: 11m"))))
        removed(module.parse(NormalizedSignal.TabSnapshot(listOf("Visitors: (0)", "Next Visitor: 10m"))))
        val positive = module.parse(NormalizedSignal.TabSnapshot(listOf("Visitors: (1)", "Next Visitor: 9m")))
        assertEquals("1", metric(widget(positive), "visitors").value)
        assertTrue(positive.none { it is CoreEvent.NoticeObserved })
    }
    @Test fun `empty visitors and pests remove only their own independent helpers`() {
        val core = CoreController(modules = ModuleRegistry().register(GardenVisitorModule { time })
            .register(GardenPestModule { time }))
        core.enterSkyBlock("garden-counters", time)
        core.ingest(NormalizedSignal.ContextSnapshot("The Garden", "garden"), time)
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Visitors: (2)")), time)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("\uE067 The Garden \uE018 x3")), time)
        assertEquals(setOf("skyglint.garden.visitors.current", "skyglint.garden.pests.current"),
            core.snapshot(time).widgets.map { it.id }.toSet())
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Visitors: (0)")), time)
        assertEquals("3", metric(core.snapshot(time).widgets.single(), "pests").value)
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Visitors: (2)")), time)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("\uE067 The Garden \uE018 x0")), time)
        assertEquals("2", metric(core.snapshot(time).widgets.single(), "visitors").value)
        assertNull(core.snapshot(time).activeNotice)
    }
    private var time = 100L
    private fun enable(module: SignalModule) { module.onSessionStarted("fixture"); module.parse(NormalizedSignal.ContextSnapshot("Plot - 4", "garden")) }
    private fun widget(events: List<CoreEvent>): ModuleWidget = events.filterIsInstance<CoreEvent.WidgetObserved>().single().widget
    private fun removed(events: List<CoreEvent>) = assertIs<CoreEvent.WidgetRemoved>(events.single())
    private fun metric(widget: ModuleWidget, id: String): WidgetMetric = assertNotNull(widget.presentation?.metrics?.firstOrNull { it.id == id })

    @Test fun `Garden authorization requires copied context not a familiar looking tab line`() {
        val module = GardenVisitorModule { time }
        val tab = NormalizedSignal.TabSnapshot(listOf("Visitors: (3)", " Next Visitor: 11m"))
        assertTrue(module.parse(tab).isEmpty())
        module.parse(NormalizedSignal.ContextSnapshot("The Garden", "hub"))
        assertTrue(module.parse(tab).isEmpty()) // Explicit incompatible API mode wins over a name.
        module.parse(NormalizedSignal.ContextSnapshot("The Garden", null))
        assertEquals("3", metric(widget(module.parse(tab)), "visitors").value)
        removed(module.parse(NormalizedSignal.ContextSnapshot("Village", "hub")))
        assertTrue(module.parse(tab).isEmpty())
    }

    @Test fun `visitors use observed count and exact next visitor grammar without invented arrival`() {
        val module = GardenVisitorModule { time }; enable(module)
        val events = module.parse(NormalizedSignal.TabSnapshot(listOf("§aVisitors: §f(5)", " Next Visitor: Queue Full!")))
        val view = widget(events)
        assertEquals("Visitors", view.title)
        assertNull(view.detail)
        assertEquals(WidgetRole.COUNTERS, view.presentation?.role)
        assertEquals("5", metric(view, "visitors").value)
        assertEquals("Queue full", metric(view, "next_visitor").value)
        assertEquals(40, view.priority)
        assertEquals(4_100L, view.expiresAt)
        time = 1_000
        assertEquals(5_000L, widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Visitors: (5)", " Next Visitor: 11m")))).expiresAt)
        removed(module.parse(NormalizedSignal.TabSnapshot(listOf("Visitors: five"))))
    }

    @Test fun `only explicit visitor server chat emits arrival notice`() {
        val module = GardenVisitorModule { time }; enable(module)
        val notice = assertIs<CoreEvent.NoticeObserved>(module.parse(NormalizedSignal.Chat(
            "§a§r§aBanker Broadjaw §r§ehas arrived on your §r§aGarden§r§e!")).single()).request
        assertEquals("Visitor arrived", notice.title)
        assertEquals("Banker Broadjaw", notice.detail)
        assertTrue(module.parse(NormalizedSignal.Chat("From Player: Banker Broadjaw has arrived on your Garden!")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("[NPC] Jacob: has arrived on your Garden!")).isEmpty())
    }

    @Test fun `pest sidebar glyph variants and plot totals remain absolute snapshots`() {
        val module = GardenPestModule { time }; enable(module)
        val garden = widget(module.parse(NormalizedSignal.SidebarSnapshot(listOf(" §7\uE067 §aThe Garden §4§l\uE018§7 x1"))))
        assertEquals("Pests", garden.title)
        assertEquals("Garden", garden.detail)
        assertEquals("1", metric(garden, "pests").value)
        assertEquals(WidgetRole.COUNTERS, garden.presentation?.role)
        assertEquals(75, garden.priority)
        assertEquals("8", metric(widget(module.parse(NormalizedSignal.SidebarSnapshot(
            listOf(" §7\uE067 §cThe Garden §4§l\uE07F§7 x8")))), "pests").value)
        val plot = widget(module.parse(NormalizedSignal.SidebarSnapshot(listOf("   §aPlot §7- §b4 §4§l\uE018§7 x1"))))
        assertEquals("Plot 4", plot.detail)
        assertTrue(module.parse(NormalizedSignal.Chat("YUCK! 4 \uE018 Pest have spawned in Plot - 14!")).isEmpty())
        removed(module.parse(NormalizedSignal.Chat("§cThere are not any Pests on your Garden right now! Keep farming!")))
    }

    @Test fun `pest tab plot list is not a fabricated pest count and sidebar absence preserves fresh tab source`() {
        val module = GardenPestModule { time }; enable(module)
        val tab = widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Pests:", " Plots: 4, 12, 13, 18, 20", "Pet:"))))
        assertEquals("Pests", tab.title)
        assertEquals("Infested plots", tab.detail)
        assertEquals("5", metric(tab, "infested_plots").value)
        assertEquals("4, 12, 13, 18, 20", metric(tab, "plot_list").value)
        assertNull(tab.presentation?.metrics?.firstOrNull { it.id == "pests" })
        assertEquals(tab.title, widget(module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Garden")))).title)
        time = 4_100
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("The Garden"))))
    }

    @Test fun `crop milestones require their own widget section and keep percent unknown totals`() {
        val module = GardenMilestoneModule { time }; enable(module)
        removed(module.parse(NormalizedSignal.TabSnapshot(listOf("Potato 32: 97.7%"))))
        val view = widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Crop Milestones:", " Potato 32: 97.7%", "Pet:"))))
        assertEquals("Potato", view.title)
        assertEquals("Milestone", view.detail)
        assertEquals(WidgetRole.PROGRESSION, view.presentation?.role)
        assertEquals("minecraft:potato", view.presentation?.icon)
        assertEquals("32", metric(view, "tier").value)
        assertEquals(.977, view.progress!!.fraction, .000001)
        assertNull(view.progress?.current)
        assertNull(view.progress?.maximum)
        assertEquals(45, view.priority)
        assertEquals(1.0, widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Crop Milestones:", " Cocoa Beans 46: MAX")))).progress?.fraction)
        removed(module.parse(NormalizedSignal.TabSnapshot(listOf("Crop Milestones:", " Fake Crop 3: 30%"))))
        removed(module.parse(NormalizedSignal.TabSnapshot(listOf("Crop Milestones:", " Potato 32: 101%"))))
    }

    @Test fun `explicit Garden milestone header is a short notice but snapshots are not`() {
        val module = GardenMilestoneModule { time }; enable(module)
        val request = assertIs<CoreEvent.NoticeObserved>(module.parse(NormalizedSignal.Chat(
            "  §r§b§lGARDEN MILESTONE §3Melon §845➜§346")).single()).request
        assertEquals("Garden milestone!", request.title)
        assertEquals("Melon · Tier 46", request.detail)
        assertTrue(module.parse(NormalizedSignal.Chat("Player: GARDEN MILESTONE Melon 45➜46")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("GARDEN MILESTONE Melon 46➜45")).isEmpty())
    }

    @Test fun `Garden baseline replaces generic objective even before statistics are observed`() {
        val module = GardenStatisticsModule { time }
        val baseline = widget(module.parse(NormalizedSignal.ContextSnapshot("Plot - 4", "garden")))
        assertEquals("Garden", baseline.title)
        assertEquals("Plot - 4", baseline.detail)
        assertEquals(30, baseline.priority)
        assertNull(baseline.progress)
        val values = widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Farming Fortune: \uE0511234", "Garden Level: 17", "Copper: 12,345"))))
        assertEquals("Plot - 4", values.detail)
        assertEquals(WidgetRole.PRIMARY, values.presentation?.role)
        assertEquals("1234", metric(values, "farming_fortune").value)
        assertEquals("17", metric(values, "garden_level").value)
        assertEquals("12345", metric(values, "copper").value)
        time = 5_000
        val expired = widget(module.parse(NormalizedSignal.ContextSnapshot("Plot - 4", "garden")))
        assertEquals("Plot - 4", expired.detail)
        assertTrue(expired.presentation!!.metrics.isEmpty())
    }

    @Test fun `malformed statistics do not become zeros or huge values`() {
        val module = GardenStatisticsModule { time }; enable(module)
        val view = widget(module.parse(NormalizedSignal.TabSnapshot(listOf("Farming Fortune: 1234", "Copper: 99,99", "Garden Level: XVII"))))
        assertEquals("Plot - 4", view.detail)
        assertNull(view.progress)
    }

    @Test fun `Jacob explicit active sidebar parses crop time and collection outside Garden`() {
        val module = JacobContestModule { time }
        val events = module.parse(NormalizedSignal.SidebarSnapshot(listOf("§eJacob's Contest", "§e○ §fCarrot §a18m17s", " Collected §e8,264")))
        val contest = widget(events)
        assertEquals("Jacob · Carrot", contest.title)
        assertEquals("18m17s", contest.detail)
        assertEquals(WidgetRole.PRIMARY, contest.presentation?.role)
        assertEquals("minecraft:carrot", contest.presentation?.icon)
        assertEquals("8264", metric(contest, "collected").value)
        assertEquals(85, contest.priority)
        assertNull(contest.progress) // No medal threshold observed; collected isn't a percentage.
        assertEquals(4_100L, contest.expiresAt)
    }

    @Test fun `Jacob future tab crops no header and malformed adjacent lines do not fabricate participation`() {
        val module = JacobContestModule { time }
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Jacob's Contest: 19m left", "○ Carrot"))).isEmpty())
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("○ Cocoa Beans 15m14s"))))
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Jacob's Contest", "Visitors: (5)", "○ Carrot 18m17s"))))
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Jacob's Contest", "○ Fake Crop 18m17s"))))
        removed(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Jacob's Contest", "○ Carrot 1s3m"))))
    }

    @Test fun `leaving Garden and session lifecycle removes widgets and cached state`() {
        val modules = listOf(GardenVisitorModule { time }, GardenPestModule { time }, GardenMilestoneModule { time }, GardenStatisticsModule { time })
        modules.forEach { module ->
            enable(module)
            removed(module.parse(NormalizedSignal.ContextSnapshot("Village", "hub")))
            module.onSessionStarted("next")
            assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Visitors: (5)", "Crop Milestones:", "Potato 32: 97%"))).isEmpty())
        }
        val module = GardenStatisticsModule { time }; enable(module)
        module.parse(NormalizedSignal.TabSnapshot(listOf("Farming Fortune: \uE0511234")))
        module.onSessionEnded()
        assertNull(widget(module.parse(NormalizedSignal.ContextSnapshot("The Garden", "garden"))).detail)
    }

    @Test fun `widgets expire in reducer and snapshots never activate farming XP`() {
        val modules = ModuleRegistry().register(GardenStatisticsModule { time }).register(GardenVisitorModule { time })
        val core = CoreController(modules = modules)
        core.enterSkyBlock("fixture", 0)
        core.ingest(NormalizedSignal.ContextSnapshot("The Garden", "garden"), time)
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Visitors: (2)", "Next Visitor: 11m")), time)
        assertEquals(2, core.snapshot(time).widgets.size)
        assertNull(core.snapshot(time).progression)
        assertTrue(core.snapshot(4_100).widgets.isEmpty())
    }
}
