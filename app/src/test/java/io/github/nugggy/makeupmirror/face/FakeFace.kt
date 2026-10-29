package io.github.nugggy.makeupmirror.face

import kotlin.math.tan

/** Builds a synthetic 478-point face with only the landmarks FaceAnalyser reads. */
object FakeFace {
    fun build(
        faceLength: Float = 140f,
        cheekWidth: Float = 100f,
        jawWidth: Float = 80f,
        foreheadWidth: Float = 80f,
        eyeWidth: Float = 30f,
        eyeHeight: Float = 9f,
        tiltDeg: Float = 3f,
        browGap: Float = 24f,
        lipHeight: Float = 16f,
        mouthWidth: Float = 45f,
        rollDeg: Float = 0f,
    ): List<Pt> {
        val p = MutableList(478) { Pt(0f, 0f) }
        val cx = 200f
        val eyeY = 200f
        p[10] = Pt(cx, eyeY - faceLength * 0.4f)
        p[152] = Pt(cx, eyeY + faceLength * 0.6f)
        p[234] = Pt(cx - cheekWidth / 2, eyeY + 10)
        p[454] = Pt(cx + cheekWidth / 2, eyeY + 10)
        p[172] = Pt(cx - jawWidth / 2, eyeY + 70)
        p[397] = Pt(cx + jawWidth / 2, eyeY + 70)
        p[54] = Pt(cx - foreheadWidth / 2, eyeY - 40)
        p[284] = Pt(cx + foreheadWidth / 2, eyeY - 40)
        val rise = (eyeWidth * tan(Math.toRadians(tiltDeg.toDouble()))).toFloat()
        // The subject's right eye is on the image left, so its outer corner (33) has the smaller x.
        fun eye(outer: Int, inner: Int, top: Int, bottom: Int, brow: Int, innerX: Float, outward: Float) {
            val innerPt = Pt(innerX, eyeY)
            val outerPt = Pt(innerX + outward * eyeWidth, eyeY - rise)
            p[inner] = innerPt
            p[outer] = outerPt
            val mid = Pt((innerPt.x + outerPt.x) / 2, (innerPt.y + outerPt.y) / 2)
            p[top] = Pt(mid.x, mid.y - eyeHeight / 2)
            p[bottom] = Pt(mid.x, mid.y + eyeHeight / 2)
            p[brow] = Pt(mid.x, mid.y - eyeHeight / 2 - browGap)
        }
        eye(outer = 33, inner = 133, top = 159, bottom = 145, brow = 105, innerX = cx - 15, outward = -1f)
        eye(outer = 263, inner = 362, top = 386, bottom = 374, brow = 334, innerX = cx + 15, outward = 1f)
        val mouthY = eyeY + 60
        p[61] = Pt(cx - mouthWidth / 2, mouthY)
        p[291] = Pt(cx + mouthWidth / 2, mouthY)
        p[0] = Pt(cx, mouthY - lipHeight / 2)
        p[17] = Pt(cx, mouthY + lipHeight / 2)
        val roll = Math.toRadians(rollDeg.toDouble())
        val centre = Pt(cx, eyeY)
        return p.map { it.rotate(roll, centre) }
    }
}
