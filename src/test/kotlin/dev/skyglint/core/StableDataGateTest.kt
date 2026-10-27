package dev.skyglint.core

import kotlin.test.*

class StableDataGateTest {
    @Test fun `repeated stable structure settles once while new structure restarts its own delay`() {
        val gate = StableDataGate<String>()
        assertFalse(gate.ready("full", 0))
        assertFalse(gate.ready("full", 499))
        assertTrue(gate.ready("full", 500))
        assertTrue(gate.ready("full", 501))
        assertFalse(gate.ready("other", 700))
        assertTrue(gate.ready("other", 1_200))
    }
    @Test fun `missing data lifecycle clear and backwards clock cannot reuse readiness`() {
        val gate = StableDataGate<String>()
        gate.ready("full", 0)
        assertTrue(gate.ready("full", 500))
        assertFalse(gate.ready(null, 501))
        assertFalse(gate.ready("full", 502))
        assertTrue(gate.ready("full", 1_002))
        assertFalse(gate.ready("full", 200))
        assertTrue(gate.ready("full", 700))
        gate.clear()
        assertFalse(gate.ready("full", 701))
    }
}
