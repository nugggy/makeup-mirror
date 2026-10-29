package io.github.nugggy.makeupmirror.render

import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.Pt
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.face.RegionGeometry
import io.github.nugggy.makeupmirror.face.Resolved

/** Draws a [RenderPlan] onto a canvas that already holds the camera image, using view-space landmarks. */
class MakeupRenderer {
    private enum class Mode { NORMAL, MULTIPLY, SCREEN, COLOUR }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(canvas: Canvas, plan: RenderPlan, landmarks: List<Pt>) {
        if (plan.layers.isEmpty()) return
        val faceWidth = RegionGeometry.faceWidth(landmarks)
        for (layer in plan.layers) {
            val shapes = RegionGeometry.resolve(layer.region, landmarks)
            val blur = layer.softness * faceWidth
            when (layer.blend) {
                Blend.NORMAL -> drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.NORMAL, blur)
                Blend.MULTIPLY -> drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.MULTIPLY, blur)
                Blend.SCREEN -> drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.SCREEN, blur)
                Blend.TINT -> {
                    drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.COLOUR, blur)
                    if (ColourMath.toLab(layer.colour).l < 55) {
                        drawShapes(canvas, shapes, layer.colour, layer.opacity * 0.8f, Mode.MULTIPLY, blur)
                    }
                }
            }
            if (layer.shimmer) drawShapes(canvas, shapes, 0xFFFFFFFF.toInt(), layer.opacity * 0.25f, Mode.SCREEN, blur)
        }
    }

    /** A pulsing dashed outline showing where the current lesson step goes. [pulse] runs 0..1. */
    fun drawHighlight(canvas: Canvas, region: Region, landmarks: List<Pt>, pulse: Float) {
        val faceWidth = RegionGeometry.faceWidth(landmarks)
        outline.color = 0xFFFFFFFF.toInt()
        outline.alpha = (120 + 135 * pulse).toInt().coerceIn(0, 255)
        outline.strokeWidth = faceWidth * 0.006f
        outline.pathEffect = DashPathEffect(floatArrayOf(faceWidth * 0.02f, faceWidth * 0.012f), 0f)
        for (shape in RegionGeometry.resolve(region, landmarks)) {
            when (shape) {
                is Resolved.Poly -> canvas.drawPath(polygonPath(shape.points, emptyList()), outline)
                is Resolved.Line -> canvas.drawPath(linePath(shape.points), outline)
                is Resolved.Ellipse -> {
                    canvas.save()
                    canvas.translate(shape.centre.x, shape.centre.y)
                    canvas.rotate(shape.angleDeg)
                    canvas.drawOval(-shape.rx, -shape.ry, shape.rx, shape.ry, outline)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawShapes(canvas: Canvas, shapes: List<Resolved>, colour: Int, alpha: Float, mode: Mode, blur: Float) {
        for (shape in shapes) {
            paint.reset()
            paint.isAntiAlias = true
            applyMode(paint, mode)
            when (shape) {
                is Resolved.Poly -> {
                    paint.style = Paint.Style.FILL
                    paint.color = withAlpha(colour, alpha)
                    if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(polygonPath(shape.points, shape.holes), paint)
                }
                is Resolved.Line -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = shape.width
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = withAlpha(colour, alpha)
                    if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(linePath(shape.points), paint)
                }
                is Resolved.Ellipse -> {
                    paint.style = Paint.Style.FILL
                    paint.shader = RadialGradient(
                        0f, 0f, 1f,
                        intArrayOf(withAlpha(colour, alpha), withAlpha(colour, alpha * 0.55f), withAlpha(colour, 0f)),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    canvas.save()
                    canvas.translate(shape.centre.x, shape.centre.y)
                    canvas.rotate(shape.angleDeg)
                    canvas.scale(shape.rx, shape.ry)
                    canvas.drawCircle(0f, 0f, 1f, paint)
                    canvas.restore()
                }
            }
        }
    }

    /** True blend modes need API 29. Older phones fall back to plain alpha blending. */
    private fun applyMode(p: Paint, mode: Mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            p.blendMode = when (mode) {
                Mode.NORMAL -> BlendMode.SRC_OVER
                Mode.MULTIPLY -> BlendMode.MULTIPLY
                Mode.SCREEN -> BlendMode.SCREEN
                Mode.COLOUR -> BlendMode.COLOR
            }
        }
    }

    private fun polygonPath(points: List<Pt>, holes: List<List<Pt>>) = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addContour(points)
        holes.forEach { addContour(it) }
    }

    private fun Path.addContour(points: List<Pt>) {
        if (points.isEmpty()) return
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        close()
    }

    private fun linePath(points: List<Pt>) = Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (colour and 0xFFFFFF)
}
