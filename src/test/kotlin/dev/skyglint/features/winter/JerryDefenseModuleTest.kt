package dev.skyglint.features.winter

import dev.skyglint.core.*
import kotlin.test.*

class JerryDefenseModuleTest {
    private var now = 0L
    private val module = JerryDefenseModule { now }
    private fun context(mode: String? = "winter", location: String? = null) = module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun sidebar(vararg lines: String) = module.parse(NormalizedSignal.SidebarSnapshot(lines.toList()))
    private fun List<CoreEvent>.widgets() = filterIsInstance<CoreEvent.WidgetObserved>().map { it.widget }
    private fun List<CoreEvent>.metrics() = widgets().flatMap { it.presentation!!.metrics }

    @Test fun `event start countdown and idle winter never manufacture an active defense`() {
        assertTrue(context().isEmpty())
        for (lines in listOf(listOf("Event Start: §a2:38"), listOf("Event Start: 0:00"),
            listOf("Magma Cubes Left: §c3"), listOf("Jerry's Workshop"))) {
            assertTrue(sidebar(*lines.toTypedArray()).widgets().isEmpty())
        }
        val parsed = JerryDefensePatterns.sidebar(listOf("Event Start: §a2:38"))
        assertEquals(158_000L, parsed.eventStartsInMillis)
        assertFalse(parsed.active)
        now = 200_000
        context()
        assertTrue(module.parse(NormalizedSignal.Chat("The countdown is over!")).isEmpty())
    }

    @Test fun `verified wave and cubes become separate primary and grouped helper widgets`() {
        context()
        val events = sidebar("§cWave 5", "Magma Cubes Left: §c3")
        val primary = events.widgets().single { it.presentation!!.role == WidgetRole.PRIMARY }
        assertEquals(JerryDefenseModule.WIDGET_ID, primary.id)
        assertEquals("Jerry Defense", primary.title)
        assertEquals("Wave 5", primary.detail)
        assertNull(primary.progress)
        assertTrue(primary.replacedBossIds.isEmpty())
        val count = events.metrics().single()
        assertEquals("3", count.value)
        assertEquals(JerryDefenseModule.GROUP, count.group)
        assertTrue(events.none { it is CoreEvent.NoticeObserved || it is CoreEvent.RewardObserved })
    }

    @Test fun `next wave Soon or countdown is active evidence without an invented wave number`() {
        context()
        val soon = sidebar("Next Wave: §a§aSoon!")
        assertEquals("Next wave", soon.widgets().single { it.presentation!!.role == WidgetRole.PRIMARY }.detail)
        assertEquals("Soon!", soon.metrics().single().value)
        assertNull(soon.metrics().single().countdown)
        val timed = sidebar("Next Wave: §a0:30", "Magma Cubes Left: §c0")
        val timer = timed.metrics().single { it.id == "next_wave" }
        assertEquals(30_000L, timer.countdown!!.deadline)
        assertEquals(WidgetMetricFormat.REMAINING_TIME, timer.format)
        assertEquals("0", timed.metrics().single { it.id == "cubes_remaining" }.value)
        now = 1_000
        val updated = sidebar("Next Wave: 0:29")
        assertEquals(30_000L, updated.metrics().single().countdown!!.deadline)
    }

    @Test fun `negative cubes remain unknown and never become zero or a completed wave`() {
        context()
        for (negative in listOf("-4", "-1", "-1000000")) {
            val result = sidebar("Wave 5", "Magma Cubes Left: §c$negative")
            assertEquals(1, result.widgets().size)
            assertTrue(result.metrics().isEmpty())
            assertNull(JerryDefensePatterns.sidebar(listOf("Wave 5", "Magma Cubes Left: $negative")).cubesRemaining)
        }
    }

    @Test fun `full clean sidebar and prestart state retire the prior defense instead of keeping stale wave`() {
        context();sidebar("Wave 2", "Magma Cubes Left: 4")
        val cleared = sidebar("Event Start: 1:00")
        assertEquals(setOf(JerryDefenseModule.WIDGET_ID, JerryDefenseModule.COUNTERS_ID),
            cleared.filterIsInstance<CoreEvent.WidgetRemoved>().map { it.id }.toSet())
        assertTrue(context().isEmpty())
        assertTrue(sidebar("Jerry's Workshop").isEmpty())
    }

    @Test fun `unverified boss chat tab and location cannot activate or create gift point claims`() {
        context("hub", "Jerry's Workshop")
        assertTrue(sidebar("Wave 5").isEmpty())
        context()
        assertTrue(module.parse(NormalizedSignal.Chat("Wave 5")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Wave 5"))).isEmpty())
        assertTrue(module.parse(NormalizedSignal.BossSnapshot(listOf(BossBarState(java.util.UUID(0, 1), "Gift Pile", .5f)))).isEmpty())
        assertTrue(sidebar("Winter Wave 5", "Next Wave: later", "Magma Cubes Left: -4").isEmpty())
    }

    @Test fun `source freshness cannot be renewed by context and resets across sessions`() {
        context();sidebar("Wave 1")
        now = 4_999
        assertEquals(5_000L, context().widgets().single().expiresAt)
        now = 5_000
        assertEquals(2, context().filterIsInstance<CoreEvent.WidgetRemoved>().size)
        now = 6_000
        assertEquals(1, sidebar("Wave 2").widgets().size)
        module.onSessionEnded()
        assertTrue(sidebar("Wave 2").isEmpty())
        module.onSessionStarted("new-server")
        assertTrue(sidebar("Wave 2").isEmpty())
    }

    @Test fun `malformed conflicting and bounded numbers remain unknown`() {
        for (text in listOf("Next Wave: 0:60", "Next Wave: 1::05", "Next Wave: -1", "Wave 0", "Wave 1001", "Wave 2\n"))
            assertFalse(JerryDefensePatterns.sidebar(listOf(text)).active, text)
        assertNull(JerryDefensePatterns.sidebar(listOf("Wave 2", "Wave 3")).wave)
        assertNull(JerryDefensePatterns.sidebar(listOf("Wave 2", "Magma Cubes Left: 4", "Magma Cubes Left: -4")).cubesRemaining)
        assertTrue(JerryDefensePatterns.sidebar(listOf("Next Wave: 0:00")).active)
    }
}
