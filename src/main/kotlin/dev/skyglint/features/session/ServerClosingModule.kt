package dev.skyglint.features.session

import dev.skyglint.core.*
import kotlin.math.abs

/** A server-local warning, never a fabricated restart, relocation command or global event banner. */
class ServerClosingModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.server_closing"
    private data class Clock(val source: ServerClosingPatterns.Observation, val deadline: Long, val interval: Long,
                             val metricId: String, var introduced: Boolean = false, var urgentIntroduced: Boolean = false)
    private var observed: Clock? = null
    private var observedAt = Long.MIN_VALUE
    private var generation = 0L

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        when (signal) {
            is NormalizedSignal.SidebarSnapshot -> {
                val incoming = ServerClosingPatterns.parse(signal.lines)
                if (incoming == null) { observedAt = Long.MIN_VALUE; return removed() }
                val prior = observed
                val newOccurrence = prior == null || prior.source.server != incoming.server ||
                    (now >= prior.deadline && prior.source != incoming && incoming.remainingMillis > 5_000)
                observed = if (newOccurrence) Clock(incoming, now + incoming.remainingMillis,
                    incoming.remainingMillis.coerceAtLeast(1), "closing:${incoming.server}:${generation++}")
                else if (prior.source == incoming) prior
                else {
                    val proposed = now + incoming.remainingMillis
                    prior.copy(source = incoming, deadline = if (incoming.remainingMillis == 0L) now
                        else if (abs(proposed - prior.deadline) <= 1_500) prior.deadline else proposed,
                        interval = maxOf(prior.interval, incoming.remainingMillis))
                }
                observedAt = now
            }
            is NormalizedSignal.ContextSnapshot -> Unit // Revalidation can expire the independently leased sidebar.
            else -> return emptyList()
        }
        return publish(now)
    }

    private fun publish(now: Long): List<CoreEvent> {
        val state = observed?.takeIf { observedAt != Long.MIN_VALUE && now - observedAt in 0 until LEASE && now < it.deadline }
            ?: return removed()
        val timer = CountdownSnapshot(state.deadline, state.interval)
        val urgent = timer.remaining(now) <= URGENT
        val metric = WidgetMetric(state.metricId, "Server closing", "", "minecraft:clock",
            priority = if (urgent) 95 else 0, detail = "Server ${state.source.server}", countdown = timer,
            format = WidgetMetricFormat.REMAINING_TIME, accent = RED)
        return buildList {
            add(CoreEvent.WidgetObserved(ModuleWidget(WIDGET_ID, "Server closing", priority = 90,
                expiresAt = minOf(observedAt + LEASE, state.deadline),
                presentation = WidgetPresentation(WidgetRole.COUNTERS, metrics = listOf(metric)))))
            if (!state.introduced || (urgent && !state.urgentIntroduced)) {
                val phase = if (state.introduced) "urgent" else "observed"
                state.introduced = true
                if (urgent) state.urgentIntroduced = true
                add(CoreEvent.NoticeObserved(NoticeRequest("$id:${state.metricId}:$phase", "Server closing",
                    priority = 75, freshnessMillis = LEASE, item = ItemVisualHint(vanillaItemId = "minecraft:clock"),
                    countdown = timer, target = NoticeMetricTarget(WIDGET_ID, state.metricId))))
            }
        }
    }

    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun reset() { observed = null; observedAt = Long.MIN_VALUE }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()
    companion object {
        const val WIDGET_ID = "skyglint.server_closing.current"
        private const val LEASE = 5_000L
        private const val URGENT = 60_000L
        private val RED = 0xFFF28E8E.toInt()
    }
}
