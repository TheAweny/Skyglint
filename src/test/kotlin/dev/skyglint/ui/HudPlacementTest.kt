package dev.skyglint.ui

import dev.skyglint.ui.layout.HudPlacement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudPlacementTest {
    @Test fun `normalized corner placement keeps the whole changing row onscreen without shrinking default scale`() {
        for(guiScale in 1..4)for(designWidth in listOf(80,220,700))for(x in listOf(-1.0,0.0,1.0))for(y in listOf(0.0,.5,1.0)) {
            val w=1920/guiScale;val h=1080/guiScale
            val base=HudViewport.fit(w,h,guiScale,1.5,designWidth,30,12,1000)
            val placed=HudPlacement(x,y).place(base,w,h,30f)
            assertEquals(45.0,placed.scale*30*guiScale.toDouble(),.001)
            assertTrue(placed.x>=5.99&&placed.y>=5.99)
            assertTrue(placed.x+placed.width*placed.scale<=w-5.99)
            assertTrue(placed.y+30*placed.scale<=h-5.99)
        }
    }
    @Test fun `legacy top offset survives and normalized position adapts to resize and scale`() {
        val base=HudViewport.fit(427,240,2,1.5,240,44,37,1000)
        assertEquals(37f,HudPlacement().place(base,427,240,44f).y)
        val bottom=HudPlacement(1.0,1.0).place(base,427,240,44f)
        assertEquals(234f,bottom.y+44*bottom.scale,.001f)
        val resized=HudViewport.fit(854,480,1,3.0,240,44,37,1000)
        val next=HudPlacement(1.0,1.0,2.0).place(resized,854,480,44f)
        assertEquals(848f,next.x+next.width*next.scale,.001f)
        assertEquals(474f,next.y+44*next.scale,.001f)
    }
}
