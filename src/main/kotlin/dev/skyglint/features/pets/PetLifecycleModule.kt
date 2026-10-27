package dev.skyglint.features.pets

import dev.skyglint.core.CoreEvent
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.PetSnapshot
import dev.skyglint.core.SignalModule
import dev.skyglint.core.SkyBlockParser

/**
 * Confirmed equips only. A manual summon does not reveal a level or identify a particular pet
 * instance; neither another same-name snapshot nor a pet XP curve can fill those unknowns here.
 *
 * Server-text evidence (independently implemented; no upstream logic or assets copied):
 * [SkyHanni summon](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/api/pet/CurrentPetApi.kt#L24),
 * [Autopet](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/api/pet/PetStoragePatterns.kt#L112),
 * [Skyblocker fixture](https://github.com/SkyblockerMod/Skyblocker/blob/ca1163e2b7309113cc4dd3c084829d4417606235/src/test/java/de/hysky/skyblocker/skyblock/chat/filters/AutopetFilterTest.java#L16).
 * No supported upstream despawn-chat fixture exists; missing menu/tab data is not a despawn.
 */
class PetLifecycleModule : SignalModule {
    override val id = "skyglint.pet_lifecycle"

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.Chat || signal.text.length > 512 ||
            signal.text.any(Char::isISOControl)) return emptyList()
        val line = SkyBlockParser.clean(signal.text)
        summon.matchEntire(line)?.let { match ->
            return listOf(CoreEvent.PetSwitched(
                PetSnapshot(match.groupValues[1], level = null, skin = match.groupValues[2].isNotEmpty()),
            ))
        }
        val match = autopet.matchEntire(line) ?: return emptyList()
        val level = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..1_000 } ?: return emptyList()
        return listOf(CoreEvent.PetSwitched(
            PetSnapshot(match.groupValues[3], level, skin = match.groupValues[2].isNotEmpty() ||
                match.groupValues[4].isNotEmpty()), automatic = true,
        ))
    }

    companion object {
        // Keep the message anchored: player chat containing the same words is never evidence.
        private const val NAME = "([\\p{L}\\p{N}][\\p{L}\\p{N}'’ -]{0,79}?)"
        private val summon = Regex("You summoned your $NAME( ✦)?!")
        private val autopet = Regex("Autopet equipped your \\[Lvl ([0-9]{1,4})] (?:\\[([0-9]+✦)] )?$NAME( ✦)?! VIEW RULE(?: \\([0-9]+\\))?")
    }
}
