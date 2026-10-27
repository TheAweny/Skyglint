package dev.skyglint.ui

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.RankVisuals
import org.junit.jupiter.api.Test
import kotlin.test.*

class RankVisualsTest {
    @Test fun `all six packaged sprites are small transparent 32 pixel PNG assets`() {
        for (file in listOf("d", "c", "b", "a", "s", "s_plus")) {
            val resource = "/assets/skyglint/textures/gui/sprites/ranks/$file.png"
            val bytes = assertNotNull(javaClass.getResourceAsStream(resource), resource).use { it.readBytes() }
            assertTrue(bytes.size < 4_096, resource)
            val image = javax.imageio.ImageIO.read(bytes.inputStream())
            assertEquals(32, image.width, resource)
            assertEquals(32, image.height, resource)
            var opaque = 0
            var transparent = 0
            for (y in 0 until 32) for (x in 0 until 32) {
                val alpha = image.getRGB(x, y).ushr(24)
                assertTrue(alpha == 0 || alpha == 255, resource)
                if (alpha == 0) transparent++ else opaque++
            }
            assertTrue(opaque > 100 && transparent > 100, resource)
        }
    }
    @Test fun `six known source ranks select distinct sprite icons without duplicated rank text`() {
        for ((rank, file) in mapOf("D" to "d", "C" to "c", "B" to "b", "A" to "a", "S" to "s", "S+" to "s_plus")) {
            val score = RankVisuals.score("210 · $rank")!!
            assertEquals("210", score.number)
            assertEquals("@rank:$rank", score.icon)
            assertEquals("ranks/$file", RankVisuals.sprite(score.icon))
        }
    }

    @Test fun `unknown ranks and invented incomplete score forms do not get a known sprite`() {
        for (value in listOf("—", "210", "210 · Unknown", "210 · SS", "Estimate 210 · B", "-1 · D", "210 · B extra"))
            assertNull(RankVisuals.score(value), value)
        for (reference in listOf("@rank:Unknown", "@rank:s", "minecraft:nether_star", "@rank:B/../../x"))
            assertNull(RankVisuals.sprite(reference), reference)
        assertNull(RankVisuals.sprite(null))
    }

    @Test fun `unknown dungeon score keeps neutral fallback rather than manufacturing a rank`() {
        val widget = ModuleWidget("skyglint.dungeon.run", "Catacombs F7", "Exploring",
            presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:iron_sword"))
        val snapshot = HudSnapshot(true, "fixture", emptyList(), null, null, null, widgets = listOf(widget))
        val score = HudComposition().compose(snapshot, 0).single { it.id == "group:dungeon:run" }.parts.single()
        assertEquals("—", score.text)
        assertEquals("minecraft:nether_star", score.icon)
    }
}
