package dev.skyglint.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyBlockParserTest {
    @Test fun `Hypixel team uniqueness codes inside words and amounts are nonprinting`() {
        assertEquals("The Garden", SkyBlockParser.clean("§7The Garde§u§cn§z"))
        assertEquals("Plot - 4", SkyBlockParser.clean("   §aPlot §7- §b§u§b4§s"))
        assertEquals("", SkyBlockParser.clean("§s"))
        val gain = assertIs<CoreEvent.SkillGain>(SkyBlockParser.actionBar("§3+1§u.1 Mini§vng (4§w8.39%)").single())
        assertEquals(Skill.MINING, gain.skill)
        assertEquals(1.1, gain.gain)
        assertEquals(.4839, gain.progress!!.fraction)
    }
    @Test fun `each supported skill uses independent domain key`() {
        Skill.entries.forEach { skill ->
            val event = assertIs<CoreEvent.SkillGain>(SkyBlockParser.actionBar("+1.1 ${skill.displayName} (48.39%)").single())
            assertEquals(skill, event.skill)
            assertEquals(0.4839, event.progress!!.fraction, 0.00000001)
            assertNull(event.progress.current)
        }
    }

    @Test fun `formatted actionbar extracts XP without consuming health or mana`() {
        val events = SkyBlockParser.actionBar("§c100/100❤     §3+6.3 Foraging (24/750)     §b200/200✎ Mana")
        val event = assertIs<CoreEvent.SkillGain>(events.single())
        assertEquals(Skill.FORAGING, event.skill)
        assertEquals(6.3, event.gain)
        assertEquals(24.0, event.progress!!.current)
        assertEquals(750.0, event.progress.maximum)
    }

    @Test fun `ratio supports comma groups compact denominators and overflow`() {
        val ordinary = assertIs<CoreEvent.SkillGain>(SkyBlockParser.actionBar("+207.2 Hunting (5,183,244/10M)").single())
        assertEquals(10_000_000.0, ordinary.progress!!.maximum)
        val overflow = assertIs<CoreEvent.SkillGain>(SkyBlockParser.actionBar("+207.2 Hunting (5,183,244/0)").single())
        assertNull(overflow.progress)
    }

    @Test fun `SB XP is not a skill and uses absolute progress`() {
        val event = assertIs<CoreEvent.SkyBlockGain>(SkyBlockParser.actionBar("§b+10 SkyBlock XP §7(Bestiary)§b (88/100)").single())
        assertEquals(10.0, event.gain)
        assertEquals(88.0, event.progress!!.current)
        assertTrue(SkyBlockParser.actionBar("+10 SkyBlock XP (Bestiary) (88/200)").isEmpty())
    }

    @Test fun `malformed numbers unknown skills and invalid percentages stay unparsed`() {
        listOf(
            "+1..2 Mining (50%)", "+1 Mining (101%)", "+1 Mining (-1%)",
            "+1 Mining (20/10)", "+1 Mining (NaN%)", "+1 FakeSkill (50%)",
            "+0 Mining (50%)", "+-1 Mining (50%)", "+1 Mining (4,2/750)",
            "+1 Mining (Infinity/1)", "x".repeat(9000),
        ).forEach { assertTrue(SkyBlockParser.actionBar(it).isEmpty(), it) }
    }

    @Test fun `tab snapshots retain exact SB XP and separate skill percentage`() {
        val events = SkyBlockParser.tabLines(listOf("SB Level: [287] 24 XP", " Farming 35: 12.4%", " Mining 60: MAX"))
        assertEquals(3, events.size)
        val sb = assertIs<CoreEvent.SkyBlockObserved>(events[0]).snapshot
        assertEquals(287, sb.level)
        assertEquals(24.0, sb.progress!!.current)
        val farming = assertIs<CoreEvent.SkillObserved>(events[1]).snapshot
        assertEquals(35, farming.level)
        assertEquals(0.124, farming.progress!!.fraction, 0.00000001)
        assertEquals(1.0, assertIs<CoreEvent.SkillObserved>(events[2]).snapshot.progress!!.fraction)
    }

    @Test fun `pet must belong to pet widget and uses explicit percentage`() {
        assertTrue(SkyBlockParser.tabLines(listOf("[Lvl 67] T-Rex")).isEmpty())
        val pet = assertIs<CoreEvent.PetObserved>(SkyBlockParser.tabLines(listOf("Pet:", " [Lvl 68] Blaze ✦", " 33,915/179.7k XP (18.9%)")).single()).snapshot!!
        assertEquals("Blaze", pet.name)
        assertEquals(68, pet.level)
        assertTrue(pet.skin)
        assertEquals(0.189, pet.progress!!.fraction, 0.00000001)
    }

    @Test fun `pet XP missing stays unknown and explicit no pet clears selection`() {
        val pet = assertIs<CoreEvent.PetObserved>(SkyBlockParser.tabLines(listOf("Pet:", "[Lvl 100] Flying Fish")).single()).snapshot!!
        assertNull(pet.progress)
        assertNull(assertIs<CoreEvent.PetObserved>(SkyBlockParser.tabLines(listOf("Pet:", "No pet selected")).single()).snapshot)
        val dragon = assertIs<CoreEvent.PetObserved>(SkyBlockParser.tabLines(listOf("Pet:", "[Lvl 200] [122✦] Golden Dragon")).single()).snapshot!!
        assertEquals("Golden Dragon", dragon.name)
        assertTrue(dragon.skin)
    }

    @Test fun `pet ascension skin and explicit max remain separate observed values`() {
        val dragon = assertIs<CoreEvent.PetObserved>(SkyBlockParser.tabLines(listOf("Pet:",
            "§7[Lvl 200] §8[§6335§8§4✦§8] §6Golden Dragon", "MAX LEVEL")).single()).snapshot!!
        assertEquals("Golden Dragon", dragon.name)
        assertEquals(200, dragon.level)
        assertTrue(dragon.skin)
        assertEquals(1.0, dragon.progress!!.fraction)
        assertNull(dragon.progress.current)
        assertNull(dragon.progress.maximum)
    }

    @Test fun `only confirmed SB header creates SB level event`() {
        assertTrue(SkyBlockParser.chat("SKILL LEVEL UP Mining 12").isEmpty())
        assertTrue(SkyBlockParser.chat("[VIP] player: SKYBLOCK LEVEL UP Level 200").isEmpty())
        val exact = assertIs<CoreEvent.SkyBlockLevelUp>(SkyBlockParser.chat("  §3§lSKYBLOCK LEVEL UP §bLevel 288").single())
        assertEquals(288, exact.newLevel)
        val unknownSuffix = assertIs<CoreEvent.SkyBlockLevelUp>(SkyBlockParser.chat("SKYBLOCK LEVEL UP Level 287 ➜ 288").single())
        assertNull(unknownSuffix.newLevel)
    }
}
