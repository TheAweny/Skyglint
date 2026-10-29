package dev.skyglint.ui

import dev.skyglint.fabric.ClientSettings
import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.motion.CriticalSpring
import dev.skyglint.ui.settings.*
import dev.skyglint.ui.text.SettingsText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** One native workspace: navigation, module settings and updates share geometry, material and motion. */
open class SkyglintSettingsScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val actions: SettingsActions = ClientSettings,
    initialPage: String = "modules",
    private val updatePreview: GitHubUpdates.View? = null,
) : Screen(Component.literal("Skyglint")) {
    private val catalog = WorkspaceCatalog(actions)
    private val updates = UpdatesPage(updatePreview)
    private var pageId = initialPage
    private var pendingPage: String? = null
    private var query = ""
    private var firstRow = 0
    private var rowsPerPage = 1
    private var columns = 1
    private var resultCount = 0
    private var status = "Changes save automatically"
    private var error = false
    private var needsSearch = false
    private var closing = false
    private lateinit var search: EditBox
    private var px = 0; private var py = 0; private var pw = 0; private var ph = 0
    private var nav = 0; private var cx = 0; private var cw = 0; private var contentTop = 0; private var contentBottom = 0
    private var frame = 0L
    private val entrance = CriticalSpring(0.0, 23.0)
    private val page = CriticalSpring(1.0, 28.0)
    private val navSelection = CriticalSpring(0.0, 26.0)
    private var selectionInitialized = false
    private val chrome = mutableListOf<Renderable>()
    private val body = mutableListOf<SettingsButton>()
    private val navigation = mutableListOf<SettingsButton>()
    private var navPages = emptyList<SettingsPage>()

    /** Existing workspace instances keep their shell and navigation when updates are opened. */
    fun showUpdates() = navigate("updates")
    private fun navigate(id: String) {
        if (id == (pendingPage ?: pageId) && query.isBlank()) return
        pendingPage = id
    }

    override fun init() {
        chrome.clear(); body.clear(); navigation.clear()
        pw = minOf(760, width - 28).coerceAtLeast(220)
        ph = minOf(420, height - 28).coerceAtLeast(180)
        px = (width - pw) / 2; py = (height - ph) / 2
        nav = if (pw >= 650) 130 else 125.coerceAtMost(pw / 2)
        cx = px + nav + 20; cw = (pw - nav - 40).coerceAtLeast(60)
        contentTop = py + if (ph >= 310) 80 else 65
        contentBottom = py + ph - 35
        navPages = catalog.pages.filter { it.navigation }.toMutableList().apply {
            add(4.coerceAtMost(size), SettingsPage("updates", "Updates", "Release notes and update preferences.", true) { emptyList() })
        }
        val navTop = py + if (ph >= 310) 78 else 55
        val navStep = if (ph >= 310) 29 else ((ph - 94) / 6).coerceIn(14, 22)
        navPages.forEachIndexed { index, target ->
            val button = SettingsButton(px + 10, navTop + index * navStep, nav - 20, navStep - 3,
                if (target.id == "events") "Events" else target.title,
                hint = target.description.ifBlank { target.title }) { navigate(target.id) }
            button.kind = SettingsButton.Kind.NAVIGATION; button.icon = target.id
            button.selected = { target.id == selectedNavigation() }
            navigation += button; chrome += button; addRenderableWidget(button)
        }
        val selectedIndex = navIndex(pendingPage ?: pageId)
        if (!selectionInitialized) { navSelection.snap((navTop + selectedIndex * navStep).toDouble()); selectionInitialized = true }
        val done = SettingsButton(px + 10, py + ph - 36, nav - 20, 24, "Done", hint = "Close settings.") { onClose() }
        done.icon = "back"; chrome += done; addRenderableWidget(done)

        val searchWidth = minOf(186, cw / 2).coerceAtLeast(60)
        search = SettingsEditBox(font, cx + cw - searchWidth, py + 20, searchWidth, 24, "Search settings", "Search settings…")
        search.setMaxLength(96); search.setValue(query); search.setHint(Component.literal("Search settings…"))
        search.setResponder { value -> if (value != query) { query = value; firstRow = 0; needsSearch = true } }
        search.visible = pageId != "updates"
        chrome += search; addRenderableWidget(search)
        if (pageId == "updates" && query.isBlank()) {
            updates.controls(cx, contentTop, cw, contentBottom - contentTop).forEach { button -> body += button; addRenderableWidget(button) }
            resultCount = 0; rowsPerPage = 1
            return
        }
        val selected = catalog.pages.firstOrNull { it.id == pageId } ?: catalog.pages.first()
        val overview = pageId == "modules" && query.isBlank()
        val controls = if (query.isBlank()) selected.controls(actions) else catalog.pages.flatMap { it.controls(actions) }
            .distinctBy { it.id }.filter { (it.caption + " " + it.hint).contains(query, ignoreCase = true) }
        columns = if (overview && cw >= 410) 2 else 1
        val gap = if (ph >= 310) 8 else 6
        val rowHeight = if (overview) (if (ph >= 310) 52 else 43) else (if (ph >= 310) 48 else 32)
        val extra = if (pageId in setOf("hud", "rewards") && query.isBlank()) 39 else 0
        val availableRows = ((contentBottom - contentTop - extra + gap) / (rowHeight + gap)).coerceAtLeast(1)
        rowsPerPage = availableRows * columns
        resultCount = if (overview) catalog.pages.count { !it.navigation } else controls.size
        firstRow = firstRow.coerceIn(0, ((resultCount - 1).coerceAtLeast(0) / columns - availableRows + 1).coerceAtLeast(0) * columns)
        if (extra > 0) {
            if (pageId == "hud") bodyButton(cx, contentTop, cw, 30, "Open HUD editor", "Position & scale", "Move and scale the linked row.", "hud") {
                minecraft.gui.setScreen(SkyglintHudEditorScreen(this, actions))
            } else bodyButton(cx, contentTop, cw, 30, "Item rules", "Show / Hide / Reset", "Choose which individual rewards appear.", "rewards") {
                minecraft.gui.setScreen(SkyglintRewardRulesScreen(this, actions))
            }
        }
        if (overview) {
            val cardWidth = (cw - (columns - 1) * gap) / columns
            catalog.pages.filter { !it.navigation }.drop(firstRow).take(rowsPerPage).forEachIndexed { index, target ->
                val button = bodyButton(cx + index % columns * (cardWidth + gap), contentTop + index / columns * (rowHeight + gap),
                    cardWidth, rowHeight, target.title, "›", target.description, target.id) { navigate(target.id) }
                button.kind = SettingsButton.Kind.CARD
            }
        } else controls.drop(firstRow).take(rowsPerPage).forEachIndexed { index, control ->
            val button = SettingsButton(cx, contentTop + extra + index * (rowHeight + gap), cw, rowHeight,
                control.caption, control.value, control.state, control.hint) {
                try { control.action(); error = false; status = if (control.persisted) "Saved" else control.transientStatus }
                catch (failure: Exception) { error = true; status = "Could not save: ${failure.message ?: failure.javaClass.simpleName}" }
                body.forEach(SettingsButton::refreshMessage)
            }
            button.kind = SettingsButton.Kind.ROW; body += button; addRenderableWidget(button)
        }
    }

    private fun bodyButton(x: Int, y: Int, w: Int, h: Int, title: String, value: String, hint: String, icon: String,
                           action: () -> Unit): SettingsButton = SettingsButton(x, y, w, h, title, { value }, hint = hint, action = action).also {
        it.icon = icon; body += it; addRenderableWidget(it)
    }
    private fun navIndex(id: String): Int = navPages.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 1
    private fun selectedNavigation(): String = if (navPages.any { it.id == (pendingPage ?: pageId) }) pendingPage ?: pageId else "modules"

    override fun tick() {
        if (needsSearch) { needsSearch = false; rebuildWidgets(); setInitialFocus(search); search.moveCursorToEnd(false) }
        if (pageId == "updates" && updates.tick()) rebuildWidgets()
    }
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (closing || entrance.value < .85 || (event.x() >= cx && event.y() >= contentTop && page.value < .9)) return true
        return super.mouseClicked(event, doubleClick)
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (mouseX in cx.toDouble()..(cx + cw).toDouble() && mouseY >= contentTop && mouseY < contentBottom && resultCount > rowsPerPage) {
            val maximum = ((resultCount - rowsPerPage + columns - 1) / columns).coerceAtLeast(0) * columns
            firstRow = (firstRow + if (vertical < 0) columns else -columns).coerceIn(0, maximum)
            rebuildWidgets(); return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.nanoTime()
        val dt = if (frame == 0L) 0.0 else ((now - frame) / 1e9).coerceIn(0.0, .08)
        frame = now
        entrance.step(if (closing) 0.0 else 1.0, dt)
        if (closing && entrance.value < .02) { minecraft.gui.setScreen(parent); return }
        page.step(if (pendingPage == null) 1.0 else 0.0, dt)
        if (pendingPage != null && page.value < .035) {
            pageId = pendingPage!!; pendingPage = null; query = ""; firstRow = 0; rebuildWidgets()
        }
        val navTop = py + if (ph >= 310) 78 else 55
        val navStep = if (ph >= 310) 29 else ((ph - 94) / 6).coerceIn(14, 22)
        navSelection.step((navTop + navIndex(selectedNavigation()) * navStep).toDouble(), dt)
        val alpha = entrance.value.coerceIn(0.0, 1.0).toFloat()
        val scale = .975f + alpha * .025f
        g.fill(0, 0, width, height, ((88 * alpha).toInt() shl 24) or 0x08090D)
        HudBackdrop.beginFrame(g)
        g.pose().pushMatrix()
        val oldTextOpacity = SettingsText.opacity
        val oldPaintOpacity = SettingsPaint.opacity
        try {
            g.pose().translate(width / 2f, height / 2f + (1 - alpha) * 8)
            g.pose().scale(scale, scale); g.pose().translate(-width / 2f, -height / 2f)
            SettingsText.opacity = alpha; SettingsPaint.opacity = alpha
            HudBackdrop.capsule(g, px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 10f, alpha)
            SettingsPaint.rounded(g, px, py, pw, ph, 10, 0xED1B1D27.toInt())
            SettingsPaint.rounded(g, px, py, nav, ph, 10, 0xD5191B25.toInt())
            g.fill(px + nav, py + 14, px + nav + 1, py + ph - 14, SettingsPaint.color(0x304D4B5A))
            g.fill(cx, py + 65, cx + cw, py + 66, SettingsPaint.color(0x27504C5C))
            SettingsText.drawSized(g, "Skyglint", px + 21f, py + 20f, 13.5f, SettingsPaint.INK)
            if (ph >= 310) SettingsText.drawSized(g, "WORKSPACE", px + 21f, py + 55f, 6.8f, SettingsPaint.MUTED)
            SettingsPaint.rounded(g, px + 10f, navSelection.value.toFloat(), (nav - 20).toFloat(), (navStep - 3).toFloat(), 7f, 0x24BAA6EE)
            val heading = when {
                query.isNotBlank() -> "Search results"
                pageId == "updates" -> "Updates"
                else -> catalog.pages.firstOrNull { it.id == pageId }?.title ?: "Modules"
            }
            val titleBudget = if (search.visible) cw - search.width - 18 else cw
            SettingsText.opacity = alpha * page.value.coerceIn(0.0, 1.0).toFloat()
            SettingsText.drawSized(g, SettingsText.fit(heading, titleBudget.coerceAtLeast(24), 17f), cx.toFloat(), py + 22f, 17f, SettingsPaint.INK)
            val description = when {
                query.isNotBlank() -> "$resultCount matching settings"
                pageId == "updates" -> "Release notes, installation and update preferences."
                else -> catalog.pages.firstOrNull { it.id == pageId }?.description.orEmpty()
            }
            if (ph >= 310) SettingsText.drawSized(g, SettingsText.fit(description, cw, 8f), cx.toFloat(), py + 51f, 8f, SettingsPaint.MUTED)
            SettingsText.opacity = alpha
            chrome.forEach { it.extractRenderState(g, mouseX, mouseY, delta) }
            if (pageId != "updates") {
                val footer = if (resultCount > rowsPerPage) "Scroll for more · ${firstRow + 1}–${minOf(firstRow + rowsPerPage, resultCount)} of $resultCount" else status
                SettingsText.drawSized(g, SettingsText.fit(footer, cw - 30, 7.3f), cx.toFloat(), py + ph - 19f, 7.3f,
                    if (error) 0xFFFFB1AA.toInt() else SettingsPaint.MUTED)
            }
            g.enableScissor(cx - 1, contentTop - 1, cx + cw + 2, contentBottom + 1)
            try {
                val contentAlpha = page.value.coerceIn(0.0, 1.0).toFloat()
                SettingsText.opacity = alpha * contentAlpha; SettingsPaint.opacity = alpha * contentAlpha
                g.pose().pushMatrix()
                try {
                    g.pose().translate(0f, (1 - contentAlpha) * 7f)
                    if (pageId == "updates" && query.isBlank()) updates.render(g, cx, contentTop, cw, contentBottom - contentTop, mouseX, mouseY)
                    else if (resultCount == 0) SettingsText.draw(g, "No settings match your search.", cx, contentTop + 16, SettingsPaint.MUTED)
                    body.forEach { it.extractRenderState(g, mouseX, mouseY, delta) }
                } finally { g.pose().popMatrix() }
            } finally { g.disableScissor() }
            SettingsPaint.opacity = alpha; SettingsText.opacity = alpha
            if (pageId != "updates" && resultCount > rowsPerPage) {
                val track = (contentBottom - contentTop).toFloat()
                val thumb = (track * rowsPerPage / resultCount).coerceAtLeast(18f)
                val offset = (track - thumb) * firstRow / (resultCount - rowsPerPage).coerceAtLeast(1)
                SettingsPaint.rounded(g, cx + cw + 7f, contentTop.toFloat(), 2f, track, 1f, 0x1F9E96AC)
                SettingsPaint.rounded(g, cx + cw + 7f, contentTop + offset, 2f, thumb, 1f, 0x8FBAA6EE.toInt())
            }
            if (!closing && alpha > .98f && page.value > .98) {
                navigation.forEach { it.renderModernTooltip(g, mouseX, mouseY) }
                body.forEach { it.renderModernTooltip(g, mouseX, mouseY) }
                if (pageId == "updates") updates.renderHint(g, mouseX, mouseY, width, height)
            }
        } finally {
            SettingsText.opacity = oldTextOpacity; SettingsPaint.opacity = oldPaintOpacity
            g.pose().popMatrix()
        }
    }

    override fun onClose() { closing = true }
    override fun isPauseScreen() = false
}
