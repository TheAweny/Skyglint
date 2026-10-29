package dev.skyglint.ui

import dev.skyglint.core.NoticeQueue
import dev.skyglint.core.NoticeRequest
import dev.skyglint.ui.presentation.UpdateNoticePresentation
import dev.skyglint.updates.github.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UpdateNoticePresentationTest {
    @Test fun `critical update yields to gameplay urgent notice and disappears after bounded presentation`() {
        val queue = NoticeQueue()
        val notice = UpdateNoticePresentation.notice(UpdateAnnouncement("1.0.0", UpdateImportance.CRITICAL, 8_000, 30))
        queue.offer(notice, 1_000)
        queue.offer(NoticeRequest("confirmed_level", "Level up", priority = 100, durationMillis = 4_000), 2_000)
        assertEquals("confirmed_level", queue.active(2_500)?.id)
        assertEquals("skyglint.update:1.0.0", queue.active(6_000)?.id)
        assertNull(queue.active(13_001))
    }

    @Test fun `publisher importance changes heading without inventing rarity or reward data`() {
        UpdateImportance.entries.forEach { importance ->
            val notice = UpdateNoticePresentation.notice(UpdateAnnouncement("1.0.0", importance, 4_000, 10))
            assertEquals(UpdateNoticePresentation.heading(importance), notice.title)
            assertEquals("1.0.0", notice.detail)
            assertNull(notice.rarity)
            assertNull(notice.reward)
            assertNull(notice.countdown)
        }
    }
}
