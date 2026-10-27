package dev.skyglint.features.session

import dev.skyglint.core.*
import kotlin.test.*

/** Synthetic source-shaped examples, not captured or live-verified Hypixel observations. */
class ServerClosingModuleTest {
    private var time = 0L
    private val module = ServerClosingModule { time }
    private fun sidebar(timer: String = "2:00", server: String = "mini123A") =
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Server closing: $timer $server")))
    private fun context() = module.parse(NormalizedSignal.ContextSnapshot("Village", "hub"))
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget
    private fun List<CoreEvent>.notices() = filterIsInstance<CoreEvent.NoticeObserved>().map { it.request }
    private fun ModuleWidget.metric() = presentation!!.metrics.single()
    private fun List<CoreEvent>.removed() = any { it == CoreEvent.WidgetRemoved(ServerClosingModule.WIDGET_ID) }

    @Test fun `initial intro owns a hidden additive timer anchor`() {
        val events = sidebar()
        val widget = events.widget()!!
        val metric = widget.metric()
        val notice = events.notices().single()
        assertEquals(WidgetRole.COUNTERS, widget.presentation!!.role)
        assertEquals(0, metric.priority)
        assertEquals(WidgetMetricFormat.REMAINING_TIME, metric.format)
        assertEquals(120_000L, metric.countdown!!.remaining(time))
        assertEquals(5_000L, widget.expiresAt)
        assertEquals(NoticeMetricTarget(widget.id, metric.id), notice.target)
        assertEquals(metric.countdown, notice.countdown)
        assertEquals("Server closing", notice.title)
        assertTrue(widget.replacedBossIds.isEmpty())
        assertTrue(metric.replacedBossIds.isEmpty())
    }

    @Test fun `sidebar lease expires at five seconds and context does not refresh it`() {
        sidebar()
        time = 4_999
        assertNotNull(context().widget())
        time = 5_000
        assertTrue(context().removed())
        assertNull(context().widget())
    }

    @Test fun `unchanged actual samples renew only the source lease never the deadline`() {
        val first = sidebar().widget()!!.metric().countdown!!
        time = 4_000
        val repeated = sidebar()
        assertTrue(repeated.notices().isEmpty())
        assertEquals(first, repeated.widget()!!.metric().countdown)
        assertEquals(9_000L, repeated.widget()!!.expiresAt)
        time = 8_999
        assertEquals(111_001L, context().widget()!!.metric().countdown!!.remaining(time))
        time = 9_000
        assertTrue(context().removed())
    }

    @Test fun `normal second updates including fifteen hundred millisecond jitter preserve one deadline`() {
        val initial = sidebar().widget()!!.metric().countdown!!
        listOf(1_000L to "1:59", 2_400L to "1:58", 3_000L to "1:58", 4_500L to "1:57").forEach { (now, sample) ->
            time = now
            val events = sidebar(sample)
            assertEquals(initial, events.widget()!!.metric().countdown)
            assertTrue(events.notices().isEmpty())
        }
    }

    @Test fun `material clock correction updates observed deadline without replaying intro`() {
        val initial = sidebar().widget()!!.metric()
        time = 1_000
        val corrected = sidebar("3:00")
        val metric = corrected.widget()!!.metric()
        assertEquals(initial.id, metric.id)
        assertEquals(181_000L, metric.countdown!!.deadline)
        assertTrue(corrected.notices().isEmpty())
        time = 2_000
        val shorter = sidebar("1:30")
        assertEquals(92_000L, shorter.widget()!!.metric().countdown!!.deadline)
        assertTrue(shorter.notices().isEmpty())
    }

    @Test fun `same server reappearance retains intro dedupe and clock while new server owns a new occurrence`() {
        val original = sidebar().widget()!!.metric()
        time = 1_000
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(emptyList())).removed())
        time = 2_000
        val returned = sidebar()
        assertEquals(original.id, returned.widget()!!.metric().id)
        assertEquals(original.countdown, returned.widget()!!.metric().countdown)
        assertTrue(returned.notices().isEmpty())
        val transferred = sidebar(server = "mini456B")
        assertNotEquals(original.id, transferred.widget()!!.metric().id)
        assertEquals(1, transferred.notices().size)
        assertEquals("Server mini456B", transferred.widget()!!.metric().detail)
    }

    @Test fun `last minute enables additive helper and one urgent reminder`() {
        sidebar()
        time = 59_000
        assertTrue(sidebar("1:01").notices().isEmpty())
        time = 60_000
        val urgent = context()
        assertTrue(urgent.widget()!!.metric().priority > 0)
        assertEquals(WidgetRole.COUNTERS, urgent.widget()!!.presentation!!.role)
        assertEquals(1, urgent.notices().size)
        time = 60_100
        assertTrue(context().notices().isEmpty())
        assertTrue(sidebar("1:00").notices().isEmpty())
    }

    @Test fun `initial last minute observation emits one intro and no duplicate urgent notice`() {
        val first = sidebar("0:30")
        assertTrue(first.widget()!!.metric().priority > 0)
        assertEquals(1, first.notices().size)
        time = 1_000
        assertTrue(sidebar("0:29").notices().isEmpty())
        assertTrue(context().notices().isEmpty())
    }

    @Test fun `explicit terminal zero immediately removes countdown and does not replay a notice`() {
        sidebar("0:02")
        time = 1_000
        val zero = sidebar("0:00")
        assertTrue(zero.removed())
        assertNull(zero.widget())
        assertTrue(zero.notices().isEmpty())
        time = 1_100
        assertTrue(sidebar("0:00").removed())
        assertTrue(context().notices().isEmpty())
    }

    @Test fun `locally reaching zero never fabricates restart relocation or another notice`() {
        sidebar("0:02")
        time = 2_000
        val expired = context()
        assertTrue(expired.removed())
        assertTrue(expired.notices().isEmpty())
        time = 2_001
        assertTrue(sidebar("0:02").removed())
        assertTrue(sidebar("0:02").notices().isEmpty())
    }

    @Test fun `initial zero is terminal and a later changed positive sample may establish a new occurrence`() {
        assertTrue(sidebar("0:00").removed())
        assertTrue(sidebar("0:00").notices().isEmpty())
        time = 1_000
        val established = sidebar("2:00")
        assertEquals(1, established.notices().size)
        assertEquals(121_000L, established.widget()!!.metric().countdown!!.deadline)
    }

    @Test fun `source absence invalidity and unrelated signals never invent or renew a warning`() {
        assertNull(context().widget())
        assertTrue(module.parse(NormalizedSignal.Chat("Server closing: 2:00 mini123A")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Server closing: 2:00 mini123A"))).isEmpty())
        sidebar()
        time = 4_000
        assertTrue(module.parse(NormalizedSignal.ActionBarPresence("Server closing: 2:00 mini123A")).isEmpty())
        time = 5_000
        assertTrue(context().removed())
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Server closing: unknown"))).removed())
        assertTrue(context().notices().isEmpty())
    }

    @Test fun `session lifecycle clears source clock and introduction state`() {
        val first = sidebar().widget()!!.metric().id
        module.onSessionEnded()
        assertTrue(context().removed())
        time = 1_000
        val restarted = sidebar()
        assertNotEquals(first, restarted.widget()!!.metric().id)
        assertEquals(1, restarted.notices().size)
        module.onSessionStarted("new-session")
        assertTrue(context().removed())
        assertEquals(1, sidebar().notices().size)
    }
}
