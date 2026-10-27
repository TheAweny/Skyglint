package dev.skyglint.features.mining

import dev.skyglint.core.SkyBlockParser

/** Protocol facts verified against the pinned upstream fixtures documented in NUCLEUS.md. */
object NucleusPatterns {
    enum class Crystal(val label: String, val icon: String) {
        AMETHYST("Amethyst", "minecraft:purple_dye"), SAPPHIRE("Sapphire", "minecraft:light_blue_dye"),
        AMBER("Amber", "minecraft:orange_dye"), TOPAZ("Topaz", "minecraft:yellow_dye"), JADE("Jade", "minecraft:lime_dye");
        companion object { fun named(value: String) = entries.firstOrNull { it.label == value } }
    }
    enum class Tool(val skyBlockId: String, val label: String, val keeper: String) {
        LAPIS("DWARVEN_LAPIS_SWORD", "Scavenged Lapis Sword", "Lapis"),
        EMERALD("DWARVEN_EMERALD_HAMMER", "Scavenged Emerald Hammer", "Emerald"),
        GOLD("DWARVEN_GOLD_HAMMER", "Scavenged Golden Hammer", "Gold"),
        DIAMOND("DWARVEN_DIAMOND_AXE", "Scavenged Diamond Axe", "Diamond");
        companion object {
            fun identified(id: String?) = entries.firstOrNull { it.skyBlockId == id }
            fun named(name: String) = entries.firstOrNull { it.label == name }
        }
    }
    private val found = Regex("✦ CRYSTAL FOUND \\(([1-5])/5\\)")
    private val crystalName = Regex("(Amethyst|Sapphire|Amber|Topaz|Jade) Crystal")
    private val placed = Regex("✦ You placed the (Amethyst|Sapphire|Amber|Topaz|Jade) Crystal!")
    private val returned = Regex("\\[NPC] Keeper of (Lapis|Emerald|Gold|Diamond): Excellent! You have returned the (Scavenged [A-Za-z ]{1,40}) to its rightful place!")
    private val allReturned = Regex("\\[NPC] Keeper of (Lapis|Emerald|Gold|Diamond): You found all of the items! Behold\\.\\.\\. the Jade Crystal!")
    private val discovered = Regex("You found (Scavenged [A-Za-z ]{1,40}) with your Metal Detector!")
    private val treasure = Regex("(?:^|\\s)TREASURE: ([0-9]{1,4}(?:\\.[0-9]{1,2})?)m(?:$|\\s)")
    fun foundCount(line: String): Int? = found.matchEntire(SkyBlockParser.clean(line))?.groupValues?.get(1)?.toIntOrNull()
    fun foundName(line: String): Crystal? = crystalName.matchEntire(SkyBlockParser.clean(line))?.groupValues?.get(1)?.let(Crystal::named)
    fun placed(line: String): Crystal? = placed.matchEntire(SkyBlockParser.clean(line))?.groupValues?.get(1)?.let(Crystal::named)
    fun returned(line: String): Tool? = returned.matchEntire(SkyBlockParser.clean(line))?.let { match ->
        Tool.named(match.groupValues[2])?.takeIf { it.keeper == match.groupValues[1] }
    }
    fun allReturned(line: String) = allReturned.matches(SkyBlockParser.clean(line))
    fun discovered(line: String): Tool? = discovered.matchEntire(SkyBlockParser.clean(line))?.groupValues?.get(1)?.let(Tool::named)
    fun searching(line: String) = treasure.containsMatchIn(SkyBlockParser.clean(line))
    fun completed(line: String) = SkyBlockParser.clean(line) == "CRYSTAL NUCLEUS LOOT BUNDLE"
}
