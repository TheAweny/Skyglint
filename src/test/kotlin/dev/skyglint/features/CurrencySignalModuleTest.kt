package dev.skyglint.features

import dev.skyglint.core.*
import dev.skyglint.features.garden.GardenStatisticsModule
import kotlin.test.*

class CurrencySignalModuleTest {
    private var now = 0L
    private val module = CurrencySignalModule { now }
    private fun context(mode: String? = "garden") = module.parse(NormalizedSignal.ContextSnapshot("The Garden", mode))
    private fun sidebar(vararg lines: String) = module.parse(NormalizedSignal.SidebarSnapshot(lines.toList()))
    private fun List<CoreEvent>.view() = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget

    @Test fun `real sidebar forms produce absolute grouped balances without gain events`() {
        context()
        val events = sidebar("Purse: §6423,085,776 §e(+5)", "Bits: §b140,965", "Gems: §a350", "Copper: §c3,416", "Sowdust: §230,210,307")
        val widget = events.view()!!
        assertEquals(CurrencySignalModule.WIDGET_ID, widget.id)
        assertEquals(WidgetRole.COUNTERS, widget.presentation!!.role)
        val metrics = widget.presentation!!.metrics
        assertEquals(listOf("coins", "bits", "gems", "copper", "sowdust"), metrics.map { it.id })
        assertEquals(listOf("423085776", "140965", "350", "3416", "30210307"), metrics.map { it.value })
        assertTrue(metrics.all { it.group == "currency" })
        assertTrue(events.all { it is CoreEvent.WidgetObserved })
        assertNull(widget.progress)
    }
    @Test fun `compact sowdust stays literal and purse earned suffix is never added`() {
        context()
        val metrics = sidebar("Purse: 1,234.50 (+5)", "Sowdust: §26.5k §7(+912)").view()!!.presentation!!.metrics
        assertEquals("1234.5", metrics.first { it.id == "coins" }.value)
        assertEquals("6.5k", metrics.first { it.id == "sowdust" }.value)
        assertEquals("2.7B", sidebar("Sowdust: §22.7B §7(+1.2M)").view()!!.presentation!!.metrics.single().value)
        assertNull(sidebar("Purse: 1.2M", "Bank: 249M / 100M").view())
    }
    @Test fun `zero remains observed while absent invalid or duplicate currency is unknown`() {
        context()
        val zeros = sidebar("Bits: 0", "Gems: 0", "Copper: 0").view()!!.presentation!!.metrics
        assertEquals(3, zeros.size)
        assertTrue(zeros.all { it.value == "0" })
        assertNull(sidebar("Bits: 10", "Bits: 10").view())
        assertNull(sidebar("Purse: 1", "Piggy: 2").view())
        assertEquals(listOf("gems"), sidebar("Bits: 10", "Bits: 20", "Gems: 5").view()!!.presentation!!.metrics.map { it.id })
        assertNull(sidebar().view())
    }
    @Test fun `malformed amounts and unrelated currencies do not create balances`() {
        context()
        for (amount in listOf("-1", "99,99", "1,00,000", "1.5", "NaN", "1e10", "1000000000000001", "12 extra"))
            assertNull(sidebar("Bits: $amount").view(), amount)
        assertNull(sidebar("Purse: -1", "Gems: 1.5", "Copper: 1/10", "Bits Available: 1,234", "Purse: 1 (+oops)").view())
        assertTrue(module.parse(NormalizedSignal.Chat("Bits: 123")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.ActionBar("Purse: 100")).isEmpty())
    }
    @Test fun `context renewal does not extend source age and mode or lifecycle clears old currency`() {
        assertNull(sidebar("Purse: 10").view())
        context(); sidebar("Purse: 10")
        now = 3_999
        assertEquals(4_000L, context().view()!!.expiresAt)
        now = 4_000
        assertNull(context().view())
        sidebar("Bits: 4")
        assertNull(context("hub").view())
        sidebar("Gems: 3")
        module.onSessionEnded(); module.onSessionStarted("new")
        assertNull(sidebar("Purse: 2").view())
        context(); assertNull(context().view())
    }
    @Test fun `garden copper joins currency group while fortune uses statistics and level remains separate`() {
        val garden = GardenStatisticsModule { now }
        garden.parse(NormalizedSignal.ContextSnapshot("The Garden", "garden"))
        val metrics = garden.parse(NormalizedSignal.TabSnapshot(listOf("Farming Fortune: \uE0511234", "Garden Level: 17", "Copper: 12,345")))
            .filterIsInstance<CoreEvent.WidgetObserved>().single().widget.presentation!!.metrics
        assertEquals("currency", metrics.single { it.id == "copper" }.group)
        assertEquals("garden:stats", metrics.single { it.id == "farming_fortune" }.group)
        assertNull(metrics.single { it.id == "garden_level" }.group)
        context()
        val sidebarCopper = sidebar("Copper: 123").view()!!.presentation!!.metrics.single()
        assertTrue(sidebarCopper.priority > metrics.single { it.id == "copper" }.priority)
    }
}
