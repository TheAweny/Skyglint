package dev.skyglint.diagnostics

import com.google.gson.GsonBuilder
import dev.skyglint.config.ModConfig
import java.io.Closeable
import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded JSONL diagnostics. Details are technical metadata, never chat bodies or inventory dumps.
 * HUD raw text and full chat capture have independent explicit preferences.
 * record() never touches the filesystem. A single daemon writer flushes every 250 ms.
 * Explicit flush/close are bounded to five seconds and belong only to lifecycle or tests.
 */
class DiagnosticLog(
    val directory: Path,
    private val config: () -> ModConfig,
    private val maxBytes: Long = 2L * 1024 * 1024,
    private val archiveCount: Int = 3,
) : Closeable {
    val path: Path = directory.resolve("skyglint-events.jsonl")
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private var sequence = 0L
    @Volatile private var closed = false
    private val queue = ArrayBlockingQueue<Work>(2048)
    private val dropped = AtomicLong()
    private val unreportedDropped = AtomicLong()
    val droppedRecords: Long get() = dropped.get()
    private var writer: BufferedWriter? = null
    private var fileBytes = 0L
    private val worker: Thread

    @Volatile
    var lastFailure: IOException? = null
        private set

    init {
        require(maxBytes >= 128) { "maxBytes must be at least 128" }
        require(archiveCount in 1..10) { "archiveCount must be between 1 and 10" }
        worker = Thread(::writeLoop, "skyglint-diagnostics").apply { isDaemon = true; start() }
    }

    /** Returns whether queued, not durability. force bypasses the flag, never the bounded queue. */
    fun record(
        source: String,
        action: String,
        details: Map<String, Any?> = emptyMap(),
        rawText: String? = null,
        force: Boolean = false,
    ): Boolean = append(source, action, details, rawText, force, chat = false)

    /** Full received chat, including messages hidden by another mod. No signatures or profile objects. */
    fun recordChat(channel: String, text: String, cancelled: Boolean = false, session: String? = null): Boolean =
        append("chat_capture", "received", mapOf("channel" to channel, "cancelled" to cancelled, "session" to session),
            text, force = false, chat = true)

    @Synchronized
    private fun append(source: String, action: String, details: Map<String, Any?>, rawText: String?,
                       force: Boolean, chat: Boolean): Boolean {
        if (closed) return false
        val preferences = config()
        if (chat && (!preferences.captureChat || !preferences.loggingEnabled)) return false
        if (!preferences.loggingEnabled && !force) return false
        val pendingLoss = unreportedDropped.getAndSet(0)
        val record = linkedMapOf<String, Any?>(
            "time" to Instant.now().toString(),
            "sequence" to ++sequence,
            "source" to source.take(80),
            "action" to action.take(100),
            "details" to details.entries.take(32).associate { it.key.take(80) to safeValue(it.value) },
        )
        if (pendingLoss > 0) record["droppedSincePrevious"] = pendingLoss
        if (preferences.loggingEnabled && (if (chat) preferences.captureChat else preferences.captureRawText) && rawText != null) {
            val limit = if (chat) 8192 else 2048
            record["rawText"] = rawText.take(limit)
            if (rawText.length > limit) record["rawTextTruncated"] = true
        }
        if (queue.offer(Work.Record(record))) return true
        dropped.incrementAndGet()
        unreportedDropped.addAndGet(pendingLoss + 1)
        return false
    }

    private fun safeValue(value: Any?): Any? = when (value) {
        null, is Boolean -> value
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        is Number -> value.toDouble().let { if (it.isFinite()) it else it.toString() }
        is Enum<*> -> value.name
        is String -> value.take(512)
        else -> "<${value.javaClass.simpleName}>"
    }

    private fun rotate() {
        Files.deleteIfExists(archive(archiveCount))
        for (index in archiveCount - 1 downTo 1) {
            val previous = archive(index)
            if (Files.exists(previous)) Files.move(previous, archive(index + 1), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(path, archive(1), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun archive(index: Int): Path = directory.resolve("skyglint-events.$index.jsonl")

    private fun writeLoop() {
        var lastFlushAt = System.nanoTime()
        try {
            while (true) {
                when (val work = queue.poll(250, TimeUnit.MILLISECONDS)) {
                    is Work.Record -> try { append(work.values) } catch (failure: Exception) {
                        dropped.incrementAndGet()
                        unreportedDropped.incrementAndGet()
                        fail(failure)
                    }
                    is Work.Barrier -> {
                        try { writer?.flush() } catch (failure: IOException) { fail(failure) }
                        finally { work.done.countDown() }
                    }
                    is Work.Stop -> { closeWriter(); work.done.countDown(); return }
                    null -> {
                        try { writer?.flush() } catch (failure: IOException) { fail(failure) }
                        if (closed) return
                    }
                }
                val time = System.nanoTime()
                if (time - lastFlushAt >= TimeUnit.MILLISECONDS.toNanos(250)) {
                    try { writer?.flush() } catch (failure: IOException) { fail(failure) }
                    lastFlushAt = time
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            closeWriter()
            while (true) {
                when (val remaining = queue.poll() ?: break) {
                    is Work.Barrier -> remaining.done.countDown()
                    is Work.Stop -> remaining.done.countDown()
                    is Work.Record -> { dropped.incrementAndGet(); unreportedDropped.incrementAndGet() }
                }
            }
        }
    }

    private fun append(values: Map<String, Any?>) {
        val line = gson.toJson(values) + "\n"
        val bytes = line.toByteArray(StandardCharsets.UTF_8).size
        if (writer == null) {
            Files.createDirectories(directory)
            fileBytes = if (Files.exists(path)) Files.size(path) else 0
            openWriter()
        }
        if (fileBytes > 0 && fileBytes + bytes > maxBytes) {
            closeWriter()
            rotate()
            fileBytes = 0
            openWriter()
        }
        writer!!.write(line)
        fileBytes += bytes
        lastFailure = null
    }

    private fun openWriter() {
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun fail(failure: Exception) {
        lastFailure = if (failure is IOException) failure else IOException("Diagnostic writer failed", failure)
        closeWriter()
    }

    private fun closeWriter() {
        val previous = writer
        writer = null
        try { previous?.close() } catch (failure: IOException) { lastFailure = failure }
    }

    /** Ordered barrier for explicit lifecycle/tests, never call from a render loop. */
    fun flush(): Boolean {
        if (closed) return !worker.isAlive && lastFailure == null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val done = CountDownLatch(1)
        return try {
            if (!queue.offer(Work.Barrier(done), 5, TimeUnit.SECONDS)) return false
            done.await((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS) && lastFailure == null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        try {
            queue.offer(Work.Stop(CountDownLatch(1)), 5, TimeUnit.SECONDS)
            val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(worker, remaining)
            if (worker.isAlive) lastFailure = IOException("Diagnostic writer shutdown timed out; queued records may be lost")
        } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private sealed interface Work {
        data class Record(val values: Map<String, Any?>) : Work
        data class Barrier(val done: CountDownLatch) : Work
        data class Stop(val done: CountDownLatch) : Work
    }
}
