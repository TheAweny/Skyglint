package dev.skyglint.ui

import dev.skyglint.preview.*

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import kotlin.test.*

class MiningCompositionTest {
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
}
