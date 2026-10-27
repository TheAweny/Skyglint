package dev.skyglint.features

import dev.skyglint.core.CoreEvent
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.ProgressSnapshot
import dev.skyglint.core.SignalModule
import dev.skyglint.core.SkyBlockParser
import dev.skyglint.core.SkyBlockSnapshot

/** Reads only the authoritative SkyBlock Leveling item in the matching server menu. */
class LevelingMenuModule : SignalModule {
    override val id = "skyglint.menu_leveling"
    private val levelPattern = Regex("Your SkyBlock Level: \\[([0-9]+)]")
    private val xpPattern = Regex("(?<![0-9])([0-9]+)/100 XP$")

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal !is NormalizedSignal.MenuSnapshot) return emptyList()
        if (SkyBlockParser.clean(signal.title) != "SkyBlock Menu") return emptyList()
        val item = signal.items.firstOrNull {
            SkyBlockParser.clean(it.name) == "SkyBlock Leveling"
        } ?: return emptyList()
        val lore = item.lore.map(SkyBlockParser::clean)
        val level = lore.firstNotNullOfOrNull {
            levelPattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
        } ?: return emptyList()
        val xp = lore.firstNotNullOfOrNull {
            xpPattern.find(it)?.groupValues?.get(1)?.toDoubleOrNull()
        }?.takeIf { it >= 0 && it < 100 } ?: return emptyList()
        return listOf(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(level, ProgressSnapshot(xp, 100.0, xp / 100))))
    }
}
