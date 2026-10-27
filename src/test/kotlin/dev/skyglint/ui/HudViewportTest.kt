package dev.skyglint.ui

import dev.skyglint.config.ModConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudViewportTest {
    @Test fun `variable composition fits as a group without using the old main capsule width cap`() {
        val desktop = HudViewport.fit(960, 540, 2, 1.5, 720, 30, 12, 1000)
        assertEquals(720, desktop.width)
        assertEquals(.75f, desktop.scale)
        val compact = HudViewport.fit(320, 180, 2, 1.5, 720, 30, 12, 1000)
        assertTrue(compact.x + compact.width * compact.scale <= 320)
        assertTrue(compact.width * compact.scale >= 300)
    }
    @Test
    fun `1080p HUD retains prototype 45 pixel height across Minecraft GUI scales`() {
        for (guiScale in 1..4) {
            val viewport = HudViewport.fit(1920 / guiScale, 1080 / guiScale, guiScale,
                ModConfig().renderScale, 400, 30, 12)
            assertEquals(45.0, 30 * viewport.scale.toDouble() * guiScale, 1e-5)
            assertEquals(600.0, viewport.width * viewport.scale.toDouble() * guiScale, 1e-5)
            assertEquals(960.0, (viewport.x + viewport.width * viewport.scale / 2) * guiScale.toDouble(), 1e-5)
        }
    }

    @Test
    fun `user scale multiplies agreed size once at GUI two`() {
        val viewport = HudViewport.fit(960, 540, 2, ModConfig(hudScale = 2.0).renderScale, 400, 30, 12)
        assertEquals(90.0, 30 * viewport.scale.toDouble() * 2, 1e-5)
    }

    @Test
    fun `short content does not reserve the full 400 pixel design limit`() {
        val viewport = HudViewport.fit(960, 540, 2, ModConfig().renderScale, 230, 30, 12)
        assertEquals(230, viewport.width)
        assertEquals(345.0, viewport.width * viewport.scale.toDouble() * 2, 1e-5)
    }

    @Test
    fun `small viewport bounds both axes without cropping stacked bars`() {
        val viewport = HudViewport.fit(160, 90, 2, ModConfig().renderScale, 400, 139, 12)
        assertTrue(viewport.x >= 0)
        assertTrue(viewport.x + viewport.width * viewport.scale <= 160)
        assertTrue(viewport.y + 139 * viewport.scale <= 90)
        assertTrue(viewport.scale > 0)
    }
}
