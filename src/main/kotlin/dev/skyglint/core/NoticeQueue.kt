package dev.skyglint.core

/** Bounded presentation policy, independent of the underlying boss/activity/progression stores. */
class NoticeQueue(private val capacity: Int = 4) {
    private data class Pending(val request: NoticeRequest, val receivedAt: Long, val validUntil: Long, val remaining: Long,
                               val presentedMillis: Long = 0, val ids: Set<String> = setOf(request.id))
    private data class Playing(val pending: Pending, val startedAt: Long, val endsAt: Long)
    private val pending = mutableListOf<Pending>()
    private val recentlySeen = linkedMapOf<String, Long>()
    private var playing: Playing? = null

    init { require(capacity in 1..16) }

    fun clear() { pending.clear(); recentlySeen.clear(); playing = null }

    /** Drop hidden presentations permanently; never reset dedupe or restart retained presentation time. */
    fun retain(now: Long, predicate: (NoticeRequest) -> Boolean) {
        pending.removeAll { !predicate(it.request) }
        if (playing?.pending?.request?.let(predicate) == false) playing = null
        advance(now)
    }

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
        var removed = pending.removeAll { id in it.ids }
        if (playing?.pending?.ids?.contains(id) == true) { playing = null; removed = true }
        if (removed) advance(now)
        return removed
    }

    /** Returns accepted observation, including hidden ones. Repeats never extend or enqueue a copy. */
    fun offer(request: NoticeRequest, now: Long, visible: Boolean = true): Boolean {
        advance(now)
        if (playing?.pending?.ids?.contains(request.id) == true || pending.any { request.id in it.ids } ||
            recentlySeen[request.id]?.let { now < it } == true) return false
        recentlySeen[request.id] = now + 2_000
        if (recentlySeen.size > 256) recentlySeen.remove(recentlySeen.keys.first())
        // Accepted hidden observations consume dedupe without occupying or preempting the queue.
        if (!visible) return true
        val immutable = request.copy(pages = java.util.Collections.unmodifiableList(request.pages.toList()),
            loot = java.util.Collections.unmodifiableList(request.loot.toList()))
        if (mergeReward(immutable, now)) return true
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
            current.pending.presentedMillis, request.countdown, request.target, request.compactCaption, request.reward, request.loot)
    }

    /** Only one bounded, same-origin delivery burst is one presentation. No value is inferred or summed. */
    private fun mergeReward(incoming: NoticeRequest, now: Long): Boolean {
        val reward = incoming.reward ?: return false
        if (reward.sourceId.isBlank() || incoming.pages.isNotEmpty() || incoming.target != null || incoming.countdown != null) return false
        val added = incoming.loot.ifEmpty { listOf(reward) }
        if (added.any { it.sourceId != reward.sourceId || it.delivery != reward.delivery }) return false
        fun compatible(item: Pending): Boolean {
            val prior = item.request.reward ?: return false
            val priorLoot = item.request.loot.ifEmpty { listOf(prior) }
            return prior.sourceId == reward.sourceId && prior.delivery == reward.delivery &&
                now >= item.receivedAt && now - item.receivedAt <= 1_000 &&
                item.request.pages.isEmpty() && item.request.target == null && item.request.countdown == null &&
                priorLoot.size + added.size <= 8
        }
        fun merged(item: Pending): Pending {
            val loot = java.util.Collections.unmodifiableList(item.request.loot.ifEmpty { listOf(item.request.reward!!) } + added)
            val duration = maxOf(item.request.durationMillis, incoming.durationMillis, loot.size * 1_800L).coerceAtMost(15_000)
            // Keep a bounded wait budget as well as enough presentation time for every page.
            val freshness = maxOf(item.request.freshnessMillis, incoming.freshnessMillis, duration + 15_000).coerceAtMost(60_000)
            val combined = item.request.copy(durationMillis = duration, freshnessMillis = freshness, loot = loot)
            val expiry = maxOf(item.validUntil, minOf(item.receivedAt + 60_000, now + freshness))
            return item.copy(request = combined, validUntil = expiry,
                remaining = (duration - item.presentedMillis).coerceAtLeast(0), ids = item.ids + incoming.id)
        }
        val current = playing
        if (current != null && compatible(current.pending)) {
            val combined = merged(current.pending)
            playing = current.copy(pending = combined, endsAt = minOf(current.startedAt + combined.remaining, combined.validUntil))
            return true
        }
        val index = pending.indexOfFirst(::compatible)
        if (index < 0) return false
        pending[index] = merged(pending[index])
        return true
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
