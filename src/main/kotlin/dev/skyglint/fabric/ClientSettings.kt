package dev.skyglint.fabric

import dev.skyglint.config.ModConfig
import dev.skyglint.ui.settings.SettingsActions

/** Runtime binding. The catalog and native screen can also use isolated actions in tests. */
object ClientSettings : SettingsActions {
    override val config get() = SkyglintClient.config.current
    override val preview get() = SkyglintClient.preview
    override fun changeConfig(transform: (ModConfig) -> ModConfig) = SkyglintClient.changeConfig(transform)
    override fun setLogging(enabled: Boolean) = SkyglintClient.setLogging(enabled)
    override fun setRawLogging(enabled: Boolean) = SkyglintClient.setRawLogging(enabled)
    override fun setChatLogging(enabled: Boolean) = SkyglintClient.setChatLogging(enabled)
    override fun setDiagnostics(enabled: Boolean) = SkyglintClient.setDiagnostics(enabled)
    override fun exportDiagnostics() = SkyglintClient.exportDiagnostics()
    override fun setPreview(enabled: Boolean) = SkyglintClient.setPreview(enabled)
    override fun selectPreview(id: String) = SkyglintClient.setPreviewScenario(id)
    override fun openUpdates() = GitHubUpdates.open()
}
