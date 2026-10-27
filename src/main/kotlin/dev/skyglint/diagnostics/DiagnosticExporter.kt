package dev.skyglint.diagnostics

import com.google.gson.GsonBuilder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/** Explicit local export. The caller owns background scheduling and should flush DiagnosticLog first. */
class DiagnosticExporter(private val limits: Limits = Limits()) {
    data class Limits(
        val eventBytes: Int = 2 * 1024 * 1024,
        val minecraftBytes: Int = 8 * 1024 * 1024,
        val totalBytes: Int = 20 * 1024 * 1024,
    ) {
        init {
            require(eventBytes in 1..2 * 1024 * 1024)
            require(minecraftBytes in 1..8 * 1024 * 1024)
            require(totalBytes in MANIFEST_RESERVE + 1..20 * 1024 * 1024)
        }
    }

    /** Returns a finished ZIP only. Never scans recursively or reads the user's raw configuration file. */
    fun export(gameConfigSkyglintDir: Path, gameLogDir: Path, exportsDir: Path, currentVersion: String,
               preferences: Map<String, Any?> = emptyMap()): Path {
        val destinationDirectory = exportsDir.toAbsolutePath().normalize()
        requireNoSymlinkAncestors(destinationDirectory)
        Files.createDirectories(destinationDirectory)
        requireNoSymlinkAncestors(destinationDirectory)
        val eventsRoot = gameConfigSkyglintDir.toAbsolutePath().normalize().resolve("logs")
        val gameLogsRoot = gameLogDir.toAbsolutePath().normalize()
        val sources = listOf(
            Source(eventsRoot, "skyglint-events.jsonl", "skyglint/skyglint-events.jsonl", limits.eventBytes),
            Source(eventsRoot, "skyglint-events.1.jsonl", "skyglint/skyglint-events.1.jsonl", limits.eventBytes),
            Source(eventsRoot, "skyglint-events.2.jsonl", "skyglint/skyglint-events.2.jsonl", limits.eventBytes),
            Source(eventsRoot, "skyglint-events.3.jsonl", "skyglint/skyglint-events.3.jsonl", limits.eventBytes),
            Source(gameLogsRoot, "latest.log", "minecraft/latest.log", limits.minecraftBytes),
        )
        val id = UUID.randomUUID().toString()
        val created = Instant.now()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(created)
        val destination = destinationDirectory.resolve("skyglint-diagnostics-$stamp-$id.zip")
        val temporary = Files.createTempFile(destinationDirectory, ".skyglint-report-", ".partial")
        val records = mutableListOf<Map<String, Any?>>()
        var remaining = limits.totalBytes - MANIFEST_RESERVE
        var totalPayload = 0
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                for (source in sources) {
                    val snapshot = snapshot(source, remaining)
                    records += snapshot.status
                    if (snapshot.bytes != null) {
                        entry(zip, source.entry, snapshot.bytes)
                        remaining -= snapshot.readBytes
                        totalPayload += snapshot.bytes.size
                    }
                }
                val manifest = linkedMapOf<String, Any?>(
                    "formatVersion" to 1,
                    "modVersion" to currentVersion.take(120),
                    "createdAtUtc" to created.toString(),
                    "localExportOnly" to true,
                    "includesChat" to true,
                    "privacyNotice" to "Logs may contain player names, public/private chat and server addresses. No automatic redaction or upload is performed.",
                    "payloadBytes" to totalPayload,
                    "payloadLimitBytes" to limits.totalBytes - MANIFEST_RESERVE,
                    "totalUncompressedLimitBytes" to limits.totalBytes,
                    "preferences" to safePreferences(preferences),
                    "files" to records,
                )
                val json = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(manifest)
                    .toByteArray(UTF_8)
                val readme = PRIVACY.toByteArray(UTF_8)
                check(json.size + readme.size <= MANIFEST_RESERVE) { "Diagnostic manifest exceeded its reserved size" }
                entry(zip, "manifest.json", json)
                entry(zip, "README.txt", readme)
            }
            // Names are unique and no REPLACE_EXISTING option is used.
            try { Files.move(temporary, destination, ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, destination) }
            return destination
        } finally { Files.deleteIfExists(temporary) }
    }

    private data class Source(val directory: Path, val file: String, val entry: String, val limit: Int)
    private data class Snapshot(val bytes: ByteArray?, val status: Map<String, Any?>, val readBytes: Int = 0)

    private fun snapshot(source: Source, remaining: Int): Snapshot {
        val path = source.directory.resolve(source.file)
        val info = linkedMapOf<String, Any?>("entry" to source.entry)
        if (remaining <= 0) return Snapshot(null, info + ("status" to "total_limit_reached"))
        try {
            requireNoSymlinkAncestors(path)
            if (!Files.exists(path, NOFOLLOW_LINKS)) return Snapshot(null, info + ("status" to "missing"))
            val before = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!before.isRegularFile) return Snapshot(null, info + ("status" to "not_regular_file"))
            val maximum = min(source.limit, remaining)
            val count = min(before.size(), maximum.toLong()).toInt()
            val offset = before.size() - count
            val buffer = ByteBuffer.allocate(count)
            Files.newByteChannel(path, setOf<OpenOption>(READ, NOFOLLOW_LINKS)).use { channel ->
                channel.position(offset)
                while (buffer.hasRemaining()) {
                    val read = channel.read(buffer)
                    if (read < 0) break
                }
            }
            val captured = buffer.position()
            var bytes = buffer.array().copyOf(captured)
            // A tail cut can start inside UTF-8 or a JSONL record. Omit that incomplete leading line.
            if (offset > 0) {
                val newline = bytes.indexOf('\n'.code.toByte())
                bytes = if (newline >= 0) bytes.copyOfRange(newline + 1, bytes.size) else ByteArray(0)
            }
            var changed = captured != count
            var change = if (changed) "short_read" else null
            try {
                val after = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (before.fileKey() != after.fileKey() || before.size() != after.size() ||
                    before.lastModifiedTime() != after.lastModifiedTime()) {
                    changed = true; change = "source_changed_during_capture"
                }
            } catch (_: Exception) { changed = true; change = "source_rotated_or_missing_after_capture" }
            info["status"] = "included"
            info["originalBytes"] = before.size()
            info["capturedBytes"] = bytes.size
            info["readBytes"] = captured
            info["truncated"] = offset > 0 || captured != count
            info["sourceChanged"] = changed
            if (change != null) info["change"] = change
            return Snapshot(bytes, info, captured)
        } catch (failure: Exception) {
            // Safe identifiers only: exception messages can contain full local paths/usernames.
            return Snapshot(null, info + mapOf("status" to "skipped", "error" to failure.javaClass.simpleName))
        }
    }

    private fun safePreferences(input: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (key in SAFE_PREFERENCES) {
            when (val value = input[key]) {
                is Boolean -> result[key] = value
                is Number -> value.toDouble().takeIf(Double::isFinite)?.let { result[key] = it }
                is String -> result[key] = value.take(120)
            }
        }
        val rules = input["rewardOverrides"] as? Map<*, *>
        if (rules != null) result["rewardOverrides"] = rules.entries.asSequence().mapNotNull { (name, rule) ->
            if (name !is String || rule !is String || name.length !in 1..120 || name.any(Char::isISOControl)) null
            else if (rule == "show" || rule == "hide") name to rule else null
        }.take(128).toMap()
        return result
    }

    private fun requireNoSymlinkAncestors(path: Path) {
        var current = path.root ?: error("Absolute path required")
        for (part in path) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "Symbolic links are not exported" }
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    companion object {
        private const val MANIFEST_RESERVE = 64 * 1024
        private val SAFE_PREFERENCES = setOf("schemaVersion", "enabled", "loggingEnabled", "captureRawText",
            "captureChat", "skillTimeoutSeconds", "hudScale", "topOffset", "showPets", "rewardMode",
            "minecraft", "flushSucceeded", "droppedRecords", "detailedDungeonHud")
        private const val PRIVACY = """Skyglint local diagnostic export

This archive may include player names, public and private chat, server addresses,
and raw HUD text. The Minecraft latest.log can include chat even if Skyglint's chat
capture is currently disabled. includesChat=true is a conservative privacy warning,
not a claim that every archive contains private messages. No automatic redaction
or upload is performed. Review the archive before choosing to share it.

Only four fixed Skyglint event-log names, Minecraft latest.log, and explicitly
allowlisted preferences are included. No inventory files, screenshots, account
files, tokens or raw config.json are read. Logs are bounded tail snapshots, so old
lines may be omitted; concurrent writes/rotation can affect consistency. See
manifest.json for missing files, truncation, read errors and detected changes.
"""
    }
}
