package dev.skyglint.features.slayer

import kotlin.test.*

class SlayerPatternsTest {
    private fun block(name: String = "Voidgloom Seraph IV", stage: String = "(1.2k/21k) Combat XP") =
        listOf("SKYBLOCK", "Slayer Quest", name, stage, "⏣ Void Sepulture")

    @Test fun `recognizes all six source verified families and aliases with actual per family tier bounds`() {
        val cases = mapOf(
            "Revenant Horror V" to SlayerPatterns.Type.REVENANT,
            "Atoned Horror V" to SlayerPatterns.Type.REVENANT,
            "Tarantula Broodfather V" to SlayerPatterns.Type.TARANTULA,
            "Conjoined Brood V" to SlayerPatterns.Type.TARANTULA,
            "Sven Packmaster IV" to SlayerPatterns.Type.SVEN,
            "Voidgloom Seraph IV" to SlayerPatterns.Type.VOIDGLOOM,
            "Inferno Demonlord IV" to SlayerPatterns.Type.INFERNO,
            "Bloodfiend V" to SlayerPatterns.Type.BLOODFIEND,
            "Riftstalker Bloodfiend V" to SlayerPatterns.Type.BLOODFIEND,
        )
        for ((name, type) in cases) {
            val result = assertNotNull(SlayerPatterns.sidebar(block(name)))
            assertEquals(type, result.quest.type)
            assertEquals(name, result.quest.label)
        }
        for (name in listOf("Sven Packmaster V", "Voidgloom Seraph V", "Inferno Demonlord V",
            "Revenant Horror VI", "Conjoined Brood", "Some Voidgloom Seraph IV", "Bloodfiend 5"))
            assertNull(SlayerPatterns.sidebar(block(name)), name)
    }

    @Test fun `requires exact adjacent header and complete quest name rather than names elsewhere`() {
        assertNull(SlayerPatterns.sidebar(listOf("Voidgloom Seraph IV", "Slay the boss!")))
        assertNull(SlayerPatterns.sidebar(listOf("Slayer Quest", "", "Voidgloom Seraph IV", "Slay the boss!")))
        assertNull(SlayerPatterns.sidebar(block() + listOf("Slayer Quest")))
        assertNull(SlayerPatterns.sidebar(block("Voidgloom Seraph IV extra")))
        assertNull(SlayerPatterns.sidebar(block().toMutableList().apply { this[1] = "Player: Slayer Quest" }))
        val formatted = block("§5Voidgloom Seraph §cIV", "§e(1.2k/21k) Combat XP")
        assertEquals(SlayerPatterns.Phase.GRINDING, SlayerPatterns.sidebar(formatted)?.phase)
    }

    @Test fun `explicit current maximum is bounded copied progress with distinct XP and kill units`() {
        val xp = assertNotNull(SlayerPatterns.progress("(1.2k/21k) Combat XP"))
        assertEquals(1_200.0, xp.current)
        assertEquals(21_000.0, xp.total)
        assertEquals("1.2k/21k Combat XP", xp.caption)
        assertEquals(1_200.0 / 21_000, xp.snapshot.fraction)
        assertEquals(SlayerPatterns.Unit.COMBAT_XP, xp.unit)
        val kills = assertNotNull(SlayerPatterns.progress("38/40 Kills"))
        assertEquals(SlayerPatterns.Unit.KILLS, kills.unit)
        assertEquals(0.95, kills.snapshot.fraction)
        assertEquals(1_234.0, SlayerPatterns.progress("(1,234/12,000) Combat XP")?.current)
        assertEquals(0.0, SlayerPatterns.progress("(0/4.8k) Combat XP")?.current)
    }

    @Test fun `malformed unbounded or ambiguous progress never becomes a filled bar`() {
        for (line in listOf("(1/0) Combat XP", "(41/40) Combat XP", "-1/40 Kills", "1.5/40 Kills",
            "(NaN/40) Combat XP", "(∞/40) Combat XP", "(1,2/40) Combat XP", "(1/1,00) Combat XP",
            "(1/40) Combat XP extra", "1/40 Kills extra", "75% Combat XP", "Kills: 1/40",
            "(1/999999999k) Combat XP", "38/40 Kills\n", "(1/40) Combat\tXP")) {
            assertNull(SlayerPatterns.progress(line), line)
            assertNull(SlayerPatterns.sidebar(block(stage = line))?.phase, line)
        }
    }

    @Test fun `boss phase and terminal headers remain different explicit observations`() {
        assertEquals(SlayerPatterns.Phase.FIGHT, SlayerPatterns.sidebar(block(stage = "Slay the boss!"))?.phase)
        assertEquals(SlayerPatterns.Phase.SLAIN, SlayerPatterns.sidebar(block(stage = "Boss slain!"))?.phase)
        assertNull(SlayerPatterns.sidebar(block(stage = "Boss disappeared!"))?.phase)
        assertTrue(SlayerPatterns.started("§a  SLAYER QUEST STARTED!"))
        val lines = mapOf("NICE! SLAYER BOSS SLAIN!" to SlayerPatterns.Outcome.SLAIN,
            "SLAYER QUEST COMPLETE!" to SlayerPatterns.Outcome.COMPLETE,
            "SLAYER QUEST FAILED!" to SlayerPatterns.Outcome.FAILED,
            "Your Slayer Quest has been cancelled!" to SlayerPatterns.Outcome.CANCELLED)
        for ((line, outcome) in lines) {
            assertEquals(outcome, SlayerPatterns.outcome("§a  $line"))
            assertNull(SlayerPatterns.outcome("Player: $line"))
            assertNull(SlayerPatterns.outcome("$line extra"))
            assertNull(SlayerPatterns.outcome("$line\n"))
        }
        assertFalse(SlayerPatterns.started("Party > Player: SLAYER QUEST STARTED!"))
    }
}
