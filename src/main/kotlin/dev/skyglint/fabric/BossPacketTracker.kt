package dev.skyglint.fabric

import dev.skyglint.core.BossBarState
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.world.BossEvent
import java.util.UUID

/** Exact packet values; rendering interpolation never pollutes diagnostic observations. */
class BossPacketTracker(private val changed: (String, UUID, Map<String, Any?>) -> Unit) : ClientboundBossEventPacket.Handler {
    val states = linkedMapOf<UUID, BossBarState>()
    private val duplicates = linkedMapOf<String, Long>()
    private val properties = mutableMapOf<UUID, Triple<Boolean, Boolean, Boolean>>()
    private fun repeated(action: String) { duplicates[action] = (duplicates[action] ?: 0) + 1 }
    fun drainDuplicates(): Map<String, Long> = duplicates.toMap().also { duplicates.clear() }
    override fun add(id: UUID, name: Component, progress: Float, color: BossEvent.BossBarColor, overlay: BossEvent.BossBarOverlay,
                     darken: Boolean, music: Boolean, fog: Boolean) {
        states[id] = BossBarState(id, name.string, progress, color.name, overlay.name, formatted(name))
        properties[id] = Triple(darken, music, fog)
        changed("add", id, details(states.getValue(id)) + mapOf("darken" to darken, "music" to music, "fog" to fog))
    }
    override fun remove(id: UUID) {
        states.remove(id)
        properties.remove(id)
        changed("remove", id, emptyMap())
    }
    override fun updateProgress(id: UUID, progress: Float) {
        if (states[id]?.progress == progress) { repeated("progress"); return }
        states[id]?.let { states[id] = it.copy(progress = progress) }
        changed("progress", id, mapOf("progress" to progress))
    }
    override fun updateName(id: UUID, name: Component) {
        val formatted = formatted(name)
        if (states[id]?.let { it.title == name.string && it.formattedTitle == formatted } == true) { repeated("name"); return }
        states[id]?.let { states[id] = it.copy(title = name.string, formattedTitle = formatted) }
        changed("name", id, mapOf("title" to name.string, "formattedTitle" to formatted))
    }
    override fun updateStyle(id: UUID, color: BossEvent.BossBarColor, overlay: BossEvent.BossBarOverlay) {
        if (states[id]?.let { it.color == color.name && it.overlay == overlay.name } == true) { repeated("style"); return }
        states[id]?.let { states[id] = it.copy(color = color.name, overlay = overlay.name) }
        changed("style", id, mapOf("color" to color.name, "overlay" to overlay.name))
    }
    override fun updateProperties(id: UUID, darken: Boolean, music: Boolean, fog: Boolean) {
        val observed = Triple(darken, music, fog)
        if (properties[id] == observed) { repeated("properties"); return }
        properties[id] = observed
        changed("properties", id, mapOf("darken" to darken, "music" to music, "fog" to fog))
    }
    fun clear() { states.clear(); properties.clear(); duplicates.clear() }
    private fun details(state: BossBarState): Map<String, Any?> = mapOf("title" to state.title,
        "progress" to state.progress, "color" to state.color, "overlay" to state.overlay,
        "formattedTitle" to state.formattedTitle)

    private fun formatted(name: Component): String {
        val text = StringBuilder()
        name.visit<Unit>({ style, run ->
            text.append('§').append(PALETTE[style.color?.value] ?: 'r').append(run)
            java.util.Optional.empty()
        }, Style.EMPTY)
        return text.toString()
    }
    companion object {
        private val PALETTE = mapOf(0x000000 to '0', 0x0000AA to '1', 0x00AA00 to '2', 0x00AAAA to '3',
            0xAA0000 to '4', 0xAA00AA to '5', 0xFFAA00 to '6', 0xAAAAAA to '7', 0x555555 to '8',
            0x5555FF to '9', 0x55FF55 to 'a', 0x55FFFF to 'b', 0xFF5555 to 'c', 0xFF55FF to 'd',
            0xFFFF55 to 'e', 0xFFFFFF to 'f')
    }
}
