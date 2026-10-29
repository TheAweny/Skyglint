package dev.skyglint.ui

import dev.skyglint.config.ConfigStore
import dev.skyglint.config.ModConfig
import dev.skyglint.ui.settings.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class WorkspaceCatalogTest {
    @TempDir lateinit var directory: Path

    private class Actions(initial: ModConfig = ModConfig(), private val store: ConfigStore? = null) : SettingsActions {
        override var config = initial
            private set
        private var previewEnabled = false
        override val preview get() = previewEnabled
        override fun changeConfig(transform: (ModConfig) -> ModConfig) {
            config = store?.update(transform) ?: transform(config).sanitized()
        }
        override fun setLogging(enabled: Boolean) = changeConfig { it.copy(loggingEnabled = enabled) }
        override fun setRawLogging(enabled: Boolean) = changeConfig { it.copy(captureRawText = enabled) }
        override fun setChatLogging(enabled: Boolean) = changeConfig { it.copy(captureChat = enabled) }
        override fun setDiagnostics(enabled: Boolean) = changeConfig {
            it.copy(loggingEnabled = enabled, captureRawText = enabled, captureChat = enabled)
        }
        override fun exportDiagnostics() {}
        override fun setPreview(enabled: Boolean) { previewEnabled = enabled }
        override fun selectPreview(id: String) { previewEnabled = true }
        override fun openUpdates() {}
    }

    @Test fun `catalog construction resolves reused pages and every page has a unique stable id`() {
        val actions = Actions()
        val catalog = WorkspaceCatalog(actions)
        val ids = catalog.pages.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(setOf("hud", "modules", "events", "appearance", "diagnostics",
            "activity", "skills", "pets", "mining", "garden", "dungeon", "fishing", "rewards")))
        catalog.pages.forEach { page ->
            assertTrue(page.title.isNotBlank(), page.id)
            assertTrue(page.description.isNotBlank(), page.id)
            val controls = page.controls(actions)
            assertEquals(controls.size, controls.map { it.id }.toSet().size, page.id)
            assertTrue(controls.all { it.id.isNotBlank() && it.caption.isNotBlank() && it.hint.isNotBlank() }, page.id)
        }
        assertEquals(listOf("skill_hold", "skill_level_ups", "sb_level_ups"),
            catalog.pages.single { it.id == "skills" }.controls(actions).map { it.id })
        assertEquals(listOf("show_pets", "pet_intro", "pet_level_ups"),
            catalog.pages.single { it.id == "pets" }.controls(actions).map { it.id })
    }

    @Test fun `search can deduplicate controls without changing their identity across pages`() {
        val actions = Actions()
        val catalog = WorkspaceCatalog(actions)
        val all = catalog.pages.flatMap { it.controls(actions) }
        all.groupBy { it.id }.forEach { (id, copies) ->
            copies.forEach { assertSame(copies.first(), it, id) }
        }
        val results = all.distinctBy { it.id }
            .filter { (it.caption + " " + it.hint).contains("level-up", ignoreCase = true) }
        assertTrue(results.map { it.id }.containsAll(listOf("skill_level_ups", "pet_level_ups", "sb_level_ups")))
        assertEquals(1, results.count { it.id == "pet_level_ups" })
        val petNotice = results.single { it.id == "pet_level_ups" }
        assertTrue(petNotice.state!!.invoke())
        petNotice.action()
        assertFalse(petNotice.state!!.invoke())
        val petPageControl = catalog.pages.single { it.id == "pets" }.controls(actions).single { it.id == petNotice.id }
        assertSame(petNotice, petPageControl)
        assertFalse(petPageControl.state!!.invoke())
    }

    @Test fun `new module toggles persist only the corresponding preference`() {
        val path = directory.resolve("config.json")
        val store = ConfigStore(path)
        store.load()
        val initial = store.update { it.copy(loggingEnabled = true, captureRawText = true, captureChat = true,
            hudScale = 1.5, hudOffsetX = .3, hudPositionY = .6, topOffset = 27,
            rewardOverrides = mapOf("sorrow" to "hide"), detailedDungeonHud = true) }
        val actions = Actions(initial, store)
        val controls = WorkspaceCatalog(actions).pages.flatMap { it.controls(actions) }.associateBy { it.id }
        val changes: List<Pair<String, (ModConfig) -> ModConfig>> = listOf(
            "welcome" to { c -> c.copy(showWelcome = false) },
            "event_reminders" to { c -> c.copy(showEventReminders = false) },
            "event_starts" to { c -> c.copy(showEventStarts = false) },
            "skill_level_ups" to { c -> c.copy(showSkillLevelUps = false) },
            "pet_level_ups" to { c -> c.copy(showPetLevelUps = false) },
            "sb_level_ups" to { c -> c.copy(showSkyBlockLevelUps = false) },
            "pet_intro" to { c -> c.copy(showPetIntroduction = false) },
            "mining_timers" to { c -> c.copy(showMiningTimers = false) },
            "fuel_warning" to { c -> c.copy(showFuelWarnings = false) },
            "visitors" to { c -> c.copy(showGardenVisitors = false) },
            "pests" to { c -> c.copy(showGardenPests = false) },
            "milestones" to { c -> c.copy(showCropMilestones = false) },
            "fishing_bait" to { c -> c.copy(showFishingBait = false) },
            "sack_warning" to { c -> c.copy(showSackWarning = false) },
            "cookie_warning" to { c -> c.copy(showCookieWarning = false) },
            "god_potion_warning" to { c -> c.copy(showGodPotionWarning = false) },
            "jerry_results" to { c -> c.copy(showJerryResults = false) },
        )
        var expected = initial
        changes.forEach { (id, change) ->
            val control = controls.getValue(id)
            assertTrue(control.persisted, id)
            assertTrue(control.state!!.invoke(), id)
            expected = change(expected)
            control.action()
            assertFalse(control.state!!.invoke(), id)
            assertEquals(expected, actions.config, id)
            assertEquals(expected, ConfigStore(path).load(), id)
        }
        assertEquals(initial.loggingEnabled, actions.config.loggingEnabled)
        assertEquals(initial.captureChat, actions.config.captureChat)
        assertEquals(initial.hudScale, actions.config.hudScale)
    }

    @Test fun `bounded helper and rotation actions wrap without changing other preferences`() {
        val initial = ModConfig(loggingEnabled = true, captureRawText = true, captureChat = true,
            hudScale = 1.75, helperLimit = 7, rotationSeconds = 15)
        val actions = Actions(initial)
        val controls = WorkspaceCatalog(actions).pages.single { it.id == "appearance" }.controls(actions).associateBy { it.id }
        controls.getValue("helper_limit").action()
        assertEquals(initial.copy(helperLimit = 1), actions.config)
        controls.getValue("rotation").action()
        assertEquals(initial.copy(helperLimit = 1, rotationSeconds = 4), actions.config)
        assertEquals("1", controls.getValue("helper_limit").value())
        assertEquals("4s", controls.getValue("rotation").value())
        controls.getValue("rotation").action()
        assertEquals(initial.copy(helperLimit = 1, rotationSeconds = 5), actions.config)
    }

    @Test fun `buff warning and winter results controls describe separate notices rather than persistent HUD`() {
        val actions = Actions()
        val catalog = WorkspaceCatalog(actions)
        val buffs = catalog.pages.single { it.id == "buffs" }.controls(actions).associateBy { it.id }
        assertEquals(setOf("cookie_warning", "god_potion_warning", "cookie_expiry", "god_potion_expiry"), buffs.keys)
        for (id in listOf("cookie_warning", "god_potion_warning")) {
            assertTrue(buffs.getValue(id).hint.contains("30, 10 and 1 minute"))
            assertTrue(buffs.getValue(id).state!!.invoke())
        }
        buffs.getValue("cookie_expiry").action()
        assertTrue(buffs.getValue("cookie_warning").state!!.invoke())
        buffs.getValue("god_potion_warning").action()
        assertTrue(buffs.getValue("god_potion_expiry").state!!.invoke())
        val winter = catalog.pages.single { it.id == "winter" }.controls(actions).associateBy { it.id }
        winter.getValue("jerry_defense").action()
        assertTrue(winter.getValue("jerry_results").state!!.invoke())
        winter.getValue("jerry_results").action()
        assertFalse(actions.config.showJerryResults)
        assertTrue(actions.config.showReindrake)
    }

    @Test fun `extension registry rejects builtin or duplicate ids and exposes an accepted page`() {
        WorkspaceCatalog.BUILTIN_IDS.forEach { id ->
            assertThrows(IllegalArgumentException::class.java) {
                SettingsPageRegistry.register(SettingsPage(id, "Invalid override", "Cannot replace builtins") { emptyList() })
            }
        }
        val id = "tests.workspace:${UUID.randomUUID()}"
        val page = SettingsPage(id, "Test extension", "Registered companion page") { emptyList() }
        SettingsPageRegistry.register(page)
        assertThrows(IllegalArgumentException::class.java) { SettingsPageRegistry.register(page.copy(title = "Duplicate")) }
        val actions = Actions()
        assertSame(page, WorkspaceCatalog(actions).pages.single { it.id == id })
        assertEquals(1, SettingsPageRegistry.pages().count { it.id == id })
    }
}
