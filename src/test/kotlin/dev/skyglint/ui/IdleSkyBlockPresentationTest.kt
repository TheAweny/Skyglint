package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import java.util.UUID
import kotlin.test.*

class IdleSkyBlockPresentationTest {
    private val progress = ProgressSnapshot(88.0, 100.0, .88)
    private fun idle(level: Int? = 287) = HudSnapshot(true, "idle-fixture", emptyList(),
        ProgressionView.SkyBlock(level, progress), PetSnapshot("Squid", 70, ProgressSnapshot(null, null, .72)),
        null, location = "Your Island")

    @Test fun `idle private island expands confirmed SkyBlock level with actual player icon and independent XP`() {
        val shown = HudComposition().compose(idle(), 0)
        assertEquals(listOf("skyblock", "pet"), shown.map { it.id })
        val skyblock = shown.first()
        assertEquals(HudElementKind.MAIN, skyblock.kind)
        assertEquals("SkyBlock · 287", skyblock.title)
        assertEquals("@player", skyblock.icon)
        assertEquals(.88, skyblock.progress)
        assertEquals("sb:287", skyblock.identity)
        assertNull(skyblock.value)
        assertEquals(HudElementKind.PROGRESS, shown.last().kind)
        assertEquals("@pet", shown.last().icon)
        assertEquals(.72, shown.last().progress)
    }

    @Test fun `unknown level and unknown XP remain unknown without invented values`() {
        val shown = HudComposition().compose(idle(null).copy(progression = ProgressionView.SkyBlock(null, null)), 0)
        val skyblock = shown.single { it.id == "skyblock" }
        assertEquals(HudElementKind.MAIN, skyblock.kind)
        assertEquals("SkyBlock", skyblock.title)
        assertEquals("@player", skyblock.icon)
        assertNull(skyblock.progress)
        assertNull(skyblock.value)
        assertEquals("sb:null", skyblock.identity)
        assertTrue(HudComposition().compose(idle().copy(progression = null, pet = null), 0).isEmpty())
    }

    @Test fun `short notice preempts idle presentation and current SkyBlock data returns afterward`() {
        val composer = HudComposition()
        val snapshot = idle()
        val original = composer.compose(snapshot, 0).single { it.id == "skyblock" }
        val notice = TransientNotice("example.notice", "Server confirmation", null, 50, 0, 4_000)
        val during = composer.compose(snapshot.copy(activeNotice = notice), 1_000)
        assertEquals(1, during.size)
        assertEquals("Server confirmation", during.single().title)
        assertTrue(during.none { it.id == "skyblock" || it.id == "pet" })
        val after = composer.compose(snapshot.copy(progression = ProgressionView.SkyBlock(287,
            ProgressSnapshot(91.0, 100.0, .91))), 4_001).single { it.id == "skyblock" }
        assertEquals(HudElementKind.MAIN, after.kind)
        assertEquals(original.id, after.id)
        assertEquals(original.identity, after.identity)
        assertEquals("SkyBlock · 287", after.title)
        assertEquals(.91, after.progress)
    }

    @Test fun `objective and public location retain compact SkyBlock circle`() {
        val composer = HudComposition()
        val objective = idle().copy(bosses = listOf(BossBarState(UUID(0, 91), "Objective: Craft a workbench", 1f)))
        val shown = composer.compose(objective, 0)
        assertEquals("Craft a workbench", shown.single { it.id == "main" }.title)
        assertEquals(HudElementKind.PROGRESS, shown.single { it.id == "skyblock" }.kind)
        assertEquals("SkyBlock", shown.single { it.id == "skyblock" }.title)
        assertEquals(.88, shown.single { it.id == "skyblock" }.progress)
        val village = composer.compose(idle().copy(location = "Village"), 1)
        assertEquals("Village", village.single { it.id == "main" }.title)
        assertEquals(HudElementKind.PROGRESS, village.single { it.id == "skyblock" }.kind)
    }

    @Test fun `active skill does not manufacture an expanded SkyBlock element`() {
        val shown = HudComposition().compose(idle().copy(progression =
            ProgressionView.SkillView(Skill.FISHING, 42, ProgressSnapshot(null, null, .34), 10.0)), 0)
        assertTrue(shown.none { it.id == "skyblock" })
        assertEquals(HudElementKind.PROGRESS, shown.single { it.id == "skill" }.kind)
        assertEquals("Fishing", shown.single { it.id == "skill" }.title)
        assertEquals(.34, shown.single { it.id == "skill" }.progress)
        assertEquals(HudElementKind.PROGRESS, shown.single { it.id == "pet" }.kind)
    }

    @Test fun `mining layout never gains an idle SkyBlock fallback and ordinary helpers keep its circle`() {
        val timer = WidgetMetric("event:double_powder", "Double Powder", "", "minecraft:redstone", 80,
            countdown = CountdownSnapshot(60_000, 60_000))
        val mining = ModuleWidget("skyglint.mining.events.current", "Mining events",
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(timer)))
        val shown = HudComposition().compose(idle().copy(widgets = listOf(mining)), 0)
        assertTrue(shown.none { it.id == "skyblock" || it.id == "main" })
        assertTrue(shown.any { it.kind == HudElementKind.TIMER })
        val fuel = ModuleWidget("skyglint.mining.drill_fuel.current", "Drill fuel",
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(
                WidgetMetric("fuel", "Drill fuel", "20%", "minecraft:coal", 80))))
        val withHelper = HudComposition().compose(idle().copy(widgets = listOf(fuel)), 0)
        assertEquals(HudElementKind.PROGRESS, withHelper.single { it.id == "skyblock" }.kind)
        assertTrue(withHelper.any { it.kind == HudElementKind.COUNTER })
    }

    @Test fun `hiding pets does not remove or change the expanded SkyBlock observation`() {
        val shown = HudComposition().compose(idle(), 0, showPets = false)
        assertEquals(1, shown.size)
        assertEquals("SkyBlock · 287", shown.single().title)
        assertEquals(.88, shown.single().progress)
        assertEquals("@player", shown.single().icon)
    }
}
