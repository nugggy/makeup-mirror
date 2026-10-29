package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Pt
import org.junit.Assert.assertEquals
import org.junit.Test

class LandmarkMapperTest {
    // 480x640 portrait frame drawn to fill a 1080x1920 view: scale 3, image 1440 wide, cropped 180 each side.
    private val plain = LandmarkMapper(480, 640, 1080f, 1920f, mirror = false)
    private val mirrored = LandmarkMapper(480, 640, 1080f, 1920f, mirror = true)

    @Test
    fun fillScaleAndCropOffset() {
        assertEquals(3f, plain.scale, 1e-4f)
        assertEquals(-180f, plain.offsetX, 1e-3f)
        assertEquals(0f, plain.offsetY, 1e-3f)
    }

    @Test
    fun centreMapsToCentre() {
        assertEquals(Pt(540f, 960f), plain.map(0.5f, 0.5f))
        assertEquals(Pt(540f, 960f), mirrored.map(0.5f, 0.5f))
    }

    @Test
    fun mirrorFlipsHorizontally() {
        assertEquals(180f, plain.map(0.25f, 0.5f).x, 1e-3f)
        assertEquals(900f, mirrored.map(0.25f, 0.5f).x, 1e-3f)
    }
}
