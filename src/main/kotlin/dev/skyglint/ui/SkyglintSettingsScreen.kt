package dev.skyglint.ui

import dev.skyglint.fabric.SkyglintClient
import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.settings.SettingsButton
import dev.skyglint.ui.settings.SettingsPaint
import dev.skyglint.ui.text.HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale

/** Persistent native settings: each control is keyboard/narration accessible and commits immediately. */
class SkyglintSettingsScreen(private val parent: Screen?) : Screen(Component.literal("Skyglint")) {
    private var status = "Настройки сохраняются автоматически"
    private var error = false
    private val buttons = mutableListOf<SettingsButton>()
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var sceneIndex = -1
    private val scenes = listOf("objective" to "Цель", "skill" to "Навык", "skyblock" to "SB XP",
        "levelup" to "Повышение", "rare" to "Редкий дроп", "unknown" to "Без данных", "sequence" to "События по очереди",
        "idle" to "Локация", "event" to "Расписание", "petlevelup" to "Уровень питомца", "skilllevelup" to "Уровень навыка",
        "garden" to "Garden", "dungeon" to "Catacombs", "election" to "Выборы", "ranks" to "Ранги", "mining" to "Майнинг", "nucleus" to "Nucleus run",
        "confirmations" to "Подтверждения", "slayerpets" to "Slayer / питомцы", "fishing" to "Рыбалка")

    override fun init() {
        buttons.clear()
        val compact = height < 370
        val gap = if (compact) 2 else 6
        val rowHeight = if (compact) 18 else 30
        val headerHeight = if (compact) 40 else 48
        val margin = if (compact) 6 else 16
        val controlCount = 16
        val singleColumnHeight = headerHeight + controlCount * (rowHeight + gap) + rowHeight + margin * 2
        val columns = if (singleColumnHeight > height - 8 && width >= 280) 2 else 1
        panelWidth = minOf(if (columns == 2) 480 else 370, width - 20).coerceAtLeast(160)
        val rows = (controlCount + columns - 1) / columns
        panelHeight = headerHeight + rows * (rowHeight + gap) + rowHeight + margin * 2
        panelX = (width - panelWidth) / 2
        panelY = ((height - panelHeight) / 2).coerceAtLeast(4)
        val controlWidth = (panelWidth - margin * 2 - gap * (columns - 1)) / columns
        var index = 0

        fun control(caption: String, value: () -> String = { "" }, state: (() -> Boolean)? = null,
                    hint: String, persisted: Boolean = true, transientStatus: String = "Демонстрация не меняет игровые данные", action: () -> Unit) {
            val x = panelX + margin + index % columns * (controlWidth + gap)
            val y = panelY + headerHeight + index / columns * (rowHeight + gap)
            index++
            val button = SettingsButton(x, y, controlWidth, rowHeight, caption, value, state, hint) {
                try { action(); status = if (persisted) "Сохранено" else transientStatus; error = false }
                catch (failure: Exception) {
                    status = "Не удалось сохранить: ${failure.message ?: failure.javaClass.simpleName}"
                    error = true
                }
                buttons.forEach(SettingsButton::refreshMessage)
            }
            buttons += button
            addRenderableWidget(button)
        }
        control("Интерфейс", { onOff(SkyglintClient.config.current.enabled) }, { SkyglintClient.config.current.enabled },
            "Замена bossbar включается только в подтверждённой сессии SkyBlock.") {
            SkyglintClient.setEnabled(!SkyglintClient.config.current.enabled)
        }
        control("Логирование", { onOff(SkyglintClient.config.current.loggingEnabled) }, { SkyglintClient.config.current.loggingEnabled },
            "Диагностика событий и решений HUD. Выбор сохраняется после перезапуска игры.") {
            SkyglintClient.setLogging(!SkyglintClient.config.current.loggingEnabled)
        }
        control("Исходный текст", { onOff(SkyglintClient.config.current.captureRawText) }, { SkyglintClient.config.current.captureRawText },
            "Сохранять исходные серверные строки HUD. Работает при включённом логировании; полезно для новых обработчиков.") {
            SkyglintClient.setRawLogging(!SkyglintClient.config.current.captureRawText)
        }
        control("Питомец", { onOff(SkyglintClient.config.current.showPets) }, { SkyglintClient.config.current.showPets },
            "Показывать активного питомца. Настоящая иконка наблюдается в меню Pets; прогресс не выдумывается.") {
            SkyglintClient.changeConfig { it.copy(showPets = !it.showPets) }
        }
        control("Масштаб", { String.format(Locale.ROOT, "%.2f×", SkyglintClient.config.current.hudScale) }, hint =
            "1× — согласованный размер прототипа: высота 45 экранных пикселей. Масштаб Minecraft GUI не увеличивает HUD повторно.") {
            val choices = listOf(.5, .75, 1.0, 1.25, 1.5, 1.75, 2.0)
            SkyglintClient.changeConfig { it.copy(hudScale = choices.firstOrNull { scale -> scale > it.hudScale + .001 } ?: choices.first()) }
        }
        control("База удержания", { "${SkyglintClient.config.current.skillTimeoutSeconds} сек." }, hint =
            "Минимум после положительного XP. Fishing — от 45 секунд; редкий темп XP адаптивно продлевает удержание до 120 секунд. Снимки tab и локация не включают навык.") {
            val choices = listOf(10, 15, 30, 60, 120)
            SkyglintClient.changeConfig { it.copy(skillTimeoutSeconds = choices.firstOrNull { seconds -> seconds > it.skillTimeoutSeconds } ?: choices.first()) }
        }
        control("Демонстрация", { onOff(SkyglintClient.preview) }, { SkyglintClient.preview },
            "Показать изолированные примерные данные с отметкой PREVIEW. Настройка не сохраняется.", persisted = false) {
            SkyglintClient.setPreview(!SkyglintClient.preview)
        }
        control("Сценарий", { scenes.getOrNull(sceneIndex)?.second ?: "Выбрать" }, hint =
            "Цель → навык → SkyBlock XP → повышение уровня → редкий предмет → неизвестный прогресс. Выбор включает демонстрацию.", persisted = false) {
            sceneIndex = (sceneIndex + 1) % scenes.size
            SkyglintClient.setPreviewScenario(scenes[sceneIndex].first)
        }
        control("Сброс размера", { "100%" }, hint =
            "Вернуть согласованный размер 1×. Остальные настройки, включая логирование, сохраняются.") {
            SkyglintClient.changeConfig { it.copy(hudScale = 1.0) }
        }
        control("Отступ сверху", { SkyglintClient.config.current.topOffset.toString() }, hint =
            "Расстояние от верхнего края в единицах игрового GUI. Переключение: 8, 12, 18, 24, 32, 48, 64.") {
            val choices = listOf(8, 12, 18, 24, 32, 48, 64)
            SkyglintClient.changeConfig { it.copy(topOffset = choices.firstOrNull { offset -> offset > it.topOffset } ?: choices.first()) }
        }
        control("Частый дроп", { if (SkyglintClient.config.current.rewardMode == "filtered") "Скрыт" else "Показан" },
            hint = "Скрывает жемчуг, обычные Eye of Ender, плоть и частые награды. Summoning Eye остаётся. Правило предмета: /skyglint drops hide|show|reset <название>.") {
            SkyglintClient.changeConfig { it.copy(rewardMode = if (it.rewardMode == "filtered") "all" else "filtered") }
        }
        control("Полный чат", { onOff(SkyglintClient.config.current.captureChat) }, { SkyglintClient.config.current.captureChat },
            hint = "Записывать все полученные сообщения, включая личные и скрытые другими модами. Независимо от исходного HUD текста; включение также включает логирование.") {
            SkyglintClient.setChatLogging(!SkyglintClient.config.current.captureChat)
        }
        control("Вся диагностика", { if (SkyglintClient.config.current.let { it.loggingEnabled && it.captureRawText && it.captureChat }) "Вкл." else "Включить" },
            hint = "Включить и сохранить события, исходные HUD/menu snapshots и весь полученный чат, включая личный.") {
            SkyglintClient.setDiagnostics(true)
        }
        control("Архив логов", { "ZIP" }, hint = "Собрать локальный отчёт; он может содержать личный чат. Ничего не отправляется автоматически.", persisted = false) {
            SkyglintClient.exportDiagnostics()
        }
        control("Dungeon HUD", { if (SkyglintClient.config.current.detailedDungeonHud) "Подробно" else "Компактно" },
            hint = "По умолчанию — стадия, score/rank и secrets, без питомца. Подробно: дополнительные счётчики, puzzles и оценка score при полной базе данных.") {
            SkyglintClient.changeConfig { it.copy(detailedDungeonHud = !it.detailedDungeonHud) }
        }
        control("Обновления", { "GitHub" }, hint = "Проверка новой версии, явное скачивание и настройка проверки при входе. Новая версия загружается после перезапуска.", persisted = false,
            transientStatus = "Окно обновлений открыто") {
            GitHubUpdates.open()
        }
        addRenderableWidget(SettingsButton(panelX + margin, panelY + headerHeight + rows * (rowHeight + gap),
            panelWidth - margin * 2, rowHeight, "Готово", { "Esc" }, hint = "Вернуться в игру. Изменения уже сохранены.") { onClose() })
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x70070C14)
        HudBackdrop.beginFrame(graphics)
        HudBackdrop.capsule(graphics, panelX.toFloat(), panelY.toFloat(), panelWidth.toFloat(), panelHeight.toFloat(), 12f, 1f)
        SettingsPaint.rounded(graphics, panelX, panelY, panelWidth, panelHeight, 12, 0xC0141C27.toInt())
        HudText.draw(graphics, "Skyglint", panelX + 16, panelY + 10, SettingsPaint.INK)
        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate((panelX + 16).toFloat(), (panelY + 25).toFloat())
            graphics.pose().scale(.75f, .75f)
            HudText.draw(graphics, HudText.fit(status, ((panelWidth - 32) / .75f).toInt()), 0, 0,
                if (error) 0xFFFFB1AA.toInt() else SettingsPaint.MUTED)
        } finally { graphics.pose().popMatrix() }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
    private fun onOff(value: Boolean) = if (value) "включено" else "выключено"
}
