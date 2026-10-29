package dev.skyglint.ui.presentation

/** Immutable presentation choices. These never enable, disable or replay server observations. */
data class HudPreferences(
    val showPets: Boolean = true,
    val detailedDungeonHud: Boolean = false,
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
    val showJerryDefense: Boolean = true,
    val showReindrake: Boolean = true,
)
