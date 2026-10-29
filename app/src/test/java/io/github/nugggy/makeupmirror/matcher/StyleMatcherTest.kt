package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.EyeShape
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.face.FaceShape
import io.github.nugggy.makeupmirror.face.LipFullness
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.face.SkinDepth
import io.github.nugggy.makeupmirror.face.Undertone
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.styles.LessonStep
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleMatcherTest {
    private fun layer(role: String, type: ProductType, colour: String, optional: Boolean = false) =
        LookLayer(role, type, Region.LIPS, colour, 0.5f, optional)

    private fun look(id: String, tags: List<String>, vararg layers: LookLayer) =
        Look(id, id, "", tags, "img/$id.jpg", layers.toList(), layers.map { LessonStep(it.role) })

    private fun product(id: Long, type: ProductType, colour: String) =
        Product(id, type, "$type $id", ColourMath.parseHex(colour))

    private val warmProfile = FaceProfile(FaceShape.OVAL, EyeShape.ALMOND, LipFullness.MEDIUM, Undertone.WARM, SkinDepth.LIGHT)

    @Test
    fun lookIsUnavailableWhenARequiredProductIsMissing() {
        val red = look("red", emptyList(), layer("liner", ProductType.EYELINER, "#111111"), layer("lip", ProductType.LIPSTICK, "#B3121E"))
        val result = StyleMatcher.match(listOf(product(1, ProductType.LIPSTICK, "#B3121E")), null, listOf(red)).single()
        assertFalse(result.available)
        assertEquals(setOf(ProductType.EYELINER), result.missing)
    }

    @Test
    fun missingOptionalProductKeepsLookAvailable() {
        val l = look("l", emptyList(), layer("lip", ProductType.LIPSTICK, "#B3121E"), layer("gloss", ProductType.LIP_GLOSS, "#FFFFFF", optional = true))
        val result = StyleMatcher.match(listOf(product(1, ProductType.LIPSTICK, "#B3121E")), null, listOf(l)).single()
        assertTrue(result.available)
        assertEquals(setOf("lip"), result.assignments.keys)
    }

    @Test
    fun closestShadeIsAssigned() {
        val kit = listOf(product(1, ProductType.LIPSTICK, "#C2A49A"), product(2, ProductType.LIPSTICK, "#B01020"))
        val red = look("red", emptyList(), layer("lip", ProductType.LIPSTICK, "#B3121E"))
        assertEquals(2L, StyleMatcher.match(kit, null, listOf(red)).single().assignments.getValue("lip").id)
    }

    @Test
    fun singleProductCoversTwoRolesOfSameType() {
        val smoky = look("smoky", emptyList(), layer("lid", ProductType.EYESHADOW, "#3A3A40"), layer("crease", ProductType.EYESHADOW, "#6E6670"))
        val result = StyleMatcher.match(listOf(product(7, ProductType.EYESHADOW, "#8A6A5A")), null, listOf(smoky)).single()
        assertTrue(result.available)
        assertEquals(7L, result.assignments.getValue("lid").id)
        assertEquals(7L, result.assignments.getValue("crease").id)
    }

    @Test
    fun availableLooksComeFirst() {
        val needsLiner = look("a", emptyList(), layer("liner", ProductType.EYELINER, "#111111"))
        val needsLipstick = look("b", emptyList(), layer("lip", ProductType.LIPSTICK, "#B3121E"))
        val result = StyleMatcher.match(listOf(product(1, ProductType.LIPSTICK, "#B3121E")), null, listOf(needsLiner, needsLipstick))
        assertEquals(listOf("b", "a"), result.map { it.look.id })
    }

    @Test
    fun warmProfileRanksWarmLookFirstWithAReason() {
        val kit = listOf(product(1, ProductType.BRONZER, "#A86B45"))
        val warm = look("warm", listOf("warm"), layer("b", ProductType.BRONZER, "#A86B45"))
        val cool = look("cool", listOf("cool"), layer("b", ProductType.BRONZER, "#A86B45"))
        val result = StyleMatcher.match(kit, warmProfile, listOf(cool, warm))
        assertEquals("warm", result.first().look.id)
        assertTrue(result.first().suitsYou)
        assertTrue(result.first().reasons.first().contains("warm undertone"))
        assertFalse(result.last().suitsYou)
    }

    @Test
    fun noProfileMeansNoBadges() {
        val kit = listOf(product(1, ProductType.BRONZER, "#A86B45"))
        val warm = look("warm", listOf("warm", "wing"), layer("b", ProductType.BRONZER, "#A86B45"))
        val result = StyleMatcher.match(kit, null, listOf(warm)).single()
        assertTrue(result.reasons.isEmpty())
        assertFalse(result.suitsYou)
    }
}
