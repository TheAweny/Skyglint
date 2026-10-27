package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import java.util.UUID
import kotlin.test.*

class MiningPresentationSafetyTest {
    private fun base() = HudSnapshot(true, "test", emptyList(), null, null, null)

    @Test fun `capped out replacement releases precisely its original boss UUID`() {
        val claimed = UUID(0, 501)
        val represented = UUID(0, 502)
        val mining = ModuleWidget("skyglint.mining.events", "Events", presentation = WidgetPresentation(WidgetRole.COUNTERS,
            metrics = listOf(
                WidgetMetric("event:double_powder", "Powder", "X2", priority = 10,
                    countdown = CountdownSnapshot(90_000, 90_000), replacedBossIds = setOf(claimed)),
                WidgetMetric("event:raffle", "Raffle", "Raffle", priority = 100,
                    countdown = CountdownSnapshot(30_000, 30_000), replacedBossIds = setOf(represented)))),
            replacedBossIds = setOf(claimed, represented))
        val busy = ModuleWidget("example.stats", "Busy", presentation = WidgetPresentation(WidgetRole.COUNTERS,
            metrics = (1..6).map { WidgetMetric("stat$it", "Stat", "$it", priority = 90) }))
        val snapshot = base().copy(widgets = listOf(mining, busy), bosses = listOf(
            BossBarState(claimed, "Powder server bar", .4f), BossBarState(represented, "Raffle server bar", .3f)))
        val elements = HudComposition().compose(snapshot, 0)
        assertEquals("Powder server bar", elements.single { it.kind == HudElementKind.MAIN }.title)
        assertEquals(1, elements.count { it.kind == HudElementKind.TIMER })
        assertEquals("Raffle", elements.single { it.kind == HudElementKind.TIMER }.title)
    }

    @Test fun `fuel at every band is additive and preserves boss skill pet and event`() {
        for (current in listOf(4_999L, 2_500L, 2_499L, 1_000L, 999L, 500L, 499L, 0L)) {
            val module = dev.skyglint.features.mining.DrillFuelModule { 0L }
            val fuel = module.parse(NormalizedSignal.HeldItemSnapshot(MenuItemSnapshot(0, "Drill",
                listOf("Fuel: $current/10k", "MYTHIC DRILL")))).filterIsInstance<CoreEvent.WidgetObserved>().single().widget
            val event = ModuleWidget("skyglint.mining.events", "Events", presentation = WidgetPresentation(WidgetRole.COUNTERS,
                metrics = listOf(WidgetMetric("event:double_powder", "Double Powder", "X2", priority = 80,
                    countdown = CountdownSnapshot(60_000, 60_000)))))
            val snapshot = base().copy(widgets = listOf(fuel, event),
                bosses = listOf(BossBarState(UUID(0, 1), "Unknown mining boss", .5f)),
                progression = ProgressionView.SkillView(Skill.MINING, 40, ProgressSnapshot(null, null, .4), 10.0),
                pet = PetSnapshot("Silverfish", 70, ProgressSnapshot(null, null, .5)))
            val composer = HudComposition()
            for (time in listOf(0L, 1_000L, 2_500L)) {
                val shown = composer.compose(snapshot, time)
                assertEquals("Unknown mining boss", shown.single { it.id == "main" }.title)
                assertTrue(shown.any { it.id == "skill" })
                assertTrue(shown.any { it.id == "pet" })
                assertTrue(shown.any { it.kind == HudElementKind.TIMER })
                val helper = shown.single { it.id.endsWith(":fuel") }
                assertEquals(HudElementKind.COUNTER, helper.kind)
                assertEquals(current < 500, helper.pulse)
                assertEquals(when {
                    current < 1_000 -> 0xFFFF8E91.toInt()
                    current < 2_500 -> 0xFFFFD182.toInt()
                    else -> 0xFFE4D3A4.toInt()
                }, helper.accent)
            }
            val reward = snapshot.copy(activeNotice = TransientNotice("reward", "Reward received", null, 50, 0, 4_000))
            assertEquals("Reward received", composer.compose(reward, 0).single().title)
            assertTrue(composer.compose(snapshot, 3_000).none { it.id.endsWith(":fuel") })
        }
    }

    @Test fun `fuel alone cannot hide objective skyblock head or idle location`() {
        val module = dev.skyglint.features.mining.DrillFuelModule { 0L }
        val fuel = module.parse(NormalizedSignal.HeldItemSnapshot(MenuItemSnapshot(0, "Drill",
            listOf("Fuel: 100/10k", "MYTHIC DRILL")))).filterIsInstance<CoreEvent.WidgetObserved>().single().widget
        val snapshot = base().copy(location = "Village", widgets = listOf(fuel),
            progression = ProgressionView.SkyBlock(300, ProgressSnapshot(null, null, .6)))
        val composer = HudComposition()
        val idle = composer.compose(snapshot, 0)
        assertEquals("Village", idle.single { it.id == "main" }.title)
        assertTrue(idle.any { it.id == "skyblock" })
        val task = composer.compose(snapshot.copy(bosses = listOf(BossBarState(UUID(0, 9), "Objective: Craft a workbench", 1f))), 1)
        assertEquals("Craft a workbench", task.single { it.id == "main" }.title)
        assertTrue(task.any { it.id == "skyblock" })
    }

    @Test fun `countdown advances between packets without changing label or body identity`() {
        val timer = CountdownSnapshot(60_000, 60_000)
        val metric = WidgetMetric("event:double_powder", "Double Powder", "server title", priority = 80, countdown = timer)
        val snapshot = base().copy(widgets = listOf(ModuleWidget("skyglint.mining.events", "Events",
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(metric)))))
        val composer = HudComposition()
        val first = composer.compose(snapshot, 1_000).single()
        val second = composer.compose(snapshot, 2_500).single()
        assertEquals("X2", second.value)
        assertEquals(first.id, second.id)
        assertEquals(first.identity, second.identity)
        assertTrue(second.progress!! < first.progress!!)
        assertEquals("0:58", second.countdown!!.caption(2_500))
    }
}
