package dev.skyglint.core

/** Compact observed durations. Formatting never changes the source deadline or its precision. */
object DurationText {
    fun countdown(millis: Long): String {
        val nonnegative = millis.coerceAtLeast(0)
        // Ceiling without adding 999: even a Long.MAX_VALUE duration must not overflow.
        val seconds = nonnegative / 1_000 + if (nonnegative % 1_000 == 0L) 0 else 1
        return when {
            seconds >= 86_400 -> "${seconds / 86_400}d ${(seconds / 3_600) % 24}h"
            seconds >= 3_600 -> "${seconds / 3_600}h ${(seconds / 60) % 60}m"
            else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
    }
}
