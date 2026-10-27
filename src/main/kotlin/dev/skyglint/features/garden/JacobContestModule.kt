package dev.skyglint.features.garden

import dev.skyglint.core.*

/** An explicit active sidebar contest works outside Garden too; a future tab crop list is not activity. */
class JacobContestModule(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : SignalModule {
    override val id = "skyglint.garden.jacob"
    private val widgetId = "$id.current"
    private val crop = Regex("[○\\uE051] (${GardenSignals.cropPattern}) ([0-9hms ]{1,20})")
    private val collected = Regex("Collected ([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{1,12})")
    private val bracket = Regex("(DIAMOND|PLATINUM|GOLD|SILVER|BRONZE) \\(Top ([0-9]{1,2})%\\): ([0-9,]{1,16})")
    private val startsIn = Regex("Starts In: ([0-9hms ]{1,20})")
    private val upcomingCrop = Regex("[○\\uE051] (${GardenSignals.cropPattern})")
    private data class Upcoming(val crops: List<String>, val startsAt: Long, val token: String)
    private var upcoming: Upcoming? = null
    private var lastUpcomingToken: String? = null
    private var activeEndsAt: Long? = null
    private var lastActiveToken: String? = null
    private var lastNotifiedAt = Long.MIN_VALUE
    private var contestSequence = 0L
    private var context: Pair<String?, String?>? = null
    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        if (signal is NormalizedSignal.ContextSnapshot) {
            val next = signal.location to signal.mode
            val changed = context != null && next.second != context?.second
            context = next
            return if (changed) removed() else emptyList()
        }
        if (signal is NormalizedSignal.TabSnapshot) { rememberUpcoming(signal.lines); return emptyList() }
        if (signal !is NormalizedSignal.SidebarSnapshot) return emptyList()
        val lines = GardenSignals.lines(signal.lines)
        val start = lines.indexOf("Jacob's Contest")
        if (start < 0) return removed()
        val match = lines.getOrNull(start + 1)?.let(crop::matchEntire) ?: return removed()
        val time = GardenSignals.time(match.groupValues[2]) ?: return removed()
        val cropName = match.groupValues[1]
        val detailLines = lines.drop(start + 2).take(2)
        val amount = detailLines.firstNotNullOfOrNull { collected.matchEntire(it)?.groupValues?.get(1)?.let { value -> GardenSignals.count(value) } }
        val medal = detailLines.firstNotNullOfOrNull { bracket.matchEntire(it)?.groupValues?.get(1) }
        val icon = GardenSignals.icon(match.groupValues[1])
        val metrics = listOfNotNull(
            WidgetMetric("time", "Remaining", time, "minecraft:clock", 90, group = "jacob:progress"),
            amount?.let { WidgetMetric("collected", "Collected", it.toString(), icon, 85, group = "jacob:progress") },
            medal?.let { WidgetMetric("medal", "Medal", it, "minecraft:gold_nugget", 60, group = "jacob:progress") })
        return startNotice(cropName, time) + CoreEvent.WidgetObserved(ModuleWidget(widgetId, "Jacob · $cropName", time,
            priority = 85, expiresAt = clock() + 4_000,
            presentation = WidgetPresentation(WidgetRole.PRIMARY, icon, metrics)))
    }
    private fun seconds(text: String): Long? {
        if (text in setOf("0s", "0m", "0h")) return 0
        if (GardenSignals.time(text) == null) return null
        return Regex("([0-9]{1,3})([hms])").findAll(text.replace(" ", "")).sumOf {
            it.groupValues[1].toLong() * when (it.groupValues[2]) { "h" -> 3600; "m" -> 60; else -> 1 }
        }
    }
    private fun rememberUpcoming(rawLines: List<String>) {
        val lines = GardenSignals.lines(rawLines)
        val header = lines.indexOf("Jacob's Contest:")
        if (header < 0) return // unrelated tab chunk/snapshot does not invent a different contest
        val countdown = lines.getOrNull(header + 1)?.let(startsIn::matchEntire)?.groupValues?.get(1)
        val delay = countdown?.let(::seconds)?.takeIf { it in 0..3_600 }
        val body = lines.drop(header + 2).takeWhile { upcomingCrop.matches(it) }.take(4)
        val crops = body.mapNotNull { upcomingCrop.matchEntire(it)?.groupValues?.get(1) }
        if (delay == null || crops.size != 3 || crops.distinct().size != 3) { upcoming = null; lastUpcomingToken = null; return }
        val token = "$countdown|${crops.joinToString("|")}"
        if (lastUpcomingToken == token) return // forced runtime revalidation must not move a frozen countdown forward
        lastUpcomingToken = token
        val startsAt = clock() + delay * 1_000
        val previous = upcoming
        upcoming = if (previous != null && previous.crops.toSet() == crops.toSet() &&
            kotlin.math.abs(previous.startsAt - startsAt) <= 90_000) previous.copy(token = token)
        else Upcoming(crops, startsAt, token)
    }
    private fun startNotice(crop: String, time: String): List<CoreEvent> {
        val remaining = seconds(time)?.takeIf { it in 1..1_200 } ?: return emptyList()
        val now = clock()
        val pending = upcoming?.takeIf { kotlin.math.abs(now - it.startsAt) <= 300_000 && crop in it.crops }
        val confirmedNewCycle = pending != null && lastNotifiedAt != Long.MIN_VALUE && now - lastNotifiedAt > 1_800_000
        val token = "$crop|$time"
        if (token == lastActiveToken && !confirmedNewCycle) return emptyList()
        lastActiveToken = token
        val endsAt = now + remaining * 1_000
        val previousEnd = activeEndsAt
        if (previousEnd != null && kotlin.math.abs(previousEnd - endsAt) <= 120_000) return emptyList()
        activeEndsAt = endsAt
        val crops = pending?.crops ?: listOf(crop)
        lastNotifiedAt = now
        contestSequence++
        return listOf(CoreEvent.NoticeObserved(NoticeRequest(
            id = "$id.started.$contestSequence", title = "Jacob's Contest", detail = "Started", priority = 40,
            durationMillis = 6_500, pages = crops.map { NoticePage(it, GardenSignals.icon(it)) })))
    }
    private fun removed() = listOf(CoreEvent.WidgetRemoved(widgetId))
    private fun clear() { context = null; upcoming = null; lastUpcomingToken = null; activeEndsAt = null; lastActiveToken = null; lastNotifiedAt = Long.MIN_VALUE; contestSequence = 0 }
    override fun onSessionStarted(sessionId: String) = clear()
    override fun onSessionEnded() = clear()
}
