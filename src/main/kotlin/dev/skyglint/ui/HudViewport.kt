package dev.skyglint.ui

import kotlin.math.floor
import kotlin.math.min

/** Converts prototype screen pixels into Minecraft GUI units exactly once, then fits the viewport. */
data class HudViewport(val scale: Float, val width: Int, val x: Float, val y: Float) {
    companion object {
        fun fit(
            guiWidth: Int,
            guiHeight: Int,
            minecraftGuiScale: Int,
            preferredPixelScale: Double,
            desiredWidth: Int,
            designHeight: Int,
            topOffsetGui: Int,
            maxDesignWidth: Int = 400,
        ): HudViewport {
            require(guiWidth > 0 && guiHeight > 0 && minecraftGuiScale > 0)
            require(preferredPixelScale.isFinite() && preferredPixelScale > 0)
            require(desiredWidth > 0 && designHeight > 0)
            val offset = topOffsetGui.coerceIn(0, (guiHeight - 40).coerceAtLeast(0))
            val widthLimit = desiredWidth.coerceAtMost(maxDesignWidth)
            val screenToGui = preferredPixelScale / minecraftGuiScale
            val scale = min(screenToGui, min(
                (guiWidth - 12).coerceAtLeast(1).toDouble() / (if (maxDesignWidth > 400) widthLimit else widthLimit.coerceAtMost(240)),
                (guiHeight - offset - 12).coerceAtLeast(1).toDouble() / designHeight,
            )).toFloat()
            val available = floor((guiWidth - 12).coerceAtLeast(1) / scale).toInt().coerceAtLeast(1)
            val width = min(widthLimit, available)
            return HudViewport(scale, width, (guiWidth - width * scale) / 2f, offset.toFloat())
        }
    }
}
