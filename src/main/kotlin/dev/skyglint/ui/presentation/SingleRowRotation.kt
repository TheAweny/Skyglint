package dev.skyglint.ui.presentation

/** Keep all server UUIDs reachable in one row without restarting a dwell on each progress update. */
class SingleRowRotation(private val dwellMillis: Long = 6_000) {
    private var selected: String? = null
    private var since = 0L
    private var lastUrgent: String? = null
    fun select(ids: List<String>, now: Long, urgent: String? = null, urgentToken: String? = urgent): String? {
        if (ids.isEmpty()) { reset(); return null }
        if (urgent != null && urgentToken != lastUrgent && urgent in ids) { selected = urgent; since = now }
        lastUrgent = urgentToken
        val index = ids.indexOf(selected)
        if (index < 0 || now < since) { selected = ids.first(); since = now }
        else if (now - since >= dwellMillis && ids.size > 1) {
            selected = ids[(index + 1) % ids.size]; since = now
        }
        return selected
    }
    fun reset() { selected = null; since = 0; lastUrgent = null }
}
