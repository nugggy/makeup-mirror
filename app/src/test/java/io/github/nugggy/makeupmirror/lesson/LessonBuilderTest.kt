package io.github.nugggy.makeupmirror.lesson

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.styles.DefaultInstructions
import io.github.nugggy.makeupmirror.styles.LessonStep
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonBuilderTest {
    private val blush = LookLayer("blush", ProductType.BLUSH, Region.CHEEKS, "#E8A3A0")
    private val lip = LookLayer("lip", ProductType.LIPSTICK, Region.LIPS, "#B3121E")
    private val gloss = LookLayer("gloss", ProductType.LIP_GLOSS, Region.LIPS, "#FFFFFF", optional = true)
    private val look = Look(
        "l", "L", "", emptyList(), "img/l.jpg", listOf(blush, lip, gloss),
        listOf(LessonStep("blush"), LessonStep("lip", instruction = "Custom lip step.", tip = "Go slowly."), LessonStep("gloss")),
    )
    private val kitBlush = Product(1, ProductType.BLUSH, "Rose Petal", 0)
    private val kitLip = Product(2, ProductType.LIPSTICK, "Ruby", 0)

    @Test
    fun buildsCardsInStepOrderAndSkipsUnassignedOptionalSteps() {
        val match = MatchedLook(look, mapOf("blush" to kitBlush, "lip" to kitLip), emptySet(), 0.0, emptyList())
        val cards = LessonBuilder.build(match)
        assertEquals(listOf("blush", "lip"), cards.map { it.role })
        assertEquals(kitBlush, cards[0].product)
        assertEquals(Region.CHEEKS, cards[0].region)
        assertEquals(DefaultInstructions.forLayer(blush), cards[0].instruction)
        assertEquals("Custom lip step.", cards[1].instruction)
        assertEquals("Go slowly.", cards[1].tip)
    }
}
