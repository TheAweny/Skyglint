package dev.skyglint.updates.github

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir

/** Explicit release validation against public GitHub; never touches a Minecraft profile. */
@EnabledIfEnvironmentVariable(named = "SKYGLINT_LIVE_UPDATE_TEST", matches = "1")
class GitHubUpdateLiveTest {
    @TempDir lateinit var directory: Path

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `public latest release downloads from a legacy installation with verified bytes and metadata`() {
        verifyUpgrade("0.1.12")
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `public latest patch downloads from the first public release without replacing its running jar`() {
        verifyUpgrade("0.2.0")
    }

    private fun verifyUpgrade(loadedVersion: String) {
        val mods = Files.createDirectories(directory.resolve("mods"))
        val staging = directory.resolve("config/skyglint/downloads")
        val legacy = mods.resolve("legacy-$loadedVersion.jar")
        // Synthetic installation metadata only: no historical mod code is executed.
        ZipOutputStream(Files.newOutputStream(legacy)).use { zip ->
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write("""{"schemaVersion":1,"id":"reborn","version":"$loadedVersion","depends":{"minecraft":"26.2","java":">=25","fabricloader":">=0.19.3"}}""".toByteArray())
            zip.closeEntry()
        }
        val original = Files.readAllBytes(legacy)
        val service = GitHubUpdateService(UpdateInstallation(mods, staging, legacy, loadedVersion, "0.19.3"))
        val release = assertIs<UpdateCheck.Available>(service.check()).release
        assertFalse(Files.exists(mods.resolve(release.assetName)), "Checking must not download the JAR")
        val installed = assertIs<UpdateDownload.Ready>(service.download(release))
        assertEquals(mods.resolve(release.assetName), installed.path)
        val bytes = Files.readAllBytes(installed.path)
        assertEquals(release.size, bytes.size.toLong())
        assertEquals(release.sha256, GitHubUpdateService.sha256(bytes))
        ZipFile(installed.path.toFile()).use { zip ->
            val metadata = zip.getInputStream(zip.getEntry("fabric.mod.json")).use {
                JsonParser.parseString(it.readAllBytes().toString(Charsets.UTF_8)).asJsonObject
            }
            assertEquals("reborn", metadata.get("id").asString)
            assertEquals("Skyglint", metadata.get("name").asString)
            assertEquals(release.version, metadata.get("version").asString)
            val dependencies = metadata.getAsJsonObject("depends")
            assertEquals("26.2", dependencies.get("minecraft").asString)
            assertEquals(">=25", dependencies.get("java").asString)
            assertEquals(">=${release.loaderVersion}", dependencies.get("fabricloader").asString)
            assertTrue(zip.getEntry("skyglint.mixins.json") != null)
        }
        assertContentEquals(original, Files.readAllBytes(legacy), "The running legacy JAR must stay intact")
        assertIs<UpdateDownload.AlreadyInstalled>(service.download(release))
        Files.list(staging).use { assertEquals(0L, it.count(), "No partial downloads remain") }
    }
}
