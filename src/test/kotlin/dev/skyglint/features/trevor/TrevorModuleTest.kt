package dev.skyglint.features.trevor

import dev.skyglint.core.*
import kotlin.test.*

class TrevorModuleTest {
    private var now = 0L
    private val module = TrevorModule { now }
    private fun context(mode: String? = "farming_1", location: String? = "Trapper's Den") =
        module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun chat(text: String) = module.parse(NormalizedSignal.Chat(text))
    private fun assign(rarity: String = "TRACKABLE", area: String = "Desert Mountain") =
        chat("[NPC] Trevor: You can find your $rarity animal near the $area.")
    private fun List<CoreEvent>.notice() = assertIs<CoreEvent.NoticeObserved>(single()).request

    @Test fun `observed assignment stays hidden until a confirmed outcome and carries no invented reward`() {
        context()
        assertTrue(assign().isEmpty())
        assertTrue(module.parse(NormalizedSignal.TabSnapshot(listOf("Time Left: 5:00"))).isEmpty())
        val notice = chat(RETURN).notice()
        assertEquals("Trevor hunt completed", notice.title)
        assertEquals("minecraft:compass", notice.item?.vanillaItemId)
        assertNull(notice.item?.itemName)
        assertNull(notice.rarity)
        assertNull(notice.magicFind)
        assertNull(notice.countdown)
        assertNull(notice.detail)
        assertTrue(notice.pages.isEmpty())
        assertEquals(4_000L, notice.durationMillis)
        assertEquals(5_000L, notice.freshnessMillis)
    }

    @Test fun `timeout is a different confirmed state and each quest accepts only one outcome`() {
        context(); assign()
        val notice = chat(TIMEOUT).notice()
        assertEquals("Trevor hunt timed out", notice.title)
        assertEquals("minecraft:clock", notice.item?.vanillaItemId)
        assertTrue(chat(TIMEOUT).isEmpty())
        assertTrue(chat(RETURN).isEmpty())
        now = 20_000; context(); assign("ELUSIVE")
        assertNotEquals(notice.id, chat(RETURN).notice().id)
    }

    @Test fun `outcomes require an assignment rather than generic tab timers hints or chat echoes`() {
        context()
        assertTrue(chat(RETURN).isEmpty())
        assertTrue(chat(TIMEOUT).isEmpty())
        module.parse(NormalizedSignal.TabSnapshot(listOf("Trapper:", "Location: Desert Mountain", "Time Left: 4:59")))
        chat("The target is around 40 blocks above, at a 45 degrees angle!")
        assertTrue(chat(RETURN).isEmpty())
        assertTrue(chat(TIMEOUT).isEmpty())
    }

    @Test fun `official island mode is mandatory even if the location text resembles Trevor`() {
        for (mode in listOf(null, "barn", "hub", "garden", "farming_2")) {
            context(mode); assign()
            assertTrue(chat(RETURN).isEmpty(), "Rejected mode: $mode")
        }
        context(); assign(); context("hub"); context()
        assertTrue(chat(RETURN).isEmpty())
        assign()
        assertNotNull(chat(RETURN).notice())
    }

    @Test fun `NPC assignment grammar rejects malformed payloads unknown rarities areas and player prefixes`() {
        context()
        val valid = "[NPC] Trevor: You can find your TRACKABLE animal near the Desert Mountain."
        val malformed = listOf(
            "[VIP] Player: $valid", "Party > Player: $valid", valid + " extra",
            valid.replace("Trevor:", "Talbot:"), valid.replace("TRACKABLE", "RARE"),
            valid.replace("Desert Mountain", "Village"), valid.replace("Desert Mountain", "A".repeat(65)),
            valid.replace("Desert Mountain", "Desert\nMountain"), valid.replace("TRACKABLE", ""),
            valid.replace("Mountain.", "Mountain!"), "x".repeat(257),
        )
        for (line in malformed) {
            assertTrue(chat(line).isEmpty())
            assertTrue(chat(RETURN).isEmpty(), "No quest from: $line")
        }
    }

    @Test fun `formatted source evidence accepts all five known rarities and six known regions`() {
        val rarities = listOf("TRACKABLE", "UNTRACKABLE", "UNDETECTED", "ENDANGERED", "ELUSIVE")
        val areas = listOf("Oasis", "Mushroom Gorge", "Overgrown Mushroom Cave", "Desert Settlement",
            "Glowing Mushroom Cave", "Desert Mountain")
        for ((index, area) in areas.withIndex()) {
            now = index * 20_000L; context()
            assertTrue(chat("§e[NPC] §6Trevor§f: You can find your §a${rarities[index % rarities.size]}§r animal near the §e$area§r.").isEmpty())
            assertNotNull(chat("§a$RETURN").notice())
        }
    }

    @Test fun `player-prefixed or malformed outcome does not consume a valid assignment`() {
        context(); assign()
        for (line in listOf("[MVP+] Player: $RETURN", "Guild > Player: $TIMEOUT", "$RETURN extra", "$TIMEOUT\n"))
            assertTrue(chat(line).isEmpty())
        assertNotNull(chat(TIMEOUT).notice())
    }

    @Test fun `duplicate assignment after outcome is suppressed but later identical genuine assignment can rearm`() {
        context(); assign()
        val first = chat(RETURN).notice()
        now = 1_000
        assign()
        assertTrue(chat(RETURN).isEmpty())
        assertTrue(chat(TIMEOUT).isEmpty())
        for (second in 4..20 step 4) {
            now = second * 1_000L
            context()
        }
        assign()
        assertNotEquals(first.id, chat(RETURN).notice().id)
    }

    @Test fun `new different authoritative assignment replaces old identity without a guessed cooldown`() {
        context(); assign()
        now = 1_000; assign("ELUSIVE", "Oasis")
        val first = chat(RETURN).notice()
        // Different observed assignments remain valid even inside the duplicate replay guard.
        assign("ENDANGERED", "Mushroom Gorge")
        assertNotEquals(first.id, chat(TIMEOUT).notice().id)
    }

    @Test fun `stale context and reentry cannot revive a hunt and unrelated subarea changes preserve it`() {
        context(); assign()
        now = 4_999; context(location = "Oasis")
        assertNotNull(chat(RETURN).notice())
        assign("ELUSIVE")
        now = 9_999
        assertTrue(chat(TIMEOUT).isEmpty())
        context()
        assertTrue(chat(TIMEOUT).isEmpty())
        assign()
        now = 15_000; context() // context itself detects the gap before an outcome arrives
        assertTrue(chat(RETURN).isEmpty())
    }

    @Test fun `repeated assignment and fresh context never extend the original association safety bound`() {
        context(); assign()
        for (step in 1..225) {
            now = step * 4_000L
            assertTrue(context().isEmpty())
            if (step < 225) assertTrue(assign().isEmpty())
        }
        assertTrue(chat(RETURN).isEmpty())
        assertTrue(chat(TIMEOUT).isEmpty())
    }

    @Test fun `clock regression invalidates association and session lifecycle clears all temporary state`() {
        now = 100; context(); assign()
        now = 99
        assertTrue(chat(RETURN).isEmpty())
        context()
        assertTrue(chat(RETURN).isEmpty())
        assign()
        module.onSessionEnded()
        assertTrue(chat(RETURN).isEmpty())
        module.onSessionStarted("second")
        assertTrue(chat(RETURN).isEmpty())
        context(); assign()
        assertNotNull(chat(RETURN).notice())
    }

    @Test fun `core session gate and notice queue show one short outcome and return without persistent widgets`() {
        val core = CoreController(modules = ModuleRegistry().register(module))
        fun ingest(signal: NormalizedSignal) = core.ingest(signal, now)
        assertTrue(ingest(NormalizedSignal.ContextSnapshot("Trapper's Den", "farming_1")).isEmpty())
        assertTrue(core.onChat(RETURN, now).isEmpty())
        core.enterSkyBlock("first", now)
        ingest(NormalizedSignal.ContextSnapshot("Trapper's Den", "farming_1"))
        assertTrue(core.onChat(RETURN, now).isEmpty())
        core.onChat("[NPC] Trevor: You can find your TRACKABLE animal near the Desert Mountain.", now)
        assertNull(core.snapshot(now).activeNotice)
        core.onChat(RETURN, now)
        assertEquals("Trevor hunt completed", core.snapshot(now).activeNotice?.title)
        assertTrue(core.snapshot(now).widgets.isEmpty())
        now = 4_000
        assertNull(core.snapshot(now).activeNotice)
        core.leaveSkyBlock(); core.enterSkyBlock("second", now)
        ingest(NormalizedSignal.ContextSnapshot("Trapper's Den", "farming_1"))
        assertTrue(core.onChat(TIMEOUT, now).isEmpty())
    }

    companion object {
        private const val RETURN = "Return to the Trapper soon to get a new animal to hunt!"
        private const val TIMEOUT = "You ran out of time and the animal disappeared!"
    }
}
