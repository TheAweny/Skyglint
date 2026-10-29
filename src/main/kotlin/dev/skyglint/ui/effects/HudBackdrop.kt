package dev.skyglint.ui.effects

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import dev.skyglint.mixin.effects.BackdropGraphicsAccessor
import dev.skyglint.mixin.effects.BackdropPipelineInvoker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

/**
 * Real clipped framebuffer blur, using Blaze3D on both graphics backends.
 * One GPU snapshot is reused by every capsule. No glReadPixels, CPU pixel copies or raw OpenGL calls.
 */
object HudBackdrop {
    private val logger = LoggerFactory.getLogger("Skyglint/Backdrop")
    private val pipeline: RenderPipeline = BackdropPipelineInvoker.`skyglint$registerBackdrop`(
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("skyglint", "pipeline/hud_backdrop"))
            .withVertexShader(Identifier.fromNamespaceAndPath("skyglint", "core/hud_backdrop"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("skyglint", "core/hud_backdrop"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build()
    )
    private val contourPipeline: RenderPipeline = BackdropPipelineInvoker.`skyglint$registerBackdrop`(
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("skyglint", "pipeline/hud_contour"))
            .withVertexShader(Identifier.fromNamespaceAndPath("skyglint", "core/hud_backdrop"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("skyglint", "core/hud_contour"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build()
    )
    private var snapshot: GpuTexture? = null
    private val surfacePipeline: RenderPipeline = BackdropPipelineInvoker.`skyglint$registerBackdrop`(
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("skyglint", "pipeline/ui_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath("skyglint", "core/hud_backdrop"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("skyglint", "core/ui_surface"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false).build())
    private var snapshotView: GpuTextureView? = null
    private var requested = false
    private var disabled = false
    private var lastGraphics: GuiGraphicsExtractor? = null
    private var captures = 0L
    private var capsulesSubmitted = 0L
    private var allocationCount = 0L

    /** Load/register shaders at client initialization, before the first resource compilation. */
    fun initialize() { pipeline.location; contourPipeline.location; surfacePipeline.location }

    /** Antialiased native settings surfaces; radius and color are independent of framebuffer scale. */
    fun surface(graphics: GuiGraphicsExtractor,x: Float,y: Float,w: Float,h: Float,radius: Float,color: Int) {
        if(w<=0||h<=0)return
        val state=(graphics as BackdropGraphicsAccessor).`skyglint$backdropRenderState`()
        state.addGuiElement(BackdropRenderState(surfacePipeline,TextureSetup.noTexture(),Matrix3x2f(graphics.pose()),
            x,y,w,h,color,(radius/minOf(w,h)).coerceIn(0f,.5f)))
    }

    /** Call once at the beginning of each HUD extraction, before the first capsule. */
    fun beginFrame(graphics: GuiGraphicsExtractor) {
        if (disabled) return
        if (lastGraphics !== graphics) {
            lastGraphics = graphics
            requested = false
        }
    }

    /** Draw before tint, fill, icons and text. Coordinates use the caller's current 2D pose. */
    fun capsule(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, radius: Float, alpha: Float) {
        if (disabled || w <= 0 || h <= 0 || alpha <= 0) return
        if (!x.isFinite() || !y.isFinite() || !w.isFinite() || !h.isFinite() || !radius.isFinite() || !alpha.isFinite()) return
        val view = ensureSnapshot() ?: return
        requested = true
        capsulesSubmitted++
        val state = (graphics as BackdropGraphicsAccessor).`skyglint$backdropRenderState`()
        val fraction = (radius / minOf(w, h)).coerceIn(0f, .5f)
        // R is the normalized corner radius, A the opacity; green/blue are unused shader parameters.
        val color = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or
            ((fraction * 255).roundToInt() shl 16)
        val texture = TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
        state.addGuiElement(BackdropRenderState(pipeline, texture, Matrix3x2f(graphics.pose()), x, y, w, h, color))
    }

    /** Analytic, antialiased stadium perimeter. null is a dashed unknown track; 0f is known empty. */
    fun contour(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, fraction: Float?, color: Int, opacity: Float = 1f) {
        if (w <= 0 || h <= 0 || !x.isFinite() || !y.isFinite() || !w.isFinite() || !h.isFinite()) return
        if (fraction != null && !fraction.isFinite() || !opacity.isFinite() || opacity <= 0f) return
        // Float UV transport retains frame-by-frame countdown precision, including for long events.
        val progress = ContourProgress.encode(fraction)
        val parameters = ((opacity.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color and 0xFFFFFF)
        val state = (graphics as BackdropGraphicsAccessor).`skyglint$backdropRenderState`()
        state.addGuiElement(BackdropRenderState(contourPipeline, TextureSetup.noTexture(), Matrix3x2f(graphics.pose()),
            x, y, w, h, parameters, progress))
    }

    /** Called by the GUI draw Mixin after world rendering, before the GUI render pass opens. */
    @JvmStatic
    fun capturePendingFrame() {
        if (!requested || disabled) return
        requested = false
        try {
            val source = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture ?: return
            val target = snapshot ?: return
            if (source.getWidth(0) != target.getWidth(0) || source.getHeight(0) != target.getHeight(0)) return
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, target, 0, 0, 0, 0, 0,
                source.getWidth(0), source.getHeight(0))
            captures++
            if (captures == 1L) logger.info("HUD backdrop active: GPU framebuffer {}x{}, local Gaussian capsule shader, backend {}",
                source.getWidth(0), source.getHeight(0), RenderSystem.getBackendDescription())
        } catch (failure: Exception) {
            disabled = true
            logger.error("Backdrop capture failed; using unblurred HUD until restart", failure)
        }
    }

    private fun ensureSnapshot(): GpuTextureView? {
        try {
            val source = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture ?: return null
            val old = snapshot
            if (old == null || old.isClosed || old.getWidth(0) != source.getWidth(0) || old.getHeight(0) != source.getHeight(0) || old.format != source.format) {
                snapshotView?.close()
                old?.close()
                val device = RenderSystem.getDevice()
                snapshot = device.createTexture("Skyglint HUD background snapshot", GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
                    source.format, source.getWidth(0), source.getHeight(0), 1, 1)
                snapshotView = device.createTextureView(snapshot!!)
                allocationCount++
            }
            return snapshotView
        } catch (failure: Exception) {
            disabled = true
            logger.error("Could not allocate HUD backdrop; using unblurred HUD until restart", failure)
            return null
        }
    }

    /** Counters are observational only: useful to distinguish a compiled shader from a live capture. */
    @JvmStatic
    fun captureCount(): Long = captures

    @JvmStatic
    fun debugStatus(): String = "disabled=$disabled,captures=$captures,capsules=$capsulesSubmitted,allocations=$allocationCount," +
        "texture=${snapshot?.let { "${it.getWidth(0)}x${it.getHeight(0)}" } ?: "none"}"

    @JvmStatic
    fun close() {
        requested = false
        lastGraphics = null
        snapshotView?.close()
        snapshotView = null
        snapshot?.close()
        snapshot = null
    }
}
