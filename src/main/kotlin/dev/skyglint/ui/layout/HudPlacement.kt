package dev.skyglint.ui.layout

import dev.skyglint.ui.HudViewport

/** Persistent geometry only. Coordinates use the available travel, so the entire row fits after resize. */
data class HudPlacement(val offsetX: Double = 0.0, val positionY: Double? = null, val scale: Double = 1.0) {
    fun place(base: HudViewport, guiWidth: Int, guiHeight: Int, designHeight: Float): HudViewport {
        val safe=sanitized()
        return base.copy(x=(6+(guiWidth-base.width*base.scale-12).coerceAtLeast(0f)*(1+safe.offsetX)/2).toFloat(),
            y=safe.positionY?.let { (6+(guiHeight-designHeight*base.scale-12).coerceAtLeast(0f)*it).toFloat() } ?: base.y)
    }
    fun sanitized() = copy(offsetX = finite(offsetX, 0.0).coerceIn(-1.0, 1.0),
        positionY = positionY?.let { finite(it, 0.0).coerceIn(0.0, 1.0) }, scale = finite(scale, 1.0).coerceIn(1.0 / 3.0, 2.0))
    companion object { private fun finite(value: Double, fallback: Double) = value.takeIf(Double::isFinite) ?: fallback }
}

data class HudEditorBounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(px: Double, py: Double) = px >= x && py >= y && px <= x + width && py <= y + height
}
