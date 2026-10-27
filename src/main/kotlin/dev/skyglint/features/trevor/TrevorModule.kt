package dev.skyglint.features.trevor

import dev.skyglint.core.*

/**
 * Associates exact server outcomes with a recently observed Trevor assignment.
 * Hidden association state only: no idle dashboard, inferred kill/reward or ready timer.
 *
 * Server text evidence: SkyHanni 893cc82630895a5e735ec6dd3f54986c1eb2a8b2,
 * TrevorFeatures.kt L58-64, L93-100; IslandType.kt L23 confirms official farming_1 mode.
 * The retention bound below is defensive source policy, not a server quest duration.
 */
class TrevorModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.trevor"

    private data class Assignment(val rarity: String, val area: String)
    private data class Hunt(val assignment: Assignment, val observedAt: Long, val sequence: Long)

    private var contextAt = Long.MIN_VALUE
    private var eligible = false
    private var hunt: Hunt? = null
    private var sequence = 0L
    private var lastClosedAssignment: Assignment? = null
    private var closedAt = Long.MIN_VALUE

    // Bounded full-line grammar; player-chat prefixes and added suffixes cannot match.
    private val assignmentPattern = Regex(
        "\\[NPC] Trevor: You can find your (TRACKABLE|UNTRACKABLE|UNDETECTED|ENDANGERED|ELUSIVE) animal near the ([A-Za-z][A-Za-z '\\-]{0,63})\\.",
    )

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            // A gap in context validity cannot revive an old association on the same island.
            if (!fresh(contextAt, now, CONTEXT_LEASE)) clearHunt()
            eligible = signal.mode == "farming_1"
            contextAt = now
            if (!eligible) clearHunt()
            expire(now)
            return emptyList()
        }
        if (!eligible || !fresh(contextAt, now, CONTEXT_LEASE)) {
            clearHunt()
            return emptyList()
        }
        expire(now)
        if (signal !is NormalizedSignal.Chat || signal.text.length > 256 || signal.text.any(Char::isISOControl))
            return emptyList()
        val line = SkyBlockParser.clean(signal.text)
        assignmentPattern.matchEntire(line)?.let { match ->
            val assignment = Assignment(match.groupValues[1], match.groupValues[2])
            // Source-known areas only; do not turn arbitrary NPC-looking text into an association.
            if (assignment.area !in TARGET_AREAS) return emptyList()
            if (hunt?.assignment == assignment) return emptyList() // repeats do not renew original age
            if (lastClosedAssignment == assignment && fresh(closedAt, now, REPLAY_GUARD)) return emptyList()
            hunt = Hunt(assignment, now, sequence++)
            return emptyList()
        }
        val outcome = when (line) {
            "Return to the Trapper soon to get a new animal to hunt!" -> "ended"
            "You ran out of time and the animal disappeared!" -> "timeout"
            else -> return emptyList()
        }
        val current = hunt ?: return emptyList()
        hunt = null
        lastClosedAssignment = current.assignment
        closedAt = now
        val timedOut = outcome == "timeout"
        return listOf(CoreEvent.NoticeObserved(NoticeRequest(
            id = "$id:${current.sequence}:$outcome",
            title = if (timedOut) "Trevor hunt timed out" else "Trevor hunt completed",
            priority = 50,
            durationMillis = 4_000,
            freshnessMillis = 5_000,
            item = ItemVisualHint(vanillaItemId = if (timedOut) "minecraft:clock" else "minecraft:compass"),
        )))
    }

    private fun expire(now: Long) {
        val current = hunt ?: return
        if (!fresh(current.observedAt, now, ASSOCIATION_LEASE)) {
            hunt = null // expiry is unknown, never a timeout confirmation
            lastClosedAssignment = current.assignment
            closedAt = now
        }
    }

    private fun clearHunt() {
        hunt = null
        lastClosedAssignment = null
        closedAt = Long.MIN_VALUE
    }

    private fun fresh(at: Long, now: Long, lease: Long) = at != Long.MIN_VALUE && now >= at && now - at < lease
    private fun reset() { eligible = false; contextAt = Long.MIN_VALUE; sequence = 0; clearHunt() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        private const val CONTEXT_LEASE = 5_000L
        private const val REPLAY_GUARD = 5_000L
        private const val ASSOCIATION_LEASE = 15 * 60_000L
        // Area evidence: same pinned source, TrapperMobArea.kt L6-11; no coordinates used.
        private val TARGET_AREAS = setOf("Oasis", "Mushroom Gorge", "Overgrown Mushroom Cave",
            "Desert Settlement", "Glowing Mushroom Cave", "Desert Mountain")
    }
}
