package dev.skyglint.core

import java.util.Locale

/** Chat is evidence of a level-up; tab/menu are snapshots and can temporarily lag behind it. */
class PetLevelUpPolicy {
    data class Confirmation(val notice: NoticeRequest, val activePet: PetSnapshot?)
    private data class Authority(val name: String, val level: Int, val until: Long, val skin: Boolean)
    private val seen = linkedSetOf<String>()
    private var authority: Authority? = null

    fun clear() { seen.clear(); authority = null }
    fun selectionChanged() { authority = null }

    fun observe(event: CoreEvent.PetLevelUp, current: PetSnapshot?, now: Long): Confirmation? {
        val name = SkyBlockParser.clean(event.name).removeSuffix(" ✦").trim()
        if (name.isEmpty() || name.length > 80 || name.any(Char::isISOControl) || event.level !in 1..1_000) return null
        val normalized = normalize(name)
        val sameActive = current?.let { normalize(it.name) == normalized } == true
        // A delayed lower-level message cannot replace more recent confirmed state.
        if (sameActive && current?.level?.let { it > event.level } == true) return null
        val key = "$normalized:${event.level}"
        if (!seen.add(key)) return null
        while (seen.size > MAX_CONFIRMATIONS) seen.remove(seen.first())
        var reconciled = current
        if (sameActive && current != null) {
            if (current.level == null || event.level > current.level) {
                // Do not attach previous level XP or a fabricated zero to the newly confirmed level.
                reconciled = current.copy(level = event.level, progress = null)
            }
            authority = Authority(normalized, event.level, now + SNAPSHOT_GRACE, current.skin)
        }
        return Confirmation(NoticeRequest(
            id = "skyglint.pet_level_up:$key",
            title = "Pet level up!",
            detail = "$name · Level ${event.level}",
            priority = 60,
            item = ItemVisualHint(name),
        ), reconciled)
    }

    fun reconcile(incoming: PetSnapshot?, current: PetSnapshot?, now: Long): PetSnapshot? {
        val confirmed = authority ?: return incoming
        if (now >= confirmed.until || incoming == null || normalize(incoming.name) != confirmed.name ||
            incoming.skin != confirmed.skin) {
            authority = null
            return incoming
        }
        if (incoming.level == null || incoming.level < confirmed.level) return current
        return incoming
    }

    private fun normalize(name: String): String = SkyBlockParser.clean(name).removeSuffix(" ✦").trim().lowercase(Locale.ROOT)

    companion object {
        private const val SNAPSHOT_GRACE = 5_000L
        private const val MAX_CONFIRMATIONS = 256
    }
}
