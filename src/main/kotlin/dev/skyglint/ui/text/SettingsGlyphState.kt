package dev.skyglint.ui.text

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import dev.skyglint.mixin.effects.BackdropPipelineInvoker
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Native glyph geometry; grayscale TTF coverage is integrated across each framebuffer pixel. */
internal class SettingsGlyphState(private val pose: Matrix3x2f, private val glyph: TextRenderable,
                                  private val scissor: ScreenRectangle?) : GuiElementRenderState {
    private val filtered = glyph.guiPipeline() === RenderPipelines.GUI_TEXT_GRAYSCALE
    private val baseArea = ScreenRectangle(floor(glyph.left()).toInt(),floor(glyph.top()).toInt(),
        (ceil(glyph.right())-floor(glyph.left())).toInt().coerceAtLeast(1),
        (ceil(glyph.bottom())-floor(glyph.top())).toInt().coerceAtLeast(1)).transformMaxBounds(pose)
    // The coverage filter also needs the fraction of a pixel just outside the original ink quad.
    private val area = if (filtered) ScreenRectangle(baseArea.left()-1, baseArea.top()-1,
        baseArea.width()+2, baseArea.height()+2) else baseArea

    override fun buildVertices(consumer: VertexConsumer) {
        if (!filtered) {
            glyph.render(Matrix4f().mul(pose), consumer, 15728880, true)
            return
        }
        val capture = GlyphVertices()
        glyph.render(Matrix4f().mul(pose), capture, 15728880, true)
        for (quad in capture.vertices.chunked(4)) {
            if (quad.size != 4) continue
            val left = quad.minOf { it.x }; val right = quad.maxOf { it.x }
            val top = quad.minOf { it.y }; val bottom = quad.maxOf { it.y }
            val u0 = quad.minOf { it.u }; val u1 = quad.maxOf { it.u }
            val v0 = quad.minOf { it.v }; val v1 = quad.maxOf { it.v }
            if (right <= left || bottom <= top) continue
            val du = (u1-u0)/(right-left); val dv = (v1-v0)/(bottom-top)
            for (vertex in quad) {
                val dx = if (vertex.x < (left+right)*.5f) -1f else 1f
                val dy = if (vertex.y < (top+bottom)*.5f) -1f else 1f
                consumer.addVertex(vertex.x+dx, vertex.y+dy, vertex.z)
                    .setColor(vertex.color).setUv(vertex.u+dx*du, vertex.v+dy*dv)
                    .setUv1((u0*UV_PRECISION).roundToInt(), (v0*UV_PRECISION).roundToInt())
                    .setUv2((u1*UV_PRECISION).roundToInt(), (v1*UV_PRECISION).roundToInt())
            }
        }
    }
    override fun pipeline() = if (filtered) coveragePipeline else glyph.guiPipeline()
    override fun textureSetup() = TextureSetup.singleTextureWithLightmap(glyph.textureView(),
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
    override fun bounds() = area
    override fun scissorArea() = scissor

    companion object {
        private const val UV_PRECISION = 32767f
        private val format = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT).build()
        private val coveragePipeline = BackdropPipelineInvoker.`skyglint$registerBackdrop`(
            RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("skyglint", "pipeline/settings_text"))
                .withVertexShader(Identifier.fromNamespaceAndPath("skyglint", "core/settings_text"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("skyglint", "core/settings_text"))
                .withVertexBinding(0, format).build())

        fun initialize() { coveragePipeline.location }
    }

    private data class Vertex(val x: Float, val y: Float, val z: Float,
                              var color: Int = -1, var u: Float = 0f, var v: Float = 0f)

    /** Read the public native glyph stream instead of accessing private atlas fields. */
    private class GlyphVertices : VertexConsumer {
        val vertices = ArrayList<Vertex>(4)
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply { vertices.add(Vertex(x,y,z)) }
        override fun setColor(color: Int): VertexConsumer = apply { vertices.last().color = color }
        override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer =
            setColor((a shl 24) or (r shl 16) or (g shl 8) or b)
        override fun setUv(u: Float, v: Float): VertexConsumer = apply { vertices.last().u=u; vertices.last().v=v }
        override fun setUv1(u: Int, v: Int): VertexConsumer = this
        override fun setUv2(u: Int, v: Int): VertexConsumer = this
        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun setLineWidth(width: Float): VertexConsumer = this
    }
}
