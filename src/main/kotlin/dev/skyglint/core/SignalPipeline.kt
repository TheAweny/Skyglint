package dev.skyglint.core

import java.util.Collections

/** Immutable input values created at the Minecraft boundary, never live game objects. */
sealed interface NormalizedSignal {
    data class ActionBar(val text: String) : NormalizedSignal
    /** A newly received identical packet confirms presence, without replaying XP/delta detectors. */
    data class ActionBarPresence(val text: String) : NormalizedSignal
    data class Chat(val text: String) : NormalizedSignal
    data class ContextSnapshot(val location: String?, val mode: String?) : NormalizedSignal
    data class HeldItemSnapshot(val item: MenuItemSnapshot?) : NormalizedSignal
    /** Own slot-8 server bait preview. Missing item is unknown, never an inferred empty sack. */
    data class FishingBaitSnapshot(val item: MenuItemSnapshot?, val holdingRod: Boolean) : NormalizedSignal
    /** Player inventory only, copied at the adapter; absence from a full snapshot is authoritative. */
    class InventorySnapshot(items: List<MenuItemSnapshot>) : NormalizedSignal {
        val items: List<MenuItemSnapshot> = Collections.unmodifiableList(items.toList())
    }
    class BossSnapshot(bosses: List<BossBarState>) : NormalizedSignal {
        val bosses: List<BossBarState> = Collections.unmodifiableList(bosses.toList())
    }
    class ElectionSnapshot(val year: Int, candidates: List<ElectionVoteCandidate>, val ageMillis: Long = 0) : NormalizedSignal {
        val candidates: List<ElectionVoteCandidate> = Collections.unmodifiableList(candidates.toList())
    }
    data class ElectionClosed(val ageMillis: Long = 0) : NormalizedSignal
    data class ElectionSourceAvailability(val publicApiAllowed: Boolean) : NormalizedSignal
    class SidebarSnapshot(lines: List<String>) : NormalizedSignal {
        val lines: List<String> = Collections.unmodifiableList(lines.toList())
    }

    /** Copy caller-owned lists so a subsequent tab update cannot mutate an in-flight observation. */
    class TabSnapshot(lines: List<String>) : NormalizedSignal {
        val lines: List<String> = Collections.unmodifiableList(lines.toList())
    }

    /** Inventory adapter supplies copied menu values, including lore; never live ItemStacks. */
    class MenuSnapshot(val title: String, items: List<MenuItemSnapshot>) : NormalizedSignal {
        val items: List<MenuItemSnapshot> = Collections.unmodifiableList(items.toList())
    }
}

class MenuItemSnapshot(val slot: Int, val name: String, lore: List<String>,
                       val skyBlockId: String? = null, val drillFuel: Long? = null) {
    val lore: List<String> = Collections.unmodifiableList(lore.toList())
}

/** Add a detector by registering a module. Return no events for signals the module does not own. */
interface SignalModule {
    val id: String
    fun parse(signal: NormalizedSignal): List<CoreEvent>
    fun onSessionStarted(sessionId: String) {}
    fun onSessionEnded() {}
}

data class ModuleFailure(val moduleId: String, val signalType: String, val cause: Exception)

/**
 * Ordered, client-thread-only registry. A broken detector cannot stop another detector.
 * The diagnostic callback runs on that same thread and must not throw or perform blocking I/O.
 * JVM fatal Errors are deliberately not caught.
 */
class ModuleRegistry(var onFailure: (ModuleFailure) -> Unit = {}) {
    private val registered = linkedMapOf<String, SignalModule>()
    private var activeSession: String? = null
    val ids: List<String> get() = registered.keys.toList()

    fun register(module: SignalModule): ModuleRegistry = apply {
        require(module.id.isNotBlank()) { "A signal module needs a stable nonblank id" }
        require(module.id !in registered) { "Signal module already registered: ${module.id}" }
        registered[module.id] = module
        activeSession?.let { sessionId -> invoke(module, "SessionStarted") { module.onSessionStarted(sessionId) } }
    }

    fun sessionStarted(sessionId: String) {
        require(sessionId.isNotBlank())
        if (activeSession == sessionId) return
        if (activeSession != null) sessionEnded()
        activeSession = sessionId
        registered.values.toList().forEach { module -> invoke(module, "SessionStarted") { module.onSessionStarted(sessionId) } }
    }

    fun sessionEnded() {
        if (activeSession == null) return
        activeSession = null
        registered.values.toList().forEach { module -> invoke(module, "SessionEnded") { module.onSessionEnded() } }
    }

    fun process(signal: NormalizedSignal): List<CoreEvent> = processSelected(signal, null)

    /** Revalidate selected live context modules without replaying ordinary detectors or notices. */
    fun processSelected(signal: NormalizedSignal, ids: Set<String>?): List<CoreEvent> = buildList {
        // Registration from a callback becomes visible to the next signal, not halfway through this one.
        registered.values.toList().filter { ids == null || it.id in ids }.forEach { module ->
            try {
                addAll(module.parse(signal))
            } catch (failure: Exception) {
                onFailure(ModuleFailure(module.id, signal.javaClass.simpleName, failure))
            }
        }
    }

    private inline fun invoke(module: SignalModule, signalType: String, callback: () -> Unit) {
        try { callback() } catch (failure: Exception) {
            onFailure(ModuleFailure(module.id, signalType, failure))
        }
    }

    companion object {
        fun defaults(onFailure: (ModuleFailure) -> Unit = {}): ModuleRegistry = ModuleRegistry(onFailure)
            .register(SkillSignalModule)
            .register(ProgressionSignalModule)
            .register(PetSignalModule)
            .register(dev.skyglint.features.PetLevelUpModule())
            .register(dev.skyglint.features.SkillLevelUpModule())
    }
}

/** Each module owns recognition, while the reducer owns display policy and session state. */
object SkillSignalModule : SignalModule {
    override val id = "skyglint.skills"
    override fun parse(signal: NormalizedSignal): List<CoreEvent> = when (signal) {
        is NormalizedSignal.ActionBar -> SkyBlockParser.actionBar(signal.text).filterIsInstance<CoreEvent.SkillGain>()
        is NormalizedSignal.TabSnapshot -> SkyBlockParser.tabLines(signal.lines).filterIsInstance<CoreEvent.SkillObserved>()
        else -> emptyList()
    }
}

object ProgressionSignalModule : SignalModule {
    override val id = "skyglint.skyblock_progression"
    override fun parse(signal: NormalizedSignal): List<CoreEvent> = when (signal) {
        is NormalizedSignal.ActionBar -> SkyBlockParser.actionBar(signal.text).filterIsInstance<CoreEvent.SkyBlockGain>()
        is NormalizedSignal.TabSnapshot -> SkyBlockParser.tabLines(signal.lines).filterIsInstance<CoreEvent.SkyBlockObserved>()
        is NormalizedSignal.Chat -> SkyBlockParser.chat(signal.text)
        else -> emptyList()
    }
}

object PetSignalModule : SignalModule {
    override val id = "skyglint.pets"
    override fun parse(signal: NormalizedSignal): List<CoreEvent> = when (signal) {
        is NormalizedSignal.TabSnapshot -> SkyBlockParser.tabLines(signal.lines).filterIsInstance<CoreEvent.PetObserved>()
        else -> emptyList()
    }
}
