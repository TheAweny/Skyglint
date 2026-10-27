package dev.skyglint.ui

import dev.skyglint.ui.presentation.MiningBossVisuals
import kotlin.test.*
import org.junit.jupiter.api.Test

class MiningBossVisualsTest {
    @Test fun `six typed mining event IDs have distinct compact presentation hints`() {
        val ids = listOf("double_powder", "better_together", "gone_with_the_wind", "goblin_raid", "raffle", "mithril_gourmand")
        val visuals = ids.map { MiningBossVisuals.resolve(it)!! }
        assertEquals("×2", visuals.first().shortLabel)
        assertEquals("minecraft:redstone", visuals.first().icon)
        assertEquals(6, visuals.map { it.icon }.toSet().size)
        assertTrue(visuals.all { it.title.isNotBlank() && it.shortLabel.length <= 10 })
    }
    @Test fun `raw boss text and guessed unknown IDs never create a visual claim`() {
        for (id in listOf("PASSIVE EVENT 2X POWDER RUNNING FOR 02:14", "DOUBLE_POWDER", "future_event", "powder", ""))
            assertNull(MiningBossVisuals.resolve(id))
    }
}
