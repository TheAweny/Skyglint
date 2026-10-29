package dev.skyglint.preview

import dev.skyglint.core.*

import dev.skyglint.features.ContextModules
import java.util.UUID

/**
 * Source-shaped synthetic fixtures through the registered live modules and reducer.
 * This private controller never reads a world, account, chat history or live controller.
 * Bestiary body wording is intentionally synthetic; only its framing is protocol evidence.
 */
class ConfirmationPreviewTimeline(private val startedAt: Long) {
    private data class Observation(val at: Long, val apply: (CoreController, Long) -> Unit)
    private var clock = startedAt
    private var core = CoreController()
    private var cycle = Long.MIN_VALUE
    private var applied = 0
    private var previousOffset = -1L

    private val plan = buildList {
        add(Observation(0) { controller, time ->
            controller.updateBoss(BOSS_ID, "Unknown server objective", .64f, "PURPLE", now = time)
            controller.apply(listOf(
                CoreEvent.SkyBlockObserved(SkyBlockSnapshot(287, ProgressSnapshot(88.0, 100.0, .88))),
                CoreEvent.PetObserved(PetSnapshot("Seal", 70, ProgressSnapshot(null, null, .72))),
            ), time)
            controller.onActionBar("+1.1 Farming (48.39%)", time)
        })
        // Fresh copied sidebar/context observations, never cached actionbar or XP replay.
        for (second in 0L..29L) add(Observation(second * 1_000) { controller, time ->
            controller.apply(listOf(CoreEvent.ContextObserved("Desert Settlement", "farming_1")), time)
            controller.ingest(NormalizedSignal.ContextSnapshot("Desert Settlement", "farming_1"), time)
            val sidebar = buildList {
                add("\uE067 Desert Settlement")
                if (second < 12) {
                    val remaining = 66 - second
                    add("Server closing: ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')} mini123A")
                }
            }
            controller.ingest(NormalizedSignal.SidebarSnapshot(sidebar), time)
        })
        add(Observation(11_500) { controller, time ->
            controller.onChat("[NPC] Trevor: You can find your TRACKABLE animal near the Desert Mountain.", time)
        })
        add(Observation(12_000) { controller, time ->
            controller.onChat("Return to the Trapper soon to get a new animal to hunt!", time)
        })
        add(Observation(16_500) { controller, time ->
            controller.onChat("[NPC] Trevor: You can find your ELUSIVE animal near the Oasis.", time)
        })
        add(Observation(17_000) { controller, time ->
            controller.onChat("You ran out of time and the animal disappeared!", time)
        })
        val bestiary = listOf("§3§l" + "▬".repeat(64),
            "§f" + " ".repeat(34) + "§6§lBESTIARY",
            "§6Observed family II", "§6Observed milestone §812➡§e13", "§a+1 Health",
            "§3§l" + "▬".repeat(64))
        bestiary.forEachIndexed { index, line ->
            add(Observation(21_500 + index * 100L) { controller, time -> controller.onChat(line, time) })
        }
    }.sortedBy { it.at }

    fun snapshot(now: Long): HudSnapshot {
        val safe = maxOf(now, startedAt)
        val elapsed = safe - startedAt
        val nextCycle = elapsed / PERIOD_MILLIS
        val offset = elapsed % PERIOD_MILLIS
        val base = startedAt + nextCycle * PERIOD_MILLIS
        if (cycle != nextCycle || offset < previousOffset) {
            clock = base
            core = CoreController().apply {
                ContextModules.create { clock }.forEach(modules::register)
                enterSkyBlock("SYNTHETIC_CONFIRMATIONS:$nextCycle", base)
            }
            cycle = nextCycle
            applied = 0
        }
        // Replaying all due observations makes seeking and sparse test calls deterministic.
        while (applied < plan.size && plan[applied].at <= offset) {
            val observation = plan[applied++]
            clock = base + observation.at
            observation.apply(core, clock)
            core.snapshot(clock)
        }
        previousOffset = offset
        clock = safe
        return core.snapshot(safe)
    }

    companion object {
        const val PERIOD_MILLIS = 30_000L
        private val BOSS_ID = UUID(0, 910)
    }
}
