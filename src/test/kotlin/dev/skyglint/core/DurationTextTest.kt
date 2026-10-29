package dev.skyglint.core

import kotlin.test.*

class DurationTextTest {
    @Test fun `countdown uses seconds minutes hours and days at their boundaries`() {
        val cases = mapOf(
            -1L to "0:00", 0L to "0:00", 1L to "0:01", 999L to "0:01", 1_000L to "0:01",
            59_000L to "0:59", 59_001L to "1:00", 60_000L to "1:00",
            3_599_000L to "59:59", 3_599_001L to "1h 0m", 3_600_000L to "1h 0m",
            18_720_000L to "5h 12m", 86_399_000L to "23h 59m", 86_400_000L to "1d 0h",
            183_600_000L to "2d 3h", 604_800_000L to "7d 0h",
        )
        cases.forEach { (millis, expected) -> assertEquals(expected, DurationText.countdown(millis), "$millis ms") }
    }

    @Test fun `large durations and deadline subtraction cannot wrap into an expired caption`() {
        val extreme = DurationText.countdown(Long.MAX_VALUE)
        assertEquals("106751991167d 7h", extreme)
        val timer = CountdownSnapshot(Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, timer.remaining(-1))
        assertEquals(extreme, timer.caption(-1))
        assertEquals(1.0, timer.fraction(-1))
        assertEquals("0:00", timer.caption(Long.MAX_VALUE))
    }

    @Test fun `long captions do not tick each second and leave the exact monotonic deadline intact`() {
        val deadline = 185_400_000L + 123
        val timer = CountdownSnapshot(deadline, deadline)
        assertEquals("2d 3h", timer.caption(123))
        assertEquals("2d 3h", timer.caption(1_123))
        assertEquals(deadline, timer.deadline)
        assertEquals(185_399_000L, timer.remaining(1_123))
        assertTrue(timer.fraction(1_123) < timer.fraction(123))
    }
}
