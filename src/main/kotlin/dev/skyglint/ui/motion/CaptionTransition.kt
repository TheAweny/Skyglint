package dev.skyglint.ui.motion

/** Interrupted text changes fade from the actual currently visible mixture, never from a stale label. */
class CaptionTransition(initial: String) {
    data class Layer(val text: String, val opacity: Double)
    private var from = listOf(Layer(initial, 1.0))
    private var target = initial
    private var elapsed = 1.0
    private val duration = .22

    fun update(text: String, seconds: Double): List<Layer> {
        require(seconds.isFinite() && seconds >= 0)
        if (text != target) {
            from = layers().filter { it.opacity > .005 }
            target = text
            elapsed = 0.0
        }
        elapsed = (elapsed + seconds).coerceAtMost(duration)
        return layers()
    }

    private fun layers(): List<Layer> {
        if (elapsed >= duration) return listOf(Layer(target, 1.0))
        val t = (elapsed / duration).coerceIn(0.0, 1.0)
        val incoming = t * t * (3 - 2 * t)
        val result = linkedMapOf<String, Double>()
        from.forEach { result[it.text] = (result[it.text] ?: 0.0) + it.opacity * (1 - incoming) }
        result[target] = (result[target] ?: 0.0) + incoming
        return result.map { Layer(it.key, it.value) }
    }
}
