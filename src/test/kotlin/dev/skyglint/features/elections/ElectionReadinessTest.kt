package dev.skyglint.features.elections

import dev.skyglint.core.*
import kotlin.test.*

class ElectionReadinessTest {
    private var now = 0L
    private val module = ElectionModule(clock = { now })
    private val candidates = listOf(ElectionVoteCandidate("Aatrox", 60), ElectionVoteCandidate("Foxy", 25),
        ElectionVoteCandidate("Marina", 10), ElectionVoteCandidate("Diana", 4), ElectionVoteCandidate("Paul", 1))
    private fun context(area: String = "Community Center") = module.parse(NormalizedSignal.ContextSnapshot(area, "hub"))
    private fun api(values: List<ElectionVoteCandidate> = candidates, age: Long = 0) =
        module.parse(NormalizedSignal.ElectionSnapshot(522, values, age))
    private fun tab() = module.parse(NormalizedSignal.TabSnapshot(ElectionLiveFixtures.partialTab))
    private fun advance(next: Long) {
        while (now + 1_000 < next) { now += 1_000; context() }
        now = next
    }
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().single().widget
    private fun List<CoreEvent>.loading() {
        assertEquals(WidgetRole.COUNTERS, widget().presentation!!.role)
        assertTrue(widget().presentation!!.metrics.isEmpty())
        assertNull(widget().detail)
    }

    @Test fun `main network waits for complete pool and reveals once after five hundred stable milliseconds`() {
        context().loading()
        now = 100; tab().loading()
        now = 200; api().loading()
        now = 699; context().loading()
        now = 700
        val full = context().widget()
        assertEquals(WidgetRole.INLINE, full.presentation!!.role)
        assertEquals(5, full.presentation!!.metrics.size)
        now = 800
        assertEquals(5, tab().widget().presentation!!.metrics.size)
    }

    @Test fun `vote updates and cache replay do not restart structural settlement or visible pool`() {
        context(); api()
        now = 300
        val changed = candidates.map { it.copy(votes = it.votes + 1) }.reversed()
        api(changed).loading()
        now = 500
        assertEquals(5, context().widget().presentation!!.metrics.size)
        now = 600
        assertEquals(5, api(changed, age = 300).widget().presentation!!.metrics.size)
    }

    @Test fun `bounded main source wait allows one settled whole-tab fallback after timeout`() {
        context(); tab()
        advance(11_999); context().loading()
        now = 12_000; context().loading()
        now = 12_499; context().loading()
        now = 12_500
        val fallback = context().widget()
        assertEquals(3, fallback.presentation!!.metrics.size)
        assertEquals("Aatrox 61%", fallback.presentation!!.metrics.first().value)
        now = 13_000
        assertEquals(3, tab().widget().presentation!!.metrics.size)
        assertEquals(3, api().widget().presentation!!.metrics.size) // Keep the committed source during upgrade settlement.
        now = 13_499
        assertEquals(3, context().widget().presentation!!.metrics.size)
        now = 13_500
        assertEquals(5, context().widget().presentation!!.metrics.size)
    }

    @Test fun `validated public pool is treated atomically without assuming an invented fixed candidate count`() {
        context(); api(candidates.take(3)).loading()
        now = 500
        assertEquals(3, context().widget().presentation!!.metrics.size)
        now = 700
        assertEquals(3, api().widget().presentation!!.metrics.size)
        now = 1_199
        assertEquals(3, context().widget().presentation!!.metrics.size)
        now = 1_200
        assertEquals(5, context().widget().presentation!!.metrics.size)
    }

    @Test fun `switching public source availability clears every cached network-specific election value`() {
        context(); module.parse(NormalizedSignal.SidebarSnapshot(listOf("Year 523 Votes"))); tab(); api()
        now = 500
        assertEquals(5, context().widget().presentation!!.metrics.size)
        module.parse(NormalizedSignal.ElectionClosed())
        now = 600
        module.parse(NormalizedSignal.ElectionSourceAvailability(false)).loading()
        now = 1_100; context().loading()
        tab().loading()
        now = 1_600
        assertEquals(3, context().widget().presentation!!.metrics.size)
        module.parse(NormalizedSignal.ElectionSourceAvailability(true)).loading()
        now = 2_100; context().loading()
        // A different main-network election year is accepted: the Alpha/sidebar year was cleared.
        module.parse(NormalizedSignal.ElectionSnapshot(523, candidates)).loading()
        now = 2_600
        assertEquals(5, context().widget().presentation!!.metrics.size)
    }

    @Test fun `alpha tab settles without public source wait and public snapshots cannot leak in`() {
        module.parse(NormalizedSignal.ElectionSourceAvailability(false))
        context(); tab().loading()
        now = 500
        assertEquals(3, context().widget().presentation!!.metrics.size)
        assertEquals(3, api().widget().presentation!!.metrics.size)
        assertEquals(3, module.parse(NormalizedSignal.ElectionClosed()).widget().presentation!!.metrics.size)
    }

    @Test fun `leaving and reentering settles cached pool again without refreshing its original age`() {
        context(); api(age = 359_000)
        now = 500
        assertEquals(1_000L, context().widget().expiresAt)
        now = 600; context("Village")
        now = 650; context().loading()
        now = 1_000
        context().loading() // The cached source expires before another full settlement can finish.
        now = 1_150; context().loading()
        module.onSessionEnded(); module.onSessionStarted("other")
        context().loading()
    }

    @Test fun `confirmed closed bypasses candidate gate and stale tab never populates a strip`() {
        context(); tab(); api()
        now = 250
        val closed = module.parse(NormalizedSignal.ElectionClosed()).widget()
        assertEquals("Closed", closed.detail)
        assertTrue(closed.presentation!!.metrics.isEmpty())
        now = 1_000
        assertEquals("Closed", tab().widget().detail)
        assertEquals("Closed", context().widget().detail)
    }
}
