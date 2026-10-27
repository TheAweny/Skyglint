package dev.skyglint.features.session

import kotlin.test.*

/** Synthetic source-shaped examples, not captured or live-verified Hypixel observations. */
class ServerClosingPatternsTest {
    private fun parse(vararg lines: String) = ServerClosingPatterns.parse(lines.toList())

    @Test fun `exact sidebar line copies server identity and observed remaining time`() {
        assertEquals(ServerClosingPatterns.Observation("mini123A", 134_000),
            parse("SKYBLOCK", "Server closing: 2:14 mini123A", "Purse: 100"))
        assertEquals(ServerClosingPatterns.Observation("mega42B", 59_000),
            parse("§cServer closing: §e0:59 §7mega42B"))
    }

    @Test fun `explicit zero remains an observed terminal sample`() {
        assertEquals(ServerClosingPatterns.Observation("mini123A", 0), parse("Server closing: 0:00 mini123A"))
    }

    @Test fun `bounded minutes seconds and server identifier accept the supported limits`() {
        assertEquals(59_999_000L, parse("Server closing: 999:59 ab")!!.remainingMillis)
        val longestServer = "a" + "1".repeat(31)
        assertEquals(longestServer, parse("Server closing: 1:00 $longestServer")!!.server)
    }

    @Test fun `unknown malformed and player-like text cannot become a server clock`() {
        listOf(
            "Server closing: 1:60 mini123A", "Server closing: 1000:00 mini123A",
            "Server closing: -1:00 mini123A", "Server closing: 1:1 mini123A",
            "Server closing: 1:00 a", "Server closing: 1:00 12345",
            "Server closing: 1:00 mini-123", "Server closing: 1:00 ${"a".repeat(33)}",
            "Server closing: 1:00 mini123A extra", "Player: Server closing: 1:00 mini123A",
            "Server restarting: 1:00 mini123A", "Server closing in 1 minute",
            "Server closing: 1:00 mini123A\u0000", "Server closing: 1:00\nmini123A",
            "Server closing: " + "1".repeat(300),
        ).forEach { assertNull(parse(it), it) }
    }

    @Test fun `ambiguous duplicate or contradictory closing lines are rejected`() {
        assertNull(parse("Server closing: 1:00 mini123A", "Server closing: 1:00 mini123A"))
        assertNull(parse("Server closing: 1:00 mini123A", "Server closing: 2:00 mini123A"))
        assertNull(parse("Server closing: 1:00 mini123A", "Server closing: unavailable"))
    }

    @Test fun `input line scan has a fixed bound and absence remains unknown`() {
        assertNull(parse())
        assertNull(parse("SKYBLOCK", "Purse: 100", "Server: mini123A"))
        val fillers = List(63) { "unrelated $it" }
        assertNotNull(ServerClosingPatterns.parse(fillers + "Server closing: 1:00 mini123A"))
        assertNull(ServerClosingPatterns.parse(fillers + "unrelated" + "Server closing: 1:00 mini123A"))
    }
}
