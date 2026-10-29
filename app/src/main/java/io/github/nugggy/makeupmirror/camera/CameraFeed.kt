package io.github.nugggy.makeupmirror.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Delivers upright (not mirrored) RGBA frames from one camera on a background thread. */
class CameraFeed(private val context: Context) {
    private var executor: ExecutorService? = null
    private var analysis: ImageAnalysis? = null
    private var provider: ProcessCameraProvider? = null

    fun start(
        owner: LifecycleOwner,
        lensFacing: Int,
        onFrame: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val worker = Executors.newSingleThreadExecutor().also { executor = it }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get().also { provider = it }
                val useCase = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                useCase.setAnalyzer(worker) { proxy ->
                    proxy.use {
                        val raw = it.toBitmap()
                        val rotation = it.imageInfo.rotationDegrees
                        val upright = if (rotation == 0) {
                            raw
                        } else {
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        }
                        onFrame(upright, it.imageInfo.timestamp / 1_000_000)
                    }
                }
                analysis = useCase
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                cameraProvider.bindToLifecycle(owner, selector, useCase)
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        analysis?.let { useCase ->
            useCase.clearAnalyzer()
            provider?.unbind(useCase)
        }
        analysis = null
        executor?.shutdown()
        executor = null
    }
}
