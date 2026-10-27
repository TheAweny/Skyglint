package dev.skyglint.features

import dev.skyglint.core.SkyBlockParser

/** Strict source markers; ordinary chat/names cannot become context or item rarity. */
object ObservedText {
    private val area = Regex("[⏣ф\\uE067] (.{1,80})")
    private val pestSuffix = Regex(" [\\uE018\\uE07F] x[0-9]{1,3}$")
    private val rarity = Regex("(?:SHINY |DUNGEON )*(VERY SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|SUPREME|ULTIMATE)(?: [A-Z][A-Z ]{0,50})?")
    fun location(sidebar: List<String>): String? = sidebar.asSequence().map(SkyBlockParser::clean)
        .mapNotNull { area.matchEntire(it)?.groupValues?.get(1)?.replace(pestSuffix, "") }.firstOrNull()
    fun rarity(lore: List<String>): String? = lore.asReversed().asSequence().take(5)
        .map(SkyBlockParser::clean).mapNotNull { rarity.matchEntire(it)?.groupValues?.get(1) }.firstOrNull()
}
