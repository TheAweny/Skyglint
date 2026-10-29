package dev.skyglint.ui

import dev.skyglint.preview.*

import dev.skyglint.core.*
import dev.skyglint.features.ContextModules
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class MinimalHudTest {
    @Test fun `ordinary Garden excludes scoreboard balances and passive fortune while keeping useful helpers`() {
        val state = ContextPreviewTimeline(0, false).snapshot(0)
        val elements = HudComposition().compose(state, 0)
        assertTrue(elements.none { it.id == "group:currency" || it.id == "group:garden:stats" })
        assertTrue(elements.any { it.id.endsWith(":visitors") })
        assertTrue(elements.any { it.id.endsWith(":pests") })
        assertTrue(elements.any { it.kind == HudElementKind.PROGRESS && it.badge != null })
        assertTrue(state.widgets.flatMap { it.presentation?.metrics.orEmpty() }.any { it.id == "farming_fortune" && it.referenceOnly })
    }

    @Test fun `empty Garden has no pest visitor currency or fortune helpers`() {
        var now = 0L
        val core = CoreController().apply {
            ContextModules.create { now }.forEach(modules::register)
            enterSkyBlock("garden", 0)
        }
        core.ingest(NormalizedSignal.ContextSnapshot("Garden", "garden"), now)
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Visitors: (0)", "Next Visitor: 3m", "Stats:",
            "Farming Fortune: \uE0511234", "Copper: 7,456", "Pests:", "Plots: 4")), now)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("\uE067 The Garden \uE018 x0", "Purse: 10,000", "Bits: 50")), now)
        val shown = HudComposition().compose(core.snapshot(now), now)
        assertEquals(1, shown.size)
        assertEquals("Garden", shown.single().title)
    }

    @Test fun `election context hides unrelated pet while preserving real boss and skill`() {
        val boss = BossBarState(java.util.UUID(0, 81), "Unknown objective", .5f)
        for (area in listOf("Community Center", "Election Room", "Election Booth")) {
            val state = HudSnapshot(true, "election", listOf(boss),
                ProgressionView.SkillView(Skill.COMBAT, 10, null, 5.0), PetSnapshot("Tiger", 20), null,
                location = area)
            val shown = HudComposition().compose(state, 0)
            assertTrue(shown.none { it.id == "pet" })
            assertEquals("Unknown objective", shown.single { it.kind == HudElementKind.MAIN }.title)
            assertTrue(shown.any { it.id == "skill" })
        }
    }
}
