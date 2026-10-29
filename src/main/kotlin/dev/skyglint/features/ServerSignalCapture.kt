package dev.skyglint.features

import dev.skyglint.core.SkyBlockParser
import dev.skyglint.features.winter.JerryDefenseResultPatterns

/** Opt-in raw diagnostics for relevant unparsed server signals; never a general chat recorder. */
object ServerSignalCapture {
    private val prefix = Regex("(?:RARE DROP!|PET DROP!|(?:GOOD|GREAT|OUTSTANDING) CATCH!|(?:QUEST|OBJECTIVE)(?: COMPLETE| COMPLETED| STARTED| UPDATED|:)|You recovered a Summoning Eye!|A special Zealot has spawned nearby!)(?:.*)")
    fun relevant(text: String): Boolean {
        val line = SkyBlockParser.clean(text).removePrefix("\uE025 ")
        if (line.length <= 512 && prefix.matches(line)) return true
        if (text.length > 12_000) return false
        val lines = text.lines()
        return lines.size <= 24 && lines.any {
            JerryDefenseResultPatterns.line(it) != null || JerryDefenseResultPatterns.startedWave(it) != null
        }
    }
}
