package dev.skyglint.fabric

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonObject
import dev.skyglint.core.SkyBlockParser
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.ClientAsset
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Native head item rendering keeps Mojang skin caching and resource-pack rendering in charge. */
object HeadIconResolver {
    private var observedSkyBlockHead: ItemStack? = null
    private var cachedPlayerProfile: GameProfile? = null
    private var cachedPlayerSkin: PlayerSkin? = null
    private var cachedPlayerHead: ItemStack? = null

    fun playerHead(client: Minecraft): ItemStack? {
        if (client.level == null) return null
        val player = client.player ?: return null
        // Player#getProfile wraps the spawn identity as already resolved. That identity may lack
        // textures even while PlayerInfo and the rendered player have the real downloaded skin.
        val profile = client.connection?.getPlayerInfo(player.uuid)?.profile ?: player.gameProfile
        val skin = player.skin
        if (cachedPlayerProfile != profile || cachedPlayerSkin != skin) {
            cachedPlayerProfile = profile
            cachedPlayerSkin = skin
            cachedPlayerHead = ItemStack(Items.PLAYER_HEAD).apply {
                set(DataComponents.PROFILE, profileForHead(profile, skin))
            }
        }
        return cachedPlayerHead?.copy()
    }

    /** Prefer the genuine server's SkyBlock Leveling item; the player head is the explicit neutral fallback. */
    fun skyBlockHead(client: Minecraft): ItemStack? = observedSkyBlockHead?.copy() ?: playerHead(client)

    fun observeMenu(client: Minecraft) {
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (SkyBlockParser.clean(screen.title.string) != "SkyBlock Menu") return
        for (slot in screen.menu.slots) {
            if (slot.container === client.player?.inventory) continue
            val item = slot.item
            if (item.isEmpty || item.item != Items.PLAYER_HEAD) continue
            if (SkyBlockParser.clean(item.hoverName.string) != "SkyBlock Leveling") continue
            observedSkyBlockHead = item.copy()
            return
        }
    }

    fun reset() {
        observedSkyBlockHead = null
        cachedPlayerProfile = null
        cachedPlayerSkin = null
        cachedPlayerHead = null
    }

    /** An identity-only profile must remain unresolved; "resolved" would freeze its default skin. */
    private fun profileForHead(profile: GameProfile, skin: PlayerSkin): ResolvableProfile {
        if (!profile.properties().get("textures").isEmpty()) return ResolvableProfile.createResolved(profile)
        // The rendered player may already have a downloaded skin even before texture properties
        // are visible in its tab profile. Preserve that observed URL as a native head property.
        // SkinManager's normal texture cache/downloader owns loading; this adapter does no HTTP.
        val body = skin.body()
        if (body is ClientAsset.DownloadedTexture) {
            val texture = JsonObject().apply {
                addProperty("url", body.url())
                if (skin.model() == PlayerModelType.SLIM) add("metadata", JsonObject().apply { addProperty("model", "slim") })
            }
            val payload = JsonObject().apply {
                add("textures", JsonObject().apply { add("SKIN", texture) })
            }
            val encoded = Base64.getEncoder().encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
            val properties = ImmutableMultimap.builder<String, Property>()
                .putAll(profile.properties()).put("textures", Property("textures", encoded)).build()
            return ResolvableProfile.createResolved(GameProfile(profile.id(), profile.name(), PropertyMap(properties)))
        }
        return ResolvableProfile.createUnresolved(profile.id())
    }
}
