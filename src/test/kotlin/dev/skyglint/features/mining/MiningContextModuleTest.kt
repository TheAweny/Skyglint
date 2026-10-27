package dev.skyglint.features.mining

import dev.skyglint.core.*
import kotlin.test.*

class MiningContextModuleTest {
    private var time = 0L
    private val module = MiningContextModule { time }
    private fun context(location: String? = "Dwarven Mines", mode: String? = "mining_3") =
        module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget
    private fun tab(mithril: String = "37.5%", titanium: String = "DONE") = module.parse(NormalizedSignal.TabSnapshot(listOf(
        "Commissions:", "Mithril Miner: $mithril", "Titanium Miner: $titanium", "", "Powders:",
        "Mithril: 35,448", "Gemstone: 36,758", "Glacite: 29,537")))
    private fun List<CoreEvent>.metrics() = widget()!!.presentation!!.metrics

    @Test fun `official mining modes retain only an empty context marker with no idle location statistic`() {
        val widget = context("Jungle", "crystal_hollows").widget()!!
        assertEquals("Mining", widget.title)
        assertEquals(WidgetRole.COUNTERS, widget.presentation!!.role)
        assertNull(widget.detail)
        assertNull(widget.progress)
        assertTrue(widget.presentation!!.metrics.isEmpty())
        for (mode in listOf(null, "hub", "garden", "dungeon_hub", "glacite_tunnels"))
            assertTrue(context("Dwarven Mines", mode).all { it is CoreEvent.WidgetRemoved })
        assertEquals("Mining", context(null, "crystal_hollows").widget()!!.title)
        assertTrue(module.parse(NormalizedSignal.Chat("CHEST LOCKPICKED")).isEmpty())
    }

    @Test fun `initial commissions and powder are observations and never create activity or permanent counters`() {
        context("Glacite Tunnels")
        assertTrue(tab().metrics().isEmpty())
        repeat(3) { time += 1_000; context("Glacite Tunnels"); assertTrue(tab().metrics().isEmpty()) }
        assertTrue(tab("0%", "0%").metrics().isEmpty())
        assertTrue(tab("0%", "0%").metrics().isEmpty())
    }

    @Test fun `actual commission increase shows only one helper and retains all observed values in its tooltip`() {
        context("Glacite Tunnels"); tab()
        time = 1_000
        val metrics = tab("38%").metrics()
        assertEquals(listOf("commission"), metrics.map { it.id })
        assertEquals("Mithril Miner", metrics.single().label)
        assertEquals("38%", metrics.single().value)
        assertTrue(metrics.single().detail!!.contains("Titanium Miner: DONE"))
        assertTrue(metrics.single().detail!!.contains("Glacite Powder: 29537"))
        assertTrue(metrics.none { it.id.startsWith("powder:") })
    }

    @Test fun `unchanged snapshots renew source freshness but cannot extend recent activity past twelve seconds`() {
        context(); tab()
        time = 1_000; tab("38%")
        for (second in 2..12) {
            time = second * 1_000L; context()
            assertEquals("commission", tab("38%").metrics().single().id)
        }
        time = 13_000; context()
        assertTrue(tab("38%").metrics().isEmpty())
        time = 14_000; context()
        assertEquals("39%", tab("39%").metrics().single().value)
    }

    @Test fun `done transition is activity but disappearance reset and stale baseline cannot manufacture it`() {
        context(); tab("99%", "0%")
        time = 1_000
        assertEquals("DONE", tab("DONE", "0%").metrics().single().value)
        assertTrue(tab("0%", "0%").metrics().isEmpty())
        time = 6_000; context()
        assertTrue(tab("60%", "DONE").metrics().isEmpty())
        assertEquals("61%", tab("61%", "DONE").metrics().single().value)
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(emptyList())).metrics().isEmpty())
        assertTrue(tab("99%", "DONE").metrics().isEmpty())
    }

    @Test fun `meaningful environment remains separate and healthy zero or immunity does not clutter HUD`() {
        context("Glacite Tunnels"); tab()
        val cold = module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -3❄"))).metrics().single()
        assertEquals("cold", cold.id)
        assertEquals("-3❄", cold.value)
        assertTrue(cold.detail!!.contains("Glacite Powder: 29537"))
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: 0❄"))).metrics().isEmpty())
        context("Magma Fields", "crystal_hollows"); tab()
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Heat: IMMUNE"))).metrics().isEmpty())
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Heat: 0♨"))).metrics().isEmpty())
        val heat = module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -3❄", "Heat: 74♨"))).metrics().single()
        assertEquals("heat", heat.id)
        assertEquals("74♨", heat.value)
    }

    @Test fun `context refresh never renews old hazard and source absence clears without invented zero`() {
        context("Glacite Tunnels")
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -3❄")))
        time = 4_999
        assertEquals(5_000L, context("Glacite Tunnels").widget()!!.expiresAt)
        time = 5_000
        assertTrue(context("Glacite Tunnels").metrics().isEmpty())
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -8❄")))
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(emptyList())).metrics().isEmpty())
    }

    @Test fun `subarea island exit and session lifecycle clear hazards and activity`() {
        context("Glacite Tunnels"); tab(); tab("38%")
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -3❄")))
        val moved = context("Dwarven Base Camp").metrics()
        assertEquals(listOf("commission"), moved.map { it.id })
        assertTrue(context("Village", "hub").all { it is CoreEvent.WidgetRemoved })
        assertTrue(context("Glacite Tunnels").metrics().isEmpty())
        tab(); tab("38%"); module.onSessionEnded(); module.onSessionStarted("new")
        assertTrue(context().metrics().isEmpty())
        assertTrue(tab("50%").metrics().isEmpty())
        time = 5_000
        assertTrue(tab().all { it is CoreEvent.WidgetRemoved })
    }

    @Test fun `official mineshaft keeps fresh cold without importing old commissions or powder widgets`() {
        context("Glacite Tunnels"); tab(); tab("38%")
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -3❄")))
        val entered = context(null, "mineshaft").widget()!!
        assertEquals("Mining", entered.title)
        assertTrue(entered.presentation!!.metrics.isEmpty())
        val cold = module.parse(NormalizedSignal.SidebarSnapshot(listOf("Cold: -12❄", "Heat: 90♨"))).metrics()
        assertEquals(listOf("cold"), cold.map { it.id })
        val powder = module.parse(NormalizedSignal.TabSnapshot(listOf("Powders:", "Glacite: 18,000"))).metrics()
        assertEquals(listOf("cold"), powder.map { it.id })
        assertTrue(powder.single().detail!!.contains("Glacite Powder: 18000"))
        assertTrue(context("Glacite Tunnels", "mining_3").metrics().isEmpty())
    }
}
