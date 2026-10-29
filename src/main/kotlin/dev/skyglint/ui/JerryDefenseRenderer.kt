package dev.skyglint.ui

import dev.skyglint.core.ModuleWidget
import dev.skyglint.features.winter.JerryDefenseModule
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.text.HudText

/** Five measured point indicators inside the ordinary capsule; colors are copied server facts. */
object JerryDefenseRenderer {
    private fun points(widget: ModuleWidget) = widget.presentation?.metrics.orEmpty()
        .filter { it.id.startsWith("gift_pile:") }.sortedBy { it.id }
    private fun caption(widget: ModuleWidget) = widget.detail ?: widget.title
    fun register() {
        UiWidgetRegistry.register(JerryDefenseModule.WIDGET_ID, WidgetRenderer { g, widget, x, y, width, height ->
            val points = points(widget)
            val text = caption(widget)
            val textWidth = HudText.width(text)
            val body = textWidth + if (points.size == 5) 128 else 0
            val left = x + (width - body) / 2f
            HudText.draw(g, text, left, HudText.centeredOriginY(text, y.toFloat(), height.toFloat()), 0xFFF1F5FA.toInt())
            if (points.size == 5) {
                points.forEachIndexed { index, point ->
                    val px = left + textWidth + 12 + index * 23
                    val color = point.accent ?: 0xFFB9BEC6.toInt()
                    val destroyed = point.value == "destroyed"
                    HudBackdrop.surface(g, px, y + 4f, 20f, height - 8f, 7f,
                        (if (destroyed) 0x19 else 0x26) shl 24 or (color and 0xFFFFFF))
                    val label = if (destroyed) "×" else point.label
                    HudText.draw(g, label, px + (20 - HudText.width(label)) / 2f,
                        HudText.centeredOriginY(label, y.toFloat(), height.toFloat()), color)
                    HudBackdrop.surface(g, px + 6f, y + height - 5f, 8f, 1.2f, .6f, color)
                }
            }
        }, WidgetWidthMeasure { widget, _ ->
            (24 + HudText.width(caption(widget)) + if (points(widget).size == 5) 128 else 0).toDouble()
        })
    }
}
