package dev.skyglint.ui.presentation

import dev.skyglint.core.NoticeRequest
import dev.skyglint.updates.github.UpdateAnnouncement
import dev.skyglint.updates.github.UpdateImportance

/** Application announcements reuse the short-notice surface without becoming gameplay input. */
object UpdateNoticePresentation {
    fun notice(announcement: UpdateAnnouncement): NoticeRequest = NoticeRequest(
        id = "skyglint.update:${announcement.version}",
        title = heading(announcement.importance),
        detail = announcement.version,
        priority = announcement.priority,
        durationMillis = announcement.durationMillis,
        freshnessMillis = 15_000,
    )

    fun heading(importance: UpdateImportance): String = when (importance) {
        UpdateImportance.NORMAL -> "Update available"
        UpdateImportance.IMPORTANT -> "Important update available"
        UpdateImportance.CRITICAL -> "Critical update available"
    }
}
