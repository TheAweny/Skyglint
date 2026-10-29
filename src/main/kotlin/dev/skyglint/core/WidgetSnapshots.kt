package dev.skyglint.core

import java.util.Collections
import java.util.LinkedHashSet

/** Own nested contributor collections before storing a widget in session state. */
internal fun ModuleWidget.ownedSnapshot(): ModuleWidget = copy(
    replacedBossIds = Collections.unmodifiableSet(LinkedHashSet(replacedBossIds)),
    presentation = presentation?.let { value ->
        value.copy(metrics = Collections.unmodifiableList(value.metrics.map { metric ->
            metric.copy(replacedBossIds = Collections.unmodifiableSet(LinkedHashSet(metric.replacedBossIds)))
        }))
    },
)
