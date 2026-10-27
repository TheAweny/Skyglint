package dev.skyglint.fabric

import dev.skyglint.ui.SkyglintUpdatesScreen
import dev.skyglint.updates.github.*
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import net.minecraft.client.Minecraft
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
            state = state.copy(phase = Phase.UNAVAILABLE, message = "In-game updates are unavailable. Open GitHub Releases to update manually.")
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
        player.sendSystemMessage(SkyglintChat.updateAvailable(release.version))
        announcedVersion = release.version
    }

    fun open() {
        val client = Minecraft.getInstance()
        client.execute { client.gui.setScreen(SkyglintUpdatesScreen(client.gui.screen())) }
    }

    fun check(manual: Boolean = true) {
        val updater = service ?: run {
            if (manual) feedback(state.message.ifBlank { "In-game updates are unavailable. Open GitHub Releases to update manually." })
            return
        }
        if (inFlight || stopped) return
        if (state.phase == Phase.READY) {
            if (manual) feedback("The update is ready. Restart Minecraft to load it.")
            return
        }
        val now = SkyglintClient.now()
        if (lastCheck != Long.MIN_VALUE && now - lastCheck < CHECK_COOLDOWN) {
            if (manual) {
                state = state.copy(message = "Check again in ${((CHECK_COOLDOWN - (now - lastCheck)) / 60_000 + 1).coerceAtMost(10)} min. The last result is saved; releases are available on GitHub.")
                feedback(state.message)
            }
            return
        }
        lastCheck = now
        state = state.copy(phase = Phase.CHECKING, message = "Checking the latest GitHub release…")
        submit({ updater.check() }) { result ->
            when (result) {
                is UpdateCheck.Available -> {
                    offer = result.release
                    state = state.copy(phase = Phase.AVAILABLE, availableVersion = result.release.version,
                        message = "A new version for Minecraft 26.2 is available. Choose Download below.")
                }
                UpdateCheck.UpToDate -> {
                    offer = null
                    state = state.copy(phase = Phase.UP_TO_DATE, availableVersion = null, message = "You are up to date.")
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
            feedback("Check for updates first: /skyglint update check")
            return
        }
        state = state.copy(phase = Phase.DOWNLOADING, message = "Downloading and verifying the JAR. You can close this screen.")
        submit({ updater.download(release) }) { result ->
            when (result) {
                is UpdateDownload.Ready -> state = state.copy(phase = Phase.READY, availableVersion = result.version,
                    message = "Update verified and installed. Restart Minecraft to load the new version.")
                is UpdateDownload.AlreadyInstalled -> state = state.copy(phase = Phase.READY, availableVersion = result.version,
                    message = "This version or a newer one is already in mods. Restart Minecraft to load it.")
                is UpdateDownload.Failed -> state = state.copy(phase = Phase.FAILED, message = friendly(result.reason))
            }
            feedback(state.message)
        }
    }

    fun setAutomaticChecks(enabled: Boolean) {
        if (inFlight || stopped) return
        submit({ SkyglintClient.config.update { it.copy(checkUpdates = enabled) }; enabled }) {
            state = state.copy(message = if (it) "Update checks on SkyBlock entry are enabled." else "Update checks on SkyBlock entry are disabled.")
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
                            state = state.copy(phase = Phase.FAILED, message = "The operation could not finish. Releases are available on GitHub.")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            inFlight = false
            state = state.copy(phase = Phase.FAILED, message = "Update checking is unavailable. Releases are available on GitHub.")
        }
    }

    val busy: Boolean get() = inFlight
    val canDownload: Boolean get() = offer != null && !inFlight && state.phase != Phase.READY
    private fun feedback(message: String) = SkyglintChat.send(message, when (state.phase) {
        Phase.FAILED -> SkyglintChat.Tone.ERROR
        Phase.UNAVAILABLE -> SkyglintChat.Tone.WARNING
        Phase.READY, Phase.UP_TO_DATE -> SkyglintChat.Tone.SUCCESS
        else -> SkyglintChat.Tone.INFO
    })
    private fun friendly(reason: String): String = when {
        reason.startsWith("installation_") -> "In-game updates require a regular JAR in mods. Use GitHub Releases for this installation."
        reason == "fabric_loader_update_required" -> "Update Fabric Loader before installing this version. Releases are available on GitHub."
        reason == "unsupported_java" -> "Java 25 is required. Releases are available on GitHub."
        reason == "http_403" || reason == "http_429" -> "GitHub is temporarily limiting requests. Try later or open Releases."
        reason == "http_404" -> "No public release is available yet. Check GitHub Releases."
        reason == "destination_already_exists" -> "The update file already exists and was preserved. Check your mods folder."
        reason == "mods_directory_read_only" || reason == "AccessDeniedException" || reason == "SecurityException" ->
            "Cannot write to the installation folder. Download the JAR from GitHub Releases."
        reason == "staging_requires_same_filesystem" || reason == "UnsupportedOperationException" || reason == "FileSystemException" ->
            "This filesystem cannot safely install the update. Use GitHub Releases."
        reason == "update_cancelled" -> "Update cancelled. Your current version is unchanged."
        reason.startsWith("download_") || reason.startsWith("jar_") || reason.startsWith("invalid_") ||
            reason.contains("mismatch") || reason.startsWith("untrusted_") -> "The update failed verification. Your current version is unchanged; use GitHub Releases."
        else -> "Could not retrieve the update. Try later or open GitHub Releases."
    }

    override fun close() {
        stopped = true
        worker.shutdownNow()
    }

    private const val CHECK_COOLDOWN = 10 * 60_000L
}
