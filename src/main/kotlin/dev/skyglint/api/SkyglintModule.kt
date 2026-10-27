package dev.skyglint.api

import dev.skyglint.core.ModuleRegistry

/** Optional Fabric entrypoint `skyglint:modules` for companion mods. Called once on client initialization. */
fun interface SkyglintModule {
    fun register(modules: ModuleRegistry)
}
