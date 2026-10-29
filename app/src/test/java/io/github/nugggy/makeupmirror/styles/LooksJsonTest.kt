package io.github.nugggy.makeupmirror.styles

import io.github.nugggy.makeupmirror.colour.ColourMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LooksJsonTest {
    private val looks = LookLibrary.parse(File("src/main/assets/styles/looks.json").readText())

    @Test
    fun libraryHasAtLeastEighteenUniqueLooks() {
        assertTrue(looks.size >= 18)
        assertEquals(looks.size, looks.map { it.id }.toSet().size)
    }

    @Test
    fun everyLayerHasExactlyOneStepAndEveryStepHasALayer() {
        looks.forEach { look ->
            val roles = look.layers.map { it.role }
            assertEquals("${look.id}: duplicate layer roles", roles.size, roles.toSet().size)
            look.steps.forEach { assertNotNull("${look.id}: step ${it.role} has no layer", look.layer(it.role)) }
            assertEquals("${look.id}: steps must match layers", roles.toSet(), look.steps.map { it.role }.toSet())
            assertEquals("${look.id}: duplicate steps", look.steps.size, look.steps.map { it.role }.toSet().size)
        }
    }

    @Test
    fun coloursAndOpacitiesAreValid() {
        looks.flatMap { it.layers }.forEach { layer ->
            ColourMath.parseHex(layer.colour)
            assertTrue("${layer.role} opacity ${layer.opacity}", layer.opacity in 0.05f..1f)
        }
    }

    @Test
    fun everyLookNeedsAtLeastOneProduct() {
        looks.forEach { assertTrue(it.id, it.requiredTypes.isNotEmpty()) }
    }

    @Test
    fun everyStepHasUsefulInstructions() {
        looks.forEach { look ->
            look.steps.forEach { step ->
                val text = step.instruction ?: DefaultInstructions.forLayer(look.layer(step.role)!!)
                assertTrue("${look.id}/${step.role}", text.length > 30)
            }
        }
    }
}
