package dev.skyglint.features

import dev.skyglint.core.CoreEvent
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.SignalModule

/** Observes server reward grammar. The domain policy separately decides whether the HUD should interrupt. */
class RareDropSignalModule : SignalModule {
    override val id = "skyglint.rare_drops"
    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.Chat) return emptyList()
        val reward = RewardPatterns.observe(signal.text) ?: return emptyList()
        return listOf(CoreEvent.RewardObserved(reward))
    }
}
