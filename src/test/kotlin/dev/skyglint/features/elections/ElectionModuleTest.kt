package dev.skyglint.features.elections

import dev.skyglint.core.*
import kotlin.test.*

class ElectionModuleTest {
    private var time = 0L
    // Source/parser regressions exercise immediate projection; production readiness is covered separately.
    private val module = ElectionModule({ time }, settleMillis = 0, sourceWaitMillis = 0)
    private fun context(location: String? = "Community Center", mode: String? = "hub") =
        module.parse(NormalizedSignal.ContextSnapshot(location, mode))
    private fun tab(vararg lines: String) = module.parse(NormalizedSignal.TabSnapshot(lines.toList()))
    private fun List<CoreEvent>.widget() = filterIsInstance<CoreEvent.WidgetObserved>().singleOrNull()?.widget

    @Test fun `only hub community subarea activates and unknown votes do not become zero`() {
        val widget = context().widget()!!
        assertEquals("Election", widget.title)
        assertNull(widget.detail)
        assertEquals(WidgetRole.COUNTERS, widget.presentation?.role)
        assertNull(widget.presentation?.icon)
        assertTrue(widget.presentation!!.metrics.isEmpty())
        for ((area, mode) in listOf("Village" to "hub", "Community Center" to "crimson_isle", "Community Center" to null, null to "hub"))
            assertTrue(context(area, mode).all { it is CoreEvent.WidgetRemoved })
        assertNotNull(context("Election Room").widget())
    }
    @Test fun `header bounded complete votes give all observed candidates without minister claims`() {
        context()
        val widget = tab("Election: 2d 4h", "§aDiana: |||||| (60%)", "Cole: ||| (30%)", "Marina: | (10%)", "", "Pet:").widget()!!
        assertEquals("Voting", widget.detail)
        val metrics = widget.presentation!!.metrics
        assertEquals(listOf("Diana 60%", "Cole 30%", "Marina 10%"), metrics.map { it.value })
        assertEquals(listOf("1. Diana", "2. Cole", "Observed · Marina"), metrics.map { it.label })
        assertEquals(listOf("candidate:diana", "candidate:cole", "candidate:marina"), metrics.map { it.id })
        assertTrue(metrics.none { "Minister" in it.label || "Mayor" in it.label })
    }
    @Test fun `malformed duplicate and unanchored vote rows do not become leaders`() {
        context()
        for (lines in listOf(
            listOf("Diana: |||| (70%)", "Cole: || (30%)"),
            listOf("Election: 1h", "Diana: |||| (60%)", "Diana: || (40%)"),
            listOf("Election: 1h", "Diana: |||| (60%)", "diana: || (40%)"),
            listOf("Election: 1h", "Diana: |||| (60%)", "Cole: || (1000%)"),
            listOf("Election: 1h", "Diana: |||| (101%)", "Cole: || (0%)"),
            listOf("Election: 1h", "Diana: |||| (70%)", "Cole: || (40%)"),
            listOf("Election: 1h", "Diana: |||| (60%)", "Cole: ||"),
            listOf("Election: 1h", "Diana: |||| (60.5%)", "Cole: || (39.5%)"),
        )) assertTrue(module.parse(NormalizedSignal.TabSnapshot(lines)).widget()!!.presentation!!.metrics.isEmpty(), lines.toString())
    }
    @Test fun `live top three below one hundred retains confirmed leading shares`() {
        context()
        val widget = module.parse(NormalizedSignal.TabSnapshot(ElectionLiveFixtures.partialTab)).widget()!!
        assertEquals("Voting", widget.detail)
        assertEquals(listOf("Aatrox 61%", "Foxy 29%", "Marina 4%"), widget.presentation!!.metrics.map { it.value })
        assertEquals(listOf("1. Aatrox", "2. Foxy", "Observed · Marina"), widget.presentation!!.metrics.map { it.label })
        assertEquals(3, module.parse(NormalizedSignal.SidebarSnapshot(ElectionLiveFixtures.sidebar)).widget()!!.presentation!!.metrics.size)
    }
    @Test fun `partial shares with a competitive unseen remainder stay observed without global ranks`() {
        context()
        val widget = tab("Election: 1h", "Cole: || (10%)", "Diana: |||| (40%)").widget()!!
        assertEquals("Observed", widget.detail)
        assertEquals(listOf("Diana 40%", "Cole 10%"), widget.presentation!!.metrics.map { it.value })
        assertTrue(widget.presentation!!.metrics.all { it.label.startsWith("Observed · ") })
        val boundary = tab("Election: 1h", "Diana: || (59%)", "Cole: || (21%)").widget()!!
        assertEquals("Observed", boundary.detail) // rounding permits an unseen candidate to overtake
        val zero = tab("Election: 1h", "Diana: || (0%)", "Cole: || (0%)").widget()!!
        assertEquals("Observed", zero.detail)
        assertEquals(listOf("Cole 0%", "Diana 0%"), zero.presentation!!.metrics.map { it.value })
    }
    @Test fun `row order does not alter values and rounded complete totals remain valid`() {
        context()
        val widget = tab("Election: 1h", "Marina: || (4%)", "Foxy: || (29%)", "Aatrox: || (61%)").widget()!!
        assertEquals(listOf("Aatrox 61%", "Foxy 29%", "Marina 4%"), widget.presentation!!.metrics.map { it.value })
        val rounded = tab("Election: 1h", "Diana: || (34%)", "Cole: || (34%)", "Marina: || (33%)").widget()!!
        assertEquals("Voting · Tie", rounded.detail)
    }
    @Test fun `explicit zero share survives and ties remain neutral`() {
        context()
        val zero = tab("Election: 1h", "Diana: || (100%)", "Cole: | (0%)").widget()!!.presentation!!.metrics
        assertEquals("Cole 0%", zero[1].value)
        val tie = tab("Election: 1h", "Diana: || (50%)", "Cole: || (50%)").widget()!!
        assertEquals("Voting · Tie", tie.detail)
        assertEquals(listOf("Cole 50%", "Diana 50%"), tie.presentation!!.metrics.map { it.value })
        assertTrue(tie.presentation!!.metrics.all { it.label.startsWith("Tie") })
    }
    @Test fun `closed election clears candidates instead of electing second place`() {
        context()
        tab("Election: 1h", "Diana: || (60%)", "Cole: || (40%)")
        val closed = tab("Election: Over!", "Mayor Diana", "Minister Cole").widget()!!
        assertEquals("Closed", closed.detail)
        assertTrue(closed.presentation!!.metrics.isEmpty())
    }
    @Test fun `context refresh cannot extend old votes and leaving area hides the widget`() {
        context(); tab("Election: 1h", "Diana: || (60%)", "Cole: || (40%)")
        time = 29_000
        assertEquals(30_000L, context().widget()!!.expiresAt)
        time = 30_000
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
        assertTrue(context("Village").all { it is CoreEvent.WidgetRemoved })
        time = 35_001
        assertTrue(tab("Election: 1h", "Diana: || (60%)", "Cole: || (40%)").all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `menu guesses sidebar bars and other sessions never produce unsupported votes`() {
        context()
        assertTrue(module.parse(NormalizedSignal.MenuSnapshot("Mayor Election", listOf(MenuItemSnapshot(1, "Diana", listOf("Votes: 60%"))))).isEmpty())
        assertTrue(module.parse(NormalizedSignal.SidebarSnapshot(listOf("Year 384 Votes", "|||| Diana", "|||| Cole"))).widget()!!.presentation!!.metrics.isEmpty())
        tab("Election: 1h", "Diana: || (60%)", "Cole: || (40%)")
        module.onSessionEnded(); module.onSessionStarted("next")
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
    }
    private fun api(year: Int = 522, candidates: List<ElectionVoteCandidate> = apiCandidates, ageMillis: Long = 0) =
        module.parse(NormalizedSignal.ElectionSnapshot(year, candidates, ageMillis))
    private fun sidebar(year: Int = 523) = module.parse(NormalizedSignal.SidebarSnapshot(listOf("Year $year Votes")))

    @Test fun `current API pool renders all five with decimal shares only under matching year`() {
        context(); sidebar()
        val metrics = api().widget()!!.presentation!!.metrics
        assertEquals(listOf("Aatrox 60.0%", "Foxy 25.0%", "Marina 10.0%", "Diana 4.0%", "Paul 1.0%"), metrics.map { it.value })
        assertEquals(5, metrics.size)
        assertEquals("Votes: 60 · Total: 100", metrics.first().detail)
        assertTrue(metrics.none { "Minister" in it.label || "Mayor" in it.label })
        assertTrue(sidebar(524).widget()!!.presentation!!.metrics.isEmpty())
        assertEquals(5, sidebar(523).widget()!!.presentation!!.metrics.size)
    }
    @Test fun `API works without sidebar year in Community Center but explicit mismatch rejects`() {
        context()
        assertEquals(5, api().widget()!!.presentation!!.metrics.size)
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Community Center", "Purse: 100")))
        assertEquals(5, context().widget()!!.presentation!!.metrics.size)
        sidebar(524)
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
        module.parse(NormalizedSignal.SidebarSnapshot(listOf("Community Center")))
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty()) // absence does not erase contrary evidence
        sidebar(523)
        assertTrue(api(521).widget()!!.presentation!!.metrics.isEmpty())
        assertTrue(api(candidates = listOf(ElectionVoteCandidate("Diana", 0), ElectionVoteCandidate("Paul", 0))).widget()!!.presentation!!.metrics.isEmpty())
        context("Village")
        assertTrue(api().all { it is CoreEvent.WidgetRemoved })
        context("Community Center", "dungeon_hub")
        assertTrue(api().all { it is CoreEvent.WidgetRemoved })
    }
    @Test fun `cached API age survives reentry and invalid age cannot extend a lease`() {
        context()
        assertEquals(1_000L, api(ageMillis = 359_000).widget()!!.expiresAt)
        time = 1_000
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
        module.onSessionEnded(); module.onSessionStarted("next"); context()
        assertTrue(api(ageMillis = 360_000).widget()!!.presentation!!.metrics.isEmpty())
        assertTrue(api(ageMillis = 360_001).widget()!!.presentation!!.metrics.isEmpty())
        assertTrue(api(ageMillis = -1).widget()!!.presentation!!.metrics.isEmpty())
        assertEquals(5, api(ageMillis = 300_000).widget()!!.presentation!!.metrics.size)
        time = 61_000
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
    }
    @Test fun `API and tab are selected as whole sources with independent expiry and closed veto`() {
        context(); sidebar(); api()
        val tabResult = module.parse(NormalizedSignal.TabSnapshot(ElectionLiveFixtures.partialTab)).widget()!!
        assertEquals(5, tabResult.presentation!!.metrics.size) // precise full source, no mixed shares
        assertEquals("Aatrox 60.0%", tabResult.presentation!!.metrics.first().value)
        time = 359_000; context(); sidebar()
        assertEquals(5, context().widget()!!.presentation!!.metrics.size)
        time = 360_000; context(); sidebar()
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
        val fallback = module.parse(NormalizedSignal.TabSnapshot(ElectionLiveFixtures.partialTab)).widget()!!
        assertEquals(3, fallback.presentation!!.metrics.size)
        api()
        assertEquals("Closed", tab("Election: Over!").widget()!!.detail)
        time = 390_000; context(); sidebar()
        assertEquals(5, context().widget()!!.presentation!!.metrics.size) // closed tab expires independently
    }
    @Test fun `API ties are neutral explicit zeros survive and lifecycle clears cache`() {
        context(); sidebar()
        val metrics = api(candidates = listOf(ElectionVoteCandidate("Diana", 10), ElectionVoteCandidate("Cole", 10),
            ElectionVoteCandidate("Paul", 0))).widget()!!.presentation!!.metrics
        assertEquals(listOf("Cole 50.0%", "Diana 50.0%", "Paul 0.0%"), metrics.map { it.value })
        assertTrue(metrics.take(2).all { it.label.startsWith("Tie · ") })
        module.onSessionEnded(); module.onSessionStarted("new")
        context(); sidebar()
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty())
    }
    @Test fun `API shares round the full vote total without overflow or invented precision`() {
        context(); sidebar()
        val fractional = api(candidates = listOf(ElectionVoteCandidate("Diana", 1), ElectionVoteCandidate("Cole", 2)))
            .widget()!!.presentation!!.metrics
        assertEquals(listOf("Cole 66.7%", "Diana 33.3%"), fractional.map { it.value })
        val large = api(candidates = listOf(ElectionVoteCandidate("Diana", Long.MAX_VALUE), ElectionVoteCandidate("Cole", 0)))
            .widget()!!.presentation!!.metrics
        assertEquals(listOf("Diana 100.0%", "Cole 0.0%"), large.map { it.value })
    }
    @Test fun `confirmed closed clears prior sources and old tab cannot resurrect until lease expiry`() {
        context(); sidebar(); api()
        val closed = module.parse(NormalizedSignal.ElectionClosed()).widget()!!
        assertEquals("Closed", closed.detail)
        assertTrue(closed.presentation!!.metrics.isEmpty())
        val staleTab = module.parse(NormalizedSignal.TabSnapshot(ElectionLiveFixtures.partialTab)).widget()!!
        assertEquals("Closed", staleTab.detail)
        assertTrue(staleTab.presentation!!.metrics.isEmpty())
        time = 359_999; context()
        assertEquals("Closed", context().widget()!!.detail)
        time = 360_000
        assertTrue(context().widget()!!.presentation!!.metrics.isEmpty()) // cached sources did not become fresh
        val after = module.parse(NormalizedSignal.TabSnapshot(ElectionLiveFixtures.partialTab)).widget()!!
        assertEquals(3, after.presentation!!.metrics.size)
    }
    @Test fun `fresh valid API reopens confirmed closed but invalid API and age do not`() {
        context(); module.parse(NormalizedSignal.ElectionClosed(ageMillis = 359_000))
        assertEquals("Closed", api(candidates = listOf(ElectionVoteCandidate("Diana", 0), ElectionVoteCandidate("Paul", 0))).widget()!!.detail)
        assertEquals(5, api().widget()!!.presentation!!.metrics.size)
        assertEquals(5, module.parse(NormalizedSignal.ElectionClosed(ageMillis = -1)).widget()!!.presentation!!.metrics.size)
        assertEquals(5, module.parse(NormalizedSignal.ElectionClosed(ageMillis = 360_000)).widget()!!.presentation!!.metrics.size)
        module.parse(NormalizedSignal.ElectionClosed(ageMillis = 359_000))
        time = 1_000
        assertNull(context().widget()!!.detail)
    }
    @Test fun `candidate head references follow descending values and survive vote updates by stable identity`() {
        context()
        val first = api(candidates = listOf(ElectionVoteCandidate("Paul", 20), ElectionVoteCandidate("Aatrox", 70),
            ElectionVoteCandidate("Foxy", 10))).widget()!!.presentation!!.metrics
        assertEquals(listOf("@candidate:aatrox", "@candidate:paul", "@candidate:foxy"), first.map { it.icon })
        val second = api(candidates = listOf(ElectionVoteCandidate("Paul", 80), ElectionVoteCandidate("Aatrox", 15),
            ElectionVoteCandidate("Foxy", 5))).widget()!!.presentation!!.metrics
        assertEquals(listOf("candidate:paul", "candidate:aatrox", "candidate:foxy"), second.map { it.id })
        assertEquals("@candidate:paul", second.first().icon)
        val fallback = tab("Election: 1h", "Diana: || (60%)", "Cole: || (40%)").widget()!!
        // The authoritative API pool still wins; head selection follows that same complete source.
        assertEquals(second.map { it.icon }, fallback.presentation!!.metrics.map { it.icon })
        module.onSessionEnded(); module.onSessionStarted("tab-only"); context()
        assertEquals(listOf("@candidate:diana", "@candidate:cole"),
            tab("Election: 1h", "Diana: || (60%)", "Cole: || (40%)").widget()!!.presentation!!.metrics.map { it.icon })
    }

    @Test fun `pinned public catalogue includes actual heads for ordinary and special candidates with license`() {
        val path = "/assets/skyglint/data/election-heads.json"
        val root = javaClass.getResourceAsStream(path)!!.bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val rows = root.getAsJsonArray("heads").map { it.asJsonObject }
        assertEquals(13, rows.size)
        assertEquals(rows.size, rows.map { it.get("name").asString }.toSet().size)
        val names = rows.map { it.get("name").asString }.toSet()
        assertTrue(names.containsAll(listOf("Aatrox", "Diana", "Cole", "Diaz", "Finnegan", "Foxy", "Marina", "Paul", "Scorpius", "Jerry", "Derpy")))
        assertTrue(rows.all { it.get("texture").asString.matches(Regex("[a-fA-F0-9]{32,128}")) })
        assertEquals("f19539bbd6c7cb017c5976505436d55c697e3938", root.getAsJsonObject("source").get("commit").asString)
        assertEquals("MIT", root.getAsJsonObject("source").get("license").asString)
        assertNotNull(javaClass.getResource("/assets/skyglint/data/election-heads-license.txt"))
    }
    companion object {
        private val apiCandidates = listOf(ElectionVoteCandidate("Paul", 1), ElectionVoteCandidate("Diana", 4),
            ElectionVoteCandidate("Marina", 10), ElectionVoteCandidate("Foxy", 25), ElectionVoteCandidate("Aatrox", 60))
    }
}
