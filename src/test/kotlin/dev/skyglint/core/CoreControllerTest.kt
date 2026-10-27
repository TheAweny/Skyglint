package dev.skyglint.core

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreControllerTest {
    private fun active(): CoreController = CoreController().apply { enterSkyBlock("profile-a:world-1", 0) }

    @Test fun `all observations are gated behind explicit SkyBlock context`() {
        val controller = CoreController()
        assertTrue(controller.onActionBar("+1 Mining (50%)", 1).isEmpty())
        assertTrue(controller.onChat("SKYBLOCK LEVEL UP Level 10", 2).isEmpty())
        controller.updateBoss(UUID.randomUUID(), "Any boss", .5f, now = 3)
        assertFalse(controller.snapshot(3).active)
        assertTrue(controller.snapshot(3).bosses.isEmpty())
    }

    @Test fun `unknown SB progress is distinct from zero`() {
        val controller = active()
        assertNull(controller.snapshot(0).progression)
        controller.apply(listOf(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(100, null))), 1)
        assertNull(assertIs<ProgressionView.SkyBlock>(controller.snapshot(1).progression).progress)
        controller.onTabLines(listOf("SB Level: [100] 0 XP"), 2)
        assertEquals(0.0, assertIs<ProgressionView.SkyBlock>(controller.snapshot(2).progression).progress!!.current)
    }

    @Test fun `skill gains take the progress slot then expire to SB XP`() {
        val controller = active()
        controller.onTabLines(listOf("SB Level: [287] 24 XP"), 10)
        controller.onActionBar("+1.1 Mining (48.39%)", 100)
        assertEquals(Skill.MINING, assertIs<ProgressionView.SkillView>(controller.snapshot(101).progression).skill)
        assertIs<ProgressionView.SkillView>(controller.snapshot(10_099).progression)
        val sb = assertIs<ProgressionView.SkyBlock>(controller.snapshot(10_100).progression)
        assertEquals(24.0, sb.progress!!.current)
        assertEquals(287, sb.level)
    }

    @Test fun `repeated actionbar segment does not refresh skill activity`() {
        val controller = active()
        assertEquals(1, controller.onActionBar("100/100 HP  +1 Mining (50%)  10 Mana", 100).size)
        assertTrue(controller.onActionBar("99/100 HP  +1 Mining (50%)  11 Mana", 9000).isEmpty())
        assertNull(controller.snapshot(10_100).progression)
        assertEquals(1, controller.onActionBar("99/100 HP  +1 Mining (50.01%)  11 Mana", 10_101).size)
        assertIs<ProgressionView.SkillView>(controller.snapshot(10_101).progression)
    }

    @Test fun `tab snapshots and SB gains never activate skill`() {
        val controller = active()
        controller.onTabLines(listOf("Mining 14: 22,922/75k", "SB Level: [287] 24 XP"), 1)
        assertIs<ProgressionView.SkyBlock>(controller.snapshot(1).progression)
        controller.onActionBar("+10 SkyBlock XP (Bestiary) (34/100)", 2)
        assertEquals(34.0, assertIs<ProgressionView.SkyBlock>(controller.snapshot(2).progression).progress!!.current)
        assertNull(controller.snapshot(2).notice)
    }

    @Test fun `each skill retains its progress without overwriting SB`() {
        val controller = active()
        controller.onTabLines(listOf("SB Level: [3] 12 XP", "Farming 35: 12.4%"), 1)
        controller.onActionBar("+1 Mining (50%)", 2)
        controller.onActionBar("+2 Farming (12.5%)", 3)
        val farming = assertIs<ProgressionView.SkillView>(controller.snapshot(3).progression)
        assertEquals(Skill.FARMING, farming.skill)
        assertEquals(35, farming.level)
        assertEquals(.125, farming.progress!!.fraction)
        assertEquals(12.0, assertIs<ProgressionView.SkyBlock>(controller.snapshot(10_003).progression).progress!!.current)
    }

    @Test fun `snapshot level changes do not celebrate and cannot suppress real header`() {
        val controller = active()
        controller.onTabLines(listOf("SB Level: [288] 0 XP"), 1)
        assertNull(controller.snapshot(1).notice)
        assertEquals(1, controller.onChat("SKYBLOCK LEVEL UP Level 288", 2).size)
        assertNotNull(controller.snapshot(2).notice)
        assertTrue(controller.onChat("SKYBLOCK LEVEL UP Level 288", 3).isEmpty())
        assertNull(controller.snapshot(4002).notice)
    }

    @Test fun `new session clears all caches and dedupe state`() {
        val controller = active()
        controller.onActionBar("+1 Mining (50%)", 1)
        controller.onTabLines(listOf("Pet:", "[Lvl 100] Flying Fish", "MAX LEVEL"), 2)
        controller.onChat("SKYBLOCK LEVEL UP Level 288", 3)
        controller.updateBoss(UUID.randomUUID(), "Unknown server event", .7f, now = 4)
        controller.enterSkyBlock("profile-b:world-2", 5)
        val next = controller.snapshot(5)
        assertTrue(next.bosses.isEmpty())
        assertNull(next.pet)
        assertNull(next.notice)
        assertNull(next.progression)
        assertEquals(1, controller.onActionBar("+1 Mining (50%)", 6).size)
    }

    @Test fun `same session entry is idempotent and leaving makes all HUD absent`() {
        val controller = active()
        controller.onActionBar("+1 Mining (50%)", 1)
        controller.enterSkyBlock("profile-a:world-1", 2)
        assertIs<ProgressionView.SkillView>(controller.snapshot(2).progression)
        controller.leaveSkyBlock()
        assertFalse(controller.snapshot(3).active)
        assertNull(controller.snapshot(3).progression)
    }

    @Test fun `late observation cannot rewind state`() {
        val controller = active()
        controller.onActionBar("+1 Mining (50%)", 100)
        assertTrue(controller.onActionBar("+1 Farming (20%)", 99).isEmpty())
        assertEquals(Skill.MINING, assertIs<ProgressionView.SkillView>(controller.snapshot(100).progression).skill)
    }

    @Test fun `boss updates track UUID preserve unknown text and remove only target`() {
        val controller = active()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        controller.updateBoss(first, "New unrecognized event", .8f, "RED", now = 1)
        controller.updateBoss(second, "Other boss", .3f, now = 2)
        controller.updateBoss(first, "Updated event", .7f, "BLUE", now = 3)
        val snapshot = controller.snapshot(3)
        assertEquals(2, snapshot.bosses.size)
        assertEquals("Updated event", snapshot.bosses.first().title)
        assertEquals(.7f, snapshot.bosses.first().progress)
        controller.removeBoss(first)
        assertEquals(second, controller.snapshot(4).bosses.single().id)
    }

    @Test fun `pet is authoritative and no skill gain invents its progress`() {
        val controller = active()
        controller.onTabLines(listOf("Pet:", "[Lvl 51] Kuudra"), 1)
        controller.onActionBar("+1 Combat (12%)", 2)
        assertEquals("Kuudra", controller.snapshot(2).pet!!.name)
        assertNull(controller.snapshot(2).pet!!.progress)
        controller.onTabLines(listOf("Pet:", "No pet selected"), 3)
        assertNull(controller.snapshot(3).pet)
    }

    @Test fun `configured skill activity can last two minutes`() {
        val controller = active()
        controller.skillActiveMillis = 120_000
        controller.onActionBar("+1 Mining (50%)", 1)
        assertIs<ProgressionView.SkillView>(controller.snapshot(119_999).progression)
        assertNull(controller.snapshot(120_001).progression)
    }

    @Test fun `neutral actionbar separates identical genuine skill bursts`() {
        val controller = active()
        assertEquals(1, controller.onActionBar("+1 Mining (50%)", 1).size)
        assertTrue(controller.onActionBar("+1 Mining (50%)", 9_000).isEmpty())
        controller.onActionBar("100/100❤   200/200✎ Mana", 10_001)
        assertEquals(1, controller.onActionBar("+1 Mining (50%)", 10_002).size)
        assertIs<ProgressionView.SkillView>(controller.snapshot(10_003).progression)
    }

    @Test fun `invalid XP frame does not manufacture a new skill burst`() {
        val controller = active()
        controller.onActionBar("+1 Mining (50%)", 1)
        controller.onActionBar("+1 Mining (invalid%)", 9_000)
        assertTrue(controller.onActionBar("+1 Mining (50%)", 10_001).isEmpty())
        assertNull(controller.snapshot(10_001).progression)
    }

    @Test fun `neutral actionbar also separates identical SkyBlock XP observations`() {
        val controller = active()
        val xp = "+10 SkyBlock XP (Bestiary) (25/100)"
        assertEquals(1, controller.onActionBar(xp, 1).size)
        assertTrue(controller.onActionBar(xp, 2).isEmpty())
        controller.onActionBar("100/100❤", 3)
        assertEquals(1, controller.onActionBar(xp, 4).size)
        assertEquals(25.0, assertIs<ProgressionView.SkyBlock>(controller.snapshot(4).progression).progress!!.current)
    }

    @Test fun `pet absence without a visible widget is not evidence of unequipping`() {
        val controller = active()
        controller.onTabLines(listOf("Pet:", "[Lvl 51] Kuudra"), 1)
        controller.onTabLines(listOf("Area: Private Island"), 2)
        assertEquals("Kuudra", controller.snapshot(2).pet!!.name)
        controller.onTabLines(listOf("Pet:", "No pet selected"), 3)
        assertNull(controller.snapshot(3).pet)
    }

    @Test fun `celebration overrides only presentation while current boss activity and skill keep updating`() {
        val controller = active()
        val id = UUID.randomUUID()
        controller.updateBoss(id, "Before", .9f, now = 1)
        controller.onActionBar("+1 Mining (50%)", 2)
        controller.onChat("SKYBLOCK LEVEL UP Level 288", 3)
        controller.updateBoss(id, "Latest boss", .4f, now = 4)
        controller.apply(listOf(CoreEvent.WidgetObserved(ModuleWidget("activity", "Powder"))), 5)
        controller.onActionBar("+2 Farming (12%)", 6)
        val during = controller.snapshot(7)
        assertEquals(100, during.activeNotice!!.priority)
        assertEquals("Latest boss", during.bosses.single().title)
        assertEquals(Skill.FARMING, assertIs<ProgressionView.SkillView>(during.progression).skill)
        val after = controller.snapshot(4_003)
        assertNull(after.activeNotice)
        assertEquals("Latest boss", after.bosses.single().title)
        assertEquals("Powder", after.widgets.single().title)
        assertEquals(Skill.FARMING, assertIs<ProgressionView.SkillView>(after.progression).skill)
    }

    @Test fun `rare waits under confirmed level up and session change removes queue`() {
        val controller = active()
        controller.onTabLines(listOf("SB Level: [288] 0 XP"), 1)
        assertNull(controller.snapshot(1).activeNotice)
        controller.onChat("SKYBLOCK LEVEL UP Level 288", 2)
        controller.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("rare", "Rare drop!", "Sorrow"))), 3)
        assertEquals(100, controller.snapshot(4).activeNotice!!.priority)
        assertEquals("rare", controller.snapshot(4_002).activeNotice!!.id)
        controller.enterSkyBlock("new-profile", 4_003)
        assertNull(controller.snapshot(4_003).activeNotice)
    }

    @Test fun `unknown level suffix is briefly deduped without blocking future confirmed headers forever`() {
        val controller = active()
        assertEquals(1, controller.onChat("SKYBLOCK LEVEL UP!", 1).size)
        assertTrue(controller.onChat("SKYBLOCK LEVEL UP!", 2).isEmpty())
        assertEquals(1, controller.onChat("SKYBLOCK LEVEL UP!", 5_001).size)
        assertNotNull(controller.snapshot(5_002).activeNotice)
    }

    @Test fun `generic contributor urgency cannot displace a confirmed SkyBlock celebration`() {
        val controller = active()
        controller.onChat("SKYBLOCK LEVEL UP Level 288", 1)
        controller.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest("custom", "Custom", priority = Int.MAX_VALUE))), 2)
        assertEquals(100, controller.snapshot(3).activeNotice!!.priority)
        assertEquals("custom", controller.snapshot(4_001).activeNotice!!.id)
        assertEquals(99, controller.snapshot(4_001).activeNotice!!.priority)
    }

    @Test fun `generic notification cannot reserve the dedupe key of a later confirmed level up`() {
        val controller = active()
        assertTrue(controller.apply(listOf(CoreEvent.NoticeObserved(NoticeRequest(
            "skyglint.skyblock_level_up:288", "Generic widget with colliding ID",
        ))), 1).isEmpty())
        assertEquals(1, controller.onChat("SKYBLOCK LEVEL UP Level 288", 2).size)
        assertEquals("SkyBlock level up!", controller.snapshot(3).activeNotice!!.title)
        assertEquals(100, controller.snapshot(3).activeNotice!!.priority)
    }
}
