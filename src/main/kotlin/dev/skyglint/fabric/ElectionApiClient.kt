package dev.skyglint.fabric

import dev.skyglint.features.elections.ElectionApiPayload
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.RejectedExecutionException

/** Public global resource only: no player identity, API key, profiles or game-thread I/O. */
class ElectionApiClient(
    private val fetch: () -> Result = ::download,
    private val diagnostic: (String) -> Unit = {},
    private val onClosed: (Long) -> Unit = {},
) : AutoCloseable {
    data class Result(val payload: ElectionApiPayload.Payload?, val retryAfterMillis: Long = 0, val status: String = "ok", val closed: Boolean = false)
    private data class Completion(val generation: Long, val requestedAt: Long, val result: Result)
    private data class Cache(val payload: ElectionApiPayload.Payload?, val observedAt: Long, val revision: Long)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "skyglint-election-resource").apply { isDaemon = true } }
    private val inFlight = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val completed = AtomicReference<Completion?>()
    private var cache: Cache? = null
    private var cacheRevision = 0L
    private var deliveredGeneration = Long.MIN_VALUE
    private var deliveredRevision = Long.MIN_VALUE
    private var key: String? = null
    private var generation = 0L
    private var nextAttemptAt = 0L
    private var failures = 0

    /** Client-thread poll. Public cache/cooldown survive area/session keys; callbacks carry original age. */
    fun poll(now: Long, contextKey: String?, consume: (ElectionApiPayload.Payload, Long) -> Unit) {
        if (closed.get()) return
        if (key != contextKey) {
            key = contextKey; generation++
        }
        val completion = completed.getAndSet(null)
        if (completion != null) {
            val result = completion.result
            if (result.payload != null || result.closed) {
                failures = 0
                nextAttemptAt = completion.requestedAt + 300_000
                cacheRevision++
                // Timestamp the request, conservatively including network latency in the payload age.
                cache = Cache(result.payload?.let { it.copy(candidates = Collections.unmodifiableList(ArrayList(it.candidates))) },
                    completion.requestedAt, cacheRevision)
                diagnostic("updated")
            } else {
                failures = (failures + 1).coerceAtMost(5)
                val backoff = (60_000L shl (failures - 1)).coerceAtMost(1_200_000)
                nextAttemptAt = now + maxOf(backoff, result.retryAfterMillis.coerceIn(0, 3_600_000))
                diagnostic(result.status)
            }
        }
        val available = cache?.takeIf { now >= it.observedAt && now - it.observedAt < 360_000 }
        // A departed request populates only the global cache on this poll. A later active poll may reuse it.
        val matchingCompletion = completion == null || completion.generation == generation
        if (contextKey != null && available != null && matchingCompletion &&
            (deliveredGeneration != generation || deliveredRevision != available.revision)) {
            deliveredGeneration = generation; deliveredRevision = available.revision
            val value = available.payload
            if (value != null) consume(value, now - available.observedAt) else onClosed(now - available.observedAt)
        }
        if (contextKey == null || now < nextAttemptAt || !inFlight.compareAndSet(false, true)) return
        val requestedGeneration = generation
        try {
            executor.execute {
                try {
                    val result = try { fetch() } catch (_: Exception) { Result(null, status = "network_error") }
                    if (!closed.get()) completed.set(Completion(requestedGeneration, now, result))
                } finally { inFlight.set(false) }
            }
        } catch (_: RejectedExecutionException) {
            inFlight.set(false)
        }
    }

    override fun close() { closed.set(true); completed.set(null); executor.shutdownNow() }

    companion object {
        private val endpoint = URI.create("https://api.hypixel.net/v2/resources/skyblock/election")
        private val client: HttpClient by lazy {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build()
        }
        private fun download(): Result {
            val request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json").header("User-Agent", "Skyglint-GUI/0.1.5").GET().build()
            val boundedBody = HttpResponse.BodyHandler<ByteArray> {
                HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(), 131_072)
            }
            val pending = client.sendAsync(request, boundedBody)
            val response = try { pending.get(12, java.util.concurrent.TimeUnit.SECONDS) }
                catch (failure: Exception) { pending.cancel(true); throw failure }
            val retry = response.headers().firstValue("Retry-After").orElse("").toLongOrNull()
                ?.coerceIn(0, 3_600)?.times(1_000) ?: 0
            if (response.statusCode() != 200) return Result(null, retry, "http_${response.statusCode()}")
            val json = response.body().toString(Charsets.UTF_8)
            val parsed = ElectionApiPayload.parse(json)
            val isClosed = ElectionApiPayload.isClosed(json)
            return Result(parsed, status = if (parsed != null) "ok" else if (isClosed) "closed" else "invalid_response", closed = isClosed)
        }
    }
}
