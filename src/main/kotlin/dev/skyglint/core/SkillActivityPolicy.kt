package dev.skyglint.core

import kotlin.math.roundToLong

/**
 * A bounded activity lease inferred only from positive, deduplicated XP observations.
 * Location can shorten contradictory old evidence; it never creates activity or XP.
 */
class SkillActivityPolicy {
    data class Active(val skill: Skill, val gain: Double, val observedAt: Long, val expiresAt: Long)
    private data class History(var lastAt: Long, val gaps: ArrayDeque<Long> = ArrayDeque())
    private data class Focus(val skill: Skill, val gain: Double, val at: Long, val inferredLease: Long, val contextCap: Long? = null)
    private val histories = mutableMapOf<Skill, History>()
    private var focus: Focus? = null
    private var context: Pair<String?, String?>? = null

    fun clear() { histories.clear(); focus = null; context = null }

    /** Returns false for an invalid observation, true even if a secondary skill doesn't take focus. */
    fun observe(skill: Skill, gain: Double, now: Long, baseMillis: Long): Boolean {
        if (!gain.isFinite() || gain <= 0) return false
        val history = histories[skill]
        if (history == null) histories[skill] = History(now)
        else {
            if (now < history.lastAt) return false
            val gap = now - history.lastAt
            if (gap > MAX_SAMPLE_GAP) history.gaps.clear()
            else if (gap > 0) {
                history.gaps.addLast(gap)
                if (history.gaps.size > 4) history.gaps.removeFirst()
            }
            history.lastAt = now
        }
        val current = active(now, baseMillis)
        // Pet XP routinely accompanies the actual trained skill. It is data, not proof that
        // the player abandoned fishing/mining/etc. Other secondary gains obey the same rule.
        if (skill in SECONDARY && current != null && current.skill !in SECONDARY && current.skill != skill) return true
        val gaps = histories.getValue(skill).gaps.sorted()
        val median = if (gaps.isEmpty()) 0L else if (gaps.size % 2 == 0)
            (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2 else gaps[gaps.size / 2]
        val cadenceLease = if (median > 0) (median * 2.5 + 3_000).roundToLong() else 0L
        val floor = if (skill == Skill.FISHING) 45_000L else 0L
        focus = Focus(skill, gain, now, maxOf(cadenceLease, floor).coerceAtMost(MAX_LEASE))
        return true
    }

    fun active(now: Long, baseMillis: Long): Active? {
        val current = focus ?: return null
        val lease = maxOf(baseMillis.coerceIn(10_000, MAX_LEASE), current.inferredLease)
        val expiry = minOf(current.at + lease, current.contextCap ?: Long.MAX_VALUE)
        return if (now >= current.at && now < expiry) Active(current.skill, current.gain, current.at, expiry) else null
    }

    fun observeContext(location: String?, mode: String?, now: Long, baseMillis: Long) {
        val next = location to mode
        val previous = context
        context = next
        if (previous == null || previous == next || previous.second == mode) return
        val compatible = MODE_SKILLS[mode?.lowercase()] ?: return
        val current = focus ?: return
        if (current.skill in compatible) return
        val shortGrace = baseMillis.coerceIn(10_000, 15_000)
        focus = current.copy(contextCap = minOf(current.contextCap ?: Long.MAX_VALUE, now + shortGrace))
    }

    companion object {
        private const val MAX_SAMPLE_GAP = 90_000L
        private const val MAX_LEASE = 120_000L
        private val SECONDARY = setOf(Skill.TAMING, Skill.CARPENTRY, Skill.RUNECRAFTING, Skill.SOCIAL)
        // Conservative known island modes. Fishing remains plausible in mining worlds (worms /
        // lava) and hubs; a location name alone does not prove that fishing has ended.
        private val MODE_SKILLS = mapOf(
            "garden" to setOf(Skill.FARMING, Skill.COMBAT, Skill.HUNTING),
            "mining_1" to setOf(Skill.MINING, Skill.COMBAT, Skill.FISHING),
            "mining_2" to setOf(Skill.MINING, Skill.COMBAT, Skill.FISHING),
            "mining_3" to setOf(Skill.MINING, Skill.COMBAT, Skill.FISHING),
            "foraging_1" to setOf(Skill.FORAGING, Skill.COMBAT, Skill.FISHING, Skill.HUNTING),
            "dungeon" to setOf(Skill.COMBAT, Skill.MINING),
        )
    }
}
