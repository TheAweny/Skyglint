package dev.skyglint.features.bestiary

import dev.skyglint.core.CoreEvent
import dev.skyglint.core.ItemVisualHint
import dev.skyglint.core.NormalizedSignal
import dev.skyglint.core.NoticeRequest
import dev.skyglint.core.SignalModule
import dev.skyglint.core.SkyBlockParser

/**
 * Observes complete server chat blocks without changing chat. The framing and decimal milestone
 * suffix are evidenced by SkyHanni 893cc826, CompactBestiaryChatMessage.kt:26-29, 35-77.
 * That source does not specify a family/tier grammar: the first body line stays opaque server text.
 * This is an independent bounded parser, not the upstream chat cancellation implementation.
 */
class BestiaryModule(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : SignalModule {
    override val id = "skyglint.bestiary"

    private data class Block(
        val startedAt: Long,
        var lines: Int = 1,
        var hasHeader: Boolean = false,
        var description: String? = null,
        var milestone: Pair<Int, Int>? = null,
        val body: MutableList<String> = mutableListOf(),
    )

    private var block: Block? = null
    private val recent = linkedMapOf<String, Long>()
    private var sequence = 0L

    override fun parse(signal: NormalizedSignal): List<CoreEvent> {
        val now = clock()
        block?.let { if (now < it.startedAt || now - it.startedAt > BLOCK_MILLIS) block = null }
        recent.entries.removeIf { now < it.value || now - it.value >= DEDUPE_MILLIS }
        if (signal !is NormalizedSignal.Chat) return emptyList()
        val raw = signal.text
        if (!safeLine(raw)) return abort()

        val pending = block
        if (pending == null) {
            if (raw == BORDER) block = Block(now)
            return emptyList()
        }
        if (++pending.lines > MAX_LINES) return abort()
        if (!pending.hasHeader) {
            when {
                raw == HEADER -> pending.hasHeader = true
                raw == BORDER -> block = Block(now)
                SkyBlockParser.clean(raw).isNotEmpty() -> return abort()
            }
            return emptyList()
        }
        if (raw == BORDER) {
            block = null
            return complete(pending, now)
        }
        // An intervening ordinary chat line or another header invalidates the incomplete block.
        val plain = SkyBlockParser.clean(raw)
        if (raw == HEADER || (plain.isNotEmpty() && !raw.trimStart().startsWith('§'))) return abort()
        if (plain.isNotEmpty()) {
            if (!plain.any(Char::isLetterOrDigit) || plain == "BESTIARY") return abort()
            if (pending.description == null) {
                // The opaque native title is bounded text, never an arbitrary reward/chat caption.
                if (plain.length > MAX_DESCRIPTION || plain.firstOrNull()?.isLetter() != true ||
                    plain.any { !it.isLetterOrDigit() && it !in " '-().,➡➜→" } ||
                    plain.equals("REWARDS", ignoreCase = true)) return abort()
                pending.description = plain
            }
            val match = MILESTONE.find(raw)
            if (match == null && NUMERIC_MILESTONE_MARKER.containsMatchIn(raw)) return abort()
            if (match != null) {
                val transition = match.groupValues[1].toInt() to match.groupValues[2].toInt()
                if (transition.second <= transition.first ||
                    pending.milestone?.let { it != transition } == true) return abort()
                pending.milestone = transition
            }
        }
        pending.body += raw
        return emptyList()
    }

    private fun complete(pending: Block, now: Long): List<CoreEvent> {
        val description = pending.description ?: return emptyList()
        val signature = pending.body.joinToString("\n")
        if (signature in recent) return emptyList()
        recent[signature] = now
        while (recent.size > 32) recent.remove(recent.keys.first())
        val milestone = pending.milestone
        val detail = if (milestone == null) description else {
            val transition = "Milestone ${milestone.first} → ${milestone.second}"
            // A milestone-only body already contains the native transition; do not repeat it.
            if (MILESTONE.containsMatchIn(pending.body.firstOrNull { SkyBlockParser.clean(it).isNotEmpty() }.orEmpty()))
                transition else "$description · $transition"
        }
        return listOf(CoreEvent.NoticeObserved(NoticeRequest(
            id = "skyglint.bestiary.confirmed:${sequence++}",
            title = "Bestiary updated",
            detail = detail,
            priority = 55,
            durationMillis = 4_000,
            freshnessMillis = 10_000,
            item = ItemVisualHint(vanillaItemId = if (milestone == null) "minecraft:book" else "minecraft:nether_star"),
            compactCaption = if (milestone == null) "Bestiary · $description"
                else "Bestiary · Milestone ${milestone.first} → ${milestone.second}",
        )))
    }

    private fun safeLine(raw: String): Boolean {
        if (raw.length > 512 || raw.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) return false
        var index = 0
        while (index < raw.length) {
            if (raw[index] == '§') {
                val code = raw.getOrNull(index + 1)?.lowercaseChar() ?: return false
                if (code !in "0123456789abcdefklmnor") return false
                index++
            }
            index++
        }
        return true
    }

    private fun abort(): List<CoreEvent> { block = null; return emptyList() }
    private fun reset() { block = null; recent.clear(); sequence = 0 }
    override fun onSessionStarted(sessionId: String) = reset()
    override fun onSessionEnded() = reset()

    companion object {
        private const val MAX_LINES = 15
        private const val MAX_DESCRIPTION = 100
        private const val BLOCK_MILLIS = 2_000L
        private const val DEDUPE_MILLIS = 10_000L
        private val BORDER = "§3§l" + "▬".repeat(64)
        private val HEADER = "§f" + " ".repeat(34) + "§6§lBESTIARY"
        private val MILESTONE = Regex("§8([0-9]{1,3})➡§e([0-9]{1,3})$")
        private val NUMERIC_MILESTONE_MARKER = Regex("§8[0-9]+➡§e")
    }
}
