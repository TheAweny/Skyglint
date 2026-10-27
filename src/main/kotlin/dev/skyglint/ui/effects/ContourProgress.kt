package dev.skyglint.ui.effects

/** Float payload for hud_backdrop.vsh/hud_contour.fsh. Zero means unknown, [.25,.75] is known. */
internal object ContourProgress {
    fun encode(fraction: Float?): Float {
        require(fraction == null || fraction.isFinite())
        return fraction?.let { .25f + it.coerceIn(0f, 1f) * .5f } ?: 0f
    }
}
