package io.github.nugggy.makeupmirror.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkSmootherTest {
    @Test
    fun steadyInputStaysPut() {
        val f = OneEuroFilter()
        repeat(30) { assertEquals(5.0, f.filter(5.0, it / 30.0), 1e-9) }
    }

    @Test
    fun jumpIsFollowedGradually() {
        val f = OneEuroFilter()
        f.filter(0.0, 0.0)
        val first = f.filter(10.0, 1 / 30.0)
        assertTrue(first > 0.0 && first < 10.0)
        var last = first
        for (i in 2..60) last = f.filter(10.0, i / 30.0)
        assertEquals(10.0, last, 0.05)
    }

    @Test
    fun gapResetsFilters() {
        val s = LandmarkSmoother()
        s.smooth(listOf(Pt(0f, 0f)), 0.0)
        s.smooth(listOf(Pt(0f, 0f)), 0.033)
        // Face lost for a second, then found somewhere else: jump straight there.
        val after = s.smooth(listOf(Pt(100f, 50f)), 1.2).single()
        assertEquals(Pt(100f, 50f), after)
    }
}
