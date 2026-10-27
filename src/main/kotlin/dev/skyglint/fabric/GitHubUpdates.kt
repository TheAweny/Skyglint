package dev.skyglint.fabric

import dev.skyglint.ui.SkyglintUpdatesScreen
import dev.skyglint.updates.github.*
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Client-thread presentation and one bounded, daemon I/O worker; no player data is sent. */
object GitHubUpdates : AutoCloseable {
    enum class Phase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, UP_TO_DATE, UNAVAILABLE, FAILED }
    data class View(
        val currentVersion: String,
        val phase: Phase = Phase.IDLE,
        val availableVersion: String? = null,
        val message: String = "",
        val automaticChecks: Boolean = true,
    )

    val releasesUri: URI = URI.create("https://github.com/TheAweny/Skyglint/releases")
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1),
        { task -> Thread(task, "skyglint-github-updates").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private var service: GitHubUpdateService? = null
    private var state = View("—")
    private var offer: UpdateRelease? = null
    private var automaticAttempted = false
    private var announcedVersion: String? = null
    private var lastCheck = Long.MIN_VALUE
    private var inFlight = false
    @Volatile private var stopped = false

    val view: View get() = state.copy(automaticChecks = SkyglintClient.config.current.checkUpdates)

    fun initialize() {
        if (service != null) return
        try {
            val loader = FabricLoader.getInstance()
            val own = loader.getModContainer("reborn").orElse(null) ?: return
            val path = if (own.origin.kind == ModOrigin.Kind.PATH) own.origin.paths.singleOrNull() else null
            val configuredMods = System.getProperty("fabric.modsFolder")?.let(Path::of)
            val mods = configuredMods?.toAbsolutePath() ?: loader.gameDir.resolve("mods")
            val version = own.metadata.version.friendlyString
            state = View(version)
            service = GitHubUpdateService(UpdateInstallation(mods, loader.configDir.resolve("skyglint/downloads"),
                path ?: loader.gameDir, version,
                loader.getModContainer("fabricloader").orElse(null)?.metadata?.version?.friendlyString ?: "0.0.0",
                production = !loader.isDevelopmentEnvironment && path != null && loader.rawGameVersion == "26.2"))
        } catch (_: Exception) {
            state = state.copy(phase = Phase.UNAVAILABLE, message = "Автообновление недоступно. Используйте релизы GitHub.")
        }
    }

    fun onSkyBlockLogin() {
        if (automaticAttempted) return
        automaticAttempted = true
        if (SkyglintClient.config.current.checkUpdates) check(manual = false)
    }

    /** Called only for a confirmed live SkyBlock session; disconnects never consume the notice. */
    fun onSkyBlockTick() {
        if (stopped || state.phase != Phase.AVAILABLE) return
        val release = offer ?: return
        if (announcedVersion == release.version) return
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(Component.literal("[Skyglint] Доступна версия ${release.version}. ")
            .append(Component.literal("[Обновление]").setStyle(Style.EMPTY.withColor(0xB5DDF4)
                .withUnderlined(true).withClickEvent(ClickEvent.RunCommand("/skyglint update")))))
        announcedVersion = release.version
    }

    fun open() {
        val client = Minecraft.getInstance()
        client.execute { client.gui.setScreen(SkyglintUpdatesScreen(client.gui.screen())) }
    }

    fun check(manual: Boolean = true) {
        val updater = service ?: run {
            if (manual) feedback(state.message.ifBlank { "Автообновление недоступно. Используйте релизы GitHub." })
            return
        }
        if (inFlight || stopped) return
        if (state.phase == Phase.READY) {
            if (manual) feedback("Обновление подготовлено. Перезапустите Minecraft.")
            return
        }
        val now = SkyglintClient.now()
        if (lastCheck != Long.MIN_VALUE && now - lastCheck < CHECK_COOLDOWN) {
            if (manual) {
                state = state.copy(message = "Повторная проверка доступна через ${((CHECK_COOLDOWN - (now - lastCheck)) / 60_000 + 1).coerceAtMost(10)} мин. Последний результат сохранён; релизы доступны на GitHub.")
                feedback(state.message)
            }
            return
        }
        lastCheck = now
        state = state.copy(phase = Phase.CHECKING, message = "Проверяем публичный релиз GitHub…")
        submit({ updater.check() }) { result ->
            when (result) {
                is UpdateCheck.Available -> {
                    offer = result.release
                    state = state.copy(phase = Phase.AVAILABLE, availableVersion = result.release.version,
                        message = "Новая версия для Minecraft 26.2. Скачивание — по кнопке ниже.")
                }
                UpdateCheck.UpToDate -> {
                    offer = null
                    state = state.copy(phase = Phase.UP_TO_DATE, availableVersion = null, message = "Установлена актуальная версия.")
                }
                is UpdateCheck.Unavailable -> {
                    offer = null
                    state = state.copy(phase = Phase.UNAVAILABLE, availableVersion = null, message = friendly(result.reason))
                }
                is UpdateCheck.Failed -> {
                    offer = null
                    state = state.copy(phase = Phase.FAILED, availableVersion = null, message = friendly(result.reason))
                }
            }
            if (manual && result !is UpdateCheck.Available) feedback(state.message)
        }
    }

    fun download() {
        if (inFlight || stopped || state.phase == Phase.READY) return
        val updater = service ?: return
        val release = offer
        if (release == null) {
            feedback("Сначала проверьте обновления: /skyglint update check")
            return
        }
        state = state.copy(phase = Phase.DOWNLOADING, message = "Скачиваем и проверяем JAR. Можно закрыть это окно.")
        submit({ updater.download(release) }) { result ->
            when (result) {
                is UpdateDownload.Ready -> state = state.copy(phase = Phase.READY, availableVersion = result.version,
                    message = "JAR проверен и установлен. Перезапустите Minecraft для загрузки новой версии.")
                is UpdateDownload.AlreadyInstalled -> state = state.copy(phase = Phase.READY, availableVersion = result.version,
                    message = "Эта или более новая версия уже находится в mods. Перезапустите Minecraft.")
                is UpdateDownload.Failed -> state = state.copy(phase = Phase.FAILED, message = friendly(result.reason))
            }
            feedback(state.message)
        }
    }

    fun setAutomaticChecks(enabled: Boolean) {
        if (inFlight || stopped) return
        submit({ SkyglintClient.config.update { it.copy(checkUpdates = enabled) }; enabled }) {
            state = state.copy(message = if (it) "Проверка при входе в SkyBlock включена." else "Проверка при входе в SkyBlock выключена.")
        }
    }

    private fun <T> submit(operation: () -> T, complete: (T) -> Unit) {
        inFlight = true
        try {
            worker.execute {
                try {
                    val result = operation()
                    Minecraft.getInstance().execute {
                        if (!stopped) { inFlight = false; complete(result) }
                    }
                } catch (_: Exception) {
                    Minecraft.getInstance().execute {
                        if (!stopped) {
                            inFlight = false
                            state = state.copy(phase = Phase.FAILED, message = "Не удалось завершить операцию. Релизы доступны на GitHub.")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            inFlight = false
            state = state.copy(phase = Phase.FAILED, message = "Проверка недоступна. Релизы доступны на GitHub.")
        }
    }

    val busy: Boolean get() = inFlight
    val canDownload: Boolean get() = offer != null && !inFlight && state.phase != Phase.READY
    private fun feedback(message: String) { Minecraft.getInstance().player?.sendSystemMessage(Component.literal("[Skyglint] $message")) }
    private fun friendly(reason: String): String = when {
        reason.startsWith("installation_") -> "Автообновление доступно для обычного JAR в mods. Используйте релизы GitHub."
        reason == "fabric_loader_update_required" -> "Для новой версии сначала обновите Fabric Loader. Релизы доступны на GitHub."
        reason == "unsupported_java" -> "Нужна Java 25. Релизы доступны на GitHub."
        reason == "http_403" || reason == "http_429" -> "GitHub временно ограничил запросы. Повторите позже или откройте релизы."
        reason == "http_404" -> "Публичный релиз пока недоступен. Проверить публикацию можно на GitHub."
        reason == "destination_already_exists" -> "Файл новой версии уже существует. Он сохранён; проверьте папку mods."
        reason == "mods_directory_read_only" || reason == "AccessDeniedException" || reason == "SecurityException" ->
            "Нет доступа для записи в папку установки. Скачайте JAR со страницы релизов GitHub."
        reason == "staging_requires_same_filesystem" || reason == "UnsupportedOperationException" || reason == "FileSystemException" ->
            "Файловая система не поддерживает безопасную установку. Используйте релизы GitHub."
        reason == "update_cancelled" -> "Операция отменена. Текущая версия сохранена."
        reason.startsWith("download_") || reason.startsWith("jar_") || reason.startsWith("invalid_") ||
            reason.contains("mismatch") || reason.startsWith("untrusted_") -> "Обновление не прошло проверку. Текущая версия сохранена; релизы доступны на GitHub."
        else -> "Не удалось получить обновление. Повторите позже или откройте релизы GitHub."
    }

    override fun close() {
        stopped = true
        worker.shutdownNow()
    }

    private const val CHECK_COOLDOWN = 10 * 60_000L
}
