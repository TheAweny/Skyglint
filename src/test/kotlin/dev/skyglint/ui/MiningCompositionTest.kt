package dev.skyglint.ui

import dev.skyglint.preview.*

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import java.util.UUID
import kotlin.test.*

class MiningCompositionTest {
    private val eventBoss = UUID(0, 71)
    private val eventTarget = NoticeMetricTarget("skyglint.mining.events.current", "event:better_together")
    private fun eventMetric() = WidgetMetric(eventTarget.metricId, "Better Together", "Together · 0:02",
        "minecraft:amethyst_shard", 80, countdown = CountdownSnapshot(2_000, 240_000),
        replacedBossIds = setOf(eventBoss))
    private fun eventState(metric: WidgetMetric, expiry: Long = 5_000) = HudSnapshot(true, "mining",
        listOf(BossBarState(eventBoss, "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 00:00", 0f)), null, null, null,
        widgets = listOf(ModuleWidget(eventTarget.widgetId, "Mining events", expiresAt = expiry,
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(metric)),
            replacedBossIds = setOf(eventBoss))))

    @Test fun `mining keeps stable event circles but no permanent fuel powder or idle location`() {
        val timeline = MiningPreviewTimeline(0)
        val composer = HudComposition()
        val introduction = composer.compose(timeline.snapshot(0), 0).single()
        assertEquals("Better Together", introduction.title)
        assertEquals(HudElementKind.MAIN, introduction.kind)
        assertEquals("3:59 left", introduction.value)
        val initial = composer.compose(timeline.snapshot(4_500), 4_500)
        assertEquals(introduction.id, initial.single { it.kind == HudElementKind.TIMER }.id)
        assertTrue(initial.any { it.kind == HudElementKind.TIMER && it.title == "Better Together" })
        assertTrue(initial.none { it.value == "5395/10k" || it.title.endsWith("Powder") && it.kind != HudElementKind.TIMER })
        val powderIntro = composer.compose(timeline.snapshot(10_000), 10_000).single()
        assertEquals("Double Powder", powderIntro.title)
        val doubled = composer.compose(timeline.snapshot(14_500), 14_500)
        val circle = doubled.single { it.kind == HudElementKind.TIMER }
        assertEquals("X2", circle.value)
        assertNotNull(circle.countdown)
        assertEquals(powderIntro.id, circle.id)
        assertEquals(powderIntro.identity, circle.identity)
        assertEquals(900_000, circle.countdown!!.intervalMillis)
        assertTrue(doubled.none { it.title.contains("RUNNING FOR") })
        val later = composer.compose(timeline.snapshot(15_000), 15_000).single { it.kind == HudElementKind.TIMER }
        assertEquals(circle.id, later.id)
        assertEquals(circle.identity, later.identity)
        assertEquals(circle.value, later.value)
        assertEquals(circle.countdown!!.deadline, later.countdown!!.deadline)
        assertTrue(later.progress!! < circle.progress!!)
        timeline.snapshot(20_000) // New server/island context gets a fresh brief introduction.
        val hot = composer.compose(timeline.snapshot(24_500), 24_500)
        assertEquals("Mining", hot.single { it.kind == HudElementKind.MAIN }.title)
        assertTrue(hot.any { it.value == "74♨" })
        assertTrue(hot.none { it.title == "Cold" })
    }

    @Test fun `changing server timer digits never restart the active capsule content`() {
        val metric = eventMetric()
        val composer = HudComposition()
        val first = composer.compose(eventState(metric), 0).single()
        val later = composer.compose(eventState(metric.copy(value = "Together · 0:01")), 1_000).single()
        assertEquals(HudElementKind.TIMER, first.kind)
        assertEquals(first.id, later.id)
        assertEquals(first.identity, later.identity)
        assertEquals(first.visibleContentKey(0), later.visibleContentKey(0))
        assertTrue(later.progress!! < first.progress!!)
        assertEquals("0:01", later.countdown!!.caption(1_000))
    }

    @Test fun `event circle expands into ending text without exposing raw zero bar then disappears`() {
        val composer = HudComposition()
        val active = composer.compose(eventState(eventMetric()), 1_000).single()
        val endedMetric = eventMetric().copy(label = "Better Together ended", value = "", countdown = null,
            expandedUntil = 4_000)
        val ending = eventState(endedMetric, expiry = 4_000)
        for (now in listOf(2_000L, 3_999L)) {
            val shown = composer.compose(ending, now).single()
            assertEquals(active.id, shown.id)
            assertEquals(active.identity, shown.identity)
            assertEquals(HudElementKind.MAIN, shown.kind)
            assertEquals("Better Together ended", shown.title)
            assertEquals(active.icon, shown.icon)
            assertNull(shown.value)
            assertNull(shown.countdown)
            assertNull(shown.progress)
        }
        assertTrue(composer.compose(ending.copy(bosses = emptyList()), 4_000).isEmpty())
    }

    @Test fun `ending state replaces still active introduction immediately with same ending payload`() {
        val metric = eventMetric().copy(label = "Better Together ended", value = "", countdown = null,
            expandedUntil = 4_000)
        val state = eventState(metric, expiry = 4_000)
        val intro = TransientNotice("mining:intro", "Better Together", null, 40, 0, 5_000,
            item = ItemVisualHint(vanillaItemId = metric.icon), countdown = CountdownSnapshot(2_000, 240_000),
            target = eventTarget)
        val composer = HudComposition()
        val endingIntro = composer.compose(state.copy(activeNotice = intro), 2_000).single()
        val ending = composer.compose(state, 2_000).single()
        assertEquals("Better Together ended", endingIntro.title)
        assertNull(endingIntro.value)
        assertNull(endingIntro.countdown)
        assertEquals(ending.id, endingIntro.id)
        assertEquals(ending.visibleContentKey(0), endingIntro.visibleContentKey(0))
    }

    @Test fun `hidden capped or expired ending metric releases original boss UUID`() {
        val metric = eventMetric().copy(label = "Better Together ended", value = "", countdown = null,
            expandedUntil = 4_000)
        val state = eventState(metric, expiry = 4_000)
        val disabled = HudComposition().compose(state, 2_000, HudPreferences(showMiningTimers = false))
        assertEquals("boss:$eventBoss", disabled.single().identity)
        assertEquals(state.bosses.single().title, disabled.single().title)
        val busy = ModuleWidget("test.busy", "Busy", presentation = WidgetPresentation(WidgetRole.COUNTERS,
            metrics = listOf(WidgetMetric("busy", "Busy", "42", priority = 90))))
        val capped = HudComposition().compose(state.copy(widgets = state.widgets + busy), 2_000,
            HudPreferences(helperLimit = 1))
        assertEquals("boss:$eventBoss", capped.single { it.kind == HudElementKind.MAIN }.identity)
        assertTrue(capped.none { it.id == "metric:${eventTarget.widgetId}:${eventTarget.metricId}" })
        val expired = HudComposition().compose(state, 4_000)
        assertEquals("boss:$eventBoss", expired.single().identity)
    }
}
