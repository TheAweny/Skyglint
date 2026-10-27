package dev.skyglint.core

/** Conservative readers of normalized server text. Unknown or invalid variants are left untouched. */
object SkyBlockParser {
    private const val NUMBER = "(?:[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)"
    private const val COMPACT = "$NUMBER[kKmMbB]?"
    // Hypixel scoreboard team identifiers also insert §g..§z inside visible words/numbers.
    // They are nonprinting formatting markers in game, not part of an area, amount or name.
    private val formatting = Regex("§[0-9a-z]", RegexOption.IGNORE_CASE)
    private val skillNames = Skill.entries.joinToString("|") { Regex.escape(it.displayName) }
    private val skillGain = Regex("(?<![\\w+])\\+($NUMBER) ($skillNames) \\(([^()]*)\\)")
    private val skyBlockGain = Regex("(?<![\\w+])\\+($NUMBER) SkyBlock XP \\([^()]+\\) \\(([^()]*)\\)")
    private val ratio = Regex("($COMPACT)/($COMPACT)")
    private val percentage = Regex("($NUMBER)%")
    private val skillTab = Regex("($skillNames)(?: ([0-9]+))?: (.+)")
    private val skyBlockTab = Regex("SB Level: \\[([0-9]+)] ($NUMBER)(?:/100)?(?: XP)?(?: .*)?")
    private val petName = Regex("\\[Lvl ([0-9]+)] (?:\\[([0-9]+✦)] )?([\\w -]+?)( ✦)?")
    private val petXp = Regex("($COMPACT)/($COMPACT) XP(?: \\(($NUMBER)%\\))?")
    private val levelHeader = Regex("SKYBLOCK LEVEL UP(?: Level(?: .*)?)?[!]?", RegexOption.IGNORE_CASE)
    private val exactLevelSuffix = Regex("SKYBLOCK LEVEL UP Level ([0-9]+)[!]?")

    fun clean(text: String): String = formatting.replace(text, "").replace('\u00a0', ' ').trim()

    fun actionBar(text: String): List<CoreEvent> {
        val normalized = clean(text)
        if (normalized.length > 8192) return emptyList()
        return buildList {
            skillGain.findAll(normalized).forEach { match ->
                val skill = Skill.fromName(match.groupValues[2]) ?: return@forEach
                val gain = number(match.groupValues[1])?.takeIf { it > 0 } ?: return@forEach
                val rawProgress = match.groupValues[3]
                // A zero denominator is used for overflow XP: valid gain, unknown level progress.
                if (parseProgress(rawProgress) == null && !isOverflow(rawProgress)) return@forEach
                add(CoreEvent.SkillGain(skill, gain, parseProgress(rawProgress), match.value))
            }
            skyBlockGain.findAll(normalized).forEach { match ->
                val gain = number(match.groupValues[1])?.takeIf { it > 0 } ?: return@forEach
                val progress = parseProgress(match.groupValues[2]) ?: return@forEach
                // SkyBlock levels use 100 XP; never reinterpret another denominator as this track.
                if (progress.maximum != 100.0) return@forEach
                add(CoreEvent.SkyBlockGain(gain, progress, match.value))
            }
        }
    }

    fun chat(text: String): List<CoreEvent> {
        val line = clean(text)
        if (line.length > 512 || !levelHeader.matches(line)) return emptyList()
        // Upstream confirms the header, but not every suffix. Unknown suffix stays unknown.
        val level = exactLevelSuffix.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
        return listOf(CoreEvent.SkyBlockLevelUp(line, level))
    }

    fun tabLines(lines: List<String>): List<CoreEvent> {
        val normalized = lines.take(256).map(::clean)
        return buildList {
            normalized.forEachIndexed { index, line ->
                skyBlockTab.matchEntire(line)?.let { match ->
                    val level = match.groupValues[1].toIntOrNull()
                    val xp = number(match.groupValues[2])
                    if (level != null && xp != null && xp >= 0 && xp < 100) {
                        add(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(level, ProgressSnapshot(xp, 100.0, xp / 100))))
                    }
                }
                skillTab.matchEntire(line)?.let { match ->
                    val skill = Skill.fromName(match.groupValues[1]) ?: return@let
                    val level = match.groupValues[2].toIntOrNull()
                    val progress = if (match.groupValues[3] == "MAX") ProgressSnapshot(null, null, 1.0)
                    else parseProgress(match.groupValues[3]) ?: return@let
                    add(CoreEvent.SkillObserved(SkillSnapshot(skill, level, progress)))
                }
                if (line == "Pet:") {
                    val next = normalized.getOrNull(index + 1) ?: return@forEachIndexed
                    if (next == "No pet selected") {
                        add(CoreEvent.PetObserved(null))
                        return@forEachIndexed
                    }
                    petName.matchEntire(next)?.let { match ->
                        val level = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return@let
                        val xpLine = normalized.getOrNull(index + 2)
                        val progress = when {
                            xpLine == "MAX LEVEL" -> ProgressSnapshot(null, null, 1.0)
                            xpLine != null -> petXp.matchEntire(xpLine)?.let { xp ->
                                val exactRatio = parseProgress("${xp.groupValues[1]}/${xp.groupValues[2]}")
                                // Rounded compact denominators should not override the explicit percent.
                                val fraction = xp.groupValues[3].takeIf(String::isNotEmpty)?.let { parseProgress("$it%") }
                                if (exactRatio != null && fraction != null) exactRatio.copy(fraction = fraction.fraction) else exactRatio
                            }
                            else -> null
                        }
                        add(CoreEvent.PetObserved(PetSnapshot(match.groupValues[3], level, progress,
                            match.groupValues[2].isNotEmpty() || match.groupValues[4].isNotEmpty())))
                    }
                }
            }
        }
    }

    fun parseProgress(text: String): ProgressSnapshot? {
        percentage.matchEntire(text)?.let {
            val value = number(it.groupValues[1])?.takeIf { n -> n in 0.0..100.0 } ?: return null
            return ProgressSnapshot(null, null, value / 100)
        }
        ratio.matchEntire(text)?.let {
            val current = number(it.groupValues[1]) ?: return null
            val maximum = number(it.groupValues[2])?.takeIf { n -> n > 0 } ?: return null
            if (current > maximum) return null
            return ProgressSnapshot(current, maximum, current / maximum)
        }
        return null
    }

    private fun isOverflow(text: String): Boolean = ratio.matchEntire(text)?.let {
        number(it.groupValues[1]) != null && number(it.groupValues[2]) == 0.0
    } ?: false

    private fun number(text: String): Double? {
        val suffix = text.lastOrNull()?.lowercaseChar()
        val multiplier = when (suffix) { 'k' -> 1_000.0; 'm' -> 1_000_000.0; 'b' -> 1_000_000_000.0; else -> 1.0 }
        val raw = if (multiplier == 1.0) text else text.dropLast(1)
        return raw.replace(",", "").toDoubleOrNull()?.times(multiplier)?.takeIf { it.isFinite() && it >= 0 }
    }
}
