package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.styles.Look

/** A look paired with the kit products that will be used for each of its layer roles. */
data class MatchedLook(
    val look: Look,
    val assignments: Map<String, Product>,
    val missing: Set<ProductType>,
    val score: Double,
    val reasons: List<String>,
    val suitsYou: Boolean = false,
) {
    val available: Boolean get() = missing.isEmpty()
}
