package dev.skyglint.smoke;

import dev.skyglint.fabric.SkyglintClient;
import dev.skyglint.ui.effects.HudBackdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.slf4j.LoggerFactory;

/** Actual local world, generated only in run-smoke; no accounts or remote server connection. */
final class WorldSmoke {
    private static final String WORLD = "Skyglint Visual QA";
    private static final String[] SCENARIOS = {"objective", "skill", "skyblock", "levelup", "rare", "unknown", "idle", "event", "petlevelup", "skilllevelup", "election", "ranks"};
    private boolean opening;
    private boolean creating;
    private boolean prepared;
    private int ticks;
    private int phase;
    private int totalWait;
    private int sequenceTicks = -1;
    private int eventTicks = -1;
    private int contextTicks = -1;
    private int contextPhase;
    private long confirmationStartedAt = -1;
    private int confirmationCapture;
    private long slayerPetStartedAt = -1;
    private int slayerPetCapture;
    private long fishingStartedAt = -1;
    private int fishingCapture;
    private static final long[] FISHING_AT = {1200, 2400, 4400, 7400, 10400, 13400, 16400, 19000, 21400, 25400, 29400, 33400, 37400};
    private static final String[] FISHING_LABELS = {"healthy", "selected-icon", "low", "empty", "unknown", "refill", "sack-disabled", "sack-enabled",
        "rare-double-hook", "junk-rusty-coin", "junk-leather-boot", "trophy-diamond", "shard-three"};
    private static final long[] SLAYER_PET_AT = {2700, 4700, 6600, 9000, 11500, 14500, 18500, 23000, 25700, 30100, 32700, 35700};
    private static final String[] SLAYER_PET_LABELS = {"welcome-level", "welcome-pet", "welcome-location", "quest-xp",
        "quest-progress", "boss-fight", "boss-slain", "manual-expanded", "manual-circle", "autopet-expanded", "autopet-circle", "settled"};
    private static final long[] CONFIRMATION_AT = {1200, 5000, 7200, 10800, 13800, 18800, 23800, 27000};
    private static final String[] CONFIRMATION_LABELS = {"closing-intro", "closing-above-minute", "closing-urgent-intro",
        "closing-remaining", "trevor-completed", "trevor-timeout", "bestiary-complete", "ordinary-return"};
    private java.util.concurrent.CompletableFuture<Void> reload;
    private int resizeWait;
    private Screen confirmedScreen;

    void tick(Minecraft client) {
        if (totalWait % 100 == 0) LoggerFactory.getLogger("SkyglintWorldSmoke").info("World stage screen={} level={} creating={} ticks={}",
            client.gui.screen() == null ? "null" : client.gui.screen().getClass().getSimpleName(), client.level != null, creating, ticks);
        if (++totalWait > 20 * 300) throw new AssertionError("Test world did not complete within five minutes");
        if (!opening) {
            opening = true;
            client.options.pauseOnLostFocus = false;
            client.getWindow().setWindowed(1920, 1080);
            client.options.guiScale().set(2);
            client.resizeGui();
            if (client.gameDirectory.toPath().resolve("saves").resolve(WORLD).toFile().isDirectory()) {
                client.createWorldOpenFlows().openWorld(WORLD, () -> {});
            } else CreateWorldScreen.testWorld(client, () -> {});
            return;
        }
        if (!creating && client.gui.screen() instanceof CreateWorldScreen screen) {
            creating = true;
            screen.getUiState().setName(WORLD);
            screen.getUiState().setAllowCommands(true);
            screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            try {
                var create = CreateWorldScreen.class.getDeclaredMethod("onCreate");
                create.setAccessible(true);
                create.invoke(screen);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            return;
        }
        if (creating && client.level == null && client.gui.screen() instanceof ConfirmScreen screen) {
            if (confirmedScreen == screen) return;
            confirmedScreen = screen;
            // The dedicated vanilla test preset warns about experimental world settings.
            // This branch exists only in our offline smoke source set and cannot affect user worlds.
            try {
                var field = ConfirmScreen.class.getDeclaredField("callback");
                field.setAccessible(true);
                ((it.unimi.dsi.fastutil.booleans.BooleanConsumer) field.get(screen)).accept(true);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            return;
        }
        if (client.level == null && client.gui.screen() instanceof BackupConfirmScreen screen) {
            if (confirmedScreen == screen) return;
            confirmedScreen = screen;
            try {
                var field = BackupConfirmScreen.class.getDeclaredField("onProceed");
                field.setAccessible(true);
                ((BackupConfirmScreen.Listener) field.get(screen)).proceed(false, false);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            return;
        }
        if (client.level == null || client.player == null || client.getConnection() == null) return;
        if (!prepared) {
            if (++ticks < 60) return;
            prepared = true;
            verifyBossTracker();
            verifyPlayerHead(client);
            verifyHeldDrillFuel(client);
            verifyElectionHeads();
            verifyKeeperInventory(client);
            verifyFishingBaitPreview(client);
            ticks = 0;
            client.gui.setScreen(null);
            for (String command : new String[] {
                "time set day", "weather clear", "fill -24 62 -24 24 62 24 stone_bricks",
                "fill -16 63 -8 16 80 -8 white_concrete", "fill -16 63 -9 16 80 -9 stone",
                "fill -14 63 -7 -13 80 -7 dark_oak_planks", "fill -10 63 -7 -9 80 -7 oak_leaves",
                "fill -6 63 -7 -5 80 -7 cyan_concrete", "fill -2 63 -7 -1 80 -7 dark_oak_planks",
                "fill 2 63 -7 3 80 -7 oak_leaves", "fill 6 63 -7 7 80 -7 cyan_concrete",
                "fill 10 63 -7 11 80 -7 dark_oak_planks", "fill 14 63 -7 15 80 -7 oak_leaves",
                "setblock -3 63 2 chest", "setblock 3 63 2 enchanting_table",
                "tp @s 0 63 12 180 15", "give @s enchanted_book", "give @s diamond_pickaxe",
            }) client.getConnection().sendCommand(command);
            if (Boolean.getBoolean("skyglint.smoke.fishingOnly")) {
                SkyglintClient.INSTANCE.setPreviewScenario("fishing");
                fishingStartedAt = SkyglintClient.INSTANCE.now();
            } else if (Boolean.getBoolean("skyglint.smoke.slayerPetsOnly")) {
                SkyglintClient.INSTANCE.setPreviewScenario("slayerpets");
                slayerPetStartedAt = SkyglintClient.INSTANCE.now();
            } else if (Boolean.getBoolean("skyglint.smoke.confirmationsOnly")) {
                SkyglintClient.INSTANCE.setPreviewScenario("confirmations");
                confirmationStartedAt = SkyglintClient.INSTANCE.now();
            } else SkyglintClient.INSTANCE.setPreviewScenario(SCENARIOS[0]);
            return;
        }
        ticks++;
        if (fishingStartedAt >= 0) {
            long elapsed = SkyglintClient.INSTANCE.now() - fishingStartedAt;
            if (fishingCapture < FISHING_AT.length && elapsed >= FISHING_AT[fishingCapture]) {
                String label = FISHING_LABELS[fishingCapture++];
                verifyFishingScene(label);
                Screenshot.grab(client.gameDirectory, "fishing-" + label + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("Synthetic fishing capture {} at {}ms", label, elapsed);
            }
            if (elapsed >= 39000) {
                if (fishingCapture != FISHING_AT.length) throw new AssertionError("Missing fishing phase captures");
                if (HudBackdrop.captureCount() < 10 || HudBackdrop.debugStatus().contains("disabled=true"))
                    throw new AssertionError("Fishing scene framebuffer blur did not remain active");
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_FISHING_SMOKE_PASSED: isolated native fishing XP, selected bait introduction/icon, low/empty/unknown/refill, sack confirmation, rare catch, Junk, trophy grade, shard amount; thirteen captures, {}", HudBackdrop.debugStatus());
                client.stop();
            }
            return;
        }
        if (slayerPetStartedAt >= 0) {
            long elapsed = SkyglintClient.INSTANCE.now() - slayerPetStartedAt;
            if (slayerPetCapture < SLAYER_PET_AT.length && elapsed >= SLAYER_PET_AT[slayerPetCapture]) {
                String label = SLAYER_PET_LABELS[slayerPetCapture++];
                verifySlayerPetScene(label);
                Screenshot.grab(client.gameDirectory, "slayer-pet-" + label + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("Synthetic Slayer/pet capture {} at {}ms", label, elapsed);
            }
            if (elapsed >= 38500) {
                if (slayerPetCapture != SLAYER_PET_AT.length) throw new AssertionError("Missing Slayer/pet phase captures");
                if (HudBackdrop.captureCount() < 10 || HudBackdrop.debugStatus().contains("disabled=true"))
                    throw new AssertionError("Slayer/pet scene framebuffer blur did not remain active");
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_SLAYER_PET_SMOKE_PASSED: isolated native welcome carousel, Slayer quest/fight/slain, manual and Autopet expansion/circle, twelve captures, {}", HudBackdrop.debugStatus());
                client.stop();
            }
            return;
        }
        if (confirmationStartedAt >= 0) {
            // New captures follow the timeline's monotonic clock; slow rendering must not miss a phase.
            long elapsed = SkyglintClient.INSTANCE.now() - confirmationStartedAt;
            if (confirmationCapture < CONFIRMATION_AT.length && elapsed >= CONFIRMATION_AT[confirmationCapture]) {
                String label = CONFIRMATION_LABELS[confirmationCapture++];
                Screenshot.grab(client.gameDirectory, "confirmation-" + label + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("Synthetic confirmation capture {} at {}ms", label, elapsed);
            }
            if (elapsed >= 29000) {
                if (Boolean.getBoolean("skyglint.smoke.confirmationsOnly")) {
                    if (HudBackdrop.captureCount() < 10 || HudBackdrop.debugStatus().contains("disabled=true"))
                        throw new AssertionError("Confirmation scene framebuffer blur did not remain active");
                    LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_CONFIRMATION_SMOKE_PASSED: isolated native closing/Trevor/Bestiary scene, eight captures, {}", HudBackdrop.debugStatus());
                    client.stop();
                    return;
                }
                confirmationStartedAt = -1; sequenceTicks = 0;
                SkyglintClient.INSTANCE.setPreviewScenario("sequence");
            }
            return;
        }
        if (contextTicks >= 0) {
            contextTicks++;
            if (contextPhase == 1 && contextTicks == 400) SkyglintClient.INSTANCE.setDetailedDungeonHud(true);
            if (contextTicks == 20 || contextTicks == 100 || contextTicks == 180 || contextTicks == 200 ||
                contextTicks == 240 || contextTicks == 260 || contextTicks == 280 || contextTicks == 340 || contextTicks == 420 ||
                contextTicks == 490 || contextTicks == 540)
                Screenshot.grab(client.gameDirectory, (contextPhase == 0 ? "garden-" : contextPhase == 1 ? "dungeon-" : contextPhase == 2 ? "mining-" : "nucleus-") + contextTicks + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
            if (contextTicks == (contextPhase >= 2 ? 580 : 440)) {
                if (contextPhase++ == 0) { contextTicks = 0; SkyglintClient.INSTANCE.setPreviewScenario("dungeon"); }
                else if (contextPhase == 2) { contextTicks = 0; SkyglintClient.INSTANCE.setDetailedDungeonHud(false); SkyglintClient.INSTANCE.setPreviewScenario("mining"); }
                else if (contextPhase == 3) { contextTicks = 0; SkyglintClient.INSTANCE.setPreviewScenario("nucleus"); }
                else {
                    SkyglintClient.INSTANCE.setDetailedDungeonHud(false); contextTicks = -1;
                    SkyglintClient.INSTANCE.setPreviewScenario("confirmations");
                    confirmationStartedAt = SkyglintClient.INSTANCE.now();
                    confirmationCapture = 0;
                }
            }
            return;
        }
        if (eventTicks >= 0) {
            eventTicks++;
            if (eventTicks == 20 || eventTicks == 100 || eventTicks == 160 || eventTicks == 240 || eventTicks == 320 || eventTicks == 400)
                Screenshot.grab(client.gameDirectory, "event-" + eventTicks + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
            if (eventTicks == 420) {
                eventTicks = -1; contextTicks = 0; contextPhase = 0;
                SkyglintClient.INSTANCE.setPreviewScenario("garden");
            }
            return;
        }
        if (sequenceTicks >= 0) {
            sequenceTicks++;
            if (sequenceTicks == 12 || sequenceTicks == 50 || sequenceTicks == 75 || sequenceTicks == 145 || sequenceTicks == 230 || sequenceTicks == 300) {
                Screenshot.grab(client.gameDirectory, "sequence-" + String.format("%03d", sequenceTicks) + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
            }
            if (sequenceTicks == 310) reload = client.reloadResourcePacks();
            if (reload != null && reload.isDone() && resizeWait == 0) {
                if (reload.isCompletedExceptionally()) throw new AssertionError("Resource reload failed");
                client.getWindow().setWindowed(1280, 720);
                client.resizeGui();
                resizeWait = 1;
            } else if (resizeWait > 0 && ++resizeWait >= 60) {
                Screenshot.grab(client.gameDirectory, "world-reload-resized.png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
                if (HudBackdrop.captureCount() < 10 || HudBackdrop.debugStatus().contains("disabled=true")) throw new AssertionError("Real framebuffer blur did not remain active");
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("Backdrop verification after resource reload/resize: {}", HudBackdrop.debugStatus());
                SkyglintClient.INSTANCE.setPreview(false);
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_WORLD_SMOKE_PASSED: real local world, GUI 1/2/3, synthetic closing/Trevor/Bestiary confirmations, priority sequence, resource reload and framebuffer resize");
                client.stop();
            }
            return;
        }
        int motionTick = ticks % 90;
        if ((phase == 1 && (motionTick == 20 || motionTick == 35 || motionTick == 50 || motionTick == 52 || motionTick == 54 || motionTick == 57 || motionTick == 62)) ||
            ((phase == 3 || phase == 8 || phase == 9) && (motionTick == 20 || motionTick == 50))) {
            Screenshot.grab(client.gameDirectory, "detail-" + SCENARIOS[phase] + "-" + motionTick + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
        }
        if (phase == 1 && (motionTick == 1 || motionTick == 3 || motionTick == 6 || motionTick == 10 || motionTick == 16)) {
            Screenshot.grab(client.gameDirectory, "motion-skill-" + String.format("%02d", motionTick) + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
        }
        // Capture settled states and then compare identical HUD at vanilla GUI 1/2/3.
        if (ticks >= 90 && ticks % 90 == 0) {
            String label;
            if (phase < SCENARIOS.length) label = SCENARIOS[phase];
            else label = "gui" + (phase - SCENARIOS.length + 1);
            Screenshot.grab(client.gameDirectory, "world-" + label + ".png", client.gameRenderer.mainRenderTarget(), 1,
                text -> LoggerFactory.getLogger("SkyglintWorldSmoke").info(text.getString()));
            phase++;
            if (phase < SCENARIOS.length) SkyglintClient.INSTANCE.setPreviewScenario(SCENARIOS[phase]);
            else if (phase < SCENARIOS.length + 3) {
                client.options.guiScale().set(phase - SCENARIOS.length + 1);
                client.resizeGui();
                SkyglintClient.INSTANCE.setPreviewScenario("objective");
            } else {
                if (HudBackdrop.captureCount() < 10) throw new AssertionError("Real framebuffer blur did not run");
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("Backdrop verification: {}", HudBackdrop.debugStatus());
                eventTicks = 0;
                client.options.guiScale().set(2);
                client.resizeGui();
                SkyglintClient.INSTANCE.setPreviewScenario("event");
            }
        }
    }

    private static void verifyFishingScene(String label) {
        var snapshot = SkyglintClient.INSTANCE.hudSnapshot();
        if (snapshot.getBosses().size() != 1 || snapshot.getBosses().getFirst().getProgress() != .64f ||
            !(snapshot.getProgression() instanceof dev.skyglint.core.ProgressionView.SkillView skill) ||
            skill.getSkill() != dev.skyglint.core.Skill.FISHING || skill.getGain() != 1.1 ||
            snapshot.getPet() == null || !snapshot.getPet().getName().equals("Seal") || snapshot.getNotice() != null)
            throw new AssertionError("Fishing helper or reward replaced underlying ordinary state: " + snapshot);
        var bait = snapshot.getWidgets().stream().filter(widget -> widget.getId().equals("skyglint.fishing.bait.current")).findFirst().orElse(null);
        String expectedCount = switch (label) {
            case "low" -> "12";
            case "empty" -> "0";
            case "rare-double-hook", "junk-rusty-coin", "junk-leather-boot", "trophy-diamond", "shard-three" -> "8";
            default -> null;
        };
        if (expectedCount != null) {
            if (bait == null || bait.getPresentation() == null || bait.getPresentation().getRole() != dev.skyglint.core.WidgetRole.COUNTERS ||
                bait.getPresentation().getMetrics().size() != 1 || !bait.getPresentation().getMetrics().getFirst().getId().equals("bait_selected") ||
                !bait.getPresentation().getMetrics().getFirst().getValue().equals(expectedCount))
                throw new AssertionError("Missing exact additive bait count for " + label + ": " + snapshot);
        } else if (label.equals("sack-disabled")) {
            if (bait == null || bait.getPresentation().getMetrics().size() != 2 ||
                bait.getPresentation().getMetrics().stream().noneMatch(metric -> metric.getId().equals("bait_sack_disabled") && metric.getValue().isEmpty() && metric.getIcon().equals("@sack_disabled")) ||
                bait.getPresentation().getMetrics().stream().noneMatch(metric -> metric.getId().equals("bait_selected") && metric.getValue().isEmpty()))
                throw new AssertionError("Missing explicit disabled sack helper: " + snapshot);
        } else if (label.equals("unknown")) {
            if (bait != null) throw new AssertionError("Unknown bait manufactured a selected item or zero: " + snapshot);
        } else {
            if (bait == null || bait.getPresentation() == null || bait.getPresentation().getMetrics().size() != 1)
                throw new AssertionError("Healthy bait lost its selected item: " + snapshot);
            var selected = bait.getPresentation().getMetrics().getFirst();
            if (!selected.getId().equals("bait_selected") || !selected.getValue().isEmpty() ||
                selected.getFormat() != dev.skyglint.core.WidgetMetricFormat.ITEM || !"@fishing_bait".equals(selected.getIcon()) || selected.getAccent() != null)
                throw new AssertionError("Healthy selected bait became a low warning or lost native item routing: " + snapshot);
            if (label.equals("selected-icon") && (selected.getExpandedUntil() == null || selected.getExpandedUntil() > SkyglintClient.INSTANCE.now()))
                throw new AssertionError("Selected bait introduction did not expire into its icon: " + snapshot);
            if ((label.equals("healthy") || label.equals("refill")) &&
                (selected.getExpandedUntil() == null || selected.getExpandedUntil() <= SkyglintClient.INSTANCE.now()))
                throw new AssertionError("Fresh selected bait lost its short introduction: " + snapshot);
        }
        var notice = snapshot.getActiveNotice();
        if (label.equals("rare-double-hook")) {
            if (notice == null || !notice.getTitle().equals("Water Hydra caught · Double Hook") || notice.getReward() != null)
                throw new AssertionError("Rare catch acquired fabricated reward metadata: " + snapshot);
        } else if (label.startsWith("junk-") || label.equals("trophy-diamond") || label.equals("shard-three")) {
            if (notice == null || notice.getReward() == null) throw new AssertionError("Missing source-backed fishing reward: " + snapshot);
            var reward = notice.getReward();
            if (label.startsWith("junk-") && reward.getFishingCategory() != dev.skyglint.core.FishingLootCategory.JUNK)
                throw new AssertionError("Junk lost its independent catch category");
            if (label.equals("junk-rusty-coin") && (!reward.getItemName().equals("Rusty Coin") || !"UNCOMMON".equals(reward.getRarity())))
                throw new AssertionError("Rusty Coin item rarity was replaced by catch quality");
            if (label.equals("junk-leather-boot") && (!reward.getItemName().equals("Old Leather Boot") || !"EPIC".equals(reward.getRarity())))
                throw new AssertionError("Old Leather Boot item rarity was replaced by catch quality");
            if (label.equals("trophy-diamond") && (reward.getTrophyGrade() != dev.skyglint.core.TrophyFishGrade.DIAMOND || !"COMMON".equals(reward.getRarity())))
                throw new AssertionError("Trophy grade was conflated with item rarity");
            if (label.equals("shard-three") && !Long.valueOf(3).equals(reward.getAmount()))
                throw new AssertionError("Explicit shard amount lost");
        } else if (notice != null) throw new AssertionError("Bait generated a notice takeover: " + snapshot);
    }

    private static void verifySlayerPetScene(String label) {
        var snapshot = SkyglintClient.INSTANCE.hudSnapshot();
        if (label.startsWith("welcome-")) {
            var notice = snapshot.getActiveNotice();
            if (notice == null || !notice.getTitle().equals("Welcome to SkyBlock") || notice.getPages().size() != 3)
                throw new AssertionError("Welcome must freeze exactly the observed level/pet/location: " + snapshot);
        } else if (label.equals("quest-xp") || label.equals("quest-progress") || label.equals("boss-fight")) {
            if (snapshot.getWidgets().stream().noneMatch(widget -> widget.getId().equals("skyglint.slayer.quest")))
                throw new AssertionError("Missing Slayer source-backed quest: " + snapshot);
        } else if (label.equals("boss-slain")) {
            if (snapshot.getActiveNotice() == null || !snapshot.getActiveNotice().getTitle().equals("Voidgloom Seraph IV slain"))
                throw new AssertionError("Missing explicit Slayer completion: " + snapshot);
        } else {
            boolean expanded = label.endsWith("expanded");
            if ((snapshot.getPetTransition() != null) != expanded)
                throw new AssertionError("Pet transition lifetime disagrees with " + label + ": " + snapshot);
            String expectedPet = label.startsWith("manual-") ? "Rabbit" : "Tiger";
            if (snapshot.getPet() == null || !snapshot.getPet().getName().equals(expectedPet))
                throw new AssertionError("Stale pet snapshot replaced confirmed equip: " + snapshot);
            if (snapshot.getBosses().size() != 1 || snapshot.getProgression() == null)
                throw new AssertionError("Pet transition discarded ordinary HUD state: " + snapshot);
        }
    }

    private static void verifyFishingBaitPreview(Minecraft client) {
        var inventory = client.player.getInventory();
        int savedSlot = inventory.getSelectedSlot();
        int rodSlot = savedSlot == 8 ? 0 : savedSlot;
        var savedRod = inventory.getItem(rodSlot).copy();
        var savedBait = inventory.getItem(8).copy();
        var core = SkyglintClient.INSTANCE.getCore();
        try {
            inventory.setSelectedSlot(rodSlot);
            inventory.setItem(rodSlot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
            var preview = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SALMON, 64);
            preview.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Fish Bait"));
            var data = new net.minecraft.nbt.CompoundTag();
            data.putString("id", "FISH_BAIT");
            preview.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            inventory.setItem(8, preview);
            var observe = SkyglintClient.class.getDeclaredMethod("observeFishingBait", Minecraft.class, long.class);
            observe.setAccessible(true);
            long now = System.nanoTime() / 1_000_000;
            core.enterSkyBlock("SMOKE_FISHING_BAIT_PREVIEW", now);
            for (String count : new String[] {"256", "12"}) {
                preview.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                    net.minecraft.network.chat.Component.literal("Bait Remaining: " + count),
                    net.minecraft.network.chat.Component.literal("COMMON BAIT"))));
                observe.invoke(SkyglintClient.INSTANCE, client, now);
                var widget = core.snapshot(now).getWidgets().stream().filter(value -> value.getId().equals("skyglint.fishing.bait.current")).findFirst().orElseThrow();
                var metric = widget.getPresentation().getMetrics().getFirst();
                if (!metric.getDetail().contains(count + " remaining") || !metric.getIcon().equals("@fishing_bait"))
                    throw new AssertionError("Bait preview lore/identity was lost or replaced with the stack size64");
                if (metric.getFormat() != dev.skyglint.core.WidgetMetricFormat.ITEM ||
                    (count.equals("12") && !metric.getValue().equals("12")))
                    throw new AssertionError("Selected bait presentation/count did not survive native boundary");
                var icon = SkyglintClient.INSTANCE.getFishingBaitIcon();
                if (icon == null || icon.getItem() != net.minecraft.world.item.Items.SALMON ||
                    icon.get(net.minecraft.core.component.DataComponents.LORE) != null)
                    throw new AssertionError("Actual bait item artwork was not copied independently of count lore");
                now++;
            }
            inventory.setItem(8, net.minecraft.world.item.ItemStack.EMPTY);
            observe.invoke(SkyglintClient.INSTANCE, client, now);
            if (core.snapshot(now).getWidgets().stream().anyMatch(value -> value.getId().equals("skyglint.fishing.bait.current")))
                throw new AssertionError("Unknown bait preview manufactured a zero or retained a stale icon");
            LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_FISHING_BAIT_BOUNDARY_PASSED: native own slot8 preview, lore256/12 independent of stack64, actual icon, unknown removal");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally {
            inventory.setSelectedSlot(savedSlot); inventory.setItem(rodSlot, savedRod); inventory.setItem(8, savedBait);
            core.leaveSkyBlock();
        }
    }

    private static void verifyBossTracker() {
        var actions = new java.util.ArrayList<String>();
        var tracker = new dev.skyglint.fabric.BossPacketTracker((action, id, details) -> {
            actions.add(action); return kotlin.Unit.INSTANCE;
        });
        var id = new java.util.UUID(0, 55);
        var label = net.minecraft.network.chat.Component.literal("Objective: Craft a workbench");
        var color = net.minecraft.world.BossEvent.BossBarColor.YELLOW;
        var overlay = net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
        tracker.add(id, label, 1f, color, overlay, false, false, false);
        for (int i = 0; i < 100; i++) {
            tracker.updateName(id, label); tracker.updateProgress(id, 1f);
            tracker.updateStyle(id, color, overlay); tracker.updateProperties(id, false, false, false);
        }
        if (actions.size() != 1 || tracker.drainDuplicates().values().stream().mapToLong(Long::longValue).sum() != 400)
            throw new AssertionError("Unchanged packet diagnostic suppression failed");
        tracker.updateProgress(id, .5f);
        if (tracker.getStates().get(id).getProgress() != .5f || actions.size() != 2)
            throw new AssertionError("Changed packet was suppressed");
        tracker.remove(id);
        if (!tracker.getStates().isEmpty() || !tracker.drainDuplicates().isEmpty())
            throw new AssertionError("Boss tracker lifecycle did not reset");
        LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_BOSS_TRACKER_PASSED: 400 unchanged packets coalesced; real changes retained");
    }

    private static void verifyPlayerHead(Minecraft client) {
        var head = dev.skyglint.fabric.HeadIconResolver.INSTANCE.playerHead(client);
        if (head == null) throw new AssertionError("Player head absent in a real world");
        var profile = head.get(net.minecraft.core.component.DataComponents.PROFILE);
        if (profile == null || !profile.partialProfile().id().equals(client.player.getUUID()))
            throw new AssertionError("Player head does not use the current player's identity");
        try {
            var fixture = new com.mojang.authlib.GameProfile(new java.util.UUID(0, 73), "SkinFixture");
            String textureUrl = "https://textures.minecraft.net/texture/3a7728bbf6f53f6c1e3b121778e2c2175f6c27032dbd9a5fb3a0d8747e104f67";
            var body = new net.minecraft.core.ClientAsset.DownloadedTexture(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("skyglint", "skin_fixture"), textureUrl);
            var skin = net.minecraft.world.entity.player.PlayerSkin.insecure(body, null, null,
                net.minecraft.world.entity.player.PlayerModelType.SLIM);
            var method = dev.skyglint.fabric.HeadIconResolver.class.getDeclaredMethod("profileForHead",
                com.mojang.authlib.GameProfile.class, net.minecraft.world.entity.player.PlayerSkin.class);
            method.setAccessible(true);
            var resolved = (net.minecraft.world.item.component.ResolvableProfile) method.invoke(
                dev.skyglint.fabric.HeadIconResolver.INSTANCE, fixture, skin);
            var property = resolved.partialProfile().properties().get("textures").iterator().next();
            var json = com.google.gson.JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(property.value()), java.nio.charset.StandardCharsets.UTF_8));
            if (!textureUrl.equals(json.getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString()))
                throw new AssertionError("Already downloaded custom skin URL was lost");
            // No texture download is needed to verify the profile data path.
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_PLAYER_HEAD_PASSED: current identity and already-loaded custom texture preserved");
    }

    private static void verifyHeldDrillFuel(Minecraft client) {
        var inventory = client.player.getInventory();
        int selected = inventory.getSelectedSlot();
        int otherSlot = (selected + 1) % 9;
        var saved = inventory.getItem(selected).copy();
        var savedOther = inventory.getItem(otherSlot).copy();
        var core = SkyglintClient.INSTANCE.getCore();
        try {
            var drill = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PRISMARINE_SHARD);
            drill.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Auspicious Titanium Drill DR-X655"));
            drill.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                net.minecraft.network.chat.Component.literal("Fuel: 395/10k"),
                net.minecraft.network.chat.Component.literal("MYTHIC DRILL"))));
            var data = new net.minecraft.nbt.CompoundTag();
            data.putString("id", "TITANIUM_DRILL_4");
            data.putLong("drill_fuel", 395);
            drill.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            inventory.setItem(selected, drill);
            var observe = SkyglintClient.class.getDeclaredMethod("observeHeldTool", Minecraft.class, long.class);
            observe.setAccessible(true);
            long now = System.nanoTime() / 1_000_000;
            core.enterSkyBlock("SMOKE_DRILL_TOOL", now);
            observe.invoke(SkyglintClient.INSTANCE, client, now);
            var widget = core.snapshot(now).getWidgets().stream()
                .filter(value -> value.getId().equals("skyglint.mining.drill_fuel.current")).findFirst()
                .orElseThrow(() -> new AssertionError("Actual held-item adapter did not publish drill fuel"));
            if (widget.getPresentation().getRole() != dev.skyglint.core.WidgetRole.COUNTERS || widget.getProgress().getCurrent() != 395.0 ||
                widget.getProgress().getMaximum() != 10000.0)
                throw new AssertionError("Actual drill lore/custom-data ratio was lost");
            inventory.setItem(otherSlot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
            inventory.setSelectedSlot(otherSlot);
            observe.invoke(SkyglintClient.INSTANCE, client, now + 1);
            if (core.snapshot(now + 1).getWidgets().stream().anyMatch(value -> value.getId().equals("skyglint.mining.drill_fuel.current")))
                throw new AssertionError("Switching to a non-drill retained stale fuel");
            LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_DRILL_TOOL_PASSED: actual selected ItemStack lore/custom-data observed; non-drill slot removes fuel");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally {
            inventory.setSelectedSlot(selected);
            inventory.setItem(selected, saved);
            inventory.setItem(otherSlot, savedOther);
            core.leaveSkyBlock();
        }
    }

    private static void verifyElectionHeads() {
        if (dev.skyglint.fabric.ElectionHeads.INSTANCE.knownCandidateCount() != 13)
            throw new AssertionError("Candidate head catalogue missing");
        for (String name : new String[] {"aatrox", "diana", "marina", "paul", "foxy"}) {
            var head = dev.skyglint.fabric.ElectionHeads.INSTANCE.resolve("@candidate:" + name);
            if (head == null || head.getItem() != net.minecraft.world.item.Items.PLAYER_HEAD ||
                head.get(net.minecraft.core.component.DataComponents.PROFILE) == null)
                throw new AssertionError("Missing real candidate profile: " + name);
        }
        if (dev.skyglint.fabric.ElectionHeads.INSTANCE.resolve("@candidate:unknown") != null)
            throw new AssertionError("Unknown candidate must not resolve Steve");
        LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_ELECTION_HEADS_PASSED: five candidate profiles, 13-entry native catalogue");
    }

    private static void verifyKeeperInventory(Minecraft client) {
        var inventory = client.player.getInventory();
        int selected = inventory.getSelectedSlot();
        int toolSlot = (selected + 1) % 9;
        var saved = inventory.getItem(selected).copy();
        var savedTool = inventory.getItem(toolSlot).copy();
        var core = SkyglintClient.INSTANCE.getCore();
        try {
            var detector = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);
            var detectorData = new net.minecraft.nbt.CompoundTag();
            detectorData.putString("id", "DWARVEN_METAL_DETECTOR");
            detector.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(detectorData));
            var tool = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_AXE);
            var toolData = new net.minecraft.nbt.CompoundTag();
            toolData.putString("id", "DWARVEN_DIAMOND_AXE");
            tool.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(toolData));
            inventory.setItem(selected, detector);
            inventory.setItem(toolSlot, tool);
            var held = SkyglintClient.class.getDeclaredMethod("observeHeldTool", Minecraft.class, long.class);
            var observed = SkyglintClient.class.getDeclaredMethod("observeMiningInventory", Minecraft.class, long.class);
            held.setAccessible(true); observed.setAccessible(true);
            long now = System.nanoTime() / 1_000_000;
            core.enterSkyBlock("SMOKE_KEEPER_INVENTORY", now);
            core.ingest(new dev.skyglint.core.NormalizedSignal.ContextSnapshot("Mines of Divan", "crystal_hollows"), now);
            held.invoke(SkyglintClient.INSTANCE, client, now);
            observed.invoke(SkyglintClient.INSTANCE, client, now);
            core.ingest(new dev.skyglint.core.NormalizedSignal.ActionBar("TREASURE: 12.3m"), now);
            var widget = core.snapshot(now).getWidgets().stream().filter(value -> value.getId().equals("skyglint.mining.nucleus.search")).findFirst().orElseThrow();
            if (!widget.getDetail().startsWith("1 carried")) throw new AssertionError("Keeper tool inventory ID not observed");
            inventory.setItem(toolSlot, net.minecraft.world.item.ItemStack.EMPTY);
            observed.invoke(SkyglintClient.INSTANCE, client, now + 1);
            widget = core.snapshot(now + 1).getWidgets().stream().filter(value -> value.getId().equals("skyglint.mining.nucleus.search")).findFirst().orElseThrow();
            if (!widget.getDetail().startsWith("0 carried")) throw new AssertionError("Absent keeper tool was retained");
            LoggerFactory.getLogger("SkyglintWorldSmoke").info("REBORN_KEEPER_INVENTORY_PASSED: actual native inventory ID and removal, detector search");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally { inventory.setItem(selected, saved); inventory.setItem(toolSlot, savedTool); core.leaveSkyBlock(); }
    }
}
