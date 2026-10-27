package dev.skyglint.core

import dev.skyglint.features.ContextModules

/** Thirty-second copied source sequence through the same modules as live play; never touches inventory. */
class NucleusPreviewTimeline(private val startedAt: Long) {
    private var clock = startedAt
    private val core = CoreController().apply {
        ContextModules.create { clock }.forEach(modules::register)
    }
    private var cycle = Long.MIN_VALUE
    private var phase = -1
    private var lastAt = Long.MIN_VALUE

    fun snapshot(now: Long): HudSnapshot {
        clock = maxOf(clock, now)
        if (lastAt == Long.MIN_VALUE || clock - lastAt >= 250) {
            lastAt = clock
            val elapsed = (clock - startedAt).coerceAtLeast(0)
            val nextCycle = elapsed / 30_000
            if (cycle != nextCycle) {
                cycle = nextCycle; phase = -1
                core.enterSkyBlock("SYNTHETIC_NUCLEUS:$cycle", clock)
            }
            val within = elapsed % 30_000
            val nextPhase = when {
                within < 2_000 -> 0
                within < 12_000 -> 1
                within < 17_000 -> 2
                within < 22_000 -> 3
                else -> 4
            }
            val location = if (nextPhase == 1) "Mines of Divan" else "Crystal Nucleus"
            core.apply(listOf(CoreEvent.ContextObserved(location, "crystal_hollows")), clock)
            core.ingest(NormalizedSignal.ContextSnapshot(location, "crystal_hollows"), clock)
            core.ingest(NormalizedSignal.SidebarSnapshot(listOf("\uE067 $location")), clock)
            core.ingest(NormalizedSignal.TabSnapshot(emptyList()), clock)
            if (nextPhase == 1) {
                core.ingest(NormalizedSignal.HeldItemSnapshot(MenuItemSnapshot(0, "Metal Detector", emptyList(), "DWARVEN_METAL_DETECTOR")), clock)
                core.ingest(NormalizedSignal.InventorySnapshot(listOf(
                    MenuItemSnapshot(1, "Scavenged Lapis Sword", emptyList(), "DWARVEN_LAPIS_SWORD"),
                    MenuItemSnapshot(2, "Scavenged Golden Hammer", emptyList(), "DWARVEN_GOLD_HAMMER"))), clock)
                core.ingest(NormalizedSignal.ActionBar("1,200❤     450❈ Defense     TREASURE: 37.2m"), clock)
            } else {
                core.ingest(NormalizedSignal.HeldItemSnapshot(null), clock)
                core.ingest(NormalizedSignal.InventorySnapshot(emptyList()), clock)
            }
            // Source snapshots renew leases above; explicit chat confirmations occur once per phase.
            if (phase != nextPhase) {
                phase = nextPhase
                when (phase) {
                    1 -> core.onChat("[NPC] Keeper of Diamond: Excellent! You have returned the Scavenged Diamond Axe to its rightful place!", clock)
                    2 -> {
                        core.onChat("§5§l✦ CRYSTAL FOUND §7(3/5)", clock)
                        core.onChat("§aJade Crystal", clock)
                    }
                    3 -> core.onChat("§5§l✦ §dYou placed the §aJade Crystal§d!", clock)
                    4 -> core.onChat("  §5§lCRYSTAL NUCLEUS LOOT BUNDLE", clock)
                }
            }
        }
        return core.snapshot(clock)
    }
}
