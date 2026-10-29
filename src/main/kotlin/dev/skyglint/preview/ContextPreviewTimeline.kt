package dev.skyglint.preview

import dev.skyglint.core.*

import dev.skyglint.features.ContextModules
import java.util.UUID

/** Source snapshots through the real modules; all data is explicitly synthetic and isolated. */
class ContextPreviewTimeline(private val startedAt: Long, private val dungeon: Boolean) {
    private var clock = startedAt
    private var cycle = -1L
    private var processedAt = Long.MIN_VALUE
    private var phase = 0
    private var core = CoreController()
    fun snapshot(now: Long): HudSnapshot {
        val safe = maxOf(now, startedAt)
        val nextCycle = (safe - startedAt) / 24_000
        val base = startedAt + nextCycle * 24_000
        val offset = safe - base
        if (cycle != nextCycle) {
            cycle = nextCycle; phase = 0; processedAt = Long.MIN_VALUE; clock = base
            core = CoreController().apply {
                ContextModules.create { clock }.forEach(modules::register)
                enterSkyBlock("SYNTHETIC_CONTEXT:$dungeon:$cycle", base)
                updateBoss(UUID(0, 600), "Objective: Talk to the Bartender", 1f, "YELLOW", now = base)
                apply(listOf(CoreEvent.PetObserved(PetSnapshot("Seal", 70, ProgressSnapshot(null, null, .72))),
                    CoreEvent.SkyBlockObserved(SkyBlockSnapshot(88, ProgressSnapshot(12.0, 100.0, .12)))), base)
            }
        }
        if (processedAt == Long.MIN_VALUE || safe - processedAt >= 250) {
            clock = safe; processedAt = safe
            val location = if (dungeon) "The Catacombs (F7)" else "The Garden"
            core.apply(listOf(CoreEvent.ContextObserved(location, if (dungeon) "dungeon" else "garden")), clock)
            core.ingest(NormalizedSignal.ContextSnapshot(location, if (dungeon) "dungeon" else "garden"), clock)
            val sidebar = if (dungeon) listOf("⏣ The Catacombs (F7)", "Cleared: 63% (210)", "Time Elapsed: 4m 12s")
                else buildList {
                    add("§7\uE067 §cThe Garde§v§cn §4§l\uE07F§7 x3")
                    add("   §aPlot §7- §b§u§b${if (offset < 6_000) 4 else 8} §4§l\uE07F§7 x1")
                    addAll(listOf("Purse: 554,022,360.5", "Bits: 40,235", "Gems: 350", "Copper: 12,345", "Sowdust: 6.5k"))
                    if (offset >= 8_000) addAll(listOf("Jacob's Contest", "○ Carrot 18m17s", "Collected 8,264"))
                }
            val tab = if (dungeon) listOf("Secrets Found: 72.5%", "Crypts: 4", "Completed Rooms: 19", "Team Deaths: 1",
                "Puzzles: (2)", "Blaze: [✔]", "Water: [✦]")
                else listOf("Visitors: (3)", "Next Visitor: 11m", "", "Crop Milestones:", "Carrot 32: 97.7%", "",
                    "Farming Fortune: \uE0511234", "Garden Level: 17", "Copper: 12,345", "",
                    "Jacob's Contest:", "Starts In: 1s", "○ Carrot", "○ Pumpkin", "○ Wheat", "")
            core.ingest(NormalizedSignal.TabSnapshot(tab), clock)
            core.ingest(NormalizedSignal.SidebarSnapshot(sidebar), clock)
            if (dungeon && phase == 0 && offset >= 4_000) {
                core.onChat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", clock); phase = 1
            }
            if (dungeon && phase == 1 && offset >= 14_000) {
                core.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.", clock); phase = 2
            }
        }
        return core.snapshot(safe)
    }
}
