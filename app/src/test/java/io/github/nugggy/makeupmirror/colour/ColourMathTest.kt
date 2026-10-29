package io.github.nugggy.makeupmirror.colour

import io.github.nugggy.makeupmirror.colour.ColourMath.Lab
import org.junit.Assert.assertEquals
import org.junit.Test

class ColourMathTest {
    @Test
    fun whiteHasFullLightnessAndNoChroma() {
        val lab = ColourMath.toLab(0xFFFFFFFF.toInt())
        assertEquals(100.0, lab.l, 0.01)
        assertEquals(0.0, lab.a, 0.01)
        assertEquals(0.0, lab.b, 0.01)
    }

    @Test
    fun pureRedMatchesReferenceLab() {
        val lab = ColourMath.toLab(0xFFFF0000.toInt())
        assertEquals(53.24, lab.l, 0.05)
        assertEquals(80.09, lab.a, 0.05)
        assertEquals(67.20, lab.b, 0.05)
    }

    @Test
    fun deltaE2000MatchesSharmaReferenceData() {
        assertEquals(2.0425, ColourMath.deltaE2000(Lab(50.0, 2.6772, -79.7751), Lab(50.0, 0.0, -82.7485)), 1e-4)
        assertEquals(1.0000, ColourMath.deltaE2000(Lab(50.0, 2.5, 0.0), Lab(50.0, 3.1736, 0.5854)), 1e-4)
        assertEquals(27.1492, ColourMath.deltaE2000(Lab(50.0, 2.5, 0.0), Lab(73.0, 25.0, -18.0)), 1e-4)
    }

    @Test
    fun identicalColoursHaveZeroDistance() {
        val c = ColourMath.parseHex("#B3121E")
        assertEquals(0.0, ColourMath.deltaE(c, c), 1e-9)
    }

    @Test
    fun hexRoundTrips() {
        assertEquals("#B3121E", ColourMath.toHex(ColourMath.parseHex("#b3121e")))
        assertEquals(0xFF, ColourMath.red(ColourMath.parseHex("#FF0000")))
    }
}
