package dev.skyglint.features.mining

import dev.skyglint.core.SkyBlockParser

/** Exact server announcements only; an occurrence never invents a remaining lifetime. */
object RandomMiningEventPatterns {
    enum class Type(val id: String, val title: String, val icon: String, val modes: Set<String>) {
        FALLEN_STAR("fallen_star", "Fallen Star", "minecraft:nether_star", setOf("mining_3")),
        POWDER_GHAST("powder_ghast", "Powder Ghast", "minecraft:ghast_tear", setOf("mining_3")),
        GOLDEN_GOBLIN("golden_goblin", "Golden Goblin spawned", "minecraft:gold_nugget", setOf("mining_3", "crystal_hollows")),
        DIAMOND_GOBLIN("diamond_goblin", "Diamond Goblin spawned", "minecraft:diamond", setOf("mining_3", "crystal_hollows")),
        MINESHAFT_PORTAL("mineshaft_portal", "Glacite Mineshaft portal found", "minecraft:blue_ice", setOf("mining_3")),
    }

    data class Observation(val type: Type, val location: String? = null)

    private val star = Regex("✯ A Fallen Star has crashed at (.{1,80})! Nearby ore and Powder drops are amplified!")
    private val ghast = Regex("Find the Powder Ghast near the (.{1,80})!")
    private const val GHAST_ANNOUNCEMENT = "The sound of pickaxes clashing against the rock has attracted the attention of the POWDER GHAST!"
    private val starLocations = setOf("Royal Mines", "Far Reserve", "Goblin Burrows", "Upper Mines", "Rampart's Quarry", "Great Ice Wall", "Cliffside Veins")
    private val ghastLocations = setOf("Divan's Gateway", "Rampart's Quarry", "Cliffside Veins", "Forge Basin")

    fun parse(text: String): Observation? {
        if (text.length > 1_024 || text.any { it.isISOControl() && it != '\n' && it != '\r' }) return null
        val lines = text.lines().map(SkyBlockParser::clean).filter(String::isNotBlank)
        // Some transports preserve the two-line ghast announcement in a single message.
        val line = when {
            lines.size == 1 -> lines.single()
            lines.size == 2 && lines[0] == GHAST_ANNOUNCEMENT -> lines[1]
            else -> return null
        }
        star.matchEntire(line)?.groupValues?.get(1)?.takeIf { it in starLocations }?.let {
            return Observation(Type.FALLEN_STAR, it)
        }
        ghast.matchEntire(line)?.groupValues?.get(1)?.takeIf { it in ghastLocations }?.let {
            return Observation(Type.POWDER_GHAST, it)
        }
        val type = when (line) {
            "A Golden Goblin has spawned!" -> Type.GOLDEN_GOBLIN
            "A Diamond Goblin has spawned!" -> Type.DIAMOND_GOBLIN
            "WOW! You found a Glacite Mineshaft portal!" -> Type.MINESHAFT_PORTAL
            else -> return null
        }
        return Observation(type)
    }
}
