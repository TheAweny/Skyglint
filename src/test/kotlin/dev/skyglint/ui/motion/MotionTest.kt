package dev.skyglint.ui.motion

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MotionTest {
    @Test fun `geometry at the same instant is independent of frame rate`() {
        fun sample(frames: Int): CriticalSpring = CriticalSpring(84.0).apply { repeat(frames) { step(30.0, .6 / frames) } }
        val low = sample(18)
        val high = sample(144)
        assertEquals(low.value, high.value, 1e-10)
        assertEquals(low.velocity, high.velocity, 1e-9)
        assertTrue(low.value in 30.0..84.0)
    }

    @Test fun `interruption preserves current geometry and velocity then settles`() {
        val spring = CriticalSpring(140.0)
        spring.step(300.0, .07)
        val visible = spring.value
        val velocity = spring.velocity
        assertEquals(visible, spring.step(90.0, 0.0))
        assertEquals(velocity, spring.velocity)
        repeat(240) { spring.step(90.0, 1.0 / 120) }
        assertEquals(90.0, spring.value, .001)
        assertEquals(0.0, spring.velocity, .001)
    }

    @Test fun `long paused frame converges without unstable numerical integration`() {
        val spring = CriticalSpring(30.0)
        assertEquals(400.0, spring.step(400.0, 10.0), .0001)
        assertTrue(spring.value.isFinite())
    }

    @Test fun `caption interruption conserves opacity and never revives an already hidden caption`() {
        val caption = CaptionTransition("A")
        caption.update("B", .08)
        val interrupted = caption.update("C", 0.0)
        assertEquals(1.0, interrupted.sumOf { it.opacity }, 1e-10)
        assertTrue(interrupted.any { it.text == "A" && it.opacity > 0 })
        assertTrue(interrupted.any { it.text == "B" && it.opacity > 0 })
        val settled = caption.update("C", .5)
        assertEquals(listOf(CaptionTransition.Layer("C", 1.0)), settled)
        assertFalse(caption.update("D", .01).any { it.text == "A" || it.text == "B" })
    }
}
