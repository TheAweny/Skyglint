package dev.skyglint.ui.settings

import dev.skyglint.preview.PreviewScenario
import java.util.Locale

/** Ordered settings catalog. Native layout derives its dimensions from this list. */
class SettingsControls(private val actions: SettingsActions) {
    private var sceneIndex = -1
    private val scenes = PreviewScenario.entries
    val controls: List<SettingsControl> = buildList {
        fun control(id: String, caption: String, value: () -> String = { "" }, state: (() -> Boolean)? = null,
                    hint: String, persisted: Boolean = true, transientStatus: String = "Preview uses isolated sample data", action: () -> Unit) {
            add(SettingsControl(id, caption, value, state, hint, persisted, transientStatus, action))
        }
        control("enabled", "HUD", { onOff(actions.config.enabled) }, { actions.config.enabled },
            "Replace bossbars only in a confirmed SkyBlock session.") {
            actions.changeConfig { it.copy(enabled = !it.enabled) }
        }
        control("logging", "Logging", { onOff(actions.config.loggingEnabled) }, { actions.config.loggingEnabled },
            "Record event and HUD diagnostics. This setting persists after restarting Minecraft.") {
            actions.setLogging(!actions.config.loggingEnabled)
        }
        control("raw_text", "Raw HUD text", { onOff(actions.config.captureRawText) }, { actions.config.captureRawText },
            "Record original server HUD text while logging is enabled. Useful for investigating unrecognized formats.") {
            actions.setRawLogging(!actions.config.captureRawText)
        }
        control("show_pets", "Pet", { onOff(actions.config.showPets) }, { actions.config.showPets },
            "Show the active pet. Its item icon can be observed in the Pets menu; progress uses confirmed data.") {
            actions.changeConfig { it.copy(showPets = !it.showPets) }
        }
        control("hud_scale", "Scale", { String.format(Locale.ROOT, "%.2f×", actions.config.hudScale) }, hint =
            "1× is the default HUD size: 45 screen pixels tall. Minecraft GUI scale does not multiply it again.") {
            val choices = listOf(.5, .75, 1.0, 1.25, 1.5, 1.75, 2.0)
            actions.changeConfig { it.copy(hudScale = choices.firstOrNull { scale -> scale > it.hudScale + .001 } ?: choices.first()) }
        }
        control("skill_hold", "Skill hold", { "${actions.config.skillTimeoutSeconds}s" }, hint =
            "Minimum hold after an XP gain. Fishing stays for at least 45 seconds; slower gains extend the hold up to 120 seconds. Tab snapshots and location do not activate a skill.") {
            val choices = listOf(10, 15, 30, 60, 120)
            actions.changeConfig { it.copy(skillTimeoutSeconds = choices.firstOrNull { seconds -> seconds > it.skillTimeoutSeconds } ?: choices.first()) }
        }
        control("preview", "Preview", { onOff(actions.preview) }, { actions.preview },
            "Show isolated sample data marked PREVIEW. This setting is not saved.", persisted = false) {
            actions.setPreview(!actions.preview)
        }
        control("preview_scene", "Scene", { scenes.getOrNull(sceneIndex)?.label ?: "Choose" }, hint =
            "Cycle through sample HUD scenes. Choosing a scene enables preview mode.", persisted = false) {
            sceneIndex = (sceneIndex + 1) % scenes.size
            actions.selectPreview(scenes[sceneIndex].id)
        }
        control("reset_scale", "Reset scale", { "100%" }, hint =
            "Restore the default 1× size. Other settings, including logging, are preserved.") {
            actions.changeConfig { it.copy(hudScale = 1.0) }
        }
        control("top_offset", "Top offset", { actions.config.topOffset.toString() }, hint =
            "Distance from the top edge in Minecraft GUI units. Cycles through 8, 12, 18, 24, 32, 48 and 64.") {
            val choices = listOf(8, 12, 18, 24, 32, 48, 64)
            actions.changeConfig { it.copy(topOffset = choices.firstOrNull { offset -> offset > it.topOffset } ?: choices.first(), hudPositionY = null) }
        }
        control("reward_mode", "Common drops", { if (actions.config.rewardMode == "filtered") "Hidden" else "Shown" },
            hint = "Filter common rewards such as pearls, ordinary Eyes of Ender and rotten flesh. Summoning Eyes remain visible. Item rule: /skyglint drops hide|show|reset <item>.") {
            actions.changeConfig { it.copy(rewardMode = if (it.rewardMode == "filtered") "all" else "filtered") }
        }
        control("capture_chat", "Full chat", { onOff(actions.config.captureChat) }, { actions.config.captureChat },
            hint = "Record all received chat, including private messages and messages hidden by other mods. Independent of raw HUD text; enabling this also enables logging.") {
            actions.setChatLogging(!actions.config.captureChat)
        }
        control("diagnostics", "Diagnostics", { if (actions.config.let { it.loggingEnabled && it.captureRawText && it.captureChat }) "On" else "Enable" },
            hint = "Enable and save event logging, raw HUD/menu snapshots and all received chat, including private messages.") {
            actions.setDiagnostics(true)
        }
        control("export_logs", "Export logs", { "ZIP" }, hint = "Create a local report. It may contain private chat; nothing is uploaded automatically.", persisted = false,
            transientStatus = "Preparing local report") {
            actions.exportDiagnostics()
        }
        control("dungeon_detail", "Dungeon HUD", { if (actions.config.detailedDungeonHud) "Detailed" else "Compact" },
            hint = "Compact shows the phase, score/rank and secrets, with no pet. Detailed adds counters, puzzles and a score estimate when all required data is known.") {
            actions.changeConfig { it.copy(detailedDungeonHud = !it.detailedDungeonHud) }
        }
        control("updates", "Updates", { "GitHub" }, hint = "Check for updates, download on request and set login checks. New versions load after restarting Minecraft.", persisted = false,
            transientStatus = "Update screen opened") {
            actions.openUpdates()
        }
    }
    private fun onOff(value: Boolean) = if (value) "On" else "Off"
}
