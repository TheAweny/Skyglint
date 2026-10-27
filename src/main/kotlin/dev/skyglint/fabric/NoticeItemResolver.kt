package dev.skyglint.fabric

import dev.skyglint.core.ItemVisualHint
import dev.skyglint.core.SkyBlockParser
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.component.DataComponents
import dev.skyglint.features.ObservedText

/** Only a current inventory match carries Hypixel custom model data, skin and resource-pack overrides. */
object NoticeItemResolver {
    data class Resolution(val icon: ItemStack, val rarity: String?, val observed: Boolean)
    fun resolve(client: Minecraft, hint: ItemVisualHint?): ItemStack? {
        return resolveDetails(client, hint)?.icon
    }
    fun resolveDetails(client: Minecraft, hint: ItemVisualHint?): Resolution? {
        if (hint == null || client.level == null) return null
        val name = hint.itemName?.let(SkyBlockParser::clean)
        val inventory = client.player?.inventory
        var candidate: ItemStack? = null
        var ambiguous = false
        if (inventory != null && name != null) {
            val petName = Regex("\\[Lvl [0-9]+] ${Regex.escape(name)}(?: ✦)?")
            for (slot in 0 until inventory.containerSize) {
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue
                val observedName = SkyBlockParser.clean(stack.hoverName.string)
                if (observedName == name || (stack.item == Items.PLAYER_HEAD && petName.matches(observedName))) {
                    if (candidate != null && !ItemStack.isSameItemSameComponents(candidate, stack)) ambiguous = true
                    else candidate = stack
                }
            }
        }
        if (!ambiguous && candidate != null) return Resolution(candidate.copy(),
            ObservedText.rarity(candidate.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty()), true)
        val id = hint.vanillaItemId?.let(Identifier::tryParse) ?: return null
        if (id.namespace != "minecraft") return null
        return BuiltInRegistries.ITEM.getOptional(id).map { Resolution(ItemStack(it), null, false) }.orElse(null)
    }
}
