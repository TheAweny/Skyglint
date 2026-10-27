package dev.skyglint.features.mining

import dev.skyglint.core.SkyBlockParser
import java.util.Locale

/** Server text grammar audited at SkyHanni 893cc826 MiningEventsApi; no guessed event durations. */
object MiningEventPatterns {
    enum class Type(val id: String, val title: String, val shortLabel: String, val icon: String) {
        DOUBLE_POWDER("double_powder", "Double Powder", "×2", "minecraft:redstone"),
        BETTER_TOGETHER("better_together", "Better Together", "Together", "minecraft:amethyst_shard"),
        GONE_WITH_THE_WIND("gone_with_the_wind", "Gone with the Wind", "Wind", "minecraft:feather"),
        GOBLIN_RAID("goblin_raid", "Goblin Raid", "Raid", "minecraft:iron_sword"),
        RAFFLE("raffle", "Raffle", "Raffle", "minecraft:name_tag"),
        MITHRIL_GOURMAND("mithril_gourmand", "Mithril Gourmand", "Gourmand", "minecraft:cyan_dye"),
    }
    data class Event(val type: Type, val remainingMillis: Long, val location: String? = null)
    private val passive = Regex("PASSIVE EVENT (2X POWDER|BETTER TOGETHER|GONE WITH THE WIND) RUNNING FOR ([0-9]{1,2}:[0-5][0-9])")
    private val active = Regex("EVENT (GOBLIN RAID|RAFFLE|MITHRIL GOURMAND) ACTIVE IN (.{1,80}) for ([0-9]{1,2}:[0-5][0-9])")
    private val tabHeader = Regex("Mining Event: (2x Powder|Better Together|Gone with the Wind|Goblin Raid|Raffle|Mithril Gourmand)")
    private val tabDuration = Regex("Ends in: ((?:[0-9]{1,2}h ?)?(?:[0-9]{1,2}m ?)?(?:[0-9]{1,2}s)?)")
    private val unit = Regex("([0-9]{1,2})([hms])")
    private val ended = Regex("(2X POWDER|BETTER TOGETHER|GONE WITH THE WIND|GOBLIN RAID|RAFFLE|MITHRIL GOURMAND) ENDED!")

    fun parseBoss(title: String): Event? {
        val line = clean(title) ?: return null
        passive.matchEntire(line)?.let { match -> return Event(type(match.groupValues[1])!!, clockDuration(match.groupValues[2])) }
        active.matchEntire(line)?.let { match ->
            val location = match.groupValues[2].takeIf(String::isNotBlank) ?: return null
            return Event(type(match.groupValues[1])!!, clockDuration(match.groupValues[3]), location)
        }
        return null
    }

    fun parseTab(lines: List<String>): Event? {
        val safe = lines.take(256).map { clean(it).orEmpty() }
        for (index in safe.indices) {
            val header = tabHeader.matchEntire(safe[index]) ?: continue
            val duration = safe.getOrNull(index + 1)?.let(tabDuration::matchEntire) ?: continue
            val text = duration.groupValues[1].replace(" ", "")
            if (text.isEmpty()) continue
            var seconds = 0L
            var consumed = 0
            for (token in unit.findAll(text)) {
                if (token.range.first != consumed) return null
                val number = token.groupValues[1].toLong()
                if (token.groupValues[2] != "h" && number >= 60) return null
                seconds += number * when (token.groupValues[2]) { "h" -> 3_600; "m" -> 60; else -> 1 }
                consumed = token.range.last + 1
            }
            if (consumed != text.length || seconds > MAX_SECONDS) continue
            return Event(type(header.groupValues[1])!!, seconds * 1_000)
        }
        return null
    }

    fun tabNotAnnounced(lines: List<String>): Boolean = lines.take(256).any { clean(it) == "Mining Event: Not announced" }
    fun endedType(text: String): Type? = clean(text)?.let(ended::matchEntire)?.groupValues?.get(1)?.let(::type)
    fun signature(title: String): String = SkyBlockParser.clean(title)
    private fun clean(text: String): String? = text.takeIf { it.length <= 1_024 && it.none(Char::isISOControl) }?.let(SkyBlockParser::clean)
    private fun type(name: String): Type? = when (name.uppercase(Locale.ROOT)) {
        "2X POWDER" -> Type.DOUBLE_POWDER; "BETTER TOGETHER" -> Type.BETTER_TOGETHER
        "GONE WITH THE WIND" -> Type.GONE_WITH_THE_WIND; "GOBLIN RAID" -> Type.GOBLIN_RAID
        "RAFFLE" -> Type.RAFFLE; "MITHRIL GOURMAND" -> Type.MITHRIL_GOURMAND; else -> null
    }
    private fun clockDuration(text: String): Long {
        val (minutes, seconds) = text.split(':').map(String::toLong)
        return (minutes * 60 + seconds) * 1_000
    }
    private const val MAX_SECONDS = 99 * 60 + 59L
}
