package dev.skyglint.features.bestiary

import dev.skyglint.core.*
import kotlin.test.*

/**
 * Source-shaped synthetic observations, not captured Hypixel chat. Only header, border and the
 * decimal milestone color boundary have upstream evidence. Description wording is deliberately
 * synthetic: these tests do not assert a live family/tier grammar.
 */
class BestiaryModuleTest {
    private var now = 0L
    private val module = BestiaryModule { now }
    private fun line(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun open() { assertTrue(line(BORDER).isEmpty()); assertTrue(line(HEADER).isEmpty()) }
    private fun complete(vararg body: String): List<CoreEvent> {
        open()
        body.forEach { assertTrue(line(it).isEmpty()) }
        return line(BORDER)
    }
    private fun notice(vararg body: String) = assertIs<CoreEvent.NoticeObserved>(complete(*body).single()).request

    @Test fun `complete exact framed block retains opaque native description and no invented numbers`() {
        val request = notice(DESCRIPTION, "§a+1 Combat XP")
        assertEquals("Bestiary updated", request.title)
        assertEquals("Observed family II", request.detail)
        assertEquals("minecraft:book", request.item?.vanillaItemId)
        assertNull(request.rarity)
        assertNull(request.magicFind)
        assertTrue(request.pages.isEmpty())
        assertFalse(request.detail!!.contains("Received"))
    }

    @Test fun `observed description and supported milestone produce exactly one combined notice`() {
        val request = notice(DESCRIPTION, "§6Observed milestone §812➡§e13", "§a+1 Health")
        assertEquals("Observed family II · Milestone 12 → 13", request.detail)
        assertEquals("minecraft:nether_star", request.item?.vanillaItemId)
        assertEquals(4_000L, request.durationMillis)
        assertEquals(10_000L, request.freshnessMillis)
    }

    @Test fun `supported milestone only does not repeat its opaque prefix`() {
        assertEquals("Milestone 0 → 1", notice("§6Observed milestone §80➡§e1").detail)
    }

    @Test fun `no header no opening border or no closing border cannot publish`() {
        for (text in listOf("BESTIARY", "§6§lBESTIARY", HEADER, DESCRIPTION, BORDER))
            assertTrue(line(text).isEmpty())
        module.onSessionEnded()
        assertTrue(line(HEADER).isEmpty())
        assertTrue(line(DESCRIPTION).isEmpty())
        assertTrue(line(BORDER).isEmpty())
        module.onSessionEnded()
        open()
        assertTrue(line(DESCRIPTION).isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Bestiary"))).isEmpty())
    }

    @Test fun `player imitations and plain BESTIARY cannot open a source block`() {
        for (header in listOf("BESTIARY", "[MVP+] Player: BESTIARY", "§fPlayer: §6§lBESTIARY",
            "§f${" ".repeat(33)}§6§lBESTIARY", "§f${" ".repeat(34)}§e§lBESTIARY")) {
            module.onSessionEnded()
            assertTrue(line(BORDER).isEmpty())
            assertTrue(line(header).isEmpty())
            assertTrue(line(DESCRIPTION).isEmpty())
            assertTrue(line(BORDER).isEmpty(), header)
        }
    }

    @Test fun `empty or reward only blocks do not mislabel rewards as a family`() {
        for (body in listOf("", "§f ", "§fBESTIARY", "§6REWARDS", "§a+10 Combat XP", "§aRewards:", "§f---")) {
            module.onSessionEnded()
            assertTrue(complete(body).isEmpty(), body)
        }
    }

    @Test fun `only exact colored decimal suffix contributes milestone data`() {
        assertEquals("Observed family II", notice(DESCRIPTION, "§6Observed milestone 12➡13").detail)
        now += 10_000
        assertEquals("Observed family II", notice(DESCRIPTION, "§6Observed milestone §712➡§e13").detail)
    }

    @Test fun `decreasing equal overlong trailing and conflicting milestones abort block`() {
        for (body in listOf(listOf("§6Observed milestone §813➡§e12"),
            listOf("§6Observed milestone §813➡§e13"), listOf("§6Observed milestone §8123➡§e1000"),
            listOf("§6Observed milestone §812➡§e13 extra"),
            listOf("§6Observed milestone §812➡§e13", "§6Observed milestone §813➡§e14"))) {
            module.onSessionEnded()
            assertTrue(complete(DESCRIPTION, *body.toTypedArray()).isEmpty(), body.toString())
        }
    }

    @Test fun `malformed unsafe oversized or ordinary chat interrupts without a later partial result`() {
        for (bad in listOf("Player: hello", "§fPlayer: hello", "§fBad\nline", "§fBad\rline", "§fBad\u0000line",
            "§fBad\tline", "§fBad\u202Eline", "§fBad§", "§fBad§?line", "§f" + "a".repeat(511), HEADER)) {
            module.onSessionEnded()
            open()
            assertTrue(line(bad).isEmpty())
            assertTrue(line(DESCRIPTION).isEmpty())
            assertTrue(line(BORDER).isEmpty(), bad)
        }
    }

    @Test fun `description bound and forbidden caption separators reject whole block`() {
        for (body in listOf("§f" + "a".repeat(101), "§fObserved | injected", "§fObserved <injected>")) {
            module.onSessionEnded()
            assertTrue(complete(body).isEmpty())
        }
    }

    @Test fun `blank gap is allowed and collection never exceeds fifteen lines`() {
        assertTrue(line(BORDER).isEmpty())
        assertTrue(line("").isEmpty())
        assertTrue(line(HEADER).isEmpty())
        assertTrue(line(DESCRIPTION).isEmpty())
        repeat(10) { assertTrue(line("§f").isEmpty()) }
        assertEquals(1, line(BORDER).size) // 15 total including both borders.
        now += 10_000
        open()
        assertTrue(line(DESCRIPTION).isEmpty())
        repeat(12) { assertTrue(line("§f").isEmpty()) }
        assertTrue(line(BORDER).isEmpty())
    }

    @Test fun `time bound includes opening and does not slide with body lines`() {
        open()
        now = 1_999
        assertTrue(line(DESCRIPTION).isEmpty())
        now = 2_000
        assertEquals(1, line(BORDER).size)
        now += 10_000
        open()
        now += 1_999
        assertTrue(line(DESCRIPTION).isEmpty())
        now += 2
        assertTrue(line(BORDER).isEmpty())
    }

    @Test fun `clock rollback expires partial block`() {
        now = 100
        open()
        assertTrue(line(DESCRIPTION).isEmpty())
        now = 99
        assertTrue(line(BORDER).isEmpty())
    }

    @Test fun `same block burst is deduped and genuine later changed description is accepted`() {
        val first = notice(DESCRIPTION)
        now = 100
        assertTrue(complete(DESCRIPTION).isEmpty())
        val next = notice("§fObserved family III")
        assertNotEquals(first.id, next.id)
        assertEquals("Observed family III", next.detail)
        now = 10_000
        assertEquals(1, complete(DESCRIPTION).size)
    }

    @Test fun `both session hooks clear pending block and dedupe`() {
        notice(DESCRIPTION)
        module.onSessionStarted("profile:other")
        assertEquals(1, complete(DESCRIPTION).size)
        open()
        assertTrue(line(DESCRIPTION).isEmpty())
        module.onSessionEnded()
        assertTrue(line(BORDER).isEmpty())
        module.onSessionStarted("profile:next")
        assertEquals(1, complete(DESCRIPTION).size)
    }

    @Test fun `menus snapshots actionbars and unclosed progress cannot manufacture an event`() {
        val signals = listOf(NormalizedSignal.MenuSnapshot("Bestiary", listOf(
            MenuItemSnapshot(1, "Observed family III", listOf("Kills: 1,000", "Progress to Tier IV: 10%")))),
            NormalizedSignal.TabSnapshot(listOf("Bestiary:", "Observed family III")),
            NormalizedSignal.ActionBar("+100 Combat (Bestiary)"),
            NormalizedSignal.SidebarSnapshot(listOf("BESTIARY", "Observed family III")))
        signals.forEach { assertTrue(module.parse(it).isEmpty()) }
    }

    @Test fun `controller preemption resumes short confirmation while original boss remains authoritative`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        core.enterSkyBlock("profile:one", now)
        val boss = java.util.UUID.randomUUID()
        core.updateBoss(boss, "Unknown server objective", .6f, now = now)
        fun server(text: String) = core.onChat(text, now)
        server(BORDER); server(HEADER); server(DESCRIPTION)
        assertEquals(1, server(BORDER).size)
        val first = core.snapshot(now).activeNotice!!
        now = 1_000
        core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("urgent", "Confirmed urgent", priority = 100))), now)
        assertEquals("urgent", core.snapshot(now).activeNotice?.id)
        assertEquals(boss, core.snapshot(now).bosses.single().id)
        now = 5_000
        val resumed = core.snapshot(now).activeNotice!!
        assertEquals(first.id, resumed.id)
        assertEquals(first.compactCaption, resumed.compactCaption)
        assertEquals("Bestiary · Observed family II", resumed.compactCaption)
        assertEquals(1_000L, resumed.presentationOffsetMillis)
        assertNull(core.snapshot(8_000).activeNotice)
        assertEquals(boss, core.snapshot(8_000).bosses.single().id)
        assertNull(core.snapshot(8_000).progression)
    }

    @Test fun `controller isolates session fragments and expired queued confirmations cannot resurrect`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        assertTrue(core.onChat(BORDER, now).isEmpty()) // Outside confirmed SkyBlock.
        core.enterSkyBlock("profile:one", now)
        core.onChat(BORDER, now); core.onChat(HEADER, now); core.onChat(DESCRIPTION, now)
        core.enterSkyBlock("profile:two", now)
        assertTrue(core.onChat(BORDER, now).isEmpty())
        assertNull(core.snapshot(now).activeNotice)
        core.leaveSkyBlock()
        core.enterSkyBlock("profile:three", now)
        core.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("urgent", "Confirmed urgent", priority = 100,
            durationMillis = 15_000, freshnessMillis = 15_000))), now)
        core.onChat(BORDER, now); core.onChat(HEADER, now); core.onChat(DESCRIPTION, now)
        assertEquals(1, core.onChat(BORDER, now).size)
        assertEquals("urgent", core.snapshot(now).activeNotice?.id)
        now = 15_001
        assertNull(core.snapshot(now).activeNotice)
        core.leaveSkyBlock()
        assertFalse(core.snapshot(now).active)
    }

    companion object {
        private val BORDER = "§3§l" + "▬".repeat(64)
        private val HEADER = "§f" + " ".repeat(34) + "§6§lBESTIARY"
        private const val DESCRIPTION = "§fObserved family II"
    }
}
