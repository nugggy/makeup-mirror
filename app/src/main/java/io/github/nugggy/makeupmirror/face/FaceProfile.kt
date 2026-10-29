package io.github.nugggy.makeupmirror.face

import kotlinx.serialization.Serializable

@Serializable enum class FaceShape { OVAL, ROUND, SQUARE, HEART, LONG }

@Serializable enum class EyeShape { ALMOND, ROUND, HOODED, MONOLID, UPTURNED, DOWNTURNED }

@Serializable enum class LipFullness { THIN, MEDIUM, FULL }

@Serializable enum class Undertone { WARM, COOL, NEUTRAL }

@Serializable enum class SkinDepth { FAIR, LIGHT, MEDIUM, TAN, DEEP }

@Serializable
data class FaceProfile(
    val faceShape: FaceShape,
    val eyeShape: EyeShape,
    val lips: LipFullness,
    val undertone: Undertone?,
    val depth: SkinDepth?,
)

fun Enum<*>.label(): String = name.lowercase().replace('_', ' ')
