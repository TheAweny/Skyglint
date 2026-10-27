package dev.skyglint.fabric

import dev.skyglint.config.ConfigStore
import dev.skyglint.config.ModConfig
import dev.skyglint.core.*
import dev.skyglint.diagnostics.DiagnosticLog
import dev.skyglint.ui.SkyglintHud
import dev.skyglint.ui.SkyglintSettingsScreen
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.api.SkyglintModule
import dev.skyglint.features.LevelingMenuModule
import dev.skyglint.features.SimpleActivityModule
import dev.skyglint.features.RareDropSignalModule
import dev.skyglint.features.EventScheduleModule
import dev.skyglint.features.ObservedText
import dev.skyglint.features.ServerSignalCapture
import dev.skyglint.features.ContextModules
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.*
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.skyglint.mixin.PlayerTabOverlayAccessor
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam
import org.slf4j.LoggerFactory
import java.util.UUID
import com.mojang.brigadier.arguments.StringArgumentType
import dev.skyglint.diagnostics.DiagnosticExporter
import java.util.concurrent.atomic.AtomicBoolean

object SkyglintClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("Skyglint")
    lateinit var config: ConfigStore; private set
    lateinit var diagnostics: DiagnosticLog; private set
    private val liveEventNotices = EventNoticePolicy()
    val core = CoreController(eventNoticePolicy = liveEventNotices)
    var preview = false; private set
    private var previewScenario = "skill"
    private var previewStartedAt = 0L
    private var previewTimeline: PreviewTimeline? = null
    private var eventPreviewTimeline: EventPreviewTimeline? = null
    private var levelUpPreviewTimeline: LevelUpPreviewTimeline? = null
    private var contextPreviewTimeline: ContextPreviewTimeline? = null
    private var electionPreviewTimeline: ElectionPreviewTimeline? = null
    private var miningPreviewTimeline: MiningPreviewTimeline? = null
    private var nucleusPreviewTimeline: NucleusPreviewTimeline? = null
    private var confirmationPreviewTimeline: ConfirmationPreviewTimeline? = null
    private var slayerPetPreviewTimeline: SlayerPetPreviewTimeline? = null
    private var fishingPreviewTimeline: FishingPreviewTimeline? = null
    var petIcon: ItemStack? = null; private set
    var noticeIcon: ItemStack? = null; private set
    var noticeRarity: String? = null; private set
    var skyBlockIcon: ItemStack? = null; private set
    var fishingBaitIcon: ItemStack? = null; private set
    private val eventSchedule = EventScheduleModule()
    private var lastEventRefresh = 0L
    private var lastBossSummary = 0L
    private var lastNoticeHint: ItemVisualHint? = null
    private var lastNoticeKey: String? = null
    private var lastNoticeResolveAt = 0L
    private var petAuthorityUntil = 0L
    private var ticks = 0
    private val exporting = AtomicBoolean(false)
    private val electionApi = ElectionApiClient(onClosed = { age ->
        recordEvents("election_api", core.ingest(NormalizedSignal.ElectionClosed(age), now()))
    }, diagnostic = { status ->
        if (::diagnostics.isInitialized) diagnostics.record("election_api", status)
    })
    private var lastContextRefresh = 0L
    private var lastToolSignature: String? = null
    private var lastToolRefresh = Long.MIN_VALUE
    private var lastInventorySignature: String? = null
    private var lastInventoryRefresh = Long.MIN_VALUE
    private var contextModuleIds: Set<String> = emptySet()
    private var session: String? = null
    private var lastLevel: Any? = null
    private var lastConfirmedAt = 0L
    private var lastActionBar: String? = null
    private var lastActionPresenceAt = Long.MIN_VALUE
    private var lastTab: List<String> = emptyList()
    private var lastSidebar: List<String> = emptyList()
    private var lastLocation: String? = null
    private var locationObservedAt = 0L
    private var lastView: String? = null
    private var lastProfile: String? = null
    private var welcomePending = true
    private var lastMenuSignature: String? = null
    private var configFailure: String? = null
    private val contextSource = HypixelContextSource { context ->
        diagnostics.record("context", "location", mapOf("skyblock" to context.skyBlock, "server" to context.server, "mode" to context.mode))
        reset("location_changed", false)
        if (!context.skyBlock) welcomePending = true
    }
    private val bosses: BossPacketTracker = BossPacketTracker { action, id, details ->
        if (session != null) {
            diagnostics.record("bossbar", action, details + mapOf("id" to id.toString(), "session" to session))
            if (action == "remove") {
                core.removeBoss(id)
                publishMiningBosses(now())
            } else syncBosses(now())
        }
    }

    override fun onInitializeClient() {
        HudBackdrop.initialize()
        PetIconCache.initialize()
        ElectionHeads.initialize()
        val migration = dev.skyglint.updates.LegacyConfigMigration.migrate(FabricLoader.getInstance().configDir)
        if (migration.migrated) logger.info("Imported legacy preferences; original config and logs are preserved")
        else if (migration.error != null) logger.warn("Legacy preference import was skipped: {}", migration.error)
        val directory = FabricLoader.getInstance().configDir.resolve("skyglint")
        config = ConfigStore(directory.resolve("config.json"))
        try { config.load() } catch (e: Exception) {
            configFailure = e.message ?: e.javaClass.simpleName
            logger.error("Skyglint config could not load; HUD disabled until the file is repaired", e)
        }
        core.rewardPolicy = RewardRulePolicy.fromConfig(config.current.rewardMode, config.current.rewardOverrides)
        diagnostics = DiagnosticLog(directory.resolve("logs"), { config.current })
        GitHubUpdates.initialize()
        core.modules.onFailure = { failure ->
            logger.error("Skyglint detector {} failed on {}", failure.moduleId, failure.signalType, failure.cause)
            diagnostics.record("module", "failure", mapOf("id" to failure.moduleId, "signal" to failure.signalType,
                "error" to failure.cause.javaClass.simpleName), force = true)
        }
        core.modules.register(LevelingMenuModule()).register(SimpleActivityModule(::now)).register(RareDropSignalModule()).register(eventSchedule)
        val contextualModules = ContextModules.create(::now)
        contextualModules.forEach(core.modules::register)
        contextModuleIds = contextualModules.mapTo(mutableSetOf()) { it.id }
        FabricLoader.getInstance().getEntrypoints("skyglint:modules", SkyglintModule::class.java).forEach { module ->
            try { module.register(core.modules) } catch (failure: Exception) {
                logger.error("Skyglint companion module initialization failed", failure)
            }
        }
        contextSource.initialize()
        diagnostics.record("lifecycle", "start", mapOf("version" to FabricLoader.getInstance().getModContainer("reborn").get().metadata.version.friendlyString,
            "minecraft" to "26.2", "configError" to configFailure))
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientLifecycleEvents.CLIENT_STARTED.register { ModUpdateCleanup.run() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> contextSource.reset(); reset("disconnect", true) }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> receiveGame(message, overlay, false) }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> receiveGame(message, overlay, true) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            diagnostics.recordChat("player", observedChatText(message), session = session)
        }
        ClientReceiveMessageEvents.CHAT_CANCELED.register { message, _, _, _, _ ->
            diagnostics.recordChat("player", observedChatText(message), cancelled = true, session = session)
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { GitHubUpdates.close(); electionApi.close(); diagnostics.record("lifecycle", "stop"); diagnostics.close() }
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("skyglint", "hud")) { graphics, _ -> SkyglintHud.render(graphics) }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val commands = dispatcher.register(literal("skyglint").requires { it.attended() }
                .executes { openSettings(); 1 }
                .then(literal("update").executes { GitHubUpdates.open(); 1 }
                    .then(literal("check").executes { GitHubUpdates.check(); 1 })
                    .then(literal("download").executes { GitHubUpdates.download(); 1 }))
                .then(literal("diagnostics").executes { feedback("Diagnostics: logs=${config.current.loggingEnabled}, raw=${config.current.captureRawText}, chat=${config.current.captureChat}. on|off|export"); 1 }
                    .then(literal("on").executes { command {
                        setDiagnostics(true)
                        feedback("Full diagnostics ON and saved: events, HUD/menu sources and all received chat. Report: /skyglint diagnostics export")
                    } })
                    .then(literal("off").executes { command {
                        setDiagnostics(false)
                        feedback("Full diagnostics OFF")
                    } })
                    .then(literal("export").executes { exportDiagnostics(); 1 }))
                .then(literal("status").executes {
                    feedback("HUD=${config.current.enabled}; SkyBlock=${session != null}; logs=${config.current.loggingEnabled}; raw=${config.current.captureRawText}; chat=${config.current.captureChat}; drops=${config.current.rewardMode}; dropped=${diagnostics.droppedRecords}; ${diagnostics.lastFailure?.message ?: configFailure ?: "config OK"}"); 1
                })
                .then(literal("drops").executes { feedback("Drop mode=${config.current.rewardMode}; item rules=${config.current.rewardOverrides.size}. /skyglint drops hide|show|reset <item>; mode all|filtered"); 1 }
                    .then(literal("mode").then(literal("all").executes { command { changeConfig { it.copy(rewardMode = "all") }; feedback("Show all recognized rewards; explicit item rules retained") } })
                        .then(literal("filtered").executes { command { changeConfig { it.copy(rewardMode = "filtered") }; feedback("Frequent drop filter ON") } }))
                    .then(literal("hide").then(argument("item", StringArgumentType.greedyString()).executes {
                        command { setRewardRule(StringArgumentType.getString(it, "item"), "hide") }
                    }))
                    .then(literal("show").then(argument("item", StringArgumentType.greedyString()).executes {
                        command { setRewardRule(StringArgumentType.getString(it, "item"), "show") }
                    }))
                    .then(literal("reset").then(argument("item", StringArgumentType.greedyString()).executes {
                        command { setRewardRule(StringArgumentType.getString(it, "item"), null) }
                    })))
                .then(literal("logs").then(literal("on").executes { command { setLogging(true); feedback("Logging ON; config/skyglint/logs/skyglint-events.jsonl") } })
                    .then(literal("off").executes { command { setLogging(false); feedback("Logging OFF") } })
                    .then(literal("chat").then(literal("on").executes { command { setChatLogging(true); feedback("Full received chat capture ON; includes private and hidden messages") } })
                        .then(literal("off").executes { command { setChatLogging(false); feedback("Full chat capture OFF") } }))
                    .then(literal("raw").then(literal("on").executes { command { setRawLogging(true); feedback("Raw relevant server signals ON") } })
                        .then(literal("off").executes { command { setRawLogging(false); feedback("Raw signals OFF") } })))
                .then(literal("preview").then(literal("on").executes { setPreview(true); feedback("Synthetic preview ON"); 1 })
                    .then(literal("off").executes { setPreview(false); feedback("Preview OFF"); 1 })
                    .then(literal("skill").executes { setPreviewScenario("skill"); 1 })
                    .then(literal("skyblock").executes { setPreviewScenario("skyblock"); 1 })
                    .then(literal("unknown").executes { setPreviewScenario("unknown"); 1 })
                    .then(literal("levelup").executes { setPreviewScenario("levelup"); 1 })
                    .then(literal("objective").executes { setPreviewScenario("objective"); 1 })
                    .then(literal("rare").executes { setPreviewScenario("rare"); 1 })
                    .then(literal("ranks").executes { setPreviewScenario("ranks"); 1 })
                    .then(literal("mining").executes { setPreviewScenario("mining"); 1 })
                    .then(literal("nucleus").executes { setPreviewScenario("nucleus"); 1 })
                    .then(literal("confirmations").executes { setPreviewScenario("confirmations"); 1 })
                    .then(literal("slayerpets").executes { setPreviewScenario("slayerpets"); 1 })
                    .then(literal("fishing").executes { setPreviewScenario("fishing"); 1 })
                    .then(literal("idle").executes { setPreviewScenario("idle"); 1 })
                    .then(literal("event").executes { setPreviewScenario("event"); 1 })
                    .then(literal("petlevelup").executes { setPreviewScenario("petlevelup"); 1 })
                    .then(literal("skilllevelup").executes { setPreviewScenario("skilllevelup"); 1 })
                    .then(literal("garden").executes { setPreviewScenario("garden"); 1 })
                    .then(literal("dungeon").executes { setPreviewScenario("dungeon"); 1 })
                    .then(literal("election").executes { setPreviewScenario("election"); 1 })
                    .then(literal("sequence").executes { setPreviewScenario("sequence"); 1 }))
                .then(literal("enable").executes { command { setEnabled(true) } })
                .then(literal("disable").executes { command { setEnabled(false) } }))
            dispatcher.register(literal("reborn").requires { it.attended() }.redirect(commands))
        }
    }

    fun now(): Long = System.nanoTime() / 1_000_000
    fun changeConfig(transform: (ModConfig) -> ModConfig) {
        val before = config.current
        val after = config.update(transform)
        core.skillActiveMillis = after.skillTimeoutSeconds * 1_000L
        core.rewardPolicy = RewardRulePolicy.fromConfig(after.rewardMode, after.rewardOverrides)
        diagnostics.record("config", "changed", mapOf("enabled" to after.enabled, "logs" to after.loggingEnabled,
            "raw" to after.captureRawText, "chat" to after.captureChat, "scale" to after.hudScale, "timeoutSeconds" to after.skillTimeoutSeconds,
            "rewardMode" to after.rewardMode, "rewardRules" to after.rewardOverrides.size, "detailedDungeon" to after.detailedDungeonHud),
            force = before.loggingEnabled || after.loggingEnabled)
    }
    fun setLogging(enabled: Boolean) {
        changeConfig { it.copy(loggingEnabled = enabled) }
        if (enabled) {
            diagnostics.record("session", "logging_snapshot", mapOf("session" to session, "modules" to core.modules.ids.joinToString(",")))
            bosses.states.values.forEach { diagnostics.record("bossbar", "logging_snapshot", mapOf("id" to it.id.toString(),
                "title" to it.title, "progress" to it.progress, "color" to it.color, "overlay" to it.overlay)) }
        }
    }
    fun setRawLogging(enabled: Boolean) = changeConfig { it.copy(captureRawText = enabled) }
    fun setChatLogging(enabled: Boolean) = changeConfig { it.copy(captureChat = enabled, loggingEnabled = it.loggingEnabled || enabled) }
    fun setDiagnostics(enabled: Boolean) {
        changeConfig { it.copy(loggingEnabled = enabled, captureRawText = enabled, captureChat = enabled) }
        if (enabled) setLogging(true)
    }
    fun exportDiagnostics() {
        if (!exporting.compareAndSet(false, true)) { feedback("A diagnostic archive is already being prepared"); return }
        val preferences = config.current
        val loader = FabricLoader.getInstance()
        val directory = loader.configDir.resolve("skyglint")
        val gameLogs = loader.gameDir.resolve("logs")
        val version = loader.getModContainer("reborn").get().metadata.version.friendlyString
        val snapshot = core.snapshot(now())
        diagnostics.record("report", "snapshot", mapOf("version" to version, "active" to snapshot.active,
            "location" to snapshot.location, "modules" to core.modules.ids.joinToString(","),
            "bossCount" to snapshot.bosses.size, "widgets" to snapshot.widgets.joinToString(",") { it.id },
            "progression" to snapshot.progression?.javaClass?.simpleName, "pet" to snapshot.pet?.name), force = true)
        feedback("Preparing local ZIP; it can include private chat. Nothing is uploaded automatically.")
        Thread({
            try {
                val flushed = diagnostics.flush()
                val output = DiagnosticExporter().export(directory, gameLogs, directory.resolve("reports"), version,
                    mapOf("schemaVersion" to preferences.schemaVersion, "enabled" to preferences.enabled,
                        "loggingEnabled" to preferences.loggingEnabled, "captureRawText" to preferences.captureRawText,
                        "captureChat" to preferences.captureChat, "rewardMode" to preferences.rewardMode,
                        "rewardOverrides" to preferences.rewardOverrides, "showPets" to preferences.showPets, "topOffset" to preferences.topOffset,
                        "detailedDungeonHud" to preferences.detailedDungeonHud,
                        "hudScale" to preferences.hudScale, "skillTimeoutSeconds" to preferences.skillTimeoutSeconds,
                        "minecraft" to "26.2", "flushSucceeded" to flushed, "droppedRecords" to diagnostics.droppedRecords))
                Minecraft.getInstance().execute { feedback("Report ready: $output", SkyglintChat.Tone.SUCCESS) }
            } catch (failure: Exception) {
                logger.warn("Could not export diagnostic archive", failure)
                Minecraft.getInstance().execute { feedback("Could not create report: ${failure.javaClass.simpleName}", SkyglintChat.Tone.ERROR) }
            } finally { exporting.set(false) }
        }, "skyglint-report-export").apply { isDaemon = true; start() }
    }
    fun setEnabled(enabled: Boolean) = changeConfig { it.copy(enabled = enabled) }
    fun setDetailedDungeonHud(detailed: Boolean) = changeConfig { it.copy(detailedDungeonHud = detailed) }
    private fun setRewardRule(itemName: String, rule: String?) {
        val name = RewardRulePolicy.normalizeItemName(itemName)
        require(name.isNotEmpty() && name.length <= 120 && name.none(Char::isISOControl)) { "Item name must contain 1–120 characters" }
        require(rule == null || name in config.current.rewardOverrides || config.current.rewardOverrides.size < 128) { "Maximum 128 item rules; reset an existing rule first" }
        changeConfig { it.copy(rewardOverrides = if (rule == null) it.rewardOverrides - name else it.rewardOverrides + (name to rule)) }
        feedback("$name: ${rule ?: "default"}")
    }
    fun setPreview(enabled: Boolean) {
        if (enabled && !preview) {
            previewStartedAt = now()
            if (previewScenario == "sequence") previewTimeline = PreviewTimeline(previewStartedAt)
            if (previewScenario == "event") eventPreviewTimeline = EventPreviewTimeline(previewStartedAt)
            if (previewScenario in setOf("petlevelup", "skilllevelup"))
                levelUpPreviewTimeline = LevelUpPreviewTimeline(previewStartedAt, previewScenario == "petlevelup")
            if (previewScenario in setOf("garden", "dungeon")) contextPreviewTimeline = ContextPreviewTimeline(previewStartedAt, previewScenario == "dungeon")
            if (previewScenario == "election") electionPreviewTimeline = ElectionPreviewTimeline(previewStartedAt)
            if (previewScenario == "mining") miningPreviewTimeline = MiningPreviewTimeline(previewStartedAt)
            if (previewScenario == "confirmations") confirmationPreviewTimeline = ConfirmationPreviewTimeline(previewStartedAt)
            if (previewScenario == "slayerpets") slayerPetPreviewTimeline = SlayerPetPreviewTimeline(previewStartedAt)
            if (previewScenario == "fishing") fishingPreviewTimeline = FishingPreviewTimeline(previewStartedAt)
        }
        if (!enabled && preview) clearNoticeIcon()
        preview = enabled
        diagnostics.record("preview", if (enabled) "on" else "off")
    }
    fun setPreviewScenario(scenario: String) {
        require(scenario in setOf("objective", "skill", "skyblock", "unknown", "levelup", "rare", "sequence", "idle", "event", "petlevelup", "skilllevelup", "garden", "dungeon", "election", "ranks", "mining", "nucleus", "confirmations", "slayerpets", "fishing"))
        previewScenario = scenario
        previewStartedAt = now()
        previewTimeline = if (scenario == "sequence") PreviewTimeline(previewStartedAt) else null
        eventPreviewTimeline = if (scenario == "event") EventPreviewTimeline(previewStartedAt) else null
        levelUpPreviewTimeline = if (scenario in setOf("petlevelup", "skilllevelup")) LevelUpPreviewTimeline(previewStartedAt, scenario == "petlevelup") else null
        contextPreviewTimeline = if (scenario in setOf("garden", "dungeon")) ContextPreviewTimeline(previewStartedAt, scenario == "dungeon") else null
        electionPreviewTimeline = if (scenario == "election") ElectionPreviewTimeline(previewStartedAt) else null
        miningPreviewTimeline = if (scenario == "mining") MiningPreviewTimeline(previewStartedAt) else null
        nucleusPreviewTimeline = if (scenario == "nucleus") NucleusPreviewTimeline(previewStartedAt) else null
        confirmationPreviewTimeline = if (scenario == "confirmations") ConfirmationPreviewTimeline(previewStartedAt) else null
        slayerPetPreviewTimeline = if (scenario == "slayerpets") SlayerPetPreviewTimeline(previewStartedAt) else null
        fishingPreviewTimeline = if (scenario == "fishing") FishingPreviewTimeline(previewStartedAt) else null
        clearNoticeIcon()
        setPreview(true)
    }
    fun openSettings() { val client = Minecraft.getInstance(); client.execute { client.gui.setScreen(SkyglintSettingsScreen(client.gui.screen())) } }
    fun hudSnapshot(): HudSnapshot {
        if (preview) {
            val demo = if (previewScenario == "ranks") rankPreviewSnapshot() else
                fishingPreviewTimeline?.snapshot(now()) ?: slayerPetPreviewTimeline?.snapshot(now()) ?: confirmationPreviewTimeline?.snapshot(now()) ?: nucleusPreviewTimeline?.snapshot(now()) ?: miningPreviewTimeline?.snapshot(now()) ?: electionPreviewTimeline?.snapshot(now()) ?: contextPreviewTimeline?.snapshot(now()) ?: levelUpPreviewTimeline?.snapshot(now()) ?: eventPreviewTimeline?.snapshot(now()) ?: previewTimeline?.snapshot(now()) ?: demoSnapshot()
            val client = Minecraft.getInstance()
            updateNoticeIcon(client, demo.activeNotice, demo.pet)
            if (client.level != null) { petIcon = PetIconCache.resolve(demo.pet); skyBlockIcon = HeadIconResolver.skyBlockHead(client) }
            if (previewScenario == "fishing" && client.level != null) {
                // Explicit preview-only stand-in. Live play always copies the actual server preview ItemStack.
                fishingBaitIcon = ItemStack(net.minecraft.world.item.Items.SALMON)
            }
            return demo
        }
        return if (configFailure != null) HudSnapshot(false, null, emptyList(), null, null, null) else core.snapshot(now())
    }

    @JvmStatic fun shouldReplaceBossBars(): Boolean = ::config.isInitialized && configFailure == null && config.current.enabled && session != null
    @JvmStatic fun onBossPacket(packet: ClientboundBossEventPacket) { if (::diagnostics.isInitialized) packet.dispatch(bosses) }
    @JvmStatic fun onBossReset() { if (::diagnostics.isInitialized) { bosses.clear(); core.snapshot(now()).bosses.forEach { core.removeBoss(it.id) }; core.ingest(NormalizedSignal.BossSnapshot(emptyList()), now()); diagnostics.record("bossbar", "reset") } }

    private fun tick(client: Minecraft) {
        if (client.level !== lastLevel) {
            // Vanilla reset owns packet-map clearing. Packets for the new world can arrive before this tick.
            reset("world_changed", false)
            lastLevel = client.level
        }
        if (++ticks % 5 != 0) return
        val summaryTime = now()
        if (summaryTime - lastBossSummary >= 30_000) {
            lastBossSummary = summaryTime
            val duplicates = bosses.drainDuplicates()
            if (duplicates.isNotEmpty()) diagnostics.record("bossbar", "unchanged_packets", duplicates + mapOf("session" to session))
        }
        val connection = client.connection
        val address = client.currentServer?.ip?.substringBefore(':')?.lowercase().orEmpty()
        val hypixel = address == "hypixel.net" || address.endsWith(".hypixel.net")
        val title = client.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.displayName?.string.orEmpty()
        val apiContext = contextSource.current
        val confirmed = hypixel && (apiContext?.skyBlock ?: SkyBlockParser.clean(title).replace(" ", "").startsWith("SKYBLOCK"))
        val time = now()
        if (confirmed) {
            lastConfirmedAt = time
            if (session == null) {
                session = UUID.randomUUID().toString()
                core.enterSkyBlock(session!!, time)
                if (configFailure == null) GitHubUpdates.onSkyBlockLogin()
                if (welcomePending) core.prepareWelcome(time)
                diagnostics.record("session", "enter", mapOf("session" to session, "source" to if (apiContext != null) "hypixel_mod_api" else "hypixel_sidebar"))
                syncBosses(time)
            }
        } else if (session != null && (!hypixel || connection == null || time - lastConfirmedAt > 1_500)) {
            reset("skyblock_left", false)
        }
        if (session == null || connection == null) {
            electionApi.poll(time, null) { _, _ -> }
            return
        }
        core.skillActiveMillis = config.current.skillTimeoutSeconds * 1_000L
        val tabAccess = client.gui.hud.tabList as PlayerTabOverlayAccessor
        val tab = buildList {
            tabAccess.skyglintHeader()?.string?.lines()?.let(::addAll)
            addAll(connection.listedOnlinePlayers.sortedWith(PlayerTabOverlayAccessor.skyglintComparator())
                .mapNotNull { it.tabListDisplayName?.string })
            tabAccess.skyglintFooter()?.string?.lines()?.let(::addAll)
        }
        val board = client.level?.scoreboard
        val sidebar = board?.getDisplayObjective(DisplaySlot.SIDEBAR)?.let { objective ->
            board.listPlayerScores(objective).filterNot { it.isHidden }.sortedByDescending { it.value() }.take(15).map {
                PlayerTeam.formatNameForTeam(board.getPlayersTeam(it.owner()), it.ownerName()).string
            }
        }.orEmpty()
        val serverLocation = ObservedText.location(sidebar)
        val observedLocation = serverLocation
        if (observedLocation != null) { lastLocation = observedLocation; locationObservedAt = time }
        val freshLocation = lastLocation.takeIf { time - locationObservedAt < 3_000 }
        val publicElectionAllowed = address != "alpha.hypixel.net" && !address.startsWith("alpha.")
        recordEvents("election_source", core.ingest(NormalizedSignal.ElectionSourceAvailability(publicElectionAllowed), time))
        recordEvents("sidebar", core.apply(listOf(CoreEvent.ContextObserved(freshLocation, apiContext?.mode)), time))
        recordEvents("context_features", core.ingest(NormalizedSignal.ContextSnapshot(freshLocation, apiContext?.mode), time))
        observeHeldTool(client, time)
        observeFishingBait(client, time)
        if (apiContext?.mode == "crystal_hollows") observeMiningInventory(client, time)
        if (sidebar != lastSidebar) {
            lastSidebar = sidebar
            diagnostics.record("sidebar", "snapshot", mapOf("session" to session), rawText = sidebar.joinToString("\n"))
        }
        val profile = tab.asSequence().map(SkyBlockParser::clean).firstOrNull { it.startsWith("Profile: ") }
        if (profile != null && lastProfile != null && profile != lastProfile) {
            reset("profile_changed", false)
            // Require the next tick to establish the new session before consuming its snapshots.
            lastProfile = profile
            return
        }
        if (profile != null) lastProfile = profile
        if (petAuthorityUntil != 0L && time >= petAuthorityUntil) {
            petAuthorityUntil = 0
            lastTab = emptyList() // Reconcile a stable tab snapshot deferred by authoritative menu data.
        }
        if (tab != lastTab) {
            lastTab = tab
            val parsed = core.modules.process(NormalizedSignal.TabSnapshot(tab)).filterNot { time < petAuthorityUntil && it is CoreEvent.PetObserved }
            recordEvents("tab", core.apply(parsed, time))
            if (config.current.captureRawText) tab.chunked(32).forEachIndexed { index, lines ->
                diagnostics.record("tab", "snapshot", mapOf("session" to session, "chunk" to index), rawText = lines.joinToString("\n"))
            }
        }
        if (time - lastContextRefresh >= 1_000) {
            lastContextRefresh = time
            recordEvents("context_tab", core.apply(core.modules.processSelected(NormalizedSignal.TabSnapshot(tab), contextModuleIds), time))
            recordEvents("context_sidebar", core.ingest(NormalizedSignal.SidebarSnapshot(sidebar), time))
            recordEvents("mining_boss", core.ingest(NormalizedSignal.BossSnapshot(bosses.states.values.toList()), time))
        }
        // The public main-network resource has no player data or API key. Alpha is a separate election.
        val electionArea = freshLocation in setOf("Community Center", "Election Room", "Election Booth")
        val publicElectionContext = session.takeIf { config.current.enabled && apiContext?.mode == "hub" &&
            electionArea && publicElectionAllowed }
        electionApi.poll(time, publicElectionContext) { payload, age ->
            recordEvents("election_api", core.ingest(NormalizedSignal.ElectionSnapshot(payload.year, payload.candidates, age), time))
        }
        val refreshEvents = time - lastEventRefresh >= 10_000
        if (refreshEvents) {
            lastEventRefresh = time
            recordEvents("tab_event", core.apply(eventSchedule.parse(NormalizedSignal.TabSnapshot(tab)), time))
        }
        PetMenuReader.observe(client, time)?.let { observation ->
            recordEvents("pet_menu", core.apply(listOf(CoreEvent.PetObserved(observation.pet, authoritative = true)), time))
            petIcon = observation.icon
            petAuthorityUntil = time + 5_000
        }
        HeadIconResolver.observeMenu(client)
        observeMenu(client, time, refreshEvents)
        val snapshot = core.snapshot(time)
        GitHubUpdates.onSkyBlockTick()
        if (core.welcomeWasOffered()) welcomePending = false
        petIcon = PetIconCache.resolve(snapshot.pet)
        skyBlockIcon = HeadIconResolver.skyBlockHead(client)
        updateNoticeIcon(client, snapshot.activeNotice, snapshot.pet)
        val view = "${snapshot.progression?.javaClass?.simpleName}:${(snapshot.progression as? ProgressionView.SkillView)?.skill}:${snapshot.activeNotice?.id}:${snapshot.pet?.name}"
        if (view != lastView) { lastView = view; diagnostics.record("hud", "selection", mapOf("view" to view, "session" to session)) }
    }

    private fun receiveGame(message: Component, overlay: Boolean, cancelled: Boolean) {
        val text = if (overlay) message.string else observedChatText(message)
        if (!overlay) diagnostics.recordChat("system", text, cancelled, session)
        receive(text, overlay)
    }

    private fun receive(text: String, overlay: Boolean) {
        if (session == null) return
        val time = now()
        if (overlay) {
            if (text != lastActionBar) {
                lastActionBar = text
                diagnostics.record("actionbar", "observed", mapOf("session" to session), rawText = text)
                recordEvents("actionbar", core.onActionBar(text, time))
                lastActionPresenceAt = time
            } else if (lastActionPresenceAt == Long.MIN_VALUE || time - lastActionPresenceAt >= 1_000) {
                lastActionPresenceAt = time
                // This is a newly received packet, never replay of cached text from the tick loop.
                recordEvents("actionbar_presence", core.ingest(NormalizedSignal.ActionBarPresence(text), time))
            }
        } else {
            val events = core.onChat(text, time)
            if (events.isNotEmpty()) {
                diagnostics.record("chat", "recognized", mapOf("session" to session), rawText = text)
                recordEvents("chat", events)
            } else if (config.current.captureRawText && ServerSignalCapture.relevant(text))
                diagnostics.record("chat", "unrecognized_server_signal", mapOf("session" to session), rawText = text)
        }
    }
    /** Preserve legacy colors in diagnostics and detectors. Player chat is logged but never parsed as gameplay. */
    private fun observedChatText(message: Component): String {
        val result = StringBuilder()
        val colors = mapOf(0x000000 to '0', 0x0000AA to '1', 0x00AA00 to '2', 0x00AAAA to '3',
            0xAA0000 to '4', 0xAA00AA to '5', 0xFFAA00 to '6', 0xAAAAAA to '7', 0x555555 to '8',
            0x5555FF to '9', 0x55FF55 to 'a', 0x55FFFF to 'b', 0xFF5555 to 'c', 0xFF55FF to 'd',
            0xFFFF55 to 'e', 0xFFFFFF to 'f')
        message.visit<Unit>({ style, text ->
            result.append('§').append(colors[style.color?.value] ?: 'r').append(text)
            java.util.Optional.empty()
        }, Style.EMPTY)
        return result.toString()
    }
    private fun recordEvents(source: String, events: List<CoreEvent>) {
        events.forEach { event ->
            val details: Map<String, Any?> = when (event) {
                is CoreEvent.SkillGain -> mapOf("skill" to event.skill.name, "gain" to event.gain, "fraction" to event.progress?.fraction, "current" to event.progress?.current, "maximum" to event.progress?.maximum)
                is CoreEvent.SkillObserved -> mapOf("skill" to event.snapshot.skill.name, "level" to event.snapshot.level, "fraction" to event.snapshot.progress?.fraction)
                is CoreEvent.SkyBlockGain -> mapOf("gain" to event.gain, "current" to event.progress?.current, "maximum" to event.progress?.maximum)
                is CoreEvent.SkyBlockObserved -> mapOf("level" to event.snapshot.level, "current" to event.snapshot.progress?.current)
                is CoreEvent.PetObserved -> mapOf("name" to event.snapshot?.name, "level" to event.snapshot?.level, "fraction" to event.snapshot?.progress?.fraction)
                is CoreEvent.PetSwitched -> {
                    petAuthorityUntil = now() + 5_000
                    if (event.snapshot.skin) PetIconCache.invalidateSelectedSkin(event.snapshot.name)
                    mapOf("name" to event.snapshot.name, "level" to event.snapshot.level, "automatic" to event.automatic)
                }
                is CoreEvent.PetLevelUp -> mapOf("name" to event.name, "level" to event.level)
                is CoreEvent.SkillLevelUp -> mapOf("skill" to event.skill.name, "level" to event.level)
                is CoreEvent.SkyBlockLevelUp -> mapOf("level" to event.newLevel)
                is CoreEvent.WidgetObserved -> mapOf("widget" to event.widget.id, "title" to event.widget.title, "priority" to event.widget.priority, "expiresAt" to event.widget.expiresAt)
                is CoreEvent.WidgetRemoved -> mapOf("widget" to event.id)
                is CoreEvent.NoticeObserved -> mapOf("notice" to event.request.id, "priority" to event.request.priority,
                    "item" to event.request.item?.itemName, "magicFind" to event.request.magicFind, "rarity" to event.request.rarity)
                is CoreEvent.ContextObserved -> mapOf("location" to event.location, "mode" to event.mode)
                is CoreEvent.EventObserved -> mapOf("event" to event.observation.name, "startsInMillis" to event.observation.startsInMillis,
                    "active" to event.observation.active, "source" to event.observation.source)
                is CoreEvent.RewardObserved -> mapOf("item" to event.observation.itemName, "sourceId" to event.observation.sourceId,
                    "sourceTitle" to event.observation.sourceTitle, "magicFind" to event.observation.magicFind,
                    "rarity" to event.observation.rarity, "amount" to event.observation.amount,
                    "fishingCategory" to event.observation.fishingCategory?.name,
                    "catchQuality" to event.observation.catchQuality?.name, "trophyGrade" to event.observation.trophyGrade?.name,
                    "shown" to event.decision?.shown,
                    "reason" to event.decision?.reason, "queued" to event.decision?.queued)
            }
            diagnostics.record(source, event.javaClass.simpleName, details + mapOf("session" to session))
        }
    }
    private fun syncBosses(time: Long) {
        if (session == null) return
        bosses.states.values.forEach { core.updateBoss(it.id, it.title, it.progress, it.color, it.overlay, time) }
        recordEvents("mining_boss", core.ingest(NormalizedSignal.BossSnapshot(bosses.states.values.toList()), time))
    }
    private fun publishMiningBosses(time: Long) {
        recordEvents("mining_boss", core.ingest(NormalizedSignal.BossSnapshot(bosses.states.values.toList()), time))
    }
    /** The server bait preview is own hotbar slot 8, never a container inventory or inferred stack sum. */
    private fun observeFishingBait(client: Minecraft, time: Long) {
        val player = client.player ?: return
        val holdingRod = player.mainHandItem.`is`(net.minecraft.world.item.Items.FISHING_ROD)
        val stack = player.inventory.getItem(8)
        val item = if (!holdingRod || client.gui.screen() is AbstractContainerScreen<*> || stack.isEmpty) null else {
            val custom = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
            val attributes = custom?.getCompound("ExtraAttributes")?.orElse(null) ?: custom
            MenuItemSnapshot(8, stack.hoverName.string.take(256),
                stack.get(DataComponents.LORE)?.lines()?.take(48)?.map { it.string.take(256) }.orEmpty(),
                attributes?.getString("id")?.orElse(null)?.take(80))
        }
        recordEvents("fishing_bait", core.ingest(NormalizedSignal.FishingBaitSnapshot(item, holdingRod), time))
        fishingBaitIcon = item?.let { stack.copy().apply {
            // Count lore is not artwork: preserving it here would restart icon transitions every catch.
            remove(DataComponents.LORE)
            count = 1
        } }
    }

    /** Selected stack only, copied server lore and two public drill fields; no raw NBT export. */
    private fun observeHeldTool(client: Minecraft, time: Long) {
        val player = client.player ?: return
        val stack = player.mainHandItem
        val custom = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val attributes = custom?.getCompound("ExtraAttributes")?.orElse(null) ?: custom
        val item = if (stack.isEmpty) null else MenuItemSnapshot(0, stack.hoverName.string,
            stack.get(DataComponents.LORE)?.lines()?.take(128)?.map { it.string.take(1_024) }.orEmpty(),
            attributes?.getString("id")?.orElse(null)?.take(80), attributes?.getLong("drill_fuel")?.orElse(null))
        val signature = item?.let { "${it.name}|${it.skyBlockId}|${it.drillFuel}|${it.lore}" } ?: "empty"
        val changed = signature != lastToolSignature
        if (!changed && lastToolRefresh != Long.MIN_VALUE && time - lastToolRefresh < 1_000) return
        lastToolSignature = signature; lastToolRefresh = time
        recordEvents("held_tool", core.ingest(NormalizedSignal.HeldItemSnapshot(item), time))
        if (changed && (item?.drillFuel != null || item?.lore?.any { SkyBlockParser.clean(it).contains(" DRILL") } == true)) {
            diagnostics.record("held_tool", "snapshot", mapOf("name" to item?.name, "itemId" to item?.skyBlockId,
                "fuel" to item?.drillFuel), rawText = item?.lore?.joinToString("\n"))
        }
    }
    /** Bounded player slots only; no container/world scan or unbounded custom NBT diagnostics. */
    private fun observeMiningInventory(client: Minecraft, time: Long) {
        val player = client.player ?: return
        val inventory = player.inventory
        val stacks = (0 until 36).map { it to inventory.getItem(it) } + (40 to player.offhandItem)
        val items = stacks.mapNotNull { (slot, stack) ->
            if (stack.isEmpty) return@mapNotNull null
            val custom = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
            val attributes = custom?.getCompound("ExtraAttributes")?.orElse(null) ?: custom
            val id = attributes?.getString("id")?.orElse(null)?.take(80) ?: return@mapNotNull null
            // Only keeper tools are relevant here. Full inventory observation still confirms absence.
            if (id !in setOf("DWARVEN_LAPIS_SWORD", "DWARVEN_EMERALD_HAMMER", "DWARVEN_GOLD_HAMMER", "DWARVEN_DIAMOND_AXE")) return@mapNotNull null
            MenuItemSnapshot(slot, stack.hoverName.string.take(256), emptyList(), id)
        }
        val signature = items.joinToString("|") { "${it.slot}:${it.skyBlockId}" }
        val changed = signature != lastInventorySignature
        if (!changed && lastInventoryRefresh != Long.MIN_VALUE && time - lastInventoryRefresh < 1_000) return
        lastInventorySignature = signature; lastInventoryRefresh = time
        recordEvents("mining_inventory", core.ingest(NormalizedSignal.InventorySnapshot(items), time))
        if (changed) diagnostics.record("mining_inventory", "keeper_tools", mapOf("items" to signature), rawText = null)
    }
    private fun observeMenu(client: Minecraft, time: Long, refreshEvents: Boolean) {
        val screen = client.gui.screen() as? AbstractContainerScreen<*>
        if (screen == null) { lastMenuSignature = null; return }
        val items = screen.menu.slots.asSequence().filter { it.container !== client.player?.inventory }
            .filter { !it.item.isEmpty }.map { slot ->
                MenuItemSnapshot(slot.index, slot.item.hoverName.string,
                    slot.item.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty())
            }.toList()
        val signature = screen.title.string + items.joinToString { "${it.slot}:${it.name}:${it.lore}" }
        if (signature == lastMenuSignature) {
            if (refreshEvents) recordEvents("menu_event", core.apply(eventSchedule.parse(NormalizedSignal.MenuSnapshot(screen.title.string, items)), time))
            return
        }
        lastMenuSignature = signature
        val title = SkyBlockParser.clean(screen.title.string)
        val relevant = title in setOf("SkyBlock Menu", "SkyBlock Leveling", "Pets", "Calendar and Events", "Your Skills") ||
            title.matches(Regex("Pets \\([0-9]+/[0-9]+\\)"))
        diagnostics.record("menu", "observed", mapOf("title" to title, "slots" to items.size, "relevant" to relevant),
            rawText = null)
        if (config.current.captureRawText) items.take(120).forEach { item ->
            diagnostics.record("menu", "item_snapshot", mapOf("title" to title, "slot" to item.slot),
                rawText = "${item.name}\n${item.lore.take(48).joinToString("\n")}")
        }
        recordEvents("menu", core.ingest(NormalizedSignal.MenuSnapshot(screen.title.string, items), time))
    }
    private fun reset(reason: String, clearBosses: Boolean) {
        if (core.welcomeWasOffered()) welcomePending = false
        if (reason in setOf("disconnect", "profile_changed", "skyblock_left")) welcomePending = true
        if (reason in setOf("disconnect", "profile_changed")) liveEventNotices.clear()
        if (session != null) diagnostics.record("session", "leave", mapOf("session" to session, "reason" to reason))
        session = null; core.leaveSkyBlock(); petIcon = null; petAuthorityUntil = 0
        fishingBaitIcon = null
        lastActionBar = null; lastTab = emptyList(); lastView = null; lastProfile = null; lastMenuSignature = null
        lastActionPresenceAt = Long.MIN_VALUE
        lastSidebar = emptyList(); lastLocation = null; locationObservedAt = 0; lastContextRefresh = 0
        lastToolSignature = null; lastToolRefresh = Long.MIN_VALUE
        lastInventorySignature = null; lastInventoryRefresh = Long.MIN_VALUE
        PetMenuReader.reset()
        if (reason in setOf("profile_changed", "disconnect", "skyblock_left")) PetIconCache.clear()
        HeadIconResolver.reset(); skyBlockIcon = null; lastEventRefresh = 0
        clearNoticeIcon()
        if (clearBosses) bosses.clear()
    }
    private fun updateNoticeIcon(client: Minecraft, notice: TransientNotice?, activePet: PetSnapshot?) {
        val hint = notice?.item
        val key = notice?.let { "${it.id}:${it.observedAt}" }
        if (hint != lastNoticeHint || key != lastNoticeKey) {
            noticeIcon = null; noticeRarity = null
            lastNoticeHint = hint; lastNoticeKey = key; lastNoticeResolveAt = 0
        }
        val time = now()
        // Inventory packets may arrive after chat. Revalidate throughout this short notice,
        // including a formerly unique match becoming ambiguous when the new item arrives.
        if (hint != null && time - lastNoticeResolveAt >= 250) {
            lastNoticeResolveAt = time
            val resolved = NoticeItemResolver.resolveDetails(client, hint)
            val petHead = if (notice?.id?.startsWith("skyglint.pet_level_up:") == true && activePet?.name == hint.itemName)
                PetIconCache.resolve(activePet) else null
            noticeIcon = resolved?.icon ?: petHead; noticeRarity = resolved?.rarity
        }
    }
    private fun clearNoticeIcon() {
        noticeIcon = null; noticeRarity = null; lastNoticeHint = null; lastNoticeKey = null; lastNoticeResolveAt = 0
    }
    private fun demoSnapshot(): HudSnapshot = HudSnapshot(true, "SYNTHETIC_PREVIEW", if (previewScenario in setOf("idle", "event")) emptyList() else listOf(
        if (previewScenario == "objective") BossBarState(UUID(0, 1), "Craft a workbench", 1f, "YELLOW")
        else BossBarState(UUID(0, 1), "Magma Boss", .64f, "RED")),
        when (previewScenario) {
            "objective", "skyblock", "levelup", "idle", "event" -> ProgressionView.SkyBlock(287, ProgressSnapshot(88.0, 100.0, .88))
            "unknown" -> ProgressionView.SkyBlock(500, null)
            else -> ProgressionView.SkillView(Skill.MINING, null, ProgressSnapshot(null, null, previewFraction(.4839)), 1.1)
        },
        PetSnapshot("Seal", 70, ProgressSnapshot(null, null, previewFraction(.72))), null,
        activeNotice = when (previewScenario) {
            "levelup" -> TransientNotice("preview.level", "SkyBlock level up!", "Level 288", 100, previewStartedAt, previewStartedAt + 4000)
            "rare" -> TransientNotice("preview.rare", "Rare drop!", "Enchanted Book", 50, previewStartedAt, previewStartedAt + 4000,
                ItemVisualHint("Enchanted Book", "minecraft:enchanted_book"), magicFind = 123, rarity = "EPIC")
            else -> null
        }, location = if (previewScenario in setOf("idle", "event")) "Your Island" else null,
        nextEvent = if (previewScenario == "event") ScheduledEvent("Spooky Festival", previewStartedAt + 7_200_000,
            previewStartedAt, false, "synthetic_preview", previewStartedAt + 10_000_000) else null)
    private fun rankPreviewSnapshot() = HudSnapshot(true, "SYNTHETIC_RANKS", emptyList(), null, null, null,
        widgets = listOf("D" to 0, "C" to 100, "B" to 160, "A" to 230, "S" to 270, "S+" to 300).map { (rank, score) ->
            ModuleWidget("preview.rank.$rank", rank, presentation = WidgetPresentation(WidgetRole.COUNTERS,
                metrics = listOf(WidgetMetric("score", "Score", "$score · $rank", priority = 100))))
        })
    private fun previewFraction(start: Double): Double = if (previewScenario != "skill") start else
        start + ((now() - previewStartedAt).coerceAtLeast(0) % 7_000 / 700).toInt().coerceAtMost(2) * .01
    private fun feedback(message: String, tone: SkyglintChat.Tone = SkyglintChat.Tone.INFO) = SkyglintChat.send(message, tone)
    private fun command(action: () -> Unit): Int = try { action(); 1 } catch (e: Exception) {
        logger.warn("Skyglint command failed", e); feedback("Could not save: ${e.message}", SkyglintChat.Tone.ERROR); 0
    }
}
