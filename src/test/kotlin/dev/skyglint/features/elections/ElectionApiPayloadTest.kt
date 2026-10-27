package dev.skyglint.features.elections

import kotlin.test.*

class ElectionApiPayloadTest {
    private fun payload(rows: String, year: String = "522") =
        """{"success":true,"current":{"year":$year,"candidates":[$rows]}}"""
    private fun row(name: String, votes: String) = """{"name":"$name","votes":$votes}"""

    @Test fun `only successful absent or null current is confirmed closed`() {
        assertTrue(ElectionApiPayload.isClosed("""{"success":true,"current":null}"""))
        assertTrue(ElectionApiPayload.isClosed("""{"success":true,"mayor":{"election":{"year":521}}}"""))
        for (json in listOf("{}", "null", "[]", "broken", "{\"success\":true,}",
            """{"success":false,"current":null}""", """{"success":"true","current":null}""",
            """{"success":true,"current":{}}""", """{"success":true,"current":[]}""",
            """{"success":true,"current":false}"""))
            assertFalse(ElectionApiPayload.isClosed(json), json)
    }

    @Test fun `only current full candidates are read and sorted not previous mayor election`() {
        val json = """{"success":true,"mayor":{"election":{"year":521,"candidates":[{"name":"Old","votes":9000}]}},
            "current":{"year":522,"candidates":[{"name":"Paul","votes":1},{"name":"Diana","votes":4},
            {"name":"Marina","votes":10},{"name":"Foxy","votes":25},{"name":"Aatrox","votes":60}]}}"""
        val result = ElectionApiPayload.parse(json)!!
        assertEquals(522, result.year)
        assertEquals(listOf("Aatrox", "Foxy", "Marina", "Diana", "Paul"), result.candidates.map { it.name })
        assertNull(ElectionApiPayload.parse("""{"success":true,"mayor":{"election":{"year":522,"candidates":[]}}}"""))
        assertNull(ElectionApiPayload.parse("""{"success":true,"current":null}"""))
    }
    @Test fun `missing negative fractional string overflowing and zero totals are rejected`() {
        val good = row("Diana", "1")
        for (invalid in listOf("-1", "1.5", "1e3", "\"4\"", "9223372036854775808", "null"))
            assertNull(ElectionApiPayload.parse(payload("$good,${row("Paul", invalid)}")), invalid)
        assertNull(ElectionApiPayload.parse(payload("$good,{\"name\":\"Paul\"}")))
        assertNull(ElectionApiPayload.parse(payload("${row("Diana", "0")},${row("Paul", "0")}")))
        assertNull(ElectionApiPayload.parse(payload("${row("Diana", Long.MAX_VALUE.toString())},${row("Paul", "1")}")))
        val explicitZero = ElectionApiPayload.parse(payload("$good,${row("Paul", "0")}"))!!
        assertEquals(0L, explicitZero.candidates.last().votes)
    }
    @Test fun `duplicate invalid names row bounds bad years and failure reject whole response`() {
        val good = row("Diana", "1")
        for (name in listOf("diana", "", "§aPaul", "Paul\n", "A".repeat(41)))
            assertNull(ElectionApiPayload.parse(payload("$good,${row(name, "1")}")), name)
        for (year in listOf("0", "-1", "100001", "522.0", "\"522\""))
            assertNull(ElectionApiPayload.parse(payload("$good,${row("Paul", "1")}", year)))
        assertNull(ElectionApiPayload.parse(payload(good)))
        assertNull(ElectionApiPayload.parse(payload((0..12).joinToString(",") { row("Mayor ${('A'.code + it).toChar()}", "1") })))
        assertNull(ElectionApiPayload.parse(payload("$good,${row("Paul", "1")}").replace("true", "false")))
        assertNull(ElectionApiPayload.parse("x".repeat(131_073)))
    }
}
