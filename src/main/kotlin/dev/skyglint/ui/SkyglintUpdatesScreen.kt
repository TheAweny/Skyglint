package dev.skyglint.ui

import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.settings.SettingsButton
import dev.skyglint.ui.settings.SettingsPaint
import dev.skyglint.ui.text.HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Util

/** Same native controls/material as settings; preview states cannot perform update actions. */
class SkyglintUpdatesScreen(
    private val parent: Screen?,
    private val previewState: GitHubUpdates.View? = null,
) : Screen(Component.literal("Skyglint updates")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private lateinit var checkButton: SettingsButton
    private lateinit var downloadButton: SettingsButton
    private lateinit var toggleButton: SettingsButton
    private lateinit var releaseButton: SettingsButton
    private var readyCaption = false
    private val view get() = previewState ?: GitHubUpdates.view

    override fun init() {
        readyCaption = view.phase == GitHubUpdates.Phase.READY
        val compact = height < 310
        val rowHeight = if (compact) 22 else 28
        val gap = if (compact) 4 else 6
        val margin = 12
        panelWidth = minOf(430, width - 20).coerceAtLeast(180)
        panelHeight = minOf(280, height - 16).coerceAtLeast(180)
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        val controlWidth = (panelWidth - margin * 2 - gap) / 2
        val controlY = panelY + panelHeight - margin - 3 * rowHeight - 2 * gap
        checkButton = addRenderableWidget(SettingsButton(panelX + margin, controlY, controlWidth, rowHeight,
            "Check", hint = "Check the latest public release. Available once every 10 minutes; this does not download a JAR.") {
            GitHubUpdates.check()
        })
        downloadButton = addRenderableWidget(SettingsButton(panelX + margin + controlWidth + gap, controlY, controlWidth, rowHeight,
            if (readyCaption) "Restart required" else "Download", hint = "Download and verify the JAR for the next Minecraft launch. Your current game keeps running.") {
            GitHubUpdates.download()
        })
        toggleButton = addRenderableWidget(SettingsButton(panelX + margin, controlY + rowHeight + gap,
            panelWidth - margin * 2, rowHeight, "Check on login", toggled = { view.automaticChecks },
            hint = "Check once on the first SkyBlock entry of each game launch. This preference is saved; updates are not downloaded automatically.") {
            GitHubUpdates.setAutomaticChecks(!view.automaticChecks)
        })
        releaseButton = addRenderableWidget(SettingsButton(panelX + margin, controlY + 2 * (rowHeight + gap), controlWidth, rowHeight,
            "GitHub releases", hint = "Open github.com/TheAweny/Skyglint/releases in your browser.") {
            Util.getPlatform().openUri(GitHubUpdates.releasesUri)
        })
        addRenderableWidget(SettingsButton(panelX + margin + controlWidth + gap, controlY + 2 * (rowHeight + gap), controlWidth, rowHeight,
            "Back", { "Esc" }, hint = "Close this screen. An active download continues safely in the background.") { onClose() })
        refreshControls()
    }

    override fun tick() {
        if (readyCaption != (view.phase == GitHubUpdates.Phase.READY)) rebuildWidgets()
        refreshControls()
    }

    private fun refreshControls() {
        val preview = previewState != null
        checkButton.active = !preview && !GitHubUpdates.busy && view.phase != GitHubUpdates.Phase.READY
        downloadButton.active = !preview && GitHubUpdates.canDownload
        toggleButton.active = !preview && !GitHubUpdates.busy
        releaseButton.active = !preview
        listOf(checkButton, downloadButton, toggleButton).forEach(SettingsButton::refreshMessage)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x70070C14)
        HudBackdrop.beginFrame(graphics)
        HudBackdrop.capsule(graphics, panelX.toFloat(), panelY.toFloat(), panelWidth.toFloat(), panelHeight.toFloat(), 12f, 1f)
        SettingsPaint.rounded(graphics, panelX, panelY, panelWidth, panelHeight, 12, 0xC0141C27.toInt())
        val current = view
        val compact = height < 310
        val line = if (compact) 14 else 17
        val left = panelX + 14
        HudText.draw(graphics, "Skyglint updates${if (previewState != null) " · PREVIEW" else ""}", left, panelY + 12, SettingsPaint.INK)
        HudText.draw(graphics, HudText.fit("Installed: ${current.currentVersion} · Minecraft 26.2", panelWidth - 28),
            left, panelY + 34, SettingsPaint.MUTED)
        val heading = when (current.phase) {
            GitHubUpdates.Phase.IDLE -> "Check here or when you join SkyBlock"
            GitHubUpdates.Phase.CHECKING -> "Checking for updates…"
            GitHubUpdates.Phase.AVAILABLE -> "Version ${current.availableVersion ?: "—"} available"
            GitHubUpdates.Phase.DOWNLOADING -> "Downloading ${current.availableVersion ?: "—"}…"
            GitHubUpdates.Phase.READY -> "Restart Minecraft · ${current.availableVersion ?: "—"}"
            GitHubUpdates.Phase.UP_TO_DATE -> "You are up to date"
            GitHubUpdates.Phase.UNAVAILABLE -> "Install manually from GitHub"
            GitHubUpdates.Phase.FAILED -> "Update could not be completed"
        }
        HudText.draw(graphics, HudText.fit(heading, panelWidth - 28), left, panelY + 53,
            if (current.phase == GitHubUpdates.Phase.FAILED) 0xFFFFB1AA.toInt() else SettingsPaint.ACCENT)
        val message = current.message.ifBlank { "Download an update when you choose. The new version loads after a full Minecraft restart." }
        wrapped(message, panelWidth - 28).take(if (compact) 3 else 4).forEachIndexed { index, text ->
            HudText.draw(graphics, text, left, panelY + 75 + index * line, SettingsPaint.MUTED)
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun wrapped(text: String, maximumWidth: Int): List<String> {
        val result = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val proposed = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && HudText.width(proposed) > maximumWidth) {
                result += line
                line = word
            } else line = proposed
        }
        if (line.isNotEmpty()) result += HudText.fit(line, maximumWidth)
        return result
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
}
