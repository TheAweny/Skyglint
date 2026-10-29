package dev.skyglint.features

import dev.skyglint.core.ModuleRegistry

/** Composition boundary for production features; core owns only baseline protocol detectors. */
object BuiltInModules {
    /** Install once before companions. Returned IDs revalidate context without replaying XP/rewards. */
    fun registerFeatures(
        registry: ModuleRegistry,
        clock: () -> Long,
        eventSchedule: EventScheduleModule = EventScheduleModule(),
    ): Set<String> {
        registry.register(LevelingMenuModule())
            .register(SimpleActivityModule(clock))
            .register(RareDropSignalModule())
            .register(eventSchedule)
        val contextual = ContextModules.create(clock)
        contextual.forEach(registry::register)
        return contextual.mapTo(linkedSetOf()) { it.id }
    }
}
