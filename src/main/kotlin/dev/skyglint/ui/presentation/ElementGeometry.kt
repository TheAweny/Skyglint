package dev.skyglint.ui.presentation

/** One source of truth for measured text, native icon reservation and symmetric padding. */
object ElementGeometry {
    const val ICON_SIZE = 16
    const val ICON_GAP = 6
    const val ICON_SPACE = ICON_SIZE + ICON_GAP
    const val TEXT_PADDING = 24
    const val ROUNDING_RESERVE = 2
    fun mainWidth(titleWidth: Int, tailWidth: Int, icon: Boolean): Double =
        (titleWidth + tailWidth + TEXT_PADDING + (if (icon) ICON_SPACE else 0) + ROUNDING_RESERVE)
            .coerceIn(70, 320).toDouble()
    fun textBudget(bodyWidth: Double, icon: Boolean): Int =
        kotlin.math.floor(bodyWidth).toInt() - TEXT_PADDING - (if (icon) ICON_SPACE else 0)
    data class Group(val left: Float, val iconCenter: Float?, val textLeft: Float)
    fun group(bodyWidth: Float, textWidth: Int, icon: Boolean): Group {
        val iconSpace = if (icon) ICON_SPACE else 0
        val left = (bodyWidth - textWidth - iconSpace) / 2f
        return Group(left, if (icon) left + ICON_SIZE / 2f else null, left + iconSpace)
    }
}
