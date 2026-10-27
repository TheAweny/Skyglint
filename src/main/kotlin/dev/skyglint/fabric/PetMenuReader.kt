package dev.skyglint.fabric

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.skyglint.core.PetSnapshot
import dev.skyglint.core.ProgressSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Passive observation of the server's Pets menu. Never opens a screen or clicks a slot.
 * A page without the selected pet is not evidence that the player has no active pet.
 */
object PetMenuReader {
    data class Observation(val pet: PetSnapshot, val icon: ItemStack)

    private val formatting = Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE)
    private val menuTitle = Regex("^(?:\\(\\d+/\\d+\\) )?Pets(?:: \"[^\"]*\")?(?: \\(\\d+/\\d+\\))?$")
    private val petName = Regex("^\\[Lvl ([\\d,]+)] (?:\\[\\d+✦] )?([\\p{L}\\p{N}][\\p{L}\\p{N}' -]{0,70}?)( ✦)?$")
    // Only an explicit level XP ratio is accepted. A total-lifetime XP field is a different quantity.
    private val exactXpRatio = Regex("^(?:▸\\s*)?([\\d,]+(?:\\.\\d+)?)/([\\d,]+(?:\\.\\d+)?)(?: XP)?$")
    private var lastPet: PetSnapshot? = null
    private var lastIcon: ItemStack? = null
    private var lastScanAt = Long.MIN_VALUE

    fun observe(client: Minecraft, now: Long): Observation? {
        val player = client.player ?: return null
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return null
        if (!menuTitle.matches(clean(screen.title.string))) return null
        if (lastScanAt != Long.MIN_VALUE && now >= lastScanAt && now - lastScanAt < 200) return null
        lastScanAt = now
        var observation: Observation? = null

        for (slot in screen.menu.slots) {
            if (slot.container === player.inventory) continue
            val stack = slot.item
            if (stack.isEmpty || stack.item != Items.PLAYER_HEAD) continue
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { clean(it.string) }.orEmpty()
            val custom = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
            val info = parsePetInfo(custom?.getStringOr("petInfo", "").orEmpty())
            val active = info?.get("active")?.let {
                it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
            } == true
            val selected = active || lore.any { it == "Click to despawn!" }

            val displayName = clean(stack.hoverName.string)
            val match = petName.matchEntire(displayName) ?: continue
            val level = match.groupValues[1].replace(",", "").toIntOrNull()?.takeIf { it in 1..1000 } ?: continue
            val name = match.groupValues[2].trim()
            val skin = match.groupValues[3].isNotEmpty() || Regex("\\[\\d+✦]").containsMatchIn(displayName) ||
                info?.get("skin")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() } == true
            val pet = PetSnapshot(name, level, readExactProgress(lore), skin)
            PetIconCache.observe(pet, stack, selected)
            if (!selected) continue
            if (pet == lastPet && lastIcon?.let { ItemStack.isSameItemSameComponents(it, stack) } == true) {
                // An unchanged open menu is still a fresh authoritative observation. The reducer
                // suppresses duplicate events, while the adapter refreshes its menu-over-tab lease.
                if (observation == null) observation = Observation(pet, lastIcon!!.copy())
                continue
            }

            val icon = stack.copy()
            lastPet = pet
            lastIcon = icon.copy()
            if (observation == null) observation = Observation(pet, icon)
        }

        if (observation != null) return observation

        // Absence only clears this icon cache; it never emits a speculative despawn observation.
        lastPet = null
        lastIcon = null
        return null
    }

    fun reset() {
        lastPet = null
        lastIcon = null
        lastScanAt = Long.MIN_VALUE
    }

    private fun clean(text: String): String = formatting.replace(text, "").trim()

    private fun parsePetInfo(text: String): JsonObject? {
        if (text.isBlank() || text.length > 16384) return null
        return try {
            JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun readExactProgress(lore: List<String>): ProgressSnapshot? {
        for (line in lore) {
            val match = exactXpRatio.matchEntire(line) ?: continue
            val current = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val maximum = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            if (!current.isFinite() || !maximum.isFinite() || current < 0 || maximum <= 0 || current > maximum) continue
            return ProgressSnapshot(current, maximum, current / maximum)
        }
        // Abbreviated totals, MAX LEVEL and lifetime `petInfo.exp` need a pet-specific XP curve.
        // Retain unknown instead of pretending those values are a zero-progress level.
        return null
    }
}
