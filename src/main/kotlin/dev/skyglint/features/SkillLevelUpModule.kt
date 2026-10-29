package dev.skyglint.features

import dev.skyglint.core.*

/** Compatibility adapter for the baseline explicit skill-confirmation detector. */
class SkillLevelUpModule : SignalModule {
    override val id = "skyglint.skill_level_up"
    override fun parse(signal: NormalizedSignal): List<CoreEvent> = LevelUpParser.skill(signal)
}