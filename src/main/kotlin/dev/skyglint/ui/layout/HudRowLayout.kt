package dev.skyglint.ui.layout

/** Measured presentation slots, independent of captions, native drawing and Minecraft GUI scale. */
object HudRowLayout {
    data class Item(val slotId: String, val occupiedWidth: Double, val visibility: Double, val visible: Boolean)
    data class Placement(val slotId: String, val bounds: HudBounds)
    data class Result(val width: Double, val placements: List<Placement>)

    fun place(items: List<Item>, height: Double = 30.0, gap: Double = 5.0): Result {
        require(height.isFinite() && height > 0 && gap.isFinite() && gap >= 0)
        require(items.map { it.slotId }.distinct().size == items.size) { "Row slot IDs must be unique" }
        var cursor = 0.0
        val placements = items.map { item ->
            require(item.occupiedWidth.isFinite() && item.occupiedWidth >= 0)
            require(item.visibility.isFinite() && item.visibility in 0.0..1.0)
            Placement(item.slotId, HudBounds(cursor, 0.0, item.occupiedWidth, height)).also {
                cursor += item.occupiedWidth + gap * item.visibility
            }
        }
        val trailingGap = gap * (items.lastOrNull { it.visible }?.visibility ?: 0.0)
        return Result((cursor - trailingGap).coerceAtLeast(1.0), placements)
    }
}

data class HudBounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun transform(originX: Double, originY: Double, scale: Double): HudBounds {
        require(scale.isFinite() && scale > 0)
        return HudBounds(originX + x * scale, originY + y * scale, width * scale, height * scale)
    }

    fun contains(pointX: Double, pointY: Double): Boolean =
        pointX >= x && pointX < x + width && pointY >= y && pointY < y + height
}
