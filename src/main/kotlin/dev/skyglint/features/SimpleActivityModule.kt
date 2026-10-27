package dev.skyglint.features

import dev.skyglint.core.CoreEvent
import dev.skyglint.core.ModuleWidget
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.SignalModule
import dev.skyglint.core.SkyBlockParser

/**
 * Confirmed lockpick action, not region-based activity inference.
 * Crystal observations belong to NucleusActivityModule so they never leave a one-minute stale caption.
 */
class SimpleActivityModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.basic_mining_activity"

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.Chat) return emptyList()
        val line = SkyBlockParser.clean(signal.text)
        if (line == "CHEST LOCKPICKED") {
            return observed("Powder grinding", "Chest lockpicked")
        }
        return emptyList()
    }

    private fun observed(title: String, detail: String): List<CoreEvent> =
        listOf(CoreEvent.WidgetObserved(ModuleWidget(
            id = "skyglint.activity.mining",
            title = title,
            detail = detail,
            priority = 10,
            expiresAt = clock() + 60_000,
        )))
}
