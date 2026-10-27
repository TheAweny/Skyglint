package dev.skyglint.features.dungeon

import dev.skyglint.core.SkyBlockParser
import dev.skyglint.core.WidgetMetric
import java.util.Locale

/** Narrow server text observations; no score formula, map scan, mob-health or loot inference. */
internal object DungeonPatterns {
    data class Sidebar(val floor: String?, val cleared: Int?, val elapsed: String?, val score: DungeonScore.Observation? = null,
                       val elapsedSeconds: Int? = null)
    data class Puzzles(val total: Int, val completed: Int, val penalized: Int)
    data class Stats(val secretsPercent: Double?, val crypts: Int?, val rooms: Int?, val deaths: Int?, val puzzles: Puzzles? = null) {
        fun metrics(): List<WidgetMetric> = listOfNotNull(
            secretsPercent?.let { WidgetMetric("secrets", "Secrets", "${number(it)}%", "minecraft:ender_pearl", 70) },
            crypts?.let { WidgetMetric("crypts", "Crypts", it.toString(), "minecraft:bone", 30) },
            deaths?.let { WidgetMetric("deaths", "Deaths", it.toString(), "minecraft:skeleton_skull", 80) },
            rooms?.let { WidgetMetric("rooms", "Rooms", it.toString(), "minecraft:filled_map", 20) },
            puzzles?.let { WidgetMetric("puzzles", "Puzzles", "${it.completed}/${it.total}", "minecraft:tripwire_hook", 25) },
        )
        fun caption(): String? = listOfNotNull(
            secretsPercent?.let { "Secrets ${number(it)}%" }, crypts?.let { "Crypts $it" },
            deaths?.let { "Deaths $it" }, rooms?.let { "Rooms $it" },
            puzzles?.let { "Puzzles ${it.completed}/${it.total}" },
        ).joinToString(" · ").ifEmpty { null }
    }
    private val floor = Regex("(?:[⏣\uE067] )?The Catacombs \\((E|[FM][1-7])\\)")
    private val cleared = Regex("Cleared: ([0-9]{1,3})%(?: \\([0-9]+\\))?")
    private val elapsed = Regex("Time Elapsed: (?:(\\d{1,3})m )?([0-9]{1,2})s")
    private val secrets = Regex("Secrets Found: ([0-9]{1,3}(?:\\.[0-9]{1,2})?)%")
    private val crypts = Regex("Crypts: ([0-9]{1,3})")
    private val rooms = Regex("Completed Rooms: ([0-9]{1,3})")
    private val deaths = Regex("Team Deaths: ([0-9]{1,3})")
    private val puzzleCount = Regex("Puzzles: \\(([0-9]{1,2})\\)")
    private val puzzleState = Regex("[^:\\r\\n]{1,64}: \\[([✔✖✦])] (?:\\([A-Za-z0-9_]*\\))")
    private val puzzleStatePlain = Regex("[^:\\r\\n]{1,64}: \\[([✔✖✦])]")
    private val complete = Regex("☠ Defeated (Bonzo|Scarf|The Professor|Thorn|Livid|Sadan|Necron|The Watcher|Wither King) in (?:[0-9]{1,3}[hms] ?){1,3}(?: \\(NEW RECORD!\\))?")
    private val bossFloor = mapOf("The Watcher" to "E", "Bonzo" to "1", "Scarf" to "2", "The Professor" to "3",
        "Thorn" to "4", "Livid" to "5", "Sadan" to "6", "Necron" to "7", "Wither King" to "7")
    private val introductions = mapOf(
        "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable." to ("1" to "Bonzo"),
        "[BOSS] Scarf: This is where the journey ends for you, Adventurers." to ("2" to "Scarf"),
        "[BOSS] The Professor: I was burdened with terrible news recently..." to ("3" to "The Professor"),
        "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!" to ("4" to "Thorn"),
        "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows." to ("5" to "Livid"),
        "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!" to ("6" to "Sadan"),
        "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" to ("7" to "Maxor"),
        "[BOSS] Storm: Pathetic Maxor, just like expected." to ("7" to "Storm"),
        "[BOSS] Goldor: Who dares trespass into my domain?" to ("7" to "Goldor"),
        "The Core entrance is opening!" to ("7" to "Core entrance"),
        "[BOSS] Necron: You went further than any human before, congratulations." to ("7" to "Necron"),
    )

    fun floor(text: String): String? = floor.matchEntire(SkyBlockParser.clean(text))?.groupValues?.get(1)
    fun sidebar(lines: List<String>): Sidebar {
        val values = lines.take(64).map(SkyBlockParser::clean)
        val seconds = values.firstNotNullOfOrNull { line -> elapsed.matchEntire(line)?.let { match ->
            val minutes = match.groupValues[1].ifEmpty { "0" }.toIntOrNull() ?: return@let null
            val seconds = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return@let null
            minutes * 60 + seconds
        } }
        return Sidebar(values.firstNotNullOfOrNull(::floor), values.firstNotNullOfOrNull { line ->
            cleared.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
        }, seconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" },
            values.firstNotNullOfOrNull(DungeonScore::fromSidebar), seconds)
    }
    fun stats(lines: List<String>): Stats {
        val values = lines.take(256).map(SkyBlockParser::clean)
        fun count(pattern: Regex) = values.firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        return Stats(values.firstNotNullOfOrNull { secrets.matchEntire(it)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { number -> number in 0.0..100.0 } },
            count(crypts), count(rooms), count(deaths), puzzles(values))
    }
    private fun puzzles(lines: List<String>): Puzzles? {
        val header = lines.indexOfFirst { puzzleCount.matches(it) }
        if (header < 0) return null
        val count = puzzleCount.matchEntire(lines[header])!!.groupValues[1].toInt().takeIf { it in 0..10 } ?: return null
        val body = lines.drop(header + 1).take(count)
        if (body.size != count) return null
        val states = body.map { (puzzleStatePlain.matchEntire(it) ?: puzzleState.matchEntire(it))?.groupValues?.get(1) ?: return null }
        val named = body.map { it.substringBefore(':') }.filter { it != "???" }
        if (named.distinct().size != named.size) return null // repeated undiscovered ??? entries are real server data
        return Puzzles(count, states.count { it == "✔" }, states.count { it == "✖" || it == "✦" })
    }
    fun phase(text: String, floor: String?): String? {
        val line = SkyBlockParser.clean(text)
        if (line == "[NPC] Mort: Here, I found this map when I first entered the dungeon." ||
            line == "[NPC] Mort: Right-click the Orb for spells, and Left-click (or Drop) to use your Ultimate!") return "Exploring"
        if (line == "The BLOOD DOOR has been opened!") return "Blood room"
        if (line == "[BOSS] The Watcher: You have proven yourself. You may pass.") return "Blood cleared"
        introductions[line]?.let { (required, name) ->
            if (floor != null && floor.last().toString() == required) return name
        }
        complete.matchEntire(line)?.let { match ->
            val required = bossFloor[match.groupValues[1]]
            if (floor != null && (floor == required || floor.last().toString() == required)) return "Completed"
        }
        return null
    }
    private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
}
