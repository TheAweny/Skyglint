package dev.skyglint.core

/** An explicitly armed login summary. Snapshot data is presentation evidence, never gameplay activity. */
class WelcomeNoticePolicy {
    var wasOffered: Boolean = false; private set
    private var enteredAt: Long? = null
    private var lastKey: List<NoticePage>? = null
    private var changedAt = 0L
    fun arm(now: Long) { enteredAt = now; lastKey = null; changedAt = now; wasOffered = false }
    fun clear() { enteredAt = null; lastKey = null; wasOffered = false }
    fun observe(level: Int?, pet: PetSnapshot?, location: String?, now: Long): NoticeRequest? {
        val entered = enteredAt ?: return null
        if (now < entered) { clear(); return null }
        val pages = buildList {
            level?.let { add(NoticePage("SkyBlock · Level $it", "@player")) }
            pet?.let { add(NoticePage(it.name + (it.level?.let { level -> " · Lv. $level" } ?: ""),
                "@pet:${it.name}:${it.skin}")) }
            location?.takeIf { it.isNotBlank() }?.let { add(NoticePage(it, "minecraft:compass")) }
        }
        if (pages != lastKey) { lastKey = pages; changedAt = now }
        // Wait for initial inputs and structural settlement. Missing values are omitted at the bound.
        if (now - entered < 1_500 || (now - entered < 5_000 &&
                (level == null || pet == null || location == null || now - changedAt < 500))) return null
        enteredAt = null
        wasOffered = true
        return NoticeRequest("skyglint.welcome:$entered", "Welcome to SkyBlock", priority = 10,
            durationMillis = maxOf(4_000L, 1_000L + pages.size * 1_800L), freshnessMillis = 15_000,
            pages = pages, item = ItemVisualHint(vanillaItemId = "minecraft:compass"))
    }
}
