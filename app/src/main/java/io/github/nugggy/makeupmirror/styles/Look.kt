package io.github.nugggy.makeupmirror.styles

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.ProductType
import kotlinx.serialization.Serializable

@Serializable
data class Look(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val image: String,
    val layers: List<LookLayer>,
    val steps: List<LessonStep>,
) {
    val requiredTypes: Set<ProductType>
        get() = layers.filterNot { it.optional }.map { it.type }.toSet()

    fun layer(role: String): LookLayer? = layers.firstOrNull { it.role == role }
}

/** One coat of one product type on one region. [colour] is the target shade as #RRGGBB. */
@Serializable
data class LookLayer(
    val role: String,
    val type: ProductType,
    val region: Region,
    val colour: String,
    val opacity: Float = 0.5f,
    val optional: Boolean = false,
)

/** A lesson step for the layer with the same [role]. A null [instruction] uses [DefaultInstructions]. */
@Serializable
data class LessonStep(
    val role: String,
    val instruction: String? = null,
    val tip: String? = null,
)
