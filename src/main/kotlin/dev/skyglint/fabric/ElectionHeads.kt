package dev.skyglint.fabric

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
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

/** Public candidate textures, with native Mojang loading/caching. No HTTP or disk work in render. */
object ElectionHeads {
    private var initialized = false
    private var textures: Map<String, String> = emptyMap()
    private val heads = mutableMapOf<String, ItemStack>()

    /** Call once during client initialization, before the first HUD render. */
    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            val stream = ElectionHeads::class.java.getResourceAsStream("/assets/skyglint/data/election-heads.json") ?: return
            val json = stream.bufferedReader(StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
            textures = json.getAsJsonArray("heads").take(32).associate { value ->
                val entry = value.asJsonObject
                val name = entry.get("name").asString.lowercase(Locale.ROOT)
                val hash = entry.get("texture").asString
                require(name.matches(Regex("[a-z][a-z -]{0,39}")))
                require(hash.matches(Regex("[a-fA-F0-9]{32,128}")))
                name to hash
            }
        } catch (failure: Exception) {
            textures = emptyMap()
            LoggerFactory.getLogger("Skyglint/ElectionHeads").warn("Could not read candidate heads; unknown icons remain neutral", failure)
        }
    }

    /** Unknown names return null, never another candidate's skin or an identity-only Steve. */
    fun resolve(reference: String?): ItemStack? {
        if (Minecraft.getInstance().level == null || reference?.startsWith("@candidate:") != true) return null
        val name = reference.removePrefix("@candidate:").lowercase(Locale.ROOT)
        val hash = textures[name] ?: return null
        return heads.getOrPut(name) { createHead(name, hash) }.copy()
    }

    fun knownCandidateCount(): Int = textures.size

    private fun createHead(name: String, hash: String): ItemStack {
        val payload = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/$hash\"}}}"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", encoded)))
        val profile = GameProfile(UUID.nameUUIDFromBytes(("skyglint:candidate:$name:$hash").toByteArray(StandardCharsets.UTF_8)), "SkyglintCandidate", properties)
        return ItemStack(Items.PLAYER_HEAD).apply { set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile)) }
    }
}
