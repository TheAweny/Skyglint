package dev.skyglint.features.buffs

import dev.skyglint.core.*
import kotlin.test.*

class BuffExpiryModuleTest {
    private var now = 0L
    private fun module() = BuffExpiryModule { now }.apply { onSessionStarted("one") }
    private fun context(module: BuffExpiryModule, mode: String? = "hub") =
        module.parse(NormalizedSignal.ContextSnapshot("Village", mode))
    private fun cookie(value: String) = NormalizedSignal.TabSnapshot(listOf("Cookie Buff", value))
    private fun godActive() = NormalizedSignal.TabSnapshot(listOf("Active Effects", "You have a God Potion active! 12 Hours"))
    private fun noEffects() = NormalizedSignal.TabSnapshot(listOf("Active Effects",
        "No effects active. Drink Potions or splash them on the", "ground to buff yourself!"))
    private fun cookieInactive() = cookie("Not active! Obtain booster cookies from the community")
    private fun notice(events: List<CoreEvent>): NoticeRequest = events.single().let { (it as CoreEvent.NoticeObserved).request }

    @Test fun `healthy buffs create no widgets and explicit inactive transitions create short notices once`() {
        val module = module()
        context(module)
        assertTrue(module.parse(cookie("4d 12h")).isEmpty())
        assertTrue(module.parse(godActive()).isEmpty())
        now = 100
        val cookie = notice(module.parse(cookieInactive()))
        assertEquals("Booster Cookie no longer active", cookie.title)
        assertEquals("minecraft:cookie", cookie.item!!.vanillaItemId)
        assertEquals(4_000, cookie.durationMillis)
        assertTrue(module.parse(cookieInactive()).isEmpty())
        val god = notice(module.parse(noEffects()))
        assertEquals("God Potion no longer active", god.title)
        assertTrue(module.parse(noEffects()).isEmpty())
        assertNull(god.countdown)
        assertNull(god.target)
    }

    @Test fun `first inactive login is silent and absent zero malformed or conflicting data never expire buffs`() {
        val module = module()
        context(module)
        assertTrue(module.parse(cookieInactive()).isEmpty())
        assertTrue(module.parse(noEffects()).isEmpty())
        module.parse(cookie("4d")); module.parse(godActive())
        for (signal in listOf(
            NormalizedSignal.TabSnapshot(emptyList()),
            cookie("0s"), cookie("4d unknown"), cookie("999999999999999999999d"),
            NormalizedSignal.TabSnapshot(listOf("Not active! Obtain booster cookies from the community")),
            NormalizedSignal.TabSnapshot(listOf("Active Effects", "You have 10 non-god effects.")),
            NormalizedSignal.TabSnapshot(listOf("Active Effects", "No effects active. Drink Potions or splash them on the")),
            NormalizedSignal.MenuSnapshot("Active Effects", emptyList()),
            NormalizedSignal.TabSnapshot(listOf("Cookie Buff", "4d", "Cookie Buff", "Not active! Obtain booster cookies from the community")),
        )) assertTrue(module.parse(signal).isEmpty(), signal.javaClass.simpleName)
        assertNotNull(notice(module.parse(cookieInactive())))
        assertNotNull(notice(module.parse(noEffects())))
    }

    @Test fun `God Potion never expires from elapsed wall time paused context or missing context`() {
        val module = module()
        module.parse(godActive())
        now = 999_999_999
        assertTrue(module.parse(noEffects()).isEmpty())
        for (mode in listOf("dungeon", "dungeon_hub", "rift", "limbo", "unknown", null)) {
            context(module, mode)
            assertTrue(module.parse(noEffects()).isEmpty(), mode)
        }
        context(module)
        now += 5_000
        assertTrue(module.parse(noEffects()).isEmpty())
        context(module)
        assertEquals("God Potion no longer active", notice(module.parse(noEffects())).title)
    }

    @Test fun `cookie wallclock semantics do not become a locally inferred deadline`() {
        val module = module()
        assertEquals("Booster Cookie expiring soon", notice(module.parse(cookie("1s"))).title)
        now = Long.MAX_VALUE / 2
        assertTrue(module.parse(NormalizedSignal.ContextSnapshot("The Catacombs", "dungeon")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(emptyList())).isEmpty())
        assertEquals("Booster Cookie no longer active", notice(module.parse(cookieInactive())).title)
    }

    @Test fun `consumption rearms transitions and grace suppresses lagging inactive snapshots`() {
        val module = module()
        context(module)
        module.parse(godActive()); module.parse(cookie("1d"))
        val firstCookie = notice(module.parse(cookieInactive()))
        val firstGod = notice(module.parse(noEffects()))
        now = 100
        assertTrue(module.parse(NormalizedSignal.Chat("§eYou consumed a §6Booster Cookie§e! §dYummy!")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("GULP! The God Potion grants you powers for 28h 48m!")).isEmpty())
        assertTrue(module.parse(cookieInactive()).isEmpty())
        assertTrue(module.parse(noEffects()).isEmpty())
        now = 3_100
        context(module)
        val secondCookie = notice(module.parse(cookieInactive()))
        val secondGod = notice(module.parse(noEffects()))
        assertNotEquals(firstCookie.id, secondCookie.id)
        assertNotEquals(firstGod.id, secondGod.id)
        assertTrue(module.parse(cookieInactive()).isEmpty())
        assertTrue(module.parse(noEffects()).isEmpty())
    }

    @Test fun `cookie menus require exact title named item and explicit inactive lore`() {
        val module = module()
        val active = MenuItemSnapshot(10, "§6Booster Cookie", listOf(" §7Duration: §a140d 8h 35m 36s"))
        val inactive = MenuItemSnapshot(10, "Booster Cookie", listOf(" §7Status: §cNot active!"))
        module.parse(NormalizedSignal.MenuSnapshot("SkyBlock Menu", listOf(active)))
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("Auction House", listOf(inactive))).isEmpty())
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("SkyBlock Menu", emptyList())).isEmpty())
        assertEquals("Booster Cookie no longer active", notice(module.parse(NormalizedSignal.MenuSnapshot("SkyBlock Menu", listOf(inactive)))).title)
        module.parse(cookie("2d"))
        val split = MenuItemSnapshot(13, "Booster Cookie", listOf("§7§cYou do not currently have a", "§cBooster Cookie active!"))
        assertNotNull(notice(module.parse(NormalizedSignal.MenuSnapshot("Booster Cookie", listOf(split)))))
    }

    @Test fun `God menu positivity requires observed selected filter and timer but missing slot stays unknown`() {
        val module = module()
        context(module)
        val filter = MenuItemSnapshot(50, "Filter", listOf("▶ God Potion Effects"))
        val potion = MenuItemSnapshot(10, "Speed", listOf("Remaining: 105:01:34"))
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("(1/2) Active Effects", listOf(filter, potion))).isEmpty())
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("Active Effects", listOf(filter))).isEmpty())
        assertNotNull(notice(module.parse(noEffects())))
        val other = module()
        context(other)
        other.parse(NormalizedSignal.MenuSnapshot("Active Effects", listOf(potion)))
        assertTrue(other.parse(noEffects()).isEmpty())
    }

    @Test fun `unknown chat and player styled quotations cannot arm expiry state`() {
        val module = module()
        context(module)
        for (line in listOf("[MVP+] Someone: You consumed a Booster Cookie!", "Your God Potion expired!",
            "The God Potion grants you powers for 28h 48m!", "SIP! The God Potion grants you powers for nope!"))
            assertTrue(module.parse(NormalizedSignal.Chat(line)).isEmpty())
        assertTrue(module.parse(cookieInactive()).isEmpty())
        assertTrue(module.parse(noEffects()).isEmpty())
    }

    @Test fun `session reset cannot carry positive baseline into reconnect inactive status`() {
        val module = module()
        context(module)
        module.parse(cookie("1d")); module.parse(godActive())
        module.onSessionEnded(); module.onSessionStarted("two")
        context(module)
        assertTrue(module.parse(cookieInactive()).isEmpty())
        assertTrue(module.parse(noEffects()).isEmpty())
    }
}
