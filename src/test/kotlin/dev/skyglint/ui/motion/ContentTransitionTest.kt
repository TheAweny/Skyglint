package dev.skyglint.ui.motion

import kotlin.test.*
import org.junit.jupiter.api.Test

class ContentTransitionTest {
    @Test fun `horizontal carousel moves only along x and remains continuous through interruption`() {
        val transition = ContentTransition()
        transition.update("carrot", 0.0, horizontal = true)
        val changing = transition.update("potato", .05, horizontal = true)
        assertTrue(changing.single { it.key == "carrot" }.offsetX < 0)
        assertTrue(changing.single { it.key == "potato" }.offsetX > 0)
        assertTrue(changing.all { it.offsetY == 0.0 })
        val interrupted = transition.update("wheat", 0.0, horizontal = true)
        changing.forEach { prior ->
            val retained = interrupted.single { it.key == prior.key }
            assertEquals(prior.opacity, retained.opacity)
            assertEquals(prior.offsetX, retained.offsetX)
        }
        val settled = transition.update("wheat", 1.0, horizontal = true).single()
        assertEquals(0.0, settled.offsetX, .000001)
        assertEquals("wheat", settled.key)
    }
    @Test fun `carousel incoming begins below and outgoing leaves upward without resetting either opacity`() {
        val transition = ContentTransition()
        transition.update("intro", 0.0)
        val changing = transition.update("carrot", .04, vertical = true)
        assertTrue(changing.single { it.key == "intro" }.offsetY < 0)
        assertTrue(changing.single { it.key == "carrot" }.offsetY > 0)
        assertEquals(1.0, changing.sumOf { it.opacity }, .000001)
        val settled = transition.update("carrot", 1.0, vertical = true).single()
        assertEquals("carrot", settled.key)
        assertEquals(1.0, settled.opacity, .000001)
        assertEquals(0.0, settled.offsetY, .000001)
    }

    @Test fun `interrupting vertical transition preserves the actual visible layer positions`() {
        val transition = ContentTransition()
        transition.update("intro", 0.0)
        val before = transition.update("carrot", .06, vertical = true)
        val interrupted = transition.update("potato", 0.0, vertical = true)
        before.forEach { prior ->
            val retained = interrupted.single { it.key == prior.key }
            assertEquals(prior.opacity, retained.opacity)
            assertEquals(prior.offsetY, retained.offsetY)
        }
        assertEquals(1.0, interrupted.sumOf { it.opacity }, .000001)
        assertEquals("potato", transition.update("potato", 1.0, vertical = true).single().key)
    }

    @Test fun `ordinary caption changes crossfade at zero offset and hidden old payloads do not return`() {
        val transition = ContentTransition()
        transition.update("one", 0.0)
        assertTrue(transition.update("two", .05).all { it.offsetY == 0.0 })
        assertEquals("two", transition.update("two", 1.0).single().key)
        assertTrue(transition.update("three", .016).none { it.key == "one" })
        transition.reset()
        assertEquals(listOf(ContentTransition.Layer("fresh", 1.0, 0.0)), transition.update("fresh", 0.0))
    }
}
