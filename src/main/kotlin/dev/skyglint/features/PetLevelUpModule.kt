package dev.skyglint.features

import dev.skyglint.core.*

/** Compatibility adapter for the baseline explicit pet-confirmation detector. */
class PetLevelUpModule : SignalModule {
    override val id = "skyglint.pet_level_up"
    override fun parse(signal: NormalizedSignal): List<CoreEvent> = LevelUpParser.pet(signal)
}