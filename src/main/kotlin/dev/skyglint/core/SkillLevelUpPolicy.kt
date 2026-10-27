package dev.skyglint.core

/** Confirmation is explicit chat; menu/tab levels remain observations, not celebrations. */
class SkillLevelUpPolicy {
    data class Confirmation(val notice: NoticeRequest, val skill: SkillSnapshot)
    private data class Authority(val level: Int, val until: Long)
    private val authorities = mutableMapOf<Skill, Authority>()
    private val seen = linkedSetOf<String>()

    fun clear() { authorities.clear(); seen.clear() }

    fun observe(event: CoreEvent.SkillLevelUp, current: SkillSnapshot?, now: Long): Confirmation? {
        if (event.level !in 1..1_000 || current?.level?.let { it > event.level } == true) return null
        val key = "${event.skill.name}:${event.level}"
        if (!seen.add(key)) return null
        while (seen.size > 256) seen.remove(seen.first())
        val observed = if (current?.level == event.level) current else SkillSnapshot(event.skill, event.level, null)
        authorities[event.skill] = Authority(event.level, now + 5_000)
        return Confirmation(NoticeRequest(
            id = "skyglint.skill_level_up:$key",
            title = "Skill level up!",
            detail = "${event.skill.displayName} · Level ${event.level}",
            priority = 60,
            item = ItemVisualHint(vanillaItemId = vanillaIcon(event.skill)),
        ), observed)
    }

    fun reconcile(incoming: SkillSnapshot, current: SkillSnapshot?, now: Long): SkillSnapshot {
        val authority = authorities[incoming.skill] ?: return incoming
        if (now >= authority.until) { authorities.remove(incoming.skill); return incoming }
        if (incoming.level == null || incoming.level < authority.level) return current ?: incoming
        return incoming
    }

    private fun vanillaIcon(skill: Skill): String = "minecraft:" + when (skill) {
        Skill.COMBAT -> "iron_sword"; Skill.FARMING -> "wheat"; Skill.FISHING -> "fishing_rod"
        Skill.MINING -> "diamond_pickaxe"; Skill.FORAGING -> "iron_axe"; Skill.ENCHANTING -> "enchanted_book"
        Skill.ALCHEMY -> "brewing_stand"; Skill.CARPENTRY -> "oak_planks"; Skill.TAMING -> "bone"
        Skill.HUNTING -> "bow"; Skill.RUNECRAFTING -> "amethyst_shard"; Skill.SOCIAL -> "name_tag"
    }
}
