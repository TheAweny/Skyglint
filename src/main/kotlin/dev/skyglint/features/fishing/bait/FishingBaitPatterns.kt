package dev.skyglint.features.fishing.bait

import dev.skyglint.core.MenuItemSnapshot
import dev.skyglint.core.SkyBlockParser

/** Server bait-preview lore, never a sum of inventory stacks or a per-cast consumption estimate. */
internal object FishingBaitPatterns {
    data class Selected(val itemId: String, val name: String, val remaining: Long)

    private const val INTEGER = "(?:[1-9][0-9]{0,2}(?:,[0-9]{3})+|0|[1-9][0-9]{0,8})"
    private val remaining = Regex("Bait Remaining: ($INTEGER)")
    private val category = Regex("(?:a )?(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL) BAIT(?: a)?")
    private val itemId = Regex("[A-Z0-9_]{1,80}")
    private val obfuscated = Regex("OBFUSCATED_FISH_[12]_(?:BRONZE|SILVER|GOLD|DIAMOND)")

    fun selected(item: MenuItemSnapshot?): Selected? {
        if (item == null || item.slot != 8 || item.name.length > 256 || item.name.any(Char::isISOControl) || item.lore.size > 128) return null
        val id = item.skyBlockId?.takeIf(itemId::matches) ?: return null
        val name = SkyBlockParser.clean(item.name).takeIf { it.isNotBlank() && it.length <= 120 } ?: return null
        val lines = item.lore.map { if (it.length <= 1_024 && it.none(Char::isISOControl)) SkyBlockParser.clean(it) else return null }
        if (!obfuscated.matches(id) && lines.none(category::matches)) return null
        // Duplicate/conflicting readings are not one exact absolute observation.
        val readings = lines.filter { it.startsWith("Bait Remaining:") }
        if (readings.size != 1) return null
        val value = remaining.matchEntire(readings.single())?.groupValues?.get(1)
            ?.replace(",", "")?.toLongOrNull()?.takeIf { it in 0..999_999_999 } ?: return null
        return Selected(id, name, value)
    }

    /** Explicit opposite confirmation is required; missing text never means disabled. */
    fun sackEnabled(text: String): Boolean? {
        if (text.length > 256 || text.any(Char::isISOControl)) return null
        return when (SkyBlockParser.clean(text)) {
            "Use Baits From Sacks are now disabled!" -> false
            "Use Baits From Sacks are now enabled!" -> true
            else -> null
        }
    }
}
