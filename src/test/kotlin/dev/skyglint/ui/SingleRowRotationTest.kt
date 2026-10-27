package dev.skyglint.ui

import dev.skyglint.ui.presentation.SingleRowRotation
import kotlin.test.*
import org.junit.jupiter.api.Test

class SingleRowRotationTest {
    @Test fun `new boss phase takes over even when the stable widget identity stays the same`() {
        val rotation = SingleRowRotation(4_000)
        val ids = listOf("run", "stats")
        assertEquals("run", rotation.select(ids, 0, "run", "Exploring"))
        assertEquals("stats", rotation.select(ids, 4_000, "run", "Exploring"))
        assertEquals("run", rotation.select(ids, 4_001, "run", "Maxor"))
    }
    @Test fun `new urgent context takes over once without starving other context widgets`() {
        val rotation = SingleRowRotation(4_000)
        assertEquals("visitors", rotation.select(listOf("visitors", "stats"), 0))
        val ids = listOf("contest", "visitors", "stats")
        assertEquals("contest", rotation.select(ids, 100, "contest"))
        assertEquals("visitors", rotation.select(ids, 4_100, "contest"))
        assertEquals("stats", rotation.select(ids, 8_100, "contest"))
    }
    @Test fun `all unknown UUIDs remain reachable without a second row`() {
        val rotation = SingleRowRotation()
        val ids = listOf("boss:a", "boss:b", "boss:c")
        assertEquals("boss:a", rotation.select(ids, 0))
        assertEquals("boss:a", rotation.select(ids, 5_999))
        assertEquals("boss:b", rotation.select(ids, 6_000))
        assertEquals("boss:c", rotation.select(ids, 12_000))
        assertEquals("boss:a", rotation.select(ids, 18_000))
    }
    @Test fun `removal and session reset clear selection`() {
        val rotation = SingleRowRotation()
        rotation.select(listOf("a", "b"), 0)
        assertEquals("b", rotation.select(listOf("a", "b"), 6_000))
        assertEquals("a", rotation.select(listOf("a"), 6_001))
        assertNull(rotation.select(emptyList(), 7_000))
        assertEquals("new", rotation.select(listOf("new"), 7_001))
    }
}
