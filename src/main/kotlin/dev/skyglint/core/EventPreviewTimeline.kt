package dev.skyglint.core

import java.util.UUID

/** Explicitly accelerated isolated preview: login active -> ordinary HUD -> reminder -> confirmed start. */
class EventPreviewTimeline(private val startAt: Long) {
    private var cycle = -1L
    private var applied = 0
    private var controller = CoreController()
    fun snapshot(now: Long): HudSnapshot {
        val safeNow = maxOf(now, startAt)
        val elapsed = safeNow - startAt
        val nextCycle = elapsed / PERIOD
        val offset = elapsed % PERIOD
        val base = startAt + nextCycle * PERIOD
        if (cycle != nextCycle) {
            cycle = nextCycle; applied = 0
            controller = CoreController().apply {
                enterSkyBlock("SYNTHETIC_EVENT_PREVIEW:$cycle", base)
                updateBoss(UUID(0, 400), "Objective: Talk to the Bartender", 1f, "YELLOW", now = base)
                apply(listOf(CoreEvent.ContextObserved("Village"),
                    CoreEvent.SkyBlockObserved(SkyBlockSnapshot(88, ProgressSnapshot(12.0, 100.0, .12))),
                    CoreEvent.PetObserved(PetSnapshot("Seal", 70, ProgressSnapshot(null, null, .72)))), base)
            }
        }
        val times = longArrayOf(0, 7_000, 15_000)
        while (applied < times.size && times[applied] <= offset) {
            val step = applied++
            val time = base + times[step]
            val observed = when (step) {
                0 -> EventScheduleObservation("Year of the Seal", null, true, "synthetic")
                1 -> EventScheduleObservation("Spooky Festival", 300_000, false, "synthetic")
                else -> EventScheduleObservation("Spooky Festival", null, true, "synthetic")
            }
            controller.apply(listOf(CoreEvent.EventObserved(observed)), time)
        }
        return controller.snapshot(safeNow)
    }
    companion object { const val PERIOD = 24_000L }
}
