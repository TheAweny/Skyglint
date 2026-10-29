package dev.skyglint.core

import kotlin.test.*

class RewardCarouselQueueTest {
    private fun reward(name: String, source: String = "defense:one", delivery: RewardDelivery = RewardDelivery.CLAIMABLE,
                       amount: Long? = null) = RewardObservation(name, "Defense rewards", source,
        vanillaItemId = "minecraft:chest", amount = amount, delivery = delivery)
    private fun request(name: String, source: String = "defense:one", delivery: RewardDelivery = RewardDelivery.CLAIMABLE,
                        amount: Long? = null) = NoticeRequest("reward:$source:$name:$delivery", "Defense rewards",
        detail = name, reward = reward(name, source, delivery, amount))

    @Test fun `four rewards from one delivery become one measured carousel lease without summed amounts`() {
        val queue = NoticeQueue()
        val items = listOf("White Gift", "Green Gift", "Red Gift", "Winter Sack")
        items.forEachIndexed { index, name -> assertTrue(queue.offer(request(name, amount = if(index==0)2 else null), index*100L)) }
        val shown = queue.active(300)!!
        assertEquals(items, shown.loot.map { it.itemName })
        assertEquals(listOf(2L, null, null, null), shown.loot.map { it.amount })
        assertEquals(7_200L, shown.expiresAt)
        assertEquals("White Gift", shown.reward!!.itemName)
        assertNull(queue.active(7_200))
    }

    @Test fun `single reward and unrelated notices preserve their original shape and timing`() {
        val queue = NoticeQueue()
        val first = request("Sorrow")
        queue.offer(first, 0)
        assertTrue(queue.active(100)!!.loot.isEmpty())
        assertEquals(first.reward, queue.active(100)!!.reward)
        queue.offer(NoticeRequest("jacob", "Jacob Contest Starting", priority = 40,
            pages = listOf(NoticePage("Carrot"), NoticePage("Wheat"))), 200)
        assertEquals(4_000L, queue.active(200)!!.expiresAt)
        assertEquals("jacob", queue.active(4_000)!!.id)
        assertTrue(queue.active(4_000)!!.loot.isEmpty())
    }

    @Test fun `source delivery and fixed one second burst boundary prevent unrelated aggregation`() {
        val queue = NoticeQueue()
        queue.offer(request("One"), 0)
        queue.offer(request("Two"), 1_000)
        queue.offer(request("Three"), 1_001)
        queue.offer(request("Other", source = "defense:two"), 1_002)
        queue.offer(request("Received", delivery = RewardDelivery.RECEIVED), 1_003)
        assertEquals(listOf("One", "Two"), queue.active(1_003)!!.loot.map { it.itemName })
        assertEquals("Three", queue.active(4_000)!!.reward!!.itemName)
        assertTrue(queue.active(4_000)!!.loot.isEmpty())
        assertEquals("Other", queue.active(8_000)!!.reward!!.itemName)
        assertEquals(RewardDelivery.RECEIVED, queue.active(12_000)!!.reward!!.delivery)
    }

    @Test fun `duplicate member IDs remain consumed for the entire aggregate and hidden items never join`() {
        val queue = NoticeQueue()
        queue.offer(request("One"), 0)
        queue.offer(request("Two"), 100)
        assertTrue(queue.offer(request("Hidden"), 200, visible = false))
        assertFalse(queue.offer(request("Hidden"), 300))
        assertFalse(queue.offer(request("Two"), 2_500))
        assertEquals(listOf("One", "Two"), queue.active(2_500)!!.loot.map { it.itemName })
        assertEquals(4_000L, queue.active(2_500)!!.expiresAt)
    }

    @Test fun `eight item bound applies inside a single queue slot and overflow stays in a separate batch`() {
        val queue = NoticeQueue(capacity = 1)
        queue.offer(NoticeRequest("level", "Level up", priority = 100, durationMillis = 5_000), 0)
        (1..8).forEach { queue.offer(request("Item $it"), it*10L) }
        assertEquals(8, queue.active(5_000)!!.loot.size)
        assertEquals(19_400L, queue.active(5_000)!!.expiresAt)
        assertNull(queue.active(19_400))
        val overflow = NoticeQueue()
        (1..9).forEach { overflow.offer(request("Item $it"), it*10L) }
        assertEquals(8, overflow.active(100)!!.loot.size)
        assertEquals("Item 9", overflow.active(14_410)!!.reward!!.itemName)
    }

    @Test fun `aggregation while preempted extends only remaining pages and retains presentation offset`() {
        val queue = NoticeQueue()
        queue.offer(request("One"), 0)
        queue.offer(request("Two"), 100)
        queue.offer(NoticeRequest("level", "Level up", priority = 100), 500)
        queue.offer(request("Three"), 700)
        queue.offer(request("Four"), 900)
        assertEquals("level", queue.active(4_499)!!.id)
        val resumed = queue.active(4_500)!!
        assertEquals(4, resumed.loot.size)
        assertEquals(500L, resumed.presentationOffsetMillis)
        assertEquals(11_200L, resumed.expiresAt)
        assertNull(queue.active(11_200))
    }

    @Test fun `queued aggregate still obeys original sixty second source freshness bound`() {
        val queue = NoticeQueue()
        queue.offer(NoticeRequest("urgent", "Urgent", priority = 100, durationMillis = 15_000), 0)
        queue.offer(request("One").copy(freshnessMillis = 60_000), 1)
        queue.offer(request("Two").copy(freshnessMillis = 60_000), 1_000)
        assertNotNull(queue.active(15_000))
        assertNull(queue.active(60_002))
    }

    @Test fun `copied loot owns caller list and clear resets aggregate membership and dedupe`() {
        val queue = NoticeQueue()
        val loot = mutableListOf(reward("One"), reward("Two"))
        queue.offer(request("One").copy(loot = loot), 0)
        loot.clear()
        val shown = queue.active(100)!!
        assertEquals(2, shown.loot.size)
        assertFailsWith<UnsupportedOperationException> { (shown.loot as MutableList<RewardObservation>).clear() }
        queue.clear()
        assertNull(queue.active(101))
        assertTrue(queue.offer(request("One"), 102))
        assertTrue(queue.active(102)!!.loot.isEmpty())
    }

    @Test fun `per item reward policy executes before an observed burst reaches the queue`() {
        val core = CoreController().apply {
            rewardPolicy = RewardRulePolicy.fromConfig("all", mapOf("red gift" to "hide"))
            enterSkyBlock("one", 0)
        }
        core.apply(listOf("White Gift", "Red Gift", "Green Gift", "Winter Sack").map {
            CoreEvent.RewardObserved(reward(it))
        }, 100)
        assertEquals(listOf("White Gift", "Green Gift", "Winter Sack"), core.snapshot(101).activeNotice!!.loot.map { it.itemName })
        assertTrue(core.snapshot(101).widgets.isEmpty())
    }
}
