package dev.skyglint.ui.settings

import dev.skyglint.fabric.GitHubUpdates
import dev.skyglint.ui.presentation.UpdateNoticePresentation
import dev.skyglint.ui.text.SettingsText
import dev.skyglint.updates.github.UpdateImportance
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import kotlin.math.roundToInt

/** Update content inside the settings workspace. The host owns navigation, clipping and widget registration. */
class UpdatesPage(private val previewState: GitHubUpdates.View? = null) {
    private val view get() = previewState ?: GitHubUpdates.view
    private var buttons: List<SettingsButton> = emptyList()
    private var readyCaption = false
    private var checkButton: SettingsButton? = null
    private var downloadButton: SettingsButton? = null
    private var automaticButton: SettingsButton? = null
    private var releaseButton: SettingsButton? = null
    private var hintBounds: Rect? = null
    private var hintSince = 0L
    private var hintKey: String? = null
    private var hintText = ""
    private var hintTitle = ""

    /** Returned widgets must be registered by the host Screen, which retains vanilla focus and narration. */
    fun controls(x: Int, y: Int, w: Int, h: Int): List<SettingsButton> {
        hintBounds = null; hintSince = 0; hintKey = null
        val layout = layout(x, y, w, h)
        readyCaption = view.phase == GitHubUpdates.Phase.READY
        checkButton = button(layout.check, "Check now", "Check the latest compatible stable release. This does not download a JAR.") {
            if (previewState == null) GitHubUpdates.check()
        }
        downloadButton = button(layout.download, if (readyCaption) "Restart required" else "Download update",
            "Download and verify this release. It activates only after a full Minecraft restart.") {
            if (previewState == null) GitHubUpdates.download()
        }
            .apply { kind = SettingsButton.Kind.PRIMARY }
        automaticButton = SettingsButton(layout.automatic.x, layout.automatic.y, layout.automatic.w, layout.automatic.h,
            "Auto-check", toggled = { view.automaticChecks },
            hint = "Check once on the first SkyBlock entry of each client launch. Important releases are announced once per launch. Downloads always require your click.") {
            if (previewState == null) GitHubUpdates.setAutomaticChecks(!view.automaticChecks)
        }.apply { kind = SettingsButton.Kind.ROW }
        releaseButton = button(layout.release, "Release details",
            "Open the public Skyglint release notes on GitHub in your browser.") {
            if (previewState == null) Util.getPlatform().openUri(GitHubUpdates.releasesUri)
        }
        buttons = listOfNotNull(checkButton, downloadButton, automaticButton, releaseButton)
        refresh()
        return buttons
    }

    /** True means the host should rebuild widgets because the download action became a restart label. */
    fun tick(): Boolean {
        refresh()
        return readyCaption != (view.phase == GitHubUpdates.Phase.READY)
    }

    private fun refresh() {
        val real = previewState == null
        val current = view
        val busy = real && GitHubUpdates.busy
        checkButton?.active = real && !busy && current.phase != GitHubUpdates.Phase.READY
        downloadButton?.active = real && GitHubUpdates.canDownload
        automaticButton?.active = real && !busy
        releaseButton?.active = real
        buttons.forEach(SettingsButton::refreshMessage)
    }

    /** Paints only the supplied content rectangle. There is deliberately no second page header or shell. */
    fun render(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        if (w <= 0 || h <= 0) { hintBounds = null; hintSince = 0; return }
        val geometry = layout(x, y, w, h)
        val current = view
        updateHint(geometry.hero, current, mouseX, mouseY)
        if (h < 180) {
            compactContent(g, geometry, current)
            return
        }
        val accent = accent(current)
        val badge = heading(current)
        val badgeWidth = (SettingsText.width(badge, 8.5f) + 18).coerceAtMost(geometry.hero.w)
        SettingsPaint.rounded(g, geometry.hero.x, geometry.hero.y, badgeWidth, 19, 9,
            (0x20 shl 24) or (accent and 0xFFFFFF))
        draw(g, badge, geometry.hero.x + 9, geometry.hero.y + 5, 8.5f, accent, (badgeWidth - 18).coerceAtLeast(1))
        val titleY = geometry.hero.y + 31
        val title = current.availableVersion?.let { "Skyglint $it" } ?: when (current.phase) {
            GitHubUpdates.Phase.UP_TO_DATE -> "You're up to date."
            GitHubUpdates.Phase.CHECKING -> "Looking for a new release…"
            GitHubUpdates.Phase.FAILED -> "Ready to try again."
            else -> "Keep Skyglint up to date."
        }
        draw(g, title, geometry.hero.x, titleY, 18f, SettingsPaint.INK, geometry.hero.w)
        var messageY = titleY + 29
        if (!geometry.wide) {
            draw(g, "Installed ${current.currentVersion} · Minecraft 26.2 · Fabric", geometry.hero.x, messageY,
                8f, SettingsPaint.MUTED, geometry.hero.w)
            messageY += 17
        }
        val message = current.message.ifBlank { fallback(current.phase) }
        val messageBottom = geometry.hero.y + geometry.hero.h - if (previewState != null && !geometry.wide) 16 else 0
        wrapped(message, geometry.hero.w, 9.5f).forEach { line ->
            if (messageY + 12 <= messageBottom) {
                SettingsText.draw(g, line, geometry.hero.x, messageY, SettingsPaint.MUTED)
                messageY += 14
            }
        }
        if (current.phase in setOf(GitHubUpdates.Phase.CHECKING, GitHubUpdates.Phase.DOWNLOADING)) {
            val trackY = geometry.download.y - 14
            SettingsPaint.rounded(g, geometry.hero.x, trackY, geometry.hero.w, 2, 1, 0xFF35414D.toInt())
            // The service publishes phases, not byte counts. Never turn elapsed time into a fake percentage.
            val segment = (geometry.hero.w / 4).coerceAtLeast(12)
            val fraction = ((System.nanoTime() / 1_000_000L) % 1_800L) / 1_800.0
            val offset = ((geometry.hero.w - segment) * fraction).roundToInt()
            SettingsPaint.rounded(g, geometry.hero.x + offset, trackY, segment, 2, 1, accent)
        }
        if (geometry.wide) {
            val side = geometry.side
            g.fill(side.x - 14, side.y, side.x - 13, y + h, SettingsPaint.color(0x253F4B58))
            draw(g, "INSTALLED", side.x, side.y + 3, 8f, SettingsPaint.MUTED, side.w)
            draw(g, "Skyglint ${current.currentVersion}", side.x, side.y + 22, 12f, SettingsPaint.INK, side.w)
            draw(g, "Minecraft Java 26.2", side.x, side.y + 45, 9f, SettingsPaint.MUTED, side.w)
            draw(g, "Fabric · JVM 25", side.x, side.y + 62, 9f, SettingsPaint.MUTED, side.w)
            draw(g, "UPDATE PREFERENCES", side.x, side.y + 95, 8f, SettingsPaint.MUTED, side.w)
            draw(g, "Manual download · full restart to apply", geometry.hero.x, geometry.download.y + geometry.download.h + 12,
                8f, SettingsPaint.MUTED, geometry.hero.w)
        }
        if (previewState != null) {
            val previewY = if (geometry.wide) geometry.side.y + geometry.side.h - 10 else geometry.hero.y + geometry.hero.h - 8
            draw(g, "PREVIEW · no network actions", if (geometry.wide) geometry.side.x else geometry.hero.x,
                previewY, 7.5f, SettingsPaint.MUTED, if (geometry.wide) geometry.side.w else geometry.hero.w)
        }
    }

    /** Small GUI viewports keep both action rows and the release identity inside the host clip. */
    private fun compactContent(g: GuiGraphicsExtractor, geometry: Layout, current: GitHubUpdates.View) {
        val hero = geometry.hero
        val accent = accent(current)
        val badge = heading(current)
        val badgeWidth = (SettingsText.width(badge, 8f) + 16).coerceAtMost(hero.w)
        SettingsPaint.rounded(g, hero.x, hero.y, badgeWidth, 16, 8, (0x20 shl 24) or (accent and 0xFFFFFF))
        draw(g, badge, hero.x + 8, hero.y + 4, 8f, accent, (badgeWidth - 16).coerceAtLeast(1))
        val title = current.availableVersion?.let { "Skyglint $it" } ?: "Skyglint ${current.currentVersion}"
        if (hero.h >= 39) draw(g, title, hero.x, hero.y + 24, 14f, SettingsPaint.INK, hero.w)
        if (hero.h >= 54) {
            val summary = when (current.phase) {
                GitHubUpdates.Phase.READY -> "Full Minecraft restart required"
                GitHubUpdates.Phase.DOWNLOADING -> "Verifying the update · you can keep playing"
                GitHubUpdates.Phase.CHECKING -> "Checking for a compatible stable release"
                GitHubUpdates.Phase.FAILED, GitHubUpdates.Phase.UNAVAILABLE -> current.message.ifBlank { fallback(current.phase) }
                else -> "Installed ${current.currentVersion} · manual download"
            }
            draw(g, summary, hero.x, hero.y + 45, 8f, SettingsPaint.MUTED, hero.w)
        }
    }

    private fun updateHint(bounds: Rect, current: GitHubUpdates.View, mouseX: Int, mouseY: Int) {
        hintBounds = bounds
        val key = "${current.phase}:${current.availableVersion}:${current.message}:${current.importance}"
        if (!bounds.contains(mouseX, mouseY)) { hintSince = 0; hintKey = null; return }
        if (hintKey != key || hintSince == 0L) { hintKey = key; hintSince = System.nanoTime() }
        hintTitle = heading(current)
        hintText = listOfNotNull(if (previewState != null) "PREVIEW · no network actions" else null,
            current.availableVersion?.let { "Version $it" },
            current.message.ifBlank { fallback(current.phase) },
            "Installed ${current.currentVersion}. Downloads require your action. A full Minecraft restart activates the update.")
            .joinToString(" · ")
    }

    /** The host calls this after disabling the content scissor and restoring page paint/text opacity. */
    fun renderHint(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
        if (screenW <= 0 || screenH <= 0 || hintSince == 0L || hintBounds?.contains(mouseX, mouseY) != true) return
        if (System.nanoTime() - hintSince < 600_000_000L) return
        SettingsTooltip.draw(g, hintTitle, hintText, mouseX, mouseY)
    }

    private fun button(rect: Rect, caption: String, hint: String, action: () -> Unit) =
        SettingsButton(rect.x, rect.y, rect.w, rect.h, caption, hint = hint, action = action)

    private fun heading(current: GitHubUpdates.View): String = when (current.phase) {
        GitHubUpdates.Phase.AVAILABLE -> UpdateNoticePresentation.heading(current.importance)
        GitHubUpdates.Phase.DOWNLOADING -> "Downloading and verifying"
        GitHubUpdates.Phase.READY -> "Ready for next launch"
        GitHubUpdates.Phase.CHECKING -> "Checking GitHub"
        GitHubUpdates.Phase.UP_TO_DATE -> "Up to date"
        GitHubUpdates.Phase.UNAVAILABLE -> "Manual installation"
        GitHubUpdates.Phase.FAILED -> "Could not complete update"
        GitHubUpdates.Phase.IDLE -> "Stable releases"
    }

    private fun accent(current: GitHubUpdates.View): Int = when {
        current.phase == GitHubUpdates.Phase.READY || current.phase == GitHubUpdates.Phase.UP_TO_DATE -> 0xFFB2CFBA.toInt()
        current.phase == GitHubUpdates.Phase.FAILED -> 0xFFE4A29B.toInt()
        current.phase in setOf(GitHubUpdates.Phase.AVAILABLE, GitHubUpdates.Phase.DOWNLOADING) &&
            current.importance == UpdateImportance.CRITICAL -> 0xFFE4A29B.toInt()
        current.phase in setOf(GitHubUpdates.Phase.AVAILABLE, GitHubUpdates.Phase.DOWNLOADING) &&
            current.importance == UpdateImportance.IMPORTANT -> 0xFFE0BF82.toInt()
        else -> SettingsPaint.ACCENT
    }

    private fun fallback(phase: GitHubUpdates.Phase): String = when (phase) {
        GitHubUpdates.Phase.AVAILABLE -> "A compatible stable release is available. Download when you choose; apply it after a full Minecraft restart."
        GitHubUpdates.Phase.DOWNLOADING -> "Downloading and verifying the release. You can leave this page and keep playing."
        GitHubUpdates.Phase.READY -> "The update is verified and ready. Restart Minecraft completely to load the new version."
        GitHubUpdates.Phase.FAILED -> "The update could not finish. Your current version is unchanged. Retry or open the release details."
        GitHubUpdates.Phase.UNAVAILABLE -> "Use the public GitHub release for this installation. No update has been downloaded automatically."
        GitHubUpdates.Phase.UP_TO_DATE -> "Your installed version is current. Check again later for another stable release."
        else -> "Check for a compatible release here or when you next enter SkyBlock. Downloading always requires your action."
    }

    private fun draw(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, size: Float, color: Int, budget: Int) {
        SettingsText.drawSized(g, fit(text, budget, size), x.toFloat(), y.toFloat(), size, color)
    }

    private fun fit(text: String, maximumWidth: Int, size: Float): String {
        if (maximumWidth <= 0) return ""
        if (SettingsText.width(text, size) <= maximumWidth) return text
        val points = text.codePoints().toArray()
        var end = points.size
        while (end > 0 && SettingsText.width(String(points, 0, end) + "…", size) > maximumWidth) end--
        return if (end == 0 && SettingsText.width("…", size) > maximumWidth) "" else String(points, 0, end) + "…"
    }

    private fun wrapped(text: String, maximumWidth: Int, size: Float): List<String> {
        if (maximumWidth <= 0) return emptyList()
        val result = mutableListOf<String>()
        var line = ""
        for (word in text.replace('\n', ' ').replace('\r', ' ').split(' ').filter(String::isNotBlank)) {
            if (line.isNotEmpty()) {
                if (SettingsText.width("$line $word", size) <= maximumWidth) { line += " $word"; continue }
                result += line; line = ""
            }
            word.codePoints().forEach { point ->
                val character = String(Character.toChars(point))
                if (line.isNotEmpty() && SettingsText.width(line + character, size) > maximumWidth) { result += line; line = "" }
                line += character
            }
        }
        if (line.isNotEmpty()) result += line
        return result
    }

    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(mx: Int, my: Int) = mx >= x && my >= y && mx < x + w && my < y + h
    }
    private data class Layout(val wide: Boolean, val hero: Rect, val side: Rect, val check: Rect,
                              val download: Rect, val automatic: Rect, val release: Rect)

    private fun layout(x: Int, y: Int, width: Int, height: Int): Layout {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val wide = w >= 500 && h >= 250
        if (wide) {
            val sideW = 180
            val leftW = w - sideW - 30
            val sideX = x + w - sideW
            return Layout(true, Rect(x, y + 4, leftW, h - 89), Rect(sideX, y, sideW, h),
                Rect(sideX, y + 159, sideW, 30), Rect(x, y + h - 54, minOf(220, leftW), 30),
                Rect(sideX, y + 112, sideW, 35), Rect(sideX, y + 199, sideW, 30))
        }
        if (h < 180) {
            val gap = 8
            val half = ((w - gap) / 2).coerceAtLeast(1)
            val rowHeight = 22
            return Layout(false, Rect(x, y, w, (h - 57).coerceAtLeast(1)), Rect(x, y, 0, 0),
                Rect(x, y + h - 49, half, rowHeight), Rect(x + half + gap, y + h - 49, (w - half - gap).coerceAtLeast(1), rowHeight),
                Rect(x, y + h - rowHeight, half, rowHeight), Rect(x + half + gap, y + h - rowHeight, (w - half - gap).coerceAtLeast(1), rowHeight))
        }
        val gap = 8
        val half = (w - gap) / 2
        return Layout(false, Rect(x, y, w, (h - 80).coerceAtLeast(1)), Rect(x, y, 0, 0),
            Rect(x, y + h - 62, half, 26), Rect(x + half + gap, y + h - 62, w - half - gap, 26),
            Rect(x, y + h - 28, half, 28), Rect(x + half + gap, y + h - 28, w - half - gap, 28))
    }
}
