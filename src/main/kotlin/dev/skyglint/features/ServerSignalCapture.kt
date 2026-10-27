package dev.skyglint.features

import dev.skyglint.core.SkyBlockParser

/** Opt-in raw diagnostics for relevant unparsed server signals; never a general chat recorder. */
object ServerSignalCapture {
    private val prefix = Regex("(?:RARE DROP!|PET DROP!|(?:GOOD|GREAT|OUTSTANDING) CATCH!|(?:QUEST|OBJECTIVE)(?: COMPLETE| COMPLETED| STARTED| UPDATED|:)|You recovered a Summoning Eye!|A special Zealot has spawned nearby!)(?:.*)")
    fun relevant(text: String): Boolean {
        val line = SkyBlockParser.clean(text).removePrefix("\uE025 ")
        return line.length <= 512 && prefix.matches(line)
    }
}
