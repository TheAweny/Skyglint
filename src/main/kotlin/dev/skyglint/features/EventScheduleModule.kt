package dev.skyglint.features

import dev.skyglint.core.*
import java.util.Locale

/** Explicit server event widgets, Calendar cards and named sidebar clocks. No calendar formula or HTTP. */
class EventScheduleModule : SignalModule {
    override val id = "skyglint.event_schedule"
    private val header = Regex("Event: (.{1,80})")
    private val starts = Regex("Starts In: (.{1,64})")
    private val ends = Regex("Ends In: (.{1,64})")
    private val calendarStarts = Regex("Starts in: (.{1,64})")
    private val calendarDuration = Regex("Event lasts for (.{1,64})!")
    // Source: SkyHanni ScoreboardPattern at 893cc826, explicit named timed event rows only.
    private val sidebarEvent = Regex("(Traveling Zoo|New Year Event!|Spooky Festival|[0-9]{1,4}(?:st|nd|rd|th) Anniversary|Century Raffle|Carnival) ([0-9]{1,4}:[0-5][0-9](?::[0-5][0-9])?)")

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val observations = when (signal) {
            is NormalizedSignal.TabSnapshot -> fromTab(signal.lines)
            is NormalizedSignal.MenuSnapshot -> fromCalendar(signal)
            is NormalizedSignal.SidebarSnapshot -> fromSidebar(signal.lines)
            else -> emptyList()
        }
        return observations.map(CoreEvent::EventObserved)
    }

    private fun fromTab(lines: List<String>): List<EventScheduleObservation> {
        val clean = lines.take(256).map(SkyBlockParser::clean)
        return clean.indices.mapNotNull { index ->
            val name = header.matchEntire(clean[index])?.groupValues?.get(1)?.takeIf(::validName) ?: return@mapNotNull null
            // Adjacency prevents reading a timer from another tab widget as this event's countdown.
            val durationLine = clean.getOrNull(index + 1) ?: return@mapNotNull null
            starts.matchEntire(durationLine)?.let {
                val remaining = ServerEventDuration.parseMillis(it.groupValues[1]) ?: return@let
                return@mapNotNull EventScheduleObservation(name, remaining, false, source("tab_event_widget", name))
            }
            ends.matchEntire(durationLine)?.let {
                val remaining = ServerEventDuration.parseMillis(it.groupValues[1]) ?: return@let
                return@mapNotNull EventScheduleObservation(name, null, true, source("tab_event_widget", name),
                    endsInMillis = remaining)
            }
            null
        }.distinctBy { it.source }
    }

    private fun fromCalendar(signal: NormalizedSignal.MenuSnapshot): List<EventScheduleObservation> {
        if (SkyBlockParser.clean(signal.title) != "Calendar and Events") return emptyList()
        return signal.items.take(128).mapNotNull { item ->
            val name = SkyBlockParser.clean(item.name).takeIf(::validName) ?: return@mapNotNull null
            val lore = item.lore.take(100).map(SkyBlockParser::clean)
            // Require both source-confirmed lines so arbitrary inventory items cannot become events.
            val duration = lore.firstNotNullOfOrNull { line -> calendarDuration.matchEntire(line)?.let {
                ServerEventDuration.parseMillis(it.groupValues[1])
            } } ?: return@mapNotNull null
            if (duration <= 0) return@mapNotNull null
            val remaining = lore.firstNotNullOfOrNull { line -> calendarStarts.matchEntire(line)?.let {
                ServerEventDuration.parseMillis(it.groupValues[1])
            } } ?: return@mapNotNull null
            EventScheduleObservation(name, remaining, false, source("calendar_menu", name), durationMillis = duration)
        }.sortedBy { it.startsInMillis }.distinctBy { it.source }
    }

    private fun source(channel: String, name: String) = "$channel:${name.lowercase(Locale.ROOT)}"

    private fun fromSidebar(lines: List<String>): List<EventScheduleObservation> = lines.take(64).mapNotNull { raw ->
        val match = sidebarEvent.matchEntire(SkyBlockParser.clean(raw)) ?: return@mapNotNull null
        val name = match.groupValues[1].let { if (it == "New Year Event!") "New Year Celebration" else it }
        val parts = match.groupValues[2].split(':').map(String::toLong)
        val seconds = if (parts.size == 3) parts[0] * 3_600 + parts[1] * 60 + parts[2]
            else parts[0] * 60 + parts[1]
        if (seconds > 7 * 24 * 3_600L) return@mapNotNull null
        EventScheduleObservation(name, null, true, source("sidebar_event", name), endsInMillis = seconds * 1_000)
    }.distinctBy { it.source }

    private fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 80 &&
        name.none(Char::isISOControl) && name !in setOf("None", "Not announced", "N/A", "???")
}

/** Strict ordered duration units. Source text may be coarse (7h), so this is an observation, not precision. */
internal object ServerEventDuration {
    private val token = Regex("([0-9]{1,3})(d|h|min|m|s)")
    private const val MAX_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun parseMillis(text: String): Long? {
        if (text.isEmpty() || text.length > 64) return null
        val compact = text.replace(" ", "")
        if (compact.isEmpty()) return null
        var consumed = 0
        var priorRank = 5
        var seconds = 0L
        for (part in token.findAll(compact)) {
            if (part.range.first != consumed) return null
            val amount = part.groupValues[1].toLongOrNull() ?: return null
            val unit = part.groupValues[2]
            val rank = when (unit) { "d" -> 4; "h" -> 3; "m", "min" -> 2; else -> 1 }
            if (rank >= priorRank) return null
            priorRank = rank
            val multiplier = when (rank) { 4 -> 86400; 3 -> 3600; 2 -> 60; else -> 1 }
            seconds += amount * multiplier
            if (seconds * 1000 > MAX_MILLIS) return null
            consumed = part.range.last + 1
        }
        return (seconds * 1000).takeIf { consumed == compact.length }
    }
}
