package dev.skyglint.ui.settings

/** Pages are descriptions, independent of screen layout. Companion modules may register their own pages. */
data class SettingsPage(val id: String, val title: String, val description: String,
                        val navigation: Boolean = false, val controls: (SettingsActions) -> List<SettingsControl>)

object SettingsPageRegistry {
    private val extensions = linkedMapOf<String, SettingsPage>()
    @Synchronized fun register(page: SettingsPage) {
        require(page.id.matches(Regex("[a-z0-9_.:-]{1,96}")))
        require(page.id !in WorkspaceCatalog.BUILTIN_IDS && page.id !in extensions) { "Duplicate settings page: ${page.id}" }
        extensions[page.id] = page
    }
    @Synchronized fun pages(): List<SettingsPage> = extensions.values.toList()
}
