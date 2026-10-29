package dev.skyglint.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Matrix3x2f

/** HUD heads are models, not inventory slots: inventory-helper overlays must not decorate them. */
object HudItemRenderer {
    fun item(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (stack.isEmpty) return
        if (stack.item != Items.PLAYER_HEAD) {
            graphics.item(stack, x, y)
            return
        }
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        // Keep the complete observed stack and vanilla model/texture/resource-pack resolution.
        // Submitting its native model directly avoids the inventory item helper's decoration hooks.
        val model = TrackingItemStackRenderState()
        client.itemModelResolver.updateForTopItem(model, stack, ItemDisplayContext.GUI, level, client.player, 0)
        graphics.guiRenderState.addItem(GuiItemRenderState(
            Matrix3x2f(graphics.pose()), model, x, y, graphics.scissorStack.peek(),
        ))
    }
}
