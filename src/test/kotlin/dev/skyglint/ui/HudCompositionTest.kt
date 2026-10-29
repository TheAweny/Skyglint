package dev.skyglint.ui

import dev.skyglint.preview.*

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.*
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class HudCompositionTest {
    private fun base() = HudSnapshot(true, "fixture", emptyList(), null, null, null)
    @Test fun `mining event claims suppress only the exact replaced UUID and leave unknown bars reachable`() {
        val claimed = UUID(0, 730)
        val unknown = UUID(0, 731)
        val widget = ModuleWidget("skyglint.mining.events", "Mining events", expiresAt = 10_000,
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(
                WidgetMetric("event:double_powder", "Double Powder", "×2 · 2:14", "minecraft:redstone", 80))),
            replacedBossIds = setOf(claimed))
        val snapshot = base().copy(location = "Dwarven Mines", widgets = listOf(widget), bosses = listOf(
            BossBarState(claimed, "PASSIVE EVENT 2X POWDER RUNNING FOR 02:14", .5f),
            BossBarState(unknown, "Future unknown mining event", .7f)))
        val elements = HudComposition().compose(snapshot, 0)
        assertEquals("Future unknown mining event", elements.single { it.kind == HudElementKind.MAIN }.title)
        assertEquals("×2 · 2:14", elements.single { it.kind == HudElementKind.COUNTER }.value)
        val returned = HudComposition().compose(snapshot.copy(bosses = snapshot.bosses.take(1)), 10_001)
        assertEquals("PASSIVE EVENT 2X POWDER RUNNING FOR 02:14", returned.single { it.kind == HudElementKind.MAIN }.title)
    }
    @Test fun `counter only mining modules suppress redundant idle location and never classify names themselves`() {
        val widget = ModuleWidget("skyglint.mining.events", "Mining events",
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(
                WidgetMetric("event:better_together", "Better Together", "Together · 6:34", "minecraft:amethyst_shard", 80))))
        val elements = HudComposition().compose(base().copy(location = "Dwarven Mines", widgets = listOf(widget)), 0)
        assertTrue(elements.none { it.kind == HudElementKind.MAIN })
        assertEquals(MiningBossVisuals.resolve("better_together")!!.accent, elements.single { it.kind == HudElementKind.COUNTER }.accent)
    }
    @Test fun `expanded model estimate replaces the missing score placeholder instead of duplicating it`() {
        val run = ModuleWidget("skyglint.dungeon.run", "Catacombs F7", "Maxor",
            presentation = WidgetPresentation(WidgetRole.PRIMARY, metrics = listOf(
                WidgetMetric("forecast", "Estimate", "~208–223 · B", "minecraft:spyglass", 40))))
        val snapshot = base().copy(widgets = listOf(run))
        val expanded = HudComposition().compose(snapshot, 0, detailedDungeonHud = true)
        assertEquals(1, expanded.count { it.kind == HudElementKind.COUNTER })
        assertEquals("~208–223 · B", expanded.single { it.kind == HudElementKind.COUNTER }.value)
        val compact = HudComposition().compose(snapshot, 0)
        assertEquals("—", compact.single { it.kind == HudElementKind.STRIP }.parts.single().text)
    }
    @Test fun `floor is a short entry caption then confirmed phase takes over`() {
        val composition = HudComposition()
        val run = ModuleWidget("skyglint.dungeon.run", "Catacombs F7", "Exploring", priority = 80,
            presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:iron_sword"))
        val snapshot = base().copy(widgets = listOf(run))
        assertEquals("Catacombs F7", composition.compose(snapshot, 0).first().title)
        assertEquals("Exploring", composition.compose(snapshot, 4_600).first().title)
        assertEquals("Maxor", composition.compose(snapshot.copy(widgets = listOf(run.copy(detail = "Maxor"))), 5_000).first().title)
        assertEquals("Catacombs F7", composition.compose(snapshot.copy(widgets = listOf(run.copy(detail = "Maxor"))), 5_000).first().subtitle)
    }
    @Test fun `all contributor widgets remain reachable with original source for custom rendering`() {
        val first = ModuleWidget("example.one", "One", progress = ProgressSnapshot(null, null, .3))
        val second = ModuleWidget("example.two", "Two")
        val composition = HudComposition()
        val snapshot = base().copy(widgets = listOf(first, second))
        assertEquals(first, composition.compose(snapshot, 0).single().sourceWidget)
        assertEquals(second, composition.compose(snapshot, 6_001).single().sourceWidget)
        val primary = ModuleWidget("example.context", "Context", presentation = WidgetPresentation(WidgetRole.PRIMARY))
        val contextual = snapshot.copy(widgets = listOf(primary, second))
        composition.reset()
        assertEquals(primary, composition.compose(contextual, 0).single().sourceWidget)
        assertEquals(second, composition.compose(contextual, 6_001).single().sourceWidget)
    }
    @Test fun `Garden creates independent milestone visitors pests and metrics without giant primary text`() {
        val snapshot = ContextPreviewTimeline(0, false).snapshot(0)
        val elements = HudComposition().compose(snapshot, 0)
        assertEquals("Garden · Plot - 4", elements.single { it.kind == HudElementKind.MAIN }.title)
        assertTrue(elements.any { it.kind == HudElementKind.PROGRESS && it.badge == "32" && it.progress == .977 })
        assertTrue(elements.any { it.id.endsWith(":visitors") && it.value == "3" })
        assertTrue(elements.any { it.id.endsWith(":pests") && it.value == "3" })
        assertFalse(elements.single { it.kind == HudElementKind.MAIN }.title.contains("Fortune"))
        assertTrue(elements.none { it.title.startsWith("Objective:") })
    }
    @Test fun `Dungeon separates clear time secrets crypts rooms and deaths from phase`() {
        val timeline = ContextPreviewTimeline(0, true)
        val elements = HudComposition().compose(timeline.snapshot(4_000), 4_000, detailedDungeonHud = true)
        assertEquals("Maxor", elements.single { it.kind == HudElementKind.MAIN }.title)
        val groups = elements.filter { it.kind == HudElementKind.STRIP }
        assertEquals(3, groups.size)
        val run = groups.single { it.id == "group:dungeon:run" }
        assertEquals(listOf("210", "72.5%"), run.parts.map { it.text })
        assertEquals("@rank:B", run.parts.first().icon)
        assertTrue(groups.single { it.id == "group:dungeon:progress" }.parts.any { it.text == "63%" })
        assertTrue(groups.single { it.id == "group:dungeon:exploration" }.parts.any { it.text == "1" })
        assertEquals(1, elements.count { it.kind == HudElementKind.COUNTER }) // optional puzzle summary
    }
    @Test fun `Dungeon default hides pet and optional groups and keeps server score with secrets`() {
        val elements = HudComposition().compose(ContextPreviewTimeline(0, true).snapshot(0), 0)
        assertTrue(elements.none { it.id == "pet" })
        assertTrue(elements.none { it.id in setOf("group:dungeon:progress", "group:dungeon:exploration") })
        val group = elements.single { it.id == "group:dungeon:run" }
        assertEquals(listOf("210", "72.5%"), group.parts.map { it.text })
        assertEquals("@rank:B", group.parts.first().icon)
        assertTrue(group.subtitle!!.contains("210 · B"))
        assertEquals(1, elements.count { it.kind == HudElementKind.STRIP })
    }

    @Test fun `skill and pet notices are staged with one icon instead of concatenated intro and detail`() {
        for ((id, intro, detail) in listOf(Triple("skyglint.skill_level_up:MINING:21", "Skill level up!", "Mining · Level 21"),
            Triple("skyglint.pet_level_up:seal:71", "Pet level up!", "Seal · Level 71"))) {
            val snapshot = base().copy(activeNotice = TransientNotice(id, intro, detail, 60, 0, 4000,
                ItemVisualHint(vanillaItemId = "minecraft:diamond_pickaxe")))
            val composition = HudComposition()
            assertEquals(intro, composition.compose(snapshot, 500).single().title)
            assertEquals(detail, composition.compose(snapshot, 1_500).single().title)
        }
    }
    @Test fun `Spooky theme and objectives have native icons with concise captions`() {
        val event = base().copy(activeNotice = TransientNotice("skyglint.event_soon:spooky:0", "Starts in 5 min", "Spooky Festival", 35, 0, 4000))
        val element = HudComposition().compose(event, 0).single()
        assertEquals("minecraft:carved_pumpkin", element.icon)
        assertEquals(0xFFFFA65C.toInt(), element.accent)
        val objective = base().copy(bosses = listOf(BossBarState(UUID(0, 1), "Objective: Craft a workbench", 1f)))
        assertEquals("Craft a workbench", HudComposition().compose(objective, 0).single().title)
        assertEquals("minecraft:crafting_table", HudComposition().compose(objective, 0).single().icon)
    }
    @Test fun `private island does not manufacture a redundant main capsule`() {
        assertTrue(HudComposition().compose(base().copy(location = "Your Island"), 0).isEmpty())
        assertEquals("Village", HudComposition().compose(base().copy(location = "Village"), 0).single().title)
    }
    @Test fun `reward name alone conveys rarity with color and a short magic find tail`() {
        val snapshot = base().copy(activeNotice = TransientNotice("skyglint.reward:item", "RARE DROP!", "Enchanted Book", 50, 0, 4000,
            ItemVisualHint("Enchanted Book"), magicFind = 123, rarity = "EPIC"))
        val element = HudComposition().compose(snapshot, 0).single()
        assertEquals("Enchanted Book", element.title)
        assertEquals("Received · ", element.prefix)
        assertEquals("+123% MF", element.value)
        assertEquals("Enchanted Book", element.coloredText)
        assertEquals(RarityPalette.color("EPIC"), element.accent)
        assertFalse(element.title.contains("EPIC"))
    }
    @Test fun `metric detail survives in hover context without inflating compact value`() {
        val widget = ModuleWidget("example.election", "Election", presentation = WidgetPresentation(WidgetRole.COUNTERS,
            metrics = listOf(WidgetMetric("year", "Year", "412", "minecraft:clock", 80, detail = "Early Autumn 27th, 3:00am"))))
        val snapshot = base().copy(widgets = listOf(widget))
        val element = HudComposition().compose(snapshot, 0).single()
        assertEquals("412", element.value)
        assertEquals("Early Autumn 27th, 3:00am", element.subtitle)
    }
    @Test fun `unknown boss UUID remains reachable alongside contextual primary`() {
        val snapshot = ContextPreviewTimeline(0, true).snapshot(0).let {
            it.copy(bosses = it.bosses + BossBarState(UUID(0, 99), "Unrecognized boss event", .5f))
        }
        val composition = HudComposition()
        composition.compose(snapshot, 0)
        val refreshed = snapshot.copy(widgets = snapshot.widgets.map { it.copy(expiresAt = 11_000) })
        assertEquals("Unrecognized boss event", composition.compose(refreshed, 6_000).single { it.kind == HudElementKind.MAIN }.title)
    }
    @Test fun `updating Jacob timer cannot starve an unknown bossbar`() {
        val timeline = ContextPreviewTimeline(0, false)
        val first = timeline.snapshot(8_000).let { it.copy(activeNotice = null, bosses = it.bosses + BossBarState(UUID(0, 99), "Unknown boss", .5f)) }
        val composition = HudComposition()
        composition.compose(first, 8_000)
        val changed = first.copy(widgets = first.widgets.map { if (it.id.contains("jacob")) it.copy(detail = "18m16s", expiresAt = 18_000) else it.copy(expiresAt = 18_000) })
        assertEquals("Unknown boss", composition.compose(changed, 14_000).single { it.kind == HudElementKind.MAIN }.title)
    }
}
