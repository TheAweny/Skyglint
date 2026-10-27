package dev.skyglint.features.slayer

import dev.skyglint.core.*
import java.util.UUID
import kotlin.test.*

class SlayerModuleTest {
    private var now = 0L
    private val module = SlayerModule { now }
    private fun context(mode: String? = "combat_3") = module.parse(NormalizedSignal.ContextSnapshot("Void Sepulture", mode))
    private fun chat(line: String) = module.parse(NormalizedSignal.Chat(line))
    private fun sidebar(stage: String = "(1.2k/21k) Combat XP", name: String = "Voidgloom Seraph IV") =
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("SKYBLOCK", "Slayer Quest", name, stage)))
    private fun List<CoreEvent>.widget(): ModuleWidget = assertIs<CoreEvent.WidgetObserved>(single()).widget
    private fun List<CoreEvent>.notice(): NoticeRequest = filterIsInstance<CoreEvent.NoticeObserved>().single().request
    private fun List<CoreEvent>.removed() = assertEquals(listOf(CoreEvent.WidgetRemoved(SlayerModule.WIDGET_ID)), this)

    @Test fun `initial explicit active quest is one leased PRIMARY widget without manufacturing skill gain`() {
        context()
        val widget = sidebar().widget()
        assertEquals("Voidgloom Seraph IV", widget.title)
        assertEquals("1.2k/21k Combat XP", widget.detail)
        assertEquals(1_200.0, widget.progress?.current)
        assertEquals(21_000.0, widget.progress?.maximum)
        assertEquals(WidgetRole.PRIMARY, widget.presentation?.role)
        assertTrue(widget.presentation?.metrics.orEmpty().isEmpty())
        assertTrue(widget.replacedBossIds.isEmpty())
        assertEquals(5_000L, widget.expiresAt)
        assertTrue(module.parse(NormalizedSignal.ActionBar("+100 Combat (2,000/12,000)")).isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Slayer Quest", "Voidgloom Seraph IV", "Slay the boss!"))).isEmpty())
    }

    @Test fun `fresh copied block maintains quest lease and exact progress while context alone never renews it`() {
        context(); sidebar()
        now = 4_000; context()
        val refreshed = sidebar("(2.4k/21k) Combat XP").widget()
        assertEquals(9_000L, refreshed.expiresAt)
        now = 8_000
        assertEquals(9_000L, context().widget().expiresAt)
        now = 9_000; context().removed()
        assertEquals("2.4k/21k Combat XP", sidebar("(2.4k/21k) Combat XP").widget().detail)
    }

    @Test fun `fight is observed text without invented HP lifetime countdown or boss claim`() {
        context()
        val widget = sidebar("Slay the boss!").widget()
        assertEquals("Slay the boss!", widget.detail)
        assertNull(widget.progress)
        assertTrue(widget.replacedBossIds.isEmpty())
        assertTrue(module.parse(NormalizedSignal.BossSnapshot(listOf(BossBarState(UUID.randomUUID(), "Voidgloom", 0.4f)))).isEmpty())
        assertTrue(chat("The boss disappeared").isEmpty())
    }

    @Test fun `stale grinding does not overwrite fight or extend its presentation lease`() {
        context(); sidebar()
        now = 1_000; sidebar("Slay the boss!")
        now = 4_000; context()
        val result = sidebar("(20k/21k) Combat XP").widget()
        assertEquals("Slay the boss!", result.detail)
        assertEquals(6_000L, result.expiresAt)
        now = 6_000; context(); sidebar().removed()
    }

    @Test fun `lower same quest progress cannot replace a fresh positive observation or count as a new quest`() {
        context(); sidebar("(10k/21k) Combat XP")
        now = 1_000
        assertEquals("10k/21k Combat XP", sidebar("(1k/21k) Combat XP").widget().detail)
        assertEquals("11k/21k Combat XP", sidebar("(11k/21k) Combat XP").widget().detail)
        assertEquals(11_000.0, sidebar("(11k/21k) Combat XP").widget().progress?.current)
    }

    @Test fun `slain requires prior quest and coalesces sidebar plus complete and slain chat`() {
        context()
        sidebar("Boss slain!").removed()
        assertTrue(chat("NICE! SLAYER BOSS SLAIN!").isEmpty())
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
        sidebar()
        val result = sidebar("Boss slain!")
        assertEquals("Voidgloom Seraph IV slain", result.notice().title)
        assertEquals(4_000L, result.notice().durationMillis)
        assertEquals(6_000L, result.notice().freshnessMillis)
        assertNull(result.notice().countdown)
        assertNull(result.notice().rarity)
        assertNull(result.notice().magicFind)
        assertNull(result.notice().item?.itemName)
        assertTrue(result.notice().pages.isEmpty())
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
        assertTrue(chat("NICE! SLAYER BOSS SLAIN!").isEmpty())
        sidebar("Boss slain!").removed()
        sidebar().removed()
        sidebar("Slay the boss!").removed()
    }

    @Test fun `explicit complete and failure are distinct confirmations and only first outcome wins`() {
        context(); sidebar()
        assertEquals("Voidgloom Seraph IV quest complete", chat("SLAYER QUEST COMPLETE!").notice().title)
        assertTrue(chat("SLAYER QUEST FAILED!").isEmpty())
        chat("SLAYER QUEST STARTED!")
        sidebar()
        assertEquals("Voidgloom Seraph IV quest failed", chat("SLAYER QUEST FAILED!").notice().title)
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
    }

    @Test fun `cancellation removes quietly while disappearance expiry and malformed progress never confirm failure`() {
        context(); sidebar()
        chat("Your Slayer Quest has been cancelled!").removed()
        assertTrue(chat("SLAYER QUEST FAILED!").isEmpty())
        chat("SLAYER QUEST STARTED!"); sidebar()
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("SKYBLOCK", "⏣ Village"))).removed()
        assertEquals("Voidgloom Seraph IV quest failed", chat("SLAYER QUEST FAILED!").notice().title)
        chat("SLAYER QUEST STARTED!"); sidebar()
        sidebar("(99/0) Combat XP").removed()
        now = 5_000; context().removed()
    }

    @Test fun `terminal block must refer to current quest and cannot claim a different boss was slain`() {
        context(); sidebar()
        sidebar("Boss slain!", "Sven Packmaster IV").removed()
        assertEquals("Voidgloom Seraph IV slain", chat("NICE! SLAYER BOSS SLAIN!").notice().title)
    }

    @Test fun `incomplete malformed or unsupported quest stage cannot establish outcome association`() {
        context()
        for (stage in listOf("", "(1/0) Combat XP", "Collect XP", "Boss disappeared!")) {
            sidebar(stage).removed()
            assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
        }
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Slayer Quest", "Voidgloom Seraph IV"))).removed()
        assertTrue(chat("SLAYER QUEST FAILED!").isEmpty())
    }

    @Test fun `fresh exact start re-arms identical quest and resets previous progress without a guessed cooldown`() {
        context(); sidebar("(20k/21k) Combat XP")
        val first = chat("NICE! SLAYER BOSS SLAIN!").notice()
        chat("SLAYER QUEST STARTED!").removed()
        // A cached terminal/fight cannot attach to the newly started quest.
        sidebar("Boss slain!").removed()
        sidebar("Slay the boss!").removed()
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
        assertEquals("0/21k Combat XP", sidebar("(0/21k) Combat XP").widget().detail)
        assertNotEquals(first.id, chat("SLAYER QUEST COMPLETE!").notice().id)
    }

    @Test fun `new type or tier replaces previous association but aliases remain the same quest`() {
        context("hub"); sidebar("(1/400) Combat XP", "Revenant Horror V")
        sidebar("Slay the boss!", "Atoned Horror V")
        assertEquals("Atoned Horror V", sidebar("(1/400) Combat XP", "Atoned Horror V").widget().title)
        assertEquals("Slay the boss!", sidebar("(1/400) Combat XP", "Revenant Horror V").widget().detail)
        assertEquals("Revenant Horror IV", sidebar("(0/400) Combat XP", "Revenant Horror IV").widget().title)
    }

    @Test fun `unknown absent stale or wrong context cannot bind outcomes and mode changes reset association`() {
        for (mode in listOf(null, "dungeon", "garden", "mining_3", "kuudra")) {
            context(mode); sidebar().removed()
            assertTrue(chat("SLAYER QUEST COMPLETE!").none { it is CoreEvent.NoticeObserved })
        }
        context(); sidebar()
        now = 5_000
        assertTrue(chat("SLAYER QUEST COMPLETE!").none { it is CoreEvent.NoticeObserved })
        context()
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
        sidebar(); context("hub"); context()
        assertTrue(chat("SLAYER QUEST_COMPLETE!").isEmpty())
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
    }

    @Test fun `Rift accepts only Bloodfiend while ordinary networks cannot retain Rift association`() {
        context("rift")
        sidebar().removed()
        val vampire = sidebar("38/40 Kills", "Bloodfiend V").widget()
        assertEquals(0.95, vampire.progress?.fraction)
        context("hub")
        sidebar("38/40 Kills", "Bloodfiend V").removed()
        assertTrue(chat("SLAYER QUEST FAILED!").isEmpty())
        context("rift")
        assertTrue(chat("SLAYER QUEST FAILED!").isEmpty())
    }

    @Test fun `initial or repeated static snapshot cannot manufacture gains level up spawn or reward`() {
        context()
        repeat(10) {
            val events = sidebar()
            assertEquals(1, events.size)
            assertIs<CoreEvent.WidgetObserved>(events.single())
        }
        for (line in listOf("SLAYER MINI-BOSS Revenant Champion", "YOU COCOONED YOUR SLAYER BOSS",
            "RNG Meter Stored XP: 10,000", "LVL UP! ➜ Enderman Slayer LVL 7"))
            assertTrue(chat(line).isEmpty())
    }

    @Test fun `player chat echoes and malformed terminal lines do not consume valid quest`() {
        context(); sidebar()
        for (line in listOf("[VIP] Player: SLAYER QUEST COMPLETE!", "Party > Player: SLAYER QUEST FAILED!",
            "SLAYER QUEST COMPLETE! extra", "SLAYER QUEST FAILED!\n"))
            assertTrue(chat(line).isEmpty())
        assertEquals("Voidgloom Seraph IV slain", chat("NICE! SLAYER BOSS SLAIN!").notice().title)
    }

    @Test fun `hidden association expires even with fresh context and source disappearance`() {
        context(); sidebar()
        module.parse(NormalizedSignal.SidebarSnapshot(emptyList()))
        repeat(15) { now += 4_000; context() }
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
    }

    @Test fun `fresh context cannot attach terminal outcome to stale quest metadata`() {
        context(); sidebar()
        now = 4_000; context()
        now = 5_000
        chat("SLAYER QUEST COMPLETE!").removed()
        assertTrue(chat("NICE! SLAYER BOSS SLAIN!").isEmpty())
        sidebar().removed() // terminal evidence still closes stale association without showing a notice
    }

    @Test fun `clock regression and lifecycle clear source leases and terminal deduplication`() {
        now = 100; context(); sidebar()
        now = 99
        assertTrue(chat("SLAYER QUEST COMPLETE!").none { it is CoreEvent.NoticeObserved })
        context()
        assertTrue(chat("SLAYER QUEST COMPLETE!").isEmpty())
        sidebar(); module.onSessionEnded()
        assertTrue(chat("SLAYER QUEST COMPLETE!").none { it is CoreEvent.NoticeObserved })
        module.onSessionStarted("new"); context()
        sidebar("Boss slain!").removed()
        sidebar()
        assertEquals("Voidgloom Seraph IV quest complete", chat("SLAYER QUEST COMPLETE!").notice().title)
    }

    @Test fun `core preserves unknown UUIDs and ordinary progression through Slayer notice then returns without stale quest`() {
        val core = CoreController(modules = ModuleRegistry.defaults().register(module))
        val bossId = UUID.randomUUID()
        core.enterSkyBlock("session", now)
        core.ingest(NormalizedSignal.ContextSnapshot("Void Sepulture", "combat_3"), now)
        core.updateBoss(bossId, "Unknown boss", 0.75f, now = now)
        core.ingest(NormalizedSignal.SidebarSnapshot(listOf("Slayer Quest", "Voidgloom Seraph IV", "Slay the boss!")), now)
        assertEquals(setOf(bossId), core.snapshot(now).bosses.map { it.id }.toSet())
        assertTrue(core.snapshot(now).widgets.single().replacedBossIds.isEmpty())
        core.onChat("NICE! SLAYER BOSS SLAIN!", now)
        assertEquals("Voidgloom Seraph IV slain", core.snapshot(now).activeNotice?.title)
        assertTrue(core.snapshot(now).widgets.isEmpty())
        assertEquals(setOf(bossId), core.snapshot(now).bosses.map { it.id }.toSet())
        now = 4_000
        assertNull(core.snapshot(now).activeNotice)
        assertEquals(setOf(bossId), core.snapshot(now).bosses.map { it.id }.toSet())
        core.leaveSkyBlock(); core.enterSkyBlock("next", now)
        core.ingest(NormalizedSignal.ContextSnapshot("Void Sepulture", "combat_3"), now)
        assertTrue(core.onChat("SLAYER QUEST COMPLETE!", now).isEmpty())
    }
}
