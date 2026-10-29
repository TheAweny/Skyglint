package dev.skyglint.updates.github

/** Presentation instructions, independent of Minecraft UI and of the download operation. */
data class UpdateAnnouncement(
    val version: String,
    val importance: UpdateImportance,
    val durationMillis: Long,
    val priority: Int,
)

/**
 * Owned by one client launch. Every importance appears once per version per launch.
 * Reconnecting and island/session resets never replay an announcement.
 * The adapter calls [offer] only when it can deliver the chat/optional HUD presentation.
 */
class UpdateAnnouncementPolicy {
    private val announcedThisLaunch = mutableSetOf<String>()

    fun offer(release: UpdateRelease): UpdateAnnouncement? {
        if (!announcedThisLaunch.add(release.version)) return null
        val (duration, priority) = when (release.importance) {
            UpdateImportance.NORMAL -> 4_000L to 10
            UpdateImportance.IMPORTANT -> 6_000L to 20
            UpdateImportance.CRITICAL -> 8_000L to 30
        }
        return UpdateAnnouncement(release.version, release.importance, duration, priority)
    }

    /** Kept as an adapter lifecycle hook. Only a full client restart resets announcements. */
    fun onDisconnected() = Unit
}
