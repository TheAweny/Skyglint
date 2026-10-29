package dev.skyglint.smoke;

import dev.skyglint.config.ConfigStore;
import dev.skyglint.fabric.BossPacketTracker;
import dev.skyglint.fabric.SkyglintClient;
import dev.skyglint.ui.SkyglintHud;
import dev.skyglint.ui.SkyglintSettingsScreen;
import dev.skyglint.ui.SkyglintUpdatesScreen;
import dev.skyglint.fabric.GitHubUpdates;
import dev.skyglint.fabric.SkyglintChat;
import dev.skyglint.updates.github.UpdateImportance;
import dev.skyglint.mixin.PlayerTabOverlayAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Local, offline dev-only mod. This source set is never included in the production JAR. */
public final class SmokeClient implements ClientModInitializer {
    private int ticks;
    private boolean started;
    private int captures;
    private int customRenders;
    private int failingCustomRenders;
    private int extensionTicks;
    private boolean extensionVerified;
    private static final String CUSTOM_WIDGET = "skyglint.smoke.extension.success";
    private static final String FAILING_WIDGET = "skyglint.smoke.extension.failure";
    private java.util.Set<java.nio.file.Path> reportsBefore = java.util.Set.of();
    private final String[] scenarios = {"skill", "skyblock", "unknown", "levelup", "ranks", "event", "winter", "reindrake", "buffs"};
    private final String[] settingsScenes = {"settings", "settings-editor", "settings-rewards", "update-important", "update-available", "update-ready", "settings-large", "editor-large", "update-important-large", "mining-large", "events-large", "updates-inline", "fishing-settings", "font-proof"};
    private String requestedSettingsPage;
    private int requestedSettingsAt;
    private Screen expectedWorkspace;
    private dev.skyglint.config.ModConfig editorOriginal;
    private int editorProbe;
    private final WorldSmoke worldSmoke = Boolean.getBoolean("skyglint.smoke.world") ? new WorldSmoke() : null;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (worldSmoke != null) { if (client.isGameLoadFinished() || started) { started = true; worldSmoke.tick(client); } return; }
            if (!client.isGameLoadFinished()) return;
            if (client.gui.overlay() != null) return; // Splash fade can outlive game-load completion.
            if (Boolean.getBoolean("skyglint.smoke.installed")) {
                if (!started) { started = true; verifyInstalledUpdate(client); }
                return;
            }
            if (!started) {
                started = true;
                verifyRuntime(client);
                var reports = client.gameDirectory.toPath().resolve("config/skyglint/reports");
                try {
                    if (java.nio.file.Files.isDirectory(reports)) try (var files = java.nio.file.Files.list(reports)) {
                        reportsBefore = files.collect(java.util.stream.Collectors.toSet());
                    }
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                SkyglintClient.INSTANCE.exportDiagnostics();
                prepareExtensionVerification();
                client.gui.setScreen(new HudProofScreen());
            }
            if (!extensionVerified) {
                verifyExtensionFrame(client);
                return;
            }
            ticks++;
            if(requestedSettingsPage!=null && ticks-requestedSettingsAt>=12) {
                clickSettingsPage(client,requestedSettingsPage);requestedSettingsPage=null;
            }
            if (captures == scenarios.length + 1) verifyEditor(client);
            if (ticks % 60 == 0 && captures < scenarios.length + settingsScenes.length) {
                if (captures>=scenarios.length) verifyWidgetBounds(client.gui.screen());
                String name = captures < scenarios.length ? scenarios[captures] : settingsScenes[captures-scenarios.length];
                if(name.equals("updates-inline")) {
                    if(client.gui.screen()!=expectedWorkspace)throw new AssertionError("Updates replaced the settings workspace instance");
                    if(client.gui.screen().children().stream().noneMatch(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget w &&
                        (w.getMessage().getString().startsWith("Download")||w.getMessage().getString().startsWith("Restart required"))))
                        throw new AssertionError("The inline Updates page did not become interactive");
                    LoggerFactory.getLogger("SkyglintSmoke").info("SKYGLINT_UNIFIED_UPDATES_PASSED: sidebar Updates keeps the same native workspace instance and navigation");
                }
                Screenshot.grab(client.gameDirectory, "skyglint-" + name + ".png", client.gameRenderer.mainRenderTarget(), 1,
                    message -> LoggerFactory.getLogger("SkyglintSmoke").info(message.getString()));
                captures++;
                if (captures < scenarios.length) SkyglintClient.INSTANCE.setPreviewScenario(scenarios[captures]);
                else if (captures == scenarios.length) client.gui.setScreen(new SkyglintSettingsScreen(null));
                else if (captures == scenarios.length + 1) {
                    editorOriginal = SkyglintClient.INSTANCE.getConfig().getCurrent();
                    client.gui.setScreen(new dev.skyglint.ui.SkyglintHudEditorScreen(null, dev.skyglint.fabric.ClientSettings.INSTANCE));
                }
                else if (captures == scenarios.length + 2) client.gui.setScreen(new dev.skyglint.ui.SkyglintRewardRulesScreen(null, dev.skyglint.fabric.ClientSettings.INSTANCE));
                else if (captures == scenarios.length + 3) client.gui.setScreen(new SkyglintUpdatesScreen(null,
                    new GitHubUpdates.View("0.2.2", GitHubUpdates.Phase.AVAILABLE, "0.2.3",
                        "Important compatibility fixes. Download when ready; a full restart applies the update.", true,
                        UpdateImportance.IMPORTANT)));
                else if (captures == scenarios.length + 4) client.gui.setScreen(new SkyglintUpdatesScreen(null,
                    new GitHubUpdates.View("0.2.0", GitHubUpdates.Phase.AVAILABLE, "0.2.1",
                        "A new version for Minecraft 26.2 is available. Choose Download below.", true,
                        UpdateImportance.CRITICAL)));
                else if (captures == scenarios.length + 5) client.gui.setScreen(new SkyglintUpdatesScreen(null,
                    new GitHubUpdates.View("0.2.0", GitHubUpdates.Phase.READY, "0.2.1",
                        "Update verified and installed. Restart Minecraft to load the new version.", true)));
                else if(captures==scenarios.length+6){
                    client.getWindow().setWindowed(1920,1080);client.options.guiScale().set(2);client.resizeGui();
                    client.gui.setScreen(new SkyglintSettingsScreen(null));
                }else if(captures==scenarios.length+7){
                    SkyglintClient.INSTANCE.setPreviewScenario("mining");
                    client.gui.setScreen(new dev.skyglint.ui.SkyglintHudEditorScreen(null,dev.skyglint.fabric.ClientSettings.INSTANCE));
                }else if(captures==scenarios.length+8){
                    client.getWindow().setWindowed(1920,1080);client.options.guiScale().set(2);client.resizeGui();
                    client.gui.setScreen(new SkyglintUpdatesScreen(null,
                    new GitHubUpdates.View("0.2.2",GitHubUpdates.Phase.AVAILABLE,"0.2.3",
                        "Important compatibility fixes for Minecraft 26.2.",true,UpdateImportance.IMPORTANT)));}
                else if(captures==scenarios.length+9)openSettingsPage(client,"Mining");
                else if(captures==scenarios.length+10)openSettingsPage(client,"Events");
                else if(captures==scenarios.length+11)openSettingsPage(client,"Updates");
                else if(captures==scenarios.length+12)openSettingsPage(client,"Fishing");
                else if(captures==scenarios.length+13)client.gui.setScreen(new SettingsFontProofScreen());
            }
            if (ticks >= (scenarios.length + settingsScenes.length) * 60 + 40) {
                try (var files = java.nio.file.Files.list(client.gameDirectory.toPath().resolve("config/skyglint/reports"))) {
                    var archive = files.filter(path -> path.toString().endsWith(".zip") && !reportsBefore.contains(path)).findFirst().orElseThrow();
                    try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
                        var entry = zip.getEntry("skyglint/skyglint-events.jsonl");
                        if (entry == null || !new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("Unknown hidden server fixture"))
                            throw new AssertionError("Background exported report lost captured chat");
                    }
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                SkyglintClient.INSTANCE.setPreview(false);
                SkyglintClient.INSTANCE.getDiagnostics().flush();
                LoggerFactory.getLogger("SkyglintSmoke").info("REBORN_SMOKE_PASSED: runtime Mixin packet lifecycle, config reload, full/cancelled chat, background ZIP export, contributor renderer/quarantine fallback, rank sprites, multi-day event and other HUD states, settings");
                client.stop();
            }
        });
    }

    private void openSettingsPage(Minecraft client,String title){
        client.getWindow().setWindowed(1920,1080);client.options.guiScale().set(2);client.resizeGui();
        var screen=new SkyglintSettingsScreen(null);client.gui.setScreen(screen);expectedWorkspace=screen;
        requestedSettingsPage=title;requestedSettingsAt=ticks;
    }
    private void clickSettingsPage(Minecraft client,String title){
        var screen=client.gui.screen();
        var widget=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream()
            .filter(child->child instanceof net.minecraft.client.gui.components.AbstractWidget w &&
                (w.getMessage().getString().equals(title)||w.getMessage().getString().startsWith(title+":")))
            .findFirst().orElseThrow();
        var event=new net.minecraft.client.input.MouseButtonEvent(widget.getX()+10,widget.getY()+10,new net.minecraft.client.input.MouseButtonInfo(0,0));
        screen.mouseClicked(event,false);screen.mouseReleased(event);
    }

    private void verifyWidgetBounds(Screen screen){
        if(screen==null)throw new AssertionError("Missing native settings screen");
        for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget){
            if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()+widget.getHeight()>screen.height)
                throw new AssertionError("Native control outside viewport: "+widget.getMessage().getString());
        }
    }

    private void verifyEditor(Minecraft client) {
        if (!(client.gui.screen() instanceof dev.skyglint.ui.SkyglintHudEditorScreen editor)) return;
        int age = ticks % 60;
        if (age == 12 && editorProbe == 0) {
            var slider = (net.minecraft.client.gui.components.AbstractSliderButton) editor.children().stream()
                .filter(widget -> widget instanceof net.minecraft.client.gui.components.AbstractSliderButton).findFirst().orElseThrow();
            var click = new net.minecraft.client.input.MouseButtonEvent(slider.getX()+slider.getWidth()-8, slider.getY()+25,
                new net.minecraft.client.input.MouseButtonInfo(0,0));
            editor.mouseClicked(click,false); editor.mouseReleased(click);
            if (SkyglintHud.INSTANCE.getEditorPlacement().getScale() < 1.9) throw new AssertionError("Native editor scale slider did not change the draft");
            if (!editorOriginal.equals(SkyglintClient.INSTANCE.getConfig().getCurrent())) throw new AssertionError("Scale drag wrote config before Apply");
            editorProbe=1;
        } else if (age == 22 && editorProbe == 1) {
            var b=SkyglintHud.INSTANCE.getEditorBounds();
            if (b==null)throw new AssertionError("Real HUD did not publish editor bounds");
            var start=new net.minecraft.client.input.MouseButtonEvent(b.getX()+8,b.getY()+8,new net.minecraft.client.input.MouseButtonInfo(0,0));
            var end=new net.minecraft.client.input.MouseButtonEvent(b.getX()+8,b.getY()+48,new net.minecraft.client.input.MouseButtonInfo(0,0));
            editor.mouseClicked(start,false);editor.mouseDragged(end,0,40);editor.mouseReleased(end);
            if(SkyglintHud.INSTANCE.getEditorPlacement().getPositionY()==null)throw new AssertionError("Native HUD row did not drag");
            if(!editorOriginal.equals(SkyglintClient.INSTANCE.getConfig().getCurrent()))throw new AssertionError("Row drag wrote config before Apply");
            editorProbe=2;
        } else if(age==32&&editorProbe==2){
            editor.onClose();
            if(SkyglintHud.INSTANCE.getEditorPlacement()!=null||!editorOriginal.equals(SkyglintClient.INSTANCE.getConfig().getCurrent()))throw new AssertionError("Cancel did not discard editor draft");
            client.gui.setScreen(new dev.skyglint.ui.SkyglintHudEditorScreen(null,dev.skyglint.fabric.ClientSettings.INSTANCE));
            LoggerFactory.getLogger("SkyglintSmoke").info("SKYGLINT_NATIVE_EDITOR_PASSED: real HUD, native slider/drag, no write before Apply, cancel restores saved preferences");
            editorProbe=3;
        }
    }

    /** Exercise sourceWidget -> HudComposition -> SkyglintHud -> registered renderer, not a direct registry call. */
    private void prepareExtensionVerification() {
        dev.skyglint.ui.UiWidgetRegistry.INSTANCE.register(CUSTOM_WIDGET, (graphics, widget, x, y, width, height) -> {
            if (!widget.getId().equals(CUSTOM_WIDGET) || width < 1 || height != 30)
                throw new AssertionError("Contributor renderer received the wrong source widget or bounds");
            customRenders++;
            graphics.text(Minecraft.getInstance().font, Component.literal("Contributor render OK"), x + 10, y + 10, 0xFF93E4B0, false);
        });
        dev.skyglint.ui.UiWidgetRegistry.INSTANCE.register(FAILING_WIDGET, (graphics, widget, x, y, width, height) -> {
            failingCustomRenders++;
            LoggerFactory.getLogger("SkyglintSmoke").info("REBORN_WIDGET_FAILURE_EXPECTED: deliberately testing contributor quarantine and generic fallback");
            throw new IllegalStateException("Intentional smoke contributor failure");
        });
        SkyglintClient.INSTANCE.setEnabled(true);
        SkyglintClient.INSTANCE.setPreview(false);
        var core = SkyglintClient.INSTANCE.getCore();
        long now = SkyglintClient.INSTANCE.now();
        core.enterSkyBlock("SMOKE_EXTENSION", now);
        core.apply(java.util.List.of(new dev.skyglint.core.CoreEvent.WidgetObserved(
            new dev.skyglint.core.ModuleWidget(CUSTOM_WIDGET, "Contributor render OK", null, null, 20, null, null, java.util.Set.of()))), now);
    }

    private void verifyExtensionFrame(Minecraft client) {
        extensionTicks++;
        var core = SkyglintClient.INSTANCE.getCore();
        if (extensionTicks == 20) {
            if (customRenders < 1) throw new AssertionError("SkyglintHud never invoked the registered contributor renderer");
            core.apply(java.util.List.of(new dev.skyglint.core.CoreEvent.WidgetRemoved(CUSTOM_WIDGET),
                new dev.skyglint.core.CoreEvent.WidgetObserved(new dev.skyglint.core.ModuleWidget(
                    FAILING_WIDGET, "Contributor fallback OK", null, null, 20, null, null, java.util.Set.of()))), SkyglintClient.INSTANCE.now());
        }
        if (extensionTicks == 40) {
            if (failingCustomRenders != 1) throw new AssertionError("Broken contributor was not attempted exactly once");
            if (dev.skyglint.ui.UiWidgetRegistry.INSTANCE.find(FAILING_WIDGET) != null)
                throw new AssertionError("Broken contributor renderer was not quarantined");
            var visible = new dev.skyglint.ui.presentation.HudComposition().compose(core.snapshot(SkyglintClient.INSTANCE.now()),
                SkyglintClient.INSTANCE.now(), true, false);
            if (visible.size() != 1 || visible.getFirst().getSourceWidget() == null ||
                !visible.getFirst().getTitle().equals("Contributor fallback OK"))
                throw new AssertionError("Failed contributor lost its generic fallback payload");
            Screenshot.grab(client.gameDirectory, "skyglint-contributor-fallback.png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
            core.leaveSkyBlock();
            SkyglintClient.INSTANCE.setPreviewScenario(scenarios[0]);
            extensionVerified = true;
            LoggerFactory.getLogger("SkyglintSmoke").info("REBORN_WIDGET_EXTENSION_PASSED: actual HUD invoked contributor renderer; failing renderer quarantined once with retained generic fallback");
        }
    }

    private static void verifyInstalledUpdate(Minecraft client) {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isDevelopmentEnvironment()) throw new AssertionError("Installed update test accidentally uses dev mode");
        var selected = loader.getModContainer("reborn").orElseThrow();
        if (!selected.getMetadata().getVersion().getFriendlyString().equals(System.getProperty("skyglint.smoke.expectedVersion")))
            throw new AssertionError("Fabric did not select the latest compatible Skyglint");
        var root = client.gameDirectory.toPath();
        var backup = loader.getConfigDir().resolve("skyglint/backups/mods");
        int archivedCount;
        try {
            var sha256 = java.security.MessageDigest.getInstance("SHA-256");
            var old = new java.util.Properties();
            try (var input = java.nio.file.Files.newInputStream(root.resolve("expected-old-jars.properties"))) { old.load(input); }
            if (old.size() != 14) throw new AssertionError("Installed update smoke did not seed every older release/prerelease");
            java.util.List<java.nio.file.Path> archives;
            try (var files = java.nio.file.Files.list(backup)) { archives = files.toList(); }
            for (String relative : old.stringPropertyNames()) {
                var source = root.resolve(relative);
                if (java.nio.file.Files.exists(source)) throw new AssertionError("Old installed JAR remains in mods: " + relative);
                boolean foundIntact = false;
                for (var archive : archives) {
                    var filename = archive.getFileName().toString();
                    if (!filename.startsWith(source.getFileName() + ".") || !filename.endsWith(".bak")) continue;
                    var actual = java.util.HexFormat.of().formatHex(sha256.digest(java.nio.file.Files.readAllBytes(archive)));
                    if (actual.equals(old.getProperty(relative))) foundIntact = true;
                }
                if (!foundIntact) throw new AssertionError("Old JAR lacks a byte-identical backup: " + relative);
            }
            archivedCount = old.size();
            var preserved = new java.util.Properties();
            try (var input = java.nio.file.Files.newInputStream(root.resolve("expected-preserved-files.properties"))) { preserved.load(input); }
            for (String relative : preserved.stringPropertyNames()) {
                var actual = java.util.HexFormat.of().formatHex(sha256.digest(java.nio.file.Files.readAllBytes(root.resolve(relative))));
                if (!actual.equals(preserved.getProperty(relative))) throw new AssertionError("Cleanup changed protected file: " + relative);
            }
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
        LoggerFactory.getLogger("SkyglintSmoke").info("REBORN_INSTALLED_UPDATE_PASSED: production Fabric selected current JAR; all {} older releases/prerelease archived byte-identically; incompatible/equal/future/other mod/config content/existing backup preserved", archivedCount);
        client.stop();
    }

    private static void verifyChatFormatting() {
        var update = SkyglintChat.INSTANCE.updateAvailable("0.2.3");
        if (!update.getString().equals("[Skyglint] Update available 0.2.3  [View update]"))
            throw new AssertionError("Update announcement is not concise English");
        if (update.getStyle().getClickEvent() != null)
            throw new AssertionError("The chat prefix/body inherited the update action");
        int actions = 0;
        for (var part : update.getSiblings()) {
            if (part.getStyle().getColor() == null || part.getStyle().getColor().getValue() == 0xFFFFFF)
                throw new AssertionError("Chat component lost its explicit restrained color");
            var click = part.getStyle().getClickEvent();
            if (click != null) {
                actions++;
                if (!part.getString().equals("[View update]") ||
                    !(click instanceof net.minecraft.network.chat.ClickEvent.RunCommand run) ||
                    !run.command().equals("/skyglint update") || part.getStyle().getHoverEvent() == null)
                    throw new AssertionError("Only the update action may run its fixed reviewed command");
            }
        }
        if (actions != 1) throw new AssertionError("Update message must have exactly one action");
        for (var importance : new UpdateImportance[] { UpdateImportance.IMPORTANT, UpdateImportance.CRITICAL }) {
            var emphasized = SkyglintChat.INSTANCE.updateAvailable("0.2.3", importance);
            String heading = importance == UpdateImportance.IMPORTANT ? "Important update available " : "Critical update available ";
            int expectedColor = importance == UpdateImportance.IMPORTANT ? SkyglintChat.Tone.WARNING.getColor() : SkyglintChat.Tone.ERROR.getColor();
            if (!emphasized.getString().equals("[Skyglint] " + heading + "0.2.3  [View update]") ||
                emphasized.getStyle().getClickEvent() != null)
                throw new AssertionError("Release importance lost its text or isolated action");
            int emphasizedActions = 0;
            boolean headingFound = false;
            for (var part : emphasized.getSiblings()) {
                if (part.getString().equals(heading)) {
                    headingFound = true;
                    if (part.getStyle().getColor() == null || part.getStyle().getColor().getValue() != expectedColor)
                        throw new AssertionError("Release importance lost its warning/error color");
                }
                var click = part.getStyle().getClickEvent();
                if (click != null) {
                    emphasizedActions++;
                    if (!part.getString().equals("[View update]") ||
                        !(click instanceof net.minecraft.network.chat.ClickEvent.RunCommand run) ||
                        !run.command().equals("/skyglint update") || part.getStyle().getHoverEvent() == null)
                        throw new AssertionError("Important/critical release gained an unexpected action");
                }
            }
            if (!headingFound || emphasizedActions != 1)
                throw new AssertionError("Important/critical release must have one heading and one fixed action");
        }
        var success = SkyglintChat.INSTANCE.message("Saved.", SkyglintChat.Tone.SUCCESS);
        var error = SkyglintChat.INSTANCE.message("Could not save.", SkyglintChat.Tone.ERROR);
        var successBody = success.getSiblings().getLast().getStyle();
        var errorBody = error.getSiblings().getLast().getStyle();
        if (successBody.getColor().equals(errorBody.getColor()) || successBody.getClickEvent() != null || errorBody.getClickEvent() != null)
            throw new AssertionError("Semantic chat tones or action isolation were lost");
    }

    private static void verifyBossPointColors() {
        int[] callbacks = {0};
        var tracker = new BossPacketTracker((action, id, details) -> { callbacks[0]++; return kotlin.Unit.INSTANCE; });
        var id = new UUID(0, 9002);
        var title = Component.empty().append(Component.literal("Ⓐ ").withStyle(net.minecraft.ChatFormatting.GREEN))
                .append(Component.literal("Ⓑ Ⓒ Ⓓ Ⓔ").withStyle(net.minecraft.ChatFormatting.YELLOW));
        tracker.add(id, title, .8f, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS, false, false, false);
        var points = dev.skyglint.features.winter.JerryGiftPilePatterns.INSTANCE.points(tracker.getStates().get(id).getFormattedTitle());
        if (points == null || points.getFirst().getColor() != 0xFF55FF55 || points.getLast().getColor() != 0xFFFFFF55)
            throw new AssertionError("Boss Component style runs lost gift point colors");
        var changed = Component.empty().append(Component.literal("Ⓐ ").withStyle(net.minecraft.ChatFormatting.RED))
                .append(Component.literal("Ⓑ Ⓒ Ⓓ Ⓔ").withStyle(net.minecraft.ChatFormatting.YELLOW));
        tracker.updateName(id, changed);
        if (callbacks[0] != 2 || !tracker.getStates().get(id).getFormattedTitle().contains("§cⒶ"))
            throw new AssertionError("Color-only boss name update was incorrectly deduplicated");
        tracker.updateName(id, changed);
        if (callbacks[0] != 2) throw new AssertionError("Identical styled boss title was not deduplicated");
        LoggerFactory.getLogger("SkyglintSmoke").info("SKYGLINT_BOSS_POINT_COLORS_PASSED: Component color runs and color-only updates preserved");
    }

    private static void verifyRuntime(Minecraft client) {
        verifyBossPointColors();
        verifyChatFormatting();
        try {
            if (PlayerTabOverlayAccessor.skyglintComparator() == null) throw new AssertionError("Vanilla tab comparator accessor missing");
            var tab = (PlayerTabOverlayAccessor) client.gui.hud.getTabList();
            client.gui.hud.getTabList().setHeader(Component.literal("Event: Spooky Festival"));
            client.gui.hud.getTabList().setFooter(Component.literal("Starts In: 7h"));
            if (!tab.skyglintHeader().getString().equals("Event: Spooky Festival") || !tab.skyglintFooter().getString().equals("Starts In: 7h"))
                throw new AssertionError("Tab header/footer source accessors missing");
            var field = SkyglintClient.class.getDeclaredField("bosses");
            field.setAccessible(true);
            BossPacketTracker tracker = (BossPacketTracker) field.get(null);
            var overlay = client.gui.hud.getBossOverlay();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            ServerBossEvent boss = new ServerBossEvent(first, Component.literal("Unrecognized test bar"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            boss.setProgress(.75f);
            overlay.update(ClientboundBossEventPacket.createAddPacket(boss));
            if (!tracker.getStates().containsKey(first)) throw new AssertionError("Mixin packet observer was not applied");
            var duplicate = new ServerBossEvent(second, boss.getName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
            overlay.update(ClientboundBossEventPacket.createAddPacket(duplicate));
            if (tracker.getStates().size() != 2) throw new AssertionError("Same-name UUID bars were merged");
            boss.setProgress(.25f);
            overlay.update(ClientboundBossEventPacket.createUpdateProgressPacket(boss));
            if (tracker.getStates().get(first).getProgress() != .25f) throw new AssertionError("Progress not updated");
            boss.setName(Component.literal("Updated unknown bar"));
            overlay.update(ClientboundBossEventPacket.createUpdateNamePacket(boss));
            if (!tracker.getStates().get(first).getTitle().equals("Updated unknown bar")) throw new AssertionError("Name not updated");
            overlay.update(ClientboundBossEventPacket.createRemovePacket(first));
            if (tracker.getStates().containsKey(first) || !tracker.getStates().containsKey(second)) throw new AssertionError("UUID removal not isolated");
            overlay.reset();
            if (!tracker.getStates().isEmpty()) throw new AssertionError("Reset left stale bars");
            if (SkyglintClient.shouldReplaceBossBars()) throw new AssertionError("Vanilla hidden outside SkyBlock");
            SkyglintClient.INSTANCE.setLogging(true);
            ConfigStore reloaded = new ConfigStore(SkyglintClient.INSTANCE.getConfig().getPath());
            if (!reloaded.load().getLoggingEnabled()) throw new AssertionError("Logging not persisted");
            SkyglintClient.INSTANCE.setDiagnostics(true);
            var captureConfig = new ConfigStore(SkyglintClient.INSTANCE.getConfig().getPath()).load();
            if (!captureConfig.getCaptureChat() || !captureConfig.getCaptureRawText() || !captureConfig.getLoggingEnabled()) throw new AssertionError("One-command full diagnostics not persisted");
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME_CANCELED.invoker()
                .onReceiveGameMessageCanceled(Component.literal("Unknown hidden server fixture"), false);
            SkyglintClient.INSTANCE.getDiagnostics().flush();
            try {
                if (!java.nio.file.Files.readString(SkyglintClient.INSTANCE.getDiagnostics().getPath()).contains("Unknown hidden server fixture"))
                    throw new AssertionError("Cancelled system message was not captured outside a SkyBlock session");
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            SkyglintClient.INSTANCE.setChatLogging(false);
            SkyglintClient.INSTANCE.changeConfig(config -> config.copy(config.getSchemaVersion(), config.getEnabled(),
                config.getLoggingEnabled(), config.getCaptureRawText(), config.getSkillTimeoutSeconds(),
                config.getHudScale(), config.getTopOffset(), config.getShowPets(), "filtered", java.util.Map.of("Summoning Eye", "show"), config.getCaptureChat(), config.getDetailedDungeonHud(), config.getCheckUpdates(), config.getHudOffsetX(), config.getHudPositionY(),
                config.getShowWelcome(), config.getShowEventReminders(), config.getShowEventStarts(),
                config.getShowSkillLevelUps(), config.getShowPetLevelUps(), config.getShowSkyBlockLevelUps(),
                config.getShowGardenVisitors(), config.getShowGardenPests(), config.getShowCropMilestones(),
                config.getShowMiningTimers(), config.getShowFuelWarnings(), config.getShowPetIntroduction(),
                config.getShowFishingBait(), config.getShowSackWarning(), config.getRotationSeconds(), config.getHelperLimit(),
                config.getShowCookieExpiry(), config.getShowGodPotionExpiry(),
                config.getShowJerryDefense(), config.getShowReindrake(),
                config.getShowCookieWarning(), config.getShowGodPotionWarning(), config.getShowJerryResults()));
            var saved = new ConfigStore(SkyglintClient.INSTANCE.getConfig().getPath()).load();
            if (!"show".equals(saved.getRewardOverrides().get("summoning eye"))) throw new AssertionError("Drop rule not persisted");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class HudProofScreen extends Screen {
        HudProofScreen() { super(Component.literal("Skyglint HUD proof")); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xFF303B35);
            graphics.centeredText(font, "Offline render verification · synthetic inputs", width / 2, height - 30, 0xFFD7E2DB);
            graphics.text(font, SkyglintChat.INSTANCE.updateAvailable("0.2.3", UpdateImportance.IMPORTANT), 18, height - 104, -1, false);
            graphics.text(font, SkyglintChat.INSTANCE.updateAvailable("0.2.3", UpdateImportance.CRITICAL), 18, height - 90, -1, false);
            graphics.text(font, SkyglintChat.INSTANCE.updateAvailable("0.2.3"), 18, height - 76, -1, false);
            graphics.text(font, SkyglintChat.INSTANCE.message("Preferences saved.", SkyglintChat.Tone.SUCCESS), 18, height - 62, -1, false);
            SkyglintHud.INSTANCE.render(graphics);
        }
    }
}
