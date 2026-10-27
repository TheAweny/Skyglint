package dev.skyglint.features.pets

import dev.skyglint.core.CoreEvent
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.PetSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetLifecycleModuleTest {
    private val module = PetLifecycleModule()
    private fun parse(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun switched(text: String) = assertIs<CoreEvent.PetSwitched>(parse(text).single())

    @Test fun `source manual summons retain unknown level and progress with observed skin only`() {
        val dragon = switched("§aYou summoned your §r§6Golden Dragon§r§a!")
        assertEquals(PetSnapshot("Golden Dragon", null), dragon.snapshot)
        assertEquals(false, dragon.automatic)
        val rabbit = switched("§aYou summoned your §r§dRabbit§r§9 ✦§r§a!")
        assertEquals(PetSnapshot("Rabbit", null, skin = true), rabbit.snapshot)
        assertNull(rabbit.snapshot.progress)
        assertEquals("Grandma's Wolf", switched("You summoned your Grandma's Wolf!").snapshot.name)
    }

    @Test fun `source Autopet equip carries observed level and never presumes max progress`() {
        val tiger = switched("Autopet equipped your [Lvl 85] Tiger! VIEW RULE")
        assertEquals(PetSnapshot("Tiger", 85), tiger.snapshot)
        assertTrue(tiger.automatic)
        val mosquito = switched("§cAutopet §eequipped your §7[Lvl 100] §6Mosquito§e! §a§lVIEW RULE")
        assertEquals(PetSnapshot("Mosquito", 100), mosquito.snapshot)
        assertNull(mosquito.snapshot.progress)
        val rabbit = switched("§cAutopet §eequipped your §7[Lvl 100] §5Rabbit§9 ✦§e! §a§lVIEW RULE")
        assertEquals(PetSnapshot("Rabbit", 100, skin = true), rabbit.snapshot)
    }

    @Test fun `Autopet ascension skin tags are preserved after formatting removal`() {
        for (line in listOf(
            "§cAutopet §eequipped your §7[Lvl 200] §6[122✦] Golden Dragon§e! §a§lVIEW RULE",
            "§cAutopet §eequipped your §7[Lvl 200] §8[§6335§8§4✦§8] §6Golden Dragon§e! §a§lVIEW RULE",
            "Autopet equipped your [Lvl 200] [122✦] Golden Dragon! VIEW RULE (2)",
        )) {
            assertEquals(PetSnapshot("Golden Dragon", 200, skin = true), switched(line).snapshot, line)
        }
    }

    @Test fun `other input channels never become equip confirmations`() {
        val line = "Autopet equipped your [Lvl 85] Tiger! VIEW RULE"
        for (signal in listOf(NormalizedSignal.ActionBar(line), NormalizedSignal.ActionBarPresence(line),
            NormalizedSignal.TabSnapshot(listOf(line)), NormalizedSignal.SidebarSnapshot(listOf(line)),
            NormalizedSignal.MenuSnapshot("Pets", emptyList()))) assertTrue(module.parse(signal).isEmpty())
    }

    @Test fun `spoofs malformed and unverified despawn lines do not alter pet state`() {
        for (line in listOf(
            "[MVP+] Player: You summoned your Rabbit!", "Party > Player: You summoned your Rabbit!",
            "[NPC] Player: Autopet equipped your [Lvl 85] Tiger! VIEW RULE",
            "You despawned your Rabbit!", "You summoned your [Lvl 100] Rabbit!",
            "You summoned your Rabbit! extra", "You summoned your !", "You summoned your Rabbit!\nhello",
            "You summoned your Rabbit!\r", "You summoned your Rabbit!\u0000",
            "Autopet equipped your [Lvl 0] Tiger! VIEW RULE",
            "Autopet equipped your [Lvl 1001] Tiger! VIEW RULE",
            "Autopet equipped your [Lvl 999999999999999] Tiger! VIEW RULE",
            "Autopet equipped your [Lvl 85] Tiger!", "Autopet equipped your [Lvl 85] Tiger! VIEW RULE extra",
            "Autopet equipped your [Lvl 85] [122] Tiger! VIEW RULE", "x".repeat(513),
        )) assertTrue(parse(line).isEmpty(), line)
    }

    @Test fun `lifecycle parser never emits gains or level up and has no state to leak between sessions`() {
        val line = "You summoned your Golden Dragon!"
        val first = parse(line)
        assertEquals(1, first.size)
        assertIs<CoreEvent.PetSwitched>(first.single())
        module.onSessionEnded()
        module.onSessionStarted("next-profile")
        assertEquals(first, parse(line))
        assertTrue(parse("Your Golden Dragon leveled up to level 200!").isEmpty())
    }
}
