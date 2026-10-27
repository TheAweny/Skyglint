package dev.skyglint.ui.presentation

/** Theme hints only; server recognition and exact UUID claims belong to the feature module. */
data class MiningBossVisual(val title: String, val shortLabel: String, val icon: String, val accent: Int)

/** IDs are feature contracts, not substring matches of untrusted boss text. */
object MiningBossVisuals {
    private val known = mapOf(
        "double_powder" to MiningBossVisual("Double Powder", "×2", "minecraft:redstone", 0xFFF28E8E.toInt()),
        "better_together" to MiningBossVisual("Better Together", "Together", "minecraft:amethyst_shard", 0xFFD6ABF5.toInt()),
        "gone_with_the_wind" to MiningBossVisual("Gone with the Wind", "Wind", "minecraft:feather", 0xFF9BBEFF.toInt()),
        "goblin_raid" to MiningBossVisual("Goblin Raid", "Raid", "minecraft:iron_sword", 0xFFF2A1A1.toInt()),
        "raffle" to MiningBossVisual("Raffle", "Raffle", "minecraft:name_tag", 0xFFF1CE8A.toInt()),
        "mithril_gourmand" to MiningBossVisual("Mithril Gourmand", "Gourmand", "minecraft:cyan_dye", 0xFF9DE3E9.toInt()),
    )
    fun resolve(id: String): MiningBossVisual? = known[id]
}
