package dev.skyglint.ui.presentation

/** Source-confirmed score/rank display values only; this never estimates a Dungeon score. */
object RankVisuals {
    data class Score(val number: String, val rank: String) {
        val icon: String get() = "@rank:$rank"
    }
    private val score = Regex("([0-9]{1,4}) · (D|C|B|A|S|S\\+)")
    private val sprites = mapOf("D" to "d", "C" to "c", "B" to "b", "A" to "a", "S" to "s", "S+" to "s_plus")

    fun score(value: String): Score? = score.matchEntire(value)?.let {
        Score(it.groupValues[1], it.groupValues[2])
    }

    /** Relative GUI-atlas sprite path, compatible with the ordinary resource-pack lookup. */
    fun sprite(reference: String?): String? {
        if (reference == null || !reference.startsWith("@rank:")) return null
        return sprites[reference.removePrefix("@rank:")]?.let { "ranks/$it" }
    }
}
