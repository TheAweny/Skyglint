package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class SkillLevelUpModuleTest {
    private val module = SkillLevelUpModule()
    private fun parse(text: String) = module.parse(NormalizedSignal.Chat(text))

    @Test fun `whole source header accepts known skill and canonical roman or numeric transition`() {
        val roman = assertIs<CoreEvent.SkillLevelUp>(parse("  §r§b§lSKILL LEVEL UP §3Mining §8XX➜§3XXI").single())
        assertEquals(Skill.MINING, roman.skill)
        assertEquals(21, roman.level)
        assertEquals(60, assertIs<CoreEvent.SkillLevelUp>(parse("SKILL LEVEL UP Farming 59 ➜ 60").single()).level)
        assertEquals(1, assertIs<CoreEvent.SkillLevelUp>(parse("SKILL LEVEL UP Fishing 0➜I").single()).level)
        assertEquals(49, assertIs<CoreEvent.SkillLevelUp>(parse("SKILL LEVEL UP Combat XLVIII➜XLIX").single()).level)
    }

    @Test fun `does not consume bare levels unknown skills or another players message`() {
        for (line in listOf("Mining XX➜XXI", "SKILL LEVEL UP", "[MVP+] Player: SKILL LEVEL UP Mining XX➜XXI",
            "SKILL LEVEL UP Slayer XX➜XXI", "SKILL LEVEL UP Mining XX➜XIX", "SKILL LEVEL UP Mining XX➜XX",
            "SKILL LEVEL UP Mining I➜IIV", "SKILL LEVEL UP Mining I➜VX", "SKILL LEVEL UP Mining 1➜1001",
            "SKILL LEVEL UP Mining 1➜2 extra", "SKILL LEVEL UP Mining 1➜2\nUnknown trailer"))
            assertTrue(parse(line).isEmpty(), line)
        assertTrue(module.parse(NormalizedSignal.ActionBar("SKILL LEVEL UP Mining XX➜XXI")).isEmpty())
    }
}
