package dev.skyglint.ui.settings

import dev.skyglint.ui.text.HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.exp
import kotlin.math.roundToInt

/** Only appearance is replaced. Vanilla owns mouse/keyboard activation, focus, tooltip and narration. */
class SettingsButton(
    x: Int, y: Int, width: Int, height: Int,
    private val caption: String,
    private val value: () -> String = { "" },
    private val toggled: (() -> Boolean)? = null,
    hint: String,
    action: () -> Unit,
) : Button(x, y, width, height, Component.literal(caption), OnPress { action() }, DEFAULT_NARRATION) {
    private var hover = 0.0
    private var lastFrame = 0L

    init { setTooltip(Tooltip.create(Component.literal(hint))); refreshMessage() }

    fun refreshMessage() { message = HudText.component(listOf(caption, value()).filter(String::isNotBlank).joinToString(": ")) }

    override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.nanoTime()
        val seconds = if (lastFrame == 0L) 0.0 else ((now - lastFrame).coerceAtLeast(0) / 1e9).coerceAtMost(.2)
        lastFrame = now
        val target = if (isHoveredOrFocused && active) 1.0 else 0.0
        hover += (target - hover) * (1 - exp(-18 * seconds))
        val border = if (isFocused) 0xFFB5DDF4.toInt() else ((30 + hover * 45).roundToInt() shl 24) or 0xA8C7DF
        val surface = ((110 + hover * 35).roundToInt() shl 24) or 0x243141
        SettingsPaint.rounded(g, x, y, width, height, 6, border)
        SettingsPaint.rounded(g, x + 1, y + 1, width - 2, height - 2, 5, surface)
        val state = toggled
        val reserve = if (state != null) 36 else HudText.width(value()) + 20
        val text = HudText.fit(caption, (width - reserve - 14).coerceAtLeast(0))
        val textY = y + (height - 12) / 2
        HudText.draw(g, text, x + 10, textY, if (active) SettingsPaint.INK else SettingsPaint.MUTED)
        if (state != null) {
            val enabled = state()
            val switchX = x + width - 34
            val switchY = y + (height - 12) / 2
            SettingsPaint.rounded(g, switchX, switchY, 24, 12, 6,
                if (enabled) 0xFF84BACD.toInt() else 0xFF455363.toInt())
            SettingsPaint.rounded(g, switchX + if (enabled) 14 else 2, switchY + 2, 8, 8, 4, 0xFFF3F8FC.toInt())
        } else {
            val renderedValue = value()
            HudText.draw(g, renderedValue, x + width - 10 - HudText.width(renderedValue), textY,
                if (active) SettingsPaint.ACCENT else SettingsPaint.MUTED)
        }
    }
}
