package dev.skyglint.ui.presentation

import java.util.Locale

/** Theme hints only: names come from source observers; this catalogue does not detect or start events. */
data class EventVisual(val icon: String, val accent: Int, val shortName: String)

object EventVisuals {
    private val formatting = Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")
    private val known = buildMap {
        fun theme(name: String, icon: String, color: Long, shortName: String = name, vararg aliases: String) {
            val visual = EventVisual("minecraft:$icon", color.toInt(), shortName)
            (listOf(name) + aliases).forEach { put(key(it), visual) }
        }
        theme("Spooky Festival", "carved_pumpkin", 0xFFFFA65CL)
        theme("Season of Jerry", "snowball", 0xFF9BDCE9L)
        theme("Jerry's Workshop Opens", "blue_ice", 0xFFB4CDF6L, "Jerry's Workshop", "Jerry's Workshop")
        theme("Fishing Festival", "fishing_rod", 0xFF81C7F6L, "Fishing Festival", "Bonus Fishing Festival")
        theme("Mining Fiesta", "golden_pickaxe", 0xFFF3CB72L, "Mining Fiesta", "Bonus Mining Fiesta")
        theme("Mythological Ritual", "feather", 0xFFDDB977L)
        theme("Traveling Zoo", "villager_spawn_egg", 0xFFA6D784L)
        theme("Dark Auction", "ender_eye", 0xFFC1A0EFL)
        theme("New Year Celebration", "firework_rocket", 0xFFF0A1BDL)
        theme("Bingo", "filled_map", 0xFF94D1BAL)
        theme("Carnival", "target", 0xFFF28E79L)
        theme("Hoppity's Hunt", "rabbit_foot", 0xFFF0B4DDL)
        theme("Year of the Seal", "cod", 0xFF96B9D5L)
        theme("SkyBlock Anniversary", "cake", 0xFFF0B4DDL, "SkyBlock Anniversary", "Century Raffle")
        theme("The Great Spook", "carved_pumpkin", 0xFFFFA65CL)
        theme("Harvest Feast", "wheat", 0xFFD9D985L, "Harvest Feast", "Grand Feast")
        theme("Jacob's Farming Contest", "golden_hoe", 0xFFD9D985L, "Jacob's Contest", "Jacob's Contest")
        theme("Mayor Jerry", "emerald", 0xFFB3D697L)
        theme("Cult of the Fallen Star", "nether_star", 0xFFA8B6F4L, "Fallen Star")
        theme("Election Booth Opens", "lectern", 0xFFE8B78EL, "Election opens")
        theme("Election Over!", "jukebox", 0xFFB4CFA0L, "Election over")
    }

    fun resolve(name: String): EventVisual {
        val clean = clean(name)
        return known[key(clean)] ?: if (dev.skyglint.core.TemporaryEventCatalog.resolve(clean)?.id == "anniversary")
            EventVisual("minecraft:cake", 0xFFF0B4DD.toInt(), clean) else
            EventVisual("minecraft:clock", 0xFFBDC9D6.toInt(), clean.take(80).ifEmpty { "Event" })
    }

    private fun key(value: String): String = clean(value).lowercase(Locale.ROOT)
    private fun clean(value: String): String = formatting.replace(value, "").replace('\u00a0', ' ')
        .replace('’', '\'').replace(whitespace, " ").trim()
}
