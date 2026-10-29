package dev.skyglint.ui.presentation

/** Domain identity, clocks and asset aliases must not animate an unchanged visible payload. */
fun HudElement.visibleContentKey(iconRevision: Int, sprite: String? = null): String =
    copy(progress = null, sourceWidget = null, subtitle = null, countdown = null,
        identity = "", icon = null, value = if (liveValue) null else value,
        parts = if (liveValue && kind == HudElementKind.STRIP) parts.map { it.copy(text = "", tail = "") } else parts,
        coloredText = if (liveValue) "live-value" else coloredText, carouselIndex = 0).toString() +
        ":$iconRevision:$sprite"

/** An outgoing live caption retains its last value when the element morphs into another form. */
fun HudElement.displayValue(current: HudElement): String? =
    if (liveValue && current.liveValue && kind == current.kind && identity == current.identity) current.value else value
