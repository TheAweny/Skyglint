package dev.skyglint.config

import com.google.gson.JsonParser
import dev.skyglint.diagnostics.DiagnosticLog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DiagnosticLogTest {
    @TempDir lateinit var directory: Path

    @Test fun `background flush does not acquire the producer monitor while waiting for disk`() {
        val log = DiagnosticLog(directory, { ModConfig(loggingEnabled = true) })
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            log.record("fixture", "queued_before_export")
            synchronized(log) {
                val barrier = executor.submit<Boolean> { log.flush() }
                assertTrue(barrier.get(1, java.util.concurrent.TimeUnit.SECONDS))
            }
        } finally { executor.shutdownNow(); log.close() }
    }

    @Test fun `full chat capture is separately opted in and independent of HUD raw text`() {
        var config = ModConfig(loggingEnabled = true, captureRawText = true)
        val log = DiagnosticLog(directory, { config })
        assertFalse(log.recordChat("player", "hidden while chat capture is off"))
        config = config.copy(captureChat = true, captureRawText = false)
        assertTrue(log.recordChat("system", "Your Seal leveled up to level 71!", cancelled = true, session = "test"))
        assertTrue(log.recordChat("player", "[fixture] hello"))
        log.record("actionbar", "observed", rawText = "HUD text still disabled")
        config = config.copy(loggingEnabled = false)
        assertFalse(log.recordChat("player", "not captured while logging is off"))
        log.close()
        val records = Files.readAllLines(log.path).map { JsonParser.parseString(it).asJsonObject }
        assertEquals(3, records.size)
        assertEquals("Your Seal leveled up to level 71!", records[0]["rawText"].asString)
        assertTrue(records[0]["details"].asJsonObject["cancelled"].asBoolean)
        assertEquals("[fixture] hello", records[1]["rawText"].asString)
        assertFalse(records[2].has("rawText"))
        assertFalse(records.toString().contains("not captured"))
    }

    @Test fun `oversized chat is bounded and truncation is explicit`() {
        val log = DiagnosticLog(directory, { ModConfig(loggingEnabled = true, captureChat = true) })
        assertTrue(log.recordChat("system", "x".repeat(9000)))
        log.close()
        val record = JsonParser.parseString(Files.readAllLines(log.path).single()).asJsonObject
        assertEquals(8192, record["rawText"].asString.length)
        assertTrue(record["rawTextTruncated"].asBoolean)
    }

    @Test
    fun `disabled capture creates no file and raw text needs separate opt in`() {
        var config = ModConfig()
        val log = DiagnosticLog(directory, { config })
        assertFalse(log.record("actionbar", "received", rawText = "private signal"))
        assertFalse(Files.exists(log.path))
        config = config.copy(loggingEnabled = true)
        assertTrue(log.record("actionbar", "parsed", mapOf("kind" to "skill"), rawText = "private signal"))
        assertTrue(log.flush())
        var records = Files.readAllLines(log.path)
        assertFalse(records.single().contains("private signal"))
        config = config.copy(captureRawText = true)
        log.record("actionbar", "parsed", rawText = "relevant server signal")
        assertTrue(log.flush())
        records = Files.readAllLines(log.path)
        assertEquals("relevant server signal", JsonParser.parseString(records.last()).asJsonObject["rawText"].asString)
        config = config.copy(loggingEnabled = false)
        assertTrue(log.record("command", "logging_disabled", rawText = "must not appear", force = true))
        assertTrue(log.flush())
        assertFalse(Files.readAllLines(log.path).last().contains("must not appear"))
        log.close()
    }

    @Test
    fun `rotation retains bounded archive count and valid json lines`() {
        val log = DiagnosticLog(directory, { ModConfig(loggingEnabled = true) }, maxBytes = 300, archiveCount = 3)
        repeat(20) { log.record("test", "transition", mapOf("index" to it)) }
        assertTrue(log.flush())
        val files = Files.list(directory).use { it.toList() }
        assertEquals(4, files.size)
        files.forEach { file ->
            Files.readAllLines(file).forEach { assertTrue(JsonParser.parseString(it).isJsonObject) }
        }
        assertEquals(19, JsonParser.parseString(Files.readAllLines(log.path).last()).asJsonObject["details"].asJsonObject["index"].asInt)
        log.close()
        assertFalse(log.record("test", "closed"))
    }

    @Test
    fun `unwritable log destination does not crash its caller`() {
        val destination = directory.resolve("already-a-file")
        Files.writeString(destination, "occupied")
        val log = DiagnosticLog(destination, { ModConfig(loggingEnabled = true) })
        assertTrue(log.record("test", "transition"))
        assertFalse(log.flush())
        assertNotNull(log.lastFailure)
        assertEquals("occupied", Files.readString(destination))
        log.close()
    }

    @Test
    fun `close drains accepted records in order with caller captured privacy flags`() {
        var preferences = ModConfig(loggingEnabled = true, captureRawText = true)
        val log = DiagnosticLog(directory, { preferences })
        repeat(30) { index ->
            assertTrue(log.record("test", "queued", mapOf("index" to index), rawText = "server signal $index"))
        }
        preferences = ModConfig(loggingEnabled = false, captureRawText = false)
        log.close()
        val records = Files.readAllLines(log.path).map { JsonParser.parseString(it).asJsonObject }
        assertEquals((0 until 30).toList(), records.map { it["details"].asJsonObject["index"].asInt })
        assertEquals("server signal 29", records.last()["rawText"].asString)
        assertEquals(0L, log.droppedRecords)
        assertNull(log.lastFailure)
    }

    @Test
    fun `burst capture drains accepted records in order and accounts for queue drops`() {
        val log = DiagnosticLog(directory, { ModConfig(loggingEnabled = true) })
        val accepted = mutableListOf<Int>()
        repeat(6_000) { index ->
            if (log.record("test", "burst", mapOf("index" to index))) accepted.add(index)
        }
        assertTrue(log.flush())
        assertEquals(6_000L, accepted.size + log.droppedRecords)
        assertTrue(log.record("test", "after_burst", mapOf("index" to 6_000)))
        log.close()
        val records = Files.readAllLines(log.path).map { JsonParser.parseString(it).asJsonObject }
        assertEquals(accepted + 6_000, records.map { it["details"].asJsonObject["index"].asInt })
        assertNull(log.lastFailure)
    }
}
