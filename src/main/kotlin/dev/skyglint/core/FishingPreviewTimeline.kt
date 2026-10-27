package dev.skyglint.core

import dev.skyglint.features.ContextModules
import dev.skyglint.features.RareDropSignalModule
import java.util.UUID

/** Source-shaped synthetic observations in a private reducer; no account, inventory or live core is read. */
class FishingPreviewTimeline(private val startedAt: Long) {
    private data class Observation(val at: Long, val apply: (CoreController, Long) -> Unit)
    private var clock = startedAt
    private var core = CoreController()
    private var cycle = Long.MIN_VALUE
    private var applied = 0
    private var previousOffset = -1L

    private val plan = buildList {
        add(Observation(0) { controller, time ->
            controller.updateBoss(BOSS_ID, "Unknown server objective", .64f, "PURPLE", now = time)
            controller.onTabLines(listOf("SB Level: [287] 88 XP", "Pet:", "[Lvl 70] Seal", "72/100 XP (72%)"), time)
            // One actual positive XP observation activates Fishing; preview polling never replays it.
            controller.onActionBar("+1.1 Fishing (48.39%)", time)
        })
        for (second in 0L until PERIOD_MILLIS / 1_000) add(Observation(second * 1_000) { controller, time ->
            controller.apply(listOf(CoreEvent.ContextObserved("Crimson Isle", "crimson_isle")), time)
            controller.ingest(NormalizedSignal.ContextSnapshot("Crimson Isle", "crimson_isle"), time)
            controller.ingest(NormalizedSignal.SidebarSnapshot(listOf("\uE067 Crimson Isle")), time)
            val amount = when (second) {
                in 0L..2L -> 256L
                in 3L..5L -> 12L
                in 6L..8L -> 0L
                in 9L..11L -> null
                in 12L..19L -> 256L
                else -> 8L
            }
            val item = amount?.let { MenuItemSnapshot(8, "Fish Bait",
                listOf("Bait Remaining: $it", "COMMON BAIT"), "FISH_BAIT") }
            controller.ingest(NormalizedSignal.FishingBaitSnapshot(item, true), time)
        })
        add(Observation(15_000) { controller, time -> controller.onChat("Use Baits From Sacks are now disabled!", time) })
        add(Observation(18_000) { controller, time -> controller.onChat("Use Baits From Sacks are now enabled!", time) })
        add(Observation(19_950) { controller, time -> controller.onChat("It's a Double Hook! Woot woot!", time) })
        add(Observation(20_000) { controller, time -> controller.onChat("The Water Hydra has come to test your strength.", time) })
        add(Observation(24_000) { controller, time ->
            controller.onChat("§6⛃ §r§6§lGOOD JUNK CATCH! §r§fYou caught a §r§aRusty Coin§r§f!", time)
        })
        add(Observation(28_000) { controller, time ->
            controller.onChat("§6⛃ §r§6§lGREAT JUNK CATCH! §r§fYou caught an §r§5Old Leather Boot§r§f!", time)
        })
        add(Observation(32_000) { controller, time ->
            controller.onChat("§6\uE02A TROPHY FISH! §r§fYou caught §r§fBlobfish §r§6DIAMOND!", time)
        })
        add(Observation(36_000) { controller, time ->
            controller.onChat("§6\uE025 GOOD CATCH! §r§fYou caught §r§aWater Snake Shard §r§fx3!", time)
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
                modules.register(RareDropSignalModule())
                enterSkyBlock("SYNTHETIC_FISHING:$nextCycle", base)
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
        private val BOSS_ID = UUID(0, 912)
    }
}
