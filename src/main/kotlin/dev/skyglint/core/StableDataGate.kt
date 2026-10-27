package dev.skyglint.core

/** Pure monotonic readiness gate. Callers choose a structural key rather than volatile live values. */
class StableDataGate<K>(private val settleMillis: Long = 500) {
    init { require(settleMillis >= 0) }
    private var key: K? = null
    private var changedAt = Long.MIN_VALUE
    private var lastAt = Long.MIN_VALUE

    fun ready(next: K?, now: Long): Boolean {
        if (next == null) { clear(); return false }
        if (key != next || changedAt == Long.MIN_VALUE || (lastAt != Long.MIN_VALUE && now < lastAt)) {
            key = next
            changedAt = now
        }
        lastAt = now
        return now >= changedAt && now - changedAt >= settleMillis
    }

    fun clear() { key = null; changedAt = Long.MIN_VALUE; lastAt = Long.MIN_VALUE }
}
