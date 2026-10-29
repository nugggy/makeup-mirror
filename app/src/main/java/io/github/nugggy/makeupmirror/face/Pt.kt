package io.github.nugggy.makeupmirror.face

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(s: Float) = Pt(x * s, y * s)

    fun dist(o: Pt): Float = hypot(x - o.x, y - o.y)

    fun normalised(): Pt {
        val length = hypot(x, y)
        return if (length == 0f) this else Pt(x / length, y / length)
    }

    fun rotate(rad: Double, about: Pt): Pt {
        val c = cos(rad)
        val s = sin(rad)
        val dx = x - about.x
        val dy = y - about.y
        return Pt((about.x + dx * c - dy * s).toFloat(), (about.y + dx * s + dy * c).toFloat())
    }
}
