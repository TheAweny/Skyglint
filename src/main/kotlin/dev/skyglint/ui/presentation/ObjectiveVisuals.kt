package dev.skyglint.ui.presentation

import dev.skyglint.core.SkyBlockParser

/** Presentation only. The domain retains the original boss title, UUID and packet progress. */
data class ObjectiveVisual(val title: String, val icon: String, val accent: Int)

object ObjectiveVisuals {
    private const val PREFIX = "Objective: "
    private const val AMBER = 0xFFE5D797.toInt()
    private const val CYAN = 0xFFA8DFFF.toInt()
    private const val BLUE = 0xFF9DBDFF.toInt()
    private val talk = Regex("Talk to [\\p{L}\\p{N}][\\p{L}\\p{N} .’'\\-]{0,79}")
    private val travel = Regex("Travel to [\\p{L}\\p{N}][\\p{L}\\p{N} .’'\\-]{0,79}")

    fun resolve(serverTitle: String): ObjectiveVisual? {
        if (serverTitle.length > 1_024 || serverTitle.any(Char::isISOControl)) return null
        val normalized = SkyBlockParser.clean(serverTitle)
        if (!normalized.startsWith(PREFIX)) return null
        val task = normalized.removePrefix(PREFIX).trim()
        if (task.isEmpty() || task.length > 256) return null
        return when {
            task == "Craft a workbench" -> ObjectiveVisual(task, "minecraft:crafting_table", AMBER)
            talk.matches(task) -> ObjectiveVisual(task, "minecraft:villager_spawn_egg", CYAN)
            travel.matches(task) -> ObjectiveVisual(task, "minecraft:compass", BLUE)
            else -> ObjectiveVisual(task, "minecraft:paper", AMBER)
        }
    }
}

/** Hide only explicit own/private-island captions; other locations remain useful context. */
object IdleLocationPolicy {
    fun caption(location: String?): String? {
        if (location == null || location.length > 256 || location.any(Char::isISOControl)) return null
        val normalized = SkyBlockParser.clean(location).takeIf { it.isNotEmpty() } ?: return null
        if (normalized.equals("Your Island", true) || normalized.equals("Private Island", true)) return null
        return normalized
    }
}
