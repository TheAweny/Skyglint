package dev.skyglint.features.buffs

import dev.skyglint.core.*
import kotlin.test.*

class BuffCapturedFooterTest {
    @Test fun `real captured comma separated cookie duration establishes active baseline`() {
        val module = BuffExpiryModule { 0 }
        module.parse(NormalizedSignal.TabSnapshot(listOf("Cookie Buff", "6 days, 3 hours")))
        val expired = module.parse(NormalizedSignal.TabSnapshot(listOf("Cookie Buff", "Not active! Obtain booster cookies from the community")))
        assertTrue(assertIs<CoreEvent.NoticeObserved>(expired.single()).request.id.startsWith("skyglint.buffs.expiry:cookie:"))
        val values = BuffExpiryPatterns.tab(listOf("Cookie Buff", "6 days, 3 hours", "You have a God Potion active! 6 hours"))
        assertEquals(2, values.size)
        assertEquals(529_200_000L, values.single { it.buff == BuffExpiryPatterns.Buff.COOKIE }.remainingMillis)
    }

    @Test fun `commas cannot split digits into a fabricated low time`() {
        for (text in listOf("1,000h", ",1m", "1m,", "1m,, 2s", "1d! 2h"))
            assertTrue(BuffExpiryPatterns.tab(listOf("Cookie Buff", text)).isEmpty(), text)
    }
}
