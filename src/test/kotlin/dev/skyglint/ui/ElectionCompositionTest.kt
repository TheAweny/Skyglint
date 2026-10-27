package dev.skyglint.ui

import dev.skyglint.core.ElectionPreviewTimeline
import dev.skyglint.core.*
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.HudElementKind
import kotlin.test.*
import org.junit.jupiter.api.Test

class ElectionCompositionTest {
    @Test fun `contributor inline role composes one strip without requiring a known feature id`() {
        val widget = ModuleWidget("example.inline", "Sample", presentation = WidgetPresentation(WidgetRole.INLINE,
            metrics = listOf(WidgetMetric("a", "First", "Observed first", "minecraft:book", 10),
                WidgetMetric("b", "Second", "Observed second", "minecraft:paper", 9))))
        val snapshot = HudSnapshot(true, "test", emptyList(), null, null, null, widgets = listOf(widget))
        val strip = HudComposition().compose(snapshot, 0).single()
        assertEquals(HudElementKind.STRIP, strip.kind)
        assertEquals("Sample", strip.title)
        assertEquals(listOf("Observed first", "Observed second"), strip.parts.map { it.text })
    }
    @Test fun `Community Center composes all ranked candidates inside one strip`() {
        val timeline = ElectionPreviewTimeline(0)
        assertTrue(HudComposition().compose(timeline.snapshot(0), 0).isEmpty())
        val elements = HudComposition().compose(timeline.snapshot(500), 500)
        val strip = elements.single { it.kind == HudElementKind.STRIP }
        assertEquals("Election", strip.title)
        assertTrue(elements.none { it.kind == HudElementKind.COUNTER })
        val candidates = strip.parts
        assertEquals(5, candidates.size)
        assertTrue(candidates.first().text.startsWith("Aatrox "))
        assertTrue(candidates.any { it.text.startsWith("Diana ") })
        assertTrue(elements.none { it.title.contains("Minister") || it.title.contains("Mayor elected") })
    }
}
