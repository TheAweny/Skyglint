package dev.skyglint.ui.text

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import kotlin.math.ceil
import kotlin.math.roundToInt
import dev.skyglint.mixin.effects.BackdropGraphicsAccessor
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import org.joml.Matrix3x2f

/** Settings-only Inter atlas. Geometry and drawing use the same 32-unit font and explicit GUI size. */
object SettingsText {
    private const val ATLAS_SIZE = 32f
    private const val DEFAULT_SIZE = 9.5f
    private val style = Style.EMPTY.withFont(FontDescription.Resource(Identifier.fromNamespaceAndPath("skyglint", "settings")))

    /** Scoped by the settings screen while extracting an animated page. */
    var opacity: Float = 1f

    /** Register before the first resource compilation, just like the settings surface pipelines. */
    fun initialize() = SettingsGlyphState.initialize()

    /** Carries the settings face for narration/tooltips; visual consumers must apply the same size transform. */
    fun component(text: String): Component = Component.literal(clean(text)).setStyle(style)

    fun width(text: String): Int = width(text, DEFAULT_SIZE)

    fun width(text: String, size: Float): Int {
        if (!size.isFinite() || size <= 0f) return 0
        // Font.width rounds to an integer in atlas units. Rounding again after scaling could
        // unnecessarily truncate a caption that actually fits, especially at fractional sizes.
        return ceil(measure(text, size).toDouble()).toInt()
    }

    fun measure(text: String, size: Float = DEFAULT_SIZE): Float {
        if (!size.isFinite() || size <= 0f) return 0f
        return Minecraft.getInstance().font.splitter.stringWidth(component(text).visualOrderText) * (size / ATLAS_SIZE)
    }

    fun draw(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) =
        drawSized(graphics, text, x.toFloat(), y.toFloat(), DEFAULT_SIZE, color)

    fun draw(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int) =
        drawSized(graphics, text, x, y, DEFAULT_SIZE, color)

    /** x/y are the line's cap-top origin; common H bearing keeps mixed captions on a stable baseline. */
    fun drawSized(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, size: Float, color: Int) {
        if (!size.isFinite() || size <= 0f || !x.isFinite() || !y.isFinite()) return
        val safe = clean(text)
        if (safe.isEmpty()) return
        val effectiveOpacity = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else 1f
        val alpha = (((color ushr 24) and 255) * effectiveOpacity).roundToInt()
        if (alpha == 0) return
        val fadedColor = (color and 0x00ffffff) or (alpha shl 24)
        val font = Minecraft.getInstance().font
        // Query the active resource-pack font rather than assuming Minecraft's fixed nine-unit line box.
        // No cached bearing survives a font reload; measuring a fixed reference avoids per-caption vertical jitter.
        var capTop = Float.POSITIVE_INFINITY
        font.prepareText(component("H").visualOrderText, 0f, 0f, -1, false, false, 0)
            .visit(object : Font.GlyphVisitor {
                override fun acceptRenderable(glyph: TextRenderable) { capTop = minOf(capTop, glyph.top()) }
            })
        if (!capTop.isFinite()) capTop = 0f
        val scale = size / ATLAS_SIZE
        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate(x, y - capTop * scale)
            graphics.pose().scale(scale, scale)
            val prepared=font.prepareText(component(safe).visualOrderText,0f,0f,fadedColor,false,false,0)
            val pose=Matrix3x2f(graphics.pose())
            val scissor=graphics.scissorStack.peek()
            val state=(graphics as BackdropGraphicsAccessor).`skyglint$backdropRenderState`()
            prepared.visit(object:Font.GlyphVisitor {
                override fun acceptRenderable(glyph:TextRenderable) {
                    state.addGuiElement(SettingsGlyphState(pose,glyph,scissor))
                }
            })
        } finally {
            graphics.pose().popMatrix()
        }
    }

    fun fit(text: String, maxWidth: Int): String = fit(text, maxWidth, DEFAULT_SIZE)

    fun fit(text: String, maxWidth: Int, size: Float): String {
        val safe = clean(text)
        if (maxWidth <= 0 || !size.isFinite() || size <= 0f) return ""
        if (measure(safe, size) <= maxWidth) return safe
        val ellipsis = "…"
        if (measure(ellipsis, size) > maxWidth) return ""
        val points = safe.codePoints().toArray()
        var low = 0
        var high = points.size
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (measure(String(points, 0, middle) + ellipsis, size) <= maxWidth) low = middle else high = middle - 1
        }
        return String(points, 0, low) + ellipsis
    }

    private fun clean(text: String): String = buildString(text.length) {
        text.codePoints().forEach { point ->
            when {
                point == '\n'.code || point == '\r'.code || point == '\t'.code -> append(' ')
                !Character.isISOControl(point) -> appendCodePoint(point)
            }
        }
    }
}
