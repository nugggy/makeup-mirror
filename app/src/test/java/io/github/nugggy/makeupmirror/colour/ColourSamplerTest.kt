package io.github.nugggy.makeupmirror.colour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourSamplerTest {
    private val lipstick = ColourMath.argb(180, 30, 50)

    @Test
    fun uniformPatchReturnsItsColour() {
        assertEquals(lipstick, ColourSampler.average(IntArray(50) { lipstick }))
    }

    @Test
    fun glareAndBackgroundAreIgnored() {
        val pixels = IntArray(100) { i ->
            when {
                i < 10 -> ColourMath.argb(255, 255, 255) // glare
                i < 18 -> ColourMath.argb(20, 20, 20) // shadow at the edge
                else -> ColourMath.argb(178 + i % 5, 28 + i % 5, 48 + i % 5)
            }
        }
        val result = ColourSampler.average(pixels)
        assertTrue(ColourMath.deltaE(result, lipstick) < 2.0)
    }

    @Test
    fun circleIsClippedAtImageEdges() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { it }
        val inside = ColourSampler.circle(pixels, width, height, cx = 0, cy = 0, radius = 2)
        // Quarter disc of radius 2 at the corner: (0,0),(1,0),(2,0),(0,1),(1,1),(0,2)
        assertEquals(setOf(0, 1, 2, 10, 11, 20), inside.toSet())
    }
}
