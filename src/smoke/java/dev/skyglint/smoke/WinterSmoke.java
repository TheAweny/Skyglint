package dev.skyglint.smoke;

import dev.skyglint.fabric.SkyglintClient;
import dev.skyglint.core.RewardDelivery;
import dev.skyglint.ui.effects.HudBackdrop;
import dev.skyglint.ui.presentation.HudComposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.LoggerFactory;

/** Actual local-world rendering, replaying only sanitized server result fields. */
final class WinterSmoke {
    private final String[] scenes = {"idle", "fishing", "winter", "reindrake", "buffs", "buffwarning", "godwarning", "defenseresults"};
    private final long[] resultAt = {900, 2700, 4500, 6300, 8700, 10500, 12300, 14100, 16500};
    private final String[] resultLabels = {"white-gift", "volcanic-rock", "red-gift", "green-gift", "damage", "cubes", "cannonballs", "accuracy", "return"};
    private long startedAt = -1;
    private int scene;
    private int capture;

    void tick(Minecraft client) {
        long now = SkyglintClient.INSTANCE.now();
        if (startedAt < 0) { startedAt = now; SkyglintClient.INSTANCE.setPreviewScenario(scenes[scene]); }
        long elapsed = now - startedAt;
        String name = scenes[scene];
        if (name.equals("defenseresults")) {
            if (capture < resultAt.length && elapsed >= resultAt[capture]) {
                var snapshot = SkyglintClient.INSTANCE.hudSnapshot();
                var notice = snapshot.getActiveNotice();
                if (capture < 4) {
                    String expected = new String[]{"White Gift", "Volcanic Rock", "Red Gift", "Green Gift"}[capture];
                    if (notice == null || notice.getLoot().size() != 4 || notice.getLoot().get(capture).getDelivery() != RewardDelivery.CLAIMABLE || !notice.getLoot().get(capture).getItemName().equals(expected))
                        throw new AssertionError("Claimable result item lost or labelled received: " + expected);
                    var element = new HudComposition().compose(snapshot, now, true, false).getFirst();
                    if (!element.getTitle().equals("Available") || element.getKind() != dev.skyglint.ui.presentation.HudElementKind.CAROUSEL || element.getCarouselIndex() != capture)
                        throw new AssertionError("Claimable carousel page or prefix changed");
                } else if (capture < 8) {
                    if (notice == null || notice.getPages().size() != 4)
                        throw new AssertionError("Result stats were lost behind four queued rewards");
                } else if (notice != null) throw new AssertionError("Result failed to return to the ordinary row");
                save(client, "result-" + resultLabels[capture++]);
            }
            if (elapsed >= 17500) {
                if (capture != resultAt.length || HudBackdrop.captureCount() < 10 || HudBackdrop.debugStatus().contains("disabled=true"))
                    throw new AssertionError("Missing result captures or actual blur");
                LoggerFactory.getLogger("SkyglintWorldSmoke").info("SKYGLINT_WINTER_RESULTS_SMOKE_PASSED: A-E presentation of captured markers, expanded idle SkyBlock, exact healthy bait count, Cookie and God pre-expiry, four claimable reward carousel pages, statistics and ordinary return; {}", HudBackdrop.debugStatus());
                client.stop();
            }
            return;
        }
        if (elapsed >= 1600 && capture == 0) {
            var snapshot = SkyglintClient.INSTANCE.hudSnapshot();
            if (name.equals("idle")) {
                var elements = new HudComposition().compose(snapshot, now, true, false);
                var level = elements.stream().filter(e -> e.getId().equals("skyblock")).findFirst().orElseThrow();
                if (level.getKind() != dev.skyglint.ui.presentation.HudElementKind.MAIN || !level.getTitle().contains("287"))
                    throw new AssertionError("Idle SkyBlock stayed a circle or lost the observed level");
            } else if (name.equals("fishing")) {
                var bait = new HudComposition().compose(snapshot, now, true, false).stream().filter(e -> e.getId().equals("group:fishing:bait")).findFirst().orElseThrow();
                if (!"256".equals(bait.getValue()) || !bait.getTitle().equals("Fish Bait"))
                    throw new AssertionError("Bait introduction lost its exact remaining amount");
            } else if (name.equals("winter")) {
                var widget = snapshot.getWidgets().stream().filter(w -> w.getId().equals("skyglint.winter.defense")).findFirst().orElseThrow();
                var metrics = widget.getPresentation().getMetrics();
                if (metrics.size() != 5 || !metrics.getFirst().getLabel().equals("E") || !metrics.getLast().getLabel().equals("A") || widget.getReplacedBossIds().size() != 1)
                    throw new AssertionError("Actual E-A marker order or exact UUID claim was lost");
                if (dev.skyglint.ui.UiWidgetRegistry.INSTANCE.find(widget.getId()) == null) throw new AssertionError("Winter renderer quarantined");
            } else if (name.endsWith("warning")) {
                if (snapshot.getActiveNotice() == null || !snapshot.getActiveNotice().getTitle().endsWith("expiring soon"))
                    throw new AssertionError("Pre-expiry notice missing: " + name);
            } else if (name.equals("buffs") && snapshot.getActiveNotice() == null) throw new AssertionError("Inactive notice missing");
            save(client, name);
            capture++;
        }
        if (name.equals("fishing") && elapsed >= 2400 && capture == 1) {
            var bait = new HudComposition().compose(SkyglintClient.INSTANCE.hudSnapshot(), now, true, false).stream()
                    .filter(e -> e.getId().equals("group:fishing:bait")).findFirst().orElseThrow();
            if (bait.getKind() != dev.skyglint.ui.presentation.HudElementKind.COUNTER || !"256".equals(bait.getValue()))
                throw new AssertionError("Settled bait lost its healthy count");
            save(client, "fishing-count"); capture++;
        }
        if (elapsed >= (name.equals("fishing") ? 2700 : 2300)) { scene++; capture = 0; startedAt = -1; }
    }

    private void save(Minecraft client, String label) {
        Screenshot.grab(client.gameDirectory, "winter-results-" + label + ".png", client.gameRenderer.mainRenderTarget(), 1, text -> {});
    }
}
