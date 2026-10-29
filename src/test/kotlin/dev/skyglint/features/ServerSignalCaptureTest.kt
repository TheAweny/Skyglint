package dev.skyglint.features

import kotlin.test.*
import org.junit.jupiter.api.Test

class ServerSignalCaptureTest {
    @Test fun `unknown server formats remain debuggable but player chat is rejected`() {
        assertTrue(ServerSignalCapture.relevant("§6RARE DROP! ??? (new suffix)"))
        assertTrue(ServerSignalCapture.relevant("\uE025 GREAT CATCH! Unknown new reward"))
        assertTrue(ServerSignalCapture.relevant("OBJECTIVE COMPLETE!"))
        assertTrue(ServerSignalCapture.relevant("You recovered a Summoning Eye!"))
        assertFalse(ServerSignalCapture.relevant("[MVP+] Player: RARE DROP! Summoning Eye"))
        assertFalse(ServerSignalCapture.relevant("From Player: hello"))
    }

    @Test fun `public Defense result fields remain diagnosable without broad player chat capture`() {
        for (line in listOf("WAVE 3 - YOU WON!", "Snowball Accuracy: N/A", "White Gift x34",
            "Collect your Gifts at the Gift piles!", "Snowballs Hit: 0\nSnowballs Thrown: 0"))
            assertTrue(ServerSignalCapture.relevant(line), line)
        assertFalse(ServerSignalCapture.relevant("From Player: White Gift x34"))
        assertFalse(ServerSignalCapture.relevant("[MVP+] Player: WAVE 3 - YOU WON!"))
        assertFalse(ServerSignalCapture.relevant("x".repeat(12_001)))
    }
}
