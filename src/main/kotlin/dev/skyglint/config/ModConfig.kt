package dev.skyglint.config

import dev.skyglint.core.RewardRulePolicy
import java.util.Collections

/** Preferences only. Live SkyBlock session data must never be restored from this file. */
data class ModConfig(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val enabled: Boolean = true,
    val loggingEnabled: Boolean = false,
    val captureRawText: Boolean = false,
    val skillTimeoutSeconds: Int = 10,
    val hudScale: Double = 1.0,
    val topOffset: Int = 12,
    val showPets: Boolean = true,
    val rewardMode: String = "all",
    val rewardOverrides: Map<String, String> = emptyMap(),
    val captureChat: Boolean = false,
    val detailedDungeonHud: Boolean = false,
    val checkUpdates: Boolean = true,
    /** -1..1 across available horizontal travel; zero preserves the centered row. */
    val hudOffsetX: Double = 0.0,
    /** 0..1 across available vertical travel; null preserves the legacy topOffset. */
    val hudPositionY: Double? = null,
    val showWelcome: Boolean = true,
    val showEventReminders: Boolean = true,
    val showEventStarts: Boolean = true,
    val showSkillLevelUps: Boolean = true,
    val showPetLevelUps: Boolean = true,
    val showSkyBlockLevelUps: Boolean = true,
    val showGardenVisitors: Boolean = true,
    val showGardenPests: Boolean = true,
    val showCropMilestones: Boolean = true,
    val showMiningTimers: Boolean = true,
    val showFuelWarnings: Boolean = true,
    val showPetIntroduction: Boolean = true,
    val showFishingBait: Boolean = true,
    val showSackWarning: Boolean = true,
    val rotationSeconds: Int = 6,
    val helperLimit: Int = 7,
    val showCookieExpiry: Boolean = true,
    val showGodPotionExpiry: Boolean = true,
    val showJerryDefense: Boolean = true,
    val showReindrake: Boolean = true,
    val showCookieWarning: Boolean = true,
    val showGodPotionWarning: Boolean = true,
    val showJerryResults: Boolean = true,
) {
    /** Design-to-screen-pixel scale; the renderer must compensate for Minecraft's GUI scaling. */
    val renderScale: Double get() = hudScale * PROTOTYPE_BASE_SCALE

    fun sanitized(): ModConfig {
        if (schemaVersion != CURRENT_SCHEMA) throw UnsupportedConfigVersionException(schemaVersion)
        return copy(
            skillTimeoutSeconds = skillTimeoutSeconds.coerceIn(10, 120),
            hudScale = if (hudScale.isFinite()) hudScale.coerceIn(1.0 / 3.0, 2.0) else 1.0,
            topOffset = topOffset.coerceIn(0, 200),
            hudOffsetX = if (hudOffsetX.isFinite()) hudOffsetX.coerceIn(-1.0, 1.0) else 0.0,
            hudPositionY = hudPositionY?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0),
            rotationSeconds = rotationSeconds.coerceIn(4, 15),
            helperLimit = helperLimit.coerceIn(1, 7),
            rewardMode = if (rewardMode.equals("filtered", true)) "filtered" else "all",
            rewardOverrides = Collections.unmodifiableMap(rewardOverrides.entries.asSequence().mapNotNull { (name, value) ->
                val normalized = RewardRulePolicy.normalizeItemName(name)
                if (normalized.isEmpty() || normalized.length > 120 || name.any(Char::isISOControl)) null
                else when {
                    value.equals("show", true) -> normalized to "show"
                    value.equals("hide", true) -> normalized to "hide"
                    else -> null
                }
            }.take(128).toMap()),
        )
    }

    companion object {
        const val CURRENT_SCHEMA = 3
        const val PROTOTYPE_BASE_SCALE = 1.5
    }
}

class UnsupportedConfigVersionException(val version: Int) : IllegalStateException(
    "Unsupported Skyglint config schema $version; supported schema is ${ModConfig.CURRENT_SCHEMA}. " +
        "The existing file was preserved."
)
