package dev.skyglint.core

import java.util.Collections
import java.util.Locale

enum class RewardMode { FILTERED, ALL }
enum class RewardOverride { SHOW, HIDE }

/** Exact-name presentation choices, not a valuation engine. Unknown items are never guessed cheap. */
class RewardRulePolicy(
    val mode: RewardMode = RewardMode.ALL,
    overrides: Map<String, RewardOverride> = emptyMap(),
) {
    val overrides: Map<String, RewardOverride> = Collections.unmodifiableMap(
        overrides.entries.asSequence().mapNotNull { (name, action) ->
            normalizeItemName(name).takeIf { it.isNotEmpty() && it.length <= 120 }?.let { it to action }
        }.take(128).toMap(),
    )

    fun evaluate(observation: RewardObservation): RewardDecision {
        val name = normalizeItemName(observation.itemName)
        if (name.isEmpty() || name.length > 120 || observation.itemName.any(Char::isISOControl)) {
            return RewardDecision(false, "invalid_item_name")
        }
        when (overrides[name]) {
            RewardOverride.SHOW -> return RewardDecision(true, "item_override_show")
            RewardOverride.HIDE -> return RewardDecision(false, "item_override_hide")
            null -> Unit
        }
        if (mode == RewardMode.ALL) return RewardDecision(true, "all_rewards_mode")
        if (observation.sourceId == "fishing_treasure_coins") return RewardDecision(false, "frequent_coin_reward")
        if (name in FREQUENT_ITEMS) return RewardDecision(false, "frequent_item_default")
        return RewardDecision(true, "unfiltered_item")
    }

    companion object {
        private val formatting = Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE)
        private val whitespace = Regex("\\s+")
        private val FREQUENT_ITEMS = setOf(
            "ender pearl", "enchanted ender pearl", "eye of ender", "rotten flesh", "enchanted rotten flesh",
            "enchanted end stone", "crystal fragment",
        )

        fun normalizeItemName(name: String): String = formatting.replace(name, "")
            .replace('\u00a0', ' ').trim().replace(whitespace, " ").lowercase(Locale.ROOT)

        fun fromConfig(mode: String, overrides: Map<String, String>): RewardRulePolicy = RewardRulePolicy(
            if (mode.equals("filtered", true)) RewardMode.FILTERED else RewardMode.ALL,
            overrides.mapNotNull { (name, value) ->
                val action = when {
                    value.equals("show", true) -> RewardOverride.SHOW
                    value.equals("hide", true) -> RewardOverride.HIDE
                    else -> null
                }
                action?.let { name to it }
            }.toMap(),
        )
    }
}
