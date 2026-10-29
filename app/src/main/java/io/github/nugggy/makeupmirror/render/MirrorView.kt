package io.github.nugggy.makeupmirror.render

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import io.github.nugggy.makeupmirror.camera.TrackedFrame
import io.github.nugggy.makeupmirror.face.LandmarkSmoother
import io.github.nugggy.makeupmirror.face.Region

/** The camera image as a mirror, with makeup and an optional lesson highlight drawn on the face. */
@Composable
fun MirrorView(
    frame: TrackedFrame?,
    plan: RenderPlan,
    highlight: Region?,
    showMakeup: Boolean,
    modifier: Modifier = Modifier,
) {
    val smoother = remember { LandmarkSmoother() }
    val renderer = remember { MakeupRenderer() }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val pulse = rememberInfiniteTransition(label = "highlight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val f = frame ?: return@Canvas
        val mapper = LandmarkMapper(f.bitmap.width, f.bitmap.height, size.width, size.height, mirror = true)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(-1f, 1f, size.width / 2, size.height / 2)
            native.drawBitmap(
                f.bitmap,
                null,
                RectF(mapper.offsetX, mapper.offsetY, mapper.offsetX + f.bitmap.width * mapper.scale, mapper.offsetY + f.bitmap.height * mapper.scale),
                bitmapPaint,
            )
            native.restore()
            val landmarks = f.landmarks ?: return@drawIntoCanvas
            val points = smoother.smooth(mapper.mapAll(landmarks), f.timeMs / 1000.0)
            if (showMakeup) renderer.draw(native, plan, points)
            if (highlight != null) renderer.drawHighlight(native, highlight, points, pulse.value)
        }
    }
}
