package io.github.nugggy.makeupmirror.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionGeometryTest {
    private fun blankFace(): MutableList<Pt> = MutableList(478) { Pt(0f, 0f) }.also {
        it[234] = Pt(0f, 0f)
        it[454] = Pt(100f, 0f)
    }

    @Test
    fun everyRegionHasShapesWithValidIndices() {
        Region.entries.forEach { region ->
            val shapes = FaceRegions.shapes(region)
            assertTrue("$region has no shapes", shapes.isNotEmpty())
            val indices = shapes.flatMap { shape ->
                when (shape) {
                    is RegionShape.Polygon -> shape.outline + shape.holes.flatten()
                    is RegionShape.Blob -> listOf(shape.centre, shape.towards)
                    is RegionShape.Stroke -> shape.path
                    is RegionShape.Wing -> listOf(shape.outerCorner, shape.lidPoint, shape.browTail)
                }
            }
            assertTrue("$region index out of range", indices.all { it in 0 until 478 })
        }
    }

    @Test
    fun blobScalesWithFaceWidthAndPointsTowardsTarget() {
        val pts = blankFace()
        pts[50] = Pt(50f, 50f)
        pts[34] = Pt(50f, 10f)
        val e = RegionGeometry.resolve(RegionShape.Blob(50, 34, radius = 0.1f, stretch = 2f), pts, 100f) as Resolved.Ellipse
        assertEquals(20f, e.rx, 1e-4f)
        assertEquals(10f, e.ry, 1e-4f)
        assertEquals(-90f, e.angleDeg, 1e-3f)
    }

    @Test
    fun wingTipPointsFromOuterCornerTowardsBrowTail() {
        val pts = blankFace()
        pts[33] = Pt(10f, 50f)
        pts[246] = Pt(14f, 47f)
        pts[46] = Pt(0f, 40f)
        val wing = RegionGeometry.resolve(RegionShape.Wing(33, 246, 46, length = 0.07f), pts, 100f) as Resolved.Poly
        val tip = wing.points[1]
        assertEquals(7f, tip.dist(pts[33]), 1e-3f)
        assertTrue(tip.x < pts[33].x && tip.y < pts[33].y)
    }

    @Test
    fun strokeWidthScalesWithFaceWidth() {
        val pts = blankFace()
        val line = RegionGeometry.resolve(RegionShape.Stroke(listOf(1, 2), 0.01f), pts, 250f) as Resolved.Line
        assertEquals(2.5f, line.width, 1e-4f)
    }

    @Test
    fun lipsHaveTheMouthOpeningAsAHole() {
        val poly = RegionGeometry.resolve(Region.LIPS, blankFace()).single() as Resolved.Poly
        assertEquals(FaceRegions.LIPS_OUTER.size, poly.points.size)
        assertEquals(1, poly.holes.size)
    }
}
