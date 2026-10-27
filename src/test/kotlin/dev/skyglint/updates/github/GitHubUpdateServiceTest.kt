package dev.skyglint.updates.github

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitHubUpdateServiceTest {
    @TempDir lateinit var directory: Path

    private fun jar(version: String, minecraft: String = "26.2", id: String = "reborn"): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write("""{"schemaVersion":1,"id":"$id","version":"$version","depends":{"minecraft":"$minecraft","java":">=25","fabricloader":">=0.19.3"}}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("payload")); zip.write("unchanged-$version".toByteArray()); zip.closeEntry()
        }
    }.toByteArray()

    private inner class Fixture(
        val latest: String = "0.2.1",
        val payload: ByteArray = jar(latest),
        hash: String = GitHubUpdateService.sha256(payload),
        declaredSize: Long = payload.size.toLong(),
        target: String = "26.2",
        file: String = "skyglint-$latest.jar",
        repo: String = "TheAweny/Skyglint",
        val loadedVersion: String = "0.2.0",
    ) {
        val mods = Files.createDirectories(directory.resolve("mods"))
        val oldBytes = jar(loadedVersion)
        val current = Files.write(mods.resolve("current.jar"), oldBytes)
        val staging = directory.resolve("config/skyglint/downloads")
        val requests = mutableListOf<URI>()
        val manifest = """{"schema":1,"version":"$latest","minecraft":"$target","java":25,"fabricLoader":"0.19.3","file":"$file","sha256":"$hash","size":$declaredSize}"""
        val metadata = """{"tag_name":"v$latest","draft":false,"prerelease":false,"assets":[{"name":"update-manifest.json","state":"uploaded","size":${manifest.toByteArray().size},"browser_download_url":"https://github.com/$repo/releases/download/v$latest/update-manifest.json"},{"name":"skyglint-$latest.jar","state":"uploaded","size":$declaredSize,"browser_download_url":"https://github.com/$repo/releases/download/v$latest/skyglint-$latest.jar","digest":"sha256:$hash"}]}"""
        var interceptor: ((URI) -> UpdateHttpResponse?)? = null
        val transport = UpdateTransport { uri, _ ->
            requests += uri
            interceptor?.invoke(uri) ?: UpdateHttpResponse(200, when {
                uri == GitHubUpdateService.LATEST -> metadata.toByteArray()
                uri.path.endsWith("update-manifest.json") -> manifest.toByteArray()
                else -> payload
            })
        }
        val installation get() = UpdateInstallation(mods, staging, current, loadedVersion, "0.19.3")
        val service = GitHubUpdateService(installation, transport)
        fun offered(): UpdateRelease = assertIs<UpdateCheck.Available>(service.check()).release
        fun assertCurrentIntact() = assertContentEquals(oldBytes, Files.readAllBytes(current))
        fun assertNoParts() { if (Files.exists(staging)) Files.list(staging).use { assertEquals(0L, it.count()) } }
    }

    @Test fun `check downloads only metadata and click installs beside current with untouched configuration`() {
        val f = Fixture()
        val config = Files.createDirectories(directory.resolve("config/skyglint")).resolve("settings.json")
        Files.writeString(config, "user-preferences")
        val offer = f.offered()
        assertEquals(2, f.requests.size)
        assertFalse(Files.exists(f.mods.resolve(offer.assetName)))
        val result = assertIs<UpdateDownload.Ready>(f.service.download(offer))
        assertContentEquals(f.payload, Files.readAllBytes(result.path))
        f.assertCurrentIntact()
        assertEquals("user-preferences", Files.readString(config))
        f.assertNoParts()
        assertIs<UpdateDownload.AlreadyInstalled>(f.service.download(offer))
        assertEquals(3, f.requests.size)
    }

    @Test fun `semantic comparison does not offer older equal or equal build metadata releases`() {
        listOf("0.1.99", "0.2.0", "0.2.0+other").forEachIndexed { index, version ->
            val local = Files.createDirectories(directory.resolve("$index"))
            val current = Files.write(local.resolve("current.jar"), jar("0.2.0"))
            var calls = 0
            val service = GitHubUpdateService(UpdateInstallation(local, directory.resolve("stage$index"), current, "0.2.0", "0.19.3"),
                UpdateTransport { _, _ -> calls++; UpdateHttpResponse(200, """{"tag_name":"v$version","draft":false,"prerelease":false}""".toByteArray()) })
            assertEquals(UpdateCheck.UpToDate, service.check())
            assertEquals(1, calls)
        }
    }

    @Test fun `latest update is offered from every previously released version without stepping through patches`() {
        val oldVersions = listOf("0.1.0") + (1..12).map { "0.1.$it" } + "0.2.0-rc.1"
        oldVersions.forEach { version ->
            val f = Fixture(latest = "0.2.0", loadedVersion = version)
            val offer = f.offered()
            assertEquals("0.2.0", offer.version, "loaded $version")
            val result = assertIs<UpdateDownload.Ready>(f.service.download(offer), "loaded $version")
            assertEquals("skyglint-0.2.0.jar", result.path.fileName.toString())
            assertContentEquals(f.payload, Files.readAllBytes(result.path))
            f.assertCurrentIntact()
            f.assertNoParts()
            Files.delete(result.path)
        }
    }

    @Test fun `manifest wrong game invalid digest oversized size and path traversal never offer a download`() {
        // Independent temporary roots allow each adversarial release to use the same installed version.
        val variants: List<(Fixture) -> String> = listOf(
            { it.manifest.replace("\"minecraft\":\"26.2\"", "\"minecraft\":\"26.1\"") },
            { it.manifest.replace(Regex("\"sha256\":\"[0-9a-f]+\""), "\"sha256\":\"oops\"") },
            { it.manifest.replace(Regex("\"size\":[0-9]+"), "\"size\":999999999") },
            { it.manifest.replace("skyglint-0.2.1.jar", "../escape.jar") },
            { it.manifest.replace("\"schema\":1", "\"schema\":2") },
        )
        variants.forEach { mutate ->
            val f = Fixture()
            f.interceptor = { uri -> if (uri.path.endsWith("update-manifest.json")) UpdateHttpResponse(200, mutate(f).toByteArray()) else null }
            assertIs<UpdateCheck.Failed>(f.service.check())
            f.assertCurrentIntact()
            assertEquals(1L, Files.list(f.mods).use { it.count() })
        }
    }

    @Test fun `release URLs cannot select another repository or redirect to unrelated host`() {
        val f = Fixture(repo = "attacker/Skyglint")
        assertIs<UpdateCheck.Failed>(f.service.check())
        assertEquals(1, f.requests.size)
        val good = Fixture()
        good.interceptor = { uri -> if (uri.path.endsWith("update-manifest.json"))
            UpdateHttpResponse(302, byteArrayOf(), "https://attacker.example/update") else null }
        assertIs<UpdateCheck.Failed>(good.service.check())
        assertFalse(good.requests.any { it.host == "attacker.example" })
        good.interceptor = { uri -> if (uri.path.endsWith("update-manifest.json"))
            UpdateHttpResponse(302, byteArrayOf(), "https://github.com/attacker/other/releases/download/v1/file") else null }
        assertIs<UpdateCheck.Failed>(good.service.check())
        assertFalse(good.requests.any { it.path.contains("attacker") })
    }

    @Test fun `download rejects mismatched hash and incompatible jar without touching current`() {
        val f = Fixture()
        val offer = f.offered()
        f.interceptor = { uri -> if (uri.path.endsWith(".jar")) UpdateHttpResponse(200, f.payload.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }) else null }
        assertIs<UpdateDownload.Failed>(f.service.download(offer))
        f.assertCurrentIntact(); f.assertNoParts()
        val incompatible = Fixture(payload = jar("0.2.1", minecraft = "26.1"))
        assertIs<UpdateDownload.Failed>(incompatible.service.download(incompatible.offered()))
        incompatible.assertCurrentIntact(); incompatible.assertNoParts()
        val foreignId = Fixture(payload = jar("0.2.1", id = "skyglint_unrelated"))
        assertIs<UpdateDownload.Failed>(foreignId.service.download(foreignId.offered()))
        foreignId.assertCurrentIntact(); foreignId.assertNoParts()
        assertEquals(1L, Files.list(f.mods).use { it.count() })
    }

    @Test fun `newer copy arriving during download is preserved and avoids duplicate install`() {
        val f = Fixture()
        val offer = f.offered()
        f.interceptor = { uri ->
            if (uri.path.endsWith(".jar")) Files.write(f.mods.resolve("newer.jar"), jar("0.3.0"))
            null
        }
        val result = assertIs<UpdateDownload.AlreadyInstalled>(f.service.download(offer))
        assertEquals("0.3.0", result.version)
        assertFalse(Files.exists(f.mods.resolve(offer.assetName)))
        f.assertCurrentIntact(); f.assertNoParts()
    }

    @Test fun `already installed newer release suppresses download before any file or network work`() {
        val f = Fixture()
        val offer = f.offered()
        val newer = Files.write(f.mods.resolve("renamed-future.jar"), jar("0.4.0"))
        val result = assertIs<UpdateDownload.AlreadyInstalled>(f.service.download(offer))
        assertEquals(newer, result.path)
        assertEquals(2, f.requests.size)
        assertFalse(Files.exists(f.staging))
        f.assertCurrentIntact()
    }

    @Test fun `bounded responses truncated payload and corrupt ZIP never publish executable files`() {
        val f = Fixture()
        val offer = f.offered()
        f.interceptor = { uri -> if (uri.path.endsWith(".jar")) UpdateHttpResponse(200, f.payload.copyOf(f.payload.size - 1)) else null }
        assertIs<UpdateDownload.Failed>(f.service.download(offer))
        f.interceptor = { uri -> if (uri.path.endsWith(".jar")) UpdateHttpResponse(200, f.payload + byteArrayOf(0)) else null }
        assertIs<UpdateDownload.Failed>(f.service.download(offer))
        f.assertCurrentIntact(); f.assertNoParts()
        val corrupt = Fixture(payload = "not a ZIP even with a matching manifest hash".toByteArray())
        assertIs<UpdateDownload.Failed>(corrupt.service.download(corrupt.offered()))
        corrupt.assertCurrentIntact(); corrupt.assertNoParts()
        assertEquals(1L, Files.list(f.mods).use { it.count() })
    }

    @Test fun `loader upgrade requirements block incompatible releases before artifact download`() {
        val f = Fixture()
        f.interceptor = { uri -> if (uri.path.endsWith("update-manifest.json"))
            UpdateHttpResponse(200, f.manifest.replace("0.19.3", "0.20.0").toByteArray()) else null }
        assertEquals(UpdateCheck.Unavailable("fabric_loader_update_required"), f.service.check())
        assertEquals(2, f.requests.size)
        f.assertCurrentIntact()
    }

    @Test fun `existing destination including corrupt file is never overwritten`() {
        val f = Fixture()
        val offer = f.offered()
        val existing = Files.writeString(f.mods.resolve(offer.assetName), "keep-corrupt-for-user")
        assertIs<UpdateDownload.Failed>(f.service.download(offer))
        assertEquals("keep-corrupt-for-user", Files.readString(existing))
        f.assertCurrentIntact(); f.assertNoParts()
    }

    @Test fun `HTTP failure and cancellation retain current config and remove only private part`() {
        val f = Fixture()
        val offer = f.offered()
        f.interceptor = { uri -> if (uri.path.endsWith(".jar")) UpdateHttpResponse(503, byteArrayOf()) else null }
        assertIs<UpdateDownload.Failed>(f.service.download(offer))
        f.assertCurrentIntact(); f.assertNoParts()
        f.interceptor = { uri -> if (uri.path.endsWith(".jar")) throw InterruptedException() else null }
        val cancelled = assertIs<UpdateDownload.Failed>(f.service.download(offer))
        assertEquals("update_cancelled", cancelled.reason)
        assertTrue(Thread.interrupted()) // reset JUnit worker for subsequent tests
        f.assertCurrentIntact(); f.assertNoParts()
    }

    @Test fun `dev nested or incompatible installed origins never make network requests`() {
        val f = Fixture()
        listOf(f.installation.copy(production = false), f.installation.copy(currentJar = directory),
            f.installation.copy(loadedVersion = "99.0.0"), f.installation.copy(currentJar = Files.write(directory.resolve("external.jar"), jar("0.2.0"))))
            .forEach { installation -> assertIs<UpdateCheck.Unavailable>(GitHubUpdateService(installation, f.transport).check()) }
        assertTrue(f.requests.isEmpty())
    }

    @Test fun `unverified DTO and staging directory inside mods cannot write files`() {
        val f = Fixture()
        val offer = f.offered()
        assertIs<UpdateDownload.Failed>(f.service.download(offer.copy(assetName = "../evil.jar")))
        val unsafe = GitHubUpdateService(f.installation.copy(stagingDirectory = f.mods.resolve("stage")), f.transport)
        assertIs<UpdateDownload.Failed>(unsafe.download(assertIs<UpdateCheck.Available>(unsafe.check()).release))
        assertFalse(Files.exists(f.mods.resolve("stage")))
        f.assertCurrentIntact()
    }
}
