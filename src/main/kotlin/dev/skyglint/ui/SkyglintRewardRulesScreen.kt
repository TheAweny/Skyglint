package dev.skyglint.ui

import dev.skyglint.core.RewardRulePolicy
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.settings.SettingsEditBox
import dev.skyglint.ui.settings.SettingsActions
import dev.skyglint.ui.settings.SettingsButton
import dev.skyglint.ui.settings.SettingsPaint
import dev.skyglint.ui.text.SettingsText as HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Exact-name presentation rules; this screen neither observes items nor changes reward recognition. */
class SkyglintRewardRulesScreen(
    private val parent: Screen?,
    private val actions: SettingsActions,
) : Screen(Component.literal("Skyglint reward rules")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var rowHeight = 24
    private var pageSize = 1
    private var page = 0
    private var inputValue = ""
    private lateinit var input: EditBox
    private var status = "All recognized rewards are shown by default."
    private var error = false

    override fun init() {
        val compact = height < 310
        val margin = if (compact) 12 else 18
        rowHeight = if (compact) 22 else 28
        val gap = if (compact) 4 else 6
        panelWidth = minOf(540, width - 20).coerceAtLeast(1)
        panelHeight = minOf(384, height - 16).coerceAtLeast(1)
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        val left = panelX + margin
        val contentWidth = (panelWidth - margin * 2).coerceAtLeast(1)
        val footerY = panelY + panelHeight - margin - rowHeight
        val rowsY = panelY + 130
        pageSize = ((footerY - rowsY - gap) / (rowHeight + gap)).coerceAtLeast(1)
        val rules = actions.config.rewardOverrides.toSortedMap().entries.toList()
        val pages = ((rules.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)

        input = addRenderableWidget(SettingsEditBox(font, left + 5, panelY + 54, contentWidth - 10, rowHeight,
            "Exact item name", "Exact item name, for example Summoning Eye"))
        input.setMaxLength(256)
        input.setBordered(false)
        input.setTextColor(SettingsPaint.INK)
        input.setHint(Component.literal("Exact item name, for example Summoning Eye"))
        input.setTooltip(Tooltip.create(Component.literal("Enter the complete recognized reward name. Formatting and repeated spaces are normalized. Names must be 1–120 characters.")))
        input.value = inputValue
        input.setResponder { inputValue = it }
        val actionWidth = (contentWidth - gap * 2) / 3
        listOf("Show" to "show", "Hide" to "hide", "Reset" to "reset").forEachIndexed { index, (caption, action) ->
            addRenderableWidget(SettingsButton(left + index * (actionWidth + gap), panelY + 83, actionWidth, rowHeight,
                caption, hint = when (action) {
                    "show" -> "Always show this recognized reward, including when common rewards are filtered."
                    "hide" -> "Hide this exact recognized reward from the HUD. Observation and diagnostics continue."
                    else -> "Remove only this item's override. The selected all/filtered reward mode applies again."
                }) { changeRule(action) })
        }
        rules.drop(page * pageSize).take(pageSize).forEachIndexed { index, entry ->
            addRenderableWidget(SettingsButton(left, rowsY + index * (rowHeight + gap), contentWidth, rowHeight,
                entry.key, { if (entry.value == "show") "Shown" else "Hidden" },
                hint = "${entry.key}: ${entry.value}. Select this row to edit its exact-name rule.") {
                input.value = entry.key
                input.moveCursorToStart(false)
                setFocused(input)
            })
        }
        val pagerWidth = if (compact) 48 else 62
        addRenderableWidget(SettingsButton(left, footerY, pagerWidth, rowHeight, "Prev",
            hint = "Previous page of item overrides.") { page--; rebuildWidgets() }).active = page > 0
        addRenderableWidget(SettingsButton(left + pagerWidth + gap, footerY, pagerWidth, rowHeight, "Next",
            hint = "Next page of item overrides.") { page++; rebuildWidgets() }).active = page < pages - 1
        addRenderableWidget(SettingsButton(left + contentWidth - 96, footerY, 96, rowHeight, "Back", { "Esc" },
            hint = "Return to settings. Item rule changes are already saved.") { onClose() })
        setInitialFocus(input)
    }

    private fun changeRule(action: String) {
        val raw = input.value
        val name = RewardRulePolicy.normalizeItemName(raw)
        when {
            name.isEmpty() -> { status = "Enter a complete item name."; error = true; return }
            raw.length > 120 || name.length > 120 || raw.any(Char::isISOControl) -> {
                status = "Use 1–120 characters without control characters."; error = true; return
            }
            action != "reset" && name !in actions.config.rewardOverrides && actions.config.rewardOverrides.size >= 128 -> {
                status = "128 item rules reached. Reset a rule before adding another."; error = true; return
            }
        }
        try {
            actions.changeConfig { config ->
                config.copy(rewardOverrides = if (action == "reset") config.rewardOverrides - name
                    else config.rewardOverrides + (name to action))
            }
            inputValue = name
            status = when (action) { "show" -> "Saved: this reward is shown."; "hide" -> "Saved: this reward is hidden."; else -> "Item override removed; reward mode applies." }
            error = false
            rebuildWidgets()
        } catch (failure: Exception) {
            status = "Could not save: ${failure.message ?: failure.javaClass.simpleName}"
            error = true
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        g.fill(0, 0, width, height, 0x6008090B)
        HudBackdrop.beginFrame(g)
        HudBackdrop.capsule(g, panelX.toFloat(), panelY.toFloat(), panelWidth.toFloat(), panelHeight.toFloat(), 12f, 1f)
        SettingsPaint.rounded(g, panelX, panelY, panelWidth, panelHeight, 12, 0xCF17191C.toInt())
        val margin = if (height < 310) 12 else 18
        val left = panelX + margin
        val contentWidth = panelWidth - margin * 2
        HudText.draw(g, "Skyglint / Reward rules", left, panelY + 12, SettingsPaint.INK)
        HudText.draw(g, HudText.fit(status, contentWidth), left, panelY + 32,
            if (error) 0xFFE4A29B.toInt() else SettingsPaint.MUTED)
        SettingsPaint.rounded(g, left, panelY + 49, contentWidth, rowHeight + 6, 6, 0xFF33363B.toInt())
        SettingsPaint.rounded(g, left + 1, panelY + 50, contentWidth - 2, rowHeight + 4, 5, 0xE51D2024.toInt())
        val count = actions.config.rewardOverrides.size
        val pages = ((count + pageSize - 1) / pageSize).coerceAtLeast(1)
        HudText.draw(g, "Item overrides · $count / 128", left, panelY + 114, SettingsPaint.MUTED)
        if (count == 0) HudText.draw(g, "No overrides. Your reward mode applies to every item.", left, panelY + 142, SettingsPaint.MUTED)
        val pageText = "${page + 1} / $pages"
        HudText.draw(g, pageText, panelX + panelWidth / 2 - HudText.width(pageText) / 2,
            panelY + panelHeight - margin - (rowHeight + 12) / 2, SettingsPaint.MUTED)
        super.extractRenderState(g, mouseX, mouseY, delta)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
}
