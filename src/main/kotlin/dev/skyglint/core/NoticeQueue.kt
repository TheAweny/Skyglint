package dev.skyglint.core

/** Bounded presentation policy, independent of the underlying boss/activity/progression stores. */
class NoticeQueue(private val capacity: Int = 4) {
    private data class Pending(val request: NoticeRequest, val receivedAt: Long, val validUntil: Long, val remaining: Long,
                               val presentedMillis: Long = 0)
    private data class Playing(val pending: Pending, val startedAt: Long, val endsAt: Long)
    private val pending = mutableListOf<Pending>()
    private val recentlySeen = linkedMapOf<String, Long>()
    private var playing: Playing? = null

    init { require(capacity in 1..16) }

    fun clear() { pending.clear(); recentlySeen.clear(); playing = null }

    /** A preempted introduction cannot return after its represented event disappeared. */
    fun retainTargets(valid: Set<NoticeMetricTarget>, now: Long) {
        fun invalid(request: NoticeRequest) = request.target?.let { it !in valid } == true ||
            (request.target == null && request.countdown?.let { it.deadline <= now } == true)
        pending.removeAll { invalid(it.request) }
        if (playing?.pending?.request?.let(::invalid) == true) playing = null
        advance(now)
    }

    /** Retire an obsolete notice without letting a preempted copy resume later. */
    fun cancel(id: String, now: Long): Boolean {
        advance(now)
        var removed = pending.removeAll { it.request.id == id }
        if (playing?.pending?.request?.id == id) { playing = null; removed = true }
        if (removed) advance(now)
        return removed
    }

    /** Repeats neither extend active display time nor create another queued copy. */
    fun offer(request: NoticeRequest, now: Long): Boolean {
        advance(now)
        if (playing?.pending?.request?.id == request.id || pending.any { it.request.id == request.id } ||
            recentlySeen[request.id]?.let { now < it } == true) return false
        recentlySeen[request.id] = now + 2_000
        if (recentlySeen.size > 256) recentlySeen.remove(recentlySeen.keys.first())
        val immutable = request.copy(pages = java.util.Collections.unmodifiableList(request.pages.toList()))
        val incoming = Pending(immutable, now, now + request.freshnessMillis, request.durationMillis)
        val current = playing
        if (current == null) start(incoming, now)
        else if (request.priority > current.pending.request.priority) {
            enqueue(current.pending.copy(remaining = current.endsAt - now,
                presentedMillis = current.pending.presentedMillis + now - current.startedAt))
            start(incoming, now)
        } else if (request.priority >= 100 && request.priority == current.pending.request.priority) {
            // Rapid confirmed level-ups coalesce to the latest one; don't replay old celebrations.
            start(incoming, now)
        } else enqueue(incoming)
        return true
    }

    fun active(now: Long): TransientNotice? {
        advance(now)
        val current = playing ?: return null
        if (now < current.startedAt) return null
        val request = current.pending.request
        return TransientNotice(request.id, request.title, request.detail, request.priority,
            current.startedAt, current.endsAt, request.item, request.magicFind, request.rarity, request.pages,
            current.pending.presentedMillis, request.countdown, request.target, request.compactCaption, request.reward)
    }

    private fun advance(now: Long) {
        recentlySeen.entries.removeIf { it.value <= now }
        pending.removeAll { it.validUntil <= now || it.remaining <= 0 }
        var cursor = now
        while (true) {
            val current = playing
            if (current != null && now < current.endsAt) return
            if (current != null) cursor = current.endsAt
            playing = null
            val next = pending.maxByOrNull { it.request.priority } ?: return
            pending.remove(next)
            val startAt = maxOf(cursor, next.receivedAt)
            if (next.validUntil <= startAt) continue
            start(next, startAt)
        }
    }

    private fun start(item: Pending, now: Long) {
        playing = Playing(item, now, minOf(now + item.remaining, item.validUntil))
    }

    private fun enqueue(item: Pending) {
        if (item.remaining <= 0) return
        pending += item
        if (pending.size > capacity) {
            // Keep the more urgent entries; discard the oldest among equally low-priority ones.
            val remove = pending.minBy { it.request.priority }
            pending.remove(remove)
        }
    }
}
