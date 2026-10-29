package dev.skyglint.ui

import dev.skyglint.ui.presentation.CarouselGeometry
import kotlin.test.*

class CarouselGeometryTest {
    @Test fun `short location pet and level groups share the center of a fixed page viewport`() {
        val pages = listOf(48, 92, 125).map { CarouselGeometry.contentWidth(it, true) }
        val width = CarouselGeometry.width(132, pages).toFloat()
        val layout = CarouselGeometry.layout(width, 132, pages)
        for (textWidth in listOf(48, 92, 125)) {
            val group = layout.group(textWidth, true)
            assertEquals(group.left - layout.pageLeft,
                layout.pageRight - group.textLeft - textWidth, .0001f)
            assertEquals(5f, group.textLeft - (group.iconCenter!! + 8f))
            assertEquals((layout.pageLeft + layout.pageRight) / 2,
                (group.left + group.textLeft + textWidth) / 2, .0001f)
            assertTrue(group.left >= layout.pageLeft)
            assertTrue(group.textLeft + textWidth <= layout.pageRight)
        }
        assertEquals(14f, layout.headingLeft)
        assertEquals(width - 14, layout.pageRight)
    }

    @Test fun `text-only and icon-only pages reserve only their displayed content`() {
        val pages = listOf(CarouselGeometry.contentWidth(100, true), CarouselGeometry.contentWidth(48, false))
        val layout = CarouselGeometry.layout(CarouselGeometry.width(132, pages).toFloat(), 132, pages)
        val text = layout.group(48, false)
        assertNull(text.iconCenter)
        assertEquals(text.left, text.textLeft)
        assertEquals(layout.pageLeft + layout.pageWidth / 2, text.textLeft + 24)
        val icon = layout.group(0, true)
        assertEquals(layout.pageLeft + layout.pageWidth / 2, icon.iconCenter)
        assertEquals(16f, CarouselGeometry.contentWidth(0, true))
        assertEquals(layout.pageWidth.toInt(), layout.textBudget(false))
    }

    @Test fun `body clipping keeps the complete caption allocation and icon inside its logical viewport`() {
        val pages = listOf(CarouselGeometry.contentWidth(200, true))
        val width = CarouselGeometry.width(132, pages).toFloat() - 100
        val layout = CarouselGeometry.layout(width, 132, pages)
        val budget = layout.textBudget(true)
        assertEquals(200, budget)
        assertTrue(layout.pageRight > width - 14, "The body scissor reveals the logical allocation")
        val group = layout.group(200, true)
        assertEquals(group.left - layout.pageLeft, layout.pageRight - group.textLeft - 200, .0001f)
        assertTrue(group.iconCenter!! - 8 >= layout.pageLeft)
        assertTrue(group.textLeft + 200 <= layout.pageRight)
        // Subtracting translated float edges loses a pixel here: 220.99998 instead of 221.
        val fractionalBody = CarouselGeometry.layout(145.081085205f, 132, pages)
        assertEquals(221f, fractionalBody.pageWidth)
        assertEquals(200, fractionalBody.textBudget(true), "Fractional body positions must not animate an ellipsis")
    }

    @Test fun `horizontal layers keep the same centered anchor through motion and GUI scaling`() {
        for (heading in listOf(132, 149)) { // Welcome and Jacob use the same layout contract.
            val pages = listOf(48, 92, 125).map { CarouselGeometry.contentWidth(it, true) }
            val finalWidth = CarouselGeometry.width(heading, pages).toFloat()
            val layout = CarouselGeometry.layout(finalWidth, heading, pages)
            for (animatedWidth in listOf(70f, finalWidth - 100, finalWidth - 10, finalWidth)) {
                val entering = CarouselGeometry.layout(animatedWidth, heading, pages)
                assertEquals(layout.pageWidth, entering.pageWidth)
                assertEquals(125, entering.textBudget(true), "Body reveal must not fit text to an animated clip")
                assertEquals(layout.pageLeft - layout.headingLeft, entering.pageLeft - entering.headingLeft)
            }
            val settled = layout.group(48, true)
            for (offset in listOf(-18f, -3.5f, 0f, 7.25f, 18f)) {
                val moving = layout.group(48, true, offset)
                assertEquals(settled.left + offset, moving.left)
                assertEquals(settled.textLeft + offset, moving.textLeft)
                for (scale in listOf(.375f, .5f, .75f, 1.5f)) {
                    val groupCenter = (moving.left + moving.textLeft + 48) / 2
                    assertEquals((layout.pageLeft + layout.pageRight) / 2 * scale,
                        (groupCenter - offset) * scale, .0001f)
                }
            }
        }
    }
}
