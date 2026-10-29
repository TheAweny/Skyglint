package dev.skyglint.ui.presentation

import dev.skyglint.core.*

enum class HudElementKind { MAIN, PROGRESS, COUNTER, TIMER, STRIP, CAROUSEL, ICON }
data class HudInlinePart(val text: String, val icon: String?, val accent: Int, val colored: Boolean = false,
                         val tail: String = "")

data class HudElement(
    val id: String, val kind: HudElementKind, val title: String, val icon: String?, val accent: Int,
    val progress: Double? = null, val value: String? = null, val badge: String? = null,
    val subtitle: String? = null, val identity: String = id, val priority: Int = 0,
    val coloredText: String? = null,
    val sourceWidget: ModuleWidget? = null,
    val prefix: String? = null,
    val verticalTransition: Boolean = false,
    val countdown: CountdownSnapshot? = null,
    val parts: List<HudInlinePart> = emptyList(),
    val pulse: Boolean = false,
    val liveValue: Boolean = false,
    val carouselIndex: Int = 0,
)

/** Chooses roles and compact content. Feature metrics are never parsed out of a human caption. */
class HudComposition {
    private val rotation = SingleRowRotation()
    private var dungeonEntry: String? = null
    private var dungeonEntryAt = 0L
    fun compose(snapshot: HudSnapshot, now: Long, showPets: Boolean = true, detailedDungeonHud: Boolean = false): List<HudElement> =
        compose(snapshot, now, HudPreferences(showPets = showPets, detailedDungeonHud = detailedDungeonHud))

    fun compose(snapshot: HudSnapshot, now: Long, preferences: HudPreferences): List<HudElement> {
        if (!snapshot.active) return emptyList()
        rotation.dwellMillis = preferences.rotationSeconds.coerceIn(4, 15) * 1_000L
        val widgets = visibleWidgets(snapshot.widgets.filter { it.expiresAt?.let { expiry -> now < expiry } != false }, preferences)
        // The queue normally retires disabled introductions. Isolated snapshots/preview callers
        // must also avoid rendering an introduction for a metric they no longer represent.
        val notice = snapshot.activeNotice?.takeUnless { value ->
            !preferences.showMiningTimers && value.target?.let {
                it.widgetId.startsWith("skyglint.mining.") && it.metricId.startsWith("event:")
            } == true
        }
        if (notice != null) {
            val targetMetric = notice.target?.let { target -> widgets.firstOrNull { it.id == target.widgetId }
                ?.presentation?.metrics?.firstOrNull { it.id == target.metricId } }
            val currentTimer = targetMetric?.countdown
                ?: snapshot.scheduledEvents.firstOrNull { it.active && it.name == notice.detail }
                    ?.takeIf { notice.countdown != null && notice.id.startsWith("skyglint.event_") }
                    ?.let { event -> event.endsAt?.takeIf { it > now }?.let {
                        CountdownSnapshot(it, maxOf(it - now, event.durationMillis ?: notice.countdown!!.intervalMillis))
                    } }
            return listOf(notice(if (currentTimer != null) notice.copy(countdown = currentTimer) else notice, now, targetMetric))
        }
        snapshot.notice?.let {
            val title = if (now - it.observedAt < 1_600) "SkyBlock level up!" else it.newLevel?.let { level -> "Level $level" } ?: "SkyBlock level up!"
            return listOf(HudElement("main", HudElementKind.MAIN, title, "@player", CYAN, identity = "sb:${it.newLevel}"))
        }
        val contextual = widgets.filter { it.presentation != null }
        val layoutContexts = contextual.filterNot { it.id in setOf("skyglint.mining.drill_fuel.current", "skyglint.currencies.current",
            "skyglint.server_closing.current", "skyglint.fishing.bait.current") }
        val dungeon = contextual.any { it.id.startsWith("skyglint.dungeon.") }
        val election = contextual.any { it.id.startsWith("skyglint.elections.") } ||
            snapshot.location in setOf("Community Center", "Election Room", "Election Booth")
        val mining = layoutContexts.any { it.id.startsWith("skyglint.mining.") }
        val eligibleMetrics = contextual.flatMap { widget -> widget.presentation!!.metrics.map { widget to it } }
            .filter { (widget, metric) -> !metric.referenceOnly && metric.group != "currency" &&
                metric.priority > 0 && metric.id !in setOf("tier", "next_visitor", "garden_level") &&
                widget.presentation?.role !in setOf(WidgetRole.INLINE, WidgetRole.PROGRESSION) &&
                (!dungeon || preferences.detailedDungeonHud || !widget.id.startsWith("skyglint.dungeon.") || metric.id in setOf("score", "secrets")) }
            .sortedByDescending { it.second.priority }.toMutableList()
        if (dungeon && eligibleMetrics.none { it.second.id in setOf("score", "forecast") }) {
            val owner = contextual.first { it.id.startsWith("skyglint.dungeon.") }
            eligibleMetrics += owner to WidgetMetric("score", "Score · Awaiting confirmed data", "—",
                "minecraft:nether_star", 100, group = "dungeon:run")
        }
        val blocks = MetricGrouping.select(eligibleMetrics, preferences.helperLimit.coerceIn(1, 7))
        val metrics = blocks.flatMap { it.metrics }
        // A capped-out metric cannot claim a bossbar that the HUD no longer represents.
        val replacedBossIds = metrics.flatMapTo(mutableSetOf()) { it.second.replacedBossIds }
        val selectedMetricOwners = metrics.mapTo(mutableSetOf()) { it.first.id }
        widgets.filter { it.presentation?.metrics.orEmpty().none { metric -> metric.replacedBossIds.isNotEmpty() } &&
            (it.presentation?.metrics.orEmpty().isEmpty() || it.id in selectedMetricOwners || it.presentation?.role in PRIMARY_ROLES) }
            .forEach { replacedBossIds += it.replacedBossIds }
        val remainingBosses = snapshot.bosses.filter { it.id !in replacedBossIds }
        val run = contextual.firstOrNull { it.id == "skyglint.dungeon.run" }
        if (run == null) dungeonEntry = null
        else if (dungeonEntry != run.title) { dungeonEntry = run.title; dungeonEntryAt = now }
        val primary = contextual.filter { it.presentation?.role in PRIMARY_ROLES }.maxByOrNull { it.priority }
        val genuineBosses = remainingBosses.filter { ObjectiveVisuals.resolve(it.title) == null && !SkyBlockParser.clean(it.title).startsWith("Guesting:") }
        val mainCandidates = buildList {
            contextual.filter { it.presentation?.role in PRIMARY_ROLES &&
                !(primary?.id?.startsWith("skyglint.garden.") == true && it.id.startsWith("skyglint.garden.") && it.id != primary.id)
            }.sortedByDescending { it.priority }.forEach { widget ->
                if (widget.presentation!!.role == WidgetRole.INLINE && widget.presentation.metrics.isNotEmpty()) {
                    val parts = widget.presentation.metrics.map { metric -> HudInlinePart(metric.value, metric.icon, ContextWidgets.accent(widget.id)) }
                    add("widget:${widget.id}" to HudElement("main", HudElementKind.STRIP, widget.title, null,
                        ContextWidgets.accent(widget.id), identity = widget.id, parts = parts,
                        subtitle = widget.presentation.metrics.joinToString(" · ") { "${it.label}: ${it.detail ?: it.value}" }))
                    return@forEach
                }
                val caption = if (widget.id == "skyglint.dungeon.run") {
                    widget.detail?.takeUnless { it == "Exploring" && now - dungeonEntryAt < 4_500 }
                        ?: widget.title.takeIf { now - dungeonEntryAt < 4_500 }
                } else listOfNotNull(widget.title, widget.detail.takeUnless { widget.id.contains("jacob") }).joinToString(" · ")
                if (caption != null) add("widget:${widget.id}" to HudElement("main", HudElementKind.MAIN,
                    caption, widget.presentation?.icon, ContextWidgets.accent(widget.id), progress = widget.progress?.fraction,
                    identity = widget.id, priority = widget.priority, subtitle = widget.title.takeIf { caption != it }, sourceWidget = widget))
            }
            widgets.filter { it.presentation == null }.forEach { widget -> add("widget:${widget.id}" to HudElement("main", HudElementKind.MAIN,
                    listOfNotNull(widget.title, widget.detail).joinToString(" · "), null, CYAN,
                    progress = widget.progress?.fraction, identity = widget.id, sourceWidget = widget)) }
            (if (layoutContexts.isEmpty()) remainingBosses else genuineBosses).forEach { boss -> add("boss:${boss.id}" to boss(boss)) }
        }
        val urgent = primary?.takeIf { it.priority >= 75 }?.let { "widget:${it.id}" }
        val selected = rotation.select(mainCandidates.map { it.first }, now, urgent,
            primary?.let { if (it.id == "skyglint.dungeon.run") "$urgent:${it.detail}" else urgent })
        val main = mainCandidates.firstOrNull { it.first == selected }?.second ?: if (contextual.none { it.presentation?.role in PRIMARY_ROLES })
            IdleLocationPolicy.caption(snapshot.location)?.takeUnless { mining || election }?.let { HudElement("main", HudElementKind.MAIN, it, "minecraft:compass", CYAN) } else null

        return buildList {
            main?.let(::add)
            when (val view = snapshot.progression) {
                is ProgressionView.SkillView -> add(HudElement("skill", HudElementKind.PROGRESS, view.skill.displayName,
                    "@skill:${view.skill.name}", CYAN, view.progress?.fraction, identity = "${view.skill}:${view.level}"))
                is ProgressionView.SkyBlock -> if (layoutContexts.isEmpty()) {
                    val expanded = main == null && blocks.isEmpty()
                    add(HudElement("skyblock", if (expanded) HudElementKind.MAIN else HudElementKind.PROGRESS,
                        if (expanded) "SkyBlock" + (view.level?.let { " · $it" } ?: "") else "SkyBlock",
                        "@player", CYAN, view.progress?.fraction, identity = "sb:${view.level}"))
                }
                null -> Unit
            }
            contextual.filter { it.presentation?.role == WidgetRole.PROGRESSION }.take(2).forEach { widget ->
                add(HudElement("progress:${widget.id}", HudElementKind.PROGRESS, "${widget.title} milestone",
                    widget.presentation?.icon, ContextWidgets.accent(widget.id), widget.progress?.fraction,
                    badge = widget.presentation?.metrics?.firstOrNull { it.id == "tier" }?.value,
                    identity = "${widget.id}:${widget.title}:${widget.presentation?.metrics?.firstOrNull { it.id == "tier" }?.value}"))
            }
            blocks.forEach { block ->
                if (block.grouped && (block.metrics.size > 1 || block.metrics.single().second.format != WidgetMetricFormat.ITEM)) {
                    val parts = block.metrics.map { (widget, metric) ->
                        val score = if (metric.id == "score") RankVisuals.score(metric.value) else null
                        HudInlinePart(if (metric.format == WidgetMetricFormat.ITEM) metric.value else compact(score?.number ?: metric.value), score?.icon ?: metric.icon,
                            metric.accent ?: if (score != null) rankColor(metric.value.substringAfterLast(" · ")) else ContextWidgets.accent(widget.id),
                            colored = metric.accent != null)
                    }
                    add(HudElement(block.id, HudElementKind.STRIP, "", null,
                        block.metrics.first().second.accent ?: ContextWidgets.accent(block.metrics.first().first.id),
                        parts = parts, liveValue = block.metrics.any { it.second.format == WidgetMetricFormat.ITEM },
                        subtitle = block.metrics.joinToString(" · ") { (_, metric) ->
                            metric.label + metric.value.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty() +
                                metric.detail?.let { " ($it)" }.orEmpty() }))
                    return@forEach
                }
                val (widget, metric) = block.metrics.single()
                val score = if (metric.id == "score") RankVisuals.score(metric.value) else null
                val timer = metric.countdown
                val item = metric.format == WidgetMetricFormat.ITEM
                val expanded = metric.expandedUntil?.let { now < it } == true
                val remainingText = timer != null && metric.format == WidgetMetricFormat.REMAINING_TIME
                val fuel = widget.id == "skyglint.mining.drill_fuel.current" && metric.id == "fuel"
                val fraction = widget.progress?.fraction
                add(HudElement(if (block.grouped) block.id else "metric:${widget.id}:${metric.id}", when {
                    expanded -> HudElementKind.MAIN
                    item && metric.value.isEmpty() -> HudElementKind.ICON
                    timer != null && !remainingText -> HudElementKind.TIMER
                    else -> HudElementKind.COUNTER
                },
                    metric.label, score?.icon ?: metric.icon ?: widget.presentation?.icon,
                    metric.accent ?: when {
                        fuel -> when {
                            fraction != null && fraction < .1 -> 0xFFFF8E91.toInt()
                            fraction != null && fraction < .25 -> 0xFFFFD182.toInt()
                            else -> 0xFFE4D3A4.toInt()
                        }
                        metric.id == "score" -> rankColor(metric.value.substringAfterLast(" · "))
                        widget.id.startsWith("skyglint.mining.") && metric.id.startsWith("event:") ->
                            MiningBossVisuals.resolve(metric.id.removePrefix("event:"))?.accent ?: ContextWidgets.accent(widget.id)
                        else -> ContextWidgets.accent(widget.id)
                    },
                    progress = timer?.fraction(now)?.takeUnless { remainingText },
                    value = if (remainingText) timer.caption(now) else if (timer != null) "X2".takeIf { metric.id == "event:double_powder" }
                        else (if (item) metric.value else compact(score?.number ?: metric.value)).takeIf { it.isNotEmpty() },
                    subtitle = if (score != null) listOfNotNull(metric.value, metric.detail).joinToString(" · ") else if (metric.id == "visitors")
                        listOfNotNull(widget.presentation?.metrics?.firstOrNull { it.id == "next_visitor" }?.value, metric.detail)
                            .joinToString(" · ").ifEmpty { null } else metric.detail,
                    priority = metric.priority, countdown = timer,
                    pulse = fuel && fraction != null && fraction < .05,
                    coloredText = metric.value.takeIf { fuel || metric.accent != null }, liveValue = fuel || remainingText || item))
            }
            if (preferences.showPets && !dungeon && !election) snapshot.pet?.let { pet ->
                val intro = preferences.showPetIntroduction && snapshot.petTransition?.let { now >= it.observedAt && now - it.observedAt < 3_000 } == true
                add(HudElement("pet", if (intro) HudElementKind.MAIN else HudElementKind.PROGRESS,
                    if (intro) pet.name + (pet.level?.let { " · Lv. $it" } ?: "") else pet.name,
                    "@pet", PET, pet.progress?.fraction, identity = "${pet.name}:${pet.level}:${pet.skin}",
                    subtitle = "Autopet".takeIf { snapshot.petTransition?.automatic == true }))
            }
        }
    }

    /** Filter before grouping and boss claims, retaining the original observation snapshots. */
    private fun visibleWidgets(widgets: List<ModuleWidget>, preferences: HudPreferences): List<ModuleWidget> = widgets.mapNotNull { widget ->
        val hidden = when {
            widget.id.startsWith("skyglint.garden.visitors") -> !preferences.showGardenVisitors
            widget.id.startsWith("skyglint.garden.pests") -> !preferences.showGardenPests
            widget.id.startsWith("skyglint.garden.milestone") -> !preferences.showCropMilestones
            widget.id == "skyglint.mining.drill_fuel.current" -> !preferences.showFuelWarnings
            widget.id == "skyglint.winter.defense" || widget.id == "skyglint.winter.defense.counters" -> !preferences.showJerryDefense
            widget.id.startsWith("skyglint.winter.reindrake:") -> !preferences.showReindrake
            else -> false
        }
        if (hidden) return@mapNotNull null
        val presentation = widget.presentation ?: return@mapNotNull widget
        val metrics = presentation.metrics.filterNot { metric -> when {
            widget.id.startsWith("skyglint.mining.") && metric.id.startsWith("event:") -> !preferences.showMiningTimers
            widget.id == "skyglint.fishing.bait.current" && metric.id == "bait_selected" -> !preferences.showFishingBait
            widget.id == "skyglint.fishing.bait.current" && metric.id == "bait_sack_disabled" -> !preferences.showSackWarning
            else -> false
        } }
        if (metrics.size == presentation.metrics.size) return@mapNotNull widget
        if (metrics.isEmpty() && presentation.role !in PRIMARY_ROLES) return@mapNotNull null
        // A legacy widget-level UUID set cannot be attributed to its surviving metrics.
        widget.copy(presentation = presentation.copy(metrics = metrics), replacedBossIds = emptySet())
    }

    private fun notice(notice: TransientNotice, now: Long, targetMetric: WidgetMetric? = null): HudElement {
        if (notice.loot.size > 1) {
            val age = now - notice.observedAt + notice.presentationOffsetMillis
            return HudElement("main", HudElementKind.CAROUSEL,
                if (notice.loot.first().delivery == RewardDelivery.CLAIMABLE) "Available" else "Received",
                null, 0xFFB0BAC8.toInt(), identity = "${notice.id}:${notice.observedAt}",
                parts = notice.loot.mapIndexed { index, reward ->
                    val tail = buildList {
                        reward.amount?.takeIf { it > 1 }?.let { add("×" + String.format(java.util.Locale.ROOT, "%,d", it)) }
                        reward.magicFind?.let { add("+$it% MF") }
                        if (reward.fishingCategory == FishingLootCategory.JUNK) add("Junk")
                        reward.trophyGrade?.let { add(it.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    }.joinToString(" · ")
                    HudInlinePart(reward.itemName, "@loot:$index", RarityPalette.color(reward.rarity) ?: 0xFFF1F5FA.toInt(),
                        colored = reward.rarity != null, tail = tail.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty())
                }, carouselIndex = (age.coerceAtLeast(0) / 1_800L).toInt().coerceAtMost(notice.loot.lastIndex),
                subtitle = notice.loot.joinToString(" · ") { it.itemName + (it.amount?.let { amount -> " ×$amount" } ?: "") })
        }
        if (notice.id.startsWith("skyglint.welcome:") && notice.pages.isNotEmpty()) {
            return HudElement("main", HudElementKind.CAROUSEL, "Welcome to SkyBlock", null, CYAN,
                identity = "${notice.id}:${notice.observedAt}",
                parts = notice.pages.map { HudInlinePart(it.title, it.icon, CYAN) },
                carouselIndex = NoticeCarousel.pageIndex(now - notice.observedAt + notice.presentationOffsetMillis,
                    notice.pages.size).coerceAtLeast(0))
        }
        notice.target?.let { target ->
            val visual = MiningBossVisuals.resolve(target.metricId.removePrefix("event:"))
            targetMetric?.takeIf { it.countdown == null && it.expandedUntil?.let { until -> now < until } == true }?.let { metric ->
                return HudElement("metric:${target.widgetId}:${target.metricId}", HudElementKind.MAIN,
                    metric.label, metric.icon ?: notice.item?.vanillaItemId,
                    metric.accent ?: visual?.accent ?: CYAN,
                    value = metric.value.takeIf { it.isNotEmpty() }, priority = metric.priority,
                    coloredText = metric.value.takeIf { metric.accent != null })
            }
            val timer = notice.countdown
            return HudElement("metric:${target.widgetId}:${target.metricId}", HudElementKind.MAIN,
                notice.title, notice.item?.vanillaItemId, targetMetric?.accent ?: visual?.accent ?: CYAN,
                progress = timer?.fraction(now)?.takeUnless { targetMetric?.format == WidgetMetricFormat.REMAINING_TIME },
                value = timer?.caption(now)?.let { "$it left" },
                countdown = timer, liveValue = true, coloredText = notice.title.takeIf { targetMetric?.accent != null })
        }
        if (notice.id.startsWith("skyglint.garden.jacob.started") && notice.pages.isNotEmpty()) {
            val age = now - notice.observedAt + notice.presentationOffsetMillis
            return HudElement("main", HudElementKind.CAROUSEL, "Jacob Contest Starting", null, 0xFFFFD182.toInt(),
                identity = "${notice.id}:${notice.observedAt}",
                parts = notice.pages.map { HudInlinePart(it.title, it.icon, 0xFFFFD182.toInt()) },
                carouselIndex = NoticeCarousel.pageIndex(age, notice.pages.size).coerceAtLeast(0))
        }
        val event = notice.id.startsWith("skyglint.event_")
        val skill = notice.id.startsWith("skyglint.skill_level_up:")
        val pet = notice.id.startsWith("skyglint.pet_level_up:")
        val skyblock = notice.priority >= 100
        val staged = skill || pet || skyblock
        val intro = now - notice.observedAt < if (skyblock) 1_600 else 1_350
        val theme = if (event) EventVisuals.resolve(notice.detail.orEmpty()) else null
        // A confirmation matching its only page is already final. Consecutive duplicate
        // pages cannot turn identical visible content into a second animated stage.
        val pages = notice.pages.fold(mutableListOf<NoticePage>()) { result, page ->
            if (result.lastOrNull() != page) result += page
            result
        }
        val sameIntroduction = pages.firstOrNull()?.title == notice.title &&
            listOfNotNull(notice.title, notice.detail).joinToString(" · ") == notice.title
        val pageAge = now - notice.observedAt + notice.presentationOffsetMillis
        val page = if (pages.size == 1 && sameIntroduction) pages.single() else pages.getOrNull(
            NoticeCarousel.pageIndex(pageAge, pages.size).coerceAtLeast(if (sameIntroduction) 0 else -1))
        val title = notice.compactCaption ?: when {
            pages.isNotEmpty() -> page?.title ?: listOfNotNull(notice.title, notice.detail).joinToString(" · ")
            staged -> if (intro) notice.title else notice.detail ?: notice.title
            event -> listOfNotNull(theme?.shortName, notice.title).joinToString(" · ")
            notice.item?.itemName != null -> notice.item.itemName
            else -> listOfNotNull(notice.title, notice.detail).joinToString(" · ")
        }
        val rewardTail = buildList {
            notice.reward?.let { reward ->
                reward.amount?.takeIf { it > 1 }?.let { add("×" + String.format(java.util.Locale.ROOT, "%,d", it)) }
                if (reward.fishingCategory == FishingLootCategory.JUNK) add("Junk")
                reward.trophyGrade?.let { add(it.name.lowercase().replaceFirstChar(Char::uppercase)) }
            }
            notice.magicFind?.let { add("+$it% MF") }
        }.joinToString(" · ").ifEmpty { null }
        return HudElement("main", HudElementKind.MAIN, title,
            page?.icon ?: when { theme != null -> theme.icon; pet -> "@notice"; skyblock -> "@player";
                notice.item?.vanillaItemId != null -> "@notice:${notice.item.vanillaItemId}"; else -> "@notice" },
            theme?.accent ?: RarityPalette.color(notice.rarity) ?: when {
                notice.id.contains("drill_fuel.low.") -> 0xFFFF8E91.toInt()
                notice.id.contains("drill_fuel.") -> 0xFFFFD182.toInt()
                skill || skyblock -> CYAN
                else -> PET
            },
            value = notice.countdown?.caption(now)?.let { "$it left" } ?: rewardTail,
            countdown = notice.countdown, liveValue = notice.countdown != null,
            identity = "${notice.id}:${notice.observedAt}",
            subtitle = if (notice.reward != null) notice.reward.sourceTitle else
                listOfNotNull(notice.title, notice.detail).joinToString(" · ").takeIf { notice.compactCaption != null },
            coloredText = if (notice.id.startsWith("skyglint.mining.drill_fuel.")) notice.title else
                notice.item?.itemName?.takeUnless { staged || (notice.reward?.delivery == RewardDelivery.CLAIMABLE && notice.rarity == null) },
            prefix = (if (notice.reward?.delivery == RewardDelivery.CLAIMABLE) "Available · " else "Received · ")
                .takeIf { notice.item?.itemName != null && !staged && !event && notice.pages.isEmpty() },
            verticalTransition = pages.isNotEmpty() && (!sameIntroduction || pages.size > 1))
    }
    private fun boss(boss: BossBarState): HudElement {
        val task = ObjectiveVisuals.resolve(boss.title)
        return HudElement("main", HudElementKind.MAIN, task?.title ?: SkyBlockParser.clean(boss.title),
            task?.icon ?: "minecraft:nether_star", task?.accent ?: bossColor(boss.color),
            progress = boss.progress.toDouble().takeUnless { task != null }, identity = "boss:${boss.id}")
    }
    private fun compact(value: String): String {
        val amount = value.replace(",", "").toBigDecimalOrNull() ?: return value
        val unit = listOf("1000000000000" to "t", "1000000000" to "b", "1000000" to "m", "10000" to "k")
            .firstOrNull { amount >= it.first.toBigDecimal() } ?: return value
        val divisor = if (unit.second == "k") 1_000.toBigDecimal() else unit.first.toBigDecimal()
        return amount.divide(divisor, 1, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString() + unit.second
    }
    private fun bossColor(name: String): Int = when (name.uppercase()) {
        "RED" -> 0xFFF0A5A5.toInt(); "GREEN" -> 0xFFA7D4B1.toInt(); "YELLOW" -> 0xFFE5D797.toInt()
        "PINK" -> 0xFFF0AED8.toInt(); "BLUE" -> 0xFF9DBDFF.toInt(); else -> 0xFFC6ACED.toInt()
    }
    private fun rankColor(rank: String): Int = when (rank) {
        "S+" -> 0xFFFFE8A0.toInt(); "S" -> 0xFFFFCE68.toInt(); "A" -> 0xFFD0A0FF.toInt()
        "B" -> 0xFF83B9FF.toInt(); "C" -> 0xFF84DDB2.toInt(); else -> 0xFFB4BDC8.toInt()
    }
    fun reset() { rotation.reset(); dungeonEntry = null; dungeonEntryAt = 0L }
    companion object {
        private const val CYAN = 0xFFA8DFFF.toInt()
        private const val PET = 0xFFE9BC76.toInt()
        private val PRIMARY_ROLES = setOf(WidgetRole.PRIMARY, WidgetRole.INLINE)
    }
}
