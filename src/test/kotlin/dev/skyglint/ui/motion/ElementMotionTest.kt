package dev.skyglint.ui.motion

import kotlin.test.*
import org.junit.jupiter.api.Test

class ElementMotionTest {
    @Test fun `initial frame is invisible at destination rather than full size or flying from zero`() {
        val motion = ElementMotion()
        val initial = motion.update(true, 170.0, 120.0, "Objective", 0.0)
        assertEquals(170.0, initial.x)
        assertEquals(120.0, initial.width)
        assertEquals(0.0, initial.visibility)
        assertEquals(0.0, initial.occupiedWidth)
        assertFalse(initial.visible)
        val entering = motion.update(true, 170.0, 120.0, "Objective", .016)
        assertTrue(entering.visibility in 0.0..0.1)
        assertTrue(entering.visible)
        assertEquals(170.0, entering.x)
    }

    @Test fun `absence retains payload and natural width while reservation and opacity leave smoothly`() {
        val motion = ElementMotion()
        motion.update(true, 170.0, 120.0, "Objective", 1.0)
        val leaving = motion.update(false, 170.0, 0.0, "", .016)
        assertEquals("Objective", leaving.retainedCaption)
        assertEquals(120.0, leaving.width)
        assertTrue(leaving.visibility in .9..1.0)
        assertTrue(leaving.occupiedWidth > 100)
        assertEquals(leaving.visibility, leaving.captions.sumOf { it.opacity }, .000001)
        assertTrue(leaving.captions.any { it.text == "Objective" })
        val hidden = motion.update(false, 170.0, 0.0, "", 2.0)
        assertFalse(hidden.visible)
        assertEquals(0.0, hidden.occupiedWidth)
    }

    @Test fun `return during exit preserves visible geometry and caption mixture`() {
        val motion = ElementMotion()
        motion.update(true, 120.0, 160.0, "Before", 1.0)
        val departing = motion.update(false, 90.0, 0.0, "", .08)
        val interrupted = motion.update(true, 200.0, 220.0, "After", 0.0)
        assertEquals(departing.x, interrupted.x)
        assertEquals(departing.width, interrupted.width)
        assertEquals(departing.visibility, interrupted.visibility)
        assertTrue(interrupted.captions.any { it.text == "Before" && it.opacity > .1 })
        val settled = motion.update(true, 200.0, 220.0, "After", 2.0)
        assertEquals(200.0, settled.x)
        assertEquals(220.0, settled.width)
        assertEquals(1.0, settled.visibility)
        assertEquals(listOf(CaptionTransition.Layer("After", 1.0)), settled.captions)
    }

    @Test fun `same elapsed time gives the same geometry and presence at different frame rates`() {
        fun sample(frames: Int): ElementMotion.State {
            val motion = ElementMotion()
            motion.update(true, 100.0, 100.0, "A", 0.0)
            var last = motion.update(true, 200.0, 250.0, "B", 0.0)
            repeat(frames) { last = motion.update(true, 200.0, 250.0, "B", .25 / frames) }
            return last
        }
        val low = sample(15)
        val high = sample(60)
        assertEquals(low.x, high.x, 1e-9)
        assertEquals(low.width, high.width, 1e-9)
        assertEquals(low.visibility, high.visibility, 1e-9)
        assertEquals(low.occupiedWidth, high.occupiedWidth, 1e-9)
    }

    @Test fun `new payload after complete exit does not revive a hidden prior caption`() {
        val motion = ElementMotion()
        motion.update(true, 100.0, 100.0, "Old", 1.0)
        motion.update(false, 100.0, 0.0, "", 2.0)
        val entering = motion.update(true, 100.0, 120.0, "New", .1)
        assertTrue(entering.captions.none { it.text == "Old" })
        assertTrue(entering.captions.any { it.text == "New" })
    }

    @Test fun `absent never seen elements reserve no phantom space and reset clears the exit`() {
        val motion = ElementMotion()
        val absent = motion.update(false, 100.0, 100.0, "ignored", .016)
        assertEquals(0.0, absent.occupiedWidth)
        assertTrue(absent.captions.isEmpty())
        motion.update(true, 100.0, 100.0, "A", 1.0)
        motion.reset()
        val fresh = motion.update(true, 150.0, 50.0, "B", 0.0)
        assertEquals(150.0, fresh.x)
        assertEquals(50.0, fresh.width)
        assertFalse(fresh.visible)
        assertTrue(fresh.captions.none { it.text == "A" })
    }

    @Test fun `invalid timing and geometry fail before mutating motion state`() {
        val motion = ElementMotion()
        assertFailsWith<IllegalArgumentException> { motion.update(true, Double.NaN, 100.0, "A", .016) }
        assertFailsWith<IllegalArgumentException> { motion.update(true, 0.0, -1.0, "A", .016) }
        assertFailsWith<IllegalArgumentException> { motion.update(true, 0.0, 100.0, "A", -.016) }
        assertFailsWith<IllegalArgumentException> { motion.update(true, 0.0, 100.0, "A", Double.POSITIVE_INFINITY) }
        assertFalse(motion.update(true, 0.0, 100.0, "A", 0.0).visible)
    }
}
