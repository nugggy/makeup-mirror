package io.github.nugggy.makeupmirror.face

import kotlinx.serialization.Serializable

/** Areas of the face a makeup layer can be applied to. Shapes live in [FaceRegions]. */
@Serializable
enum class Region {
    FACE,
    UNDER_EYE,
    CONTOUR,
    BRONZE,
    CHEEKS,
    HIGHLIGHT,
    LIDS,
    CREASE,
    LOWER_LASH_LINE,
    UPPER_LINER,
    WING,
    LASHES,
    BROWS,
    LIP_LINE,
    LIPS,
    LIP_GLOSS,
}
