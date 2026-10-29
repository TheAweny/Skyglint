package dev.skyglint.core

import java.util.UUID
import kotlin.test.*

class WidgetSnapshotOwnershipTest {
    @Test fun `contributor collections cannot change accepted widget metrics or boss claims after ingestion`() {
        val controller = CoreController()
        controller.enterSkyBlock("fixture", 0)
        val metricBoss = UUID(0, 1)
        val widgetBoss = UUID(0, 2)
        val metricClaims = mutableSetOf(metricBoss)
        val widgetClaims = mutableSetOf(widgetBoss)
        val metrics = mutableListOf(WidgetMetric("timer", "Event", "X2", replacedBossIds = metricClaims))
        val widget = ModuleWidget("contributor.event", "Observed event", presentation =
            WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics), replacedBossIds = widgetClaims)
        val signal = object : SignalModule {
            override val id = "contributor.observer"
            override fun parse(signal: NormalizedSignal) = listOf(CoreEvent.WidgetObserved(widget))
        }
        controller.modules.register(signal)
        val accepted = assertIs<CoreEvent.WidgetObserved>(controller.onChat("fixture", 1).single())
        metrics.clear()
        metricClaims.clear()
        widgetClaims.clear()

        val snapshot = controller.snapshot(2)
        val stored = snapshot.widgets.single()
        assertEquals("X2", stored.presentation!!.metrics.single().value)
        assertEquals(setOf(metricBoss), stored.presentation!!.metrics.single().replacedBossIds)
        assertEquals(setOf(widgetBoss), stored.replacedBossIds)
        assertEquals(stored, accepted.widget)
        assertFailsWith<UnsupportedOperationException> { (stored.presentation!!.metrics as MutableList<WidgetMetric>).clear() }
        assertFailsWith<UnsupportedOperationException> { (stored.presentation!!.metrics.single().replacedBossIds as MutableSet<UUID>).clear() }
        assertFailsWith<UnsupportedOperationException> { (stored.replacedBossIds as MutableSet<UUID>).clear() }
        assertFailsWith<UnsupportedOperationException> { (snapshot.widgets as MutableList<ModuleWidget>).clear() }
        assertEquals(stored, controller.snapshot(3).widgets.single())
    }

    @Test fun `an explicit refresh owns a new copy and still preserves semantic change detection`() {
        val controller = CoreController()
        controller.enterSkyBlock("fixture", 0)
        val metrics = mutableListOf(WidgetMetric("count", "Count", "1"))
        val widget = ModuleWidget("contributor.count", "Count", presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics))
        assertEquals(1, controller.apply(listOf(CoreEvent.WidgetObserved(widget)), 1).size)
        assertTrue(controller.apply(listOf(CoreEvent.WidgetObserved(widget.copy(expiresAt = 100))), 2).isEmpty())
        metrics[0] = metrics[0].copy(value = "2")
        assertEquals("1", controller.snapshot(3).widgets.single().presentation!!.metrics.single().value)
        assertEquals(1, controller.apply(listOf(CoreEvent.WidgetObserved(widget.copy(expiresAt = 100))), 4).size)
        metrics.clear()
        assertEquals("2", controller.snapshot(5).widgets.single().presentation!!.metrics.single().value)
        assertTrue(controller.snapshot(100).widgets.isEmpty())
    }
}
