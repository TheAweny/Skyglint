package dev.skyglint.ui.presentation

data class IdleCaption(val title: String)

object IdleContext {
    fun caption(location: String?): IdleCaption? = location?.takeIf { it.isNotBlank() }?.let(::IdleCaption)
}
