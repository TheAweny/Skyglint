package dev.skyglint.features.mining

import kotlin.test.*

class MiningEventPatternsTest {
    @Test fun `six exact boss event forms preserve observed duration and active area`() {
        val together = MiningEventPatterns.parseBoss("§e§lPASSIVE EVENT §b§l§D§LBETTER TOGETHER §e§lRUNNING FOR §a§l06:34§r")!!
        assertEquals(MiningEventPatterns.Type.BETTER_TOGETHER, together.type)
        assertEquals(394_000, together.remainingMillis)
        assertNull(together.location)
        assertEquals(MiningEventPatterns.Type.DOUBLE_POWDER,
            MiningEventPatterns.parseBoss("PASSIVE EVENT 2X POWDER RUNNING FOR 02:14")?.type)
        assertEquals(MiningEventPatterns.Type.GONE_WITH_THE_WIND,
            MiningEventPatterns.parseBoss("PASSIVE EVENT GONE WITH THE WIND RUNNING FOR 18:00")?.type)
        for ((title, type) in mapOf("GOBLIN RAID" to MiningEventPatterns.Type.GOBLIN_RAID,
            "RAFFLE" to MiningEventPatterns.Type.RAFFLE, "MITHRIL GOURMAND" to MiningEventPatterns.Type.MITHRIL_GOURMAND)) {
            val event = MiningEventPatterns.parseBoss("EVENT $title ACTIVE IN GOBLIN BURROWS for 01:06")!!
            assertEquals(type, event.type)
            assertEquals("GOBLIN BURROWS", event.location)
            assertEquals(66_000, event.remainingMillis)
        }
    }
    @Test fun `unknown bossbars malformed clocks and unrelated chat never become claims`() {
        for (line in listOf("PASSIVE EVENT FUTURE EVENT RUNNING FOR 06:34", "2X POWDER STARTED!", "BETTER TOGETHER",
            "PASSIVE EVENT 2X POWDER RUNNING FOR 2:99", "PASSIVE EVENT 2X POWDER RUNNING FOR -1:20",
            "PASSIVE EVENT 2X POWDER RUNNING FOR 2m14s", "Objective: PASSIVE EVENT 2X POWDER RUNNING FOR 02:14",
            "Player: PASSIVE EVENT 2X POWDER RUNNING FOR 02:14", "PASSIVE EVENT 2X POWDER RUNNING FOR 02:14 extra",
            "EVENT GOBLIN RAID ACTIVE IN  for 01:06", "PASSIVE EVENT RAFFLE RUNNING FOR 01:06"))
            assertNull(MiningEventPatterns.parseBoss(line), line)
        assertEquals(0, MiningEventPatterns.parseBoss("PASSIVE EVENT 2X POWDER RUNNING FOR 00:00")?.remainingMillis)
    }
    @Test fun `tab headers require adjacent end timer and exact known names`() {
        val event = MiningEventPatterns.parseTab(listOf("Mining Event: 2x Powder", "Ends in: 5m 30s"))!!
        assertEquals(MiningEventPatterns.Type.DOUBLE_POWDER, event.type)
        assertEquals(330_000, event.remainingMillis)
        assertTrue(MiningEventPatterns.tabNotAnnounced(listOf(" Mining Event: Not announced")))
        for (lines in listOf(listOf("Mining Event: Double Powder", "Ends in: 5m"),
            listOf("Mining Event: 2x Powder", "Other section:", "Ends in: 5m"),
            listOf("Mining Event: 2x Powder", "Ends in: 5m 60s"),
            listOf("Mining Event: 2x Powder", "Ends in: -5m"),
            listOf("Mining Event: 2x Powder", "Starts in: 5m"),
            listOf("Mining Event: 2x Powder", "Ends in: 3s 2m")))
            assertNull(MiningEventPatterns.parseTab(lines))
    }
    @Test fun `only exact ended announcements invalidate a named event and starts never invent duration`() {
        assertEquals(MiningEventPatterns.Type.DOUBLE_POWDER, MiningEventPatterns.endedType("§f       §r§b§l2X POWDER ENDED!"))
        assertNull(MiningEventPatterns.endedType("2X POWDER STARTED!"))
        assertNull(MiningEventPatterns.endedType("Player: 2X POWDER ENDED!"))
    }
}
