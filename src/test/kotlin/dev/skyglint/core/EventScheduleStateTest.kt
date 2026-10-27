package dev.skyglint.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventScheduleStateTest {
    @Test fun `server countdown deadline does not infer event start and stale observations disappear`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", 10_000, false, "tab"))), 1)
        val before = core.snapshot(10_000).nextEvent!!
        assertEquals(10_001, before.startsAt)
        assertFalse(core.snapshot(10_002).nextEvent!!.active)
        assertNull(core.snapshot(30_001).nextEvent)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", null, true, "chat"))), 30_002)
        assertTrue(core.snapshot(30_003).nextEvent!!.active)
        assertNull(core.snapshot(30_003).nextEvent!!.startsAt)
        core.enterSkyBlock("different-profile", 30_004)
        assertNull(core.snapshot(30_004).nextEvent)
    }

    @Test fun `invalid countdowns and stale context cannot rewind state or invent an event`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        core.apply(listOf(CoreEvent.ContextObserved("Hub", "hub")), 10)
        assertTrue(core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation(" ", 0, false, "tab"))), 11).isEmpty())
        assertTrue(core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Future", -1, false, "tab"))), 12).isEmpty())
        assertTrue(core.apply(listOf(CoreEvent.ContextObserved("Old", "old")), 9).isEmpty())
        assertEquals("Hub", core.snapshot(13).location)
        assertNull(core.snapshot(13).nextEvent)
    }

    @Test fun `fresh unchanged countdown revalidation does not push its actual deadline forward`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        val event = CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", 7 * 3_600_000L, false, "tab"))
        core.apply(listOf(event), 1)
        val initial = core.snapshot(1).nextEvent!!.startsAt
        core.apply(listOf(event), 10_001)
        assertEquals(initial, core.snapshot(35_000).nextEvent!!.startsAt)
        assertNull(core.snapshot(40_001).nextEvent)
        core.apply(listOf(event), 50_000)
        assertEquals(50_000 + 7 * 3_600_000L, core.snapshot(50_000).nextEvent!!.startsAt)
    }

    @Test fun `long verified calendar duration is accepted within seven day bound`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        assertEquals(1, core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Calendar", 26 * 3_600_000L, false, "menu"))), 1).size)
        assertTrue(core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Calendar", 604_800_001, false, "menu"))), 2).isEmpty())
    }

    @Test fun `first active is briefly announced but only explicit upcoming to active creates a start notice`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", null, true, "tab"))), 1)
        assertEquals("Event active", core.snapshot(2).activeNotice!!.title)
        assertNull(core.snapshot(4_001).activeNotice)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", null, true, "tab"))), 4_002)
        assertNull(core.snapshot(4_003).activeNotice)
        core.enterSkyBlock("fresh-connection", 5_000)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", 10_000, false, "tab"))), 5_001)
        assertEquals("Starts in 0:10", core.snapshot(5_002).activeNotice!!.title)
        assertNull(core.snapshot(15_002).activeNotice)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", null, true, "tab"))), 15_003)
        assertEquals("Event started", core.snapshot(15_004).activeNotice!!.title)
        assertEquals(40, core.snapshot(15_004).activeNotice!!.priority)
        assertNull(core.snapshot(19_003).activeNotice)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Jacob's Contest", null, true, "tab"))), 19_004)
        assertNull(core.snapshot(19_005).activeNotice)
    }

    @Test fun `tab revalidation cannot overwrite an earlier precise calendar deadline for the same event`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        val tab = CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", 7 * 3_600_000L, false, "tab"))
        val calendar = CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", (6 * 60 + 10) * 60_000L, false, "calendar"))
        core.apply(listOf(tab), 1)
        val originalTabDeadline = core.snapshot(1).nextEvent!!.startsAt
        core.apply(listOf(calendar), 1_001)
        val preciseDeadline = core.snapshot(1_001).nextEvent!!.startsAt
        core.apply(listOf(tab), 10_001)
        assertEquals(preciseDeadline, core.snapshot(10_002).nextEvent!!.startsAt)
        assertEquals("calendar", core.snapshot(10_002).nextEvent!!.source)
        core.apply(listOf(tab), 20_001)
        assertEquals(preciseDeadline, core.snapshot(30_000).nextEvent!!.startsAt)
        // Once the exact source is stale, the remaining fresh tab still retains its first deadline.
        assertEquals(originalTabDeadline, core.snapshot(31_001).nextEvent!!.startsAt)
    }

    @Test fun `explicit active source wins over fresh upcoming source for the same event`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        val upcoming = CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", 7 * 3_600_000L, false, "calendar"))
        core.apply(listOf(upcoming), 1)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Spooky Festival", null, true, "tab"))), 2)
        val noticeId = core.snapshot(3).activeNotice!!.id
        core.apply(listOf(upcoming), 1_001)
        assertTrue(core.snapshot(1_002).nextEvent!!.active)
        assertEquals("tab", core.snapshot(1_002).nextEvent!!.source)
        assertEquals(noticeId, core.snapshot(1_002).activeNotice!!.id)
        core.enterSkyBlock("new-profile", 2_000)
        core.apply(listOf(upcoming), 2_001)
        assertFalse(core.snapshot(2_002).nextEvent!!.active)
        assertNull(core.snapshot(2_002).activeNotice)
    }

    @Test fun `source cache is bounded and a new event name still selects that newly observed event`() {
        val core = CoreController().apply { enterSkyBlock("event", 0) }
        for (index in 0..64) {
            core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Shared", 10_000, false, "source:$index"))), index + 1L)
        }
        // Calendar cards use separate source IDs; sixty-five retain only the freshest sixty-four.
        assertEquals(10_002, core.snapshot(66).nextEvent!!.startsAt)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Different event", 5_000, false, "different"))), 67)
        assertEquals("Different event", core.snapshot(68).nextEvent!!.name)
    }
}
