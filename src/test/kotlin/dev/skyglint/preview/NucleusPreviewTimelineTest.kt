package dev.skyglint.preview

import dev.skyglint.core.*

import dev.skyglint.features.mining.NucleusActivityModule
import kotlin.test.*

class NucleusPreviewTimelineTest {
    @Test fun `sequence observes idle search acquisition placement completion then returns to idle`() {
        val preview = NucleusPreviewTimeline(0)
        val idle = preview.snapshot(0)
        assertNull(idle.activeNotice)
        assertTrue(idle.widgets.none { it.id == NucleusActivityModule.WIDGET_ID })
        val searching = preview.snapshot(2_000)
        assertEquals("Mines of Divan", searching.location)
        assertEquals("2 carried · ≥1/4 returned", searching.widgets.single { it.id == NucleusActivityModule.WIDGET_ID }.detail)
        assertNull(searching.activeNotice)
        val found = preview.snapshot(12_000)
        assertEquals("Crystal Nucleus", found.location)
        assertEquals("Jade Crystal found · 3/5", found.activeNotice!!.title)
        assertTrue(found.widgets.none { it.id == NucleusActivityModule.WIDGET_ID })
        assertNull(found.activeNotice!!.item)
        assertEquals("Jade Crystal placed", preview.snapshot(17_000).activeNotice!!.title)
        assertEquals("Nucleus run complete!", preview.snapshot(22_000).activeNotice!!.title)
        assertNull(preview.snapshot(25_500).activeNotice)
    }

    @Test fun `revalidation never replays chat notices and cycle reset drops old search and confirmations`() {
        val preview = NucleusPreviewTimeline(0)
        preview.snapshot(0)
        preview.snapshot(2_000)
        preview.snapshot(12_000)
        for (at in 12_250L..16_750L step 250) {
            val notice = preview.snapshot(at).activeNotice
            if (at < 15_500) assertEquals(12_000L, notice!!.observedAt)
            else assertNull(notice)
        }
        preview.snapshot(17_000)
        preview.snapshot(22_000)
        val reset = preview.snapshot(30_000)
        assertEquals("SYNTHETIC_NUCLEUS:1", reset.sessionId)
        assertNull(reset.activeNotice)
        assertTrue(reset.widgets.none { it.id == NucleusActivityModule.WIDGET_ID })
        assertEquals("2 carried · ≥1/4 returned", preview.snapshot(32_000).widgets.single { it.id == NucleusActivityModule.WIDGET_ID }.detail)
    }
}
