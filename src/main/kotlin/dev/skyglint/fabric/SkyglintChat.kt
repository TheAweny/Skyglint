package dev.skyglint.fabric

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import dev.skyglint.updates.github.UpdateImportance

/** Native chat components; only the explicit action carries a click event. */
object SkyglintChat {
    enum class Tone(val color: Int) {
        INFO(0xBAC7D2), SUCCESS(0xA8D6B7), WARNING(0xE1C18B), ERROR(0xE9A4A0),
    }

    private const val ACCENT = 0xA8D4E6
    private const val QUIET = 0x687F91

    fun message(text: String, tone: Tone = Tone.INFO): MutableComponent = prefix()
        .append(Component.literal(text).setStyle(Style.EMPTY.withColor(tone.color)))

    fun updateAvailable(version: String): MutableComponent = updateAvailable(version, UpdateImportance.NORMAL)

    fun updateAvailable(version: String, importance: UpdateImportance): MutableComponent = prefix()
        .append(Component.literal(when (importance) {
            UpdateImportance.NORMAL -> "Update available "
            UpdateImportance.IMPORTANT -> "Important update available "
            UpdateImportance.CRITICAL -> "Critical update available "
        }).setStyle(Style.EMPTY.withColor(when (importance) {
            UpdateImportance.NORMAL -> Tone.INFO.color
            UpdateImportance.IMPORTANT -> Tone.WARNING.color
            UpdateImportance.CRITICAL -> Tone.ERROR.color
        })))
        .append(Component.literal(version).setStyle(Style.EMPTY.withColor(ACCENT).withBold(true)))
        .append(Component.literal("  ").setStyle(Style.EMPTY.withColor(QUIET)))
        .append(Component.literal("[View update]").setStyle(Style.EMPTY.withColor(ACCENT)
            .withUnderlined(true).withClickEvent(ClickEvent.RunCommand("/skyglint update"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("Review the release and choose whether to download it.")
                .setStyle(Style.EMPTY.withColor(Tone.INFO.color))))))

    fun send(text: String, tone: Tone = Tone.INFO) {
        Minecraft.getInstance().player?.sendSystemMessage(message(text, tone))
    }

    private fun prefix(): MutableComponent = Component.empty()
        .append(Component.literal("[").setStyle(Style.EMPTY.withColor(QUIET)))
        .append(Component.literal("Skyglint").setStyle(Style.EMPTY.withColor(ACCENT)))
        .append(Component.literal("] ").setStyle(Style.EMPTY.withColor(QUIET)))
}
