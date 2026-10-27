package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.NoticeCarousel
import kotlin.test.*
import org.junit.jupiter.api.Test

class NoticeCarouselTest {
    @Test fun `intro then three readable pages hold final page without looping`() {
        assertEquals(-1, NoticeCarousel.pageIndex(-10, 3))
        assertEquals(-1, NoticeCarousel.pageIndex(999, 3))
        assertEquals(0, NoticeCarousel.pageIndex(1_000, 3))
        assertEquals(0, NoticeCarousel.pageIndex(2_799, 3))
        assertEquals(1, NoticeCarousel.pageIndex(2_800, 3))
        assertEquals(2, NoticeCarousel.pageIndex(4_600, 3))
        assertEquals(2, NoticeCarousel.pageIndex(50_000, 3))
        assertEquals(-1, NoticeCarousel.pageIndex(4_600, 0))
    }

    @Test fun `Jacob keeps its event heading while only crop content changes horizontally`() {
        val notice = TransientNotice("skyglint.garden.jacob.started", "Jacob's Contest", "Started", 40, 0, 6_500,
            pages = listOf(NoticePage("Carrot", "minecraft:carrot"), NoticePage("Potato", "minecraft:potato"),
                NoticePage("Wheat", "minecraft:wheat")))
        val snapshot = HudSnapshot(true, "fixture", emptyList(), null, null, null, activeNotice = notice)
        val composition = HudComposition()
        for ((time, page) in listOf(0L to 0, 1_000L to 0, 2_800L to 1, 4_600L to 2, 6_499L to 2)) {
            val shown = composition.compose(snapshot, time).single()
            assertEquals("Jacob Contest Starting", shown.title)
            assertEquals(dev.skyglint.ui.presentation.HudElementKind.CAROUSEL, shown.kind)
            assertEquals(page, shown.carouselIndex)
            assertEquals(listOf("Carrot", "Potato", "Wheat"), shown.parts.map { it.text })
            assertEquals("minecraft:carrot", shown.parts.first().icon)
            assertFalse(shown.verticalTransition)
            assertEquals("${notice.id}:0", shown.identity)
        }
        val resumed = snapshot.copy(activeNotice = notice.copy(observedAt = 6_000, expiresAt = 10_500, presentationOffsetMillis = 2_000))
        val shown = composition.compose(resumed, 9_000).single()
        assertEquals(2, shown.carouselIndex)
        assertEquals("Jacob Contest Starting", shown.title)
    }
}
