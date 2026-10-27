package dev.skyglint.features.dungeon

import dev.skyglint.core.*

/** Passive Catacombs run context with separately leased source snapshots. No world or inventory access. */
class DungeonModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.dungeon"
    private var context: Boolean? = null
    private var contextAt = Long.MIN_VALUE
    private var sidebar: DungeonPatterns.Sidebar? = null
    private var sidebarAt = Long.MIN_VALUE
    private var stats: DungeonPatterns.Stats? = null
    private var statsAt = Long.MIN_VALUE
    private var phase: String? = null
    private var lastFloor: String? = null
    private var finalScore: DungeonScore.Observation? = null

    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.ContextSnapshot -> {
                val mode = signal.mode?.lowercase(java.util.Locale.ROOT)
                context = mode?.let { it == "dungeon" }
                contextAt = now
                if (context == false) { clearRun(); return removals() }
                if (context == true) DungeonPatterns.floor(signal.location.orEmpty())?.let { observeFloor(it) }
            }
            is NormalizedSignal.SidebarSnapshot -> {
                if (context == false && fresh(contextAt, now)) return removals()
                sidebar = DungeonPatterns.sidebar(signal.lines)
                sidebarAt = now
                sidebar?.floor?.let(::observeFloor)
            }
            is NormalizedSignal.TabSnapshot -> {
                if (!active(now)) return removals()
                stats = DungeonPatterns.stats(signal.lines); statsAt = now
            }
            is NormalizedSignal.Chat -> {
                if (!active(now)) return emptyList()
                val completed = DungeonScore.fromCompletion(signal.text)
                if (completed != null) { finalScore = completed; phase = "Completed" }
                else DungeonPatterns.phase(signal.text, knownFloor(now))?.let { observed -> phase = observed }
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        if (!active(now)) { clearRun(); return removals() }
        val floor = knownFloor(now)
        val freshSidebar = sidebar?.takeIf { fresh(sidebarAt, now) }
        val freshStats = stats?.takeIf { fresh(statsAt, now) }
        val estimate = if (finalScore == null && freshSidebar?.score == null) estimate(floor, freshSidebar, freshStats) else null
        val progress = freshSidebar?.cleared?.let { ProgressSnapshot(null, null, it / 100.0) }
        val runMetrics = listOfNotNull(
            (finalScore ?: freshSidebar?.score)?.let { WidgetMetric("score", "Score", it.caption, "minecraft:nether_star", 100,
                group = "dungeon:run") },
            freshSidebar?.cleared?.let { WidgetMetric("cleared", "Cleared", "$it%", "minecraft:filled_map", 60,
                group = "dungeon:progress") },
            freshSidebar?.elapsed?.let { WidgetMetric("time", "Time", it, "minecraft:clock", 20,
                group = "dungeon:progress") },
            estimate?.let { WidgetMetric("forecast", "Estimate", it.caption, "minecraft:spyglass", 40,
                group = "dungeon:run") },
        )
        val expiresAt = activeUntil(now)
        val result = mutableListOf<CoreEvent>(CoreEvent.WidgetObserved(ModuleWidget(RUN_ID,
            "Catacombs${floor?.let { " $it" }.orEmpty()}", phase, progress, priority = 80, expiresAt = expiresAt,
            presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:iron_sword", runMetrics))))
        val caption = freshStats?.caption()
        if (caption == null) result += CoreEvent.WidgetRemoved(STATS_ID)
        else result += CoreEvent.WidgetObserved(ModuleWidget(STATS_ID, "Dungeon", caption, priority = 60, expiresAt = minOf(expiresAt, statsAt + LEASE),
            presentation = WidgetPresentation(WidgetRole.STATS, metrics = freshStats?.metrics().orEmpty().map { metric ->
                metric.copy(group = when (metric.id) {
                    "secrets" -> "dungeon:run"
                    "crypts", "deaths", "rooms" -> "dungeon:exploration"
                    else -> null
                })
            })))
        return result
    }

    private fun estimate(floor: String?, side: DungeonPatterns.Sidebar?, stats: DungeonPatterns.Stats?): DungeonScoreEstimate.Estimate? {
        return DungeonScoreEstimate.calculate(DungeonScoreEstimate.Input(floor ?: return null, side?.cleared ?: return null,
            side.elapsedSeconds ?: return null, stats?.rooms ?: return null, stats.secretsPercent ?: return null,
            stats.crypts ?: return null, stats.deaths ?: return null, (stats.puzzles ?: return null).penalized * 10))
    }

    private fun active(now: Long): Boolean {
        val official = context.takeIf { fresh(contextAt, now) }
        if (official == false) return false
        return official == true || (fresh(sidebarAt, now) && sidebar?.floor != null)
    }
    private fun activeUntil(now: Long): Long = maxOf(
        if (context == true && fresh(contextAt, now)) contextAt + LEASE else now,
        if (fresh(sidebarAt, now) && sidebar?.floor != null) sidebarAt + LEASE else now,
    )
    private fun knownFloor(now: Long): String? = if (active(now)) lastFloor else null
    private fun observeFloor(floor: String) {
        if (lastFloor != null && lastFloor != floor) { phase = null; stats = null; statsAt = Long.MIN_VALUE; finalScore = null }
        lastFloor = floor
    }
    private fun fresh(observedAt: Long, now: Long): Boolean = observedAt != Long.MIN_VALUE && now >= observedAt && now - observedAt < LEASE
    private fun clearRun() {
        sidebar = null; sidebarAt = Long.MIN_VALUE; stats = null; statsAt = Long.MIN_VALUE; phase = null; lastFloor = null; finalScore = null
    }
    private fun reset() { context = null; contextAt = Long.MIN_VALUE; clearRun() }
    private fun removals(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(RUN_ID), CoreEvent.WidgetRemoved(STATS_ID))

    companion object {
        const val RUN_ID = "skyglint.dungeon.run"
        const val STATS_ID = "skyglint.dungeon.stats"
        private const val LEASE = 5_000L
    }
}
