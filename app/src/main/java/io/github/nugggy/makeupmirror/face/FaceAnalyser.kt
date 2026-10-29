package io.github.nugggy.makeupmirror.face

import io.github.nugggy.makeupmirror.colour.ColourMath
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Classifies face, eye and lip shape from landmarks, and skin tone from a sampled colour.
 * Landmarks must be in pixel units of the un-mirrored, upright camera image.
 */
object FaceAnalyser {
    private const val LONG_RATIO = 1.5f
    private const val HEART_FOREHEAD_TO_JAW = 1.3f
    private const val SQUARE_JAW_TO_CHEEK = 0.9f
    private const val SQUARE_MAX_RATIO = 1.35f
    private const val ROUND_MAX_RATIO = 1.25f
    private const val MONOLID_ASPECT = 0.24f
    private const val HOODED_BROW_GAP = 0.62f
    private const val UPTURNED_DEG = 7f
    private const val DOWNTURNED_DEG = -1f
    private const val ROUND_EYE_ASPECT = 0.36f
    private const val THIN_LIPS = 0.30f
    private const val FULL_LIPS = 0.42f

    fun analyse(landmarks: List<Pt>, skinColour: Int?): FaceProfile {
        val pts = level(landmarks)
        return FaceProfile(
            faceShape = faceShape(pts),
            eyeShape = eyeShape(pts),
            lips = lips(pts),
            undertone = skinColour?.let(::undertone),
            depth = skinColour?.let(::depth),
        )
    }

    /** Rotates the face so the eyes are level, removing head roll. */
    private fun level(pts: List<Pt>): List<Pt> {
        val right = mid(pts[33], pts[133])
        val left = mid(pts[263], pts[362])
        val angle = atan2((left.y - right.y).toDouble(), (left.x - right.x).toDouble())
        return pts.map { it.rotate(-angle, right) }
    }

    private fun faceShape(p: List<Pt>): FaceShape {
        val length = p[10].dist(p[152])
        val cheek = p[234].dist(p[454])
        val jaw = p[172].dist(p[397])
        val forehead = p[54].dist(p[284])
        val ratio = length / cheek
        return when {
            ratio >= LONG_RATIO -> FaceShape.LONG
            forehead / jaw >= HEART_FOREHEAD_TO_JAW -> FaceShape.HEART
            jaw / cheek >= SQUARE_JAW_TO_CHEEK && ratio < SQUARE_MAX_RATIO -> FaceShape.SQUARE
            ratio < ROUND_MAX_RATIO -> FaceShape.ROUND
            else -> FaceShape.OVAL
        }
    }

    private fun eyeShape(p: List<Pt>): EyeShape {
        val width = (p[33].dist(p[133]) + p[263].dist(p[362])) / 2
        val height = (p[159].dist(p[145]) + p[386].dist(p[374])) / 2
        val aspect = height / width
        val browGap = (p[159].dist(p[105]) + p[386].dist(p[334])) / 2 / width
        val tilt = (tiltDeg(inner = p[133], outer = p[33]) + tiltDeg(inner = p[362], outer = p[263])) / 2
        return when {
            aspect < MONOLID_ASPECT -> EyeShape.MONOLID
            browGap < HOODED_BROW_GAP -> EyeShape.HOODED
            tilt > UPTURNED_DEG -> EyeShape.UPTURNED
            tilt < DOWNTURNED_DEG -> EyeShape.DOWNTURNED
            aspect >= ROUND_EYE_ASPECT -> EyeShape.ROUND
            else -> EyeShape.ALMOND
        }
    }

    /** Positive when the outer corner sits higher than the inner corner (image y grows downwards). */
    private fun tiltDeg(inner: Pt, outer: Pt): Float =
        Math.toDegrees(atan2((inner.y - outer.y).toDouble(), abs(outer.x - inner.x).toDouble())).toFloat()

    private fun lips(p: List<Pt>): LipFullness {
        val ratio = p[0].dist(p[17]) / p[61].dist(p[291])
        return when {
            ratio < THIN_LIPS -> LipFullness.THIN
            ratio > FULL_LIPS -> LipFullness.FULL
            else -> LipFullness.MEDIUM
        }
    }

    private fun undertone(colour: Int): Undertone {
        val hue = ColourMath.hueDegrees(ColourMath.toLab(colour))
        return when {
            hue > 60 -> Undertone.WARM
            hue < 50 -> Undertone.COOL
            else -> Undertone.NEUTRAL
        }
    }

    private fun depth(colour: Int): SkinDepth {
        val l = ColourMath.toLab(colour).l
        return when {
            l > 75 -> SkinDepth.FAIR
            l > 65 -> SkinDepth.LIGHT
            l > 52 -> SkinDepth.MEDIUM
            l > 40 -> SkinDepth.TAN
            else -> SkinDepth.DEEP
        }
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
}
