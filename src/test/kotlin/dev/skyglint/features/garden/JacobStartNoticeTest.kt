package dev.skyglint.features.garden

import dev.skyglint.core.*
import kotlin.test.*

class JacobStartNoticeTest {
    private var now = 0L
    private val module = JacobContestModule { now }
    private fun upcoming(time: String = "1m", crops: List<String> = listOf("Nether Wart", "Pumpkin", "Wheat")) =
        module.parse(NormalizedSignal.TabSnapshot(listOf("Jacob's Contest:", " Starts In: $time") + crops.map { " ○ $it" } + ""))
    private fun active(crop: String = "Wheat", time: String = "19m59s") =
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Jacob's Contest", "○ $crop $time", "Collected 12")))
    private fun List<CoreEvent>.notices() = filterIsInstance<CoreEvent.NoticeObserved>().map { it.request }

    @Test fun `three observed crops become native icon pages only after active server sidebar`() {
        assertTrue(upcoming().isEmpty())
        now = 60_000
        val events = active()
        val notice = events.notices().single()
        assertEquals("Jacob's Contest", notice.title)
        assertEquals("Started", notice.detail)
        assertEquals(40, notice.priority)
        assertEquals(6_500L, notice.durationMillis)
        assertEquals(listOf("Nether Wart", "Pumpkin", "Wheat"), notice.pages.map { it.title })
        assertEquals(listOf("minecraft:nether_wart", "minecraft:pumpkin", "minecraft:wheat"), notice.pages.map { it.icon })
        assertEquals(1, events.filterIsInstance<CoreEvent.WidgetObserved>().size)
    }
    @Test fun `zero countdown snapshots and time passing never manufacture start`() {
        upcoming("1s")
        now = 1_000
        assertTrue(upcoming("0s").notices().isEmpty())
        now = 300_000
        assertTrue(module.parse(NormalizedSignal.ContextSnapshot("The Garden", "garden")).notices().isEmpty())
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Purse: 10"))).notices().isEmpty())
    }
    @Test fun `repeated active snapshots countdown ticks crop switch and brief mode transfer dedupe`() {
        upcoming(); now = 60_000
        val first = active().notices().single()
        repeat(3) { now += 1_000; assertTrue(active().notices().isEmpty()) }
        assertTrue(active(time = "19m56s").notices().isEmpty())
        module.parse(NormalizedSignal.SidebarSnapshot(emptyList()))
        module.parse(NormalizedSignal.ContextSnapshot("Village", "hub"))
        now += 1_000
        assertTrue(active("Pumpkin", "19m55s").notices().isEmpty())
        assertTrue(first.id.startsWith("skyglint.garden.jacob.started."))
    }
    @Test fun `old upcoming or next future contest cannot decorate current older contest`() {
        upcoming("34m")
        assertEquals(listOf("Wheat"), active().notices().single().pages.map { it.title })
        module.onSessionEnded(); now = 0
        upcoming("1m"); now = 600_000
        assertEquals(listOf("Wheat"), active().notices().single().pages.map { it.title })
    }
    @Test fun `current crop must belong to remembered three otherwise only actual crop is used`() {
        upcoming(); now = 60_000
        assertEquals(listOf("Carrot"), active("Carrot").notices().single().pages.map { it.title })
    }
    @Test fun `invalid duplicate incomplete or extra crop sections never invent three pages`() {
        for (crops in listOf(listOf("Wheat", "Wheat", "Pumpkin"), listOf("Wheat", "Pumpkin"),
            listOf("Wheat", "Pumpkin", "Fake Crop"), listOf("Wheat", "Pumpkin", "Carrot", "Potato"))) {
            module.onSessionStarted("fixture"); now = 0
            upcoming(crops = crops); now = 60_000
            assertEquals(listOf("Wheat"), active().notices().single().pages.map { it.title })
        }
    }
    @Test fun `unchanged forced tab replay does not push old pending deadline forward`() {
        upcoming("1m")
        now = 600_000
        upcoming("1m")
        assertEquals(listOf("Wheat"), active().notices().single().pages.map { it.title })
    }
    @Test fun `new contest after gap gets independent pages and notice id`() {
        upcoming(); now = 60_000
        val first = active().notices().single()
        now = 3_600_000
        upcoming("1m", listOf("Carrot", "Potato", "Wheat"))
        now += 60_000
        val second = active("Carrot").notices().single()
        assertNotEquals(first.id, second.id)
        assertEquals(listOf("Carrot", "Potato", "Wheat"), second.pages.map { it.title })
    }
    @Test fun `same crop and same time in a later confirmed upcoming cycle still notify once`() {
        upcoming(); now = 60_000; val first = active().notices().single()
        now = 120_000; upcoming("59m")
        now = 3_600_000; upcoming("1m")
        now = 3_660_000
        val next = active().notices().single()
        assertNotEquals(first.id, next.id)
        assertEquals(3, next.pages.size)
        assertTrue(active().notices().isEmpty())
    }
    @Test fun `session lifecycle clears remembered crops and notification dedupe`() {
        upcoming(); now = 60_000; active()
        module.onSessionEnded()
        val fresh = active().notices().single()
        assertEquals(listOf("Wheat"), fresh.pages.map { it.title })
        module.onSessionStarted("new")
        assertEquals(1, active().notices().size)
    }
}
