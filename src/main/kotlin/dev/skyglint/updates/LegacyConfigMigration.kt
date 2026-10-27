package dev.skyglint.updates

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes

/** Startup-only, bounded copy of the legacy preferences. Legacy logs, backups and config stay intact. */
object LegacyConfigMigration {
    data class Result(val migrated: Boolean = false, val reason: String, val error: String? = null)

    fun migrate(configDirectory: Path): Result {
        var temporary: Path? = null
        try {
            if (!Files.isDirectory(configDirectory)) return Result(reason = "legacy_config_missing")
            val root = configDirectory.toRealPath()
            val targetDirectory = root.resolve("skyglint")
            val target = targetDirectory.resolve("config.json")
            if (Files.exists(target, NOFOLLOW_LINKS)) return Result(reason = "new_config_exists")
            if (Files.exists(targetDirectory, NOFOLLOW_LINKS) &&
                (!Files.isDirectory(targetDirectory, NOFOLLOW_LINKS) || Files.isSymbolicLink(targetDirectory)))
                return Result(reason = "unsafe_target_directory")

            val legacyDirectory = root.resolve("reborn")
            if (!Files.isDirectory(legacyDirectory, NOFOLLOW_LINKS) || Files.isSymbolicLink(legacyDirectory))
                return Result(reason = "legacy_config_missing_or_unsafe")
            val source = legacyDirectory.resolve("config.json")
            if (!Files.isRegularFile(source, NOFOLLOW_LINKS) || Files.isSymbolicLink(source))
                return Result(reason = "legacy_config_missing_or_unsafe")
            val before = Files.readAttributes(source, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (before.size() > MAX_CONFIG_BYTES) return Result(reason = "legacy_config_too_large")
            val bytes = Files.newInputStream(source, NOFOLLOW_LINKS).use { it.readNBytes(MAX_CONFIG_BYTES + 1) }
            if (bytes.size > MAX_CONFIG_BYTES) return Result(reason = "legacy_config_too_large")
            val after = Files.readAttributes(source, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!after.isRegularFile || before.fileKey() != after.fileKey() || before.size() != after.size() ||
                before.lastModifiedTime() != after.lastModifiedTime()) return Result(reason = "legacy_config_changed")

            try { Files.createDirectory(targetDirectory) } catch (_: FileAlreadyExistsException) { /* Recheck below. */ }
            if (!Files.isDirectory(targetDirectory, NOFOLLOW_LINKS) || Files.isSymbolicLink(targetDirectory) ||
                targetDirectory.toRealPath().parent != root) return Result(reason = "unsafe_target_directory")
            temporary = Files.createTempFile(targetDirectory, ".skyglint-legacy-config-", ".tmp")
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            // Linking a complete same-filesystem temporary file publishes atomically and NEVER overwrites.
            // An unsupported filesystem leaves the original untouched instead of risking a partial config.
            try { Files.createLink(target, temporary) }
            catch (_: FileAlreadyExistsException) { return Result(reason = "new_config_exists") }
            return Result(migrated = true, reason = "legacy_config_copied")
        } catch (failure: Exception) {
            return Result(reason = "migration_failed", error = failure.javaClass.simpleName)
        } finally {
            temporary?.let { try { Files.deleteIfExists(it) } catch (_: Exception) { /* Never remove source or target. */ } }
        }
    }

    private const val MAX_CONFIG_BYTES = 1_048_576
}
