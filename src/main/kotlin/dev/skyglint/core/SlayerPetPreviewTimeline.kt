package dev.skyglint.core

import dev.skyglint.features.ContextModules
import java.util.UUID

/** Copied, synthetic server observations through the same modules as live play; no live state is read. */
class SlayerPetPreviewTimeline(private val startedAt: Long) {
    private data class Observation(val at: Long, val apply: (CoreController, Long) -> Unit)
    private var clock = startedAt
    private var core = CoreController()
    private var cycle = Long.MIN_VALUE
    private var applied = 0
    private var previousOffset = -1L

    private val plan = buildList {
        add(Observation(0) { controller, time ->
            controller.prepareWelcome(time)
            controller.onTabLines(listOf("SB Level: [287] 88 XP", "Pet:", "[Lvl 70] Seal", "72/100 XP (72%)"), time)
        })
        // Replay polling too: sparse seeks must arm and settle welcome at the same time as rendered play.
        for (step in 0L until PERIOD_MILLIS / 250) add(Observation(step * 250) { controller, time ->
            if (step % 2 == 0L) {
                controller.apply(listOf(CoreEvent.ContextObserved("The End", "combat_3")), time)
                controller.ingest(NormalizedSignal.ContextSnapshot("The End", "combat_3"), time)
                val at = step * 250
                val sidebar = buildList {
                    add("\uE067 The End")
                    if (at in 8_500L until 20_000L) {
                        add("Slayer Quest")
                        add("Voidgloom Seraph IV")
                        add(when {
                            at >= 17_000 -> "Boss slain!"
                            at >= 13_500 -> "Slay the boss!"
                            at >= 11_000 -> "(3.9k/21k) Combat XP"
                            else -> "(1.2k/21k) Combat XP"
                        })
                    }
                }
                controller.ingest(NormalizedSignal.SidebarSnapshot(sidebar), time)
            }
        })
        add(Observation(21_000) { controller, time ->
            controller.updateBoss(BOSS_ID, "Unknown server objective", .64f, "PURPLE", now = time)
        })
        add(Observation(22_000) { controller, time -> controller.onChat("You summoned your Rabbit!", time) })
        add(Observation(22_500) { controller, time ->
            // The old tab has not caught up. It cannot undo an explicit summon or invent Rabbit XP.
            controller.onTabLines(listOf("Pet:", "[Lvl 70] Seal", "72/100 XP (72%)"), time)
        })
        add(Observation(27_500) { controller, time ->
            controller.onTabLines(listOf("Pet:", "[Lvl 50] Rabbit", "42/100 XP (42%)"), time)
        })
        add(Observation(29_000) { controller, time ->
            controller.onChat("Autopet equipped your [Lvl 85] Tiger! VIEW RULE", time)
        })
        add(Observation(29_500) { controller, time ->
            controller.onTabLines(listOf("Pet:", "[Lvl 50] Rabbit", "42/100 XP (42%)"), time)
        })
        add(Observation(34_500) { controller, time ->
            controller.onTabLines(listOf("Pet:", "[Lvl 85] Tiger", "56/100 XP (56%)"), time)
        })
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
                enterSkyBlock("SYNTHETIC_SLAYER_PETS:$nextCycle", base)
            }
            cycle = nextCycle
            applied = 0
        }
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
        const val PERIOD_MILLIS = 40_000L
        private val BOSS_ID = UUID(0, 911)
    }
}
