package dev.skyglint.ui.motion

import kotlin.test.*
import org.junit.jupiter.api.Test

class PercentTransitionTest {
    @Test fun `expiry retains digits while easing back to icon`() {
        val transition = PercentTransition()
        val shown = transition.update("mining", 49, .5)
        val leaving = transition.update("mining", null, .016)
        assertTrue(shown.amount > .99)
        assertTrue(leaving.amount in .9..1.0)
        assertTrue(leaving.captions.any { it.text == "49%" })
        assertTrue(transition.update("mining", null, .5).amount < .01)
    }
    @Test fun `new gain reverses from visible mixture without jumping`() {
        val transition = PercentTransition()
        transition.update("pet", 73, .5)
        val leaving = transition.update("pet", null, .08)
        val interrupted = transition.update("pet", 74, 0.0)
        assertEquals(leaving.amount, interrupted.amount)
        assertTrue(interrupted.captions.any { it.text == "73%" })
        assertTrue(transition.update("pet", 74, .6).amount > .99)
    }
    @Test fun `new identity never inherits old percentage`() {
        val transition = PercentTransition()
        transition.update("mining", 49, .5)
        val fishing = transition.update("fishing", null, .016)
        assertEquals(0.0, fishing.amount)
        assertTrue(fishing.captions.none { it.text == "49%" })
    }
}
