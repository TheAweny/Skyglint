package dev.skyglint.core

import dev.skyglint.ui.presentation.*
import kotlin.test.*

class WelcomeNoticePolicyTest {
    @Test fun `login waits for data settles freezes three observed pages and keeps ordinary state`() {
        val core = CoreController().apply { enterSkyBlock("synthetic", 0); prepareWelcome(0) }
        assertNull(core.snapshot(0).activeNotice)
        core.apply(listOf(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(287, null)),
            CoreEvent.PetObserved(PetSnapshot("Seal", 70)), CoreEvent.ContextObserved("Village")), 500)
        core.snapshot(500)
        assertNull(core.snapshot(1_499).activeNotice)
        val first = core.snapshot(1_500)
        val notice = first.activeNotice!!
        assertEquals(listOf("SkyBlock · Level 287", "Seal · Lv. 70", "Village"), notice.pages.map { it.title })
        assertEquals(10, notice.priority)
        assertEquals(7_900, notice.expiresAt)
        core.apply(listOf(CoreEvent.ContextObserved("Forest")), 2_000)
        assertEquals(notice.pages, core.snapshot(2_000).activeNotice!!.pages)
        val composer = HudComposition()
        val pages = listOf(1_500L, 4_300, 6_100).map { composer.compose(core.snapshot(it), it).single() }
        assertTrue(pages.all { it.kind == HudElementKind.CAROUSEL && it.title == "Welcome to SkyBlock" })
        assertEquals(listOf(0, 1, 2), pages.map { it.carouselIndex })
        assertEquals(1, pages.map { it.parts }.distinct().size)
        assertNull(core.snapshot(7_900).activeNotice)
        assertEquals("Forest", core.snapshot(7_900).location)
        assertNull(core.snapshot(20_000).activeNotice)
    }

    @Test fun `unknown data is omitted at bounded wait and never replaced by zero`() {
        val policy = WelcomeNoticePolicy()
        policy.arm(0)
        assertNull(policy.observe(null, null, "Village", 0))
        assertNull(policy.observe(null, null, "Village", 4_999))
        val notice = policy.observe(null, null, "Village", 5_000)!!
        assertEquals(listOf("Village"), notice.pages.map { it.title })
        assertNull(policy.observe(100, PetSnapshot("Tiger", 1), "Village", 6_000))
    }

    @Test fun `no data still gives one greeting and session reset cancels pending greeting`() {
        val policy = WelcomeNoticePolicy()
        policy.arm(0)
        val notice = policy.observe(null, null, null, 5_000)!!
        assertTrue(notice.pages.isEmpty())
        assertEquals("Welcome to SkyBlock", notice.title)
        assertTrue(policy.wasOffered)
        policy.clear()
        assertFalse(policy.wasOffered)
        val core = CoreController().apply { enterSkyBlock("one", 0); prepareWelcome(0) }
        core.enterSkyBlock("two", 1_000)
        assertNull(core.snapshot(10_000).activeNotice)
    }

    @Test fun `urgent notice preempts login and carousel presentation offset resumes without lost page`() {
        val core = CoreController().apply { enterSkyBlock("one", 0); prepareWelcome(0) }
        core.apply(listOf(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(7, null)),
            CoreEvent.PetObserved(PetSnapshot("Tiger", 2)), CoreEvent.ContextObserved("Village")), 0)
        core.snapshot(0)
        val first = core.snapshot(1_500).activeNotice!!
        core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("urgent", "Server warning", priority = 75))), 4_400)
        assertEquals("urgent", core.snapshot(4_400).activeNotice?.id)
        val resumed = core.snapshot(8_400)
        assertEquals(first.id, resumed.activeNotice?.id)
        assertEquals(2_900, resumed.activeNotice?.presentationOffsetMillis)
        assertEquals(1, HudComposition().compose(resumed, 8_400).single().carouselIndex)
        assertNull(core.snapshot(20_000).activeNotice)
    }
}
