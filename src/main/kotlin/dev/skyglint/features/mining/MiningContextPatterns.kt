package dev.skyglint.features.mining

import dev.skyglint.core.SkyBlockParser
import java.util.Locale

/** Exact copied tab/sidebar values, not mining intent or a local resource accumulator. */
internal object MiningContextPatterns {
    data class Commission(val name: String, val percent: Double, val done: Boolean, val caption: String)
    data class Tab(val commissions: List<Commission>, val powders: Map<String, Long>)
    data class Environment(val cold: Int? = null, val heat: String? = null)
    private val commission = Regex("([^:\\p{Cntrl}]{1,72}): (DONE|[0-9]{1,3}(?:\\.[0-9]{1,2})?%)")
    private val powder = Regex("(Mithril|Gemstone|Glacite): ([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{1,15})")
    private val cold = Regex("Cold: (-?[0-9]{1,3})❄")
    private val heat = Regex("Heat: (IMMUNE|[0-9]{1,3}♨)")

    fun tab(raw: List<String>): Tab {
        val lines = raw.take(256).map(SkyBlockParser::clean)
        val commissions = mutableListOf<Commission>()
        val rows = section(lines, "Commissions:")
        var invalid = rows.size > 12
        for (line in rows.take(12)) {
            val match = commission.matchEntire(line)
            if (match == null) { invalid = true; break }
            val name = match.groupValues[1].trim()
            val value = match.groupValues[2]
            val done = value == "DONE"
            val percent = if (done) 100.0 else value.removeSuffix("%").toDoubleOrNull()
            if (name.isEmpty() || name.contains('§') || percent == null || percent !in 0.0..100.0) { invalid = true; break }
            commissions += Commission(name, percent, done, value)
        }
        if (commissions.map { it.name.lowercase(Locale.ROOT) }.toSet().size != commissions.size) invalid = true
        val powders = linkedMapOf<String, Long>()
        val powderRows = section(lines, "Powders:")
        var invalidPowder = powderRows.size > 3
        for (line in powderRows.take(4)) {
            val match = powder.matchEntire(line)
            val kind = match?.groupValues?.get(1)
            val amount = match?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull()
            if (kind == null || amount == null || kind in powders || amount !in 0..1_000_000_000_000L) {
                invalidPowder = true; break
            }
            powders[kind] = amount
        }
        return Tab(if (invalid) emptyList() else commissions.toList(), if (invalidPowder) emptyMap() else powders.toMap())
    }

    fun environment(raw: List<String>): Environment {
        val lines = raw.take(64).map(SkyBlockParser::clean)
        val coldRows = lines.filter { it.startsWith("Cold:") }
        val heatRows = lines.filter { it.startsWith("Heat:") }
        val coldValue = coldRows.singleOrNull()?.let(cold::matchEntire)?.groupValues?.get(1)?.toIntOrNull()
        val heatValue = heatRows.singleOrNull()?.let(heat::matchEntire)?.groupValues?.get(1)?.takeIf {
            it == "IMMUNE" || it.removeSuffix("♨").toIntOrNull()?.let { value -> value in 0..100 } == true
        }
        return Environment(coldValue, heatValue)
    }

    private fun section(lines: List<String>, header: String): List<String> {
        val start = lines.indexOf(header)
        if (start < 0) return emptyList()
        return lines.drop(start + 1).take(13).takeWhile { it.isNotEmpty() && it !in SECTION_HEADERS }
    }
    private val SECTION_HEADERS = setOf("Commissions:", "Powders:", "Crystals:", "Forges:", "Forge:",
        "Skills:", "Stats:", "Pet:", "Profile:", "Events:")
}
