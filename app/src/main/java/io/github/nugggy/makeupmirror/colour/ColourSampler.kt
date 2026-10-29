package io.github.nugggy.makeupmirror.colour

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ColourSampler {
    /**
     * Averages pixels after discarding outliers (glare, shadow, background) that sit more than
     * [tolerance] away from the per-channel median on any channel.
     */
    fun average(pixels: IntArray, tolerance: Int = 40): Int {
        require(pixels.isNotEmpty()) { "No pixels to sample" }
        val mr = median(pixels, ColourMath::red)
        val mg = median(pixels, ColourMath::green)
        val mb = median(pixels, ColourMath::blue)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (p in pixels) {
            val pr = ColourMath.red(p)
            val pg = ColourMath.green(p)
            val pb = ColourMath.blue(p)
            if (abs(pr - mr) <= tolerance && abs(pg - mg) <= tolerance && abs(pb - mb) <= tolerance) {
                r += pr
                g += pg
                b += pb
                n++
            }
        }
        if (n == 0) return ColourMath.argb(mr, mg, mb)
        return ColourMath.argb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Pixels of a row-major [width] x [height] image that fall inside the given circle. */
    fun circle(pixels: IntArray, width: Int, height: Int, cx: Int, cy: Int, radius: Int): IntArray {
        val out = ArrayList<Int>()
        for (y in max(0, cy - radius)..min(height - 1, cy + radius)) {
            for (x in max(0, cx - radius)..min(width - 1, cx + radius)) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= radius * radius) out += pixels[y * width + x]
            }
        }
        return out.toIntArray()
    }

    private fun median(pixels: IntArray, channel: (Int) -> Int): Int {
        val values = IntArray(pixels.size) { channel(pixels[it]) }
        values.sort()
        return values[values.size / 2]
    }
}
