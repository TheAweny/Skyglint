package dev.skyglint.ui.presentation

import dev.skyglint.core.ModuleWidget
import dev.skyglint.core.WidgetMetric

/** Capacity is measured in presented blocks, not currencies/statistics inside the same block. */
object MetricGrouping {
    data class Block(val id: String, val metrics: List<Pair<ModuleWidget, WidgetMetric>>, val grouped: Boolean)
    fun select(metrics: List<Pair<ModuleWidget, WidgetMetric>>, capacity: Int = 7): List<Block> {
        require(capacity > 0)
        return metrics.sortedByDescending { it.second.priority }
            .groupBy { (widget, metric) -> metric.group?.takeIf(String::isNotBlank)?.let { "group:$it" }
                ?: "metric:${widget.id}:${metric.id}" }
            .map { (id, members) ->
                // Same currency in tab and sidebar is one observed value; higher source priority wins.
                Block(id, members.distinctBy { it.second.id }, id.startsWith("group:"))
            }.take(capacity)
    }
}
