package dev.skyglint.ui.text

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

/** One font/measurement contract for geometry and drawing, including resource-pack replacements. */
object HudText {
    private val style = Style.EMPTY.withFont(FontDescription.Resource(Identifier.fromNamespaceAndPath("skyglint", "ui")))
    fun component(text: String): Component = Component.literal(clean(text)).setStyle(style)
    fun width(text: String): Int = Minecraft.getInstance().font.width(component(text))
    fun draw(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
        graphics.text(Minecraft.getInstance().font, component(text), x, y, color, false)
    }
    /** Preserve fractional ink alignment at non-integer GUI/HUD scales. */
    fun draw(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int) {
        graphics.pose().pushMatrix()
        try { graphics.pose().translate(x, y); draw(graphics, text, 0, 0, color) }
        finally { graphics.pose().popMatrix() }
    }
    fun centered(graphics: GuiGraphicsExtractor, text: String, centerX: Int, y: Int, color: Int) {
        draw(graphics, text, centerX - width(text) / 2, y, color)
    }
    /** Ink bounds include the active resource-pack font's bearing, unlike vanilla's 9px lineHeight. */
    fun centeredOriginY(text: String, top: Float, height: Float, scale: Float = 1f): Float {
        val bounds = Minecraft.getInstance().font.prepareText(component(text).visualOrderText,
            0f, 0f, -1, false, false, 0).bounds()
        return top + height / 2f - (bounds?.let { it.top() + it.height() / 2f } ?: 4.5f) * scale
    }
    fun fit(text: String, maxWidth: Int): String {
        val safe = clean(text)
        if (maxWidth <= 0) return ""
        if (width(safe) <= maxWidth) return safe
        val ellipsis = "…"
        if (width(ellipsis) > maxWidth) return ""
        val points = safe.codePoints().toArray()
        var low = 0
        var high = points.size
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (width(String(points, 0, middle) + ellipsis) <= maxWidth) low = middle else high = middle - 1
        }
        return String(points, 0, low) + ellipsis
    }
    private fun clean(text: String): String = text.replace('\n', ' ').replace('\r', ' ')
}
