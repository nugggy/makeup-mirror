package io.github.nugggy.makeupmirror.face

import kotlin.math.atan2

/** A region shape resolved to concrete coordinates, ready to draw. */
sealed interface Resolved {
    data class Poly(val points: List<Pt>, val holes: List<List<Pt>>) : Resolved
    data class Ellipse(val centre: Pt, val rx: Float, val ry: Float, val angleDeg: Float) : Resolved
    data class Line(val points: List<Pt>, val width: Float) : Resolved
}

object RegionGeometry {
    fun faceWidth(pts: List<Pt>): Float = pts[234].dist(pts[454])

    fun resolve(region: Region, pts: List<Pt>): List<Resolved> {
        val width = faceWidth(pts)
        return FaceRegions.shapes(region).map { resolve(it, pts, width) }
    }

    fun resolve(shape: RegionShape, pts: List<Pt>, faceWidth: Float): Resolved = when (shape) {
        is RegionShape.Polygon -> Resolved.Poly(shape.outline.map(pts::get), shape.holes.map { hole -> hole.map(pts::get) })
        is RegionShape.Blob -> {
            val centre = pts[shape.centre]
            val towards = pts[shape.towards]
            val angle = Math.toDegrees(atan2((towards.y - centre.y).toDouble(), (towards.x - centre.x).toDouble())).toFloat()
            val r = shape.radius * faceWidth
            Resolved.Ellipse(centre, r * shape.stretch, r, angle)
        }
        is RegionShape.Stroke -> Resolved.Line(shape.path.map(pts::get), shape.width * faceWidth)
        is RegionShape.Wing -> {
            val outer = pts[shape.outerCorner]
            val direction = (pts[shape.browTail] - outer).normalised()
            val tip = outer + direction * (shape.length * faceWidth)
            Resolved.Poly(listOf(pts[shape.lidPoint], tip, outer), emptyList())
        }
    }
}
