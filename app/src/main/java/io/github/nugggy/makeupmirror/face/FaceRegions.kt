package io.github.nugggy.makeupmirror.face

/** Region shapes in terms of landmark indices. Sizes are fractions of the face width. */
sealed interface RegionShape {
    data class Polygon(val outline: List<Int>, val holes: List<List<Int>> = emptyList()) : RegionShape
    data class Blob(val centre: Int, val towards: Int, val radius: Float, val stretch: Float = 1f) : RegionShape
    data class Stroke(val path: List<Int>, val width: Float) : RegionShape
    data class Wing(val outerCorner: Int, val lidPoint: Int, val browTail: Int, val length: Float) : RegionShape
}

object FaceRegions {
    val FACE_OVAL = listOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
    )
    val LIPS_OUTER = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185)
    val LIPS_INNER = listOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191)
    val RIGHT_EYE = listOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7)
    val LEFT_EYE = listOf(263, 466, 388, 387, 386, 385, 384, 398, 362, 382, 381, 380, 374, 373, 390, 249)
    val RIGHT_UPPER_LID = listOf(33, 246, 161, 160, 159, 158, 157, 173, 133)
    val LEFT_UPPER_LID = listOf(263, 466, 388, 387, 386, 385, 384, 398, 362)
    val RIGHT_LOWER_LID = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133)
    val LEFT_LOWER_LID = listOf(263, 249, 390, 373, 374, 380, 381, 382, 362)
    val RIGHT_CREASE = listOf(113, 225, 224, 223, 222, 221, 189)
    val LEFT_CREASE = listOf(342, 445, 444, 443, 442, 441, 413)
    val RIGHT_BROW = listOf(46, 53, 52, 65, 55, 107, 66, 105, 63, 70)
    val LEFT_BROW = listOf(276, 283, 282, 295, 285, 336, 296, 334, 293, 300)
    private val RIGHT_UNDER_EYE = RIGHT_LOWER_LID.reversed() + listOf(111, 117, 118, 119, 120, 121, 128)
    private val LEFT_UNDER_EYE = LEFT_LOWER_LID.reversed() + listOf(340, 346, 347, 348, 349, 350, 357)

    fun shapes(region: Region): List<RegionShape> = when (region) {
        Region.FACE -> listOf(RegionShape.Polygon(FACE_OVAL, listOf(RIGHT_EYE, LEFT_EYE, LIPS_OUTER)))
        Region.UNDER_EYE -> listOf(RegionShape.Polygon(RIGHT_UNDER_EYE), RegionShape.Polygon(LEFT_UNDER_EYE))
        Region.CONTOUR -> listOf(
            RegionShape.Blob(147, 234, radius = 0.055f, stretch = 2.4f),
            RegionShape.Blob(376, 454, radius = 0.055f, stretch = 2.4f),
        )
        Region.BRONZE -> listOf(
            RegionShape.Blob(54, 103, radius = 0.06f, stretch = 1.5f),
            RegionShape.Blob(284, 332, radius = 0.06f, stretch = 1.5f),
            RegionShape.Blob(147, 234, radius = 0.06f, stretch = 2.2f),
            RegionShape.Blob(376, 454, radius = 0.06f, stretch = 2.2f),
            RegionShape.Blob(172, 136, radius = 0.05f, stretch = 2f),
            RegionShape.Blob(397, 365, radius = 0.05f, stretch = 2f),
        )
        Region.CHEEKS -> listOf(
            RegionShape.Blob(50, 34, radius = 0.085f, stretch = 1.35f),
            RegionShape.Blob(280, 264, radius = 0.085f, stretch = 1.35f),
        )
        Region.HIGHLIGHT -> listOf(
            RegionShape.Blob(117, 34, radius = 0.045f, stretch = 2f),
            RegionShape.Blob(346, 264, radius = 0.045f, stretch = 2f),
            RegionShape.Stroke(listOf(168, 6, 197, 195), width = 0.018f),
        )
        Region.LIDS -> listOf(
            RegionShape.Polygon(RIGHT_UPPER_LID + RIGHT_CREASE.reversed()),
            RegionShape.Polygon(LEFT_UPPER_LID + LEFT_CREASE.reversed()),
        )
        Region.CREASE -> listOf(
            RegionShape.Stroke(RIGHT_CREASE, width = 0.035f),
            RegionShape.Stroke(LEFT_CREASE, width = 0.035f),
        )
        Region.LOWER_LASH_LINE -> listOf(
            RegionShape.Stroke(RIGHT_LOWER_LID.take(6), width = 0.012f),
            RegionShape.Stroke(LEFT_LOWER_LID.take(6), width = 0.012f),
        )
        Region.UPPER_LINER -> listOf(
            RegionShape.Stroke(RIGHT_UPPER_LID.reversed(), width = 0.009f),
            RegionShape.Stroke(LEFT_UPPER_LID.reversed(), width = 0.009f),
        )
        Region.WING -> listOf(
            RegionShape.Wing(33, 246, 46, length = 0.07f),
            RegionShape.Wing(263, 466, 276, length = 0.07f),
        )
        Region.LASHES -> listOf(
            RegionShape.Stroke(RIGHT_UPPER_LID, width = 0.016f),
            RegionShape.Stroke(LEFT_UPPER_LID, width = 0.016f),
        )
        Region.BROWS -> listOf(RegionShape.Polygon(RIGHT_BROW), RegionShape.Polygon(LEFT_BROW))
        Region.LIP_LINE -> listOf(RegionShape.Stroke(LIPS_OUTER + LIPS_OUTER.first(), width = 0.008f))
        Region.LIPS -> listOf(RegionShape.Polygon(LIPS_OUTER, listOf(LIPS_INNER)))
        Region.LIP_GLOSS -> listOf(RegionShape.Blob(16, 291, radius = 0.03f, stretch = 2.2f))
    }
}
