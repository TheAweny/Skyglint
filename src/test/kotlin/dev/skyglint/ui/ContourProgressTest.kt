package dev.skyglint.ui

import dev.skyglint.ui.effects.ContourProgress
import kotlin.math.floor
import kotlin.test.*

class ContourProgressTest {
    /** Matches float vertex packing and the GLSL unpack, including the less precise bottom vertices. */
    private fun decoded(fraction: Float?, bottom: Boolean): Float? {
        val uvY = ContourProgress.encode(fraction) + if (bottom) 2f else 0f
        val localY = floor(uvY * .5f)
        assertEquals(if (bottom) 1f else 0f, localY)
        val encoded = uvY - localY * 2f
        return if (encoded >= .25f) ((encoded - .25f) * 2f).coerceIn(0f, 1f) else null
    }

    @Test fun `unknown empty full and intermediate progress survive both vertex bands`() {
        for (bottom in listOf(false, true)) {
            assertNull(decoded(null, bottom))
            for (fraction in listOf(0f, .001f, .1234567f, .5f, .999f, 1f)) {
                assertEquals(fraction, decoded(fraction, bottom)!!, .0000005f)
            }
        }
    }

    @Test fun `fifteen minute countdown retains distinct progress on every frame`() {
        var previous = 1f
        for (elapsed in 16..900_000 step 16) {
            val fraction = (900_000 - elapsed) / 900_000f
            val decoded = decoded(fraction, bottom = true)!!
            assertTrue(decoded < previous, "Progress stalled at $elapsed ms")
            assertEquals(fraction, decoded, .0000005f)
            previous = decoded
        }
    }
}
