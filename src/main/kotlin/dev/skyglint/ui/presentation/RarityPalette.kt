package dev.skyglint.ui.presentation

/** Hypixel rarity meanings with lighter ink so small HUD text stays legible over a dark backdrop. */
object RarityPalette {
    fun color(rarity: String?): Int? = when (rarity?.uppercase(java.util.Locale.ROOT)) {
        "COMMON" -> 0xFFE8EEF5.toInt()
        "UNCOMMON" -> 0xFFA2E7AA.toInt()
        "RARE" -> 0xFF9BBEFF.toInt()
        "EPIC" -> 0xFFCB9AFF.toInt()
        "LEGENDARY" -> 0xFFFFCE7B.toInt()
        "MYTHIC" -> 0xFFF4A1E4.toInt()
        "DIVINE" -> 0xFF9FE7EF.toInt()
        "SPECIAL", "VERY SPECIAL" -> 0xFFFFA0A9.toInt()
        "SUPREME" -> 0xFFFFCE7B.toInt()
        "ULTIMATE" -> 0xFFFFA0A9.toInt()
        else -> null
    }
}
