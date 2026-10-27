package dev.skyglint.updates

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OldVersionCleanerTest {
    @TempDir lateinit var directory: Path
    private val mods get() = Files.createDirectories(directory.resolve("mods"))
    private val backup get() = directory.resolve("config/skyglint/backups/mods")
    private fun jar(name: String, version: String, id: String = "reborn", minecraft: String = "\"26.2\""): Path {
        val file = mods.resolve(name)
        ZipOutputStream(Files.newOutputStream(file)).use {
            it.putNextEntry(ZipEntry("fabric.mod.json"))
            it.write("""{"schemaVersion":1,"id":"$id","version":"$version","depends":{"minecraft":$minecraft}}""".toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry("payload.txt")); it.write("intact-$version".toByteArray()); it.closeEntry()
        }
        return file
    }
    @Test fun `archives only metadata verified strictly older compatible copies and keeps their bytes`() {
        val current = jar("skyglint-new.jar", "0.1.3")
        val old = jar("totally-renamed.jar", "0.1.2")
        val bytes = Files.readAllBytes(old)
        val higher = jar("future.jar", "0.2.0")
        val equal = jar("same-version.jar", "0.1.3")
        val other = jar("skyglint-fake-name.jar", "0.1.0", "another_mod")
        val differentGame = jar("skyglint-26.1.jar", "0.1.1", minecraft = "\"26.1\"")
        val result = OldVersionCleaner().clean(mods, backup, current, "0.1.3", "26.2")
        assertEquals(listOf("totally-renamed.jar"), result.archived.map { it.file })
        assertContentEquals(bytes, Files.readAllBytes(result.archived.single().destination))
        assertFalse(Files.exists(old))
        listOf(current, higher, equal, other, differentGame).forEach { assertTrue(Files.exists(it), it.toString()) }
        assertTrue(result.failures.isEmpty())
    }
    @Test fun `semantic ordering handles 10 and prereleases without lexical comparisons`() {
        val current = jar("current.jar", "0.1.10")
        jar("nine.jar", "0.1.9")
        jar("prerelease.jar", "0.1.10-rc.1")
        val build = jar("equal-build.jar", "0.1.10+different")
        val weird = jar("unknown.jar", "development-build")
        val result = OldVersionCleaner().clean(mods, backup, current, "0.1.10", "26.2")
        assertEquals(setOf("nine.jar", "prerelease.jar"), result.archived.map { it.file }.toSet())
        assertTrue(Files.exists(build)); assertTrue(Files.exists(weird))
    }
    @Test fun `public release archives every older installed release and prerelease in one pass`() {
        val current = jar("current-public-release.jar", "0.2.0")
        val oldVersions = listOf("0.1") + (1..12).map { "0.1.$it" } + "0.2.0-rc.1"
        val old = oldVersions.mapIndexed { index, version -> jar("renamed-download-$index.JAR", version) }
        val originalBytes = old.associate { it.fileName.toString() to Files.readAllBytes(it) }
        val preserved = listOf(
            current,
            jar("equal.jar", "0.2.0"),
            jar("equal-build.jar", "0.2.0+repacked"),
            jar("future.jar", "0.2.1"),
            jar("future-prerelease.jar", "0.3.0-rc.1"),
            jar("skyglint-looking-unrelated.jar", "0.1", id = "unrelated"),
            jar("other-minecraft-target.jar", "0.1.12", minecraft = "\"26.1\""),
        )
        val config = directory.resolve("config/skyglint/config.json")
        Files.createDirectories(config.parent)
        Files.writeString(config, """{"captureChat":true,"hudScale":1.0}""")
        val existingBackup = Files.createDirectories(backup).resolve("already-archived.jar.bak")
        Files.writeString(existingBackup, "preserve previous archive")
        val preservedBytes = (preserved + listOf(config, existingBackup)).associateWith { Files.readAllBytes(it) }

        val result = OldVersionCleaner().clean(mods, backup, current, "0.2.0", "26.2")

        assertNull(result.reason)
        assertTrue(result.failures.isEmpty(), result.failures.toString())
        assertEquals(oldVersions.toSet(), result.archived.map { it.version }.toSet())
        assertEquals(originalBytes.keys, result.archived.map { it.file }.toSet())
        result.archived.forEach {
            assertEquals(backup.toRealPath(), it.destination.parent.toRealPath())
            assertTrue(it.destination.fileName.toString().endsWith(".bak"))
            assertContentEquals(originalBytes.getValue(it.file), Files.readAllBytes(it.destination), it.file)
            assertFalse(Files.exists(mods.resolve(it.file)), it.file)
        }
        preservedBytes.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(path), path.toString()) }
        val repeat = OldVersionCleaner().clean(mods, backup, current, "0.2.0", "26.2")
        assertTrue(repeat.archived.isEmpty())
        assertTrue(repeat.failures.isEmpty())
    }
    @Test fun `all selected origins stay protected while every unselected older copy is archived`() {
        val current = jar("current.jar", "0.2.0")
        val selected = listOf(jar("loaded-first.jar", "0.1"), jar("loaded-second.jar", "0.1.12"))
        val unselected = listOf(jar("retired-first.jar", "0.1.1"), jar("retired-second.jar", "0.2.0-rc.1"))
        val nested = Files.createDirectories(mods.resolve("nested")).resolve("retired.jar")
        Files.copy(unselected.first(), nested)

        // The current origin must also be protected when omitted from the caller's selected list.
        val result = OldVersionCleaner().clean(mods, backup, current, "0.2.0", "26.2", selected)

        assertEquals(unselected.map { it.fileName.toString() }.toSet(), result.archived.map { it.file }.toSet())
        (selected + listOf(current, nested)).forEach { assertTrue(Files.exists(it), it.toString()) }
        assertTrue(result.failures.isEmpty())
    }
    @Test fun `protects loaded origins and nested folders even when metadata is older`() {
        val current = jar("current.jar", "0.1.3")
        val protected = jar("selected-other-origin.jar", "0.1.2")
        val nested = Files.createDirectories(mods.resolve("nested")).resolve("old.jar")
        Files.copy(protected, nested)
        val result = OldVersionCleaner().clean(mods, backup, current, "0.1.3", "26.2", listOf(current, protected))
        assertTrue(result.archived.isEmpty())
        assertTrue(Files.exists(protected)); assertTrue(Files.exists(nested))
    }
    @Test fun `refuses overrides dev paths and backups inside mods`() {
        val current = jar("current.jar", "0.1.3")
        val old = jar("old.jar", "0.1.2")
        assertEquals("loaded_version_overridden", OldVersionCleaner().clean(mods, backup, current, "99.0.0", "26.2").reason)
        assertEquals("not_a_loose_installed_jar", OldVersionCleaner().clean(mods, backup, directory, "0.1.3", "26.2").reason)
        assertEquals("backup_inside_mods", OldVersionCleaner().clean(mods, mods.resolve("backup"), current, "0.1.3", "26.2").reason)
        assertTrue(Files.exists(old))
    }
    @Test fun `corrupt and oversized metadata jars remain untouched and missing metadata cannot match filenames`() {
        val current = jar("current.jar", "0.1.3")
        val corrupt = Files.writeString(mods.resolve("skyglint-old.jar"), "not a zip")
        val oversized = jar("oversized.jar", "0.1.2" + " ".repeat(70_000))
        val noMetadata = mods.resolve("skyglint-gui-0.0.1.jar")
        ZipOutputStream(Files.newOutputStream(noMetadata)).use { it.putNextEntry(ZipEntry("empty")); it.closeEntry() }
        val result = OldVersionCleaner().clean(mods, backup, current, "0.1.3", "26.2")
        assertTrue(result.archived.isEmpty())
        listOf(corrupt, oversized, noMetadata).forEach { assertTrue(Files.exists(it)) }
    }
    @Test fun `failed backup creation retains source and repeat runs are idempotent`() {
        val current = jar("current.jar", "0.1.3")
        val old = jar("old.jar", "0.1.2", minecraft = "[\"26.1\",\"26.2\"]")
        val blocked = Files.writeString(directory.resolve("blocked"), "file")
        val failure = OldVersionCleaner().clean(mods, blocked.resolve("child"), current, "0.1.3", "26.2")
        assertEquals(1, failure.failures.size); assertTrue(Files.exists(old))
        assertEquals(1, OldVersionCleaner().clean(mods, backup, current, "0.1.3", "26.2").archived.size)
        assertTrue(OldVersionCleaner().clean(mods, backup, current, "0.1.3", "26.2").archived.isEmpty())
    }
}
