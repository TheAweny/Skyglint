package dev.skyglint.features.winter

import dev.skyglint.core.*
import dev.skyglint.ui.presentation.HudComposition
import java.util.UUID
import kotlin.test.*

class JerryDefenseBossTest {
    private var now = 0L
    private val module = JerryDefenseModule { now }
    private val id = UUID(0, 47)
    private val raw = "§aⒶ §eⒷ §fⒸ §cⒹ §cⓍ"
    private fun context() = module.parse(NormalizedSignal.ContextSnapshot("Jerry's Workshop", "winter"))
    private fun wave() = module.parse(NormalizedSignal.SidebarSnapshot(listOf("Wave 3")))
    private fun bosses(vararg values: BossBarState) = module.parse(NormalizedSignal.BossSnapshot(values.toList()))
    private fun bar(uuid: UUID = id) = BossBarState(uuid, SkyBlockParser.clean(raw), .64f, formattedTitle = raw)
    private fun List<CoreEvent>.primary() = filterIsInstance<CoreEvent.WidgetObserved>().single { it.widget.id == JerryDefenseModule.WIDGET_ID }.widget

    @Test fun `only confirmed raid represents colored points and claims their precise UUID`() {
        context()
        assertTrue(bosses(bar()).isEmpty())
        val widget = wave().primary()
        assertEquals(setOf(id), widget.replacedBossIds)
        assertEquals(5, widget.presentation!!.metrics.size)
        assertTrue(widget.presentation.metrics.all { it.referenceOnly })
        assertEquals("destroyed", widget.presentation.metrics.last().value)
        assertEquals(.64f.toDouble(), widget.progress!!.fraction)
        assertNull(widget.progress.current)
    }

    @Test fun `missing renamed duplicated or stale bars relinquish claims without declaring victory`() {
        context(); wave(); bosses(bar())
        assertTrue(bosses().primary().replacedBossIds.isEmpty())
        assertTrue(bosses(bar(), bar(UUID(0, 48))).primary().replacedBossIds.isEmpty())
        assertTrue(bosses(bar().copy(title = "Quest", formattedTitle = "Quest")).primary().replacedBossIds.isEmpty())
        bosses(bar())
        now = 3_000
        context()
        assertTrue(wave().primary().replacedBossIds.isEmpty())
    }

    @Test fun `unrelated bosses remain candidates and point metrics never leak into helpers`() {
        context(); wave()
        val widget = bosses(bar()).primary()
        val other = BossBarState(UUID(0, 49), "Unknown server objective", .3f)
        val snapshot = HudSnapshot(true, "test", listOf(bar(), other), null, null, null, widgets = listOf(widget))
        val composition = HudComposition()
        val first = composition.compose(snapshot, now)
        assertEquals(1, first.size)
        assertEquals(widget, first.single().sourceWidget)
        assertTrue((0L..14_000L step 1_000).map { composition.compose(snapshot.copy(widgets=listOf(widget.copy(expiresAt=null))), it).single().title }
            .contains(other.title))
    }

    @Test fun `a fresh inactive sidebar retires old point state before another defense activation`() {
        context(); wave(); bosses(bar())
        now = 1_000
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Event Start: 0:10")))
        now = 2_000
        assertTrue(wave().primary().replacedBossIds.isEmpty())
        assertEquals(setOf(id), bosses(bar()).primary().replacedBossIds)
    }
}
