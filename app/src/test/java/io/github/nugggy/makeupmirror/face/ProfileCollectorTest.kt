package io.github.nugggy.makeupmirror.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileCollectorTest {
    private val almond = FaceProfile(FaceShape.OVAL, EyeShape.ALMOND, LipFullness.MEDIUM, Undertone.WARM, SkinDepth.LIGHT)

    @Test
    fun returnsMajorityAfterEnoughSamples() {
        val collector = ProfileCollector(needed = 5)
        repeat(3) { assertNull(collector.add(almond)) }
        assertNull(collector.add(almond.copy(eyeShape = EyeShape.HOODED, undertone = null)))
        val result = collector.add(almond.copy(eyeShape = EyeShape.HOODED, undertone = null))
        assertEquals(almond, result)
    }
}
