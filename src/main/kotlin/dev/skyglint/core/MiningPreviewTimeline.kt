package dev.skyglint.core

import dev.skyglint.features.ContextModules
import java.util.UUID

/** Explicit copied fixtures through real mining modules. Never touches a live account or tool. */
class MiningPreviewTimeline(private val startedAt: Long) {
    private var clock = startedAt
    private val core = CoreController().apply {
        ContextModules.create { clock }.forEach(modules::register)
        enterSkyBlock("SYNTHETIC_MINING", startedAt)
    }
    private val eventId = UUID(0, 706)
    private val activityId = UUID(0, 708)
    private var lastAt = Long.MIN_VALUE
    fun snapshot(now: Long): HudSnapshot {
        clock = maxOf(now, startedAt)
        if (lastAt == Long.MIN_VALUE || clock - lastAt >= 250) {
            lastAt = clock
            val elapsed = (clock - startedAt) % 30_000
            val mode = if (elapsed >= 20_000) "crystal_hollows" else "mining_3"
            val location = if (mode == "crystal_hollows") "Magma Fields" else "Glacite Tunnels"
            core.apply(listOf(CoreEvent.ContextObserved(location, mode)), clock)
            core.ingest(NormalizedSignal.ContextSnapshot(location, mode), clock)
            core.ingest(NormalizedSignal.SidebarSnapshot(listOf("\uE067 $location",
                if (mode == "crystal_hollows") "Heat: 74♨" else "Cold: -42❄")), clock)
            core.ingest(NormalizedSignal.TabSnapshot(listOf("Commissions:", "Glacite Walker Slayer: 72%",
                "Mithril Miner: 48.3%", "", "Powders:", "Mithril: 123,456", "Gemstone: 234,567", "Glacite: 345,678", "")), clock)
            val remaining = 240 - elapsed / 1_000
            val title = if (elapsed < 10_000) "PASSIVE EVENT BETTER TOGETHER RUNNING FOR 03:${(59 - elapsed / 1_000).toString().padStart(2, '0')}"
                else "PASSIVE EVENT 2X POWDER RUNNING FOR ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
            core.updateBoss(eventId, title, .7f, now = clock)
            core.ingest(NormalizedSignal.BossSnapshot(listOf(BossBarState(eventId, title, .7f))), clock)
            val fuel = if (elapsed >= 24_000) 395L else 5_395L
            if (elapsed >= 24_000) core.updateBoss(activityId, "Mining", .6f, now = clock)
            else core.removeBoss(activityId)
            core.ingest(NormalizedSignal.HeldItemSnapshot(MenuItemSnapshot(0, "Titanium Drill DR-X655",
                listOf("Fuel: $fuel/10k", "MYTHIC DRILL"), "TITANIUM_DRILL_4", fuel)), clock)
        }
        return core.snapshot(clock)
    }
}
