package dev.skyglint.features.mining

import dev.skyglint.core.MenuItemSnapshot
import dev.skyglint.core.SkyBlockParser

/** Tool lore is an absolute observation, independent of mining XP and composter fuel. */
internal object MiningToolPatterns {
    private const val INTEGER = "(?:[0-9]{1,3}(?:,[0-9]{3})*|[0-9]{1,9})"
    private val fuel = Regex("Fuel: ($INTEGER)/($INTEGER)(k?)")
    private val drillType = Regex("(?:a )?(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL) DRILL(?: a)?")
    data class Fuel(val toolName: String, val current: Long, val capacity: Long) {
        val fraction: Double get() = current.toDouble() / capacity
        val ratio: String get() = "${compact(current)}/${compact(capacity)}"
    }
    fun fuel(item: MenuItemSnapshot?): Fuel? {
        if (item == null || item.name.length > 256 || item.lore.size > 128) return null
        val name = SkyBlockParser.clean(item.name).takeIf { it.isNotBlank() } ?: return null
        val lines = item.lore.map { if (it.length <= 1_024) SkyBlockParser.clean(it) else return null }
        val trustedDrill = item.drillFuel != null && item.skyBlockId?.let {
            Regex("[A-Z0-9_]{1,80}").matches(it) && "DRILL" in it
        } == true
        if (!trustedDrill && lines.none(drillType::matches)) return null
        val readings = lines.mapNotNull { fuel.matchEntire(it) }
        if (readings.size != 1) return null
        val match = readings.single()
        val current = match.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val rawCapacity = match.groupValues[2].replace(",", "").toLongOrNull() ?: return null
        val capacity = rawCapacity * if (match.groupValues[3] == "k") 1_000 else 1
        if (capacity !in 1..1_000_000_000 || current !in 0..capacity) return null
        if (item.drillFuel != null && item.drillFuel != current) return null // asynchronous lore/customdata cannot provide one exact ratio
        return Fuel(name, current, capacity)
    }
    private fun compact(value: Long): String = if (value >= 1_000 && value % 1_000 == 0L) "${value / 1_000}k" else value.toString()
}
