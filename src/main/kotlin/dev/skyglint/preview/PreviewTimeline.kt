package dev.skyglint.preview

import dev.skyglint.core.*

import java.util.UUID

/** Deterministic synthetic observations in an isolated reducer; never reads or mutates live state. */
class PreviewTimeline(private val startAt: Long) {
    private data class PlannedObservation(val at: Long, val apply: (CoreController, Long) -> Unit)
    private var controller = CoreController()
    private var cycle = Long.MIN_VALUE
    private var applied = 0
    private var previousOffset = -1L

    private val plan = listOf(
        PlannedObservation(0) { core, time ->
            core.updateBoss(BOSS_ID, "Superior Dragon", .64f, "PURPLE", now = time)
            core.apply(listOf(
                CoreEvent.SkyBlockObserved(SkyBlockSnapshot(287, ProgressSnapshot(88.0, 100.0, .88))),
                CoreEvent.PetObserved(PetSnapshot("Flying Fish", 100, ProgressSnapshot(null, null, .72))),
            ), time)
        },
        PlannedObservation(500) { core, time -> core.onActionBar("+1.1 Mining (48.39%)", time) },
        PlannedObservation(2_000) { core, time ->
            core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest(
                "preview.drop.book", "Rare drop!", "Enchanted Book",
                item = ItemVisualHint("Enchanted Book", "minecraft:enchanted_book"),
            ))), time)
        },
        PlannedObservation(2_600) { core, time -> core.onChat("SKYBLOCK LEVEL UP Level 288", time) },
        PlannedObservation(3_100) { core, time ->
            core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest(
                "preview.drop.sorrow", "Rare drop!", "Sorrow", item = ItemVisualHint("Sorrow"),
            ))), time)
        },
        PlannedObservation(4_700) { core, time ->
            core.updateBoss(BOSS_ID, "Superior Dragon", .50f, "PURPLE", now = time)
        },
        PlannedObservation(9_000) { core, time -> core.onActionBar("+2 Farming (12.5%)", time) },
    )

    fun snapshot(now: Long): HudSnapshot {
        val effectiveNow = maxOf(now, startAt)
        val elapsed = effectiveNow - startAt
        val nextCycle = elapsed / PERIOD_MILLIS
        val offset = elapsed % PERIOD_MILLIS
        val cycleStart = startAt + nextCycle * PERIOD_MILLIS
        if (nextCycle != cycle || offset < previousOffset) {
            controller = CoreController().apply { enterSkyBlock("SYNTHETIC_SEQUENCE", cycleStart) }
            cycle = nextCycle
            applied = 0
        }
        while (applied < plan.size && plan[applied].at <= offset) {
            val observation = plan[applied++]
            observation.apply(controller, cycleStart + observation.at)
        }
        previousOffset = offset
        return controller.snapshot(effectiveNow)
    }

    companion object {
        const val PERIOD_MILLIS = 24_000L
        private val BOSS_ID = UUID(0, 300)
    }
}
