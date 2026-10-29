package dev.skyglint.ui

import dev.skyglint.config.ModConfig
import dev.skyglint.preview.PreviewScenario
import dev.skyglint.ui.settings.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SettingsControlsTest {
    private class Actions : SettingsActions {
        override var config = ModConfig(loggingEnabled = true, captureChat = true, hudScale = 1.5)
        private var previewEnabled = false
        override val preview get() = previewEnabled
        var selected: String? = null
        override fun changeConfig(transform: (ModConfig) -> ModConfig) { config = transform(config).sanitized() }
        override fun setLogging(enabled: Boolean) = changeConfig { it.copy(loggingEnabled = enabled) }
        override fun setRawLogging(enabled: Boolean) = changeConfig { it.copy(captureRawText = enabled) }
        override fun setChatLogging(enabled: Boolean) = changeConfig { it.copy(captureChat = enabled) }
        override fun setDiagnostics(enabled: Boolean) = changeConfig { it.copy(loggingEnabled = enabled, captureRawText = enabled, captureChat = enabled) }
        override fun exportDiagnostics() {}
        override fun setPreview(enabled: Boolean) { previewEnabled = enabled }
        override fun selectPreview(id: String) { selected = id; previewEnabled = true }
        override fun openUpdates() {}
    }

    @Test fun `reset scale preserves capture preferences and live values follow application changes`() {
        val actions = Actions()
        val controls = SettingsControls(actions).controls.associateBy { it.id }
        controls.getValue("reset_scale").action()
        assertEquals(1.0, actions.config.hudScale)
        assertTrue(actions.config.loggingEnabled && actions.config.captureChat)
        assertEquals("1.00×", controls.getValue("hud_scale").value())
        assertEquals(16, controls.size)
    }

    @Test fun `scene control cycles the same catalog used by commands without saving config`() {
        val actions = Actions()
        val before = actions.config
        val scene = SettingsControls(actions).controls.single { it.id == "preview_scene" }
        PreviewScenario.entries.forEach { expected ->
            scene.action()
            assertEquals(expected.id, actions.selected)
            assertEquals(expected.label, scene.value())
        }
        scene.action()
        assertEquals(PreviewScenario.entries.first().id, actions.selected)
        assertEquals(before, actions.config)
        assertFalse(scene.persisted)
    }
}
