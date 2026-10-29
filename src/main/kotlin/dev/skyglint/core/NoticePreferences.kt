package dev.skyglint.core

/** Presentation choices only: observation, confirmation and dedupe run even when a notice is hidden. */
data class NoticePreferences(
    val showWelcome: Boolean = true,
    val showEventReminders: Boolean = true,
    val showEventStarts: Boolean = true,
    val showSkillLevelUps: Boolean = true,
    val showPetLevelUps: Boolean = true,
    val showSkyBlockLevelUps: Boolean = true,
    val showMiningTimers: Boolean = true,
    val showCookieExpiry: Boolean = true,
    val showGodPotionExpiry: Boolean = true,
    val showCookieWarning: Boolean = true,
    val showGodPotionWarning: Boolean = true,
    val showJerryResults: Boolean = true,
) {
    fun allows(request: NoticeRequest): Boolean = when {
        request.id.startsWith("skyglint.welcome:") -> showWelcome
        request.id.startsWith("skyglint.event_soon:") -> showEventReminders
        request.id.startsWith("skyglint.event_started:") || request.id.startsWith("skyglint.event_active:") ||
            request.id.startsWith("skyglint.garden.jacob.started.") -> showEventStarts
        request.id.startsWith("skyglint.skill_level_up:") -> showSkillLevelUps
        request.id.startsWith("skyglint.pet_level_up:") -> showPetLevelUps
        request.id.startsWith("skyglint.skyblock_level_up:") -> showSkyBlockLevelUps
        request.id.startsWith("skyglint.buffs.expiry:cookie:") -> showCookieExpiry
        request.id.startsWith("skyglint.buffs.expiry:god_potion:") -> showGodPotionExpiry
        request.id.startsWith("skyglint.buffs.expiry:warning:cookie:") -> showCookieWarning
        request.id.startsWith("skyglint.buffs.expiry:warning:god_potion:") -> showGodPotionWarning
        request.id.startsWith("skyglint.winter.defense.result:") -> showJerryResults
        request.reward?.sourceId?.startsWith("jerry_defense_claimable:") == true -> showJerryResults
        request.target?.widgetId == "skyglint.mining.events.current" ||
            request.id.startsWith("skyglint.mining.events:intro:") -> showMiningTimers
        else -> true // Includes update announcements and unknown companion notices.
    }
}
