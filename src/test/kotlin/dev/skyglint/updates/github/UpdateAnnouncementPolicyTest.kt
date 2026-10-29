package dev.skyglint.updates.github

import kotlin.test.*

class UpdateAnnouncementPolicyTest {
    private fun release(importance: UpdateImportance, version: String = "0.3.0") =
        UpdateRelease(version, "v$version", "skyglint-$version.jar", "0".repeat(64), 1, "0.19.3", importance)

    @Test fun `every importance announces once for the launch across reconnects`() {
        for (importance in UpdateImportance.entries) {
            val policy = UpdateAnnouncementPolicy()
            val offer = release(importance)
            assertNotNull(policy.offer(offer))
            repeat(4) { assertNull(policy.offer(offer)) } // repeated entry/island checks
            policy.onDisconnected()
            assertNull(policy.offer(offer))
            assertNotNull(policy.offer(release(importance, "0.3.1")))
            assertNotNull(UpdateAnnouncementPolicy().offer(offer)) // full restart
        }
    }

    @Test fun `critical versions never repeat on reconnect or island transfer`() {
        val policy = UpdateAnnouncementPolicy()
        val offer = release(UpdateImportance.CRITICAL)
        assertNotNull(policy.offer(offer))
        repeat(4) { assertNull(policy.offer(offer)) }
        policy.onDisconnected()
        policy.onDisconnected() // duplicate disconnect callback is harmless
        assertNull(policy.offer(offer))
        assertNull(policy.offer(offer))
        assertNotNull(policy.offer(release(UpdateImportance.CRITICAL, "0.3.1")))
    }

    @Test fun `publisher importance changes cannot duplicate a version inside one connection`() {
        val policy = UpdateAnnouncementPolicy()
        assertNotNull(policy.offer(release(UpdateImportance.NORMAL)))
        assertNull(policy.offer(release(UpdateImportance.CRITICAL)))
        policy.onDisconnected()
        assertNull(policy.offer(release(UpdateImportance.CRITICAL)))
        assertNull(policy.offer(release(UpdateImportance.IMPORTANT)))
    }

    @Test fun `emphasis grows with importance while every presentation is transient and below gameplay notices`() {
        val plans = UpdateImportance.entries.map { importance ->
            assertNotNull(UpdateAnnouncementPolicy().offer(release(importance))).also {
                assertEquals("0.3.0", it.version)
                assertEquals(importance, it.importance)
                assertTrue(it.durationMillis in 250..15_000)
                assertTrue(it.priority in 0..<50) // reward50, critical gameplay90+, SB level100
            }
        }
        assertTrue(plans.zipWithNext().all { (a, b) -> a.durationMillis < b.durationMillis && a.priority < b.priority })
    }
}
