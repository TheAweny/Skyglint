package dev.skyglint.ui.motion

import kotlin.math.abs
import kotlin.math.exp

/** Exact critically damped solution. Retargeting keeps the current value and velocity. */
class CriticalSpring(initial: Double, private val frequency: Double = 20.0) {
    var value: Double = initial; private set
    var velocity: Double = 0.0; private set

    init { require(initial.isFinite() && frequency.isFinite() && frequency > 0) }

    fun step(target: Double, seconds: Double): Double {
        require(target.isFinite() && seconds.isFinite() && seconds >= 0)
        if (seconds == 0.0) return value
        val displacement = value - target
        val impulse = velocity + frequency * displacement
        val decay = exp(-frequency * seconds)
        value = target + (displacement + impulse * seconds) * decay
        velocity = (velocity - frequency * impulse * seconds) * decay
        if (abs(value - target) < .0001 && abs(velocity) < .001) snap(target)
        return value
    }

    fun snap(target: Double) { require(target.isFinite()); value = target; velocity = 0.0 }
}
