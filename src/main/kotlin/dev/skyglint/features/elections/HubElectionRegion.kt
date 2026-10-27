package dev.skyglint.features.elections

/** Conservative local neighbourhoods around MIT-source hub graph anchors, not guessed whole-building bounds. */
object HubElectionRegion {
    private data class Anchor(val x: Double, val y: Double, val z: Double, val area: String, val radius: Double)
    private val anchors = listOf(
        Anchor(13.0, 80.0, 13.0, "Community Center", 4.0),
        Anchor(9.0, 80.0, 12.0, "Community Center", 4.0),
        Anchor(0.0, 79.0, 6.0, "Community Center", 4.0),
        Anchor(-5.0, 80.0, 25.0, "Community Center", 4.0),
        Anchor(-15.0, 63.0, -50.0, "Community Center", 4.0),
        Anchor(0.0, 51.0, 45.0, "Election Room", 6.0),
        Anchor(0.0, 51.0, 47.0, "Election Room", 6.0),
        Anchor(0.0, 50.0, 35.0, "Election Booth", 6.0),
    )

    /** Caller must first verify official mode=hub; equal coordinates on another island mean nothing. */
    fun resolve(x: Double, y: Double, z: Double): String? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        return anchors.asSequence().filter {
            val dx = x - it.x
            val dz = z - it.z
            kotlin.math.abs(y - it.y) <= 3.0 && dx * dx + dz * dz <= it.radius * it.radius
        }.minByOrNull { (x - it.x) * (x - it.x) + (z - it.z) * (z - it.z) + (y - it.y) * (y - it.y) }?.area
    }
}
