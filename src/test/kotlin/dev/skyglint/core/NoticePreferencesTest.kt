package dev.skyglint.core

import kotlin.test.*
import java.util.UUID

class NoticePreferencesTest {
    private fun core(preferences: NoticePreferences = NoticePreferences()) = CoreController().apply {
        noticePreferences = preferences
        enterSkyBlock("test", 0)
    }

    @Test fun `hidden confirmations still reconcile levels and never replay when reenabled`() {
        val core = core(NoticePreferences(showSkillLevelUps = false, showPetLevelUps = false, showSkyBlockLevelUps = false))
        core.apply(listOf(CoreEvent.PetObserved(PetSnapshot("Tiger", 35)),
            CoreEvent.SkillLevelUp(Skill.MINING, 20), CoreEvent.PetLevelUp("Tiger", 36),
            CoreEvent.SkyBlockLevelUp("SkyBlock level up!", 100)), 100)
        core.apply(listOf(CoreEvent.SkillObserved(SkillSnapshot(Skill.MINING, 19, null)),
            CoreEvent.SkillGain(Skill.MINING, 10.0, null, "fresh-gain")), 200)
        val snapshot = core.snapshot(200)
        assertEquals(20, (snapshot.progression as ProgressionView.SkillView).level)
        assertEquals(36, snapshot.pet!!.level)
        assertNull(snapshot.pet!!.progress)
        assertNull(snapshot.activeNotice)
        assertNull(snapshot.notice)
        core.noticePreferences = NoticePreferences()
        assertTrue(core.apply(listOf(CoreEvent.SkillLevelUp(Skill.MINING, 20), CoreEvent.PetLevelUp("Tiger", 36),
            CoreEvent.SkyBlockLevelUp("SkyBlock level up!", 100)), 3_000).isEmpty())
        assertNull(core.snapshot(3_001).activeNotice)
        assertNull(core.snapshot(3_001).notice)
    }

    @Test fun `hiding active SkyBlock celebration clears both queue and legacy fallback`() {
        val core = core()
        core.apply(listOf(CoreEvent.SkyBlockLevelUp("SkyBlock level up!", 100)), 100)
        assertNotNull(core.snapshot(101).activeNotice)
        assertNotNull(core.snapshot(101).notice)
        core.noticePreferences = NoticePreferences(showSkyBlockLevelUps = false)
        assertNull(core.snapshot(200).activeNotice)
        assertNull(core.snapshot(200).notice)
        core.noticePreferences = NoticePreferences()
        assertNull(core.snapshot(300).activeNotice)
        assertNull(core.snapshot(300).notice)
    }

    @Test fun `hidden welcome consumes settled presentation and preserves live state`() {
        val core = core(NoticePreferences(showWelcome = false))
        core.prepareWelcome(0)
        core.apply(listOf(CoreEvent.SkyBlockObserved(SkyBlockSnapshot(67, null)),
            CoreEvent.PetObserved(PetSnapshot("Tiger", 36)), CoreEvent.ContextObserved("The End")), 100)
        val id = UUID.randomUUID()
        core.updateBoss(id, "Unknown server boss", .5f, now = 200)
        assertNull(core.snapshot(5_000).activeNotice)
        assertTrue(core.welcomeWasOffered())
        core.noticePreferences = NoticePreferences()
        val shown = core.snapshot(5_001)
        assertNull(shown.activeNotice)
        assertEquals(id, shown.bosses.single().id)
        assertEquals("The End", shown.location)
        assertEquals(67, (shown.progression as ProgressionView.SkyBlock).level)
    }

    @Test fun `hidden event phases consume connection dedupe while schedule stays observable`() {
        val core = core(NoticePreferences(showEventReminders = false, showEventStarts = false))
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Fishing Festival", 120_000, false, "tab"))), 100)
        assertEquals("Fishing Festival", core.snapshot(101).scheduledEvents.single().name)
        core.noticePreferences = NoticePreferences()
        assertNull(core.snapshot(102).activeNotice)
        core.noticePreferences = NoticePreferences(showEventStarts = false)
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Fishing Festival", null, true, "tab", 60_000))), 200)
        assertTrue(core.snapshot(201).scheduledEvents.single().active)
        core.noticePreferences = NoticePreferences()
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Fishing Festival", null, true, "tab", 59_000))), 1_200)
        assertNull(core.snapshot(1_201).activeNotice)
    }

    @Test fun `filters classify targeted mining introductions and allow unknown or update notices`() {
        val hidden = NoticePreferences(false, false, false, false, false, false, false)
        for (id in listOf("skyglint.welcome:1", "skyglint.event_soon:x", "skyglint.event_started:x",
            "skyglint.event_active:x", "skyglint.garden.jacob.started.1", "skyglint.skill_level_up:x",
            "skyglint.pet_level_up:x", "skyglint.skyblock_level_up:x", "skyglint.mining.events:intro:x")) {
            assertFalse(hidden.allows(NoticeRequest(id, "Test")), id)
        }
        assertFalse(hidden.allows(NoticeRequest("companion:target", "Test",
            target = NoticeMetricTarget("skyglint.mining.events.current", "event:double_powder"))))
        val core = core(hidden)
        val update = CoreEvent.NoticeObserved(NoticeRequest("skyglint.update:0.3.0", "Important update", priority = 20))
        assertEquals(listOf(update), core.apply(listOf(update), 100))
        assertEquals(update.request.id, core.snapshot(101).activeNotice!!.id)
        assertTrue(hidden.allows(NoticeRequest("companion:unknown", "New notice")))
    }

    @Test fun `hidden active event stays deduped through island session transfer`() {
        val core = CoreController(eventNoticePolicy = EventNoticePolicy()).apply {
            noticePreferences = NoticePreferences(showEventStarts = false)
            enterSkyBlock("island-one", 0)
        }
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Fishing Festival", null, true, "tab", 60_000))), 100)
        assertNull(core.snapshot(101).activeNotice)
        core.enterSkyBlock("island-two", 1_000)
        core.noticePreferences = NoticePreferences()
        core.apply(listOf(CoreEvent.EventObserved(EventScheduleObservation("Fishing Festival", null, true, "tab", 59_000))), 1_100)
        assertNull(core.snapshot(1_101).activeNotice)
    }

    @Test fun `disabled mining intro never replaces or discards its widget or boss UUID`() {
        val core = core(NoticePreferences(showMiningTimers = false))
        val id = UUID.randomUUID()
        core.updateBoss(id, "DOUBLE POWDER", .5f, now = 50)
        val target = NoticeMetricTarget("skyglint.mining.events.current", "event:double_powder")
        val widget = ModuleWidget(target.widgetId, "Mining events", presentation = WidgetPresentation(WidgetRole.COUNTERS,
            metrics = listOf(WidgetMetric(target.metricId, "Double Powder", "X2", replacedBossIds = setOf(id)))))
        val request = NoticeRequest("skyglint.mining.events:intro:double_powder:1", "Double Powder", target = target)
        assertEquals(2, core.apply(listOf(CoreEvent.WidgetObserved(widget), CoreEvent.NoticeObserved(request)), 100).size)
        val snapshot = core.snapshot(101)
        assertNull(snapshot.activeNotice)
        assertEquals(id, snapshot.bosses.single().id)
        assertEquals(widget, snapshot.widgets.single())
        core.noticePreferences = NoticePreferences()
        assertNull(core.snapshot(102).activeNotice)
    }

    @Test fun `buff expiry filters are independent and never suppress updates or unknown buffs`() {
        val cookie = NoticeRequest("skyglint.buffs.expiry:cookie:1", "Cookie no longer active", priority = 55)
        val god = NoticeRequest("skyglint.buffs.expiry:god_potion:1", "God Potion no longer active", priority = 55)
        val cookieOff = NoticePreferences(showCookieExpiry = false)
        assertFalse(cookieOff.allows(cookie))
        assertTrue(cookieOff.allows(god))
        val godOff = NoticePreferences(showGodPotionExpiry = false)
        assertTrue(godOff.allows(cookie))
        assertFalse(godOff.allows(god))
        val bothOff = NoticePreferences(showCookieExpiry = false, showGodPotionExpiry = false)
        assertTrue(bothOff.allows(NoticeRequest("skyglint.update:1", "Important update")))
        assertTrue(bothOff.allows(NoticeRequest("companion:buff", "Custom buff")))
        assertTrue(bothOff.allows(NoticeRequest("skyglint.buffs.expiry:cookie_other:1", "Unknown type")))
        val core = core()
        core.apply(listOf(CoreEvent.NoticeObserved(cookie), CoreEvent.NoticeObserved(god)), 100)
        core.noticePreferences = cookieOff
        assertEquals(god.id, core.snapshot(200).activeNotice!!.id)
        core.noticePreferences = bothOff
        assertNull(core.snapshot(300).activeNotice)
        core.noticePreferences = NoticePreferences()
        assertNull(core.snapshot(400).activeNotice)
    }

    @Test fun `pre expiry warnings and winter results have independent filters and hidden notices never replay`() {
        val cookieWarning = NoticeRequest("skyglint.buffs.expiry:warning:cookie:1800000:1", "Cookie expires soon")
        val godWarning = NoticeRequest("skyglint.buffs.expiry:warning:god_potion:600000:1", "God Potion expires soon")
        val result = NoticeRequest("skyglint.winter.defense.result:1", "Defense complete")
        val cookieInactive = NoticeRequest("skyglint.buffs.expiry:cookie:1", "Cookie no longer active")
        val godInactive = NoticeRequest("skyglint.buffs.expiry:god_potion:1", "God Potion no longer active")
        val cookieOff = NoticePreferences(showCookieWarning = false)
        assertFalse(cookieOff.allows(cookieWarning))
        assertTrue(cookieOff.allows(godWarning) && cookieOff.allows(cookieInactive) && cookieOff.allows(result))
        val godOff = NoticePreferences(showGodPotionWarning = false)
        assertFalse(godOff.allows(godWarning))
        assertTrue(godOff.allows(cookieWarning) && godOff.allows(godInactive))
        val inactiveOff = NoticePreferences(showCookieExpiry = false, showGodPotionExpiry = false)
        assertTrue(inactiveOff.allows(cookieWarning) && inactiveOff.allows(godWarning))
        val hidden = NoticePreferences(showCookieWarning = false, showGodPotionWarning = false, showJerryResults = false)
        assertFalse(hidden.allows(result))
        assertTrue(hidden.allows(NoticeRequest("skyglint.update:1", "Important update")))
        assertTrue(hidden.allows(NoticeRequest("skyglint.buffs.expiry:warning:cookie_other:1", "Unknown buff")))
        val controller = core(hidden)
        controller.apply(listOf(cookieWarning, godWarning, result).map { CoreEvent.NoticeObserved(it) }, 100)
        assertNull(controller.snapshot(101).activeNotice)
        controller.noticePreferences = NoticePreferences()
        controller.apply(listOf(cookieWarning, godWarning, result).map { CoreEvent.NoticeObserved(it) }, 200)
        assertNull(controller.snapshot(201).activeNotice)
        controller.apply(listOf(CoreEvent.NoticeObserved(result.copy(id = "skyglint.winter.defense.result:2"))), 300)
        assertEquals("skyglint.winter.defense.result:2", controller.snapshot(301).activeNotice!!.id)
        controller.noticePreferences = hidden
        assertNull(controller.snapshot(302).activeNotice)
    }
}
