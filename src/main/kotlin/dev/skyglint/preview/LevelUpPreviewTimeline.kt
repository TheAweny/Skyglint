package dev.skyglint.preview

import dev.skyglint.core.*

import java.util.UUID

/** Real detectors/reducer in an isolated repeatable progression scenario, not renderer-only notice data. */
class LevelUpPreviewTimeline(private val startAt: Long, private val pet: Boolean) {
    private var cycle = -1L
    private var stage = 0
    private var core = CoreController()
    fun snapshot(now: Long): HudSnapshot {
        val safe = maxOf(now, startAt)
        val elapsed = safe - startAt
        val nextCycle = elapsed / 9_000
        val offset = elapsed % 9_000
        val base = startAt + nextCycle * 9_000
        if (cycle != nextCycle) {
            cycle = nextCycle; stage = 0
            core = CoreController().apply {
                enterSkyBlock("SYNTHETIC_LEVEL_UP:$pet:$cycle", base)
                updateBoss(UUID(0, 500), "Objective: Talk to the Bartender", 1f, "YELLOW", now = base)
                apply(listOf(CoreEvent.PetObserved(PetSnapshot("Seal", 70, ProgressSnapshot(null, null, .98))),
                    CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 20, ProgressSnapshot(null, null, .98)))), base)
                onActionBar("+1 Mining (98%)", base)
            }
        }
        if (stage == 0 && offset >= 1_000) {
            if (pet) core.onChat("Your Seal leveled up to level 71!", base + 1_000)
            else core.onChat("SKILL LEVEL UP Mining XX➜XXI", base + 1_000)
            stage = 1
        }
        if (stage == 1 && offset >= 1_500) {
            if (pet) core.apply(listOf(CoreEvent.PetObserved(PetSnapshot("Seal", 71, ProgressSnapshot(null, null, .004)))), base + 1_500)
            else {
                core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 21, ProgressSnapshot(null, null, .004)))), base + 1_500)
                core.onActionBar("+1 Mining (0.4%)", base + 1_500)
            }
            stage = 2
        }
        return core.snapshot(safe)
    }
}
