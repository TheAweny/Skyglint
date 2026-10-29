package dev.skyglint.core

/** Shared strict protocol confirmation parsing; neither snapshots nor progress resets confirm a level. */
object LevelUpParser {
    fun skill(signal: NormalizedSignal): List<CoreEvent> = SkillLevelConfirmationParser.parse(signal)
    fun pet(signal: NormalizedSignal): List<CoreEvent> = PetLevelConfirmationParser.parse(signal)
}
