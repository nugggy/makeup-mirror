package io.github.nugggy.makeupmirror.styles

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.ProductType

/** Standard how-to text for each product, used when a look does not give its own. */
object DefaultInstructions {
    private val byTypeAndRegion = mapOf(
        (ProductType.EYESHADOW to Region.LIDS) to
            "Pat eyeshadow across your eyelid, from your lash line up to the crease, with a flat brush or your fingertip.",
        (ProductType.EYESHADOW to Region.CREASE) to
            "With a fluffy brush, blend this shade into the fold of your eyelid using small side-to-side windscreen-wiper movements.",
        (ProductType.EYESHADOW to Region.LOWER_LASH_LINE) to
            "Smudge a little shadow along your lower lash line with a small brush. Start at the outer corner and fade it out before the middle.",
        (ProductType.EYELINER to Region.WING) to
            "Look straight ahead. Draw a short flick from your outer corner pointing towards the end of your brow, then join it back to your lash line and fill it in.",
    )

    private val byType = mapOf(
        ProductType.FOUNDATION to
            "Dot foundation on your forehead, cheeks, nose and chin. Blend it outwards with a damp sponge or brush until there are no edges, especially along your jaw and hairline.",
        ProductType.CONCEALER to
            "Dab concealer under each eye in a small upside-down triangle and gently tap it in with your ring finger. Cover any spots the same way.",
        ProductType.POWDER to
            "Press a light layer of powder onto your forehead, nose and chin so your base lasts all day.",
        ProductType.CONTOUR to
            "Find the hollow under your cheekbone by sucking in your cheeks. Sweep contour along it from your ear towards the middle of your cheek, then blend upwards until there are no lines.",
        ProductType.BRONZER to
            "With a big fluffy brush, sweep bronzer in a 3 shape on each side: along your forehead near the hairline, under your cheekbone and along your jaw.",
        ProductType.BLUSH to
            "Smile and tap blush onto the round apples of your cheeks, then blend it up and back towards your ears.",
        ProductType.HIGHLIGHTER to
            "Tap highlighter onto the tops of your cheekbones and a little down the bridge of your nose, where light naturally hits.",
        ProductType.EYESHADOW to
            "Pat eyeshadow onto your eyelid with a flat brush or your fingertip, then soften the edges.",
        ProductType.EYELINER to
            "Rest your elbow on a table to keep your hand steady. Draw a thin line along your upper lash line from the inner corner to the outer corner, as close to the lashes as you can.",
        ProductType.MASCARA to
            "Look down into the mirror, wiggle the wand at the root of your top lashes and sweep up to the tips. Add a second coat once the first is dry.",
        ProductType.BROW to
            "Brush your brows upwards. Fill any gaps with short, light strokes that follow the way your hairs grow. Keep the front softer than the tail.",
        ProductType.LIP_LINER to
            "Outline your lips with liner. Start with a small X at your Cupid's bow and work out to the corners, then soften the line so there's no hard edge.",
        ProductType.LIPSTICK to
            "Start in the centre of your top lip and work out to each corner, then fill in your bottom lip. Press your lips together and tidy the edges.",
        ProductType.LIP_GLOSS to
            "Dab gloss onto the centre of your lips and press them together to spread it evenly.",
    )

    fun forLayer(layer: LookLayer): String =
        byTypeAndRegion[layer.type to layer.region] ?: byType.getValue(layer.type)
}
