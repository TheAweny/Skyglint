package dev.skyglint.core

import dev.skyglint.features.EventScheduleModule
import dev.skyglint.features.mining.MiningEventModule
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.HudElementKind
import dev.skyglint.ui.presentation.visibleContentKey
import java.util.UUID
import kotlin.test.*

class TemporaryEventIntegrationTest {
    @Test fun `multi day active event has a compact brief caption and does not return after island calendar refresh`() {
        for ((observed, canonical) in listOf("The great Spook" to "The Great Spook", "Year of the seal" to "Year of the Seal")) {
            assertBriefMultiDayEvent(observed, canonical)
        }
    }

    private fun assertBriefMultiDayEvent(observed: String, canonical: String) {
        val policy = EventNoticePolicy()
        val core = CoreController(eventNoticePolicy = policy).apply {
            modules.register(EventScheduleModule())
            enterSkyBlock("island-one", 0)
        }
        val composition = HudComposition()
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Event: $observed", "Ends In: 2d 3h")), 0)
        val first = composition.compose(core.snapshot(0), 0).single()
        assertEquals("2d 3h left", first.value)
        assertEquals(canonical, core.snapshot(0).scheduledEvents.single().name)
        assertTrue(first.liveValue)
        val ticking = composition.compose(core.snapshot(1_000), 1_000).single()
        assertEquals(first.identity, ticking.identity)
        assertEquals(first.visibleContentKey(0), ticking.visibleContentKey(0))
        assertEquals(183_600_000, core.snapshot(0).activeNotice!!.countdown!!.deadline)
        assertNull(core.snapshot(4_000).activeNotice)
        assertTrue(composition.compose(core.snapshot(4_000), 4_000).isEmpty())

        // The calendar can advertise the next occurrence before tab has re-established this one.
        core.enterSkyBlock("island-two", 10_000)
        core.ingest(NormalizedSignal.MenuSnapshot("Calendar and Events", listOf(
            MenuItemSnapshot(1, canonical, listOf("Starts in: 3d", "Event lasts for 2d!")),
        )), 10_000)
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Event: $canonical", "Ends In: 2d 2h 59m 50s")), 10_000)
        assertNull(core.snapshot(10_001).activeNotice)
        assertTrue(composition.compose(core.snapshot(10_001), 10_001).isEmpty())
        assertEquals(183_600_000, core.snapshot(10_001).scheduledEvents.single().endsAt)
    }

    @Test fun `a known running event ignores future calendar cards until confirmed end permits next occurrence`() {
        val policy = EventNoticePolicy()
        val active = ScheduledEvent("Year of the Seal", null, 0, true, "tab", 30_000, endsAt = 183_600_000)
        assertNotNull(policy.observe(active, 0, explicitActive = true))
        val upcoming = ScheduledEvent("Year of the Seal", 259_200_000, 10_000, false, "calendar", 40_000)
        assertNull(policy.observe(upcoming, 10_000))
        assertNull(policy.observe(active.copy(observedAt = 10_001, expiresAt = 40_001), 10_001, explicitActive = true))
        policy.confirmedEnd("Year of the Seal", 20_000)
        val later = upcoming.copy(startsAt = 30_000, observedAt = 20_001, expiresAt = 50_001)
        assertEquals("Starts in 0:10", policy.observe(later, 20_001)?.title)
        assertEquals("Event started", policy.observe(active.copy(observedAt = 30_000, expiresAt = 60_000,
            endsAt = 213_600_000), 30_000, explicitActive = true)?.title)
    }

    @Test fun `unknown bossbar with a long raw clock is preserved rather than guessed as a global event`() {
        val core = CoreController().apply { enterSkyBlock("boss", 0) }
        val id = UUID(0, 99)
        core.updateBoss(id, "Unknown future event 3060:00", .5f, now = 0)
        val snapshot = core.snapshot(5_000)
        assertEquals(id, snapshot.bosses.single().id)
        assertEquals("Unknown future event 3060:00", HudComposition().compose(snapshot, 5_000).single().title)
        assertTrue(snapshot.scheduledEvents.isEmpty())
        assertNull(snapshot.activeNotice)
    }

    private var now = 0L
    private fun core() = CoreController().apply {
        modules.register(MiningEventModule { now })
        enterSkyBlock("server-one", 0)
    }
    private fun CoreController.context() = ingest(NormalizedSignal.ContextSnapshot("Dwarven Mines", "mining_3"), now)
    private fun CoreController.powder(seconds: Int) = ingest(NormalizedSignal.BossSnapshot(listOf(BossBarState(
        UUID(0, 8), "PASSIVE EVENT 2X POWDER RUNNING FOR ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", .8f))), now)

    @Test fun `late join introduction contracts into same circle and drains from verified full duration`() {
        val core = core(); core.context(); core.powder(120)
        val composer = HudComposition()
        val intro = composer.compose(core.snapshot(0), 0).single()
        assertEquals("Double Powder", intro.title)
        assertEquals("2:00 left", intro.value)
        assertEquals(HudElementKind.MAIN, intro.kind)
        assertEquals(120.0 / 900, intro.progress!!, 1e-9)
        for (second in 1..5) {
            now = second * 1_000L + 100
            core.context(); core.powder(120 - second)
        }
        val circle = composer.compose(core.snapshot(now), now).single()
        assertEquals(HudElementKind.TIMER, circle.kind)
        assertEquals(intro.id, circle.id)
        assertEquals(intro.identity, circle.identity)
        assertEquals(120_000, circle.countdown!!.deadline)
        assertEquals(900_000, circle.countdown!!.intervalMillis)
        assertTrue(circle.progress!! < intro.progress!!)
        assertNull(core.snapshot(now).activeNotice)
    }

    @Test fun `removed event cannot resume introduction after higher priority preemption`() {
        val core = core(); core.context(); core.powder(120)
        now = 1_000
        core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("reward", "Rare drop", priority = 50))), now)
        core.ingest(NormalizedSignal.BossSnapshot(emptyList()), now)
        assertEquals("Rare drop", core.snapshot(now).activeNotice!!.title)
        assertNull(core.snapshot(5_001).activeNotice)
        assertTrue(core.snapshot(5_001).widgets.isEmpty())
    }

    @Test fun `same island on new server clears timer and may introduce only fresh observation`() {
        val core = core(); core.context(); core.powder(120)
        now = 1_000; core.enterSkyBlock("server-two", now)
        assertTrue(core.snapshot(now).widgets.isEmpty())
        assertNull(core.snapshot(now).activeNotice)
        core.context()
        assertNull(core.snapshot(now).activeNotice)
        core.powder(300)
        assertEquals("Double Powder", core.snapshot(now).activeNotice!!.title)
        assertEquals(301_000, core.snapshot(now).activeNotice!!.countdown!!.deadline)
    }

    @Test fun `large correction adjusts deadline while retaining full denominator without another introduction`() {
        val core = core(); core.context(); core.powder(120)
        now = 4_100; core.context(); core.powder(300)
        assertNull(core.snapshot(now).activeNotice)
        val timer = core.snapshot(now).widgets.single().presentation!!.metrics.single().countdown!!
        assertEquals(304_100, timer.deadline)
        assertEquals(900_000, timer.intervalMillis)
    }

    @Test fun `calendar retains all observations and nearest event stays nearest regardless of processing order`() {
        val core = CoreController().apply { enterSkyBlock("calendar", 0); modules.register(EventScheduleModule()) }
        core.ingest(NormalizedSignal.MenuSnapshot("Calendar and Events", listOf(
            MenuItemSnapshot(1, "Traveling Zoo", listOf("Starts in: 2h", "Event lasts for 1h!")),
            MenuItemSnapshot(2, "Dark Auction", listOf("Starts in: 40s", "Event lasts for 20m!")),
        )), 1)
        val snapshot = core.snapshot(2)
        assertEquals(2, snapshot.scheduledEvents.size)
        assertEquals("Dark Auction", snapshot.nextEvent!!.name)
        assertEquals("dark_auction", snapshot.nextEvent!!.definitionId)
        assertEquals(1_200_000, snapshot.nextEvent!!.durationMillis)
        assertTrue(snapshot.scheduledEvents.none { it.active })
    }

    @Test fun `active sidebar countdown has stable local endpoint and no guessed total or start from zero`() {
        val core = CoreController().apply { enterSkyBlock("sidebar", 0); modules.register(EventScheduleModule()) }
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Spooky Festival 50:54")), 0)
        val first = core.snapshot(0)
        assertEquals(3_054_000, first.nextEvent!!.endsAt)
        assertNull(first.nextEvent!!.durationMillis)
        assertEquals(3_054_000, first.activeNotice!!.countdown!!.deadline)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Spooky Festival 50:53")), 1_100)
        assertEquals(first.nextEvent!!.endsAt, core.snapshot(1_100).nextEvent!!.endsAt)
        assertEquals(first.activeNotice!!.observedAt, core.snapshot(1_100).activeNotice!!.observedAt)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Spooky Festival 0:00")), 2_000)
        assertNull(core.snapshot(2_000).nextEvent)
        assertNull(core.snapshot(2_000).activeNotice)
    }

    @Test fun `catalog distinguishes shared schedule random server timers and contextual Jacob without detection`() {
        val powder = TemporaryEventCatalog.resolve("§b2x Powder")!!
        assertEquals(900_000, powder.durationMillis)
        assertEquals(TemporaryEventScope.SERVER, powder.scope)
        assertEquals(TemporaryEventPresentation.TIMER_CIRCLE, powder.presentation)
        assertEquals("mining_fiesta", TemporaryEventCatalog.resolve("Bonus Mining Fiesta")!!.id)
        assertEquals("anniversary", TemporaryEventCatalog.resolve("5th Anniversary")!!.id)
        assertEquals(TemporaryEventPresentation.CONTEXT_WIDGET, TemporaryEventCatalog.resolve("Jacob's Contest")!!.presentation)
        assertNull(TemporaryEventCatalog.resolve("Unknown future event"))
        assertEquals(TemporaryEventCatalog.definitions.size, TemporaryEventCatalog.definitions.map { it.id }.toSet().size)
    }

    @Test fun `fresh calendar total combines with confirmed active remaining without making calendar active`() {
        val core = CoreController().apply { enterSkyBlock("events", 0) }
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Mining Fiesta", 10_000, false,
            "calendar", durationMillis = 3_600_000))), 0)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Bonus Mining Fiesta", null, true,
            "tab", endsInMillis = 1_800_000))), 1_000)
        val active = core.snapshot(1_000)
        assertEquals(1, active.scheduledEvents.size)
        assertEquals("Mining Fiesta", active.nextEvent!!.name)
        assertEquals(3_600_000, active.nextEvent!!.durationMillis)
        assertEquals(1_801_000, active.activeNotice!!.countdown!!.deadline)
    }

    @Test fun `long independent server event names cannot collide through channel truncation`() {
        val core = CoreController().apply { enterSkyBlock("events", 0); modules.register(EventScheduleModule()) }
        val common = "Long upcoming event ".repeat(3)
        val names = listOf(common + "Alpha", common + "Bravo")
        core.ingest(NormalizedSignal.TabSnapshot(names.flatMap { listOf("Event: $it", "Starts In: 1h") }), 0)
        assertEquals(names.toSet(), core.snapshot(0).scheduledEvents.map { it.name }.toSet())
    }
}
