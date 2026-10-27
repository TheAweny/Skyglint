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
}
