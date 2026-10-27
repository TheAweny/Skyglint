package dev.skyglint.mixin.effects;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface BackdropPipelineInvoker {
    @Invoker("register")
    static RenderPipeline skyglint$registerBackdrop(RenderPipeline pipeline) {
        throw new AssertionError("Backdrop pipeline invoker was not transformed");
    }
}
