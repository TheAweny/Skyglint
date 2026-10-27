package dev.skyglint.ui.presentation

/** Presentation clock only: server observation owns the pages and notification lifetime. */
object NoticeCarousel {
    const val INTRO_MILLIS = 1_000L
    const val PAGE_MILLIS = 1_800L
    /** -1 is the introduction. Final page holds rather than looping before the notice exits. */
    fun pageIndex(ageMillis: Long, pageCount: Int): Int {
        if (pageCount <= 0 || ageMillis < INTRO_MILLIS) return -1
        return ((ageMillis - INTRO_MILLIS) / PAGE_MILLIS).coerceAtMost((pageCount - 1).toLong()).toInt()
    }
}
