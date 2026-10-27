package dev.skyglint.fabric

import net.hypixel.data.type.GameType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.minecraft.client.Minecraft
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents

/** Official shared API transport; no REST keys, polling commands or duplicate plugin-channel handlers. */
class HypixelContextSource(private val changed: (Context) -> Unit) {
    data class Context(val skyBlock: Boolean, val server: String, val mode: String?)
    var current: Context? = null; private set
    @Volatile private var generation = 0L

    fun initialize() {
        val api = HypixelModAPI.getInstance()
        ClientConfigurationConnectionEvents.INIT.register { _, _ -> reset() }
        ClientConfigurationConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        api.subscribeToEventPacket(ClientboundLocationPacket::class.java)
        api.createHandler(ClientboundLocationPacket::class.java) { packet ->
            val client = Minecraft.getInstance()
            // Location may arrive during CONFIGURATION, before a play listener exists.
            val observedGeneration = generation
            val observation = Context(packet.serverType.orElse(null) == GameType.SKYBLOCK, packet.serverName, packet.mode.orElse(null))
            client.execute {
                if (generation == observedGeneration && current != observation) {
                    current = observation
                    changed(observation)
                }
            }
        }
    }

    fun reset() { generation++; current = null }
}
