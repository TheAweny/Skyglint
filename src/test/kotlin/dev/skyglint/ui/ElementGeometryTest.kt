package dev.skyglint.ui

import dev.skyglint.ui.presentation.ElementGeometry
import kotlin.test.*
import org.junit.jupiter.api.Test

class ElementGeometryTest {
    @Test fun `short title and exact level never ellipsize just because an icon is present`() {
        for (textWidth in listOf(37, 55, 97, 150, 210)) for (tailWidth in listOf(0, 41)) {
            for (icon in listOf(false, true)) {
                val width = ElementGeometry.mainWidth(textWidth, tailWidth, icon)
                assertTrue(ElementGeometry.textBudget(width - .4, icon) >= textWidth + tailWidth,
                    "text=$textWidth tail=$tailWidth icon=$icon width=$width")
            }
        }
    }
    @Test fun `only genuinely long content reaches the explicit main width bound`() {
        assertEquals(320.0, ElementGeometry.mainWidth(900, 50, true))
        assertTrue(ElementGeometry.textBudget(320.0, true) < 900)
    }
    @Test fun `main icon and caption form one centered group with exactly six pixels between them`() {
        val body = ElementGeometry.mainWidth(110, 0, true).toFloat()
        val group = ElementGeometry.group(body, 110, true)
        val iconRight = group.iconCenter!! + ElementGeometry.ICON_SIZE / 2f
        assertEquals(6f, group.textLeft - iconRight)
        assertEquals(group.left, body - group.textLeft - 110)
        assertTrue(group.left in 12f..14f)
        val noIcon = ElementGeometry.group(150f, 100, false)
        assertNull(noIcon.iconCenter)
        assertEquals(25f, noIcon.textLeft)
    }
}
