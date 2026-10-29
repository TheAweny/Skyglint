package dev.skyglint.preview

import dev.skyglint.core.*
import dev.skyglint.features.ContextModules
import java.util.UUID

/** Explicit synthetic packets through production modules; no live account or persistent buff state. */
class WinterPreviewTimeline(private val startedAt: Long, private val scenario: PreviewScenario) {
    private var clock = startedAt
    private val core = CoreController().apply {
        ContextModules.create { clock }.forEach(modules::register)
        enterSkyBlock("SYNTHETIC_WINTER", clock)
    }
    private var lastAt = Long.MIN_VALUE
    private var lastBuffCycle = -1L
    private var resultSent = false
    private val barId = UUID(0, 9_001)
    fun snapshot(now: Long): HudSnapshot {
        clock = maxOf(now, startedAt)
        if (lastAt == Long.MIN_VALUE || clock - lastAt >= 250) {
            lastAt = clock
            val location = "Jerry's Workshop"
            core.apply(listOf(CoreEvent.ContextObserved(location, "winter")), clock)
            core.ingest(NormalizedSignal.ContextSnapshot(location, "winter"), clock)
            when (scenario) {
                PreviewScenario.WINTER -> {
                    // Captured server order and colors; the preview remains isolated synthetic state.
                    val raw = "§aⒺ       §aⒹ       §aⒸ       §aⒷ       §eⒶ       "
                    core.updateBoss(barId, SkyBlockParser.clean(raw), .64f, now = clock)
                    core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Wave 3", "Magma Cubes Left: 4")), clock)
                    core.ingest(NormalizedSignal.BossSnapshot(listOf(BossBarState(barId,
                        SkyBlockParser.clean(raw), .64f, formattedTitle = raw))), clock)
                }
                PreviewScenario.REINDRAKE -> {
                    core.updateBoss(barId, "Reindrake", .72f, now = clock)
                    core.ingest(NormalizedSignal.BossSnapshot(listOf(BossBarState(barId, "Reindrake", .72f))), clock)
                }
                PreviewScenario.BUFFS -> {
                    val cycle = (clock - startedAt) / 6_000
                    if (cycle != lastBuffCycle) {
                        lastBuffCycle = cycle
                        core.ingest(NormalizedSignal.TabSnapshot(listOf("Cookie Buff", "2h")), clock)
                        core.ingest(NormalizedSignal.TabSnapshot(listOf("Cookie Buff",
                            "Not active! Obtain booster cookies from the community")), clock)
                    }
                }
                PreviewScenario.BUFF_WARNING, PreviewScenario.GOD_WARNING -> {
                    val cycle = (clock - startedAt) / 6_000
                    if (cycle != lastBuffCycle) {
                        lastBuffCycle = cycle
                        // Re-entering the isolated session arms a new demonstration without inventing a refill.
                        core.leaveSkyBlock(); core.enterSkyBlock("SYNTHETIC_BUFF_WARNING:$cycle", clock)
                        core.ingest(NormalizedSignal.ContextSnapshot(location, "winter"), clock)
                        core.ingest(NormalizedSignal.TabSnapshot(if (scenario == PreviewScenario.GOD_WARNING)
                            listOf("You have a God Potion active! 5 minutes") else listOf("Cookie Buff", "5 minutes")), clock)
                    }
                }
                PreviewScenario.DEFENSE_RESULTS -> if (!resultSent) {
                    resultSent = true
                    WinterResultFixture.lines.forEach { core.ingest(NormalizedSignal.Chat(it), clock) }
                }
                else -> Unit
            }
        }
        return core.snapshot(clock)
    }
}
