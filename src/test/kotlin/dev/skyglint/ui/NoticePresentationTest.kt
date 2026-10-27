package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class NoticePresentationTest {
    @Test fun `live introduction keeps its last timer while fading into circle or another notice`() {
        val intro = HudElement("event", HudElementKind.MAIN, "Double Powder", "minecraft:redstone", 0,
            value = "2:00 left", liveValue = true)
        assertEquals("1:59 left", intro.displayValue(intro.copy(value = "1:59 left")))
        assertEquals("2:00 left", intro.displayValue(intro.copy(kind = HudElementKind.TIMER, value = "X2", liveValue = false)))
        assertEquals("2:00 left", intro.displayValue(intro.copy(identity = "different", value = "8:00 left")))
    }
    private fun compose(notice: TransientNotice, now: Long) = HudComposition().compose(
        HudSnapshot(true, "notice", emptyList(), null, null, null, activeNotice = notice), now).single()

    @Test fun `same resolved text and artwork do not transition due to notice phase identity or asset alias`() {
        val first = HudElement("main", HudElementKind.MAIN, "Nucleus run complete!", "@notice", 0xFFF28E8E.toInt(),
            identity = "notice:0")
        val second = first.copy(icon = "minecraft:nether_star", identity = "notice:1000", subtitle = "metadata")
        assertEquals(first.visibleContentKey(1), second.visibleContentKey(1))
        assertNotEquals(first.visibleContentKey(1), second.visibleContentKey(2))
        assertNotEquals(first.visibleContentKey(1), second.copy(title = "Different confirmation").visibleContentKey(1))
        assertNotEquals(first.visibleContentKey(1, "rank:s"), first.visibleContentKey(1, "rank:s_plus"))
    }

    @Test fun `single page Nucleus completion never animates identical intro into itself`() {
        val notice = TransientNotice("skyglint.mining.nucleus:complete", "Nucleus run complete!", null,
            60, 0, 3_500, pages = listOf(NoticePage("Nucleus run complete!", "minecraft:nether_star")))
        val first = compose(notice, 0)
        assertEquals("minecraft:nether_star", first.icon)
        assertFalse(first.verticalTransition)
        for (now in listOf(1_000L, 1_349L, 1_350L, 1_600L, 2_000L, 3_499L))
            assertEquals(first, compose(notice, now))
    }

    @Test fun `consecutive identical pages coalesce while real next page still transitions`() {
        val firstPage = NoticePage("Crystal found", "minecraft:emerald")
        val notice = TransientNotice("confirmation", "Crystal found", null, 50, 0, 10_000,
            pages = listOf(firstPage, firstPage, NoticePage("Crystal placed", "minecraft:emerald")))
        val first = compose(notice, 0)
        assertEquals(first, compose(notice, 1_500))
        assertTrue(first.verticalTransition)
        assertEquals("Crystal placed", compose(notice, 5_000).title)
    }

    @Test fun `genuinely different introduction and confirmation retain both stages`() {
        val notice = TransientNotice("confirmation", "Confirmation", null, 50, 0, 4_000,
            pages = listOf(NoticePage("All Divan tools returned", "minecraft:compass")))
        val first = compose(notice, 0)
        assertEquals("Confirmation", first.title)
        assertTrue(first.verticalTransition)
        assertEquals("All Divan tools returned", compose(notice, 1_700).title)
        assertNotEquals(first, compose(notice, 1_700))
    }

    @Test fun `powder uses red redstone theme in both introduction and circle`() {
        val visual = MiningBossVisuals.resolve("double_powder")!!
        assertEquals("minecraft:redstone", visual.icon)
        val red = visual.accent ushr 16 and 255
        assertTrue(red > (visual.accent ushr 8 and 255) && red > (visual.accent and 255))
        val target = NoticeMetricTarget("skyglint.mining.events.current", "event:double_powder")
        val timer = CountdownSnapshot(120_000, 900_000)
        val introduction = compose(TransientNotice("intro", "Double Powder", null, 40, 0, 4_000,
            item = ItemVisualHint(vanillaItemId = visual.icon), countdown = timer, target = target), 0)
        val circle = HudComposition().compose(HudSnapshot(true, "notice", emptyList(), null, null, null,
            widgets = listOf(ModuleWidget(target.widgetId, "Events", presentation = WidgetPresentation(WidgetRole.COUNTERS,
                metrics = listOf(WidgetMetric(target.metricId, "Double Powder", "X2", visual.icon, 80, countdown = timer)))))), 0).single()
        assertEquals(visual.accent, introduction.accent)
        assertEquals(visual.accent, circle.accent)
    }
}
