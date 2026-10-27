package dev.skyglint.ui.settings

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.ceil
import kotlin.math.sqrt

internal object SettingsPaint {
    const val INK = 0xFFF0F4FA.toInt()
    const val MUTED = 0xFF9CAFC2.toInt()
    const val ACCENT = 0xFFB5DDF4.toInt()

    /** Flat center and at most 2r corner strips, rather than one draw call for every panel pixel row. */
    fun rounded(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) {
        if (width <= 0 || height <= 0) return
        val r = radius.coerceIn(0, minOf(width, height) / 2)
        g.fill(x, y + r, x + width, y + height - r, color)
        for (dy in 0 until r) {
            val cy = r - dy - .5
            val inset = ceil(r - sqrt((r * r - cy * cy).coerceAtLeast(0.0))).toInt()
            g.fill(x + inset, y + dy, x + width - inset, y + dy + 1, color)
            g.fill(x + inset, y + height - dy - 1, x + width - inset, y + height - dy, color)
        }
    }
}
