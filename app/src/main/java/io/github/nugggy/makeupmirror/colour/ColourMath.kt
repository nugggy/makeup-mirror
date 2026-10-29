package io.github.nugggy.makeupmirror.colour

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** sRGB (D65) to CIELAB conversion and CIEDE2000 colour difference. Colours are packed ARGB ints. */
object ColourMath {
    data class Lab(val l: Double, val a: Double, val b: Double)

    fun red(argb: Int) = (argb shr 16) and 0xFF
    fun green(argb: Int) = (argb shr 8) and 0xFF
    fun blue(argb: Int) = argb and 0xFF
    fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun parseHex(hex: String): Int {
        val digits = hex.removePrefix("#")
        require(digits.length == 6) { "Expected #RRGGBB but got $hex" }
        return (0xFF000000L or digits.toLong(16)).toInt()
    }

    fun toHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    fun toLab(argb: Int): Lab {
        fun linear(channel: Int): Double {
            val v = channel / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(red(argb))
        val g = linear(green(argb))
        val b = linear(blue(argb))
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun hueDegrees(lab: Lab): Double {
        val h = Math.toDegrees(atan2(lab.b, lab.a))
        return if (h < 0) h + 360 else h
    }

    fun deltaE(c1: Int, c2: Int): Double = deltaE2000(toLab(c1), toLab(c2))

    fun deltaE2000(c1: Lab, c2: Lab): Double {
        val avgL = (c1.l + c2.l) / 2
        val avgC = (hypot(c1.a, c1.b) + hypot(c2.a, c2.b)) / 2
        val g = 0.5 * (1 - sqrt(avgC.pow(7) / (avgC.pow(7) + 25.0.pow(7))))
        val a1p = c1.a * (1 + g)
        val a2p = c2.a * (1 + g)
        val c1p = hypot(a1p, c1.b)
        val c2p = hypot(a2p, c2.b)
        val avgCp = (c1p + c2p) / 2
        fun hp(b: Double, ap: Double): Double {
            if (b == 0.0 && ap == 0.0) return 0.0
            val h = Math.toDegrees(atan2(b, ap))
            return if (h < 0) h + 360 else h
        }
        val h1p = hp(c1.b, a1p)
        val h2p = hp(c2.b, a2p)
        val dLp = c2.l - c1.l
        val dCp = c2p - c1p
        var dhp = h2p - h1p
        if (c1p * c2p == 0.0) dhp = 0.0 else if (dhp > 180) dhp -= 360 else if (dhp < -180) dhp += 360
        val dHp = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2))
        val avgHp = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(Math.toRadians(avgHp - 30)) +
            0.24 * cos(Math.toRadians(2 * avgHp)) +
            0.32 * cos(Math.toRadians(3 * avgHp + 6)) -
            0.20 * cos(Math.toRadians(4 * avgHp - 63))
        val dTheta = 30 * exp(-((avgHp - 275) / 25).pow(2))
        val rc = 2 * sqrt(avgCp.pow(7) / (avgCp.pow(7) + 25.0.pow(7)))
        val sl = 1 + (0.015 * (avgL - 50).pow(2)) / sqrt(20 + (avgL - 50).pow(2))
        val sc = 1 + 0.045 * avgCp
        val sh = 1 + 0.015 * avgCp * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        return sqrt(
            (dLp / sl).pow(2) + (dCp / sc).pow(2) + (dHp / sh).pow(2) + rt * (dCp / sc) * (dHp / sh),
        )
    }
}
