package dev.skyglint.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException

class ConfigStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `logging preference persists across a new store instance`() {
        val path = directory.resolve("nested/skyglint.json")
        ConfigStore(path).apply {
            load()
            update { it.copy(loggingEnabled = true, captureRawText = true) }
        }
        val reloaded = ConfigStore(path).load()
        assertTrue(reloaded.loggingEnabled)
        assertTrue(reloaded.captureRawText)
        assertEquals(10, reloaded.skillTimeoutSeconds)
    }

    @Test
    fun `new partial config keeps constructor defaults`() {
        val path = directory.resolve("skyglint.json")
        Files.writeString(path, "{\"loggingEnabled\":true}")
        val loaded = ConfigStore(path).load()
        assertTrue(loaded.enabled)
        assertTrue(loaded.loggingEnabled)
        assertTrue(loaded.showPets)
        assertEquals(1.0, loaded.hudScale)
    }

    @Test
    fun `corrupt config is backed up before defaults are written`() {
        val path = directory.resolve("skyglint.json")
        val original = "{not valid json"
        Files.writeString(path, original)
        val store = ConfigStore(path)
        assertEquals(ModConfig(), store.load())
        assertEquals(original, Files.readString(store.lastRecoveryBackup!!))
        assertEquals(ModConfig(), ConfigStore(path).load())
    }

    @Test
    fun `future schema is left byte for byte unchanged`() {
        val path = directory.resolve("skyglint.json")
        val original = "{\"schemaVersion\":42,\"futureFeature\":true}"
        Files.writeString(path, original)
        val store = ConfigStore(path)
        assertThrows(UnsupportedConfigVersionException::class.java) { store.load() }
        assertThrows(UnsupportedConfigVersionException::class.java) { store.update { it.copy(loggingEnabled = true) } }
        assertThrows(UnsupportedConfigVersionException::class.java) { ConfigStore(path).update { it.copy(loggingEnabled = true) } }
        assertEquals(original, Files.readString(path))
    }

    @Test
    fun `invalid values are bounded before persistence`() {
        val path = directory.resolve("skyglint.json")
        val store = ConfigStore(path)
        store.load()
        val sanitized = store.update { it.copy(hudScale = Double.NaN, skillTimeoutSeconds = -5, topOffset = 10000) }
        assertEquals(1.0, sanitized.hudScale)
        assertEquals(10, sanitized.skillTimeoutSeconds)
        assertEquals(200, sanitized.topOffset)
        assertEquals(sanitized, ConfigStore(path).load())
    }

    @Test
    fun `failed write does not publish unsaved preferences`() {
        val path = directory.resolve("skyglint.json")
        val store = ConfigStore(path)
        store.load()
        Files.delete(path)
        Files.createDirectory(path)
        assertThrows(IOException::class.java) { store.update { it.copy(loggingEnabled = true) } }
        assertFalse(store.current.loggingEnabled)
    }

    @Test
    fun `legacy scale migrates once preserving rendered size and logging preferences`() {
        for (oldScale in listOf(0.5, 1.0, 1.5, 2.0)) {
            val path = directory.resolve("legacy-$oldScale.json")
            Files.writeString(path, """{"schemaVersion":1,"hudScale":$oldScale,"loggingEnabled":true,"captureRawText":true}""")
            val migrated = ConfigStore(path).load()
            assertEquals(ModConfig.CURRENT_SCHEMA, migrated.schemaVersion)
            assertEquals(oldScale, migrated.renderScale, 1e-12)
            assertTrue(migrated.loggingEnabled)
            assertTrue(migrated.captureRawText)
            assertEquals(migrated, ConfigStore(path).load())
            if (oldScale == 1.5) assertEquals(1.0, migrated.hudScale)
        }
    }

    @Test
    fun `drop mode and item choices persist with canonical names and reset restores defaults`() {
        val path = directory.resolve("skyglint.json")
        val store = ConfigStore(path)
        store.load()
        store.update { it.copy(rewardMode = "ALL", rewardOverrides = mapOf(
            "§a Enchanted  Ender Pearl " to "SHOW", "Sorrow" to "HIDE",
        )) }
        val loaded = ConfigStore(path).load()
        assertEquals("all", loaded.rewardMode)
        assertEquals(mapOf("enchanted ender pearl" to "show", "sorrow" to "hide"), loaded.rewardOverrides)
        store.update { it.copy(rewardMode = "filtered", rewardOverrides = it.rewardOverrides - "sorrow") }
        val reset = ConfigStore(path).load()
        assertEquals("filtered", reset.rewardMode)
        assertFalse("sorrow" in reset.rewardOverrides)
        assertTrue(reset.loggingEnabled == loaded.loggingEnabled)
    }

    @Test
    fun `drop rule sanitization bounds names entries and actions and owns immutable data`() {
        val rules = (0..200).associate { "Item $it" to "show" } + mapOf(
            " " to "show", "a".repeat(121) to "show", "bad\nname" to "hide", "Unknown" to "guess",
        )
        val sanitized = ModConfig(rewardMode = "invalid", rewardOverrides = rules).sanitized()
        assertEquals("all", sanitized.rewardMode)
        assertEquals(128, sanitized.rewardOverrides.size)
        assertFalse(sanitized.rewardOverrides.keys.any { it.isBlank() || it.length > 120 })
        assertFalse("unknown" in sanitized.rewardOverrides)
        assertThrows(UnsupportedOperationException::class.java) {
            (sanitized.rewardOverrides as MutableMap<String, String>)["other"] = "show"
        }
    }

    @Test
    fun `schema two restores all rewards once while keeping item rules and existing preferences`() {
        val path = directory.resolve("schema-two.json")
        Files.writeString(path, """{"schemaVersion":2,"rewardMode":"filtered","rewardOverrides":{"Sorrow":"hide"},"hudScale":1.25,"loggingEnabled":true,"captureRawText":true,"showPets":false}""")
        val migrated = ConfigStore(path).load()
        assertEquals(3, migrated.schemaVersion)
        assertEquals("all", migrated.rewardMode)
        assertEquals(mapOf("sorrow" to "hide"), migrated.rewardOverrides)
        assertEquals(1.25, migrated.hudScale)
        assertTrue(migrated.loggingEnabled)
        assertTrue(migrated.captureRawText)
        assertFalse(migrated.captureChat)
        assertFalse(migrated.showPets)
        val saved = Files.readString(path)
        assertEquals(migrated, ConfigStore(path).load())
        assertEquals(saved, Files.readString(path))
    }

    @Test
    fun `schema three explicit filtered choice survives reload without migration`() {
        val path = directory.resolve("schema-three.json")
        val original = """{"schemaVersion":3,"rewardMode":"filtered","captureChat":false}"""
        Files.writeString(path, original)
        assertEquals("filtered", ConfigStore(path).load().rewardMode)
        assertEquals("filtered", ConfigStore(path).load().rewardMode)
        assertEquals(original, Files.readString(path))
    }

    @Test
    fun `full chat capture persists independently of relevant raw signals`() {
        val path = directory.resolve("chat-capture.json")
        val store = ConfigStore(path)
        assertEquals("all", store.load().rewardMode)
        assertFalse(store.current.captureChat)
        store.update { it.copy(loggingEnabled = true, captureChat = true, captureRawText = false) }
        val chatOnly = ConfigStore(path).load()
        assertTrue(chatOnly.loggingEnabled)
        assertTrue(chatOnly.captureChat)
        assertFalse(chatOnly.captureRawText)
        store.update { it.copy(captureChat = false, captureRawText = true) }
        val relevantOnly = ConfigStore(path).load()
        assertFalse(relevantOnly.captureChat)
        assertTrue(relevantOnly.captureRawText)
    }

    @Test
    fun `update checks default on for old schema three files and explicit opt out persists`() {
        val path = directory.resolve("updates.json")
        Files.writeString(path, """{"schemaVersion":3,"hudScale":1.25,"captureChat":true}""")
        val store = ConfigStore(path)
        assertTrue(store.load().checkUpdates)
        store.update { it.copy(checkUpdates = false) }
        val loaded = ConfigStore(path).load()
        assertFalse(loaded.checkUpdates)
        assertEquals(3, loaded.schemaVersion)
        assertEquals(1.25, loaded.hudScale)
        assertTrue(loaded.captureChat)
        assertEquals(loaded, ConfigStore(path).load())
    }
}
