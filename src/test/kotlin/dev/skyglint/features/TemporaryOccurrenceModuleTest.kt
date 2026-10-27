package dev.skyglint.features

import dev.skyglint.core.*
import kotlin.test.*

class TemporaryOccurrenceModuleTest {
    private var now = 0L
    private val module = TemporaryOccurrenceModule { now }
    private fun context(mode: String? = "hub", location: String? = null) = module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun chat(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun List<CoreEvent>.notice() = assertIs<CoreEvent.NoticeObserved>(single()).request

    @Test fun `explicit primal fear announcement produces a short occurrence without guessed cooldown`() {
        context()
        val notice = chat("§5FEAR. A Primal Fear has been summoned!").notice()
        assertEquals("Primal Fear summoned!", notice.title)
        assertEquals("minecraft:carved_pumpkin", notice.item?.vanillaItemId)
        assertNull(notice.item?.itemName)
        assertNull(notice.detail)
        assertEquals(4_000L, notice.durationMillis)
        assertTrue(chat("FEAR. A Primal Fear has been summoned!").isEmpty())
        now = 6_000
        context()
        assertNotEquals(notice.id, chat("FEAR. A Primal Fear has been summoned!").notice().id)
    }

    @Test fun `reindrake requires workshop context preserves observed two count and does not parse player chat`() {
        val one = "WOAH! [VIP] Georeek summoned a Reindrake from the depths!"
        val two = "WOAH! [MVP+] DulceLyncis summoned TWO Reindrakes from the depths!"
        context("hub", "Jerry's Workshop")
        assertTrue(chat(one).isEmpty())
        context("winter")
        assertEquals("Reindrake summoned!", chat(one).notice().title)
        assertEquals("2 Reindrakes summoned!", chat(two).notice().title)
        assertTrue(chat("[MVP+] Player: $one").isEmpty())
        assertTrue(chat("WOAH! Player summoned THREE Reindrakes from the depths!").isEmpty())
    }

    @Test fun `missing stale context session end and unrelated source cannot create occurrences`() {
        val fear = "FEAR. A Primal Fear has been summoned!"
        assertTrue(chat(fear).isEmpty())
        context()
        now = 5_000
        assertTrue(chat(fear).isEmpty())
        context()
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf(fear))).isEmpty())
        assertTrue(chat("[VIP] Player: $fear").isEmpty())
        assertTrue(chat("FEAR. A Primal Fear will spawn soon!").isEmpty())
        module.onSessionEnded()
        assertTrue(chat(fear).isEmpty())
        module.onSessionStarted("new-server")
        context()
        assertNotNull(chat(fear).notice())
    }
}
