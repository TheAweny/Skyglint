package dev.skyglint.ui

import dev.skyglint.ui.presentation.IdleContext
import kotlin.test.*
import org.junit.jupiter.api.Test

class IdleContextTest {
    @Test fun `idle context contains only a known location`() {
        assertEquals("Your Island", IdleContext.caption("Your Island")?.title)
        assertNull(IdleContext.caption(null))
        assertNull(IdleContext.caption(" "))
    }
}
