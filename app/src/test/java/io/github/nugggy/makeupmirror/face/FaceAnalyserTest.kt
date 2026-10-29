package io.github.nugggy.makeupmirror.face

import io.github.nugggy.makeupmirror.colour.ColourMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceAnalyserTest {
    private fun analyse(face: List<Pt>, skin: Int? = null) = FaceAnalyser.analyse(face, skin)

    @Test
    fun averageFaceIsOvalAlmondMedium() {
        val p = analyse(FakeFace.build())
        assertEquals(FaceShape.OVAL, p.faceShape)
        assertEquals(EyeShape.ALMOND, p.eyeShape)
        assertEquals(LipFullness.MEDIUM, p.lips)
    }

    @Test
    fun faceShapes() {
        assertEquals(FaceShape.LONG, analyse(FakeFace.build(faceLength = 160f)).faceShape)
        assertEquals(FaceShape.ROUND, analyse(FakeFace.build(faceLength = 115f, jawWidth = 85f)).faceShape)
        assertEquals(FaceShape.SQUARE, analyse(FakeFace.build(faceLength = 125f, jawWidth = 95f)).faceShape)
        assertEquals(FaceShape.HEART, analyse(FakeFace.build(foreheadWidth = 95f, jawWidth = 70f)).faceShape)
    }

    @Test
    fun eyeShapes() {
        assertEquals(EyeShape.HOODED, analyse(FakeFace.build(browGap = 15f)).eyeShape)
        assertEquals(EyeShape.MONOLID, analyse(FakeFace.build(eyeHeight = 6f)).eyeShape)
        assertEquals(EyeShape.UPTURNED, analyse(FakeFace.build(tiltDeg = 10f)).eyeShape)
        assertEquals(EyeShape.DOWNTURNED, analyse(FakeFace.build(tiltDeg = -4f)).eyeShape)
        assertEquals(EyeShape.ROUND, analyse(FakeFace.build(eyeHeight = 12f)).eyeShape)
    }

    @Test
    fun headRollDoesNotChangeClassification() {
        for (roll in listOf(-15f, 15f)) {
            assertEquals(EyeShape.UPTURNED, analyse(FakeFace.build(tiltDeg = 10f, rollDeg = roll)).eyeShape)
            assertEquals(EyeShape.ALMOND, analyse(FakeFace.build(rollDeg = roll)).eyeShape)
            assertEquals(FaceShape.OVAL, analyse(FakeFace.build(rollDeg = roll)).faceShape)
        }
    }

    @Test
    fun lipFullness() {
        assertEquals(LipFullness.THIN, analyse(FakeFace.build(lipHeight = 12f)).lips)
        assertEquals(LipFullness.FULL, analyse(FakeFace.build(lipHeight = 20f)).lips)
    }

    @Test
    fun skinToneFromSampledColour() {
        val warm = analyse(FakeFace.build(), ColourMath.parseHex("#E0AC7E"))
        assertEquals(Undertone.WARM, warm.undertone)
        val cool = analyse(FakeFace.build(), ColourMath.parseHex("#E8B4B8"))
        assertEquals(Undertone.COOL, cool.undertone)
        assertEquals(SkinDepth.FAIR, analyse(FakeFace.build(), ColourMath.parseHex("#F5E1D3")).depth)
        assertEquals(SkinDepth.DEEP, analyse(FakeFace.build(), ColourMath.parseHex("#5A3825")).depth)
    }

    @Test
    fun noSkinSampleMeansNoToneResult() {
        val p = analyse(FakeFace.build())
        assertNull(p.undertone)
        assertNull(p.depth)
    }
}
