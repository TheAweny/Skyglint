package dev.skyglint.fabric

import dev.skyglint.updates.OldVersionCleaner
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import org.slf4j.LoggerFactory
import java.nio.file.Path

/** Fabric has selected and started the new version before this adapter touches old installation files. */
object ModUpdateCleanup {
    private val logger = LoggerFactory.getLogger("Skyglint/Updates")

    fun run() {
        try {
            val loader = FabricLoader.getInstance()
            if (loader.isDevelopmentEnvironment) return
            val own = loader.getModContainer("reborn").orElse(null) ?: return
            if (own.origin.kind != ModOrigin.Kind.PATH) return
            val ownPath = own.origin.paths.singleOrNull() ?: return
            val selectedPaths = loader.allMods.filter { it.origin.kind == ModOrigin.Kind.PATH }.flatMap { it.origin.paths }
            val mods = System.getProperty("fabric.modsFolder")?.let(Path::of) ?: loader.gameDir.resolve("mods")
            val result = OldVersionCleaner().clean(mods, loader.configDir.resolve("skyglint/backups/mods"),
                ownPath, own.metadata.version.friendlyString, loader.rawGameVersion, selectedPaths)
            result.archived.forEach {
                logger.info("Archived old Skyglint {} ({}) outside mods: {}", it.version, it.file, it.destination)
                SkyglintClient.diagnostics.record("update", "old_version_archived", mapOf("file" to it.file,
                    "version" to it.version, "backup" to it.destination.fileName.toString()), force = true)
            }
            result.failures.forEach {
                logger.warn("Could not archive old mod {}; file left in place ({})", it.file, it.error)
                SkyglintClient.diagnostics.record("update", "archive_failed", mapOf("file" to it.file, "error" to it.error), force = true)
            }
            result.reason?.let { logger.info("Automatic Skyglint cleanup skipped: {}", it) }
        } catch (failure: Exception) {
            // Installation maintenance must never prevent the game from starting.
            logger.warn("Automatic Skyglint cleanup could not finish; no files are deleted", failure)
        }
    }
}
