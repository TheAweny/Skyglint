package dev.skyglint.ui

import dev.skyglint.fabric.SkyglintClient
import dev.skyglint.fabric.ElectionHeads
import dev.skyglint.core.Skill
import dev.skyglint.core.PetSnapshot
import dev.skyglint.fabric.PetIconCache
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.motion.*
import dev.skyglint.ui.presentation.*
import dev.skyglint.ui.text.HudText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.item.ItemStack
import kotlin.math.*

/** Independent elements retain their payload and motion through entrance, takeover and exit. */
object SkyglintHud {
    private const val HEIGHT = 30f
    private const val GAP = 5.0
    private const val WHITE = 0xFFF1F5FA.toInt()
    private val composition = HudComposition()
    private val entries = linkedMapOf<String, Entry>()
    private val itemCache = mutableMapOf<String, ItemStack>()
    private val spriteCache = mutableMapOf<String, Identifier>()
    private var previousFrame = 0L
    private var session: String? = null

    private class Payload(val element: HudElement, val icon: ItemStack?, val sprite: Identifier?)
    private class Entry(var element: HudElement, var order: Int) {
        val motion = ElementMotion()
        val fill = CriticalSpring(element.progress ?: 0.0, 18.0)
        val pulse = PercentPulse()
        val percent = PercentTransition()
        val content = ContentTransition()
        val carousel = ContentTransition()
        var carouselLayers: List<ContentTransition.Layer> = emptyList()
        val payloads = linkedMapOf<String, Payload>()
        var currentKey = ""
        var state: ElementMotion.State? = null
        var percentState = PercentTransition.State(0.0, emptyList())
        var targetX = 0.0
        var identity = element.identity
        var iconRevision = 0
        var lastIcon: ItemStack? = null
        var contentLayers: List<ContentTransition.Layer> = emptyList()
    }

    fun render(g: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        if (client.gui.hud.isHidden || client.gui.overlay() != null) return
        var snapshot = SkyglintClient.hudSnapshot()
        val config = SkyglintClient.config.current
        if (!snapshot.active || (!config.enabled && !SkyglintClient.preview)) { reset(); return }
        if (session != snapshot.sessionId) { reset(); session = snapshot.sessionId }
        snapshot.activeNotice?.takeIf { it.rarity == null && SkyglintClient.noticeRarity != null }?.let {
            snapshot = snapshot.copy(activeNotice = it.copy(rarity = SkyglintClient.noticeRarity))
        }
        val now = SkyglintClient.now()
        val dt = if (previousFrame == 0L) 0.0 else (now - previousFrame).coerceAtLeast(0) / 1000.0
        previousFrame = now
        // Hidden progression keeps receiving new snapshots while a priority notice owns the stage.
        val underlying = composition.compose(snapshot.copy(activeNotice = null, notice = null), now, config.showPets, config.detailedDungeonHud)
        val desired = if (snapshot.activeNotice != null || snapshot.notice != null)
            composition.compose(snapshot, now, config.showPets, config.detailedDungeonHud) else underlying
        val desiredMap = desired.associateBy { it.id }
        val observed = (underlying + desired).associateBy { it.id }
        desired.forEachIndexed { index, element ->
            entries.getOrPut(element.id) { Entry(element, index) }.also { it.order = index }
        }
        var cursor = 0.0
        val ordered = entries.values.sortedWith(compareBy<Entry> { it.order }.thenBy { it.element.id })
        ordered.forEach { entry ->
            val next = observed[entry.element.id]
            if (next != null) {
                if (entry.identity != next.identity) {
                    entry.fill.snap(next.progress ?: 0.0)
                    entry.identity = next.identity
                }
                entry.element = next
                val icon = resolveIcon(next.icon, client)
                val previousIcon = entry.lastIcon
                val sameIcon = if (previousIcon == null || icon == null) previousIcon == null && icon == null
                    else ItemStack.isSameItemSameComponents(previousIcon, icon)
                if (!sameIcon) { entry.iconRevision++; entry.lastIcon = icon?.copy() }
                // Progress changes do not restart caption/icon transitions.
                // Metadata/tooltip-only changes do not animate otherwise unchanged visible content.
                val sprite = resolveSprite(next.icon)
                val content = next.visibleContentKey(entry.iconRevision, sprite?.toString())
                entry.payloads[content] = Payload(next, icon?.copy(), sprite)
                entry.currentKey = content
            }
            val data = entry.element
            if (data.kind == HudElementKind.CAROUSEL)
                entry.carouselLayers = entry.carousel.update(data.carouselIndex.toString(), dt, horizontal = true)
            val percent = entry.pulse.observe(data.identity, data.progress, now)
            entry.percentState = entry.percent.update(data.identity, percent, dt)
            entry.fill.step(data.progress ?: 0.0, dt)
            entry.targetX = cursor
            val state = entry.motion.update(data.id in desiredMap, cursor, width(data), entry.currentKey, dt)
            entry.state = state
            entry.contentLayers = entry.content.update(entry.currentKey, dt, data.verticalTransition)
            cursor += state.occupiedWidth + GAP * state.visibility
            val retained = entry.contentLayers.mapTo(mutableSetOf(entry.currentKey)) { it.key }
            entry.payloads.keys.retainAll(retained)
        }
        val last = ordered.lastOrNull { it.state?.visible == true }
        val total = (cursor - GAP * (last?.state?.visibility ?: 0.0)).coerceAtLeast(1.0)
        if (ordered.none { it.state?.visible == true }) return
        val previewHeight = if (SkyglintClient.preview) 14 else 0
        val viewport = HudViewport.fit(g.guiWidth(), g.guiHeight(), client.window.guiScale, config.renderScale,
            ceil(total).toInt(), HEIGHT.toInt() + previewHeight, config.topOffset, ceil(total).toInt().coerceAtLeast(1_000))
        HudBackdrop.beginFrame(g)
        g.pose().pushMatrix()
        try {
            g.pose().translate(viewport.x, viewport.y)
            g.pose().scale(viewport.scale, viewport.scale)
            if (SkyglintClient.preview) {
                smallText(g, "PREVIEW", viewport.width / 2f, 0f, alpha(WHITE, .6), .62f)
                g.pose().translate(0f, previewHeight.toFloat())
            }
            ordered.forEach { entry ->
                val state = entry.state ?: return@forEach
                if (!state.visible) return@forEach
                val visibility = state.visibility.toFloat()
                g.pose().pushMatrix()
                try {
                    // Width/presence already interpolate the shared boundary. Filtering that same
                    // boundary through another position spring makes neighbours overlap on growth.
                    g.pose().translate((entry.targetX + state.occupiedWidth / 2).toFloat(), HEIGHT / 2)
                    g.pose().scale(visibility, visibility)
                    g.pose().translate(-state.width.toFloat() / 2, -HEIGHT / 2)
                    drawElement(g, entry, state.width.toFloat(), state)
                } finally { g.pose().popMatrix() }
                if (client.gui.screen() != null) {
                    val mx = client.mouseHandler.xpos() / client.window.guiScale
                    val my = client.mouseHandler.ypos() / client.window.guiScale
                    val left = viewport.x + entry.targetX * viewport.scale
                    val top = viewport.y + previewHeight * viewport.scale
                    if (mx >= left && mx < left + state.occupiedWidth * viewport.scale && my >= top && my < top + HEIGHT * viewport.scale) {
                        val data = entry.element
                        val tip = listOfNotNull(data.title, data.value, data.subtitle, data.countdown?.caption(now),
                            data.progress?.takeIf { data.countdown == null }?.let { "${(it * 10000).roundToInt() / 100.0}%" }, data.badge?.let { "Tier $it" }).joinToString(" · ")
                        g.setTooltipForNextFrame(HudText.component(tip), mx.toInt(), my.toInt())
                    }
                }
            }
        } finally { g.pose().popMatrix() }
        entries.entries.removeIf { (id, entry) -> id !in observed && entry.state?.visible != true }
    }

    private fun width(element: HudElement): Double = when (element.kind) {
        HudElementKind.MAIN -> ElementGeometry.mainWidth(HudText.width(element.prefix.orEmpty() + element.title),
            element.value?.let { HudText.width(" · $it") } ?: 0, element.icon != null)
        HudElementKind.PROGRESS, HudElementKind.TIMER, HudElementKind.ICON -> 30.0
        HudElementKind.STRIP -> stripWidth(element)
        HudElementKind.CAROUSEL -> carouselWidth(element)
        HudElementKind.COUNTER -> (HudText.width(element.value.orEmpty()) * .86 + 35).coerceIn(43.0, 180.0)
    }

    private fun drawElement(g: GuiGraphicsExtractor, entry: Entry, width: Float, state: ElementMotion.State) {
        val layers = entry.contentLayers.mapNotNull { layer -> entry.payloads[layer.key]?.let {
            layer.copy(opacity = layer.opacity * state.visibility) to it
        } }
        val accent = if (entry.element.pulse) 0xFFFF777D.toInt() else blendedColor(layers.map { it.second.element.accent to it.first.opacity })
        surface(g, width, accent, state.visibility.toFloat())
        if (entry.element.pulse) {
            val strength = .08 + .13 * (.5 + .5 * sin(SkyglintClient.now() / 180.0))
            fill(g, width, 1f, alpha(accent, strength * state.visibility))
        }
        if (entry.element.kind in setOf(HudElementKind.PROGRESS, HudElementKind.TIMER)) {
            HudBackdrop.contour(g, 0f, 0f, width, HEIGHT,
                if (entry.element.progress != null) entry.fill.value.coerceIn(0.0, 1.0).toFloat() else null,
                accent, state.visibility.toFloat())
        } else if (entry.element.progress != null && entry.element.kind == HudElementKind.MAIN) {
            fill(g, width, entry.fill.value.coerceIn(0.0, 1.0).toFloat(), alpha(accent, .11 * state.visibility))
        }
        g.enableScissor(3, 2, (width - 3).toInt().coerceAtLeast(4), 29)
        try {
        layers.forEach { (layer, payload) ->
            val opacity = layer.opacity
            if (opacity <= .003) return@forEach
            val data = payload.element
            val normalized = (opacity / state.visibility.coerceAtLeast(.001)).coerceIn(0.0, 1.0)
            g.pose().pushMatrix()
            try {
            g.pose().translate(0f, layer.offsetY.toFloat())
            when (data.kind) {
                HudElementKind.MAIN -> {
                    val custom = data.sourceWidget
                    if (custom != null && UiWidgetRegistry.find(custom.id) != null) {
                        g.pose().pushMatrix()
                        val rendered = try {
                            g.pose().translate(width / 2f, HEIGHT / 2f)
                            g.pose().scale(normalized.toFloat(), normalized.toFloat())
                            g.pose().translate(-width / 2f, -HEIGHT / 2f)
                            UiWidgetRegistry.render(g, custom, 0, 0, width.roundToInt(), HEIGHT.roundToInt())
                        } finally { g.pose().popMatrix() }
                        if (rendered) return@forEach
                    }
                    val prefix = data.prefix.orEmpty()
                    val tail = data.displayValue(entry.element)?.let { " · $it" }.orEmpty()
                    val maximum = ElementGeometry.textBudget(max(width.toDouble(), width(data)), data.icon != null)
                    val title = HudText.fit(data.title, maximum - HudText.width(prefix + tail))
                    val all = prefix + title + tail
                    val group = ElementGeometry.group(width, HudText.width(all), data.icon != null)
                    group.iconCenter?.let { center -> payload.icon?.let { scaledItem(g, it, center,
                        ElementGeometry.ICON_SIZE.toFloat(), normalized.toFloat()) } }
                    val x = group.textLeft
                    val y = HudText.centeredOriginY(all, 0f, HEIGHT)
                    if (prefix.isNotEmpty()) HudText.draw(g, prefix, x, y, alpha(0xFFB0BAC8.toInt(), opacity))
                    HudText.draw(g, title, x + HudText.width(prefix), y, alpha(if (data.coloredText != null) data.accent else WHITE, opacity))
                    if (tail.isNotEmpty()) HudText.draw(g, tail, x + HudText.width(prefix + title), y, alpha(0xFFA8DFFF.toInt(), opacity))
                }
                HudElementKind.COUNTER -> {
                    if (payload.sprite != null) scaledSprite(g, payload.sprite, 14f, 20f,
                        normalized.toFloat(), opacity.toFloat())
                    else payload.icon?.let { scaledItem(g, it, 14f, 14f, normalized.toFloat()) }
                    val text = HudText.fit(data.value.orEmpty(), floor((width - 30f) / .86f).toInt())
                    val x = 26f + (width - 30f - HudText.width(text) * .86f) / 2
                    text(g, text, x, HudText.centeredOriginY(text, 0f, HEIGHT, .86f), alpha(if (data.coloredText != null) data.accent else WHITE, opacity), .86f)
                }
                HudElementKind.PROGRESS -> {
                    val amount = entry.percentState.amount
                    payload.icon?.let { scaledItem(g, it, 15f, if (data.badge == null) 16f else 14f,
                        (normalized * (1 - amount)).toFloat(), if (data.badge == null) 15f else 12.5f) }
                    if (payload.icon == null && amount < .99) smallText(g, "?", 15f,
                        HudText.centeredOriginY("?", 0f, HEIGHT, .85f), alpha(WHITE, opacity * (1 - amount)), .85f)
                    data.badge?.takeIf { amount < .99 }?.let { smallText(g, it, 15f, 21f,
                        alpha(WHITE, opacity * (1 - amount)), .52f) }
                }
                HudElementKind.TIMER -> {
                    if (data.value == "X2") {
                        // A fixed label and fixed geometry: server timer digits never animate this body.
                        val label = "X2"
                        smallText(g, label, 15f, HudText.centeredOriginY(label, 0f, HEIGHT, .9f), alpha(WHITE, opacity), .9f)
                    } else payload.icon?.let { scaledItem(g, it, 15f, 16f, normalized.toFloat()) }
                }
                HudElementKind.ICON -> {
                    payload.icon?.let { scaledItem(g, it, 15f, 16f, normalized.toFloat()) }
                        ?: smallText(g, "?", 15f, HudText.centeredOriginY("?", 0f, HEIGHT, .85f), alpha(WHITE, opacity), .85f)
                    if (data.icon == "@sack_disabled") disabledSackMark(g, 15f, normalized.toFloat(), opacity)
                }
                HudElementKind.STRIP -> {
                    // Related typed metrics share one material and measured baseline.
                    var x = (width - stripWidth(data).toFloat()) / 2f + stripPadding(data)
                    if (data.title.isNotEmpty()) {
                        text(g, data.title, x, HudText.centeredOriginY(data.title, 0f, HEIGHT, .82f), alpha(data.accent, opacity), .82f)
                        x += HudText.width(data.title) * .82f + 14f
                    }
                    data.parts.forEach { part ->
                        drawInline(g, part, x, .82f, normalized.toFloat(), opacity)
                        x += inlineWidth(part, .82).toFloat() + stripGap(data)
                    }
                }
                HudElementKind.CAROUSEL -> {
                    val left = (width - carouselWidth(data).toFloat()) / 2f + 14f
                    // Measure the full carousel's ink once as one line: different page descenders
                    // must not move the right caption relative to its fixed heading and item.
                    val baseline = HudText.centeredOriginY(
                        (listOf(data.title) + data.parts.map { it.text }).joinToString(" "), 0f, HEIGHT)
                    HudText.draw(g, data.title, left, baseline, alpha(data.accent, opacity))
                    val cropLeft = left + HudText.width(data.title) + 14f
                    g.enableScissor(cropLeft.toInt(), 2, (width - 10).toInt().coerceAtLeast(cropLeft.toInt() + 1), 29)
                    try {
                        entry.carouselLayers.forEach { crop ->
                            val part = data.parts.getOrNull(crop.key.toIntOrNull() ?: -1) ?: return@forEach
                            drawInline(g, part, cropLeft + crop.offsetX.toFloat(), 1f,
                                (normalized * crop.opacity).toFloat(), opacity * crop.opacity, baseline)
                        }
                    } finally { g.disableScissor() }
                }
            }
            } finally { g.pose().popMatrix() }
        }
        if (entry.element.kind == HudElementKind.PROGRESS) {
            val percent = entry.percentState
            if (percent.amount > .003) percent.captions.forEach { layer ->
                val scale = (.74 + .08 * percent.amount).toFloat()
                smallText(g, layer.text, 15f, HudText.centeredOriginY(layer.text, 0f, HEIGHT, scale),
                    alpha(WHITE, layer.opacity * percent.amount * state.visibility), scale)
            }
        }
        } finally { g.disableScissor() }
    }

    private fun resolveIcon(reference: String?, client: Minecraft): ItemStack? {
        if (client.level == null || reference == null) return null
        return when {
            reference.startsWith("@rank:") -> if (RankVisuals.sprite(reference) == null) native("minecraft:nether_star") else null
            reference == "@pet" -> SkyglintClient.petIcon
            reference == "@fishing_bait" -> SkyglintClient.fishingBaitIcon
            reference == "@sack_disabled" -> native("minecraft:bundle")
            reference.startsWith("@pet:") -> reference.removePrefix("@pet:").let { identity ->
                PetIconCache.resolve(PetSnapshot(identity.substringBeforeLast(':'), null,
                    skin = identity.substringAfterLast(':') == "true"))
            }
            reference.startsWith("@candidate:") -> ElectionHeads.resolve(reference)
            reference == "@player" -> SkyglintClient.skyBlockIcon
            reference == "@notice" -> SkyglintClient.noticeIcon ?: native("minecraft:nether_star")
            reference.startsWith("@notice:") -> SkyglintClient.noticeIcon ?: native(reference.removePrefix("@notice:"))
            reference.startsWith("@skill:") -> Skill.entries.firstOrNull { it.name == reference.removePrefix("@skill:") }?.let(SkillItems::icon)
            else -> native(reference)
        }
    }
    private fun resolveSprite(reference: String?): Identifier? {
        val path = RankVisuals.sprite(reference) ?: return null
        return spriteCache.getOrPut(path) { Identifier.fromNamespaceAndPath("skyglint", path) }
    }
    private fun native(reference: String): ItemStack? = itemCache[reference] ?: Identifier.tryParse(reference)?.let { id ->
        if (id.namespace != "minecraft") null else BuiltInRegistries.ITEM.getOptional(id).orElse(null)?.let { item ->
            ItemStack(item).also { itemCache[reference] = it }
        }
    }
    private fun iconOnlyStrip(element: HudElement): Boolean = element.title.isEmpty() && element.parts.all { it.text.isEmpty() }
    private fun stripPadding(element: HudElement): Float = if (iconOnlyStrip(element)) 7f else 14f
    private fun stripGap(element: HudElement): Float = if (iconOnlyStrip(element)) 8f else 14f
    private fun inlineWidth(part: HudInlinePart, textScale: Double): Double =
        16.0 + if (part.text.isEmpty()) 0.0 else 5.0 + HudText.width(part.text) * textScale
    private fun stripWidth(element: HudElement): Double = stripPadding(element) * 2.0 +
        (if (element.title.isEmpty()) 0.0 else HudText.width(element.title) * .82 + 14.0) +
        element.parts.sumOf { inlineWidth(it, .82) } + stripGap(element) * (element.parts.size - 1).coerceAtLeast(0)
    private fun carouselWidth(element: HudElement): Double = 28.0 + HudText.width(element.title) + 14.0 +
        (element.parts.maxOfOrNull { inlineWidth(it, 1.0) } ?: 0.0)
    private fun drawInline(g: GuiGraphicsExtractor, part: HudInlinePart, left: Float, textScale: Float, scale: Float,
                           opacity: Double, textOriginY: Float? = null) {
        val sprite = resolveSprite(part.icon)
        if (sprite != null) scaledSprite(g, sprite, left + 8f, 20f, scale, opacity.toFloat())
        else {
            val item = resolveIcon(part.icon, Minecraft.getInstance())
            if (item != null) scaledItem(g, item, left + 8f, 16f, scale)
            else smallText(g, "?", left + 8f, textOriginY ?: HudText.centeredOriginY("?", 0f, HEIGHT, textScale), alpha(part.accent, opacity), textScale)
        }
        if (part.icon == "@sack_disabled") disabledSackMark(g, left + 8f, scale, opacity)
        if (part.text.isNotEmpty()) text(g, part.text, left + 21f, textOriginY ?: HudText.centeredOriginY(part.text, 0f, HEIGHT, textScale),
            alpha(if (part.colored) part.accent else WHITE, opacity), textScale)
    }
    /** A crossed sack denotes a disabled toggle, independently of any observed bait count. */
    private fun disabledSackMark(g: GuiGraphicsExtractor, centerX: Float, scale: Float, opacity: Double) {
        if (scale <= .003f || opacity <= .003) return
        g.pose().pushMatrix()
        try {
            g.pose().translate(centerX, HEIGHT / 2f)
            g.pose().scale(scale, scale)
            for (step in -5..4) g.fill(step - 1, -step - 2, step + 3, -step + 2, alpha(0xFF171C23.toInt(), opacity * .8))
            for (step in -5..4) g.fill(step, -step - 1, step + 2, -step + 1, alpha(0xFFD3B071.toInt(), opacity))
        } finally { g.pose().popMatrix() }
    }
    private fun scaledItem(g: GuiGraphicsExtractor, stack: ItemStack, centerX: Float, size: Float,
                           scale: Float, centerY: Float = 15f) {
        if (scale <= .003f) return
        g.pose().pushMatrix()
        try {
            g.pose().translate(centerX, centerY); g.pose().scale(size / 16 * scale, size / 16 * scale)
            g.pose().translate(-8f, -8f); g.item(stack, 0, 0)
        } finally { g.pose().popMatrix() }
    }
    private fun scaledSprite(g: GuiGraphicsExtractor, sprite: Identifier, centerX: Float, size: Float,
                             scale: Float, opacity: Float, centerY: Float = 15f) {
        if (scale <= .003f || opacity <= .003f) return
        g.pose().pushMatrix()
        try {
            g.pose().translate(centerX, centerY)
            g.pose().scale(size / 32f * scale, size / 32f * scale)
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -16, -16, 32, 32, opacity.coerceIn(0f, 1f))
        } finally { g.pose().popMatrix() }
    }
    private fun surface(g: GuiGraphicsExtractor, width: Float, accent: Int, opacity: Float) {
        HudBackdrop.capsule(g, 0f, 0f, width, HEIGHT, 15f, opacity)
        fill(g, width, 1f, alpha(0xFF10161F.toInt(), .40 * opacity))
        fill(g, width, 1f, alpha(accent, .065 * opacity))
    }
    private fun fill(g: GuiGraphicsExtractor, width: Float, fraction: Float, color: Int) {
        val radius = min(15f, width / 2)
        val end = width * fraction
        for (dy in 0 until HEIGHT.toInt()) {
            val cy = abs(dy + .5f - HEIGHT / 2)
            val inset = radius - sqrt((radius * radius - cy * cy).coerceAtLeast(0f))
            val right = min(width - inset, end)
            if (right > inset) g.fill(inset.roundToInt(), dy, right.roundToInt(), dy + 1, color)
        }
    }
    private fun text(g: GuiGraphicsExtractor, value: String, x: Float, y: Float, color: Int, scale: Float) {
        g.pose().pushMatrix()
        try { g.pose().translate(x, y); g.pose().scale(scale, scale); HudText.draw(g, value, 0, 0, color) }
        finally { g.pose().popMatrix() }
    }
    private fun smallText(g: GuiGraphicsExtractor, value: String, x: Float, y: Float, color: Int, scale: Float) {
        text(g, value, x - HudText.width(value) * scale / 2, y, color, scale)
    }
    private fun alpha(color: Int, opacity: Double): Int = ((opacity.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (color and 0xFFFFFF)
    private fun blendedColor(layers: List<Pair<Int, Double>>): Int {
        val total = layers.sumOf { it.second }.coerceAtLeast(.001)
        fun channel(shift: Int) = layers.sumOf { ((it.first shr shift) and 255) * it.second }.div(total).roundToInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    private fun reset() { entries.clear(); composition.reset(); previousFrame = 0; session = null }
}
