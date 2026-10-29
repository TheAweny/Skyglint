package dev.skyglint.ui

import dev.skyglint.core.ModuleWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.slf4j.LoggerFactory

/** Implementations are registered during client initialization and render on the client thread. */
fun interface WidgetRenderer {
    fun render(graphics: GuiGraphicsExtractor, widget: ModuleWidget, x: Int, y: Int, width: Int, height: Int)
}

/** Measures a widget body in design units; the ordinary row retains its fixed 30-unit height. */
fun interface WidgetWidthMeasure {
    fun measure(widget: ModuleWidget, fallbackWidth: Double): Double
}

/** Feature UI can expand without adding feature-specific branches to the shared HUD renderer. */
object UiWidgetRegistry {
    private data class Registration(val renderer: WidgetRenderer, val measure: WidgetWidthMeasure? = null)
    private val renderers = linkedMapOf<String, Registration>()
    private val quarantined = mutableSetOf<String>()
    private val logger = LoggerFactory.getLogger("Skyglint/Widgets")
    /** Optional application diagnostics binding, installed during client initialization. */
    var onFailure: ((String, Exception) -> Unit)? = null

    fun register(moduleId: String, renderer: WidgetRenderer) {
        register(moduleId, Registration(renderer))
    }

    /** Backward-compatible opt-in measurement; renderer and measurement share failure isolation. */
    fun register(moduleId: String, renderer: WidgetRenderer, measure: WidgetWidthMeasure) {
        register(moduleId, Registration(renderer, measure))
    }

    private fun register(moduleId: String, registration: Registration) {
        require(moduleId.isNotBlank()) { "Widget module id must not be blank" }
        require(moduleId !in renderers) { "Renderer already registered for $moduleId" }
        renderers[moduleId] = registration
    }

    fun find(moduleId: String): WidgetRenderer? = renderers[moduleId]?.renderer.takeUnless { moduleId in quarantined }

    fun measureWidth(widget: ModuleWidget, fallbackWidth: Double): Double {
        val measure = renderers[widget.id]?.measure.takeUnless { widget.id in quarantined } ?: return fallbackWidth
        return try {
            val measured = measure.measure(widget, fallbackWidth)
            require(measured.isFinite() && measured > 0) { "Widget width must be finite and positive" }
            measured.coerceIn(30.0, 320.0)
        } catch (failure: Exception) {
            quarantine(widget.id, failure)
            fallbackWidth
        }
    }

    /** A broken contributor is attempted once per client lifetime, then the shared fallback takes over. */
    fun render(graphics: GuiGraphicsExtractor, widget: ModuleWidget, x: Int, y: Int, width: Int, height: Int): Boolean {
        val renderer = find(widget.id) ?: return false
        return try {
            renderer.render(graphics, widget, x, y, width, height)
            true
        } catch (failure: Exception) {
            quarantine(widget.id, failure)
            false
        }
    }

    private fun quarantine(id: String, failure: Exception) {
        quarantined += id
        logger.error("Quarantined HUD renderer {} until client restart; using default widget", id, failure)
        try { onFailure?.invoke(id, failure) } catch (diagnosticFailure: Exception) {
            logger.warn("Could not report quarantined HUD renderer {}", id, diagnosticFailure)
        }
    }
}
