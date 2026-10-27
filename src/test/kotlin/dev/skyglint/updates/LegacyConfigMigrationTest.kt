package dev.skyglint.updates

import dev.skyglint.config.ConfigStore
import dev.skyglint.config.ModConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class LegacyConfigMigrationTest {
    @TempDir lateinit var directory: Path
    private val legacy get() = directory.resolve("reborn/config.json")
    private val target get() = directory.resolve("skyglint/config.json")

    private fun oldConfig(text: String): ByteArray {
        Files.createDirectories(legacy.parent)
        return Files.writeString(legacy, text).let { Files.readAllBytes(it) }
    }

    @Test fun `copies all preferences once and retains legacy config logs and backups byte for byte`() {
        val preferences = ModConfig(enabled = false, loggingEnabled = true, captureRawText = true,
            captureChat = true, hudScale = 1.25, topOffset = 27, skillTimeoutSeconds = 45,
            showPets = false, rewardMode = "filtered", rewardOverrides = mapOf("sorrow" to "hide"),
            detailedDungeonHud = true, checkUpdates = false)
        ConfigStore(legacy).apply { load(); update { preferences } }
        val log = Files.writeString(legacy.parent.resolve("skyglint-events.jsonl"), "private diagnostics stay here")
        val backup = Files.createDirectories(legacy.parent.resolve("backups/mods")).resolve("old.jar.bak")
        Files.writeString(backup, "old archived mod stays here")
        val originals = listOf(legacy, log, backup).associateWith { Files.readAllBytes(it) }

        val result = LegacyConfigMigration.migrate(directory)

        assertTrue(result.migrated, result.toString())
        assertContentEquals(originals.getValue(legacy), Files.readAllBytes(target))
        assertEquals(preferences, ConfigStore(target).load())
        originals.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(path), path.toString()) }
        assertEquals("new_config_exists", LegacyConfigMigration.migrate(directory).reason)
        Files.list(target.parent).use { assertEquals(listOf("config.json"), it.map { path -> path.fileName.toString() }.toList()) }
    }

    @Test fun `existing new config is authoritative and is never overwritten`() {
        val oldBytes = oldConfig("""{"loggingEnabled":true}""")
        Files.createDirectories(target.parent)
        val newBytes = Files.writeString(target, """{"loggingEnabled":false}""").let { Files.readAllBytes(it) }

        assertEquals("new_config_exists", LegacyConfigMigration.migrate(directory).reason)
        assertContentEquals(oldBytes, Files.readAllBytes(legacy))
        assertContentEquals(newBytes, Files.readAllBytes(target))
    }

    @Test fun `oversized source is bounded and leaves original intact`() {
        val original = oldConfig("x".repeat(1_048_577))
        assertEquals("legacy_config_too_large", LegacyConfigMigration.migrate(directory).reason)
        assertContentEquals(original, Files.readAllBytes(legacy))
        assertFalse(Files.exists(target))
    }

    @Test fun `invalid JSON remains intact and new ConfigStore performs ordinary recovery`() {
        val original = oldConfig("{not valid JSON")
        assertTrue(LegacyConfigMigration.migrate(directory).migrated)
        val store = ConfigStore(target)
        assertEquals(ModConfig(), store.load())
        assertContentEquals(original, Files.readAllBytes(legacy))
        assertContentEquals(original, Files.readAllBytes(assertNotNull(store.lastRecoveryBackup)))
    }

    @Test fun `blocked destination never modifies source or removes the blocker`() {
        val original = oldConfig("{}")
        val blocker = Files.writeString(target.parent, "existing unrelated file")
        assertEquals("unsafe_target_directory", LegacyConfigMigration.migrate(directory).reason)
        assertContentEquals(original, Files.readAllBytes(legacy))
        assertEquals("existing unrelated file", Files.readString(blocker))
    }

    @Test fun `symlink sources and destination directories are not followed`() {
        oldConfig("{}")
        val outside = Files.createDirectories(directory.resolve("outside"))
        try { Files.createSymbolicLink(target.parent, outside) }
        catch (_: Exception) { assumeTrue(false, "Symlink creation unavailable on this filesystem") }
        assertEquals("unsafe_target_directory", LegacyConfigMigration.migrate(directory).reason)
        assertFalse(Files.exists(outside.resolve("config.json")))
        Files.delete(target.parent)
        Files.delete(legacy)
        val externalConfig = Files.writeString(outside.resolve("config.json"), "external content")
        Files.createSymbolicLink(legacy, externalConfig)
        assertEquals("legacy_config_missing_or_unsafe", LegacyConfigMigration.migrate(directory).reason)
        assertEquals("external content", Files.readString(externalConfig))
        assertFalse(Files.exists(target))
    }
}
