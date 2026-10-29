package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Finish
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.styles.LessonStep
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlannerTest {
    private val lipLayer = LookLayer("lip", ProductType.LIPSTICK, Region.LIPS, "#B3121E", 0.6f)
    private val baseLayer = LookLayer("base", ProductType.FOUNDATION, Region.FACE, "#E0B898", 0.3f)
    private val glowLayer = LookLayer("glow", ProductType.HIGHLIGHTER, Region.HIGHLIGHT, "#FFF1D6", 0.4f, optional = true)
    private val look = Look("l", "L", "", emptyList(), "img/l.jpg", listOf(lipLayer, baseLayer, glowLayer), listOf(LessonStep("base"), LessonStep("lip"), LessonStep("glow")))
    private val lipstick = Product(1, ProductType.LIPSTICK, "Ruby", ColourMath.parseHex("#AA1122"), Finish.SATIN)
    private val foundation = Product(2, ProductType.FOUNDATION, "Base", ColourMath.parseHex("#E0B898"))
    private val match = MatchedLook(look, mapOf("lip" to lipstick, "base" to foundation), emptySet(), 0.0, emptyList())

    @Test
    fun layersFollowApplicationOrderAndSkipUnassignedRoles() {
        val plan = RenderPlanner.plan(match)
        assertEquals(listOf("base", "lip"), plan.layers.map { it.role })
    }

    @Test
    fun layerUsesTheKitColourNotTheTargetColour() {
        assertEquals(lipstick.colour, RenderPlanner.plan(match).layers.last().colour)
    }

    @Test
    fun blendModesSuitEachProduct() {
        assertEquals(Blend.TINT, RenderPlanner.blendFor(ProductType.LIPSTICK))
        assertEquals(Blend.NORMAL, RenderPlanner.blendFor(ProductType.FOUNDATION))
        assertEquals(Blend.MULTIPLY, RenderPlanner.blendFor(ProductType.EYELINER))
        assertEquals(Blend.SCREEN, RenderPlanner.blendFor(ProductType.HIGHLIGHTER))
    }

    @Test
    fun highlightStrokeHasSoftEdges() {
        assertTrue(RenderPlanner.softnessFor(Region.HIGHLIGHT) > 0f)
    }

    @Test
    fun strengthScalesOpacityWithinBounds() {
        assertEquals(0.3f, RenderPlanner.plan(match, strength = 0.5f).layers.last().opacity, 1e-4f)
        assertEquals(1f, RenderPlanner.plan(match, strength = 3f).layers.last().opacity, 1e-4f)
    }

    @Test
    fun shimmerAndGlossFinishesShine() {
        val glossy = match.copy(assignments = mapOf("lip" to lipstick.copy(finish = Finish.GLOSS)))
        assertTrue(RenderPlanner.plan(glossy).layers.single().shimmer)
    }

    @Test
    fun lessonStepShowsDoneFadedCurrentFullAndHidesFuture() {
        val plan = RenderPlanner.plan(match)
        val step0 = plan.forLessonStep(listOf("base", "lip"), 0)
        assertEquals(listOf("base"), step0.layers.map { it.role })
        val step1 = plan.forLessonStep(listOf("base", "lip"), 1)
        assertEquals(listOf("base", "lip"), step1.layers.map { it.role })
        assertEquals(0.3f * 0.6f, step1.layers.first().opacity, 1e-4f)
        assertEquals(0.6f, step1.layers.last().opacity, 1e-4f)
    }
}
