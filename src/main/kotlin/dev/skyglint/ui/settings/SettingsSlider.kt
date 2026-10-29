package dev.skyglint.ui.settings

import dev.skyglint.ui.text.SettingsText as HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** Vanilla supplies drag, keyboard focus and narration; the editor owns the unsaved value. */
class SettingsSlider(x: Int, y: Int, width: Int, initial: Double, private val changed: (Double) -> Unit) :
    AbstractSliderButton(x, y, width, 38, Component.literal("HUD scale"), (initial - MINIMUM) / (MAXIMUM - MINIMUM)) {
    init { setTooltip(Tooltip.create(Component.literal("Drag to scale. Focus and use arrow keys for fine adjustment. 100% is 45 physical pixels."))); updateMessage() }
    override fun updateMessage() { message = HudText.component("HUD scale: ${percent()}%") }
    override fun applyValue() { changed((MINIMUM + value * (MAXIMUM - MINIMUM)).coerceIn(MINIMUM, MAXIMUM)) }
    private fun percent() = ((MINIMUM + value * (MAXIMUM - MINIMUM)) * 100).roundToInt()
    fun synchronize(scale: Double) { value=((scale-MINIMUM)/(MAXIMUM-MINIMUM)).coerceIn(0.0,1.0); updateMessage() }
    override fun extractWidgetRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        HudText.draw(g, "Scale", x, y, SettingsPaint.INK)
        val label = "${percent()}%"
        HudText.draw(g, label, x + width - HudText.width(label), y, SettingsPaint.ACCENT)
        SettingsPaint.rounded(g, x, y + 25, width, 3, 1, 0xFF43494F.toInt())
        SettingsPaint.rounded(g, x, y + 25, (width * value).roundToInt(), 3, 1, SettingsPaint.ACCENT)
        val knob = x + (8 + (width - 16) * value).roundToInt()
        SettingsPaint.rounded(g, knob - 5, y + 21, 10, 11, 5, SettingsPaint.INK)
        if (isFocused) SettingsPaint.rounded(g, x, y + 36, width, 1, 0, SettingsPaint.ACCENT)
    }
    companion object { const val MINIMUM = 1.0 / 3.0; const val MAXIMUM = 2.0 }
}
