package dev.skyglint.ui

import dev.skyglint.fabric.ClientSettings
import dev.skyglint.fabric.GitHubUpdates
import net.minecraft.client.gui.screens.Screen

/** Deep links open the same workspace directly on its Updates page. */
class SkyglintUpdatesScreen(parent: Screen?, previewState: GitHubUpdates.View? = null) :
    SkyglintSettingsScreen(parent, ClientSettings, "updates", previewState)
