package dev.skyglint.core

import java.util.UUID
import java.util.Collections

/** Single client-thread reducer. All times are monotonic milliseconds supplied by the adapter. */
class CoreController(
    var skillActiveMillis: Long = 10_000,
    val modules: ModuleRegistry = ModuleRegistry.defaults(),
    eventNoticePolicy: EventNoticePolicy? = null,
) {
    private val eventNotices = eventNoticePolicy ?: EventNoticePolicy()
    private val ownsEventNotices = eventNoticePolicy == null
    var rewardPolicy: RewardRulePolicy = RewardRulePolicy()
    var noticePreferences: NoticePreferences = NoticePreferences()
        set(value) {
            field = value
            if (!value.showSkyBlockLevelUps) notice = null
        }
    private var session: String? = null
    private var newestObservation = Long.MIN_VALUE
    private val bosses = linkedMapOf<UUID, BossBarState>()
    private val skills = mutableMapOf<Skill, SkillSnapshot>()
    private val skillSignatures = mutableMapOf<Skill, String>()
    private var skyBlockSignature: String? = null
    private var skyBlock: SkyBlockSnapshot? = null
    private var pet: PetSnapshot? = null
    private val petLevelUps = PetLevelUpPolicy()
    private val petSwitches = PetSwitchPolicy()
    private val welcome = WelcomeNoticePolicy()
    private val skillLevelUps = SkillLevelUpPolicy()
    private val activity = SkillActivityPolicy()
    private var location: String? = null
    private var mode: String? = null
    private var nextEvent: ScheduledEvent? = null
    private data class EventSourceState(val observation: EventScheduleObservation, val event: ScheduledEvent)
    private val eventSources = linkedMapOf<String, EventSourceState>()
    private var notice: LevelUpNotice? = null
    private val notices = NoticeQueue()
    private var unknownLevelHeaderAt: Long? = null
    private val shownLevels = mutableSetOf<String>()
    private val widgets = linkedMapOf<String, ModuleWidget>()

    fun enterSkyBlock(sessionId: String, now: Long) {
        require(sessionId.isNotBlank())
        if (session == sessionId) return
        leaveSkyBlock()
        session = sessionId
        newestObservation = now
        modules.sessionStarted(sessionId)
    }

    fun leaveSkyBlock() {
        val hadSession = session != null
        session = null
        newestObservation = Long.MIN_VALUE
        bosses.clear()
        skills.clear()
        skillSignatures.clear()
        skyBlockSignature = null
        skyBlock = null
        pet = null
        petLevelUps.clear()
        petSwitches.clear()
        welcome.clear()
        skillLevelUps.clear()
        activity.clear()
        location = null
        mode = null
        nextEvent = null
        eventSources.clear()
        if (ownsEventNotices) eventNotices.clear()
        notice = null
        notices.clear()
        unknownLevelHeaderAt = null
        shownLevels.clear()
        widgets.clear()
        if (hadSession) modules.sessionEnded()
    }

    fun onActionBar(text: String, now: Long): List<CoreEvent> = ingest(NormalizedSignal.ActionBar(text), now)
    /** The adapter arms only a real SkyBlock entry, never every island/server transfer. */
    fun prepareWelcome(now: Long) { if (session != null) welcome.arm(now) }
    fun welcomeWasOffered(): Boolean = welcome.wasOffered
    fun onChat(text: String, now: Long): List<CoreEvent> = ingest(NormalizedSignal.Chat(text), now)
    fun onTabLines(lines: List<String>, now: Long): List<CoreEvent> = ingest(NormalizedSignal.TabSnapshot(lines), now)

    fun ingest(signal: NormalizedSignal, now: Long): List<CoreEvent> {
        if (session == null || now < newestObservation) return emptyList()
        val events = modules.process(signal)
        if (signal is NormalizedSignal.ActionBar) {
            val text = SkyBlockParser.clean(signal.text)
            val presentSkills = events.filterIsInstance<CoreEvent.SkillGain>().mapTo(mutableSetOf()) { it.skill }
            val validXpPayload = presentSkills.isNotEmpty() || events.any { it is CoreEvent.SkyBlockGain }
            // A neutral server bar ends a visible XP burst. Unparsed '+' payloads are ambiguous:
            // retain signatures rather than mistake a malformed skill frame for new activity.
            if (validXpPayload || '+' !in text) {
                skillSignatures.keys.retainAll(presentSkills)
                if (events.none { it is CoreEvent.SkyBlockGain } && "SkyBlock XP" !in text) skyBlockSignature = null
            }
        }
        return apply(events, now)
    }

    /** External authoritative menu readers may supply snapshots without manufacturing gain events. */
    fun apply(events: List<CoreEvent>, now: Long): List<CoreEvent> {
        if (session == null || now < newestObservation) return emptyList()
        newestObservation = now
        widgets.entries.removeIf { (_, widget) -> widget.expiresAt?.let { it <= now } == true }
        return events.mapNotNull { event ->
            var acceptedEvent = event
            val accepted = when (event) {
                is CoreEvent.SkillGain -> {
                    if (skillSignatures[event.skill] == event.signature ||
                        !activity.observe(event.skill, event.gain, now, skillActiveMillis)) false else {
                        skillSignatures[event.skill] = event.signature
                        val prior = skills[event.skill]
                        skills[event.skill] = SkillSnapshot(event.skill, prior?.level, event.progress)
                        true
                    }
                }
                is CoreEvent.SkillObserved -> {
                    val reconciled = skillLevelUps.reconcile(event.snapshot, skills[event.snapshot.skill], now)
                    val changed = skills[event.snapshot.skill] != reconciled
                    skills[event.snapshot.skill] = reconciled
                    changed
                }
                is CoreEvent.SkillLevelUp -> {
                    val confirmation = skillLevelUps.observe(event, skills[event.skill], now)
                    if (confirmation == null) false else {
                        skills[event.skill] = confirmation.skill
                        offerNotice(confirmation.notice, now)
                        true
                    }
                }
                is CoreEvent.SkyBlockGain -> {
                    if (skyBlockSignature == event.signature) false else {
                        skyBlockSignature = event.signature
                        // Reconcile absolute progress only. Never add a delta to possibly stale XP.
                        skyBlock = SkyBlockSnapshot(skyBlock?.level, event.progress)
                        true
                    }
                }
                is CoreEvent.SkyBlockObserved -> {
                    val changed = skyBlock != event.snapshot
                    skyBlock = event.snapshot
                    changed
                }
                is CoreEvent.PetObserved -> {
                    val reconciled = petLevelUps.reconcile(petSwitches.reconcile(event.snapshot, pet, now, event.authoritative), pet, now)
                    val changed = pet != reconciled
                    pet = reconciled
                    changed
                }
                is CoreEvent.PetSwitched -> {
                    if (!petSwitches.observe(event, now)) false else {
                        petLevelUps.selectionChanged()
                        pet = event.snapshot.copy(progress = null)
                        true
                    }
                }
                is CoreEvent.PetLevelUp -> {
                    val confirmation = petLevelUps.observe(event, pet, now)
                    if (confirmation == null) false else {
                        pet = confirmation.activePet
                        offerNotice(confirmation.notice, now)
                        true
                    }
                }
                is CoreEvent.SkyBlockLevelUp -> {
                    val known = event.newLevel
                    val fresh = if (known != null) shownLevels.add("level:$known")
                    else unknownLevelHeaderAt?.let { now - it >= 5_000 } ?: true
                    if (fresh) {
                        if (known == null) unknownLevelHeaderAt = now
                        notice = if (noticePreferences.showSkyBlockLevelUps)
                            LevelUpNotice(event.title, event.newLevel, now, now + 4_000) else null
                        offerNotice(NoticeRequest(
                            id = "skyglint.skyblock_level_up:${known ?: now}",
                            title = "SkyBlock level up!",
                            detail = known?.let { "Level $it" },
                            priority = 100,
                            item = ItemVisualHint(vanillaItemId = "minecraft:experience_bottle"),
                        ), now)
                        true
                    } else false
                }
                is CoreEvent.WidgetObserved -> {
                    if (event.widget.expiresAt?.let { it <= now } == true) false else {
                        val owned = event.widget.ownedSnapshot()
                        val changed = widgets[owned.id]?.copy(expiresAt = owned.expiresAt) != owned
                        widgets[owned.id] = owned
                        acceptedEvent = CoreEvent.WidgetObserved(owned)
                        changed
                    }
                }
                is CoreEvent.WidgetRemoved -> widgets.remove(event.id) != null
                // This namespace and priority belong to confirmed SkyBlock level-ups. A generic
                // detector must not occupy their dedupe key and suppress the real server event.
                is CoreEvent.NoticeObserved -> if (event.request.id.startsWith("skyglint.skyblock_level_up:")) false
                    else offerNotice(event.request.copy(priority = event.request.priority.coerceIn(0, 99)), now)
                is CoreEvent.ContextObserved -> {
                    val newLocation = event.location?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
                    val newMode = event.mode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.take(80)
                    val changed = location != newLocation || mode != newMode
                    location = newLocation
                    mode = newMode
                    if (changed) activity.observeContext(location, mode, now, skillActiveMillis)
                    changed
                }
                is CoreEvent.EventObserved -> {
                    val observed = event.observation
                    val delay = observed.startsInMillis
                    if (observed.name.isBlank() || listOfNotNull(delay, observed.endsInMillis, observed.durationMillis)
                            .any { it !in 0..604_800_000 }) false else {
                        // A server countdown reaching zero is not evidence that an event started.
                        val rawName = observed.name.trim().take(80)
                        val definition = TemporaryEventCatalog.resolve(rawName)
                        val name = definition?.takeUnless { it.id == "anniversary" }?.name ?: rawName
                        val source = observed.source.trim().take(160)
                        val normalized = observed.copy(name = name, source = source)
                        eventSources.entries.removeIf { now >= it.value.event.expiresAt }
                        if (observed.active && observed.endsInMillis == 0L) {
                            eventSources.entries.removeIf { it.value.event.name == name && it.value.event.active }
                            eventNotices.confirmedEnd(name, now).forEach { notices.cancel(it, now) }
                        }
                        val previousSource = eventSources[source]
                        val sameFreshPayload = normalized == previousSource?.observation
                        val startsAt = if (sameFreshPayload) previousSource!!.event.startsAt else delay?.let { now + it }
                        val endsAt = if (sameFreshPayload) previousSource!!.event.endsAt else observed.endsInMillis?.let { remaining ->
                            val proposed = now + remaining
                            val prior = previousSource?.event?.takeIf { it.active && it.name == name }?.endsAt
                            if (prior != null && kotlin.math.abs(proposed - prior) <= 1_500) prior else proposed
                        }
                        eventSources[source] = EventSourceState(normalized,
                            ScheduledEvent(name, startsAt, now, observed.active, source, now + 30_000,
                                endsAt, observed.durationMillis, definition?.id))
                        if (eventSources.size > 64) {
                            val oldest = eventSources.minBy { it.value.event.observedAt }.key
                            eventSources.remove(oldest)
                        }
                        // A tab and a calendar can describe the same event at different precision.
                        // Revalidation preserves each source's own deadline; prefer the earliest
                        // fresh countdown, while an explicit active observation wins for that event.
                        nextEvent = selectEvent(name, now)
                        val announcement = eventNotices.observe(nextEvent, now, explicitActive = observed.active)
                        if (observed.active && nextEvent?.active == true) {
                            eventNotices.reminderId(name)?.let { notices.cancel(it, now) }
                        }
                        announcement?.let { offerNotice(it, now) }
                        true
                    }
                }
                is CoreEvent.RewardObserved -> {
                    val observation = event.observation
                    val decision = rewardPolicy.evaluate(observation)
                    val request = NoticeRequest(
                        id = "skyglint.reward:${observation.sourceId}:${RewardRulePolicy.normalizeItemName(observation.itemName)}",
                        title = observation.sourceTitle.trim().take(80).ifEmpty { "Reward found" },
                        detail = observation.itemName.trim().take(120),
                        priority = 50,
                        item = ItemVisualHint(observation.itemName, observation.vanillaItemId),
                        magicFind = observation.magicFind?.takeIf { it in 0..100_000 },
                        rarity = observation.rarity,
                        reward = observation,
                        durationMillis = if (observation.delivery == RewardDelivery.CLAIMABLE) 3_000 else 4_000,
                        freshnessMillis = if (observation.delivery == RewardDelivery.CLAIMABLE) 60_000 else 15_000,
                    )
                    val offered = decision.shown && offerNotice(request, now)
                    val queued = offered && noticePreferences.allows(request)
                    acceptedEvent = event.copy(decision = decision.copy(queued = queued))
                    true // Observations remain diagnostic evidence even when presentation is hidden.
                }
            }
            if (accepted) acceptedEvent else null
        }
    }

    fun updateBoss(id: UUID, title: String, progress: Float, color: String = "PURPLE", overlay: String = "PROGRESS", now: Long) {
        if (session == null || now < newestObservation) return
        newestObservation = now
        // Preserve every title, including unknown formats. Packet progress is bounded for rendering.
        bosses[id] = BossBarState(id, title, if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f, color, overlay)
    }

    fun removeBoss(id: UUID) { bosses.remove(id) }

    private fun selectEvent(name: String, now: Long): ScheduledEvent? {
        val fresh = eventSources.values.asSequence().map { it.event }
            .filter { it.name == name && now >= it.observedAt && now < it.expiresAt &&
                (!it.active || it.endsAt?.let { deadline -> now < deadline } != false) }.toList()
        val selected = fresh.filter { it.active }.maxByOrNull { it.observedAt }
            ?: fresh.filter { it.startsAt != null }.minByOrNull { it.startsAt!! }
            ?: fresh.maxByOrNull { it.observedAt }
            ?: return null
        // A fresh Calendar full duration may describe the same active tab/sidebar occurrence.
        // Conflicting observed totals stay unknown; remaining time keeps its selected source.
        val duration = selected.durationMillis ?: fresh.mapNotNull { it.durationMillis }.distinct().singleOrNull()
        return selected.copy(durationMillis = duration)
    }

    fun snapshot(now: Long): HudSnapshot {
        if (session == null) return HudSnapshot(false, null, emptyList(), null, null, null)
        val active = activity.active(now, skillActiveMillis)
        val progression = if (active != null) {
            val stored = skills[active.skill]
            ProgressionView.SkillView(active.skill, stored?.level, stored?.progress, active.gain)
        } else skyBlock?.let { ProgressionView.SkyBlock(it.level, it.progress) }
        val visibleWidgets = widgets.values.filter { it.expiresAt?.let { expiry -> now < expiry } != false }
            .sortedByDescending { it.priority }
        val currentEvents = eventSources.values.map { it.event.name }.distinct().mapNotNull { selectEvent(it, now) }
            .sortedWith(compareBy<ScheduledEvent> { it.startsAt ?: Long.MAX_VALUE }.thenBy { it.name })
        val currentEvent = currentEvents.firstOrNull { !it.active && it.startsAt?.let { at -> at > now } == true }
            ?: currentEvents.firstOrNull { it.active } ?: currentEvents.firstOrNull()
        // Poll only fresh observed deadlines. Crossing zero still cannot manufacture an active event.
        currentEvents.forEach { eventNotices.observe(it, now)?.let { request -> offerNotice(request, now) } }
        welcome.observe(skyBlock?.level, pet, location, now)?.let { offerNotice(it, now) }
        notices.retain(now, noticePreferences::allows)
        notices.retainTargets(visibleWidgets.flatMap { widget -> widget.presentation?.metrics.orEmpty()
            .map { NoticeMetricTarget(widget.id, it.id) } }.toSet(), now)
        return HudSnapshot(true, session, Collections.unmodifiableList(bosses.values.toList()), progression, pet,
            notice?.takeIf { now < it.expiresAt && now >= it.observedAt }, Collections.unmodifiableList(visibleWidgets), notices.active(now), location,
            currentEvent, Collections.unmodifiableList(currentEvents), petSwitches.transition(now))
    }

    private fun offerNotice(request: NoticeRequest, now: Long): Boolean =
        notices.offer(request, now, visible = noticePreferences.allows(request))
}
