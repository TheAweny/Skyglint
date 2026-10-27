package dev.skyglint.core

/** Explicit game-chat selection briefly wins over lagging tab snapshots. No inferred XP or level. */
class PetSwitchPolicy {
    private var selected: PetSnapshot? = null
    private var at = Long.MIN_VALUE
    private var automatic = false
    private var lastEquip: CoreEvent.PetSwitched? = null
    fun clear() { selected = null; at = Long.MIN_VALUE; automatic = false; lastEquip = null }
    fun observe(event: CoreEvent.PetSwitched, now: Long): Boolean {
        val incoming = event.snapshot
        if (incoming.name.isBlank() || incoming.name.length > 80 || incoming.name.any(Char::isISOControl) ||
            incoming.level?.let { it !in 1..1_000 } == true) return false
        if (lastEquip == event && now >= at && now - at < 5_000) return false
        selected = incoming; at = now; automatic = event.automatic
        lastEquip = event
        return true
    }
    fun reconcile(incoming: PetSnapshot?, current: PetSnapshot?, now: Long, authoritative: Boolean = false): PetSnapshot? {
        if (authoritative) {
            val chosen = selected
            // A still-open old menu can lag the equip packet as well; a conflicting selection
            // is not confirmation of this equip. A matching freshly observed menu can enrich it.
            if (chosen != null && incoming != null && now >= at && now - at < 5_000 &&
                (!incoming.name.equals(chosen.name, true) || incoming.skin != chosen.skin)) return current
            if (incoming == null) clear() else selected = incoming
            return incoming
        }
        val confirmed = selected ?: return incoming
        if (now < at || now - at >= 5_000) return incoming
        if (incoming == null || !incoming.name.equals(confirmed.name, true) || incoming.skin != confirmed.skin) return current
        // Manual summon lacks a level/instance ID; old same-species tab XP is not current evidence yet.
        if (confirmed.level == null || incoming.level != current?.level) return current
        return incoming
    }
    fun transition(now: Long): PetTransition? = at.takeIf { it != Long.MIN_VALUE && now >= it && now - it < 3_000 }
        ?.let { PetTransition(it, automatic) }
}
