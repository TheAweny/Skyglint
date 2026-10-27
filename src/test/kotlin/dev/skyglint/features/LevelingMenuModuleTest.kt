package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelingMenuModuleTest {
    private val module = LevelingMenuModule()
    private fun menu(title: String = "SkyBlock Menu", name: String = "§aSkyBlock Leveling", xp: String = "24", includeLevel: Boolean = true) =
        NormalizedSignal.MenuSnapshot(title, listOf(MenuItemSnapshot(11, name, buildList {
            if (includeLevel) add("§7Your SkyBlock Level: §8[§9287§8]")
            add("§3§l§m      §f§l§m                   §r §b${xp}§3/§b100 §bXP")
        })))

    @Test fun `authentic SkyBlock Leveling lore establishes absolute snapshot without a celebration`() {
        val event = assertIs<CoreEvent.SkyBlockObserved>(module.parse(menu()).single())
        assertEquals(287, event.snapshot.level)
        assertEquals(24.0, event.snapshot.progress!!.current)
        val controller = CoreController().apply { modules.register(module); enterSkyBlock("session", 0) }
        controller.ingest(menu(), 1)
        val view = controller.snapshot(1)
        assertEquals(24.0, assertIs<ProgressionView.SkyBlock>(view.progression).progress!!.current)
        assertNull(view.notice)
        assertTrue(controller.ingest(menu(), 2).isEmpty())
    }

    @Test fun `unrelated menu wrong item partial and invalid lore stay unknown`() {
        listOf(menu(title = "Auction House"), menu(name = "Other item"), menu(includeLevel = false), menu(xp = "100"), menu(xp = "999"))
            .forEach { assertTrue(module.parse(it).isEmpty()) }
        assertTrue(module.parse(NormalizedSignal.Chat("Your SkyBlock Level: [287]")).isEmpty())
    }
}
