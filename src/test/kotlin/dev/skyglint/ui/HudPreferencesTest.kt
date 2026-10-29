package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.preview.ContextPreviewTimeline
import dev.skyglint.preview.MiningPreviewTimeline
import dev.skyglint.ui.presentation.*
import java.util.UUID
import kotlin.test.*

class HudPreferencesTest {
    private fun snapshot(vararg widgets: ModuleWidget) = HudSnapshot(true, "preferences", emptyList(), null, null, null,
        widgets = widgets.toList())
    private fun counters(id: String, vararg metrics: WidgetMetric) = ModuleWidget(id, "Helpers",
        presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics.toList()))

    @Test fun `hiding a mining timer returns its UUID while preserving a different helper and source observations`() {
        val claimed = UUID(0, 401)
        val timer = WidgetMetric("event:double_powder", "Double Powder", "X2", priority = 80,
            countdown = CountdownSnapshot(90_000, 90_000), replacedBossIds = setOf(claimed))
        val cold = WidgetMetric("cold", "Cold", "30", priority = 40)
        val mining = counters("skyglint.mining.events", timer, cold).copy(replacedBossIds = setOf(claimed))
        val state = snapshot(mining).copy(bosses = listOf(BossBarState(claimed, "Observed powder bar", .4f)))
        val composer = HudComposition()
        assertTrue(composer.compose(state, 0).none { it.id == "main" })
        val hidden = composer.compose(state, 1, HudPreferences(showMiningTimers = false))
        assertEquals("Observed powder bar", hidden.single { it.id == "main" }.title)
        assertTrue(hidden.none { it.kind == HudElementKind.TIMER })
        assertEquals("30", hidden.single { it.id.endsWith(":cold") }.value)
        assertEquals(listOf(timer, cold), state.widgets.single().presentation!!.metrics)
        assertEquals(setOf(claimed), state.widgets.single().replacedBossIds)
        assertTrue(composer.compose(state, 2).any { it.kind == HudElementKind.TIMER })
    }

    @Test fun `empty filtered mining widget cannot retain a legacy widget UUID claim or its introduction`() {
        val claimed = UUID(0, 402)
        val widget = counters("skyglint.mining.events", WidgetMetric("event:raffle", "Raffle", "Raffle", priority = 80,
            countdown = CountdownSnapshot(90_000, 90_000))).copy(replacedBossIds = setOf(claimed))
        val state = snapshot(widget).copy(bosses = listOf(BossBarState(claimed, "Raffle server bar", .5f)),
            activeNotice = TransientNotice("mining-intro", "Raffle", null, 40, 0, 4000,
                target = NoticeMetricTarget(widget.id, "event:raffle")))
        val result = HudComposition().compose(state, 0, HudPreferences(showMiningTimers = false))
        assertEquals("Raffle server bar", result.single().title)
        assertEquals("boss:$claimed", result.single().identity)
    }

    @Test fun `independent Garden and fuel visibility leaves boss progression and pet intact`() {
        val visitors = counters("skyglint.garden.visitors.current", WidgetMetric("visitors", "Visitors", "3", priority = 70))
        val pests = counters("skyglint.garden.pests.current", WidgetMetric("pests", "Pests", "2", priority = 75))
        val fuel = counters("skyglint.mining.drill_fuel.current", WidgetMetric("fuel", "Fuel", "8%", priority = 80))
        val milestone = ModuleWidget("skyglint.garden.milestone.current", "Wheat", progress = ProgressSnapshot(null, null, .7),
            presentation = WidgetPresentation(WidgetRole.PROGRESSION))
        val state = snapshot(visitors, pests, fuel, milestone).copy(
            bosses = listOf(BossBarState(UUID(0, 403), "Current objective", .5f)),
            progression = ProgressionView.SkillView(Skill.FARMING, 40, ProgressSnapshot(null, null, .4), 20.0),
            pet = PetSnapshot("Rabbit", 70, ProgressSnapshot(null, null, .5)))
        val preferences = HudPreferences(showGardenVisitors = false, showCropMilestones = false, showFuelWarnings = false)
        val result = HudComposition().compose(state, 0, preferences)
        assertEquals("Current objective", result.single { it.id == "main" }.title)
        assertTrue(result.any { it.id == "skill" })
        assertTrue(result.any { it.id == "pet" })
        assertTrue(result.any { it.id.endsWith(":pests") })
        assertTrue(result.none { it.id.endsWith(":visitors") || it.id.endsWith(":fuel") || it.id.startsWith("progress:") })
    }

    @Test fun `one helper block retains its grouped members and releases a capped out source boss`() {
        val hidden = UUID(0, 404)
        val helpers = counters("example.context",
            WidgetMetric("time", "Time", "30", priority = 100, group = "example:run"),
            WidgetMetric("count", "Count", "12", priority = 90, group = "example:run"),
            WidgetMetric("extra", "Extra", "4", priority = 80, replacedBossIds = setOf(hidden)))
        val state = snapshot(helpers).copy(bosses = listOf(BossBarState(hidden, "Unrepresented source bar", .5f)))
        val result = HudComposition().compose(state, 0, HudPreferences(helperLimit = 1))
        assertEquals("Unrepresented source bar", result.single { it.id == "main" }.title)
        assertEquals(listOf("30", "12"), result.single { it.kind == HudElementKind.STRIP }.parts.map { it.text })
        assertTrue(result.none { it.id.endsWith(":extra") })
    }

    @Test fun `pet introduction preference keeps the same pet identity and independent XP circle`() {
        val state = snapshot().copy(pet = PetSnapshot("Tiger", 36, ProgressSnapshot(null, null, .61)),
            petTransition = PetTransition(0, true))
        val expanded = HudComposition().compose(state, 500).single()
        val compact = HudComposition().compose(state, 500, HudPreferences(showPetIntroduction = false)).single()
        assertEquals(HudElementKind.MAIN, expanded.kind)
        assertEquals(HudElementKind.PROGRESS, compact.kind)
        assertEquals(expanded.identity, compact.identity)
        assertEquals(expanded.progress, compact.progress)
        assertEquals("Tiger", compact.title)
    }

    @Test fun `changing rotation interval preserves elapsed dwell and all UUIDs remain reachable`() {
        val state = snapshot().copy(bosses = listOf(BossBarState(UUID(0, 405), "Boss A", .3f),
            BossBarState(UUID(0, 406), "Boss B", .4f)))
        val composer = HudComposition()
        fun main(at: Long, seconds: Int) = composer.compose(state, at, HudPreferences(rotationSeconds = seconds)).single().title
        assertEquals("Boss A", main(0, 6))
        assertEquals("Boss A", main(3_000, 10))
        assertEquals("Boss A", main(9_999, 10))
        assertEquals("Boss B", main(10_000, 10))
        assertEquals("Boss B", main(13_999, 4))
        assertEquals("Boss A", main(14_000, 4))
    }

    @Test fun `bait and Sack warning can be hidden independently without losing the other helper`() {
        val widget = counters("skyglint.fishing.bait.current",
            WidgetMetric("bait_selected", "Spiked Bait", "", priority = 70, format = WidgetMetricFormat.ITEM),
            WidgetMetric("bait_sack_disabled", "Sack disabled", "", priority = 80, format = WidgetMetricFormat.ITEM))
        val state = snapshot(widget)
        val baitHidden = HudComposition().compose(state, 0, HudPreferences(showFishingBait = false))
        assertTrue(baitHidden.single().id.endsWith(":bait_sack_disabled"))
        val sackHidden = HudComposition().compose(state, 0, HudPreferences(showSackWarning = false))
        assertTrue(sackHidden.single().id.endsWith(":bait_selected"))
        assertTrue(HudComposition().compose(state, 0, HudPreferences(showFishingBait = false, showSackWarning = false)).isEmpty())
        assertEquals(2, state.widgets.single().presentation!!.metrics.size)
    }

    @Test fun `default preferences preserve the existing overload for representative contexts`() {
        val cases = listOf(ContextPreviewTimeline(0, false).snapshot(0) to 0L,
            ContextPreviewTimeline(0, true).snapshot(4_000) to 4_000L,
            MiningPreviewTimeline(0).snapshot(4_500) to 4_500L)
        for ((state, now) in cases) assertEquals(HudComposition().compose(state, now),
            HudComposition().compose(state, now, HudPreferences()))
    }

    @Test fun `hiding Jerry Defense releases original UUID and removes counters without changing source observations`() {
        val bossId = UUID(0, 407)
        val main = ModuleWidget("skyglint.winter.defense", "Jerry Defense", "Wave 2", priority = 85,
            presentation = WidgetPresentation(WidgetRole.PRIMARY), replacedBossIds = setOf(bossId))
        val counters = counters("skyglint.winter.defense.counters", WidgetMetric("gifts", "Gifts", "10", priority = 70))
        val state = snapshot(main, counters).copy(bosses = listOf(BossBarState(bossId, "Observed Jerry wave", .7f)))
        assertEquals("Jerry Defense · Wave 2", HudComposition().compose(state, 0).single { it.id == "main" }.title)
        val hidden = HudComposition().compose(state, 1, HudPreferences(showJerryDefense = false))
        assertEquals("Observed Jerry wave", hidden.single().title)
        assertEquals("boss:$bossId", hidden.single().identity)
        assertEquals(listOf(main, counters), state.widgets)
        assertEquals(setOf(bossId), state.widgets.first().replacedBossIds)
        assertTrue(HudComposition().compose(state, 2).any { it.id.endsWith(":gifts") })
    }

    @Test fun `hiding Reindrake releases every original UUID and does not hide independent winter features`() {
        val ids = listOf(UUID(0, 408), UUID(0, 409))
        val drakes = ids.map { id -> ModuleWidget("skyglint.winter.reindrake:$id", "Reindrake", "Observed", priority = 90,
            presentation = WidgetPresentation(WidgetRole.PRIMARY), replacedBossIds = setOf(id)) }
        val bosses = ids.mapIndexed { index, id -> BossBarState(id, "Original Reindrake $index", .6f) }
        val state = snapshot(*drakes.toTypedArray()).copy(bosses = bosses)
        val composer = HudComposition()
        val hidden = HudPreferences(showReindrake = false)
        assertEquals("boss:${ids[0]}", composer.compose(state, 0, hidden).single().identity)
        assertEquals("boss:${ids[1]}", composer.compose(state, 6_000, hidden).single().identity)
        assertEquals(drakes, state.widgets)
        assertEquals(bosses, state.bosses)
        assertTrue(HudComposition().compose(state, 0, HudPreferences(showJerryDefense = false)).single().title.startsWith("Reindrake"))
        val defense = ModuleWidget("skyglint.winter.defense", "Jerry Defense", priority = 85,
            presentation = WidgetPresentation(WidgetRole.PRIMARY))
        assertEquals("Jerry Defense", HudComposition().compose(snapshot(defense), 0, hidden).single().title)
    }
}
