package dev.skyglint.features.buffs

import dev.skyglint.core.*
import kotlin.test.*

class BuffExpiryWarningTest {
    private var now = 0L
    private fun module() = BuffExpiryModule { now }.apply { onSessionStarted("one") }
    private fun context(module: BuffExpiryModule, mode: String? = "hub") = module.parse(NormalizedSignal.ContextSnapshot("Village", mode))
    private fun cookie(time: String) = NormalizedSignal.TabSnapshot(listOf("Cookie Buff", time))
    private fun god(time: String) = NormalizedSignal.TabSnapshot(listOf("Active Effects", "God Potion: $time"))
    private fun warning(events: List<CoreEvent>, type: String, stage: Int): NoticeRequest {
        val request = (events.single() as CoreEvent.NoticeObserved).request
        assertTrue(request.id.startsWith("skyglint.buffs.expiry:warning:$type:$stage:"), request.id)
        assertEquals(4_000, request.durationMillis)
        assertNull(request.countdown)
        assertNull(request.target)
        return request
    }

    @Test fun `observed countdown crosses thirty ten and one minute stages once each`() {
        val module = module()
        assertTrue(module.parse(cookie("31m")).isEmpty())
        now = 60_000
        warning(module.parse(cookie("30m")), "cookie", 30)
        assertTrue(module.parse(cookie("30m")).isEmpty())
        now += 20 * 60_000
        warning(module.parse(cookie("10m")), "cookie", 10)
        now += 9 * 60_000
        warning(module.parse(cookie("1m")), "cookie", 1)
        now += 30_000
        assertTrue(module.parse(cookie("30s")).isEmpty())
    }

    @Test fun `first low snapshot presents only most urgent stage and never bursts on replay`() {
        for ((time, stage) in listOf("29m" to 30, "9m" to 10, "45s" to 1)) {
            val module = module()
            warning(module.parse(cookie(time)), "cookie", stage)
            repeat(5) { now += 1_000; assertTrue(module.parse(cookie(time)).isEmpty()) }
        }
    }

    @Test fun `elapsed clock and repeated cached footer never manufacture a threshold crossing`() {
        val module = module()
        module.parse(cookie("40m"))
        now = 3_600_000
        assertTrue(module.parse(cookie("40m")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(emptyList())).isEmpty())
        now += 20 * 60_000
        warning(module.parse(cookie("20m")), "cookie", 30)
        now += 3_600_000
        assertTrue(module.parse(cookie("20m")).isEmpty())
        now += 15 * 60_000
        warning(module.parse(cookie("5m")), "cookie", 10)
    }

    @Test fun `small jitter and replaying old high snapshots do not rearm a shown stage`() {
        val module = module()
        module.parse(cookie("31m"))
        now = 120_000
        warning(module.parse(cookie("29m")), "cookie", 30)
        module.parse(cookie("30m"))
        assertTrue(module.parse(cookie("29m")).isEmpty())
        module.parse(cookie("31m"))
        now += 120_000
        assertTrue(module.parse(cookie("29m")).isEmpty())
        val jitter = module()
        warning(jitter.parse(cookie("29m")), "cookie", 30)
        assertTrue(jitter.parse(cookie("29m 40s")).isEmpty())
        assertTrue(jitter.parse(cookie("30m 20s")).isEmpty())
        now += 120_000
        assertTrue(jitter.parse(cookie("29m")).isEmpty())
    }

    @Test fun `substantial observed refill rearms but stale low footer cannot immediately warn again`() {
        val module = module()
        warning(module.parse(cookie("29m")), "cookie", 30)
        now = 1_000
        assertTrue(module.parse(cookie("2h")).isEmpty())
        assertTrue(module.parse(cookie("29m")).isEmpty())
        now += 90 * 60_000
        warning(module.parse(cookie("30m")), "cookie", 30)
    }

    @Test fun `consumption requires fresh increased remaining time and never uses added potion duration as absolute`() {
        val module = module()
        context(module)
        warning(module.parse(god("29m")), "god_potion", 30)
        now = 100
        assertTrue(module.parse(NormalizedSignal.Chat("SIP! The God Potion grants you powers for 28h 48m!")).isEmpty())
        assertTrue(module.parse(god("29m")).isEmpty())
        now = 3_100
        context(module)
        assertTrue(module.parse(god("29m")).isEmpty())
        assertTrue(module.parse(god("28h 48m")).isEmpty())
        assertTrue(module.parse(god("29m")).isEmpty())
        now += (28 * 60 + 18) * 60_000L
        context(module)
        warning(module.parse(god("30m")), "god_potion", 30)
    }

    @Test fun `God warnings cannot originate from paused unknown or stale context`() {
        val module = module()
        assertTrue(module.parse(god("9m")).isEmpty())
        for (mode in listOf("dungeon", "rift", null)) {
            context(module, mode)
            assertTrue(module.parse(god("9m")).isEmpty())
        }
        context(module)
        now += 5_000
        assertTrue(module.parse(god("9m")).isEmpty())
        context(module)
        warning(module.parse(god("9m")), "god_potion", 10)
        context(module, "dungeon")
        now += 24 * 3_600_000
        assertTrue(module.parse(god("9m")).isEmpty())
        context(module)
        assertTrue(module.parse(god("9m")).isEmpty())
    }

    @Test fun `menu observed remaining times use the same stages and reject overflow or missing data`() {
        val module = module()
        context(module)
        val filter = MenuItemSnapshot(50, "Filter", listOf("▶ God Potion Effects"))
        val potion = MenuItemSnapshot(10, "Speed", listOf("Remaining: 0:00:59"))
        warning(module.parse(NormalizedSignal.MenuSnapshot("Active Effects", listOf(filter, potion))), "god_potion", 1)
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("Active Effects", listOf(filter))).isEmpty())
        assertTrue(module.parse(cookie("9223372036854775807d")).isEmpty())
        assertTrue(module.parse(cookie("0m")).isEmpty())
        assertTrue(module.parse(god("0s")).isEmpty())
    }

    @Test fun `inactive confirmation remains distinct and session restart cannot replay old threshold state`() {
        val module = module()
        warning(module.parse(cookie("1m")), "cookie", 1)
        val inactive = NormalizedSignal.TabSnapshot(listOf("Cookie Buff", "Not active! Obtain booster cookies from the community"))
        assertTrue((module.parse(inactive).single() as CoreEvent.NoticeObserved).request.id.startsWith("skyglint.buffs.expiry:cookie:"))
        assertTrue(module.parse(inactive).isEmpty())
        module.onSessionEnded(); module.onSessionStarted("two")
        assertTrue(module.parse(inactive).isEmpty())
        warning(module.parse(cookie("1m")), "cookie", 1)
    }
}
