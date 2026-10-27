package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleActivityModuleTest {
    @Test fun `chest lockpick is observed activity while a location and powder counter are not`() {
        val module = SimpleActivityModule { 100L }
        val event = module.parse(NormalizedSignal.Chat("  §r§6§lCHEST LOCKPICKED ")).single() as CoreEvent.WidgetObserved
        assertEquals("Powder grinding", event.widget.title)
        assertEquals(60_100L, event.widget.expiresAt)
        assertNull(event.widget.progress)
        assertTrue(module.parse(NormalizedSignal.Chat("Area: Crystal Hollows")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("+100 Mithril Powder")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.ActionBar("CHEST LOCKPICKED")).isEmpty())
    }

    @Test fun `crystal observations are owned exclusively by short nucleus notices`() {
        val module = SimpleActivityModule { 0L }
        assertTrue(module.parse(NormalizedSignal.Chat("§f                       §r§5§l✦ CRYSTAL FOUND §r§7(1§r§7/5§r§7)")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("§5§l✦ §r§dYou placed the §r§bSapphire Crystal§r§d!")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("✦ CRYSTAL FOUND (6/5)")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("✦ You placed the Ruby Crystal!")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("[VIP] player: CHEST LOCKPICKED")).isEmpty())
    }

    @Test fun `repeats coalesce one widget and latest real action expires after one minute`() {
        var clock = 0L
        val controller = CoreController().apply {
            modules.register(SimpleActivityModule { clock })
            enterSkyBlock("session", clock)
        }
        repeat(10) {
            clock++
            controller.onChat("CHEST LOCKPICKED", clock)
        }
        assertEquals(1, controller.snapshot(clock).widgets.size)
        clock = 100
        controller.onChat("CHEST LOCKPICKED", clock)
        assertEquals(1, controller.snapshot(clock).widgets.size)
        assertEquals("Chest lockpicked", controller.snapshot(clock).widgets.single().detail)
        assertEquals(1, controller.snapshot(60_099).widgets.size)
        assertTrue(controller.snapshot(60_100).widgets.isEmpty())
        controller.leaveSkyBlock()
        assertTrue(controller.snapshot(60_100).widgets.isEmpty())
    }
}
