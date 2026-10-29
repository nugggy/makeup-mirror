package io.github.nugggy.makeupmirror.face

/** Smooths every landmark coordinate, starting fresh after the face has been lost for a while. */
class LandmarkSmoother(
    private val minCutoff: Double = 1.5,
    private val beta: Double = 0.01,
    private val resetAfterSeconds: Double = 0.5,
) {
    private var xs: Array<OneEuroFilter> = emptyArray()
    private var ys: Array<OneEuroFilter> = emptyArray()
    private var lastTime: Double? = null

    fun smooth(points: List<Pt>, tSeconds: Double): List<Pt> {
        val last = lastTime
        if (xs.size != points.size || last == null || tSeconds - last > resetAfterSeconds) {
            xs = Array(points.size) { OneEuroFilter(minCutoff, beta) }
            ys = Array(points.size) { OneEuroFilter(minCutoff, beta) }
        }
        lastTime = tSeconds
        return points.mapIndexed { i, p ->
            Pt(xs[i].filter(p.x.toDouble(), tSeconds).toFloat(), ys[i].filter(p.y.toDouble(), tSeconds).toFloat())
        }
    }
}
