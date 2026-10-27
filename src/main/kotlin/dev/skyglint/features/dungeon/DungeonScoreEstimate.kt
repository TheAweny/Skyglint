package dev.skyglint.features.dungeon

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Independent arithmetic model of the documented score categories. Never substitutes unknown inputs. */
internal object DungeonScoreEstimate {
    data class Input(val floor: String, val cleared: Int, val elapsedSeconds: Int, val rooms: Int,
                     val secretsPercent: Double, val crypts: Int, val deaths: Int, val puzzlePenalty: Int)
    data class Estimate(val minimum: Int, val maximum: Int, val minimumRooms: Int, val maximumRooms: Int) {
        val caption: String get() {
            val lowRank = DungeonScore.rank(minimum)
            val highRank = DungeonScore.rank(maximum)
            val ranks = if (lowRank == highRank) lowRank else "$lowRank–$highRank"
            return "~$minimum–$maximum · $ranks"
        }
    }
    // Secrets percentage required for all exploration points; time allowance in seconds.
    private val requirements = mapOf("E" to (30 to 1200), "F1" to (30 to 600), "F2" to (40 to 600),
        "F3" to (50 to 600), "F4" to (60 to 720), "F5" to (70 to 600), "F6" to (85 to 720),
        "F7" to (100 to 840), "M1" to (100 to 480), "M2" to (100 to 480), "M3" to (100 to 480),
        "M4" to (100 to 480), "M5" to (100 to 480), "M6" to (100 to 600), "M7" to (100 to 840))

    fun calculate(input: Input): Estimate? {
        val (secretsRequired, timeAllowance) = requirements[input.floor] ?: return null
        if (input.cleared !in 1..100 || input.rooms !in 1..999 || input.elapsedSeconds !in 0..86_400 ||
            !input.secretsPercent.isFinite() || input.secretsPercent !in 0.0..100.0 || input.crypts !in 0..999 ||
            input.deaths !in 0..999 || input.puzzlePenalty !in 0..100 || input.puzzlePenalty % 10 != 0) return null
        // Include both rounding endpoints conservatively. Do not pick one guessed room count.
        val firstRoom = maxOf(input.rooms, ceil(100.0 * input.rooms / (input.cleared + .5)).toInt())
        val lastRoom = floor(100.0 * input.rooms / (input.cleared - .5)).toInt()
        if (firstRoom > lastRoom) return null // asynchronous/inconsistent sidebar and tab
        val roomSkillLow = (80L * input.rooms / lastRoom).toInt().coerceIn(0, 80)
        val roomSkillHigh = (80L * input.rooms / firstRoom).toInt().coerceIn(0, 80)
        val roomExploreLow = (60L * input.rooms / lastRoom).toInt().coerceIn(0, 60)
        val roomExploreHigh = (60L * input.rooms / firstRoom).toInt().coerceIn(0, 60)
        val deathsLow = maxOf(0, input.deaths * 2 - if (input.deaths > 0) 1 else 0) // Spirit unknown
        val deathsHigh = input.deaths * 2
        val skillLow = 20 + (roomSkillLow - input.puzzlePenalty - deathsHigh).coerceIn(0, 80)
        val skillHigh = 20 + (roomSkillHigh - input.puzzlePenalty - deathsLow).coerceIn(0, 80)
        // The tab percentage can be rounded; ±0.5% encloses all currently accepted display precision.
        fun secretPoints(percent: Double) = floor(40.0 * minOf(percent.coerceIn(0.0, 100.0), secretsRequired.toDouble()) / secretsRequired).toInt().coerceIn(0, 40)
        val secretsLow = secretPoints(input.secretsPercent - .5)
        val secretsHigh = secretPoints(input.secretsPercent + .5)
        val time = timePoints(input.elapsedSeconds, timeAllowance)
        val knownBonus = minOf(input.crypts, 5)
        // No mayor/profile/world requests: Paul 0..10, prince 0..1, bat 0..1, mimic F6/7/M6/7 0..2.
        val unknownBonus = 12 + if (input.floor in setOf("F6", "F7", "M6", "M7")) 2 else 0
        fun total(skill: Int, exploration: Int, bonus: Int): Int =
            if (input.floor == "E") listOf(skill, exploration, time, bonus).sumOf { (it * .7).roundToInt() }
            else skill + exploration + time + bonus
        return Estimate(total(skillLow, roomExploreLow + secretsLow, knownBonus),
            total(skillHigh, roomExploreHigh + secretsHigh, knownBonus + unknownBonus), firstRoom, lastRoom)
    }

    private fun timePoints(seconds: Int, allowance: Int): Int {
        if (seconds <= allowance) return 100
        val over = 100.0 * (seconds - allowance) / allowance
        val band = listOf(Triple(60.0, 18 + 2.0 / 3, 7.0), Triple(50.0, 17.0, 6.0),
            Triple(40.0, 15.0, 5.0), Triple(20.0, 10.0, 4.0), Triple(0.0, 0.0, 2.0)).first { over >= it.first }
        val penalty = floor(band.second + (over - band.first) / band.third).toInt()
        return (100 - penalty).coerceIn(0, 100)
    }
}
