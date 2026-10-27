package dev.skyglint.features.garden

import dev.skyglint.core.*

internal object GardenSignals {
    private val gardenArea = Regex("(?:[⏣\\uE067] )?The Garden(?: [\\uE018\\uE07F] x[0-9]{1,3})?")
    private val plotLocation = Regex("Plot - ([^\\p{Cntrl}\\uE018\\uE07F]{1,64}?)(?: [\\uE018\\uE07F] x[0-9]{1,3})?")
    fun isGardenArea(line: String): Boolean = gardenArea.matches(line)
    fun plotLocation(line: String): String? = plotLocation.matchEntire(line)?.groupValues?.get(1)?.trim()
        ?.takeIf { it.isNotEmpty() && !it.contains('§') }?.let { "Plot - $it" }
    val crops = setOf("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cocoa Beans", "Sugar Cane", "Cactus", "Nether Wart")
    val cropPattern = crops.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
    fun icon(crop: String): String = "minecraft:" + when (crop) {
        "Wheat" -> "wheat"; "Carrot" -> "carrot"; "Potato" -> "potato"; "Pumpkin" -> "pumpkin"
        "Melon" -> "melon_slice"; "Mushroom" -> "red_mushroom"; "Cocoa Beans" -> "cocoa_beans"
        "Sugar Cane" -> "sugar_cane"; "Cactus" -> "cactus"; "Nether Wart" -> "nether_wart"
        else -> "golden_hoe"
    }
    fun lines(lines: List<String>): List<String> = lines.take(256).map(SkyBlockParser::clean)
    fun count(value: String, maximum: Long = 1_000_000_000_000L): Long? = value.replace(",", "").toLongOrNull()?.takeIf { it in 0..maximum }
    fun time(value: String): String? {
        if (value.length !in 1..30) return null
        val compact = value.replace(" ", "")
        val matches = Regex("([0-9]{1,3})(h|m|s)").findAll(compact).toList()
        if (matches.isEmpty() || matches.joinToString("") { it.value } != compact) return null
        var previous = 4
        var seconds = 0L
        for (part in matches) {
            val rank = when (part.groupValues[2]) { "h" -> 3; "m" -> 2; else -> 1 }
            if (rank >= previous) return null
            previous = rank
            seconds += part.groupValues[1].toLong() * when (rank) { 3 -> 3600; 2 -> 60; else -> 1 }
        }
        return value.takeIf { seconds in 0..604_800 }
    }
    fun section(lines: List<String>, header: String): List<String> {
        val start = lines.indexOf(header)
        if (start < 0) return emptyList()
        return lines.drop(start + 1).take(12).takeWhile {
            it.isNotEmpty() && !it.endsWith(":") && !it.startsWith("Visitors: ") && !it.startsWith("Garden Level: ")
        }
    }
}

/** Only copied context authorizes Garden-only observations; source text alone is insufficient. */
abstract class GardenWidgetModule(
    final override val id: String,
    protected val clock: () -> Long,
    private val priority: Int,
) : SignalModule {
    protected var inGarden = false
        private set
    protected var location: String? = null
        private set
    protected val widgetId = "$id.current"
    final override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal is NormalizedSignal.ContextSnapshot) {
            val enabled = signal.mode?.equals("garden", true) ?: (signal.location in setOf("Garden", "The Garden"))
            val was = inGarden
            inGarden = enabled
            location = signal.location?.let(SkyBlockParser::clean)?.takeIf { it.isNotEmpty() }
            if (was && !enabled) { clearState(); return removed() }
            return if (enabled) observeContext() else emptyList()
        }
        if (!inGarden) return emptyList()
        return observe(signal)
    }
    protected abstract fun observe(signal: NormalizedSignal): List<CoreEvent>
    protected open fun clearState() {}
    protected open fun observeContext(): List<CoreEvent> = emptyList()
    protected fun widget(title: String, detail: String? = null, progress: ProgressSnapshot? = null,
                         lifetime: Long = 4_000, presentation: WidgetPresentation? = null): List<CoreEvent> = listOf(CoreEvent.WidgetObserved(
        ModuleWidget(widgetId, title, detail, progress, priority, clock() + lifetime, presentation)))
    protected fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(widgetId))
    override fun onSessionStarted(sessionId: String) { inGarden = false; location = null; clearState() }
    override fun onSessionEnded() { inGarden = false; location = null; clearState() }
}
