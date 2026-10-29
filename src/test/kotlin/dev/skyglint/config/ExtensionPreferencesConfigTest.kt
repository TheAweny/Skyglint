package dev.skyglint.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ExtensionPreferencesConfigTest {
    @TempDir lateinit var directory: Path

    @Test fun `all presentation choices and normalized layout survive restart independently`() {
        val path = directory.resolve("config.json")
        val store = ConfigStore(path)
        store.load()
        val saved = store.update { it.copy(
            hudOffsetX = -.65, hudPositionY = .72, topOffset = 28, hudScale = 1.25,
            showWelcome = false, showEventReminders = false, showEventStarts = false,
            showSkillLevelUps = false, showPetLevelUps = false, showSkyBlockLevelUps = false,
            showGardenVisitors = false, showGardenPests = false, showCropMilestones = false,
            showMiningTimers = false, showFuelWarnings = false, showPetIntroduction = false,
            showFishingBait = false, showSackWarning = false,
            showCookieExpiry = false, showGodPotionExpiry = false,
            showJerryDefense = false, showReindrake = false,
            showCookieWarning = false, showGodPotionWarning = false, showJerryResults = false,
            rotationSeconds = 11, helperLimit = 3, captureChat = true,
        ) }
        assertEquals(saved, ConfigStore(path).load())
        assertEquals(3, saved.schemaVersion)
        val reset = store.update { it.copy(hudOffsetX = 0.0, hudPositionY = null, hudScale = 1.0) }
        assertNull(ConfigStore(path).load().hudPositionY)
        assertEquals(28, reset.topOffset)
        assertFalse(reset.showWelcome)
        assertTrue(reset.captureChat)
    }

    @Test fun `buff expiry choices default on in old files and restore independently after restart`() {
        val path = directory.resolve("buffs.json")
        Files.writeString(path, """{"schemaVersion":3,"hudScale":1.25,"captureChat":true}""")
        val store = ConfigStore(path)
        val old = store.load()
        assertTrue(old.showCookieExpiry && old.showGodPotionExpiry)
        store.update { it.copy(showCookieExpiry = false) }
        val cookieOff = ConfigStore(path).load()
        assertFalse(cookieOff.showCookieExpiry)
        assertTrue(cookieOff.showGodPotionExpiry)
        store.update { it.copy(showCookieExpiry = true, showGodPotionExpiry = false) }
        val godOff = ConfigStore(path).load()
        assertTrue(godOff.showCookieExpiry)
        assertFalse(godOff.showGodPotionExpiry)
        assertEquals(old.copy(showGodPotionExpiry = false), godOff)
        store.update { it.copy(showGodPotionExpiry = true) }
        assertEquals(old, ConfigStore(path).load())
    }

    @Test fun `winter module visibility defaults on and persists independently without changing other preferences`() {
        val path = directory.resolve("winter.json")
        Files.writeString(path, """{"schemaVersion":3,"hudScale":1.5,"captureChat":true,"showCookieExpiry":false}""")
        val store = ConfigStore(path)
        val original = store.load()
        assertTrue(original.showJerryDefense && original.showReindrake)
        store.update { it.copy(showJerryDefense = false) }
        assertEquals(original.copy(showJerryDefense = false), ConfigStore(path).load())
        store.update { it.copy(showJerryDefense = true, showReindrake = false) }
        assertEquals(original.copy(showReindrake = false), ConfigStore(path).load())
        store.update { it.copy(showReindrake = true) }
        assertEquals(original, ConfigStore(path).load())
    }

    @Test fun `bounds and nonfinite positions normalize without changing agreed scale`() {
        val high = ModConfig(hudOffsetX = 5.0, hudPositionY = 2.0, rotationSeconds = 100, helperLimit = 100).sanitized()
        assertEquals(1.0, high.hudOffsetX)
        assertEquals(1.0, high.hudPositionY)
        assertEquals(15, high.rotationSeconds)
        assertEquals(7, high.helperLimit)
        val low = ModConfig(hudOffsetX = -5.0, hudPositionY = -2.0, rotationSeconds = -1, helperLimit = 0).sanitized()
        assertEquals(-1.0, low.hudOffsetX)
        assertEquals(0.0, low.hudPositionY)
        assertEquals(4, low.rotationSeconds)
        assertEquals(1, low.helperLimit)
        val unknown = ModConfig(hudOffsetX = Double.NaN, hudPositionY = Double.POSITIVE_INFINITY).sanitized()
        assertEquals(0.0, unknown.hudOffsetX)
        assertNull(unknown.hudPositionY)
        assertEquals(1.0, unknown.hudScale)
        assertEquals(1.5, unknown.renderScale)
    }

    @Test fun `warning and result preferences default on in legacy files and persist independently of inactive notices`() {
        val path = directory.resolve("warnings-results.json")
        val oldJson = """{"schemaVersion":3,"showCookieExpiry":false,"showGodPotionExpiry":false,"showJerryDefense":false,"captureChat":true,"hudScale":1.5}"""
        Files.writeString(path, oldJson)
        val store = ConfigStore(path)
        val original = store.load()
        assertTrue(original.showCookieWarning && original.showGodPotionWarning && original.showJerryResults)
        assertEquals(oldJson, Files.readString(path))
        store.update { it.copy(showCookieWarning = false) }
        assertEquals(original.copy(showCookieWarning = false), ConfigStore(path).load())
        store.update { it.copy(showCookieWarning = true, showGodPotionWarning = false) }
        assertEquals(original.copy(showGodPotionWarning = false), ConfigStore(path).load())
        store.update { it.copy(showGodPotionWarning = true, showJerryResults = false) }
        assertEquals(original.copy(showJerryResults = false), ConfigStore(path).load())
        store.update { it.copy(showJerryResults = true) }
        assertEquals(original, ConfigStore(path).load())
    }

    @Test fun `old schema three defaults keep legacy top offset and do not rewrite file`() {
        val path = directory.resolve("old.json")
        val original = """{"schemaVersion":3,"topOffset":37,"hudScale":1.25,"showPets":false}"""
        Files.writeString(path, original)
        val loaded = ConfigStore(path).load()
        assertEquals(ModConfig(topOffset = 37, hudScale = 1.25, showPets = false), loaded)
        assertEquals(original, Files.readString(path))
    }

    @Test fun `schema one and two migrations retain old scale and explicit reward overrides`() {
        for (schema in 1..2) {
            val path = directory.resolve("schema-$schema.json")
            Files.writeString(path, """{"schemaVersion":$schema,"hudScale":1.5,"topOffset":31,"rewardMode":"filtered","rewardOverrides":{"Sorrow":"hide"},"captureChat":true}""")
            val migrated = ConfigStore(path).load()
            assertEquals(if (schema == 1) 1.0 else 1.5, migrated.hudScale)
            assertEquals(31, migrated.topOffset)
            assertNull(migrated.hudPositionY)
            assertEquals(0.0, migrated.hudOffsetX)
            assertEquals("all", migrated.rewardMode)
            assertEquals(mapOf("sorrow" to "hide"), migrated.rewardOverrides)
            assertFalse(migrated.captureChat)
            assertTrue(migrated.showWelcome && migrated.showEventStarts && migrated.showMiningTimers)
            assertEquals(migrated, ConfigStore(path).load())
        }
    }

    @Test fun `explicit null and bounded coordinates decode while wrong type is preserved in recovery backup`() {
        val path = directory.resolve("nullable.json")
        Files.writeString(path, """{"schemaVersion":3,"hudOffsetX":-2,"hudPositionY":null,"rotationSeconds":0,"helperLimit":99}""")
        val loaded = ConfigStore(path).load()
        assertEquals(-1.0, loaded.hudOffsetX)
        assertNull(loaded.hudPositionY)
        assertEquals(4, loaded.rotationSeconds)
        assertEquals(7, loaded.helperLimit)
        val invalid = """{"schemaVersion":3,"hudPositionY":"bottom"}"""
        Files.writeString(path, invalid)
        val store = ConfigStore(path)
        assertEquals(ModConfig(), store.load())
        assertEquals(invalid, Files.readString(store.lastRecoveryBackup!!))
    }
}
