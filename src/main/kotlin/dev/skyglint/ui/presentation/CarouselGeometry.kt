package dev.skyglint.ui.presentation

import kotlin.math.floor

/** Stable page viewport; each page's actual icon and fitted caption are one centered group. */
object CarouselGeometry {
    private const val PADDING = 14.0
    private const val HEADING_GAP = 14.0
    const val ICON_SIZE = 16
    const val ICON_GAP = 5

    data class Group(val left: Float, val iconCenter: Float?, val textLeft: Float)
    data class Layout(val headingLeft: Float, val pageLeft: Float, val pageWidth: Float) {
        val pageRight: Float get() = pageLeft + pageWidth

        fun textBudget(icon: Boolean): Int =
            (floor(pageWidth).toInt() - (if (icon) ICON_SIZE + ICON_GAP else 0)).coerceAtLeast(0)

        fun group(textWidth: Int, icon: Boolean, offsetX: Float = 0f): Group {
            val left = pageLeft + (pageWidth - contentWidth(textWidth, icon)) / 2f + offsetX
            val iconSpace = if (icon) ICON_SIZE + (if (textWidth > 0) ICON_GAP else 0) else 0
            return Group(left, if (icon) left + ICON_SIZE / 2f else null, left + iconSpace)
        }
    }

    fun contentWidth(textWidth: Int, icon: Boolean): Float =
        (textWidth + (if (icon) ICON_SIZE + (if (textWidth > 0) ICON_GAP else 0) else 0)).toFloat()

    fun width(headingWidth: Int, pageWidths: List<Float>): Double =
        PADDING * 2 + headingWidth + HEADING_GAP + (pageWidths.maxOrNull() ?: 0f)

    fun layout(bodyWidth: Float, headingWidth: Int, pageWidths: List<Float>): Layout {
        val allocatedPageWidth = pageWidths.maxOrNull() ?: 0f
        val naturalWidth = width(headingWidth, pageWidths).toFloat()
        val headingLeft = (bodyWidth - naturalWidth) / 2f + PADDING.toFloat()
        val pageLeft = headingLeft + headingWidth + HEADING_GAP.toFloat()
        // The enclosing body scissor performs the reveal; changing this budget would animate ellipses.
        return Layout(headingLeft, pageLeft, allocatedPageWidth)
    }
}
