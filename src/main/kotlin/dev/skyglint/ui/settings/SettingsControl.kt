package dev.skyglint.ui.settings

import dev.skyglint.config.ModConfig

/** Application boundary shared by settings presentations; controls do not own runtime state. */
interface SettingsActions {
    val config: ModConfig
    val preview: Boolean
    fun changeConfig(transform: (ModConfig) -> ModConfig)
    fun setLogging(enabled: Boolean)
    fun setRawLogging(enabled: Boolean)
    fun setChatLogging(enabled: Boolean)
    fun setDiagnostics(enabled: Boolean)
    fun exportDiagnostics()
    fun setPreview(enabled: Boolean)
    fun selectPreview(id: String)
    fun openUpdates()
}

/** Stable identity, live value and action are independent of the native screen's geometry. */
data class SettingsControl(
    val id: String,
    val caption: String,
    val value: () -> String = { "" },
    val state: (() -> Boolean)? = null,
    val hint: String,
    val persisted: Boolean = true,
    val transientStatus: String = "Preview uses isolated sample data",
    val action: () -> Unit,
)
