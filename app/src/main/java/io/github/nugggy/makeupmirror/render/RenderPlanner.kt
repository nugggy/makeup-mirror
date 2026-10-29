package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Finish
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.matcher.MatchedLook

object RenderPlanner {
    /** The order products go on, so later layers draw on top. */
    private val applicationOrder = listOf(
        ProductType.FOUNDATION, ProductType.CONCEALER, ProductType.POWDER, ProductType.CONTOUR,
        ProductType.BRONZER, ProductType.BLUSH, ProductType.HIGHLIGHTER, ProductType.EYESHADOW,
        ProductType.EYELINER, ProductType.MASCARA, ProductType.BROW, ProductType.LIP_LINER,
        ProductType.LIPSTICK, ProductType.LIP_GLOSS,
    )

    fun plan(match: MatchedLook, strength: Float = 1f): RenderPlan = RenderPlan(
        match.look.layers
            .sortedBy { applicationOrder.indexOf(it.type) }
            .mapNotNull { layer ->
                val product = match.assignments[layer.role] ?: return@mapNotNull null
                RenderLayer(
                    role = layer.role,
                    region = layer.region,
                    colour = product.colour,
                    opacity = (layer.opacity * strength).coerceIn(0f, 1f),
                    blend = blendFor(product.type),
                    softness = softnessFor(layer.region),
                    shimmer = product.finish == Finish.SHIMMER || product.finish == Finish.GLOSS,
                )
            },
    )

    internal fun blendFor(type: ProductType): Blend = when (type) {
        ProductType.FOUNDATION, ProductType.CONCEALER, ProductType.POWDER -> Blend.NORMAL
        ProductType.CONTOUR, ProductType.BRONZER, ProductType.EYELINER, ProductType.MASCARA, ProductType.BROW -> Blend.MULTIPLY
        ProductType.HIGHLIGHTER -> Blend.SCREEN
        ProductType.BLUSH, ProductType.EYESHADOW, ProductType.LIPSTICK, ProductType.LIP_LINER, ProductType.LIP_GLOSS -> Blend.TINT
    }

    internal fun softnessFor(region: Region): Float = when (region) {
        Region.FACE -> 0.04f
        Region.UNDER_EYE -> 0.025f
        Region.CONTOUR, Region.BRONZE, Region.CHEEKS -> 0f // drawn as soft gradients already
        Region.HIGHLIGHT -> 0.012f // blurs the nose stroke; cheek blobs are gradients
        Region.LIDS -> 0.012f
        Region.CREASE -> 0.02f
        Region.LOWER_LASH_LINE -> 0.006f
        Region.UPPER_LINER, Region.WING, Region.LASHES -> 0.0015f
        Region.BROWS -> 0.004f
        Region.LIP_LINE -> 0.002f
        Region.LIPS -> 0.003f
        Region.LIP_GLOSS -> 0.01f
    }
}
