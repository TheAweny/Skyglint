package dev.skyglint.core

import kotlin.test.*

class EventNoticePolicyTest {
    @Test fun `later occurrence first observed inside five minutes rearms after confirmed end`() {
        val policy = EventNoticePolicy()
        policy.observe(event(active = true, expiresAt = 1_000_000).copy(endsAt = 60_000), 0, explicitActive = true)
        val next = event(startsAt = 300_000, observedAt = 120_000, expiresAt = 1_000_000)
        assertEquals("Starts in 3 min", policy.observe(next, 120_000)?.title)
        assertEquals("Event started", policy.observe(event(active = true, observedAt = 300_000, expiresAt = 1_000_000)
            .copy(endsAt = 600_000), 300_000, explicitActive = true)?.title)
    }

    @Test fun `missed active observation does not suppress following occurrence reminder`() {
        val policy = EventNoticePolicy()
        assertNotNull(policy.observe(event(startsAt = 60_000), 0))
        assertEquals("Starts in 3 min", policy.observe(event(startsAt = 600_000, observedAt = 420_000,
            expiresAt = 700_000), 420_000)?.title)
    }

    @Test fun `later explicit active occurrence can rearm after old observed endpoint`() {
        val policy = EventNoticePolicy()
        assertNotNull(policy.observe(event(active = true).copy(endsAt = 60_000), 0, explicitActive = true))
        assertNotNull(policy.observe(event(active = true, observedAt = 600_000, expiresAt = 700_000)
            .copy(endsAt = 960_000), 600_000, explicitActive = true))
    }

    private fun event(name: String = "Year of the Seal", active: Boolean = false,
                      startsAt: Long? = null, observedAt: Long = 0, expiresAt: Long = 30_000) =
        ScheduledEvent(name, startsAt, observedAt, active, "tab", expiresAt)

    @Test fun `login active event announces once and does not become a permanent notice`() {
        val policy = EventNoticePolicy()
        val initial = policy.observe(event(active = true), 1, explicitActive = true)
        assertEquals("Event active", initial?.title)
        assertEquals("Year of the Seal", initial?.detail)
        assertEquals(4_000, initial?.durationMillis)
        assertNull(policy.observe(event(active = true, observedAt = 10_000, expiresAt = 40_000), 10_001, explicitActive = true))
    }

    @Test fun `only fresh observed future schedule gives one reminder in the five minute window`() {
        val policy = EventNoticePolicy()
        val scheduled = event(startsAt = 305_000, expiresAt = 30_000)
        assertNull(policy.observe(scheduled, 1))
        val reminder = policy.observe(scheduled, 5_000)
        assertEquals("Starts in 5 min", reminder?.title)
        assertNull(policy.observe(scheduled, 10_000))
        assertNull(policy.observe(scheduled.copy(startsAt = 320_000, observedAt = 20_000, expiresAt = 50_000), 25_000))
        val stale = EventNoticePolicy()
        assertNull(stale.observe(scheduled.copy(expiresAt = 4_999), 5_000))
    }

    @Test fun `arriving inside reminder window uses actual time and zero does not announce a start`() {
        val policy = EventNoticePolicy()
        assertEquals("Starts in 0:30", policy.observe(event(startsAt = 30_001), 1)?.title)
        assertNull(policy.observe(event(startsAt = 30_001, expiresAt = 60_000), 30_001))
        assertNull(policy.observe(event(startsAt = 30_001, expiresAt = 60_000), 30_002))
        assertEquals("Event started", policy.observe(event(active = true, observedAt = 30_003, expiresAt = 60_000),
            30_003, explicitActive = true)?.title)
    }

    @Test fun `snapshot active is insufficient without an explicit server active observation`() {
        val policy = EventNoticePolicy()
        assertNull(policy.observe(event(active = true), 1))
        assertEquals("Event active", policy.observe(event(active = true), 2, explicitActive = true)?.title)
    }

    @Test fun `new future occurrence can rearm while source rounding cannot`() {
        val policy = EventNoticePolicy()
        assertEquals("Event active", policy.observe(event(active = true), 1, explicitActive = true)?.title)
        assertNull(policy.observe(event(startsAt = 200_000), 2))
        assertNull(policy.observe(event(active = true), 3, explicitActive = true))
        assertNull(policy.observe(event(startsAt = 600_004, observedAt = 4, expiresAt = 700_000), 4))
        assertEquals("Starts in 5 min", policy.observe(event(startsAt = 600_004, observedAt = 4, expiresAt = 700_000), 300_004)?.title)
        assertEquals("Event started", policy.observe(event(active = true, observedAt = 600_005, expiresAt = 700_000),
            600_005, explicitActive = true)?.title)
    }

    @Test fun `connection scoped policy survives island controllers and explicit clear rearms login`() {
        val shared = EventNoticePolicy()
        val first = CoreController(eventNoticePolicy = shared).apply { enterSkyBlock("island-one", 0) }
        val active = CoreEvent.EventObserved(EventScheduleObservation("Year of the Seal", null, true, "tab"))
        first.apply(listOf(active), 1)
        assertEquals("Event active", first.snapshot(2).activeNotice?.title)
        first.enterSkyBlock("island-two", 10)
        first.apply(listOf(active), 11)
        assertNull(first.snapshot(12).activeNotice)
        shared.clear()
        first.enterSkyBlock("new-profile", 20)
        first.apply(listOf(active), 21)
        assertEquals("Event active", first.snapshot(22).activeNotice?.title)
    }

    @Test fun `owned preview policy clears on its own session lifecycle`() {
        val core = CoreController().apply { enterSkyBlock("preview-one", 0) }
        val active = CoreEvent.EventObserved(EventScheduleObservation("Year of the Seal", null, true, "tab"))
        core.apply(listOf(active), 1)
        assertEquals("Event active", core.snapshot(2).activeNotice?.title)
        core.enterSkyBlock("preview-two", 10)
        core.apply(listOf(active), 11)
        assertEquals("Event active", core.snapshot(12).activeNotice?.title)
    }

    @Test fun `confirmed start cancels the obsolete reminder instead of resuming it afterward`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", 5_000, false, "tab"))), 1)
        assertEquals("Starts in 0:05", core.snapshot(2).activeNotice?.title)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", null, true, "tab"))), 1_000)
        assertEquals("Event started", core.snapshot(1_001).activeNotice?.title)
        assertNull(core.snapshot(5_000).activeNotice)
        assertNull(core.snapshot(6_000).activeNotice)
    }

    @Test fun `pending reminder behind a higher priority reward is canceled on explicit start`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("drop", "Rare drop!", priority = 50))), 1)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", 5_000, false, "tab"))), 2)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", null, true, "tab"))), 1_000)
        assertEquals("Rare drop!", core.snapshot(1_001).activeNotice?.title)
        assertEquals("Event started", core.snapshot(4_001).activeNotice?.title)
        assertNull(core.snapshot(8_001).activeNotice)
    }
}
