package dev.skyglint.ui.motion

import kotlin.math.floor

/** A measured whole-percent advance, never an animation of interpolated/guessed XP. */
class PercentPulse(private val durationMillis: Long = 1_100) {
    private var identity: String? = null
    private var previous: Int? = null
    private var visible: Int? = null
    private var until = 0L

    fun observe(key: String?, fraction: Double?, now: Long): Int? {
        val percent = fraction?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?.let { floor(it * 100 + 1e-7).toInt() }
        if (key != identity || percent == null) {
            identity = key; previous = percent; visible = null; until = 0
        } else {
            val before = previous
            if (before != null && percent > before) { visible = percent; until = now + durationMillis }
            if (before != null && percent < before) { visible = null; until = 0 }
            previous = percent
        }
        return visible.takeIf { now < until }
    }

    fun reset() { identity = null; previous = null; visible = null; until = 0 }
}
