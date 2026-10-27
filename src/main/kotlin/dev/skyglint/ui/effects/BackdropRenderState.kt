package dev.skyglint.ui.effects

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import kotlin.math.ceil
import kotlin.math.floor

/** Normalized UVs describe the rounded rectangle; screen sampling uses fragment coordinates. */
internal class BackdropRenderState(
    private val renderPipeline: RenderPipeline,
    private val texture: TextureSetup,
    private val pose: Matrix3x2f,
    private val x: Float,
    private val y: Float,
    private val width: Float,
    private val height: Float,
    private val color: Int,
    private val scalarParameter: Float = 0f,
) : GuiElementRenderState {
    private val area = ScreenRectangle(floor(x).toInt(), floor(y).toInt(),
        ceil(x + width).toInt() - floor(x).toInt(), ceil(y + height).toInt() - floor(y).toInt()).transformMaxBounds(pose)

    override fun pipeline(): RenderPipeline = renderPipeline
    override fun textureSetup(): TextureSetup = texture
    override fun scissorArea(): ScreenRectangle? = null
    override fun bounds(): ScreenRectangle = area

    override fun buildVertices(consumer: VertexConsumer) {
        // Two separated Y bands carry one continuous per-quad scalar without consuming color alpha.
        // The vertex shader recovers normalized 0/1 Y before interpolation and derivative sampling.
        consumer.addVertexWith2DPose(pose, x, y).setUv(0f, scalarParameter).setColor(color)
        consumer.addVertexWith2DPose(pose, x, y + height).setUv(0f, scalarParameter + 2f).setColor(color)
        consumer.addVertexWith2DPose(pose, x + width, y + height).setUv(1f, scalarParameter + 2f).setColor(color)
        consumer.addVertexWith2DPose(pose, x + width, y).setUv(1f, scalarParameter).setColor(color)
    }
}
