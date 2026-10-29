package dev.skyglint.preview

import dev.skyglint.core.*
import java.util.UUID

/** Owns synthetic state only. No live reducer, native artwork or persisted preferences. */
class HudPreviewController {
    var enabled = false; private set
    var scenario = PreviewScenario.SKILL; private set
    private var startedAt = 0L
    private var timeline: ((Long) -> HudSnapshot)? = null

    fun setEnabled(value: Boolean, now: Long) {
        if (value && !enabled) start(now)
        enabled = value
    }

    fun select(id: String, now: Long) {
        scenario = PreviewScenario.fromId(id)
        start(now)
        enabled = true
    }

    fun snapshot(now: Long): HudSnapshot = timeline?.invoke(now) ?: basicSnapshot(now)

    private fun start(now: Long) {
        startedAt = now
        timeline = when (scenario) {
            PreviewScenario.SEQUENCE -> PreviewTimeline(now)::snapshot
            PreviewScenario.EVENT -> EventPreviewTimeline(now)::snapshot
            PreviewScenario.PET_LEVEL_UP, PreviewScenario.SKILL_LEVEL_UP ->
                LevelUpPreviewTimeline(now, scenario == PreviewScenario.PET_LEVEL_UP)::snapshot
            PreviewScenario.GARDEN, PreviewScenario.DUNGEON ->
                ContextPreviewTimeline(now, scenario == PreviewScenario.DUNGEON)::snapshot
            PreviewScenario.ELECTION -> ElectionPreviewTimeline(now)::snapshot
            PreviewScenario.MINING -> MiningPreviewTimeline(now)::snapshot
            PreviewScenario.NUCLEUS -> NucleusPreviewTimeline(now)::snapshot
            PreviewScenario.CONFIRMATIONS -> ConfirmationPreviewTimeline(now)::snapshot
            PreviewScenario.SLAYER_PETS -> SlayerPetPreviewTimeline(now)::snapshot
            PreviewScenario.FISHING -> FishingPreviewTimeline(now)::snapshot
            PreviewScenario.WINTER, PreviewScenario.REINDRAKE, PreviewScenario.BUFFS -> WinterPreviewTimeline(now, scenario)::snapshot
            PreviewScenario.BUFF_WARNING, PreviewScenario.GOD_WARNING, PreviewScenario.DEFENSE_RESULTS -> WinterPreviewTimeline(now, scenario)::snapshot
            else -> null
        }
    }

    private fun basicSnapshot(now: Long): HudSnapshot {
        if (scenario == PreviewScenario.RANKS) return HudSnapshot(true, "SYNTHETIC_RANKS", emptyList(), null, null, null,
            widgets = listOf("D" to 0, "C" to 100, "B" to 160, "A" to 230, "S" to 270, "S+" to 300).map { (rank, score) ->
                ModuleWidget("preview.rank.$rank", rank, presentation = WidgetPresentation(WidgetRole.COUNTERS,
                    metrics = listOf(WidgetMetric("score", "Score", "$score · $rank", priority = 100))))
            })
        fun fraction(start: Double) = if (scenario != PreviewScenario.SKILL) start else
            start + ((now - startedAt).coerceAtLeast(0) % 7_000 / 700).toInt().coerceAtMost(2) * .01
        return HudSnapshot(true, "SYNTHETIC_PREVIEW", if (scenario == PreviewScenario.IDLE) emptyList() else listOf(
            if (scenario == PreviewScenario.OBJECTIVE) BossBarState(UUID(0, 1), "Craft a workbench", 1f, "YELLOW")
            else BossBarState(UUID(0, 1), "Magma Boss", .64f, "RED")),
            when (scenario) {
                PreviewScenario.OBJECTIVE, PreviewScenario.SKYBLOCK, PreviewScenario.LEVEL_UP, PreviewScenario.IDLE ->
                    ProgressionView.SkyBlock(287, ProgressSnapshot(88.0, 100.0, .88))
                PreviewScenario.UNKNOWN -> ProgressionView.SkyBlock(500, null)
                else -> ProgressionView.SkillView(Skill.MINING, null, ProgressSnapshot(null, null, fraction(.4839)), 1.1)
            }, PetSnapshot("Seal", 70, ProgressSnapshot(null, null, fraction(.72))), null,
            activeNotice = when (scenario) {
                PreviewScenario.LEVEL_UP -> TransientNotice("preview.level", "SkyBlock level up!", "Level 288", 100, startedAt, startedAt + 4_000)
                PreviewScenario.RARE -> TransientNotice("preview.rare", "Rare drop!", "Enchanted Book", 50, startedAt, startedAt + 4_000,
                    ItemVisualHint("Enchanted Book", "minecraft:enchanted_book"), magicFind = 123, rarity = "EPIC")
                else -> null
            }, location = "Your Island".takeIf { scenario == PreviewScenario.IDLE })
    }
}
