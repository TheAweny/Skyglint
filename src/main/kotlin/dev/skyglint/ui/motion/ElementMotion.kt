package dev.skyglint.ui.motion

/**
 * Per-element presentation lifetime. Call update every frame, including while absent, and keep
 * the caller's last render payload until visible is false. Geometry never owns domain state.
 */
class ElementMotion {
    data class State(
        val x: Double,
        /** Natural body width. The caller can reveal/scale the body using visibility. */
        val width: Double,
        val visibility: Double,
        /** Layout reservation contracts continuously; multiply adjacent gaps by visibility too. */
        val occupiedWidth: Double,
        /** Layer opacity already includes visibility. Text may also be an opaque content identity. */
        val captions: List<CaptionTransition.Layer>,
        val retainedCaption: String,
        val visible: Boolean,
    )

    private val position = CriticalSpring(0.0, 16.0)
    private val bodyWidth = CriticalSpring(0.0, 16.0)
    private val presence = CriticalSpring(0.0, 14.0)
    private var initialized = false
    private var retained = ""
    private var retainedWidth = 0.0
    private var caption = CaptionTransition("")

    fun update(present: Boolean, targetX: Double, targetWidth: Double, text: String, seconds: Double): State {
        require(targetX.isFinite() && targetWidth.isFinite() && targetWidth >= 0)
        require(seconds.isFinite() && seconds >= 0)
        if (present) {
            // A fully hidden element has no visible old payload to crossfade. An interrupted exit
            // keeps its actual caption mixture and velocity rather than restarting an entrance.
            if (!initialized || presence.value == 0.0) caption = CaptionTransition(text)
            retained = text
            retainedWidth = targetWidth
            if (!initialized) {
                position.snap(targetX)
                bodyWidth.snap(targetWidth)
                initialized = true
            }
        }
        if (!initialized) return State(targetX, 0.0, 0.0, 0.0, emptyList(), "", false)
        val x = position.step(targetX, seconds)
        val width = bodyWidth.step(retainedWidth, seconds).coerceAtLeast(0.0)
        val visibility = presence.step(if (present) 1.0 else 0.0, seconds).coerceIn(0.0, 1.0)
        val layers = caption.update(retained, seconds).map {
            CaptionTransition.Layer(it.text, it.opacity * visibility)
        }
        return State(x, width, visibility, width * visibility, layers, retained, visibility > .001)
    }

    fun reset() {
        position.snap(0.0); bodyWidth.snap(0.0); presence.snap(0.0)
        initialized = false; retained = ""; retainedWidth = 0.0; caption = CaptionTransition("")
    }
}
