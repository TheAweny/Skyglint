package dev.skyglint.ui.presentation

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Presentation of typed contributor widget IDs; parsing and context gating stay in feature modules. */
object ContextWidgets {
    fun isContext(id: String): Boolean = id.startsWith("skyglint.garden.") || id.startsWith("skyglint.dungeon.") ||
        id.startsWith("skyglint.elections.") || id.startsWith("skyglint.mining.") || id.startsWith("skyglint.slayer.")
    fun accent(id: String): Int = when {
        ".jacob" in id -> 0xFFFFD182.toInt()
        ".pests" in id -> 0xFFF3AAAD.toInt()
        id.startsWith("skyglint.dungeon.") -> 0xFFCAACF5.toInt()
        id.startsWith("skyglint.elections.") -> 0xFFD3BEF3.toInt()
        id.startsWith("skyglint.mining.") -> 0xFFD4C1F0.toInt()
        id.startsWith("skyglint.slayer.") -> 0xFFF0A5A5.toInt()
        else -> 0xFFA6D9AD.toInt()
    }
    fun icon(id: String): ItemStack = ItemStack(when {
        ".visitors" in id -> Items.EMERALD
        ".pests" in id -> Items.SPIDER_EYE
        ".milestone" in id -> Items.GOLDEN_HOE
        ".jacob" in id -> Items.GOLDEN_CARROT
        id.startsWith("skyglint.dungeon.") -> Items.IRON_SWORD
        id.startsWith("skyglint.mining.") -> Items.IRON_PICKAXE
        id.startsWith("skyglint.slayer.") -> Items.IRON_SWORD
        else -> Items.WHEAT
    })
}
