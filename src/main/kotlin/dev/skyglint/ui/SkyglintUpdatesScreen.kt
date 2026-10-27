package dev.skyglint.ui

import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.settings.SettingsButton
import dev.skyglint.ui.settings.SettingsPaint
import dev.skyglint.ui.text.HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Util

/** Same native controls/material as settings; preview states cannot perform update actions. */
class SkyglintUpdatesScreen(
    private val parent: Screen?,
    private val previewState: GitHubUpdates.View? = null,
) : Screen(Component.literal("Обновления Skyglint")) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private lateinit var checkButton: SettingsButton
    private lateinit var downloadButton: SettingsButton
    private lateinit var toggleButton: SettingsButton
    private lateinit var releaseButton: SettingsButton
    private var readyCaption = false
    private val view get() = previewState ?: GitHubUpdates.view

    override fun init() {
        readyCaption = view.phase == GitHubUpdates.Phase.READY
        val compact = height < 310
        val rowHeight = if (compact) 22 else 28
        val gap = if (compact) 4 else 6
        val margin = 12
        panelWidth = minOf(430, width - 20).coerceAtLeast(180)
        panelHeight = minOf(280, height - 16).coerceAtLeast(180)
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        val controlWidth = (panelWidth - margin * 2 - gap) / 2
        val controlY = panelY + panelHeight - margin - 3 * rowHeight - 2 * gap
        checkButton = addRenderableWidget(SettingsButton(panelX + margin, controlY, controlWidth, rowHeight,
            "Проверить", hint = "Проверить последний публичный релиз. Повторная проверка — раз в 10 минут; JAR не скачивается.") {
            GitHubUpdates.check()
        })
        downloadButton = addRenderableWidget(SettingsButton(panelX + margin + controlWidth + gap, controlY, controlWidth, rowHeight,
            if (readyCaption) "Перезапустите игру" else "Скачать", hint = "Скачать и проверить JAR для следующего запуска Minecraft. Игра продолжит работать.") {
            GitHubUpdates.download()
        })
        toggleButton = addRenderableWidget(SettingsButton(panelX + margin, controlY + rowHeight + gap,
            panelWidth - margin * 2, rowHeight, "Проверять при входе", toggled = { view.automaticChecks },
            hint = "Одна проверка при первом входе в SkyBlock за запуск игры. Выбор сохраняется; автоматического скачивания нет.") {
            GitHubUpdates.setAutomaticChecks(!view.automaticChecks)
        })
        releaseButton = addRenderableWidget(SettingsButton(panelX + margin, controlY + 2 * (rowHeight + gap), controlWidth, rowHeight,
            "Релизы GitHub", hint = "Открыть github.com/TheAweny/Skyglint/releases в обычном браузере.") {
            Util.getPlatform().openUri(GitHubUpdates.releasesUri)
        })
        addRenderableWidget(SettingsButton(panelX + margin + controlWidth + gap, controlY + 2 * (rowHeight + gap), controlWidth, rowHeight,
            "Назад", { "Esc" }, hint = "Закрыть окно. Начатая загрузка безопасно продолжится в фоне.") { onClose() })
        refreshControls()
    }

    override fun tick() {
        if (readyCaption != (view.phase == GitHubUpdates.Phase.READY)) rebuildWidgets()
        refreshControls()
    }

    private fun refreshControls() {
        val preview = previewState != null
        checkButton.active = !preview && !GitHubUpdates.busy && view.phase != GitHubUpdates.Phase.READY
        downloadButton.active = !preview && GitHubUpdates.canDownload
        toggleButton.active = !preview && !GitHubUpdates.busy
        releaseButton.active = !preview
        listOf(checkButton, downloadButton, toggleButton).forEach(SettingsButton::refreshMessage)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x70070C14)
        HudBackdrop.beginFrame(graphics)
        HudBackdrop.capsule(graphics, panelX.toFloat(), panelY.toFloat(), panelWidth.toFloat(), panelHeight.toFloat(), 12f, 1f)
        SettingsPaint.rounded(graphics, panelX, panelY, panelWidth, panelHeight, 12, 0xC0141C27.toInt())
        val current = view
        val compact = height < 310
        val line = if (compact) 14 else 17
        val left = panelX + 14
        HudText.draw(graphics, "Обновления Skyglint${if (previewState != null) " · PREVIEW" else ""}", left, panelY + 12, SettingsPaint.INK)
        HudText.draw(graphics, HudText.fit("Установлено: ${current.currentVersion} · Minecraft 26.2", panelWidth - 28),
            left, panelY + 34, SettingsPaint.MUTED)
        val heading = when (current.phase) {
            GitHubUpdates.Phase.IDLE -> "Проверка по кнопке или при входе в SkyBlock"
            GitHubUpdates.Phase.CHECKING -> "Проверка обновления…"
            GitHubUpdates.Phase.AVAILABLE -> "Доступна версия ${current.availableVersion ?: "—"}"
            GitHubUpdates.Phase.DOWNLOADING -> "Загрузка версии ${current.availableVersion ?: "—"}…"
            GitHubUpdates.Phase.READY -> "Перезапустите Minecraft · ${current.availableVersion ?: "—"}"
            GitHubUpdates.Phase.UP_TO_DATE -> "Установлена актуальная версия"
            GitHubUpdates.Phase.UNAVAILABLE -> "Установка вручную через GitHub"
            GitHubUpdates.Phase.FAILED -> "Не удалось завершить обновление"
        }
        HudText.draw(graphics, HudText.fit(heading, panelWidth - 28), left, panelY + 53,
            if (current.phase == GitHubUpdates.Phase.FAILED) 0xFFFFB1AA.toInt() else SettingsPaint.ACCENT)
        val message = current.message.ifBlank { "Скачивание начинается только по вашему нажатию. Новая версия загрузится после перезапуска игры." }
        wrapped(message, panelWidth - 28).take(if (compact) 3 else 4).forEachIndexed { index, text ->
            HudText.draw(graphics, text, left, panelY + 75 + index * line, SettingsPaint.MUTED)
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun wrapped(text: String, maximumWidth: Int): List<String> {
        val result = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val proposed = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && HudText.width(proposed) > maximumWidth) {
                result += line
                line = word
            } else line = proposed
        }
        if (line.isNotEmpty()) result += HudText.fit(line, maximumWidth)
        return result
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
}
