package dev.skyglint.features.elections

import kotlin.test.*

class HubElectionRegionTest {
    @Test fun `verified hub anchors resolve their exact named subareas`() {
        assertEquals("Community Center", HubElectionRegion.resolve(13.0, 80.0, 13.0))
        assertEquals("Community Center", HubElectionRegion.resolve(9.0, 80.0, 12.0))
        assertEquals("Community Center", HubElectionRegion.resolve(0.0, 79.0, 6.0))
        assertEquals("Community Center", HubElectionRegion.resolve(-5.0, 80.0, 25.0))
        assertEquals("Community Center", HubElectionRegion.resolve(-15.0, 63.0, -50.0))
        assertEquals("Election Room", HubElectionRegion.resolve(0.0, 51.0, 45.0))
        assertEquals("Election Room", HubElectionRegion.resolve(0.0, 51.0, 47.0))
        assertEquals("Election Booth", HubElectionRegion.resolve(0.0, 50.0, 35.0))
    }
    @Test fun `neighbourhood coverage is bounded horizontally and vertically and never becomes hub wide`() {
        assertEquals("Community Center", HubElectionRegion.resolve(17.0, 83.0, 13.0))
        assertNull(HubElectionRegion.resolve(17.01, 80.0, 13.0))
        assertNull(HubElectionRegion.resolve(13.0, 83.01, 13.0))
        assertEquals("Election Room", HubElectionRegion.resolve(6.0, 51.0, 45.0))
        assertNull(HubElectionRegion.resolve(6.01, 51.0, 45.0))
        assertNull(HubElectionRegion.resolve(0.0, 80.0, 45.0))
        assertNull(HubElectionRegion.resolve(100.0, 80.0, 100.0))
        assertNull(HubElectionRegion.resolve(4.0, 71.0, -97.0))
    }
    @Test fun `invalid coordinates do not match a region`() {
        assertNull(HubElectionRegion.resolve(Double.NaN, 80.0, 13.0))
        assertNull(HubElectionRegion.resolve(13.0, Double.POSITIVE_INFINITY, 13.0))
        assertNull(HubElectionRegion.resolve(13.0, 80.0, Double.NEGATIVE_INFINITY))
    }
}
