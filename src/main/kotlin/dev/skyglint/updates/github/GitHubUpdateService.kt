package dev.skyglint.updates.github

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.Version
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Public releases of one repository, checked on demand; downloads are a separate explicit action. */
class GitHubUpdateService(
    private val installation: UpdateInstallation,
    private val transport: UpdateTransport = GitHubHttpTransport(),
) {
    private var offered: UpdateRelease? = null

    @Synchronized fun check(): UpdateCheck = try {
        interrupted()
        installedRoot()
        val release = json(fetch(LATEST, MAX_RELEASE_BYTES))
        require(!release.boolean("draft") && !release.boolean("prerelease")) { "not_a_stable_release" }
        val tag = release.string("tag_name")
        val version = tag.removePrefix("v")
        val target = semantic(version)
        require(target.prereleaseKey.isEmpty) { "not_a_stable_release" }
        if ((target as Version).compareTo(semantic(installation.loadedVersion)) <= 0) {
            offered = null
            UpdateCheck.UpToDate
        } else {
            val assets = release.getAsJsonArray("assets") ?: error("missing_assets")
            require(assets.size() <= 100) { "too_many_assets" }
            val manifestAsset = assets.map { it.asJsonObject }.single { it.string("name") == MANIFEST }
            validateAsset(manifestAsset, tag, MANIFEST, MAX_MANIFEST_BYTES.toLong())
            val manifest = json(fetch(assetUri(tag, MANIFEST), MAX_MANIFEST_BYTES))
            require(manifest.integer("schema") == 1L) { "unsupported_manifest_schema" }
            val importance = if (!manifest.has("importance")) UpdateImportance.NORMAL else {
                val raw = manifest.string("importance")
                UpdateImportance.entries.singleOrNull { it.manifestValue == raw } ?: error("invalid_importance")
            }
            require(manifest.string("version") == version && manifest.string("minecraft") == MINECRAFT) { "manifest_target_mismatch" }
            require(manifest.integer("java") == 25L && installation.javaFeature >= 25) { "unsupported_java" }
            val loader = manifest.string("fabricLoader")
            require((semantic(installation.loaderVersion) as Version).compareTo(semantic(loader)) >= 0) { "fabric_loader_update_required" }
            val name = manifest.string("file")
            require(name == "skyglint-$version.jar") { "unexpected_jar_name" }
            val size = manifest.integer("size")
            require(size in 1..MAX_JAR_BYTES.toLong()) { "invalid_jar_size" }
            val hash = manifest.string("sha256")
            require(hash.matches(Regex("[0-9a-f]{64}"))) { "invalid_sha256" }
            val jarAsset = assets.map { it.asJsonObject }.single { it.string("name") == name }
            validateAsset(jarAsset, tag, name, MAX_JAR_BYTES.toLong())
            require(jarAsset.integer("size") == size) { "asset_size_mismatch" }
            jarAsset.get("digest")?.takeUnless { it.isJsonNull }?.let {
                require(it.asString == "sha256:$hash") { "asset_digest_mismatch" }
            }
            val offer = UpdateRelease(version, tag, name, hash, size, loader, importance)
            offered = offer
            UpdateCheck.Available(offer)
        }
    } catch (failure: Exception) {
        offered = null
        failureReason(failure).let {
            if (it.startsWith("installation_") || it == "fabric_loader_update_required" || it == "unsupported_java") UpdateCheck.Unavailable(it)
            else UpdateCheck.Failed(it)
        }
    }

    @Synchronized fun download(release: UpdateRelease): UpdateDownload {
        var temporary: Path? = null
        return try {
            interrupted()
            require(release == offered) { "release_was_not_verified" }
            val root = installedRoot()
            findInstalled(root, release)?.let { return it }
            require(Files.isWritable(root)) { "mods_directory_read_only" }
            val staging = installation.stagingDirectory.toAbsolutePath().normalize()
            require(!staging.startsWith(root) && noLinks(staging)) { "unsafe_staging_directory" }
            Files.createDirectories(staging)
            require(staging.toRealPath() == staging && !staging.toRealPath().startsWith(root)) { "unsafe_staging_directory" }
            require(Files.getFileStore(root) == Files.getFileStore(staging)) { "staging_requires_same_filesystem" }
            temporary = Files.createTempFile(staging, "skyglint-download-", ".part")
            val bytes = fetch(assetUri(release.tag, release.assetName), release.size.toInt())
            require(bytes.size.toLong() == release.size) { "download_size_mismatch" }
            require(sha256(bytes) == release.sha256) { "download_sha256_mismatch" }
            interrupted()
            Files.write(temporary, bytes)
            FileChannel.open(temporary, WRITE).use { it.force(true) }
            val metadata = jarMetadata(temporary) ?: error("invalid_mod_jar")
            require(metadata.version == release.version && metadata.minecraft == MINECRAFT && metadata.java == ">=25" &&
                metadata.loader == ">=${release.loaderVersion}") { "jar_target_mismatch" }
            interrupted()
            // Recheck after network I/O: another updater or the user may have installed a newer copy.
            require(installedRoot() == root) { "installation_changed" }
            findInstalled(root, release)?.let { return it }
            val destination = root.resolve(release.assetName)
            require(!Files.exists(destination, NOFOLLOW_LINKS)) { "destination_already_exists" }
            // createLink publishes the completely written file atomically with CREATE_NEW semantics.
            // Unlike ATOMIC_MOVE, it can never replace an existing destination (including a symlink).
            // Both paths are on the same filesystem; unlinking the staging name completes the move.
            Files.createLink(destination, temporary)
            UpdateDownload.Ready(release.version, destination)
        } catch (failure: Exception) {
            UpdateDownload.Failed(failureReason(failure))
        } finally {
            temporary?.let { try { Files.deleteIfExists(it) } catch (_: Exception) { /* inert .part outside mods */ } }
        }
    }

    private fun installedRoot(): Path {
        require(installation.production) { "installation_development_environment" }
        val mods = installation.modsDirectory.toAbsolutePath().normalize()
        val current = installation.currentJar.toAbsolutePath().normalize()
        require(noLinks(mods) && noLinks(current) && Files.isDirectory(mods, NOFOLLOW_LINKS)) { "installation_unsafe_mods_directory" }
        val root = mods.toRealPath()
        require(Files.isRegularFile(current, NOFOLLOW_LINKS) && current.parent == root && current.fileName.toString().endsWith(".jar")) {
            "installation_not_a_loose_jar"
        }
        val metadata = jarMetadata(current)
        require(metadata?.version == installation.loadedVersion && metadata.minecraft == MINECRAFT) { "installation_metadata_unverified" }
        semantic(installation.loadedVersion)
        return root
    }

    private fun findInstalled(root: Path, release: UpdateRelease): UpdateDownload.AlreadyInstalled? = Files.list(root).use { entries ->
        entries.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) && !Files.isSymbolicLink(it) && it.fileName.toString().endsWith(".jar") }
            .sorted().map { path -> path to jarMetadata(path) }.filter { (_, metadata) ->
                metadata != null && metadata.minecraft == MINECRAFT &&
                    (semantic(metadata.version) as Version).compareTo(semantic(release.version)) >= 0
            }.findFirst().orElse(null)?.let { (path, metadata) -> UpdateDownload.AlreadyInstalled(metadata!!.version, path) }
    }

    private data class JarMetadata(val version: String, val minecraft: String, val java: String?, val loader: String?)
    private fun jarMetadata(path: Path): JarMetadata? = try {
        require(Files.size(path) in 1..MAX_JAR_BYTES.toLong())
        ZipFile(path.toFile()).use { zip ->
            // Duplicate metadata is ambiguous to different ZIP readers and is never installed.
            val entries = zip.entries()
            var count = 0
            var metadataCount = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > 50_000) return null
                if (entry.name == "fabric.mod.json") metadataCount++
            }
            if (metadataCount != 1) return null
            val entry = zip.getEntry("fabric.mod.json") ?: return null
            if (entry.isDirectory || entry.size !in 1..MAX_MANIFEST_BYTES.toLong()) return null
            val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_MANIFEST_BYTES + 1) }
            if (bytes.size > MAX_MANIFEST_BYTES) return null
            val data = json(bytes)
            if (data.string("id") != "reborn" || data.integer("schemaVersion") != 1L) return null
            val version = data.string("version")
            semantic(version)
            val depends = data.getAsJsonObject("depends") ?: return null
            JarMetadata(version, depends.string("minecraft"), depends.get("java")?.asString, depends.get("fabricloader")?.asString)
        }
    } catch (_: Exception) { null }

    private fun fetch(initial: URI, maximumBytes: Int): ByteArray {
        var uri = initial
        repeat(6) { attempt ->
            interrupted()
            require(allowedNetworkUri(uri)) { "untrusted_update_host" }
            val response = transport.fetch(uri, maximumBytes)
            require(response.body.size <= maximumBytes) { "response_too_large" }
            if (response.status == 200) return response.body
            if (response.status in setOf(301, 302, 303, 307, 308)) {
                require(initial != LATEST && attempt < 5) { "unexpected_redirect" }
                val next = uri.resolve(response.location ?: error("redirect_without_location"))
                require(allowedNetworkUri(next) && next.host != "api.github.com") { "untrusted_update_redirect" }
                // A GitHub redirect may point only to this repository's exact original asset URL,
                // or GitHub's signed release-asset storage hosts, never another repository.
                require(next.host != "github.com" || next == initial) { "untrusted_repository_redirect" }
                uri = next
            } else error("http_${response.status}")
        }
        error("too_many_redirects")
    }

    private fun validateAsset(asset: JsonObject, tag: String, name: String, maximum: Long) {
        require(asset.string("browser_download_url") == assetUri(tag, name).toASCIIString()) { "untrusted_asset_url" }
        require(asset.string("state") == "uploaded" && asset.integer("size") in 1..maximum) { "invalid_release_asset" }
    }

    private fun json(bytes: ByteArray): JsonObject {
        // Bound nesting before handing malformed network data to a recursive JSON parser.
        var depth = 0
        var quoted = false
        var escaped = false
        for (byte in bytes) {
            val character = byte.toInt().toChar()
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> require(++depth <= 16) { "json_nesting_limit" }
                '}', ']' -> depth--
            }
        }
        return JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
    }
    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: error("missing_$key")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "invalid_$key" }
        return value.asString
    }
    private fun JsonObject.integer(key: String): Long {
        val value = get(key) ?: error("missing_$key")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.toString().matches(Regex("[0-9]{1,12}"))) { "invalid_$key" }
        return value.asLong
    }
    private fun JsonObject.boolean(key: String): Boolean {
        val value = get(key) ?: error("missing_$key")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "invalid_$key" }
        return value.asBoolean
    }
    private fun noLinks(path: Path): Boolean = generateSequence(path) { it.parent }.none { Files.isSymbolicLink(it) }
    private fun interrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedException("update_cancelled") }
    private fun failureReason(failure: Exception): String {
        if (failure is InterruptedException) { Thread.currentThread().interrupt(); return "update_cancelled" }
        return if (failure is IllegalArgumentException || failure is IllegalStateException)
            failure.message?.takeIf { it.matches(Regex("[a-z0-9_]+")) } ?: "invalid_release_metadata"
        else failure.javaClass.simpleName
    }

    companion object {
        const val MINECRAFT = "26.2"
        const val MANIFEST = "update-manifest.json"
        private const val MAX_RELEASE_BYTES = 1_048_576
        private const val MAX_MANIFEST_BYTES = 65_536
        private const val MAX_JAR_BYTES = 33_554_432
        val LATEST: URI = URI.create("https://api.github.com/repos/TheAweny/Skyglint/releases/latest")
        internal fun assetUri(tag: String, name: String): URI {
            require(tag.matches(Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+(?:\\+[0-9A-Za-z.-]+)?"))) { "invalid_release_tag" }
            require(name == MANIFEST || name == "skyglint-${tag.removePrefix("v")}.jar") { "invalid_asset_name" }
            return URI("https", "github.com", "/TheAweny/Skyglint/releases/download/$tag/$name", null)
        }
        internal fun allowedNetworkUri(uri: URI): Boolean = uri.scheme == "https" && uri.userInfo == null &&
            uri.port == -1 && uri.fragment == null && when (uri.host) {
                "api.github.com" -> uri == LATEST
                "github.com" -> uri.query == null && uri.path.startsWith("/TheAweny/Skyglint/releases/download/") &&
                    uri.rawPath == uri.path && !uri.path.contains("..")
                "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> true
                else -> false
            }
        internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun semantic(text: String): SemanticVersion {
            require(text.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?"))) { "invalid_semantic_version" }
            return SemanticVersion.parse(text).also { require(!it.hasWildcard()) { "invalid_semantic_version" } }
        }
    }
}
