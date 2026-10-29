package dev.skyglint.features.winter

import dev.skyglint.core.*
import dev.skyglint.features.TemporaryOccurrenceModule
import dev.skyglint.ui.presentation.HudComposition
import dev.skyglint.ui.presentation.HudPreferences
import java.util.UUID
import kotlin.test.*

class ReindrakeModuleTest {
    private var now = 0L
    private val module = ReindrakeModule { now }
    private val first = UUID(0, 701)
    private val second = UUID(0, 702)
    private fun context(mode: String? = "winter", location: String? = null) =
        module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun bosses(vararg bars: BossBarState) = module.parse(NormalizedSignal.BossSnapshot(bars.toList()))
    private fun List<CoreEvent>.widgets() = filterIsInstance<CoreEvent.WidgetObserved>().map { it.widget }

    @Test fun `exact bar in fresh winter context represents only its observed UUID and packet fill`() {
        context()
        val result = bosses(BossBarState(first, "§6Reindrake", .37f),
            BossBarState(second, "Future unknown winter boss", .8f))
        val widget = result.widgets().single()
        assertEquals("skyglint.winter.reindrake:$first", widget.id)
        assertEquals(setOf(first), widget.replacedBossIds)
        assertEquals(WidgetRole.PRIMARY, widget.presentation!!.role)
        assertEquals("Reindrake", widget.title)
        assertNull(widget.detail)
        assertEquals(.37f.toDouble(), widget.progress!!.fraction)
        assertNull(widget.progress!!.current)
        assertNull(widget.progress!!.maximum)
        assertEquals(3_000L, widget.expiresAt)
        assertTrue(result.none { it is CoreEvent.NoticeObserved || it is CoreEvent.RewardObserved })
    }

    @Test fun `context is official winter only and cannot be guessed from location or title`() {
        assertTrue(bosses(BossBarState(first, "Reindrake", .5f)).isEmpty())
        context("hub", "Jerry's Workshop")
        assertTrue(bosses(BossBarState(first, "Reindrake", .5f)).isEmpty())
        context(null, "Jerry's Workshop")
        assertTrue(bosses(BossBarState(first, "Reindrake", .5f)).isEmpty())
        context()
        assertEquals(1, bosses(BossBarState(first, "Reindrake", .5f)).widgets().size)
        val exit = context("hub")
        assertEquals("skyglint.winter.reindrake:$first", assertIs<CoreEvent.WidgetRemoved>(exit.single()).id)
    }

    @Test fun `two actual bars stay independent and unknown boss remains reachable by normal rotation`() {
        context()
        val unknown = UUID(0, 703)
        val source = listOf(BossBarState(first, "Reindrake", .8f), BossBarState(second, "Reindrake", .4f),
            BossBarState(unknown, "Unrecognized dragon", .2f))
        val widgets = module.parse(NormalizedSignal.BossSnapshot(source)).widgets()
        assertEquals(2, widgets.size)
        assertEquals(2, widgets.map { it.id }.toSet().size)
        assertEquals(listOf(setOf(first), setOf(second)), widgets.map { it.replacedBossIds })
        val state = HudSnapshot(true, "winter", source, null, null, null, widgets)
        val composer = HudComposition()
        val shown = listOf(0L, 6_000L, 12_000L).map { at -> composer.compose(
            state.copy(widgets = widgets.map { it.copy(expiresAt = null) }), at, HudPreferences()).single() }
        assertEquals(listOf(widgets[0].id, widgets[1].id, "boss:$unknown"), shown.map { it.identity })
    }

    @Test fun `full snapshot absence and changed unrecognized title remove previous claims`() {
        context()
        bosses(BossBarState(first, "Reindrake", .8f), BossBarState(second, "Reindrake", .4f))
        val result = bosses(BossBarState(second, "Unknown replacement", .7f))
        assertEquals(setOf("skyglint.winter.reindrake:$first", "skyglint.winter.reindrake:$second"),
            result.filterIsInstance<CoreEvent.WidgetRemoved>().map { it.id }.toSet())
        assertTrue(result.widgets().isEmpty())
        assertTrue(bosses().isEmpty())
    }

    @Test fun `zero is observed fill not a death and invalid progress remains unknown`() {
        context()
        val zero = bosses(BossBarState(first, "Reindrake", 0f)).widgets().single()
        assertEquals(0.0, zero.progress!!.fraction)
        for (progress in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            val result = bosses(BossBarState(first, "Reindrake", progress))
            assertNull(result.widgets().single().progress)
            assertTrue(result.none { it is CoreEvent.NoticeObserved || it is CoreEvent.RewardObserved })
        }
    }

    @Test fun `unverified health title forms remain generic instead of inventing HP or hits`() {
        context()
        for (name in listOf("Reindrake 2,500❤", "Reindrake 1200/2500", "Reindrake · Gifts 50", "[Lv100] Reindrake",
            "Reindrake defeated!", "reindrake", "TWO Reindrakes", "Reindrake\n")) {
            assertTrue(bosses(BossBarState(first, name, .5f)).widgets().isEmpty(), name)
        }
    }

    @Test fun `summon catch and unverified end chat neither duplicate notices nor manufacture persistent presence`() {
        context()
        val existing = TemporaryOccurrenceModule { now }
        existing.parse(NormalizedSignal.ContextSnapshot(null, "winter"))
        val summon = NormalizedSignal.Chat("WOAH! [MVP+] Player summoned TWO Reindrakes from the depths!")
        assertEquals(1, existing.parse(summon).filterIsInstance<CoreEvent.NoticeObserved>().size)
        assertTrue(module.parse(summon).isEmpty())
        assertTrue(module.parse(NormalizedSignal.Chat("A Reindrake forms from the depths.")).isEmpty())
        bosses(BossBarState(first, "Reindrake", .5f))
        assertTrue(module.parse(NormalizedSignal.Chat("DEFEATED! A Reindrake was slain and dropped all its loot!")).isEmpty())
        assertEquals(setOf(first), bosses(BossBarState(first, "Reindrake", .5f)).widgets().single().replacedBossIds)
    }

    @Test fun `repeated context never refreshes old boss data and expiry creates no kill or reward`() {
        context()
        bosses(BossBarState(first, "Reindrake", .5f))
        now = 2_000
        assertEquals(3_000L, context().widgets().single().expiresAt)
        now = 3_000
        val expired = context()
        assertIs<CoreEvent.WidgetRemoved>(expired.single())
        assertTrue(module.parse(NormalizedSignal.Chat("anything")).isEmpty())
        assertTrue(context().isEmpty())
        now = 4_000
        assertEquals(1, bosses(BossBarState(first, "Reindrake", .2f)).widgets().size)
    }

    @Test fun `stale context backwards clock and session lifecycle cannot retain a claim`() {
        context();bosses(BossBarState(first, "Reindrake", .5f))
        now = 5_000
        assertIs<CoreEvent.WidgetRemoved>(bosses(BossBarState(first, "Reindrake", .5f)).single())
        context();bosses(BossBarState(first, "Reindrake", .5f))
        now = 4_999
        assertIs<CoreEvent.WidgetRemoved>(module.parse(NormalizedSignal.Chat("any")).single())
        context();bosses(BossBarState(first, "Reindrake", .5f))
        module.onSessionEnded()
        assertTrue(bosses(BossBarState(first, "Reindrake", .5f)).isEmpty())
        module.onSessionStarted("new-winter-server")
        assertTrue(bosses(BossBarState(first, "Reindrake", .5f)).isEmpty())
    }
}
