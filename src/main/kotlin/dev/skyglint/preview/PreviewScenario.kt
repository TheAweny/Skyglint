package dev.skyglint.preview

/** Stable command IDs and labels shared by the native controls and preview lifecycle. */
enum class PreviewScenario(val id: String, val label: String) {
    OBJECTIVE("objective", "Objective"), SKILL("skill", "Skill"), SKYBLOCK("skyblock", "SB XP"),
    LEVEL_UP("levelup", "Level up"), RARE("rare", "Rare drop"), UNKNOWN("unknown", "Unknown"),
    SEQUENCE("sequence", "Sequence"), IDLE("idle", "Location"), EVENT("event", "Schedule"),
    PET_LEVEL_UP("petlevelup", "Pet level up"), SKILL_LEVEL_UP("skilllevelup", "Skill level up"),
    GARDEN("garden", "Garden"), DUNGEON("dungeon", "Catacombs"), ELECTION("election", "Election"),
    RANKS("ranks", "Ranks"), MINING("mining", "Mining"), NUCLEUS("nucleus", "Nucleus run"),
    CONFIRMATIONS("confirmations", "Confirmations"), SLAYER_PETS("slayerpets", "Slayer / pets"),
    FISHING("fishing", "Fishing"), WINTER("winter", "Jerry defense"),
    REINDRAKE("reindrake", "Reindrake"), BUFFS("buffs", "Buff expiry"),
    BUFF_WARNING("buffwarning", "Cookie warning"), GOD_WARNING("godwarning", "God Potion warning"),
    DEFENSE_RESULTS("defenseresults", "Defense results");

    companion object {
        fun fromId(id: String): PreviewScenario = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown preview scenario: $id")
    }
}
