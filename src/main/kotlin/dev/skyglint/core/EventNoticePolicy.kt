package dev.skyglint.core

import java.util.Locale

/** Short event announcements, independent of the persistent schedule and island lifecycle. */
class EventNoticePolicy {
    private data class State(
        val generation: Long = 0,
        var upcomingObserved: Boolean = false,
        var reminderShown: Boolean = false,
        var activeReported: Boolean = false,
        var startsAt: Long? = null,
        var endsAt: Long? = null,
        var lastActiveAt: Long? = null,
    )
    private val states = linkedMapOf<String, State>()

    fun clear() { states.clear() }

    fun confirmedEnd(eventName: String, now: Long): List<String> {
        val key = eventName.trim().lowercase(Locale.ROOT)
        val state = states[key] ?: return emptyList()
        state.endsAt = now
        return listOf("skyglint.event_active:$key:${state.generation}", "skyglint.event_started:$key:${state.generation}")
    }

    fun reminderId(eventName: String): String? {
        val key = eventName.trim().lowercase(Locale.ROOT)
        val state = states[key] ?: return null
        return "skyglint.event_soon:$key:${state.generation}"
    }

    /** Only a fresh explicit active observation can announce a start or an event already in progress. */
    fun observe(event: ScheduledEvent?, now: Long, explicitActive: Boolean = false): NoticeRequest? {
        if (event == null || now < event.observedAt || now >= event.expiresAt) return null
        val key = event.name.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return null
        var state = states.getOrPut(key) { State() }
        while (states.size > MAX_EVENTS) states.remove(states.keys.first())
        if (event.active) {
            if (!explicitActive) return null
            // An explicitly observed later endpoint after the previous occurrence ended is
            // evidence of a new occurrence even if its upcoming Calendar card was never seen.
            val oldEnd = state.endsAt
            if (state.activeReported && oldEnd != null && now >= oldEnd &&
                event.endsAt?.let { it > oldEnd + REMINDER_WINDOW } == true) {
                state = State(generation = state.generation + 1)
                states[key] = state
            }
            state.lastActiveAt = now
            event.endsAt?.let { state.endsAt = it }
            if (state.activeReported) return null
            state.activeReported = true
            val phase = if (state.upcomingObserved) "started" else "active"
            return NoticeRequest("skyglint.event_$phase:$key:${state.generation}",
                if (state.upcomingObserved) "Event started" else "Event active", event.name,
                priority = 40, countdown = event.endsAt?.takeIf { it > now }?.let {
                    CountdownSnapshot(it, (event.durationMillis ?: (it - now)).coerceAtLeast(it - now))
                })
        }
        // Calendar may already describe the next occurrence while this connection's current
        // observed event is still running. Island transfers clear source snapshots, not this
        // policy: that future card must not re-arm the same active login notice on every island.
        if (state.activeReported && state.endsAt?.let { now < it } == true) return null
        val deadline = event.startsAt ?: return null
        val remaining = deadline - now
        // A later server schedule following confirmed activity is evidence of another occurrence.
        // Small deadline corrections/source precision changes never re-arm a reminder.
        val previousStart = state.startsAt
        val knownEnded = state.endsAt?.let { now >= it && deadline > it } == true
        val laterSchedule = previousStart != null && now >= previousStart && deadline > previousStart + REMINDER_WINDOW
        val laterAfterActive = state.lastActiveAt?.let { now - it > REMINDER_WINDOW } == true
        if ((state.activeReported && (remaining > REMINDER_WINDOW || knownEnded || laterAfterActive)) ||
            (state.reminderShown && laterSchedule)) {
            state = State(generation = state.generation + 1)
            states[key] = state
        }
        state.startsAt = deadline
        state.upcomingObserved = true
        if (remaining !in 1..REMINDER_WINDOW || state.reminderShown || state.activeReported) return null
        state.reminderShown = true
        val seconds = (remaining + 999) / 1_000
        val duration = if (seconds % 60 == 0L) "${seconds / 60} min" else DurationText.countdown(remaining)
        return NoticeRequest("skyglint.event_soon:$key:${state.generation}", "Starts in $duration", event.name,
            priority = 35)
    }

    companion object {
        private const val REMINDER_WINDOW = 5 * 60_000L
        private const val MAX_EVENTS = 64
    }
}
