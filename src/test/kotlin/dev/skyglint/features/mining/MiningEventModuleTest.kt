package dev.skyglint.features.mining

import dev.skyglint.core.*
import java.util.UUID
import kotlin.test.*

class MiningEventModuleTest {
    private var time = 0L
    private val module = MiningEventModule { time }
    private val powderId = UUID(0, 1)
    private val unknownId = UUID(0, 2)
    private fun context(mode: String? = "mining_3", location: String? = null) = module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun boss(id: UUID = powderId, title: String = "PASSIVE EVENT 2X POWDER RUNNING FOR 02:14") = BossBarState(id, title, .5f)
    private fun bosses(vararg bars: BossBarState) = module.parse(NormalizedSignal.BossSnapshot(bars.toList()))
    private fun tab(vararg lines: String) = module.parse(NormalizedSignal.TabSnapshot(lines.toList()))
    private fun List<CoreEvent>.widget(): ModuleWidget? = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget
    private fun ModuleWidget.value(id: String = "event:double_powder") = presentation!!.metrics.single { it.id == id }.value

    @Test fun `boss counter claims only exact represented UUID and full snapshot remove releases immediately`() {
        context()
        val observed = bosses(boss(), boss(unknownId, "Unrecognized future mining event")).widget()!!
        assertEquals(setOf(powderId), observed.replacedBossIds)
        assertEquals("×2 · 2:14", observed.value())
        assertEquals(WidgetRole.COUNTERS, observed.presentation?.role)
        assertTrue(bosses(boss(unknownId, "Unrecognized future mining event")).all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `identical boss replay refreshes source lease but never pushes its deadline forward`() {
        context()
        bosses(boss())
        time = 3_000
        assertEquals("×2 · 2:11", bosses(boss()).widget()!!.value())
        time = 6_000
        context()
        assertEquals("×2 · 2:08", bosses(boss()).widget()!!.value())
        time = 134_000
        context()
        assertTrue(bosses(boss()).all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `changed actual timer anchors new observed remaining time without delta accounting`() {
        context(); bosses(boss())
        time = 2_000
        assertEquals("×2 · 2:10", bosses(boss(title = "PASSIVE EVENT 2X POWDER RUNNING FOR 02:10")).widget()!!.value())
    }
    @Test fun `boss source wins same event tab source but independent second event remains represented`() {
        context(); bosses(boss())
        time = 1_000
        val same = tab("Mining Event: 2x Powder", "Ends in: 15m").widget()!!
        assertEquals("×2 · 2:13", same.value())
        val second = tab("Mining Event: Better Together", "Ends in: 4m").widget()!!
        assertEquals(2, second.presentation!!.metrics.size)
        assertEquals(setOf(powderId), second.replacedBossIds)
        assertEquals("Together · 4:00", second.value("event:better_together"))
        assertEquals(1, tab("Mining Event: Not announced").widget()!!.presentation!!.metrics.size)
    }
    @Test fun `tab only countdown is bounded stale and never claims a UUID`() {
        context()
        val first = tab("Mining Event: 2x Powder", "Ends in: 5m 30s").widget()!!
        assertTrue(first.replacedBossIds.isEmpty())
        time = 1_000
        assertEquals("×2 · 5:29", tab("Mining Event: 2x Powder", "Ends in: 5m 30s").widget()!!.value())
        time = 6_000
        assertTrue(context().all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `mode gate excludes mining two and hub while known sidebar location fallback works`() {
        context("mining_2")
        assertNull(bosses(boss()).widget())
        context("hub", "Dwarven Mines")
        assertNull(bosses(boss()).widget())
        context(null, "Dwarven Mines")
        assertNotNull(bosses(boss()).widget())
        assertTrue(context("hub").all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `end chat prevents an unchanged stale boss replay from resurrecting the event`() {
        context(); bosses(boss())
        time = 1_000
        assertTrue(module.parse(NormalizedSignal.Chat("2X POWDER ENDED!")).all { it is CoreEvent.WidgetRemoved })
        time = 1_001
        assertNull(bosses(boss()).widget())
        assertNotNull(bosses(boss(title = "PASSIVE EVENT 2X POWDER RUNNING FOR 15:00")).widget())
        module.onSessionEnded()
        assertNull(bosses(boss()).widget())
    }
    @Test fun `end chat also suppresses an identical stale tab replay until the server changes it`() {
        context()
        tab("Mining Event: 2x Powder", "Ends in: 5m 30s")
        module.parse(NormalizedSignal.Chat("2X POWDER ENDED!"))
        assertNull(tab("Mining Event: 2x Powder", "Ends in: 5m 30s").widget())
        assertNotNull(tab("Mining Event: 2x Powder", "Ends in: 15m").widget())
    }
    @Test fun `two known UUID bars of the same type share one metric while both IDs are preserved as claims`() {
        context()
        val widget = bosses(boss(), boss(unknownId, "PASSIVE EVENT 2X POWDER RUNNING FOR 01:14")).widget()!!
        assertEquals(1, widget.presentation!!.metrics.size)
        assertEquals(setOf(powderId, unknownId), widget.replacedBossIds)
        assertEquals("×2 · 1:14", widget.value())
    }
    @Test fun `normal second updates keep one monotonic timer interval despite packet arrival jitter`() {
        context()
        val first = bosses(boss()).widget()!!.presentation!!.metrics.single().countdown!!
        for (second in 1..20) {
            time = second * 1_000L + if (second % 2 == 0) 220 else 50
            context()
            val left = 134 - second
            val title = "PASSIVE EVENT 2X POWDER RUNNING FOR ${left / 60}:${(left % 60).toString().padStart(2, '0')}"
            val metric = bosses(boss(title = title)).widget()!!.presentation!!.metrics.single()
            assertEquals(first.deadline, metric.countdown!!.deadline)
            assertEquals(first.intervalMillis, metric.countdown!!.intervalMillis)
            assertEquals(setOf(powderId), metric.replacedBossIds)
        }
    }
    @Test fun `island mode switch clears old event sources even without a new boss packet`() {
        context(); bosses(boss())
        time = 1_000
        assertNull(context("crystal_hollows").widget())
        assertNotNull(bosses(boss()).widget())
    }
}
