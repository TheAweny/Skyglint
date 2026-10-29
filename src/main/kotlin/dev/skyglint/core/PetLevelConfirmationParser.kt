package dev.skyglint.core

/** Explicit server confirmation, never inferred from a progress rollover or another pet's snapshot. */
internal object PetLevelConfirmationParser : SignalModule {
    override val id = "skyglint.pet_level_up"
    // Source fixture: SkyHanni PetXpEstimateApi, chat.level-up.clean: Your Squid leveled up to level 19!
    private val confirmation = Regex("Your ([\\p{L}\\p{N}][\\p{L}\\p{N}'’ -]{0,79}?)(?: ✦)? leveled up to level ([0-9]{1,4})!")

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.Chat || signal.text.length > 512 ||
            signal.text.any { it == '\n' || it == '\r' || it == '\u0000' }) return emptyList()
        val match = confirmation.matchEntire(SkyBlockParser.clean(signal.text)) ?: return emptyList()
        val level = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..1_000 } ?: return emptyList()
        val name = match.groupValues[1].trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        return listOf(CoreEvent.PetLevelUp(name, level))
    }
}
