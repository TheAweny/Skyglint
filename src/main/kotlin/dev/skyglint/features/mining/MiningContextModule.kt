package dev.skyglint.features.mining

import dev.skyglint.core.*
import java.util.Locale

/** Mining observations expose hazards and recent commission changes, not idle location/statistics. */
class MiningContextModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.mining.context"
    private var mode: String? = null
    private var location: String? = null
    private var contextAt = Long.MIN_VALUE
    private var tab: MiningContextPatterns.Tab? = null
    private var tabAt = Long.MIN_VALUE
    private var environment: MiningContextPatterns.Environment? = null
    private var sidebarAt = Long.MIN_VALUE
    private var activeCommission: String? = null
    private var commissionUntil = Long.MIN_VALUE

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.ContextSnapshot -> {
                val nextMode = signal.mode?.lowercase(Locale.ROOT)
                if (nextMode !in MODES) { reset(); return removed() }
                if (mode != nextMode) clearSources()
                val nextLocation = signal.location?.let(SkyBlockParser::clean)
                    ?.takeIf { it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl) }
                if (location != nextLocation) { environment = null; sidebarAt = Long.MIN_VALUE }
                mode = nextMode; location = nextLocation; contextAt = now
            }
            is NormalizedSignal.TabSnapshot -> {
                if (!active(now)) return removed()
                val next = MiningContextPatterns.tab(signal.lines)
                observeCommissionChange(next, now)
                tab = next; tabAt = now
            }
            is NormalizedSignal.SidebarSnapshot -> {
                if (!active(now)) return removed()
                environment = MiningContextPatterns.environment(signal.lines); sidebarAt = now
            }
            else -> return emptyList()
        }
        if (!active(now)) return removed()
        val currentTab = tab?.takeIf { fresh(tabAt, now) }
        val currentEnvironment = environment?.takeIf { fresh(sidebarAt, now) }
        val powderDetail = currentTab?.powders.orEmpty().entries.joinToString(" · ") { "${it.key} Powder: ${it.value}" }
        val metrics = buildList {
            if (mode in setOf("mining_3", "mineshaft")) currentEnvironment?.cold?.takeIf { it != 0 }?.let {
                add(WidgetMetric("cold", "Cold", "$it❄", "minecraft:blue_ice", 90, powderDetail.takeIf(String::isNotEmpty)))
            }
            if (mode == "crystal_hollows") currentEnvironment?.heat
                ?.takeIf { it != "IMMUNE" && it.removeSuffix("♨").toIntOrNull()?.let { value -> value > 0 } == true }?.let {
                    add(WidgetMetric("heat", "Heat", it, "minecraft:magma_cream", 90, powderDetail.takeIf(String::isNotEmpty)))
                }
            val commissions = currentTab?.commissions.orEmpty()
            val selected = commissions.firstOrNull { it.name == activeCommission }?.takeIf { now < commissionUntil }
            selected?.let {
                val detail = listOf(commissions.joinToString(" · ") { row -> "${row.name}: ${row.caption}" }, powderDetail)
                    .filter(String::isNotEmpty).joinToString(" · ")
                add(WidgetMetric("commission", it.name, it.caption, "minecraft:book", 70, detail))
            }
        }
        val sourceExpiry = mutableListOf(contextAt + LEASE)
        if (metrics.any { it.id == "commission" }) {
            sourceExpiry += tabAt + LEASE
            sourceExpiry += commissionUntil
        }
        if (metrics.any { it.id in setOf("cold", "heat") }) sourceExpiry += sidebarAt + LEASE
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID,
            "Mining", priority = 35, expiresAt = sourceExpiry.minOrNull(),
            presentation = WidgetPresentation(WidgetRole.COUNTERS, "minecraft:iron_pickaxe", metrics))))
    }

    private fun observeCommissionChange(next: MiningContextPatterns.Tab, now: Long) {
        val previous = tab?.takeIf { fresh(tabAt, now) }?.commissions.orEmpty().associateBy { it.name }
        val selected = next.commissions.firstOrNull { it.name == activeCommission }
        if (selected == null || previous[selected.name] == null ||
            previous[selected.name]?.let { selected.percent < it.percent || (it.done && !selected.done) } == true) {
            activeCommission = null; commissionUntil = Long.MIN_VALUE
        }
        val changed = next.commissions.filter { row ->
            previous[row.name]?.let { old -> row.percent > old.percent || (!old.done && row.done) } == true
        }.sortedWith(compareByDescending<MiningContextPatterns.Commission> { it.done }
            .thenByDescending { it.percent }.thenBy { it.name }).firstOrNull()
        if (changed != null) {
            activeCommission = changed.name
            commissionUntil = now + ACTIVITY_LEASE
        }
    }

    private fun active(now: Long) = mode in MODES && fresh(contextAt, now)
    private fun fresh(at: Long, now: Long) = at != Long.MIN_VALUE && now >= at && now - at < LEASE
    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun clearSources() {
        tab = null; tabAt = Long.MIN_VALUE; environment = null; sidebarAt = Long.MIN_VALUE
        activeCommission = null; commissionUntil = Long.MIN_VALUE
    }
    private fun reset() { mode = null; location = null; contextAt = Long.MIN_VALUE; clearSources() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        const val WIDGET_ID = "skyglint.mining.context.current"
        private const val LEASE = 5_000L
        private const val ACTIVITY_LEASE = 12_000L
        private val MODES = setOf("mining_3", "crystal_hollows", "mineshaft")
    }
}
