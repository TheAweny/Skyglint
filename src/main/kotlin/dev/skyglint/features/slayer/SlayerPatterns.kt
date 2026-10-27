package dev.skyglint.features.slayer

import dev.skyglint.core.ProgressSnapshot
import dev.skyglint.core.SkyBlockParser

/** Server-string grammar only. Source provenance and deliberate limits are in this package's README. */
object SlayerPatterns {
    enum class Type(val displayName: String, val icon: String, val maximumTier: Int) {
        REVENANT("Revenant Horror", "minecraft:rotten_flesh", 5),
        TARANTULA("Tarantula Broodfather", "minecraft:spider_eye", 5),
        SVEN("Sven Packmaster", "minecraft:bone", 4),
        VOIDGLOOM("Voidgloom Seraph", "minecraft:ender_pearl", 4),
        INFERNO("Inferno Demonlord", "minecraft:blaze_rod", 4),
        BLOODFIEND("Bloodfiend", "minecraft:redstone", 5),
    }

    enum class Phase { GRINDING, FIGHT, SLAIN }
    enum class Outcome { SLAIN, COMPLETE, FAILED, CANCELLED }
    enum class Unit(val label: String) { COMBAT_XP("Combat XP"), KILLS("Kills") }
    data class Quest(val type: Type, val tier: Int, val name: String) {
        val label: String get() = "$name ${ROMAN[tier - 1]}"
    }
    data class Progress(val current: Double, val total: Double, val unit: Unit, val caption: String) {
        val snapshot: ProgressSnapshot get() = ProgressSnapshot(current, total, current / total)
    }
    data class Sidebar(val quest: Quest, val phase: Phase?, val progress: Progress?)

    private val names = mapOf(
        "Revenant Horror" to Type.REVENANT, "Atoned Horror" to Type.REVENANT,
        "Tarantula Broodfather" to Type.TARANTULA, "Conjoined Brood" to Type.TARANTULA,
        "Sven Packmaster" to Type.SVEN, "Voidgloom Seraph" to Type.VOIDGLOOM,
        "Inferno Demonlord" to Type.INFERNO, "Bloodfiend" to Type.BLOODFIEND,
        "Riftstalker Bloodfiend" to Type.BLOODFIEND,
    )
    private val questPattern = Regex("(${names.keys.joinToString("|") { Regex.escape(it) }}) (III|IV|II|V|I)")
    private const val NUMBER = "(?:[0-9]{1,9}|[0-9]{1,3}(?:,[0-9]{3}){1,2})(?:\\.[0-9]{1,3})?k?"
    private val xpPattern = Regex("\\(($NUMBER)/($NUMBER)\\) Combat XP")
    private val killsPattern = Regex("($NUMBER)/($NUMBER) Kills")

    /** Requires the complete adjacent header/name/stage block, never a name elsewhere in the sidebar. */
    fun sidebar(raw: List<String>): Sidebar? {
        if (raw.size > 64) return null
        val lines = raw.map { line(it) ?: return null }
        val index = lines.indexOf("Slayer Quest")
        if (index < 0 || lines.count { it == "Slayer Quest" } != 1) return null
        val match = questPattern.matchEntire(lines.getOrNull(index + 1) ?: return null) ?: return null
        val name = match.groupValues[1]
        val type = names.getValue(name)
        val tier = ROMAN.indexOf(match.groupValues[2]) + 1
        if (tier !in 1..type.maximumTier) return null
        val quest = Quest(type, tier, name)
        val stage = lines.getOrNull(index + 2) ?: return Sidebar(quest, null, null)
        val progress = progress(stage)
        val phase = when {
            progress != null -> Phase.GRINDING
            stage == "Slay the boss!" -> Phase.FIGHT
            stage == "Boss slain!" -> Phase.SLAIN
            else -> null
        }
        return Sidebar(quest, phase, progress)
    }

    fun progress(raw: String): Progress? {
        val clean = line(raw) ?: return null
        val xp = xpPattern.matchEntire(clean)
        val match = xp ?: killsPattern.matchEntire(clean) ?: return null
        val unit = if (xp != null) Unit.COMBAT_XP else Unit.KILLS
        val current = number(match.groupValues[1]) ?: return null
        val total = number(match.groupValues[2]) ?: return null
        if (total <= 0 || current > total) return null
        if (unit == Unit.KILLS && (current % 1.0 != 0.0 || total % 1.0 != 0.0)) return null
        return Progress(current, total, unit, "${match.groupValues[1]}/${match.groupValues[2]} ${unit.label}")
    }

    fun started(raw: String): Boolean = line(raw) == "SLAYER QUEST STARTED!"
    fun outcome(raw: String): Outcome? = when (line(raw)) {
        "NICE! SLAYER BOSS SLAIN!" -> Outcome.SLAIN
        "SLAYER QUEST COMPLETE!" -> Outcome.COMPLETE
        "SLAYER QUEST FAILED!" -> Outcome.FAILED
        "Your Slayer Quest has been cancelled!" -> Outcome.CANCELLED
        else -> null
    }

    private fun number(text: String): Double? = text.removeSuffix("k").replace(",", "").toDoubleOrNull()
        ?.times(if (text.endsWith('k')) 1_000.0 else 1.0)?.takeIf { it.isFinite() && it in 0.0..1_000_000_000.0 }
    private fun line(raw: String): String? = raw.takeIf { it.length <= 256 && it.none(Char::isISOControl) }
        ?.let(SkyBlockParser::clean)
    private val ROMAN = listOf("I", "II", "III", "IV", "V")
}
