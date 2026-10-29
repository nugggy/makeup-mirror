package io.github.nugggy.makeupmirror.face

import kotlin.math.PI
import kotlin.math.abs

/** One Euro filter (Casiez et al. 2012): smooths jitter when still and keeps up when moving fast. */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.007,
    private val dCutoff: Double = 1.0,
) {
    private var previous: Double? = null
    private var previousDerivative = 0.0
    private var previousTime = 0.0

    fun filter(x: Double, tSeconds: Double): Double {
        val prev = previous
        if (prev == null) {
            previous = x
            previousTime = tSeconds
            return x
        }
        val dt = (tSeconds - previousTime).coerceAtLeast(1e-3)
        val derivative = lowPass((x - prev) / dt, previousDerivative, alpha(dt, dCutoff))
        val cutoff = minCutoff + beta * abs(derivative)
        val result = lowPass(x, prev, alpha(dt, cutoff))
        previous = result
        previousDerivative = derivative
        previousTime = tSeconds
        return result
    }

    private fun alpha(dt: Double, cutoff: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun lowPass(x: Double, previous: Double, alpha: Double) = alpha * x + (1 - alpha) * previous
}
