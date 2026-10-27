package dev.skyglint.updates

import com.google.gson.JsonParser
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipFile

/** One startup pass over loose JARs. Only an already loaded Skyglint can retire older compatible copies. */
class OldVersionCleaner {
    data class Archived(val file: String, val version: String, val destination: Path)
    data class Failure(val file: String, val error: String)
    data class Result(val archived: List<Archived> = emptyList(), val failures: List<Failure> = emptyList(),
                      val skipped: Int = 0, val reason: String? = null)
    private data class Metadata(val version: SemanticVersion, val minecraft: List<String>)

    fun clean(modsDirectory: Path, backupDirectory: Path, currentJar: Path, loadedVersion: String,
              minecraftVersion: String, loadedPaths: Collection<Path> = listOf(currentJar)): Result {
        val currentVersion = semantic(loadedVersion) ?: return Result(reason = "non_semantic_loaded_version")
        if (!Files.isDirectory(modsDirectory)) return Result(reason = "mods_directory_missing")
        val root = modsDirectory.toRealPath()
        // A source checkout, nested JAR, symlink or external classpath mod is not an installed updater.
        if (!Files.isRegularFile(currentJar, NOFOLLOW_LINKS) || Files.isSymbolicLink(currentJar))
            return Result(reason = "not_a_loose_installed_jar")
        val current = currentJar.toRealPath()
        if (current.parent != root) return Result(reason = "loaded_jar_outside_mods")
        val metadata = metadata(current) ?: return Result(reason = "loaded_metadata_unverified")
        if (metadata.version.friendlyString != loadedVersion) return Result(reason = "loaded_version_overridden")
        val gameVersion = Version.parse(minecraftVersion)
        if (!supports(metadata, gameVersion)) return Result(reason = "loaded_minecraft_unverified")
        val protectedPaths = (loadedPaths + listOf(current)).filter { Files.exists(it) }
        val plannedBackup = backupDirectory.toAbsolutePath().normalize()
        if (plannedBackup.startsWith(root)) return Result(reason = "backup_inside_mods")
        val archived = mutableListOf<Archived>()
        val failures = mutableListOf<Failure>()
        var skipped = 0
        val candidates = Files.list(root).use { entries -> entries.filter {
            it.fileName.toString().endsWith(".jar", ignoreCase = true)
        }.sorted().toList() }
        for (candidate in candidates) {
            if (!Files.isRegularFile(candidate, NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate)) { skipped++; continue }
            try {
                if (protectedPaths.any { Files.isSameFile(candidate, it) }) { skipped++; continue }
                val before = Files.readAttributes(candidate, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                val prior = metadata(candidate)
                if (prior == null || (prior.version as Version).compareTo(currentVersion) >= 0 || !supports(prior, gameVersion)) { skipped++; continue }
                Files.createDirectories(plannedBackup)
                val backup = plannedBackup.toRealPath()
                if (backup.startsWith(root)) return Result(archived, failures, skipped, "backup_inside_mods")
                val after = Files.readAttributes(candidate, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (!after.isRegularFile || before.fileKey() != after.fileKey() || before.size() != after.size() ||
                    before.lastModifiedTime() != after.lastModifiedTime()) { skipped++; continue }
                val destination = backup.resolve("${candidate.fileName}.${UUID.randomUUID()}.bak")
                // No overwrite, deletion, external process, or recursive traversal. Failed moves keep the original.
                Files.move(candidate, destination)
                archived += Archived(candidate.fileName.toString(), prior.version.friendlyString, destination)
            } catch (failure: Exception) {
                failures += Failure(candidate.fileName.toString(), failure.javaClass.simpleName)
            }
        }
        return Result(archived, failures, skipped)
    }

    private fun metadata(path: Path): Metadata? = try {
        ZipFile(path.toFile()).use { jar ->
            val entry = jar.getEntry("fabric.mod.json") ?: return null
            if (entry.isDirectory || entry.size > MAX_METADATA_BYTES) return null
            val bytes = jar.getInputStream(entry).use { it.readNBytes(MAX_METADATA_BYTES + 1) }
            if (bytes.size > MAX_METADATA_BYTES) return null
            val root = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
            if (root.get("id")?.asString != "reborn") return null
            val version = semantic(root.get("version")?.asString ?: return null) ?: return null
            val dependency = root.getAsJsonObject("depends")?.get("minecraft") ?: return null
            val ranges = if (dependency.isJsonArray) dependency.asJsonArray.map { it.asString }
                else listOf(dependency.asString)
            Metadata(version, ranges)
        }
    } catch (_: Exception) { null }

    private fun supports(metadata: Metadata, game: Version): Boolean = metadata.minecraft.any {
        try { VersionPredicate.parse(it).test(game) } catch (_: Exception) { false }
    }

    private fun semantic(text: String): SemanticVersion? = try {
        SemanticVersion.parse(text).takeUnless { it.hasWildcard() }
    } catch (_: Exception) { null }

    companion object { private const val MAX_METADATA_BYTES = 65_536 }
}
