package dev.skyglint.preview

import dev.skyglint.core.*

import dev.skyglint.features.ContextModules

class ElectionPreviewTimeline(private val startedAt: Long) {
    private var clock = startedAt
    private val core = CoreController().apply {
        ContextModules.create { clock }.forEach(modules::register)
        enterSkyBlock("SYNTHETIC_ELECTION", startedAt)
    }
    fun snapshot(now: Long): HudSnapshot {
        clock = maxOf(now, startedAt)
        core.apply(listOf(CoreEvent.ContextObserved("Community Center", "hub")), clock)
        core.ingest(NormalizedSignal.ContextSnapshot("Community Center", "hub"), clock)
        // Real server tab contains only three candidates; their rounded shares need not sum to 100.
        core.ingest(NormalizedSignal.TabSnapshot(listOf("Election: 3d", " Aatrox: |||||||||| (61%)",
            " Foxy: |||||||||| (29%)", " Marina: |||||||||| (4%)", "")), clock)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Year 523 Votes")), clock)
        core.ingest(NormalizedSignal.ElectionSnapshot(522, listOf(
            ElectionVoteCandidate("Aatrox", 182_948), ElectionVoteCandidate("Foxy", 85_810),
            ElectionVoteCandidate("Marina", 12_838), ElectionVoteCandidate("Diana", 9_659),
            ElectionVoteCandidate("Paul", 6_685))), clock)
        return core.snapshot(clock)
    }
}
