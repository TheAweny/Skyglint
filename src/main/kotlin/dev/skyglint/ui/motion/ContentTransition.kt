package dev.skyglint.ui.motion

/** Retargetable payload crossfade with optional vertical page motion; current layer positions never reset. */
class ContentTransition {
    data class Layer(val key: String, val opacity: Double, val offsetY: Double, val offsetX: Double = 0.0)
    private class Moving(val key: String, opacity: Double, offset: Double) {
        val opacity = CriticalSpring(opacity, 23.0)
        val offset = CriticalSpring(offset, 23.0)
    }
    private val moving = linkedMapOf<String, Moving>()
    private var target: String? = null
    private var vertical = false
    private var horizontal = false

    fun update(key: String, seconds: Double, vertical: Boolean = false, horizontal: Boolean = false): List<Layer> {
        require(seconds.isFinite() && seconds >= 0)
        require(!vertical || !horizontal)
        if (key != target) {
            val first = moving.isEmpty()
            moving.getOrPut(key) { Moving(key, if (first) 1.0 else 0.0,
                if (first || (!vertical && !horizontal)) 0.0 else if (horizontal) HORIZONTAL_DISTANCE else DISTANCE) }
            target = key
            this.vertical = vertical
            this.horizontal = horizontal
        }
        val result = moving.values.map { layer ->
            val incoming = layer.key == target
            val offset = layer.offset.step(if (incoming || (!this.vertical && !this.horizontal)) 0.0
                else if (this.horizontal) -HORIZONTAL_DISTANCE else -DISTANCE, seconds)
            Layer(layer.key, layer.opacity.step(if (incoming) 1.0 else 0.0, seconds).coerceIn(0.0, 1.0),
                if (this.vertical) offset else 0.0, if (this.horizontal) offset else 0.0)
        }
        moving.entries.removeIf { (id, layer) -> id != target && layer.opacity.value < .002 }
        if (moving.size > 8) moving.entries.filter { it.key != target }.minByOrNull { it.value.opacity.value }
            ?.let { moving.remove(it.key) }
        return result.filter { it.opacity > .002 }
    }

    fun reset() { moving.clear(); target = null; vertical = false; horizontal = false }
    companion object { private const val DISTANCE = 11.0; private const val HORIZONTAL_DISTANCE = 18.0 }
}
