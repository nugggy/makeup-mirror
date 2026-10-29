package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer

object StyleMatcher {
    /** How many CIEDE2000 units of average shade mismatch cost one point of fit score. */
    private const val COLOUR_PENALTY_DIVISOR = 25.0
    private const val BADGE_COUNT = 3

    fun match(kit: List<Product>, profile: FaceProfile?, looks: List<Look>): List<MatchedLook> {
        val kitTypes = kit.map { it.type }.toSet()
        val matched = looks.map { look ->
            val assignments = look.layers.mapNotNull { layer -> closest(kit, layer)?.let { layer.role to it } }.toMap()
            val mismatch = assignments.map { (role, product) ->
                ColourMath.deltaE(product.colour, ColourMath.parseHex(look.layer(role)!!.colour))
            }.average().takeUnless { it.isNaN() } ?: 0.0
            val (fit, reasons) = FitRules.score(look, profile)
            MatchedLook(
                look = look,
                assignments = assignments,
                missing = look.requiredTypes - kitTypes,
                score = fit - mismatch / COLOUR_PENALTY_DIVISOR,
                reasons = reasons,
            )
        }
        val sorted = matched.sortedWith(compareByDescending<MatchedLook> { it.available }.thenByDescending { it.score })
        val badged = sorted.filter { it.available && it.reasons.isNotEmpty() }.take(BADGE_COUNT).map { it.look.id }.toSet()
        return sorted.map { if (it.look.id in badged) it.copy(suitsYou = true) else it }
    }

    private fun closest(kit: List<Product>, layer: LookLayer): Product? {
        val target = ColourMath.parseHex(layer.colour)
        return kit.filter { it.type == layer.type }.minByOrNull { ColourMath.deltaE(it.colour, target) }
    }
}
