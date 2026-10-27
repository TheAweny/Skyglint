package dev.skyglint.ui.motion

import kotlin.test.*
import org.junit.jupiter.api.Test

class PercentPulseTest {
    @Test fun `only whole advances pulse and expiry does not retrigger`() {
        val pulse = PercentPulse()
        assertNull(pulse.observe("mining", .48, 0))
        assertNull(pulse.observe("mining", .4899, 100))
        assertEquals(49, pulse.observe("mining", .49, 200))
        assertEquals(49, pulse.observe("mining", .491, 500))
        assertNull(pulse.observe("mining", .491, 1300))
    }
    @Test fun `identity reset unknown and level rollover never manufacture gain`() {
        val pulse = PercentPulse()
        pulse.observe("pet:Fish", .98, 0)
        assertEquals(99, pulse.observe("pet:Fish", .99, 10))
        assertNull(pulse.observe("pet:Fish", .01, 20))
        assertNull(pulse.observe("pet:Seal", .80, 30))
        assertNull(pulse.observe("pet:Seal", null, 40))
        assertNull(pulse.observe("pet:Seal", .90, 50))
    }
    @Test fun `precision boundary and maximum percent stay valid`() {
        val pulse = PercentPulse()
        pulse.observe("xp", .28, 0)
        assertEquals(29, pulse.observe("xp", .29, 1))
        assertEquals(100, pulse.observe("xp", 1.0, 2))
    }
}
