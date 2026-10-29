package dev.skyglint.core

import java.util.UUID

/** Names observed in the server protocol. No Minecraft classes belong in this package. */
enum class Skill(val displayName: String) {
    COMBAT("Combat"), FARMING("Farming"), FISHING("Fishing"), MINING("Mining"),
    FORAGING("Foraging"), ENCHANTING("Enchanting"), ALCHEMY("Alchemy"),
    CARPENTRY("Carpentry"), TAMING("Taming"), HUNTING("Hunting"),
    RUNECRAFTING("Runecrafting"), SOCIAL("Social");

    companion object {
        fun fromName(name: String): Skill? = entries.firstOrNull { it.displayName.equals(name, true) }
    }
}

/** A percentage can be known even when exact XP is not. null is never silently converted to zero. */
data class ProgressSnapshot(val current: Double?, val maximum: Double?, val fraction: Double) {
    init {
        require(fraction.isFinite() && fraction in 0.0..1.0)
        require(current == null || (current.isFinite() && current >= 0))
        require(maximum == null || (maximum.isFinite() && maximum > 0))
    }
}

data class SkillSnapshot(val skill: Skill, val level: Int?, val progress: ProgressSnapshot?)
data class SkyBlockSnapshot(val level: Int?, val progress: ProgressSnapshot?)
data class PetSnapshot(val name: String, val level: Int?, val progress: ProgressSnapshot? = null, val skin: Boolean = false)
data class PetTransition(val observedAt: Long, val automatic: Boolean)
data class BossBarState(
    val id: UUID,
    val title: String,
    val progress: Float,
    val color: String = "PURPLE",
    val overlay: String = "PROGRESS",
    /** Copied legacy palette runs; point status colors must survive Component.string flattening. */
    val formattedTitle: String = title,
)

sealed interface ProgressionView {
    data class SkillView(val skill: Skill, val level: Int?, val progress: ProgressSnapshot?, val gain: Double) : ProgressionView
    data class SkyBlock(val level: Int?, val progress: ProgressSnapshot?) : ProgressionView
}

data class LevelUpNotice(val title: String, val newLevel: Int?, val observedAt: Long, val expiresAt: Long)

/** A hint is not an item asset. The Minecraft adapter may resolve a freshly observed matching item. */
data class ItemVisualHint(val itemName: String? = null, val vanillaItemId: String? = null)

enum class FishingLootCategory { TREASURE, JUNK, TROPHY }
enum class FishingCatchQuality { GOOD, GREAT, OUTSTANDING }
enum class TrophyFishGrade { BRONZE, SILVER, GOLD, DIAMOND }
enum class RewardDelivery { RECEIVED, CLAIMABLE }

/** A detector reports a reward, never its market price or whether the player should care about it. */
data class RewardObservation(
    val itemName: String,
    val sourceTitle: String,
    val sourceId: String,
    val vanillaItemId: String? = null,
    val magicFind: Int? = null,
    val rarity: String? = null,
    val amount: Long? = null,
    val fishingCategory: FishingLootCategory? = null,
    val catchQuality: FishingCatchQuality? = null,
    val trophyGrade: TrophyFishGrade? = null,
    /** Server result rewards may still require manual collection; never announce these as received. */
    val delivery: RewardDelivery = RewardDelivery.RECEIVED,
)

/** `shown` is the filtering decision; `queued` is separate because an existing lease may coalesce it. */
data class RewardDecision(val shown: Boolean, val reason: String, val queued: Boolean = false)

/** Submitted by a detector; its ID coalesces repeated observations of the same notification. */
data class NoticePage(val title: String, val icon: String? = null)

/** Optional destination for a brief introduction which then contracts into its owning metric. */
data class NoticeMetricTarget(val widgetId: String, val metricId: String)

data class NoticeRequest(
    val id: String,
    val title: String,
    val detail: String? = null,
    val priority: Int = 50,
    val durationMillis: Long = 4_000,
    val freshnessMillis: Long = 15_000,
    val item: ItemVisualHint? = null,
    val magicFind: Int? = null,
    val rarity: String? = null,
    val pages: List<NoticePage> = emptyList(),
    val countdown: CountdownSnapshot? = null,
    val target: NoticeMetricTarget? = null,
    /** Optional short row caption; complete title/detail remain available in the tooltip. */
    val compactCaption: String? = null,
    val reward: RewardObservation? = null,
    /** A bounded burst of independently observed rewards; amounts and policy decisions stay per item. */
    val loot: List<RewardObservation> = emptyList(),
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(durationMillis in 250..15_000)
        require(freshnessMillis in durationMillis..60_000)
        require(magicFind == null || magicFind in 0..100_000)
        require(pages.size <= 8 && pages.all { it.title.isNotBlank() && it.title.length <= 120 })
        require(compactCaption == null || (compactCaption.isNotBlank() && compactCaption.length <= 120))
        require(loot.size <= 8)
    }
}

/** Presentation lease selected by the domain. Width and animation belong to the renderer. */
data class TransientNotice(
    val id: String,
    val title: String,
    val detail: String?,
    val priority: Int,
    val observedAt: Long,
    val expiresAt: Long,
    val item: ItemVisualHint? = null,
    val magicFind: Int? = null,
    val rarity: String? = null,
    val pages: List<NoticePage> = emptyList(),
    val presentationOffsetMillis: Long = 0,
    val countdown: CountdownSnapshot? = null,
    val target: NoticeMetricTarget? = null,
    val compactCaption: String? = null,
    val reward: RewardObservation? = null,
    val loot: List<RewardObservation> = emptyList(),
)

data class EventScheduleObservation(val name: String, val startsInMillis: Long?, val active: Boolean, val source: String,
                                    val endsInMillis: Long? = null, val durationMillis: Long? = null)
data class ScheduledEvent(
    val name: String,
    val startsAt: Long?,
    val observedAt: Long,
    val active: Boolean,
    val source: String,
    val expiresAt: Long,
    val endsAt: Long? = null,
    val durationMillis: Long? = null,
    val definitionId: String? = null,
)
/** Generic contributor presentation: IDs are namespaced; expiresAt uses the adapter's monotonic clock. */
enum class WidgetRole { PRIMARY, PROGRESSION, COUNTERS, STATS, INLINE }

/** Monotonic deadline with a server-observed or source-verified interval. Unknown totals stay unknown. */
data class CountdownSnapshot(val deadline: Long, val intervalMillis: Long) {
    init { require(intervalMillis > 0) }
    fun remaining(now: Long): Long = if (deadline <= now) 0 else
        (deadline - now).takeIf { it >= 0 } ?: Long.MAX_VALUE
    fun fraction(now: Long): Double = (remaining(now).toDouble() / intervalMillis).coerceIn(0.0, 1.0)
    fun caption(now: Long): String = DurationText.countdown(remaining(now))
}

enum class WidgetMetricFormat { DEFAULT, REMAINING_TIME, ITEM }

data class WidgetMetric(val id: String, val label: String, val value: String, val icon: String? = null,
                        val priority: Int = 0, val detail: String? = null,
                        val countdown: CountdownSnapshot? = null,
                        val replacedBossIds: Set<UUID> = emptySet(),
                        val group: String? = null,
                        val referenceOnly: Boolean = false,
                        val format: WidgetMetricFormat = WidgetMetricFormat.DEFAULT,
                        val accent: Int? = null,
                        val expandedUntil: Long? = null)

data class ElectionVoteCandidate(val name: String, val votes: Long)

data class WidgetPresentation(val role: WidgetRole, val icon: String? = null,
                              val metrics: List<WidgetMetric> = emptyList())

data class ModuleWidget(
    val id: String,
    val title: String,
    val detail: String? = null,
    val progress: ProgressSnapshot? = null,
    val priority: Int = 0,
    val expiresAt: Long? = null,
    val presentation: WidgetPresentation? = null,
    val replacedBossIds: Set<java.util.UUID> = emptySet(),
) {
    init { require(id.isNotBlank()) { "A module widget needs a stable nonblank id" } }
}

data class HudSnapshot(
    val active: Boolean,
    val sessionId: String?,
    val bosses: List<BossBarState>,
    val progression: ProgressionView?,
    val pet: PetSnapshot?,
    val notice: LevelUpNotice?,
    val widgets: List<ModuleWidget> = emptyList(),
    val activeNotice: TransientNotice? = null,
    val location: String? = null,
    val nextEvent: ScheduledEvent? = null,
    val scheduledEvents: List<ScheduledEvent> = emptyList(),
    val petTransition: PetTransition? = null,
)

/** Events are observations, not a lossless accounting ledger. Do not sum repeated server XP text. */
sealed interface CoreEvent {
    data class SkillGain(val skill: Skill, val gain: Double, val progress: ProgressSnapshot?, val signature: String) : CoreEvent
    data class SkillObserved(val snapshot: SkillSnapshot) : CoreEvent
    data class SkyBlockGain(val gain: Double, val progress: ProgressSnapshot?, val signature: String) : CoreEvent
    data class SkyBlockObserved(val snapshot: SkyBlockSnapshot) : CoreEvent
    data class PetObserved(val snapshot: PetSnapshot?, val authoritative: Boolean = false) : CoreEvent
    data class PetSwitched(val snapshot: PetSnapshot, val automatic: Boolean = false) : CoreEvent
    data class PetLevelUp(val name: String, val level: Int) : CoreEvent
    data class SkillLevelUp(val skill: Skill, val level: Int) : CoreEvent
    data class SkyBlockLevelUp(val title: String, val newLevel: Int?) : CoreEvent
    data class WidgetObserved(val widget: ModuleWidget) : CoreEvent
    data class WidgetRemoved(val id: String) : CoreEvent
    data class NoticeObserved(val request: NoticeRequest) : CoreEvent
    data class ContextObserved(val location: String?, val mode: String? = null) : CoreEvent
    data class EventObserved(val observation: EventScheduleObservation) : CoreEvent
    data class RewardObserved(val observation: RewardObservation, val decision: RewardDecision? = null) : CoreEvent
}
