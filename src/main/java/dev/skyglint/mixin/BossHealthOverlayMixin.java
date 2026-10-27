package dev.skyglint.mixin;

import dev.skyglint.fabric.SkyglintClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe packets without changing their handling or their music/fog side effects. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void skyglint$observe(ClientboundBossEventPacket packet, CallbackInfo ci) {
        SkyglintClient.onBossPacket(packet);
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void skyglint$reset(CallbackInfo ci) {
        SkyglintClient.onBossReset();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void skyglint$replace(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (SkyglintClient.shouldReplaceBossBars()) ci.cancel();
    }
}
