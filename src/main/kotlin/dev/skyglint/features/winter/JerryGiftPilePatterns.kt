package dev.skyglint.features.winter

/** Structural, lossless reading of the five colored server markers, never estimated point HP. */
object JerryGiftPilePatterns {
    data class Point(val letter: String, val destroyed: Boolean, val color: Int?)
    private val glyphs = "ⒶⒷⒸⒹⒺ"
    private val palette = intArrayOf(0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
        0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF)

    /** Call only with a fresh, independently confirmed Winter defense context. */
    fun points(formatted: String): List<Point>? {
        if (formatted.length !in 5..512 || formatted.any(Char::isISOControl)) return null
        val markers = mutableListOf<Pair<Char, Int?>>()
        val surrounding = StringBuilder()
        var color: Int? = null
        var index = 0
        while (index < formatted.length) {
            val char = formatted[index++]
            if (char == '§') {
                val code = formatted.getOrNull(index++)?.lowercaseChar() ?: return null
                when (code) {
                    in '0'..'9', in 'a'..'f' -> color = 0xFF000000.toInt() or palette[code.digitToInt(16)]
                    'r' -> color = null
                    in 'k'..'o' -> Unit
                    else -> return null
                }
                continue
            }
            if (char in glyphs || char == 'Ⓧ') {
                if (markers.size >= 5) return null
                // An uncolored live marker contains no independently observed status.
                if (char != 'Ⓧ' && color == null) return null
                markers += char to color
            } else surrounding.append(char)
        }
        if (markers.size != 5) return null
        val heading = surrounding.toString().trim().trim(':', '|', '-', '[', ']').trim()
        if (heading.isNotEmpty() && heading.lowercase() !in setOf("gift piles", "defend jerry's workshop", "jerry's workshop")) return null
        // The captured production bar is E→A. Retain that source order, including destroyed slots.
        val order = listOf(glyphs.reversed(), glyphs).firstOrNull { candidate ->
            markers.indices.all { markers[it].first == 'Ⓧ' || markers[it].first == candidate[it] }
        } ?: return null
        return markers.mapIndexed { index, (marker, observedColor) ->
            Point(('A'.code + glyphs.indexOf(order[index])).toChar().toString(), marker == 'Ⓧ', observedColor)
        }
    }
}
