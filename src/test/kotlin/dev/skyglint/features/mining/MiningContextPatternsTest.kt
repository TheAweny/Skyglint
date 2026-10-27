package dev.skyglint.features.mining

import kotlin.test.*

class MiningContextPatternsTest {
    @Test fun `commission and powder sections require exact headers and preserve observed zero`() {
        val parsed = MiningContextPatterns.tab(listOf("§3Commissions:", " §fMithril Miner: §a37.5%", "Titanium Miner: DONE", "",
            "Powders:", "Mithril: 35,448", "Gemstone: 0", "Glacite: 29,537"))
        assertEquals(listOf(37.5, 100.0), parsed.commissions.map { it.percent })
        assertTrue(parsed.commissions[1].done)
        assertEquals(mapOf("Mithril" to 35_448L, "Gemstone" to 0L, "Glacite" to 29_537L), parsed.powders)
        assertTrue(MiningContextPatterns.tab(listOf("Mithril Miner: 50%", "Mithril: 44")).commissions.isEmpty())
        assertTrue(MiningContextPatterns.tab(listOf("Mithril: 44")).powders.isEmpty())
    }
    @Test fun `malformed or duplicate commissions do not turn into zero or partial completion`() {
        for (row in listOf("Titanium Miner: 101%", "Titanium Miner: -1%", "Titanium Miner: unknown", "Titanium Miner: 3/5",
            "Titanium Miner: NaN%", "Titanium Miner: DONE!", "Titanium Miner:", "Mithril Miner: 20%")) {
            val parsed = MiningContextPatterns.tab(listOf("Commissions:", "Mithril Miner: 50%", row))
            assertTrue(parsed.commissions.isEmpty(), row)
        }
        assertTrue(MiningContextPatterns.tab(listOf("Commissions:", "Mithril Miner: 0%", "mithril miner: 1%")).commissions.isEmpty())
    }
    @Test fun `powder requires grouped finite absolute totals and rejects duplicate or overflow`() {
        for (row in listOf("Gemstone: 1,23", "Gemstone: -1", "Gemstone: 1.5k", "Gemstone: 9223372036854775808", "Mithril: 1"))
            assertTrue(MiningContextPatterns.tab(listOf("Powders:", "Mithril: 0", row)).powders.isEmpty(), row)
    }
    @Test fun `source confirmed heat immune signed cold and absent values remain independent`() {
        assertEquals(-3, MiningContextPatterns.environment(listOf("§7Cold: §b-3❄")).cold)
        assertEquals("IMMUNE", MiningContextPatterns.environment(listOf("Heat: §6IMMUNE")).heat)
        assertEquals("0♨", MiningContextPatterns.environment(listOf("Heat: §c0♨")).heat)
        assertNull(MiningContextPatterns.environment(listOf("Heat: 101♨")).heat)
        assertNull(MiningContextPatterns.environment(listOf("Cold: 3")).cold)
        assertNull(MiningContextPatterns.environment(listOf("Cold: -3❄", "Cold: -4❄")).cold)
        assertEquals(MiningContextPatterns.Environment(), MiningContextPatterns.environment(emptyList()))
    }
}
