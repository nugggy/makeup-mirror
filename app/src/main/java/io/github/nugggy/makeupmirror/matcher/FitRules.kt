package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.face.EyeShape
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.face.FaceShape
import io.github.nugggy.makeupmirror.face.LipFullness
import io.github.nugggy.makeupmirror.face.SkinDepth
import io.github.nugggy.makeupmirror.face.Undertone
import io.github.nugggy.makeupmirror.styles.Look

/** Common makeup-artist rules of thumb, scored against a look's tags. */
internal object FitRules {
    fun score(look: Look, profile: FaceProfile?): Pair<Double, List<String>> {
        if (profile == null) return 0.0 to emptyList()
        val tags = look.tags.toSet()
        var score = 0.0
        val reasons = mutableListOf<String>()
        fun add(points: Double, reason: String? = null) {
            score += points
            if (reason != null && points > 0) reasons += reason
        }

        when (profile.undertone) {
            Undertone.WARM -> if ("warm" in tags) add(2.0, "Warm shades flatter your warm undertone") else if ("cool" in tags) add(-1.0)
            Undertone.COOL -> if ("cool" in tags) add(2.0, "Cool shades flatter your cool undertone") else if ("warm" in tags) add(-1.0)
            Undertone.NEUTRAL -> if ("warm" in tags || "cool" in tags) add(1.0, "Your neutral undertone suits warm and cool shades")
            null -> Unit
        }
        when (profile.eyeShape) {
            EyeShape.HOODED -> {
                if ("smoky" in tags) add(2.0, "Blended smoky shadow opens up hooded eyes")
                if ("cut-crease" in tags) add(-2.0)
            }
            EyeShape.MONOLID -> {
                if ("smoky" in tags) add(2.0, "A soft gradient of shadow suits your eye shape")
                if ("cut-crease" in tags) add(-1.0)
            }
            EyeShape.ALMOND -> if ("wing" in tags) add(2.0, "Almond eyes are perfect for a winged liner")
            EyeShape.ROUND -> if ("wing" in tags) add(2.0, "A longer wing lengthens round eyes")
            EyeShape.DOWNTURNED -> if ("wing" in tags) add(2.0, "A lifted wing balances downturned eyes")
            EyeShape.UPTURNED -> if ("smoky" in tags || "wing" in tags) add(1.0, "Your upturned eyes suit defined liner and shadow")
        }
        when (profile.faceShape) {
            FaceShape.ROUND -> if ("contour" in tags) add(1.0, "Soft contour adds definition to a round face")
            FaceShape.SQUARE -> if ("contour" in tags) add(1.0, "Soft contour softens a strong jaw")
            FaceShape.LONG -> if ("blush-forward" in tags) add(1.0, "Blush across the cheeks balances a longer face")
            FaceShape.HEART -> if ("bold-lip" in tags) add(1.0, "A bold lip balances a heart-shaped face")
            FaceShape.OVAL -> if ("highlight" in tags) add(0.5, "Your oval face shows off highlighter well")
        }
        when (profile.lips) {
            LipFullness.THIN -> if ("gloss" in tags) add(1.0, "Gloss makes lips look fuller")
            LipFullness.FULL -> if ("bold-lip" in tags) add(1.0, "Full lips carry a bold colour beautifully")
            LipFullness.MEDIUM -> Unit
        }
        when (profile.depth) {
            SkinDepth.TAN, SkinDepth.DEEP -> if ("highlight" in tags) add(1.0, "Highlighter glows beautifully on deeper skin")
            SkinDepth.FAIR -> if ("subtle" in tags) add(1.0, "Soft, sheer colour looks fresh on fair skin")
            else -> Unit
        }
        return score to reasons
    }
}
