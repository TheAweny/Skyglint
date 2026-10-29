package dev.skyglint.ui

import dev.skyglint.preview.*

import dev.skyglint.core.*
import dev.skyglint.features.ContextModules
import dev.skyglint.features.session.ServerClosingModule
import dev.skyglint.ui.presentation.*
import java.util.UUID
import kotlin.test.*

class ServerClosingCompositionTest {
    private var now = 0L
    private fun core() = CoreController().apply {
        ContextModules.create { now }.forEach(modules::register)
        enterSkyBlock("synthetic-server-closing", now)
        updateBoss(UUID(0, 918), "Unknown server boss", .5f, now = now)
        apply(listOf(CoreEvent.ContextObserved("Village"),
            CoreEvent.SkyBlockObserved(SkyBlockSnapshot(88, ProgressSnapshot(12.0, 100.0, .12))),
            CoreEvent.PetObserved(PetSnapshot("Tiger", 70))), now)
    }
    private fun CoreController.sidebar(time: String) = ingest(
        NormalizedSignal.SidebarSnapshot(listOf("§cServer closing: $time §8m77A")), now)

    @Test fun `hidden warning anchor preserves ordinary boss SB and pet after short introduction`() {
        val core = core()
        core.sidebar("03:11")
        val intro = HudComposition().compose(core.snapshot(now), now).single()
        assertEquals("Server closing", intro.title)
        assertEquals("3:11 left", intro.value)
        assertEquals(0xFFF28E8E.toInt(), intro.accent)
        assertNull(intro.progress) // Unknown total closing duration cannot become a percentage ring.
        now = 4_100
        core.sidebar("03:07")
        val shown = HudComposition().compose(core.snapshot(now), now)
        assertEquals(setOf("main", "skyblock", "pet"), shown.map { it.id }.toSet())
        assertEquals("Unknown server boss", shown.single { it.id == "main" }.title)
    }

    @Test fun `last minute uses additive live digits and no per second caption transition`() {
        val core = core()
        core.sidebar("00:55")
        val composer = HudComposition()
        val intro = composer.compose(core.snapshot(now), now).single()
        now = 4_100
        core.sidebar("00:51")
        val state = core.snapshot(now)
        val shown = composer.compose(state, now)
        val timer = shown.single { it.id.startsWith("metric:${ServerClosingModule.WIDGET_ID}:") }
        assertEquals(intro.id, timer.id)
        assertEquals(intro.identity, timer.identity)
        assertEquals(HudElementKind.COUNTER, timer.kind)
        assertEquals("0:51", timer.value)
        assertNull(timer.progress)
        assertTrue(timer.liveValue)
        assertNotNull(timer.coloredText)
        assertTrue(shown.any { it.id == "main" } && shown.any { it.id == "skyblock" } && shown.any { it.id == "pet" })
        val later = composer.compose(state, now + 1_000).single { it.id == timer.id }
        assertEquals("0:50", later.value)
        assertEquals(timer.visibleContentKey(0), later.visibleContentKey(0))
        assertEquals(later.value, timer.displayValue(later))
        now = 5_000
        core.ingest(NormalizedSignal.SidebarSnapshot(emptyList()), now)
        assertTrue(composer.compose(core.snapshot(now), now).none { it.id == timer.id })
    }

    @Test fun `registered confirmations remain single unstaged notices with no received reward prefix`() {
        val core = core()
        core.ingest(NormalizedSignal.ContextSnapshot("Oasis", "farming_1"), now)
        core.ingest(NormalizedSignal.Chat("[NPC] Trevor: You can find your TRACKABLE animal near the Oasis."), now)
        core.ingest(NormalizedSignal.Chat("Return to the Trapper soon to get a new animal to hunt!"), now)
        val composer = HudComposition()
        val early = composer.compose(core.snapshot(now), now).single()
        val late = composer.compose(core.snapshot(2_000), 2_000).single()
        assertEquals("Trevor hunt completed", early.title)
        assertEquals(early.visibleContentKey(0), late.visibleContentKey(0))
        assertNull(early.prefix)
        assertFalse(early.verticalTransition)
    }

    @Test fun `Bestiary is one final visible state rather than duplicated intro and result`() {
        val preview = ConfirmationPreviewTimeline(0)
        val composer = HudComposition()
        val first = composer.compose(preview.snapshot(22_000), 22_000).single()
        val later = composer.compose(preview.snapshot(23_800), 23_800).single()
        assertEquals("Bestiary · Milestone 12 → 13", first.title)
        assertEquals("Bestiary updated · Observed family II · Milestone 12 → 13", first.subtitle)
        assertEquals(first.visibleContentKey(0), later.visibleContentKey(0))
        assertNull(first.prefix)
        assertFalse(first.verticalTransition)
    }
}
