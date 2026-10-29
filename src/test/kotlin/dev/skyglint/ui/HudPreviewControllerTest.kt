package dev.skyglint.ui

import dev.skyglint.preview.HudPreviewController
import dev.skyglint.preview.PreviewScenario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudPreviewControllerTest {
    @Test fun `every public scenario can be selected and snapshots remain synthetic`() {
        val previews = HudPreviewController()
        PreviewScenario.entries.forEach { scene ->
            previews.select(scene.id, 10_000)
            assertTrue(previews.enabled)
            assertTrue(previews.snapshot(11_000).active)
            assertTrue(previews.snapshot(11_000).sessionId!!.startsWith("SYNTHETIC"))
        }
        assertThrows(IllegalArgumentException::class.java) { previews.select("unregistered", 12_000) }
    }

    @Test fun `re-enabling restarts the selected timeline instead of retaining another scenario`() {
        val previews = HudPreviewController()
        previews.select("nucleus", 1_000)
        val initial = previews.snapshot(1_000)
        previews.snapshot(80_000)
        previews.setEnabled(false, 90_000)
        previews.setEnabled(true, 100_000)
        val restarted = previews.snapshot(100_000)
        assertEquals(initial.widgets.map { it.id }, restarted.widgets.map { it.id })
        assertEquals(initial.location, restarted.location)
        assertNull(restarted.activeNotice)
        previews.select("skill", 110_000)
        assertEquals("SYNTHETIC_PREVIEW", previews.snapshot(110_000).sessionId)
    }
}
