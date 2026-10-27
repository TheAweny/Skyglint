package dev.skyglint.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.skyglint.diagnostics.DiagnosticExporter
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.concurrent.thread
import kotlin.test.*

class DiagnosticExporterTest {
    @TempDir lateinit var directory: Path
    private val skyglint get() = directory.resolve("config/skyglint")
    private val logs get() = directory.resolve("minecraft/logs")
    private val exports get() = skyglint.resolve("reports")

    private fun source(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun entries(path: Path): Map<String, ByteArray> = ZipFile(path.toFile()).use { zip ->
        zip.entries().asSequence().associate { entry -> entry.name to zip.getInputStream(entry).use { it.readAllBytes() } }
    }
    private fun manifest(entries: Map<String, ByteArray>): JsonObject =
        JsonParser.parseString(String(entries.getValue("manifest.json"), UTF_8)).asJsonObject

    @Test fun `fixed log whitelist preserves originals and includes only explicit preference fields`() {
        val event = skyglint.resolve("logs/skyglint-events.jsonl")
        source(event, "{\"source\":\"fixture\"}\n")
        source(skyglint.resolve("logs/skyglint-events.1.jsonl"), "archive\n")
        source(skyglint.resolve("logs/skyglint-events.4.jsonl"), "not allowed\n")
        source(logs.resolve("latest.log"), "[fixture] public or private chat\n")
        source(logs.resolve("debug.log"), "not allowed\n")
        source(skyglint.resolve("config.json"), "{\"token\":\"must never read\"}")
        val original = Files.readAllBytes(event)
        val zip = DiagnosticExporter().export(skyglint, logs, exports, "0.1.5", mapOf(
            "loggingEnabled" to true, "captureChat" to false, "hudScale" to 1.0,
            "token" to "must never export", "account" to "not allowed",
            "rewardOverrides" to mapOf("Summoning Eye" to "show", "Bad" to "unknown"),
        ))
        val values = entries(zip)
        assertEquals(setOf("skyglint/skyglint-events.jsonl", "skyglint/skyglint-events.1.jsonl", "minecraft/latest.log",
            "manifest.json", "README.txt"), values.keys)
        assertContentEquals(original, Files.readAllBytes(event))
        assertContentEquals(original, values.getValue("skyglint/skyglint-events.jsonl"))
        assertTrue(zip.parent == exports.toAbsolutePath().normalize())
        assertFalse(Files.list(exports).use { it.anyMatch { path -> path.fileName.toString().endsWith(".partial") } })
        val info = manifest(values)
        assertEquals("0.1.5", info["modVersion"].asString)
        assertTrue(info["includesChat"].asBoolean)
        assertTrue(info["localExportOnly"].asBoolean)
        val preferences = info["preferences"].asJsonObject
        assertFalse(preferences.has("token"))
        assertFalse(preferences.has("account"))
        assertFalse(preferences["rewardOverrides"].asJsonObject.has("Bad"))
        assertEquals("show", preferences["rewardOverrides"].asJsonObject["Summoning Eye"].asString)
        assertFalse(String(values.getValue("manifest.json"), UTF_8).contains(directory.toString()))
    }

    @Test fun `missing logs remain explicit without failing local export and filenames never collide`() {
        val exporter = DiagnosticExporter()
        val first = exporter.export(skyglint, logs, exports, "test")
        val second = exporter.export(skyglint, logs, exports, "test")
        assertNotEquals(first, second)
        assertTrue(Files.exists(first) && Files.exists(second))
        val info = manifest(entries(first))
        assertEquals(5, info["files"].asJsonArray.size())
        assertTrue(info["files"].asJsonArray.all { it.asJsonObject["status"].asString == "missing" })
        assertEquals(0, info["payloadBytes"].asInt)
    }

    @Test fun `oversized logs are tail bounded and incomplete leading records are omitted`() {
        source(skyglint.resolve("logs/skyglint-events.jsonl"), "first-xxxxxxxxxxxxxxxxxxxx\nsecond\nthird\n")
        source(logs.resolve("latest.log"), "old-line-that-is-too-long\nnew line\n")
        val zip = DiagnosticExporter(DiagnosticExporter.Limits(eventBytes = 20, minecraftBytes = 13))
            .export(skyglint, logs, exports, "test")
        val values = entries(zip)
        assertEquals("second\nthird\n", String(values.getValue("skyglint/skyglint-events.jsonl"), UTF_8))
        assertEquals("new line\n", String(values.getValue("minecraft/latest.log"), UTF_8))
        val sources = manifest(values)["files"].asJsonArray.map { it.asJsonObject }
        assertTrue(sources.first()["truncated"].asBoolean)
        assertTrue(sources.last()["truncated"].asBoolean)
    }

    @Test fun `all payload entries obey the total cap and status records explain omissions`() {
        val text = "fixture line\n".repeat(100)
        for (index in 0..3) source(skyglint.resolve("logs/skyglint-events${if (index == 0) "" else ".$index"}.jsonl"), text)
        source(logs.resolve("latest.log"), text)
        val cap = 64 * 1024 + 256
        val values = entries(DiagnosticExporter(DiagnosticExporter.Limits(eventBytes = 256, minecraftBytes = 256,
            totalBytes = cap)).export(skyglint, logs, exports, "test"))
        assertTrue(values.values.sumOf { it.size } <= cap)
        val info = manifest(values)
        assertTrue(info["payloadBytes"].asInt <= 256)
        assertTrue(info["files"].asJsonArray.any { it.asJsonObject["status"].asString == "total_limit_reached" })
    }

    @Test fun `symlink log and symlink export destination are rejected without exposing their targets`() {
        val outside = directory.resolve("private.txt")
        source(outside, "private target data\n")
        Files.createDirectories(skyglint.resolve("logs"))
        val link = skyglint.resolve("logs/skyglint-events.jsonl")
        val supported = try { Files.createSymbolicLink(link, outside); true } catch (_: Exception) { false }
        assumeTrue(supported, "Windows account cannot create symbolic links")
        val values = entries(DiagnosticExporter().export(skyglint, logs, exports, "test"))
        assertFalse(values.containsKey("skyglint/skyglint-events.jsonl"))
        assertEquals("skipped", manifest(values)["files"].asJsonArray.first().asJsonObject["status"].asString)
        assertEquals("private target data\n", Files.readString(outside))
        val redirect = directory.resolve("redirect")
        Files.createSymbolicLink(redirect, exports)
        assertFailsWith<IllegalArgumentException> { DiagnosticExporter().export(skyglint, logs, redirect, "test") }
    }

    @Test fun `concurrently growing logs still create a valid bounded zip without modifying the source`() {
        val path = logs.resolve("latest.log")
        source(path, "initial line\n".repeat(100_000))
        val alive = AtomicBoolean(true)
        val writer = thread(name = "fixture-growing-log") {
            while (alive.get()) {
                Files.writeString(path, "new fixture line\n", APPEND)
            }
        }
        val before = Files.size(path)
        val values = try { entries(DiagnosticExporter(DiagnosticExporter.Limits(minecraftBytes = 64 * 1024))
            .export(skyglint, logs, exports, "test")) }
        finally { alive.set(false); writer.join(5_000) }
        assertTrue(Files.size(path) >= before)
        assertTrue(values.getValue("minecraft/latest.log").size <= 64 * 1024)
        val latest = manifest(values)["files"].asJsonArray.last().asJsonObject
        assertEquals("included", latest["status"].asString)
        assertTrue(latest["truncated"].asBoolean)
    }
}
