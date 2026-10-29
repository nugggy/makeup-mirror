package io.github.nugggy.makeupmirror.lesson

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.styles.DefaultInstructions

data class LessonCard(
    val role: String,
    val region: Region,
    val product: Product,
    val instruction: String,
    val tip: String?,
)

object LessonBuilder {
    /** One card per step whose role has a kit product assigned, in the look's step order. */
    fun build(match: MatchedLook): List<LessonCard> = match.look.steps.mapNotNull { step ->
        val layer = match.look.layer(step.role) ?: return@mapNotNull null
        val product = match.assignments[step.role] ?: return@mapNotNull null
        LessonCard(
            role = step.role,
            region = layer.region,
            product = product,
            instruction = step.instruction ?: DefaultInstructions.forLayer(layer),
            tip = step.tip,
        )
    }
}
