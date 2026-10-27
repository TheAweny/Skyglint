package dev.skyglint.features.mining

import dev.skyglint.core.*

/** Short confirmed crystal notices and one contextual active-search caption, never an idle gem dashboard. */
class NucleusActivityModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.mining.nucleus"
    private var inHollows = false
    private var location: String? = null
    private var contextAt = Long.MIN_VALUE
    private var detector = false
    private var heldAt = Long.MIN_VALUE
    private var carried: Set<NucleusPatterns.Tool>? = null
    private var inventoryAt = Long.MIN_VALUE
    private val returned = linkedSetOf<NucleusPatterns.Tool>()
    private var returnedComplete = false
    private var searchAt = Long.MIN_VALUE
    private var pendingCount: Int? = null
    private var pendingAt = Long.MIN_VALUE
    private val notices = linkedMapOf<String, Long>()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            if (signal.mode != "crystal_hollows") { reset(); return removed() }
            inHollows = true; contextAt = now
            location = signal.location?.let(SkyBlockParser::clean)
            if (location != "Mines of Divan") searchAt = Long.MIN_VALUE
            return publish(now)
        }
        if (!inHollows || !fresh(contextAt, now, SOURCE_LEASE)) return removed()
        when (signal) {
            is NormalizedSignal.HeldItemSnapshot -> {
                detector = signal.item?.skyBlockId == "DWARVEN_METAL_DETECTOR"
                heldAt = now
                if (!detector) searchAt = Long.MIN_VALUE
            }
            is NormalizedSignal.InventorySnapshot -> {
                carried = signal.items.mapNotNull { NucleusPatterns.Tool.identified(it.skyBlockId) }.toSet()
                inventoryAt = now
            }
            is NormalizedSignal.ActionBar -> {
                if (location == "Mines of Divan" && detector && fresh(heldAt, now, SOURCE_LEASE) && NucleusPatterns.searching(signal.text))
                    searchAt = now
                else return emptyList()
            }
            is NormalizedSignal.ActionBarPresence -> {
                if (location == "Mines of Divan" && detector && fresh(heldAt, now, SOURCE_LEASE) && NucleusPatterns.searching(signal.text))
                    searchAt = now
                else return emptyList()
            }
            is NormalizedSignal.Chat -> return chat(signal.text, now) + publish(now)
            else -> return emptyList()
        }
        return publish(now)
    }

    private fun chat(raw: String, now: Long): List<CoreEvent> {
        val line = SkyBlockParser.clean(raw)
        NucleusPatterns.foundCount(line)?.let {
            pendingCount = it; pendingAt = now
            return emptyList()
        }
        NucleusPatterns.foundName(line)?.let { crystal ->
            val count = pendingCount?.takeIf { fresh(pendingAt, now, 2_000) } ?: return emptyList()
            pendingCount = null
            if (crystal == NucleusPatterns.Crystal.JADE) { returned.clear(); returnedComplete = false; searchAt = Long.MIN_VALUE }
            return notice("found:${crystal.name}:$count", "${crystal.label} Crystal found · $count/5", crystal.icon, now)
        }
        NucleusPatterns.placed(line)?.let { crystal ->
            return notice("placed:${crystal.name}", "${crystal.label} Crystal placed", crystal.icon, now)
        }
        if (NucleusPatterns.completed(line)) {
            returned.clear(); returnedComplete = false; searchAt = Long.MIN_VALUE
            return notice("complete", "Nucleus run complete!", "minecraft:nether_star", now)
        }
        if (location != "Mines of Divan") return emptyList()
        NucleusPatterns.returned(line)?.let { tool ->
            returned += tool
            return emptyList()
        }
        if (NucleusPatterns.allReturned(line)) {
            returnedComplete = true; returned.addAll(NucleusPatterns.Tool.entries)
            // The NPC confirms all four tools, not collection of the crystal itself.
            return notice("tools_complete", "All Divan tools returned", "minecraft:compass", now)
        }
        // Treasure discovery is not a pickup: carried count changes only on an inventory observation.
        NucleusPatterns.discovered(line)?.let { tool ->
            searchAt = now
            return notice("tool:${tool.name}", "${tool.label} found", "minecraft:compass", now)
        }
        return emptyList()
    }

    private fun publish(now: Long): List<CoreEvent> {
        if (location != "Mines of Divan" || !detector || !fresh(heldAt, now, SOURCE_LEASE) ||
            !fresh(searchAt, now, SEARCH_LEASE) || returnedComplete) return removed()
        val currentCarried = carried?.takeIf { fresh(inventoryAt, now, SOURCE_LEASE) }
        val carriedText = currentCarried?.size?.toString() ?: "?"
        val returnedText = when { returnedComplete -> "4"; returned.isEmpty() -> "?"; returned.size == 4 -> "4"; else -> "≥${returned.size}" }
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Divan tools",
            "$carriedText carried · $returnedText/4 returned", priority = 65,
            expiresAt = minOf(contextAt + SOURCE_LEASE, heldAt + SOURCE_LEASE, searchAt + SEARCH_LEASE),
            presentation = WidgetPresentation(WidgetRole.PRIMARY, "minecraft:compass"))))
    }

    private fun notice(key: String, title: String, icon: String, now: Long): List<CoreEvent> {
        notices.entries.removeIf { now >= it.value }
        if (notices[key]?.let { now < it } == true) return emptyList()
        notices[key] = now + 6_000
        if (notices.size > 32) notices.remove(notices.keys.first())
        return listOf(CoreEvent.NoticeObserved(NoticeRequest("$id:$key", title, priority = 60,
            durationMillis = 3_500, freshnessMillis = 8_000, pages = listOf(NoticePage(title, icon)))))
    }
    private fun fresh(at: Long, now: Long, lease: Long) = at != Long.MIN_VALUE && now >= at && now - at < lease
    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun reset() {
        inHollows = false; location = null; contextAt = Long.MIN_VALUE; detector = false; heldAt = Long.MIN_VALUE
        carried = null; inventoryAt = Long.MIN_VALUE; returned.clear(); returnedComplete = false
        searchAt = Long.MIN_VALUE; pendingCount = null; pendingAt = Long.MIN_VALUE; notices.clear()
    }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
    companion object {
        const val WIDGET_ID = "skyglint.mining.nucleus.search"
        private const val SOURCE_LEASE = 5_000L
        private const val SEARCH_LEASE = 15_000L
    }
}
