package dev.skyglint.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoticeQueueTest {
    @Test fun `interrupted carousel resumes its presentation time so every remaining crop can appear`() {
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("jacob", "Jacob's Contest", durationMillis = 6_500, priority = 40,
            pages = listOf(NoticePage("Carrot"), NoticePage("Pumpkin"), NoticePage("Wheat"))), 0)
        queue.offer(rare(), 2_000)
        assertEquals("rare", queue.active(5_999)!!.id)
        val resumed = queue.active(6_000)!!
        assertEquals("jacob", resumed.id)
        assertEquals(2_000, resumed.presentationOffsetMillis)
        val last = queue.active(9_000)!!
        assertEquals(5_000, 9_000 - last.observedAt + last.presentationOffsetMillis)
        assertNull(queue.active(10_500))
    }
    @Test fun `carousel pages copied on enqueue remain stable when caller mutates list`() {
        val pages = mutableListOf(NoticePage("Carrot", "minecraft:carrot"), NoticePage("Wheat", "minecraft:wheat"))
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("jacob", "Jacob's Contest", pages = pages), 0)
        pages.clear()
        assertEquals(listOf("Carrot", "Wheat"), queue.active(100)!!.pages.map { it.title })
    }
    private fun rare(id: String = "rare") = NoticeRequest(id, "Rare drop!", "Enchanted Book")

    @Test fun `duplicate observation does not extend active notification`() {
        val queue = NoticeQueue()
        assertTrue(queue.offer(rare(), 0))
        assertFalse(queue.offer(rare(), 3_000))
        assertEquals(4_000, queue.active(3_000)!!.expiresAt)
        assertNull(queue.active(4_000))
    }

    @Test fun `level up preempts rare drop which resumes remaining time`() {
        val queue = NoticeQueue()
        queue.offer(rare(), 0)
        queue.offer(NoticeRequest("level:2", "SkyBlock level up!", priority = 100), 1_000)
        assertEquals("level:2", queue.active(1_001)!!.id)
        val resumed = queue.active(5_000)!!
        assertEquals("rare", resumed.id)
        assertEquals(8_000, resumed.expiresAt)
        assertNull(queue.active(8_000))
    }

    @Test fun `rare drop received during level up waits without restarting celebration`() {
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("level:2", "SkyBlock level up!", priority = 100), 0)
        queue.offer(rare(), 1_000)
        assertEquals("level:2", queue.active(3_999)!!.id)
        assertEquals("rare", queue.active(4_000)!!.id)
        assertEquals(8_000, queue.active(4_000)!!.expiresAt)
    }

    @Test fun `pending queue is bounded and drops oldest equally low urgency entry`() {
        val queue = NoticeQueue(capacity = 4)
        queue.offer(NoticeRequest("critical", "Critical", priority = 100, durationMillis = 15_000), 0)
        for (index in 1..6) queue.offer(rare("rare:$index"), index * 100L)
        // At 15 seconds the oldest retained rare is still fresh; IDs 1 and 2 were evicted.
        assertEquals("rare:3", queue.active(15_000)!!.id)
        // Only the residual freshness lease is shown; old waits don't get a new 15-second lifetime.
        assertEquals(15_300, queue.active(15_000)!!.expiresAt)
        assertNull(queue.active(16_000))
    }

    @Test fun `newer urgent level replaces previous celebration without replaying it`() {
        val queue = NoticeQueue()
        queue.offer(rare(), 0)
        queue.offer(NoticeRequest("level:2", "Level 2", priority = 100), 1_000)
        queue.offer(NoticeRequest("level:3", "Level 3", priority = 100), 2_000)
        assertEquals("level:3", queue.active(2_001)!!.id)
        assertEquals("rare", queue.active(6_000)!!.id)
        assertNull(queue.active(9_000))
    }

    @Test fun `late snapshots discard expired queue rather than resurrecting old events`() {
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("critical", "Critical", priority = 100), 0)
        queue.offer(rare(), 1_000)
        assertNull(queue.active(20_000))
        queue.clear()
        assertTrue(queue.offer(rare(), 20_001))
        assertEquals("rare", queue.active(20_001)!!.id)
    }
}
