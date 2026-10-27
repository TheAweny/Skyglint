package dev.skyglint.ui

import dev.skyglint.core.ModuleWidget
import dev.skyglint.fabric.SkyglintClient
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.slf4j.LoggerFactory

/** Implementations are registered during client initialization and render on the client thread. */
fun interface WidgetRenderer {
    fun render(graphics: GuiGraphicsExtractor, widget: ModuleWidget, x: Int, y: Int, width: Int, height: Int)
}

/** Feature UI can expand without adding feature-specific branches to the shared HUD renderer. */
object UiWidgetRegistry {
    private val renderers = linkedMapOf<String, WidgetRenderer>()
    private val quarantined = mutableSetOf<String>()
    private val logger = LoggerFactory.getLogger("Skyglint/Widgets")

    fun register(moduleId: String, renderer: WidgetRenderer) {
        require(moduleId.isNotBlank()) { "Widget module id must not be blank" }
        require(moduleId !in renderers) { "Renderer already registered for $moduleId" }
        renderers[moduleId] = renderer
    }

    fun find(moduleId: String): WidgetRenderer? = renderers[moduleId].takeUnless { moduleId in quarantined }

    /** A broken contributor is attempted once per client lifetime, then the shared fallback takes over. */
    fun render(graphics: GuiGraphicsExtractor, widget: ModuleWidget, x: Int, y: Int, width: Int, height: Int): Boolean {
        val renderer = find(widget.id) ?: return false
        return try {
            renderer.render(graphics, widget, x, y, width, height)
            true
        } catch (failure: Exception) {
            quarantined += widget.id
            logger.error("Quarantined HUD renderer {} until client restart; using default widget", widget.id, failure)
            SkyglintClient.diagnostics.record("ui", "renderer_quarantined", mapOf("module" to widget.id, "error" to failure.javaClass.simpleName))
            false
        }
    }
}
