package dev.skyglint.smoke

import dev.skyglint.core.*
import dev.skyglint.fabric.SkyglintClient
import dev.skyglint.features.mining.MiningEventModule
import dev.skyglint.ui.SkyglintHud
import dev.skyglint.ui.effects.HudBackdrop
import dev.skyglint.ui.presentation.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import org.slf4j.LoggerFactory
import java.util.UUID

/** Isolated replay of sanitized Crystal Nucleus packet ordering, never live server state. */
class MiningClockSmoke {
    private var began = 0L
    private var clock = 0L
    private var phase = -1
    private lateinit var core: CoreController
    private val composer = HudComposition()
    private val eventId = UUID(0, 734)
    private var timerKey: String? = null
    private var captures = mutableSetOf<String>()
    private val events = listOf("BETTER TOGETHER" to "Better Together", "2X POWDER" to "Double Powder",
        "GONE WITH THE WIND" to "Gone with the Wind")

    fun tick(client: Minecraft) {
        clock = SkyglintClient.now()
        if (began == 0L) {
            began = clock
            SkyglintClient.setPreview(false)
            client.options.guiScale().set(2); client.resizeGui()
        }
        val elapsed = clock - began
        val nextPhase = (elapsed / 14_000).toInt()
        if (nextPhase >= events.size) {
            check(captures.size == 15 && HudBackdrop.captureCount() > 10 && !HudBackdrop.debugStatus().contains("disabled=true"))
            SkyglintHud.editorSnapshot = null
            LoggerFactory.getLogger("SkyglintMiningClockSmoke").info(
                "SKYGLINT_MINING_CLOCK_PASSED: actual native TIMER -> same-ID event-name ended -> fade; " +
                    "three passive types; Objective multiplex clock continuity; no raw zero boss fallback; {}", HudBackdrop.debugStatus())
            client.stop(); return
        }
        if (nextPhase != phase) {
            phase = nextPhase; timerKey = null
            core = CoreController().apply {
                modules.register(MiningEventModule { clock })
                enterSkyBlock("SYNTHETIC_MINING_CLOCK_$phase", clock)
            }
        }
        val age = elapsed % 14_000
        core.ingest(NormalizedSignal.ContextSnapshot("Crystal Nucleus", "crystal_hollows"), clock)
        val objective = age >= 5_000 && age % 1_000 < 150
        if (age < 11_200) {
            val remaining = ((10_000 - age).coerceAtLeast(0) + 999) / 1_000
            val title = if (objective) "Objective: Talk to the Goblin King" else
                "PASSIVE EVENT ${events[phase].first} RUNNING FOR 00:${remaining.toString().padStart(2, '0')}"
            core.updateBoss(eventId, title, .5f, now = clock)
            core.ingest(NormalizedSignal.BossSnapshot(listOf(BossBarState(eventId, title, .5f))), clock)
        } else {
            core.removeBoss(eventId)
            core.ingest(NormalizedSignal.BossSnapshot(emptyList()), clock)
        }
        val snapshot = core.snapshot(clock)
        val elements = composer.compose(snapshot, clock)
        check(elements.none { it.title.contains("PASSIVE EVENT") || it.title.contains("RUNNING FOR") })
        if (age in 4_500..9_900) {
            val timer = elements.single()
            check(timer.kind == HudElementKind.TIMER)
            val key = timer.visibleContentKey(0)
            check(timerKey == null || timerKey == key)
            timerKey = key
            check(snapshot.widgets.single().presentation!!.metrics.single().replacedBossIds.isEmpty() == objective)
        }
        if (age in 10_400..12_300) {
            val ending = elements.single()
            check(ending.kind == HudElementKind.MAIN && ending.title == "${events[phase].second} ended")
            check(ending.id == "metric:${MiningEventModule.WIDGET_ID}:event:${listOf("better_together", "double_powder", "gone_with_the_wind")[phase]}")
            check(ending.countdown == null && ending.value == null)
        }
        if (age > 12_800) check(elements.isEmpty())
        SkyglintHud.editorSnapshot = snapshot
        for ((at, label) in listOf(5_070L to "objective-gap", 5_350L to "timer", 10_850L to "ended", 11_600L to "ended-after-remove", 13_300L to "return")) {
            val name = "mining-clock-$phase-$label"
            if (age >= at && captures.add(name)) Screenshot.grab(client.gameDirectory, "$name.png",
                client.gameRenderer.mainRenderTarget(), 1) { }
        }
    }
}
