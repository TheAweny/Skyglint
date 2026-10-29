package dev.skyglint.core

import kotlin.test.*

class NoticeQueueFilteringTest {
    @Test fun `removing a preempted notice preserves survivor presentation offset and dedupe`() {
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("survivor", "Carousel", priority = 10, durationMillis = 6_000), 0)
        queue.offer(NoticeRequest("hidden", "Celebration", priority = 60), 1_000)
        queue.offer(NoticeRequest("pending-hidden", "Queued", priority = 20), 1_500)
        queue.retain(2_000) { it.id == "survivor" }
        val resumed = assertNotNull(queue.active(2_000))
        assertEquals("survivor", resumed.id)
        assertEquals(1_000, resumed.presentationOffsetMillis)
        assertEquals(7_000, resumed.expiresAt)
        assertFalse(queue.offer(NoticeRequest("hidden", "Duplicate"), 2_001))
        assertNull(queue.active(7_000))
    }

    @Test fun `hidden observation consumes dedupe without preempting an active notice`() {
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("visible", "Visible", priority = 10), 0)
        assertTrue(queue.offer(NoticeRequest("hidden", "Hidden", priority = 100), 500, visible = false))
        assertEquals("visible", queue.active(501)!!.id)
        assertFalse(queue.offer(NoticeRequest("hidden", "Duplicate", priority = 100), 600))
        assertNull(queue.active(4_000))
    }

    @Test fun `presentation filtering does not bypass target freshness for surviving notices`() {
        val queue = NoticeQueue()
        val target = NoticeMetricTarget("events", "powder")
        queue.offer(NoticeRequest("intro", "Powder", target = target, priority = 10), 0)
        queue.offer(NoticeRequest("urgent", "Urgent", priority = 60), 500)
        queue.retain(1_000) { it.id != "urgent" }
        assertEquals("intro", queue.active(1_000)!!.id)
        queue.retainTargets(emptySet(), 1_001)
        assertNull(queue.active(1_001))
        queue.retain(1_002) { true }
        assertNull(queue.active(1_002))
    }
}
