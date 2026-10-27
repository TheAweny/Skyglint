package dev.skyglint.updates.github

import java.net.URI
import java.nio.file.Path

/** Blocking operations belong on the updater's single worker, never the client/render thread. */
data class UpdateInstallation(
    val modsDirectory: Path,
    val stagingDirectory: Path,
    val currentJar: Path,
    val loadedVersion: String,
    val loaderVersion: String,
    val javaFeature: Int = Runtime.version().feature(),
    val production: Boolean = true,
)

/** Contains no server-provided executable URL or filesystem path. */
data class UpdateRelease(
    val version: String,
    val tag: String,
    val assetName: String,
    val sha256: String,
    val size: Long,
    val loaderVersion: String,
)

sealed interface UpdateCheck {
    data class Available(val release: UpdateRelease) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Unavailable(val reason: String) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

sealed interface UpdateDownload {
    data class Ready(val version: String, val path: Path) : UpdateDownload
    data class AlreadyInstalled(val version: String, val path: Path) : UpdateDownload
    data class Failed(val reason: String) : UpdateDownload
}

/** Test seam: implementations must enforce maximumBytes while receiving, including error bodies. */
fun interface UpdateTransport {
    fun fetch(uri: URI, maximumBytes: Int): UpdateHttpResponse
}

data class UpdateHttpResponse(val status: Int, val body: ByteArray, val location: String? = null)
