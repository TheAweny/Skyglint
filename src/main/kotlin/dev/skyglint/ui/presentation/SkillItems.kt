package dev.skyglint.ui.presentation

import dev.skyglint.core.Skill
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Vanilla item identities intentionally pass through Minecraft's resource-pack-aware item renderer. */
object SkillItems {
    private val icons by lazy { mapOf(
        Skill.COMBAT to ItemStack(Items.IRON_SWORD),
        Skill.FARMING to ItemStack(Items.WHEAT),
        Skill.FISHING to ItemStack(Items.FISHING_ROD),
        Skill.MINING to ItemStack(Items.DIAMOND_PICKAXE),
        Skill.FORAGING to ItemStack(Items.IRON_AXE),
        Skill.ENCHANTING to ItemStack(Items.ENCHANTED_BOOK),
        Skill.ALCHEMY to ItemStack(Items.BREWING_STAND),
        Skill.CARPENTRY to ItemStack(Items.OAK_PLANKS),
        Skill.TAMING to ItemStack(Items.BONE),
        Skill.HUNTING to ItemStack(Items.BOW),
        Skill.RUNECRAFTING to ItemStack(Items.AMETHYST_SHARD),
        Skill.SOCIAL to ItemStack(Items.NAME_TAG),
    ) }
    fun icon(skill: Skill): ItemStack = icons.getValue(skill)
}
