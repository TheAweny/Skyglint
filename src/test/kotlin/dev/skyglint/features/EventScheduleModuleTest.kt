package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class EventScheduleModuleTest {
    private val module = EventScheduleModule()
    private fun tab(vararg lines: String) = module.parse(NormalizedSignal.TabSnapshot(lines.toList()))

    @Test fun `source confirmed event tab countdown produces an upcoming event without claiming started`() {
        val observed = assertIs<CoreEvent.EventObserved>(tab("§e§lEvent: §bMining Fiesta", " Starts In: §e7h").single()).observation
        assertEquals("Mining Fiesta", observed.name)
        assertEquals(25_200_000L, observed.startsInMillis)
        assertFalse(observed.active)
        assertFalse(assertIs<CoreEvent.EventObserved>(tab("Event: Spooky Festival", " Starts In: 0s").single()).observation.active)
    }

    @Test fun `active state requires explicit server ends line and does not guess remaining start time`() {
        val observed = assertIs<CoreEvent.EventObserved>(tab("Event: Hoppity's Hunt", " Ends In: 27h").single()).observation
        assertTrue(observed.active)
        assertNull(observed.startsInMillis)
        assertEquals(97_200_000L, observed.endsInMillis)
    }

    @Test fun `unrelated timer and unknown or malformed event forms do not publish state`() {
        for (lines in listOf(
            listOf("Event: Spooky Festival", "Pet:", "Starts In: 7h"),
            listOf("Event: Not announced", "Starts In: 7h"),
            listOf("Event: Carnival", "Starts In: -5s"),
            listOf("Event: Carnival", "Starts In: 99d"),
            listOf("Event: Carnival", "Starts In: tomorrow"),
            listOf("Event: Carnival", "Starts In: 1m 2h"),
            listOf("[MVP+] Player: Event: Spooky Festival", "Starts In: 7h"),
        )) assertTrue(module.parse(NormalizedSignal.TabSnapshot(lines)).isEmpty(), lines.toString())
        assertTrue(module.parse(NormalizedSignal.Chat("Event: Spooky Festival Starts In: 7h")).isEmpty())
    }

    @Test fun `main calendar preserves all complete events in nearest first order and ignores unrelated inventory`() {
        val items = listOf(
            MenuItemSnapshot(1, "Traveling Zoo", listOf("Starts in: 1h 58m 40s", "Event lasts for 1h!")),
            MenuItemSnapshot(2, "Dark Auction", listOf("Starts in: 40s", "Event lasts for 20m!")),
            MenuItemSnapshot(3, "Unconfirmed Item", listOf("Starts in: 1s")),
        )
        val observed = module.parse(NormalizedSignal.MenuSnapshot("Calendar and Events", items))
            .map { assertIs<CoreEvent.EventObserved>(it).observation }
        assertEquals(listOf("Dark Auction", "Traveling Zoo"), observed.map { it.name })
        assertEquals(40_000L, observed.first().startsInMillis)
        assertEquals(1_200_000L, observed.first().durationMillis)
        assertEquals(3_600_000L, observed.last().durationMillis)
        assertEquals(2, observed.map { it.source }.toSet().size)
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("Chest", items)).isEmpty())
    }

    @Test fun `several explicit tab events keep separate sources and malformed neighbours cannot borrow clocks`() {
        val observed = tab("Event: Spooky Festival", "Ends In: 50m 54s", "Event: Mining Fiesta", "Starts In: 4m",
            "Event: Carnival", "Pet:", "Ends In: 2m").map { assertIs<CoreEvent.EventObserved>(it).observation }
        assertEquals(listOf("Spooky Festival", "Mining Fiesta"), observed.map { it.name })
        assertTrue(observed.first().active)
        assertEquals(3_054_000L, observed.first().endsInMillis)
        assertEquals(240_000L, observed.last().startsInMillis)
        assertEquals(2, observed.map { it.source }.toSet().size)
    }

    @Test fun `duplicate calendar cards coalesce by event name to the nearest observed occurrence`() {
        val items = listOf(
            MenuItemSnapshot(1, "Traveling Zoo", listOf("Starts in: 2h", "Event lasts for 1h!")),
            MenuItemSnapshot(2, "Traveling Zoo", listOf("Starts in: 1h", "Event lasts for 1h!")),
        )
        val observed = assertIs<CoreEvent.EventObserved>(module.parse(NormalizedSignal.MenuSnapshot("Calendar and Events", items)).single()).observation
        assertEquals(3_600_000L, observed.startsInMillis)
    }

    @Test fun `named server sidebar event clocks preserve all observed active events without fixed totals`() {
        val observations = module.parse(NormalizedSignal.SidebarSnapshot(listOf(
            "§6Spooky Festival§f 50:54", "§dNew Year Event!§f 17:53", "§aTraveling Zoo§f 0:35",
            "§d5th Anniversary§f 167:59:54", "§bCentury Raffle§f 124:00:00", "§eCarnival§f 85:33:57",
        ))).map { assertIs<CoreEvent.EventObserved>(it).observation }
        assertEquals(listOf("Spooky Festival", "New Year Celebration", "Traveling Zoo", "5th Anniversary", "Century Raffle", "Carnival"),
            observations.map { it.name })
        assertTrue(observations.all { it.active && it.startsInMillis == null && it.durationMillis == null })
        assertEquals(3_054_000L, observations.first().endsInMillis)
        assertEquals(604_794_000L, observations[3].endsInMillis)
        assertEquals(6, observations.map { it.source }.toSet().size)
    }

    @Test fun `sidebar area names generic clocks malformed and impossible durations are not events`() {
        for (line in listOf("Carnival", "Time Left: 11", "Starting in: 0:02", "Spooky Festival 1:60", "Spooky Festival -1:30",
            "Carnival 169:00:00", "Century Raffle Soon!", "Future Event 1:30", "Pet: Spooky Festival 50:54")) {
            assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf(line))).isEmpty(), line)
        }
    }

    @Test fun `duration parsing accepts confirmed compound units and rejects invalid orders or fragments`() {
        assertEquals(97_120_000L, ServerEventDuration.parseMillis("1d 2h 58m 40s"))
        assertEquals(3_120_000L, ServerEventDuration.parseMillis("52min"))
        for (text in listOf("1h garbage", "1h1h", "2s1m", "1m 2min", "Infinityh", "8d", "", "1:30"))
            assertNull(ServerEventDuration.parseMillis(text), text)
    }
}
