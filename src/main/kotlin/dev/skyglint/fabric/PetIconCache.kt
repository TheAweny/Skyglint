package dev.skyglint.fabric

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import dev.skyglint.core.PetSnapshot
import dev.skyglint.core.SkyBlockParser
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID

/**
 * Real server-observed ItemStacks win over the pinned public default texture catalogue.
 * No player UUIDs, complete petInfo NBT or account profiles are written to disk.
 */
object PetIconCache {
    private const val MAX_OBSERVED = 160
    private val observedDefaults = linkedMapOf<String, ItemStack>()
    private val selectedSkins = linkedMapOf<String, ItemStack>()
    private var defaultTextures: Map<String, String> = emptyMap()
    private val defaultHeads = linkedMapOf<String, ItemStack>()
    private var initialized = false

    /** Startup-only classpath resource read. There is no HTTP request or recurring file I/O. */
    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            val resource = PetIconCache::class.java.getResourceAsStream("/assets/skyglint/data/pet-heads.json") ?: return
            val root = resource.bufferedReader(StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
            val parsed = linkedMapOf<String, String>()
            for (element in root.getAsJsonArray("heads")) {
                val entry = element.asJsonObject
                val name = normalize(entry.get("name").asString)
                val hash = entry.get("texture").asString
                if (!hash.matches(Regex("[a-fA-F0-9]{32,128}"))) continue
                parsed[name] = hash
            }
            defaultTextures = parsed.toMap()
        } catch (failure: Exception) {
            LoggerFactory.getLogger("Skyglint/PetIcons").warn("Could not read default pet head catalogue; observed menu items remain available", failure)
        }
    }

    /** Cache all unskinned menu heads; cache a skin only when this is the confirmed selected pet. */
    fun observe(pet: PetSnapshot, stack: ItemStack, selected: Boolean = true) {
        if (Minecraft.getInstance().level == null) return
        if (stack.isEmpty || stack.item != Items.PLAYER_HEAD || stack.get(DataComponents.PROFILE) == null) return
        val key = normalize(pet.name)
        if (key.isEmpty()) return
        val target = if (pet.skin) {
            if (!selected) return
            selectedSkins
        } else observedDefaults
        if (target[key]?.let { ItemStack.isSameItemSameComponents(it, stack) } == true) return
        target.remove(key)
        target[key] = stack.copy()
        while (target.size > MAX_OBSERVED) target.remove(target.keys.first())
    }

    /** Returns a defensive copy. A starred/skin pet never falls back to the unskinned catalogue. */
    fun resolve(pet: PetSnapshot?): ItemStack? {
        if (pet == null || Minecraft.getInstance().level == null) return null
        val key = normalize(pet.name)
        if (pet.skin) return selectedSkins[key]?.copy()
        observedDefaults[key]?.let { return it.copy() }
        val hash = defaultTextures[key] ?: return null
        return defaultHeads.getOrPut(key) { createDefaultHead(hash) }.copy()
    }

    /** Call on profile/account change, not each island transfer. Public default heads remain safe to reuse. */
    fun clear() { observedDefaults.clear(); selectedSkins.clear() }
    /** A chat selection has no skin/instance identifier; a previously selected skin may be different. */
    fun invalidateSelectedSkin(name: String) { selectedSkins.remove(normalize(name)) }

    fun defaultHeadCount(): Int = defaultTextures.size

    /** This is deliberately deferred until a live world has bound Minecraft's item components. */
    private fun createDefaultHead(hash: String): ItemStack {
        check(Minecraft.getInstance().level != null) { "Pet heads may only be materialized in a world" }
        val publicTexture = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/$hash\"}}}"
        val encoded = Base64.getEncoder().encodeToString(publicTexture.toByteArray(StandardCharsets.UTF_8))
        val property = Property("textures", encoded)
        val properties = PropertyMap(ImmutableMultimap.of("textures", property))
        val profile = GameProfile(UUID.nameUUIDFromBytes(("skyglint:pet:" + hash).toByteArray(StandardCharsets.UTF_8)), "SkyglintPet", properties)
        return ItemStack(Items.PLAYER_HEAD).apply { set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile)) }
    }

    private fun normalize(name: String): String = SkyBlockParser.clean(name).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
}
