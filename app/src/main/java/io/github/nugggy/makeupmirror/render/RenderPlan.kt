package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Region

/**
 * How a layer mixes with the skin. TINT keeps the skin's light and shade but takes the product's
 * hue, and darker shades get an extra multiply pass.
 */
enum class Blend { NORMAL, MULTIPLY, SCREEN, TINT }

data class RenderLayer(
    val role: String,
    val region: Region,
    val colour: Int,
    val opacity: Float,
    val blend: Blend,
    /** Edge blur as a fraction of face width. */
    val softness: Float,
    val shimmer: Boolean,
)

data class RenderPlan(val layers: List<RenderLayer>) {
    /** Layers for a lesson: finished steps faded, the current step at full strength, later steps hidden. */
    fun forLessonStep(stepRoles: List<String>, stepIndex: Int, doneFade: Float = 0.6f): RenderPlan {
        val done = stepRoles.take(stepIndex).toSet()
        val current = stepRoles.getOrNull(stepIndex)
        return RenderPlan(
            layers.mapNotNull { layer ->
                when (layer.role) {
                    current -> layer
                    in done -> layer.copy(opacity = layer.opacity * doneFade)
                    else -> null
                }
            },
        )
    }

    companion object {
        val EMPTY = RenderPlan(emptyList())
    }
}
