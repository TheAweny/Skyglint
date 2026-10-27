package dev.skyglint.features.mining

import dev.skyglint.core.*
import kotlin.test.*

class DrillFuelModuleTest {
    private var now = 0L
    private val module = DrillFuelModule { now }
    private fun drill(fuel: String = "5,395/10k", name: String = "Titanium Drill DR-X655") =
        MenuItemSnapshot(0, name, listOf("Fuel: $fuel", "MYTHIC DRILL"))
    private fun held(item: MenuItemSnapshot?) = module.parse(NormalizedSignal.HeldItemSnapshot(item))
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().single().widget

    @Test fun `sufficient fuel stays absent and unknown never becomes empty`() {
        for (item in listOf(drill(), drill("10,000/10k"), drill("5,000/10k"), null,
            MenuItemSnapshot(0, "Composter", listOf("Fuel: 190.4k")), drill("unknown/10k"))) {
            assertEquals(listOf(CoreEvent.WidgetRemoved(DrillFuelModule.WIDGET_ID)), held(item))
        }
    }
    @Test fun `all low bands contribute exactly one counter and never a notice or primary`() {
        for (ratio in listOf("4,999/10k", "2,499/10k", "999/10k", "499/10k", "0/10k")) {
            val events = held(drill(ratio))
            assertEquals(1, events.size)
            assertTrue(events.none { it is CoreEvent.NoticeObserved })
            val widget = events.widget()
            assertEquals(WidgetRole.COUNTERS, widget.presentation!!.role)
            val metric = widget.presentation!!.metrics.single()
            assertEquals("fuel", metric.id)
            assertEquals("minecraft:coal", metric.icon)
            assertNotNull(widget.progress)
            assertEquals(3_000L, widget.expiresAt)
            assertTrue(metric.detail!!.contains("Titanium Drill"))
        }
        assertEquals("Empty", held(drill("0/10k")).widget().presentation!!.metrics.single().value)
        assertEquals("Empty drill", held(drill("0/10k")).widget().title)
        assertEquals("3.9%", held(drill("395/10k")).widget().presentation!!.metrics.single().value)
    }
    @Test fun `repeated readings renew one helper without queue spam or simulated burn`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        core.enterSkyBlock("fixture", now)
        repeat(20) {
            now = it * 1_000L
            core.ingest(NormalizedSignal.HeldItemSnapshot(drill("395/10k")), now)
            val snapshot = core.snapshot(now)
            assertNull(snapshot.activeNotice)
            assertNull(snapshot.progression)
            assertEquals(1, snapshot.widgets.size)
            assertEquals(395.0, snapshot.widgets.single().progress!!.current)
        }
        assertTrue(core.snapshot(now + 3_000).widgets.isEmpty())
    }
    @Test fun `refill switch unknown and session remove every low fuel band immediately`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        core.enterSkyBlock("fixture", now)
        for (replacement in listOf(null, drill("5,000/10k"), drill("20,000/25k", "Other Drill"),
            MenuItemSnapshot(0, "Stone", emptyList()), drill("unknown/10k"))) {
            core.ingest(NormalizedSignal.HeldItemSnapshot(drill("395/10k")), ++now)
            assertEquals(1, core.snapshot(now).widgets.size)
            core.ingest(NormalizedSignal.HeldItemSnapshot(replacement), ++now)
            assertTrue(core.snapshot(now).widgets.isEmpty())
            assertNull(core.snapshot(now).activeNotice)
        }
        core.ingest(NormalizedSignal.HeldItemSnapshot(drill("0/10k")), ++now)
        core.leaveSkyBlock()
        core.enterSkyBlock("new", ++now)
        assertTrue(core.snapshot(now).widgets.isEmpty())
    }
    @Test fun `unsupported fractional negative grouped duplicate overflowing and foreign ratios stay unknown`() {
        for (ratio in listOf("-1/10k", "1.5/10k", "1/1.5k", "1/10M", "99,99/10k", "1/1000000001", "1/999999999k", "1/10k extra"))
            assertNull(MiningToolPatterns.fuel(drill(ratio)), ratio)
        val duplicate = MenuItemSnapshot(0, "Drill", listOf("Fuel: 1/10k", "Fuel: 2/10k", "EPIC DRILL"))
        assertNull(MiningToolPatterns.fuel(duplicate))
        assertNull(MiningToolPatterns.fuel(MenuItemSnapshot(0, "Drill", listOf("Fuel: 1/10k", "EPIC PICKAXE"))))
        assertEquals(10_000L, MiningToolPatterns.fuel(drill("10,000/10,000"))!!.capacity)
    }

    @Test fun `trusted metadata establishes type but never invents capacity or reconciles conflicts`() {
        val exact = MenuItemSnapshot(0, "Custom Tool", listOf("Fuel: 5,395/10k"), skyBlockId = "TITANIUM_DRILL_4", drillFuel = 5_395)
        assertEquals(5_395L, MiningToolPatterns.fuel(exact)!!.current)
        assertNull(MiningToolPatterns.fuel(MenuItemSnapshot(0, "Custom Tool", emptyList(), skyBlockId = "TITANIUM_DRILL_4", drillFuel = 5_395)))
        assertNull(MiningToolPatterns.fuel(MenuItemSnapshot(0, "Custom Tool", listOf("Fuel: 5,395/10k"), skyBlockId = "TITANIUM_DRILL_4", drillFuel = 5_394)))
        assertNull(MiningToolPatterns.fuel(MenuItemSnapshot(0, "Custom Tool", listOf("Fuel: 5,395/10k"), skyBlockId = "FUEL_TANK", drillFuel = 5_395)))
    }
}
