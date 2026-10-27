package dev.skyglint.features.slayer

import dev.skyglint.core.*
import dev.skyglint.features.slayer.SlayerPatterns.Outcome
import dev.skyglint.features.slayer.SlayerPatterns.Phase

/** Copied sidebar quest state with exact associated outcomes. No entity/HP inference or boss UUID claims. */
class SlayerModule(private val clock: () -> Long) : SignalModule {
    override val id = "skyglint.slayer"
    private data class Active(
        var quest: SlayerPatterns.Quest,
        val sequence: Long,
        var phase: Phase?,
        var progress: SlayerPatterns.Progress?,
        var observedAt: Long,
        var visibleAt: Long,
    )
    private var mode: String? = null
    private var contextAt = Long.MIN_VALUE
    private var active: Active? = null
    private var closed: SlayerPatterns.Quest? = null
    private var pendingStartAt = Long.MIN_VALUE
    private var lastStartAt = Long.MIN_VALUE
    private var sequence = 0L

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        if (signal is NormalizedSignal.ContextSnapshot) {
            if (!fresh(contextAt, now, SOURCE_LEASE) || mode != signal.mode) clearQuest()
            mode = signal.mode
            contextAt = now
            if (mode !in MODES) clearQuest()
            return publish(now)
        }
        if (mode !in MODES || !fresh(contextAt, now, SOURCE_LEASE)) {
            clearQuest()
            return removed()
        }
        active?.let { if (!fresh(it.observedAt, now, ASSOCIATION_LEASE)) active = null }
        when (signal) {
            is NormalizedSignal.SidebarSnapshot -> return sidebar(signal.lines, now)
            is NormalizedSignal.Chat -> {
                if (SlayerPatterns.started(signal.text)) {
                    // Repeated start headers cannot roll an observed fight back to grinding.
                    if (fresh(lastStartAt, now, START_REPLAY_GUARD) && closed == null) return emptyList()
                    lastStartAt = now
                    pendingStartAt = now
                    active = null
                    closed = null
                    return removed()
                }
                val outcome = SlayerPatterns.outcome(signal.text) ?: return emptyList()
                return finish(outcome, now)
            }
            else -> return emptyList()
        }
    }

    private fun sidebar(lines: List<String>, now: Long): List<CoreEvent> {
        val observed = SlayerPatterns.sidebar(lines)
        if (observed == null || !eligible(observed.quest)) {
            active?.visibleAt = Long.MIN_VALUE
            return removed() // disappearance is never a kill, failure or cancellation
        }
        val previous = active
        if (observed.phase == null) {
            if (previous != null && !sameQuest(previous.quest, observed.quest)) active = null
            else previous?.visibleAt = Long.MIN_VALUE
            return removed() // a missing/malformed stage cannot establish a fresh association
        }
        if (observed.phase == Phase.SLAIN) {
            // A terminal baseline is reference data, never a new completion.
            if (previous == null || !sameQuest(previous.quest, observed.quest)) return removed()
            return finish(Outcome.SLAIN, now)
        }
        if (closed?.let { sameQuest(it, observed.quest) } == true)
            return removed()
        if (fresh(pendingStartAt, now, SOURCE_LEASE) && observed.phase != Phase.GRINDING)
            return removed() // wait for new quest baseline rather than binding a cached old fight
        if (previous == null || !sameQuest(previous.quest, observed.quest)) {
            active = Active(observed.quest, sequence++, observed.phase, observed.progress, now, now)
            pendingStartAt = Long.MIN_VALUE
        } else {
            if (previous.phase == Phase.FIGHT && observed.phase == Phase.GRINDING) return publish(now)
            val oldProgress = previous.progress
            val newProgress = observed.progress
            if (oldProgress != null && newProgress != null && oldProgress.unit == newProgress.unit &&
                oldProgress.total == newProgress.total && newProgress.current < oldProgress.current)
                return publish(now) // a lower cached snapshot is not a new quest
            previous.observedAt = now
            previous.quest = observed.quest
            previous.phase = observed.phase
            previous.progress = observed.progress
            previous.visibleAt = now
        }
        return publish(now)
    }

    private fun finish(outcome: Outcome, now: Long): List<CoreEvent> {
        val current = active ?: return emptyList()
        active = null
        closed = current.quest
        pendingStartAt = Long.MIN_VALUE
        if (outcome == Outcome.CANCELLED || !fresh(current.observedAt, now, SOURCE_LEASE)) return removed()
        val caption = when (outcome) {
            Outcome.SLAIN -> "${current.quest.label} slain"
            Outcome.COMPLETE -> "${current.quest.label} quest complete"
            Outcome.FAILED -> "${current.quest.label} quest failed"
            Outcome.CANCELLED -> error("Handled above")
        }
        return removed() + CoreEvent.NoticeObserved(NoticeRequest(
            id = "$id:${current.sequence}:outcome",
            title = caption,
            priority = if (outcome == Outcome.FAILED) 65 else 60,
            durationMillis = 4_000,
            freshnessMillis = 6_000,
            item = ItemVisualHint(vanillaItemId = current.quest.type.icon),
        ))
    }

    private fun publish(now: Long): List<CoreEvent> {
        val current = active ?: return removed()
        if (mode !in MODES || !fresh(contextAt, now, SOURCE_LEASE) || !fresh(current.visibleAt, now, SOURCE_LEASE))
            return removed()
        val detail = when (current.phase) {
            Phase.GRINDING -> current.progress?.caption ?: return removed()
            Phase.FIGHT -> "Slay the boss!"
            else -> return removed()
        }
        return listOf(CoreEvent.WidgetObserved(ModuleWidget(
            id = WIDGET_ID, title = current.quest.label, detail = detail,
            progress = current.progress?.snapshot.takeIf { current.phase == Phase.GRINDING },
            priority = if (current.phase == Phase.FIGHT) 85 else 75,
            expiresAt = minOf(contextAt + SOURCE_LEASE, current.visibleAt + SOURCE_LEASE),
            presentation = WidgetPresentation(WidgetRole.PRIMARY, current.quest.type.icon),
        )))
    }

    private fun eligible(quest: SlayerPatterns.Quest): Boolean =
        (quest.type == SlayerPatterns.Type.BLOODFIEND) == (mode == "rift")
    private fun sameQuest(a: SlayerPatterns.Quest, b: SlayerPatterns.Quest) = a.type == b.type && a.tier == b.tier
    private fun fresh(at: Long, now: Long, lease: Long) = at != Long.MIN_VALUE && now >= at && now - at < lease
    private fun removed(): List<CoreEvent> = listOf(CoreEvent.WidgetRemoved(WIDGET_ID))
    private fun clearQuest() {
        active = null; closed = null
        pendingStartAt = Long.MIN_VALUE; lastStartAt = Long.MIN_VALUE
    }
    private fun reset() { mode = null; contextAt = Long.MIN_VALUE; sequence = 0; clearQuest() }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        const val WIDGET_ID = "skyglint.slayer.quest"
        private const val SOURCE_LEASE = 5_000L
        // Defensive association/replay bounds, not server quest or boss durations.
        private const val ASSOCIATION_LEASE = 60_000L
        private const val START_REPLAY_GUARD = 2_000L
        private val MODES = setOf("hub", "combat_1", "combat_3", "foraging_1", "crimson_isle", "dynamic", "rift")
    }
}
