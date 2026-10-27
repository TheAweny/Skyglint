package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class PetLevelUpModuleTest {
    private val module = PetLevelUpModule()
    private fun parse(text: String) = module.parse(NormalizedSignal.Chat(text))

    @Test fun `source confirmed colored server line supplies exact pet and level`() {
        val event = assertIs<CoreEvent.PetLevelUp>(parse("§aYour §bSquid §aleveled up to level §e19§a!").single())
        assertEquals("Squid", event.name)
        assertEquals(19, event.level)
        assertEquals("Grandma's Wolf", assertIs<CoreEvent.PetLevelUp>(parse("Your Grandma's Wolf leveled up to level 100!").single()).name)
        assertEquals("Flying Fish", assertIs<CoreEvent.PetLevelUp>(parse("Your Flying Fish ✦ leveled up to level 51!").single()).name)
        assertEquals(200, assertIs<CoreEvent.PetLevelUp>(parse("Your Golden Dragon leveled up to level 200!").single()).level)
    }

    @Test fun `another player messages malformed names and unsupported values do not announce`() {
        for (line in listOf("[MVP+] Player: Your Squid leveled up to level 19!",
            "Player's Squid leveled up to level 19!", "Your Squid leveled up to level 0!",
            "Your Squid leveled up to level 999999999999999!", "Your Squid leveled up to level 19! extra",
            "Your [Lvl 19] Squid leveled up to level 20!", "Your Squid leveled up to level 19!\nhello"))
            assertTrue(parse(line).isEmpty(), line)
        assertTrue(module.parse(NormalizedSignal.ActionBar("Your Squid leveled up to level 19!")).isEmpty())
    }
}
