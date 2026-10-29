package dev.skyglint.features.buffs

import dev.skyglint.core.MenuItemSnapshot
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.SkyBlockParser

/** Server text forms verified in pinned upstream sources; no clock or missing-field inference. */
internal object BuffExpiryPatterns {
    enum class Buff(val key: String, val title: String, val icon: String) {
        COOKIE("cookie", "Booster Cookie", "minecraft:cookie"),
        GOD_POTION("god_potion", "God Potion", "minecraft:potion"),
    }
    enum class Status { ACTIVE, INACTIVE }
    data class Observation(val buff: Buff, val status: Status, val consumed: Boolean = false,
                           val remainingMillis: Long? = null)

    private val durationToken = Regex("(\\d+)\\s*(days?|hours?|minutes?|seconds?|[dhms])", RegexOption.IGNORE_CASE)
    private val godFooter = Regex("You have a God Potion active! (.+)")
    private val godWidget = Regex("God Potion: (.+)")
    private val godDrink = Regex("(?:GULP|SIP|SLURP)! The God Potion grants you powers for ([0-9dhms ]+)!")
    private val cookieDrink = Regex("You consumed a Booster Cookie!(?: (?:Yummy|Divine)!)?")
    private val effectsMenu = Regex("(?:\\(\\d+/\\d+\\) )?Active Effects")
    private val remaining = Regex("Remaining: (\\d+):(\\d{2}):(\\d{2})")

    fun tab(raw: List<String>): List<Observation> {
        val lines = raw.map(::clean)
        val result = mutableListOf<Observation>()
        lines.forEachIndexed { index, line ->
            if (line == "Cookie Buff") {
                val next = lines.getOrNull(index + 1)
                when {
                    next == "Not active! Obtain booster cookies from the community" ->
                        result += Observation(Buff.COOKIE, Status.INACTIVE)
                    next != null -> positiveDuration(next)?.let {
                        result += Observation(Buff.COOKIE, Status.ACTIVE, remainingMillis = it)
                    }
                }
            }
            val godTime = godFooter.matchEntire(line)?.groupValues?.get(1)
                ?: godWidget.matchEntire(line)?.groupValues?.get(1)
            godTime?.let(::positiveDuration)?.let {
                result += Observation(Buff.GOD_POTION, Status.ACTIVE, remainingMillis = it)
            }
            if (line == "Active Effects" &&
                lines.getOrNull(index + 1) == "No effects active. Drink Potions or splash them on the" &&
                lines.getOrNull(index + 2) == "ground to buff yourself!") {
                result += Observation(Buff.GOD_POTION, Status.INACTIVE)
            }
        }
        return unambiguous(result)
    }

    fun menu(signal: NormalizedSignal.MenuSnapshot): List<Observation> {
        val title = clean(signal.title)
        val result = mutableListOf<Observation>()
        if (title in setOf("SkyBlock Menu", "Booster Cookie", "Community Shop")) {
            signal.items.filter { clean(it.name) == "Booster Cookie" }.forEach { item ->
                val lore = item.lore.map(::clean)
                lore.filter { it.startsWith("Duration: ") }.mapNotNull { positiveDuration(it.removePrefix("Duration: ")) }
                    .distinct().singleOrNull()?.let { result += Observation(Buff.COOKIE, Status.ACTIVE, remainingMillis = it) }
                val inactive = if (title == "SkyBlock Menu") "Status: Not active!" in lore else
                    lore.windowed(2).any { it == listOf("You do not currently have a", "Booster Cookie active!") }
                if (inactive) result += Observation(Buff.COOKIE, Status.INACTIVE)
            }
        }
        if (effectsMenu.matches(title) && signal.items.any { clean(it.name) == "Filter" &&
                it.lore.any { line -> clean(line) == "▶ God Potion Effects" } }) {
            // Only a supplied timer is positive evidence. A missing slot/effect is deliberately unknown.
            signal.items.firstOrNull { it.slot == 10 }?.let { item ->
                positiveRemaining(item)?.let { result += Observation(Buff.GOD_POTION, Status.ACTIVE, remainingMillis = it) }
            }
        }
        return unambiguous(result)
    }

    fun chat(raw: String): Observation? {
        val text = clean(raw)
        if (cookieDrink.matches(text)) return Observation(Buff.COOKIE, Status.ACTIVE, consumed = true)
        val time = godDrink.matchEntire(text)?.groupValues?.get(1) ?: return null
        // Consumption reports added duration, not an absolute remaining snapshot.
        return Observation(Buff.GOD_POTION, Status.ACTIVE, consumed = true).takeIf { positiveDuration(time) != null }
    }

    private fun positiveRemaining(item: MenuItemSnapshot): Long? = item.lore.mapNotNull { line ->
        val match = remaining.matchEntire(clean(line)) ?: return@mapNotNull null
        val hours = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val minutes = match.groupValues[2].toInt()
        val seconds = match.groupValues[3].toInt()
        if (minutes >= 60 || seconds >= 60) return@mapNotNull null
        try { Math.addExact(Math.multiplyExact(hours, 3_600_000), minutes * 60_000L + seconds * 1_000L).takeIf { it > 0 } }
        catch (_: ArithmeticException) { null }
    }.distinct().singleOrNull()

    private fun positiveDuration(text: String): Long? {
        val matches = durationToken.findAll(text).toList()
        if (matches.isEmpty()) return null
        // Actual Cookie footer: "6 days, 3 hours". Commas separate units, never digits.
        var cursor = 0
        for ((index, match) in matches.withIndex()) {
            val separator = text.substring(cursor, match.range.first)
            if (index == 0 && separator.isNotBlank()) return null
            if (index > 0 && !separator.matches(Regex("\\s*(?:,\\s*)?"))) return null
            cursor = match.range.last + 1
        }
        if (text.substring(cursor).isNotBlank()) return null
        return try {
            matches.fold(0L) { total, match ->
                val amount = match.groupValues[1].toLongOrNull() ?: return null
                val unit = when (match.groupValues[2].lowercase().first()) {
                    'd' -> 86_400_000L; 'h' -> 3_600_000L; 'm' -> 60_000L; else -> 1_000L
                }
                Math.addExact(total, Math.multiplyExact(amount, unit))
            }.takeIf { it > 0 }
        } catch (_: ArithmeticException) { null }
    }

    private fun unambiguous(values: List<Observation>): List<Observation> = values.groupBy { it.buff }
        .values.mapNotNull { same -> same.distinct().singleOrNull() }

    private fun clean(text: String): String = SkyBlockParser.clean(text).trim()
}
