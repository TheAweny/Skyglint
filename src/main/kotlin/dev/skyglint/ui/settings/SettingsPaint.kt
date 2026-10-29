package dev.skyglint.ui.settings

import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.skyglint.ui.effects.HudBackdrop

internal object SettingsPaint {
    /** Caller scopes this per extraction region and restores it in finally. */
    var opacity: Float = 1f
    const val INK = 0xFFF1F0F5.toInt()
    const val MUTED = 0xFF92939F.toInt()
    const val ACCENT = 0xFFBAA6EE.toInt()
    const val SURFACE = 0xFF1C202B.toInt()
    const val BORDER = 0xFF393C49.toInt()

    /** One analytic quad keeps contours smooth at fractional GUI scales and small radii. */
    fun rounded(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) {
        HudBackdrop.surface(g,x.toFloat(),y.toFloat(),width.toFloat(),height.toFloat(),radius.toFloat(),SettingsPaint.color(color))
    }

    fun rounded(g: GuiGraphicsExtractor, x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        HudBackdrop.surface(g,x,y,width,height,radius,SettingsPaint.color(color))
    }

    fun color(value: Int): Int = ((((value ushr 24) * opacity.coerceIn(0f,1f)).toInt()) shl 24) or (value and 0xFFFFFF)

    fun mix(from: Int, to: Int, amount: Double): Int {
        val t = amount.coerceIn(0.0, 1.0)
        fun channel(shift: Int) = (((from ushr shift) and 255) * (1 - t) + ((to ushr shift) and 255) * t).toInt()
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
