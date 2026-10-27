package dev.skyglint.ui

import dev.skyglint.fabric.SkyglintClient
import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.settings.SettingsButton
import dev.skyglint.ui.settings.SettingsPaint
import dev.skyglint.ui.text.HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale

/** Persistent native settings: each control is keyboard/narration accessible and commits immediately. */
class SkyglintSettingsScreen(private val parent: Screen?) : Screen(Component.literal("Skyglint")) {
    private var status = "Settings save automatically"
    private var error = false
    private val buttons = mutableListOf<SettingsButton>()
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var sceneIndex = -1
    private val scenes = listOf("objective" to "Objective", "skill" to "Skill", "skyblock" to "SB XP",
        "levelup" to "Level up", "rare" to "Rare drop", "unknown" to "Unknown", "sequence" to "Sequence",
        "idle" to "Location", "event" to "Schedule", "petlevelup" to "Pet level up", "skilllevelup" to "Skill level up",
        "garden" to "Garden", "dungeon" to "Catacombs", "election" to "Election", "ranks" to "Ranks", "mining" to "Mining", "nucleus" to "Nucleus run",
        "confirmations" to "Confirmations", "slayerpets" to "Slayer / pets", "fishing" to "Fishing")

    override fun init() {
        buttons.clear()
        val compact = height < 370
        val gap = if (compact) 2 else 6
        val rowHeight = if (compact) 18 else 30
        val headerHeight = if (compact) 40 else 48
        val margin = if (compact) 6 else 16
        val controlCount = 16
        val singleColumnHeight = headerHeight + controlCount * (rowHeight + gap) + rowHeight + margin * 2
        val columns = if (singleColumnHeight > height - 8 && width >= 280) 2 else 1
        panelWidth = minOf(if (columns == 2) 480 else 370, width - 20).coerceAtLeast(160)
        val rows = (controlCount + columns - 1) / columns
        panelHeight = headerHeight + rows * (rowHeight + gap) + rowHeight + margin * 2
        panelX = (width - panelWidth) / 2
        panelY = ((height - panelHeight) / 2).coerceAtLeast(4)
        val controlWidth = (panelWidth - margin * 2 - gap * (columns - 1)) / columns
        var index = 0

        fun control(caption: String, value: () -> String = { "" }, state: (() -> Boolean)? = null,
                    hint: String, persisted: Boolean = true, transientStatus: String = "Preview uses isolated sample data", action: () -> Unit) {
            val x = panelX + margin + index % columns * (controlWidth + gap)
            val y = panelY + headerHeight + index / columns * (rowHeight + gap)
            index++
            val button = SettingsButton(x, y, controlWidth, rowHeight, caption, value, state, hint) {
                try { action(); status = if (persisted) "Saved" else transientStatus; error = false }
                catch (failure: Exception) {
                    status = "Could not save: ${failure.message ?: failure.javaClass.simpleName}"
                    error = true
                }
                buttons.forEach(SettingsButton::refreshMessage)
            }
            buttons += button
            addRenderableWidget(button)
        }
        control("HUD", { onOff(SkyglintClient.config.current.enabled) }, { SkyglintClient.config.current.enabled },
            "Replace bossbars only in a confirmed SkyBlock session.") {
            SkyglintClient.setEnabled(!SkyglintClient.config.current.enabled)
        }
        control("Logging", { onOff(SkyglintClient.config.current.loggingEnabled) }, { SkyglintClient.config.current.loggingEnabled },
            "Record event and HUD diagnostics. This setting persists after restarting Minecraft.") {
            SkyglintClient.setLogging(!SkyglintClient.config.current.loggingEnabled)
        }
        control("Raw HUD text", { onOff(SkyglintClient.config.current.captureRawText) }, { SkyglintClient.config.current.captureRawText },
            "Record original server HUD text while logging is enabled. Useful for investigating unrecognized formats.") {
            SkyglintClient.setRawLogging(!SkyglintClient.config.current.captureRawText)
        }
        control("Pet", { onOff(SkyglintClient.config.current.showPets) }, { SkyglintClient.config.current.showPets },
            "Show the active pet. Its item icon can be observed in the Pets menu; progress uses confirmed data.") {
            SkyglintClient.changeConfig { it.copy(showPets = !it.showPets) }
        }
        control("Scale", { String.format(Locale.ROOT, "%.2f×", SkyglintClient.config.current.hudScale) }, hint =
            "1× is the default HUD size: 45 screen pixels tall. Minecraft GUI scale does not multiply it again.") {
            val choices = listOf(.5, .75, 1.0, 1.25, 1.5, 1.75, 2.0)
            SkyglintClient.changeConfig { it.copy(hudScale = choices.firstOrNull { scale -> scale > it.hudScale + .001 } ?: choices.first()) }
        }
        control("Skill hold", { "${SkyglintClient.config.current.skillTimeoutSeconds}s" }, hint =
            "Minimum hold after an XP gain. Fishing stays for at least 45 seconds; slower gains extend the hold up to 120 seconds. Tab snapshots and location do not activate a skill.") {
            val choices = listOf(10, 15, 30, 60, 120)
            SkyglintClient.changeConfig { it.copy(skillTimeoutSeconds = choices.firstOrNull { seconds -> seconds > it.skillTimeoutSeconds } ?: choices.first()) }
        }
        control("Preview", { onOff(SkyglintClient.preview) }, { SkyglintClient.preview },
            "Show isolated sample data marked PREVIEW. This setting is not saved.", persisted = false) {
            SkyglintClient.setPreview(!SkyglintClient.preview)
        }
        control("Scene", { scenes.getOrNull(sceneIndex)?.second ?: "Choose" }, hint =
            "Cycle through sample HUD scenes. Choosing a scene enables preview mode.", persisted = false) {
            sceneIndex = (sceneIndex + 1) % scenes.size
            SkyglintClient.setPreviewScenario(scenes[sceneIndex].first)
        }
        control("Reset scale", { "100%" }, hint =
            "Restore the default 1× size. Other settings, including logging, are preserved.") {
            SkyglintClient.changeConfig { it.copy(hudScale = 1.0) }
        }
        control("Top offset", { SkyglintClient.config.current.topOffset.toString() }, hint =
            "Distance from the top edge in Minecraft GUI units. Cycles through 8, 12, 18, 24, 32, 48 and 64.") {
            val choices = listOf(8, 12, 18, 24, 32, 48, 64)
            SkyglintClient.changeConfig { it.copy(topOffset = choices.firstOrNull { offset -> offset > it.topOffset } ?: choices.first()) }
        }
        control("Common drops", { if (SkyglintClient.config.current.rewardMode == "filtered") "Hidden" else "Shown" },
            hint = "Filter common rewards such as pearls, ordinary Eyes of Ender and rotten flesh. Summoning Eyes remain visible. Item rule: /skyglint drops hide|show|reset <item>.") {
            SkyglintClient.changeConfig { it.copy(rewardMode = if (it.rewardMode == "filtered") "all" else "filtered") }
        }
        control("Full chat", { onOff(SkyglintClient.config.current.captureChat) }, { SkyglintClient.config.current.captureChat },
            hint = "Record all received chat, including private messages and messages hidden by other mods. Independent of raw HUD text; enabling this also enables logging.") {
            SkyglintClient.setChatLogging(!SkyglintClient.config.current.captureChat)
        }
        control("Diagnostics", { if (SkyglintClient.config.current.let { it.loggingEnabled && it.captureRawText && it.captureChat }) "On" else "Enable" },
            hint = "Enable and save event logging, raw HUD/menu snapshots and all received chat, including private messages.") {
            SkyglintClient.setDiagnostics(true)
        }
        control("Export logs", { "ZIP" }, hint = "Create a local report. It may contain private chat; nothing is uploaded automatically.", persisted = false,
            transientStatus = "Preparing local report") {
            SkyglintClient.exportDiagnostics()
        }
        control("Dungeon HUD", { if (SkyglintClient.config.current.detailedDungeonHud) "Detailed" else "Compact" },
            hint = "Compact shows the phase, score/rank and secrets, with no pet. Detailed adds counters, puzzles and a score estimate when all required data is known.") {
            SkyglintClient.changeConfig { it.copy(detailedDungeonHud = !it.detailedDungeonHud) }
        }
        control("Updates", { "GitHub" }, hint = "Check for updates, download on request and set login checks. New versions load after restarting Minecraft.", persisted = false,
            transientStatus = "Update screen opened") {
            GitHubUpdates.open()
        }
        addRenderableWidget(SettingsButton(panelX + margin, panelY + headerHeight + rows * (rowHeight + gap),
            panelWidth - margin * 2, rowHeight, "Done", { "Esc" }, hint = "Return to the game. Changes are already saved.") { onClose() })
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x70070C14)
        HudBackdrop.beginFrame(graphics)
        HudBackdrop.capsule(graphics, panelX.toFloat(), panelY.toFloat(), panelWidth.toFloat(), panelHeight.toFloat(), 12f, 1f)
        SettingsPaint.rounded(graphics, panelX, panelY, panelWidth, panelHeight, 12, 0xC0141C27.toInt())
        HudText.draw(graphics, "Skyglint", panelX + 16, panelY + 10, SettingsPaint.INK)
        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate((panelX + 16).toFloat(), (panelY + 25).toFloat())
            graphics.pose().scale(.75f, .75f)
            HudText.draw(graphics, HudText.fit(status, ((panelWidth - 32) / .75f).toInt()), 0, 0,
                if (error) 0xFFFFB1AA.toInt() else SettingsPaint.MUTED)
        } finally { graphics.pose().popMatrix() }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
    private fun onOff(value: Boolean) = if (value) "On" else "Off"
}
