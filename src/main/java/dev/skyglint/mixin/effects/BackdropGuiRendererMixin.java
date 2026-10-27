package dev.skyglint.mixin.effects;

import dev.skyglint.ui.effects.HudBackdrop;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class BackdropGuiRendererMixin {
    /** Runs after the world framebuffer is ready, before any foreground GUI vertices are drawn. */
    @Inject(method = "draw", at = @At("HEAD"))
    private void skyglint$captureBackdrop(CallbackInfo ci) {
        HudBackdrop.capturePendingFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void skyglint$closeBackdrop(CallbackInfo ci) {
        HudBackdrop.close();
    }
}
