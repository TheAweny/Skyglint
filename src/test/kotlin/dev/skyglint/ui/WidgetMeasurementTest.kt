package dev.skyglint.ui

import dev.skyglint.core.ModuleWidget
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WidgetMeasurementTest {
    @Test fun `legacy registration keeps generic width and measured width stays bounded`() {
        val legacy = ModuleWidget("test.measure.legacy", "Legacy")
        UiWidgetRegistry.register(legacy.id) { _, _, _, _, _, _ -> }
        assertEquals(91.0, UiWidgetRegistry.measureWidth(legacy, 91.0))
        val measured = ModuleWidget("test.measure.custom", "Custom")
        UiWidgetRegistry.register(measured.id, { _, _, _, _, _, _ -> }, { _, _ -> 10_000.0 })
        assertEquals(320.0, UiWidgetRegistry.measureWidth(measured, 91.0))
    }

    @Test fun `invalid measurement quarantines only its registration and returns shared fallback`() {
        val widget = ModuleWidget("test.measure.invalid", "Invalid")
        var calls = 0
        UiWidgetRegistry.register(widget.id, { _, _, _, _, _, _ -> }, { _, _ -> calls++; Double.NaN })
        assertEquals(92.0, UiWidgetRegistry.measureWidth(widget, 92.0))
        assertNull(UiWidgetRegistry.find(widget.id))
        assertEquals(93.0, UiWidgetRegistry.measureWidth(widget, 93.0))
        assertEquals(1, calls)
    }
}
