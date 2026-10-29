package dev.skyglint.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

/** Atomic, versioned preferences. Construct with the config FILE, e.g. config/skyglint.json. */
class ConfigStore(val path: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var loaded = false
    private var blockedSchema: Int? = null

    @Volatile
    var current: ModConfig = ModConfig()
        private set

    @Volatile
    var lastRecoveryBackup: Path? = null
        private set

    @Synchronized
    fun load(): ModConfig {
        if (!Files.exists(path)) return persist(ModConfig())
        // Disk access failures are not corrupt JSON: preserve the file and report the I/O error.
        val text = Files.readString(path, StandardCharsets.UTF_8)
        val (parsed, migrated) = try {
            decode(text)
        } catch (unsupported: UnsupportedConfigVersionException) {
            blockedSchema = unsupported.version
            throw unsupported
        } catch (_: RuntimeException) {
            val timestamp = Instant.now().toEpochMilli()
            val backup = path.resolveSibling("${path.fileName}.corrupt-$timestamp-${UUID.randomUUID()}.bak")
            Files.copy(path, backup)
            lastRecoveryBackup = backup
            return persist(ModConfig())
        }
        if (migrated) return persist(parsed)
        current = parsed.sanitized()
        blockedSchema = null
        loaded = true
        return current
    }

    /** Publishes the new preferences only AFTER a successful write. */
    @Synchronized
    fun update(transform: (ModConfig) -> ModConfig): ModConfig {
        blockedSchema?.let { throw UnsupportedConfigVersionException(it) }
        if (!loaded) load()
        return persist(transform(current).sanitized())
    }

    private fun persist(value: ModConfig): ModConfig {
        val sanitized = value.sanitized()
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".skyglint-config-", ".tmp")
        try {
            Files.writeString(temporary, gson.toJson(sanitized) + "\n", StandardCharsets.UTF_8)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            current = sanitized
            blockedSchema = null
            loaded = true
            return current
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun decode(text: String): Pair<ModConfig, Boolean> {
        val root = JsonParser.parseString(text)
        require(root.isJsonObject) { "Config root must be an object" }
        val json = root.asJsonObject
        val defaults = ModConfig()
        val schema = integer(json, "schemaVersion", ModConfig.CURRENT_SCHEMA)
        if (schema !in 1..ModConfig.CURRENT_SCHEMA) throw UnsupportedConfigVersionException(schema)
        val scale = if (schema == 1 && json.has("hudScale")) {
            val legacy = number(json, "hudScale", ModConfig.PROTOTYPE_BASE_SCALE)
            (if (legacy.isFinite()) legacy.coerceIn(0.5, 2.0) else ModConfig.PROTOTYPE_BASE_SCALE) / ModConfig.PROTOTYPE_BASE_SCALE
        } else number(json, "hudScale", defaults.hudScale)
        return ModConfig(
            schemaVersion = ModConfig.CURRENT_SCHEMA,
            enabled = boolean(json, "enabled", defaults.enabled),
            loggingEnabled = boolean(json, "loggingEnabled", defaults.loggingEnabled),
            captureRawText = boolean(json, "captureRawText", defaults.captureRawText),
            skillTimeoutSeconds = integer(json, "skillTimeoutSeconds", defaults.skillTimeoutSeconds),
            hudScale = scale,
            topOffset = integer(json, "topOffset", defaults.topOffset),
            showPets = boolean(json, "showPets", defaults.showPets),
            // Schema 3 restores all observed rewards as the product default, including upgrades
            // from the former frequent-drop default. Explicit per-item choices remain intact.
            rewardMode = if (schema < 3) defaults.rewardMode else string(json, "rewardMode", defaults.rewardMode),
            rewardOverrides = stringMap(json, "rewardOverrides"),
            captureChat = if (schema < 3) false else boolean(json, "captureChat", defaults.captureChat),
            detailedDungeonHud = boolean(json, "detailedDungeonHud", defaults.detailedDungeonHud),
            checkUpdates = boolean(json, "checkUpdates", defaults.checkUpdates),
            hudOffsetX = number(json, "hudOffsetX", defaults.hudOffsetX),
            hudPositionY = nullableNumber(json, "hudPositionY", defaults.hudPositionY),
            showWelcome = boolean(json, "showWelcome", defaults.showWelcome),
            showEventReminders = boolean(json, "showEventReminders", defaults.showEventReminders),
            showEventStarts = boolean(json, "showEventStarts", defaults.showEventStarts),
            showSkillLevelUps = boolean(json, "showSkillLevelUps", defaults.showSkillLevelUps),
            showPetLevelUps = boolean(json, "showPetLevelUps", defaults.showPetLevelUps),
            showSkyBlockLevelUps = boolean(json, "showSkyBlockLevelUps", defaults.showSkyBlockLevelUps),
            showGardenVisitors = boolean(json, "showGardenVisitors", defaults.showGardenVisitors),
            showGardenPests = boolean(json, "showGardenPests", defaults.showGardenPests),
            showCropMilestones = boolean(json, "showCropMilestones", defaults.showCropMilestones),
            showMiningTimers = boolean(json, "showMiningTimers", defaults.showMiningTimers),
            showFuelWarnings = boolean(json, "showFuelWarnings", defaults.showFuelWarnings),
            showPetIntroduction = boolean(json, "showPetIntroduction", defaults.showPetIntroduction),
            showFishingBait = boolean(json, "showFishingBait", defaults.showFishingBait),
            showSackWarning = boolean(json, "showSackWarning", defaults.showSackWarning),
            rotationSeconds = integer(json, "rotationSeconds", defaults.rotationSeconds),
            helperLimit = integer(json, "helperLimit", defaults.helperLimit),
            showCookieExpiry = boolean(json, "showCookieExpiry", defaults.showCookieExpiry),
            showGodPotionExpiry = boolean(json, "showGodPotionExpiry", defaults.showGodPotionExpiry),
            showJerryDefense = boolean(json, "showJerryDefense", defaults.showJerryDefense),
            showReindrake = boolean(json, "showReindrake", defaults.showReindrake),
            showCookieWarning = boolean(json, "showCookieWarning", defaults.showCookieWarning),
            showGodPotionWarning = boolean(json, "showGodPotionWarning", defaults.showGodPotionWarning),
            showJerryResults = boolean(json, "showJerryResults", defaults.showJerryResults),
        ) to (schema != ModConfig.CURRENT_SCHEMA)
    }

    private fun boolean(json: JsonObject, key: String, fallback: Boolean): Boolean {
        val value = json.get(key) ?: return fallback
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$key must be boolean" }
        return value.asBoolean
    }

    private fun string(json: JsonObject, key: String, fallback: String): String {
        val value = json.get(key) ?: return fallback
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key must be text" }
        return value.asString
    }

    private fun stringMap(json: JsonObject, key: String): Map<String, String> {
        val value = json.get(key) ?: return emptyMap()
        require(value.isJsonObject) { "$key must be an object" }
        return value.asJsonObject.entrySet().asSequence().filter { (_, child) ->
            child.isJsonPrimitive && child.asJsonPrimitive.isString
        }.take(128).associate { it.key to it.value.asString }
    }

    private fun number(json: JsonObject, key: String, fallback: Double): Double {
        val value = json.get(key) ?: return fallback
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be numeric" }
        return value.asDouble
    }

    private fun integer(json: JsonObject, key: String, fallback: Int): Int {
        val value = json.get(key) ?: return fallback
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be an integer" }
        return value.asBigDecimal.intValueExact()
    }

    private fun nullableNumber(json: JsonObject, key: String, fallback: Double?): Double? {
        val value = json.get(key) ?: return fallback
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be numeric or null" }
        return value.asDouble
    }
}
