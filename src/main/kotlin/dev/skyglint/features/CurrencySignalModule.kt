package dev.skyglint.features

import dev.skyglint.core.*
import java.math.BigDecimal
import java.util.Locale

/** Absolute, copied sidebar balances. Currency group controls composition, never gameplay activity. */
class CurrencySignalModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.currencies"
    private var contextAt = Long.MIN_VALUE
    private var mode: String? = null
    private var observedAt = Long.MIN_VALUE
    private var metrics: List<WidgetMetric> = emptyList()

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.ContextSnapshot -> {
                val nextMode = signal.mode?.lowercase(Locale.ROOT)
                if (nextMode != mode) { metrics = emptyList(); observedAt = Long.MIN_VALUE }
                mode = nextMode; contextAt = now
            }
            is NormalizedSignal.SidebarSnapshot -> {
                if (!fresh(contextAt, now)) return removed()
                metrics = parseBalances(signal.lines)
                observedAt = now
            }
            else -> return emptyList()
        }
        if (!fresh(contextAt, now) || !fresh(observedAt, now) || metrics.isEmpty()) return removed()
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Currencies", priority = 20,
            expiresAt = minOf(contextAt, observedAt) + LEASE,
            presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = metrics))))
    }

    private fun parseBalances(lines: List<String>): List<WidgetMetric> {
        val values = linkedMapOf<String, WidgetMetric>()
        val seen = mutableSetOf<String>()
        val invalid = mutableSetOf<String>()
        for (raw in lines.take(32)) {
            if (raw.length > 256) continue
            val line = SkyBlockParser.clean(raw)
            val name = line.substringBefore(":", "")
            val kind = KINDS[name] ?: continue
            if (!seen.add(kind.id)) { values.remove(kind.id); invalid += kind.id; continue }
            val text = line.substringAfter(": ", "")
            val match = BALANCE.matchEntire(text)
            if (match == null) { invalid += kind.id; continue }
            val literal = match.groupValues[1]
            val compact = literal.lastOrNull()?.isLetter() == true
            val value = when {
                compact && kind.id == "sowdust" && COMPACT.matches(literal) -> literal
                compact -> null
                kind.decimal -> exactNumber(literal, true)
                else -> exactNumber(literal, false)
            } ?: continue
            if (kind.id !in invalid) values[kind.id] = WidgetMetric(kind.id, kind.label, value, kind.icon,
                kind.priority, "${kind.label}: $value", group = "currency", referenceOnly = true)
        }
        return values.values.sortedByDescending { it.priority }
    }

    private fun exactNumber(value: String, decimal: Boolean): String? {
        if (!(if (decimal) DECIMAL else INTEGER).matches(value)) return null
        val number = value.replace(",", "").toBigDecimalOrNull() ?: return null
        if (number < BigDecimal.ZERO || number > MAXIMUM) return null
        return number.stripTrailingZeros().toPlainString()
    }
    private fun fresh(at: Long, now: Long) = at != Long.MIN_VALUE && now >= at && now - at < LEASE
    private fun removed() = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun reset() { contextAt = Long.MIN_VALUE; mode = null; observedAt = Long.MIN_VALUE; metrics = emptyList() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    private data class Kind(val id: String, val label: String, val icon: String, val priority: Int, val decimal: Boolean = false)
    companion object {
        const val WIDGET_ID = "skyglint.currencies.current"
        private const val LEASE = 4_000L
        private const val NUMBER = "(?:0|[1-9][0-9]{0,15}|[1-9][0-9]{0,2}(?:,[0-9]{3}){1,5})"
        private val INTEGER = Regex(NUMBER)
        private val DECIMAL = Regex("$NUMBER(?:\\.[0-9]{1,2})?")
        private val COMPACT = Regex("(?:[1-9][0-9]{0,2})(?:\\.[0-9]{1,2})?[kKmMbB]")
        private val BALANCE = Regex("([^ ]{1,32})(?: \\([+-](?:$NUMBER(?:\\.[0-9]{1,2})?|[1-9][0-9]{0,2}(?:\\.[0-9]{1,2})?[kKmMbB])\\))?")
        private val MAXIMUM = BigDecimal("1000000000000000")
        private val KINDS = listOf(
            "Purse" to Kind("coins", "Coins", "minecraft:gold_nugget", 20, true),
            "Piggy" to Kind("coins", "Coins", "minecraft:gold_nugget", 20, true),
            "Bits" to Kind("bits", "Bits", "minecraft:prismarine_crystals", 19),
            "Gems" to Kind("gems", "Gems", "minecraft:emerald", 18),
            "Copper" to Kind("copper", "Copper", "minecraft:copper_ingot", 17),
            "Sowdust" to Kind("sowdust", "Sowdust", "minecraft:bone_meal", 16),
        ).toMap()
    }
}
