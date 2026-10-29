package dev.skyglint.preview

import dev.skyglint.features.winter.JerryDefenseModule
import dev.skyglint.core.RewardDelivery
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.HudElementKind
import kotlin.test.*

class WinterPreviewTimelineTest {
    @Test fun `winter previews use production modules and keep point metadata inside one primary`() {
        val snapshot = WinterPreviewTimeline(0, PreviewScenario.WINTER).snapshot(0)
        assertEquals(5, snapshot.widgets.single { it.id == JerryDefenseModule.WIDGET_ID }.presentation!!.metrics.size)
        assertTrue(snapshot.sessionId!!.startsWith("SYNTHETIC_"))
        assertEquals(1, snapshot.bosses.size)
        val dragon = WinterPreviewTimeline(0, PreviewScenario.REINDRAKE).snapshot(0)
        assertTrue(dragon.widgets.any { it.id.startsWith("skyglint.winter.reindrake:") })
        assertTrue(WinterPreviewTimeline(0, PreviewScenario.BUFFS).snapshot(0).activeNotice!!.title.contains("Booster Cookie"))
    }

    @Test fun `observed low buff previews and captured result replay reach loot and every stat page`() {
        for (scenario in listOf(PreviewScenario.BUFF_WARNING, PreviewScenario.GOD_WARNING)) {
            val notice = WinterPreviewTimeline(0, scenario).snapshot(0).activeNotice!!
            assertTrue(notice.title.endsWith("expiring soon"))
            assertTrue(notice.detail!!.contains("5:00"))
        }
        val timeline = WinterPreviewTimeline(0, PreviewScenario.DEFENSE_RESULTS)
        val composer = HudComposition()
        val items = listOf("White Gift", "Volcanic Rock", "Red Gift", "Green Gift")
        val lootFrames = listOf(0L, 1_800L, 3_600L, 5_400L).map { at ->
            val snapshot = timeline.snapshot(at)
            val notice = snapshot.activeNotice!!
            assertEquals(items, notice.loot.map { it.itemName })
            assertEquals(listOf(34L, 5L, 8L, 30L), notice.loot.map { it.amount })
            assertTrue(notice.loot.all { it.delivery == RewardDelivery.CLAIMABLE })
            assertEquals(7_200L, notice.expiresAt)
            composer.compose(snapshot, at).single()
        }
        assertTrue(lootFrames.all { it.kind == HudElementKind.CAROUSEL && it.title == "Available" })
        assertEquals(items, lootFrames.map { it.parts[it.carouselIndex].text })
        assertEquals(listOf(" · ×34", " · ×5", " · ×8", " · ×30"), lootFrames.map { it.parts[it.carouselIndex].tail })
        val intro = timeline.snapshot(7_200)
        assertTrue(intro.activeNotice!!.id.startsWith("skyglint.winter.defense.result:"))
        val expectedPages = intro.activeNotice!!.pages.map { it.title }
        assertEquals(4, expectedPages.size)
        val statTitles = listOf(8_200L, 10_000L, 11_800L, 13_600L).map { at ->
            val snapshot = timeline.snapshot(at)
            assertTrue(snapshot.activeNotice!!.loot.isEmpty())
            composer.compose(snapshot, at).single().title
        }
        assertEquals(expectedPages, statTitles)
        assertNull(timeline.snapshot(15_400).activeNotice)
        assertNull(timeline.snapshot(23_000).activeNotice)
    }
}
