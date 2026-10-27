package dev.skyglint.fabric

import dev.skyglint.core.ElectionVoteCandidate
import dev.skyglint.features.elections.ElectionApiPayload
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ElectionApiClientTest {
    private val payload = ElectionApiPayload.validated(522, listOf(ElectionVoteCandidate("Diana", 60), ElectionVoteCandidate("Paul", 40)))!!
    private class Fetcher {
        val calls = AtomicInteger()
        @Volatile var workerThread: Thread? = null
        private val entered = Semaphore(0)
        private val answers = LinkedBlockingQueue<() -> ElectionApiClient.Result>()
        fun fetch(): ElectionApiClient.Result { workerThread = Thread.currentThread(); calls.incrementAndGet(); entered.release(); return answers.take().invoke() }
        fun awaitRequest() = assertTrue(entered.tryAcquire(2, TimeUnit.SECONDS), "worker did not begin request")
        fun answer(result: ElectionApiClient.Result) { answers.put { result } }
        fun fail() { answers.put { throw IllegalStateException("injected") } }
    }
    private data class Delivery(val payload: ElectionApiPayload.Payload, val age: Long, val thread: Thread)
    private class Harness(fetcher: Fetcher) : AutoCloseable {
        val diagnostics = mutableListOf<String>()
        val deliveries = mutableListOf<Delivery>()
        val closures = mutableListOf<Long>()
        val client = ElectionApiClient(fetcher::fetch, diagnostics::add, closures::add)
        fun poll(now: Long, key: String?) = client.poll(now, key) { payload, age -> deliveries += Delivery(payload, age, Thread.currentThread()) }
        fun until(now: Long, key: String?, condition: () -> Boolean) {
            val timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!condition() && System.nanoTime() < timeout) { poll(now, key); Thread.yield() }
            assertTrue(condition(), "worker completion was not processed")
        }
        override fun close() = client.close()
    }

    @Test fun `confirmed no current election invalidates old cache and replays closure with original age`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(payload))
            h.until(1_000, "A") { h.deliveries.size == 1 }
            h.poll(300_000, "A"); fetch.awaitRequest()
            fetch.answer(ElectionApiClient.Result(null, status = "closed", closed = true))
            h.until(301_000, "A") { h.closures.size == 1 }
            assertEquals(1_000, h.closures.single())
            h.poll(302_000, null); h.poll(303_000, "B")
            assertEquals(listOf(1_000L, 3_000L), h.closures)
            assertEquals(1, h.deliveries.size)
            assertEquals(2, fetch.calls.get())
        }
    }

    @Test fun `null context never requests data and callback runs only from poll thread`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, null); assertEquals(0, fetch.calls.get())
            h.poll(0, "A"); fetch.awaitRequest()
            assertTrue(fetch.workerThread!!.isDaemon)
            assertNotSame(Thread.currentThread(), fetch.workerThread)
            fetch.answer(ElectionApiClient.Result(payload))
            h.until(1_000, "A") { h.deliveries.isNotEmpty() }
            assertSame(Thread.currentThread(), h.deliveries.single().thread)
            assertEquals(1_000L, h.deliveries.single().age)
        }
    }
    @Test fun `one request in flight survives many context changes`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest()
            repeat(20) { h.poll(it.toLong(), if (it % 2 == 0) null else "B") }
            assertEquals(1, fetch.calls.get())
            assertTrue(h.deliveries.isEmpty())
        }
    }
    @Test fun `departed completion updates global cache without callback into replacement context on completion poll`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest()
            h.poll(1_000, null); h.poll(2_000, "B")
            fetch.answer(ElectionApiClient.Result(payload))
            h.until(2_000, "B") { h.diagnostics.isNotEmpty() }
            assertTrue(h.deliveries.isEmpty())
            h.poll(2_500, "B")
            assertEquals(payload, h.deliveries.single().payload)
            assertEquals(2_500L, h.deliveries.single().age)
            assertEquals(522, h.deliveries.single().payload.year)
            assertEquals(listOf("Diana", "Paul"), h.deliveries.single().payload.candidates.map { it.name })
            assertEquals(listOf(60L, 40L), h.deliveries.single().payload.candidates.map { it.votes })
            assertFailsWith<UnsupportedOperationException> {
                (h.deliveries.single().payload.candidates as MutableList<ElectionVoteCandidate>).clear()
            }
            assertEquals(1, fetch.calls.get())
        }
    }
    @Test fun `cache replays once per active context with original age and no region request spam`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(payload))
            h.until(1_000, "A") { h.deliveries.isNotEmpty() }
            repeat(10) { h.poll(1_500, "A") }
            assertEquals(1, h.deliveries.size)
            h.poll(2_000, null); h.poll(3_000, "B")
            h.poll(4_000, null); h.poll(5_000, "A")
            assertEquals(listOf(1_000L, 3_000L, 5_000L), h.deliveries.map { it.age })
            assertEquals(1, fetch.calls.get())
            h.poll(299_999, "A"); assertEquals(1, fetch.calls.get())
            h.poll(300_000, "A"); fetch.awaitRequest(); assertEquals(2, fetch.calls.get())
        }
    }
    @Test fun `success completed while absent is cached and consumed only after active reentry`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest(); h.poll(10, null)
            fetch.answer(ElectionApiClient.Result(payload))
            h.until(1_000, null) { h.diagnostics.isNotEmpty() }
            assertTrue(h.deliveries.isEmpty())
            h.poll(2_000, "new-session")
            assertEquals(2_000L, h.deliveries.single().age)
            assertEquals(1, fetch.calls.get())
        }
    }
    @Test fun `failures back off exponentially across null session and area keys`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(null, status = "http_500"))
            h.until(1_000, "A") { h.diagnostics.size == 1 }
            repeat(20) { h.poll(2_000 + it.toLong(), null); h.poll(3_000 + it.toLong(), "B$it") }
            assertEquals(1, fetch.calls.get())
            h.poll(60_999, "B"); assertEquals(1, fetch.calls.get())
            h.poll(61_000, "B"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(null, status = "http_500"))
            h.until(61_000, "B") { h.diagnostics.size == 2 }
            h.poll(181_000 - 1, "C"); assertEquals(2, fetch.calls.get())
            h.poll(181_000, "C"); fetch.awaitRequest(); assertEquals(3, fetch.calls.get())
            assertTrue(h.deliveries.isEmpty())
        }
    }
    @Test fun `retry after overrides backoff and exceptions become bounded network failure`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(null, 300_000, "http_429"))
            h.until(1_000, "A") { h.diagnostics.size == 1 }
            h.poll(300_999, "B"); assertEquals(1, fetch.calls.get())
            h.poll(301_000, "B"); fetch.awaitRequest(); fetch.fail()
            h.until(301_000, "B") { h.diagnostics.size == 2 }
            assertEquals("network_error", h.diagnostics.last())
            h.poll(420_999, "C"); assertEquals(2, fetch.calls.get())
        }
    }
    @Test fun `failed refresh never makes cached global data younger and six minute cache expires`() {
        val fetch = Fetcher()
        Harness(fetch).use { h ->
            h.poll(0, "A"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(payload))
            h.until(1_000, "A") { h.deliveries.isNotEmpty() }
            h.poll(300_000, "A"); fetch.awaitRequest(); fetch.answer(ElectionApiClient.Result(null, 300_000, "http_500"))
            h.until(301_000, "A") { h.diagnostics.size == 2 }
            h.poll(302_000, "B")
            assertEquals(302_000L, h.deliveries.last().age)
            h.poll(360_000, "C")
            assertEquals(2, h.deliveries.size)
            assertEquals(2, fetch.calls.get())
        }
    }
    @Test fun `close interrupts daemon work and makes subsequent polls inert`() {
        val fetch = Fetcher()
        val h = Harness(fetch)
        h.poll(0, "A"); fetch.awaitRequest(); h.close()
        fetch.answer(ElectionApiClient.Result(payload))
        repeat(10) { h.poll(1_000, "B") }
        assertTrue(h.deliveries.isEmpty())
        assertTrue(h.diagnostics.isEmpty())
        assertEquals(1, fetch.calls.get())
    }
}
