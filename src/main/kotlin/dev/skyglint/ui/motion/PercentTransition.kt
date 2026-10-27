package dev.skyglint.ui.motion

/** Retain the last number while leaving; reversing an exit keeps the spring's value and velocity. */
class PercentTransition {
    data class State(val amount: Double, val captions: List<CaptionTransition.Layer>)
    private val spring = CriticalSpring(0.0, 18.0)
    private var identity: String? = null
    private var text = ""
    private var caption = CaptionTransition("")

    fun update(key: String, percent: Int?, seconds: Double): State {
        if (key != identity) { reset(); identity = key }
        if (percent != null) text = "$percent%"
        val amount = spring.step(if (percent == null) 0.0 else 1.0, seconds).coerceIn(0.0, 1.0)
        return State(amount, caption.update(text, seconds))
    }

    fun reset() { spring.snap(0.0); identity = null; text = ""; caption = CaptionTransition("") }
}
