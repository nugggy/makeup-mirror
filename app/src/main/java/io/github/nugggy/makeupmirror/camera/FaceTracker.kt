package io.github.nugggy.makeupmirror.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import io.github.nugggy.makeupmirror.colour.ColourSampler
import io.github.nugggy.makeupmirror.face.Pt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import kotlin.math.max

/** A camera frame with the face landmarks found in it, normalised to 0..1 of the upright image. */
data class TrackedFrame(
    val bitmap: Bitmap,
    val landmarks: List<Pt>?,
    val skinColour: Int?,
    val timeMs: Long,
)

class FaceTracker(context: Context) : Closeable {
    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )
    private val lock = Any()
    private var closed = false
    private var lastTimestamp = -1L
    private var frameCount = 0

    private val _frames = MutableStateFlow<TrackedFrame?>(null)
    val frames: StateFlow<TrackedFrame?> = _frames

    /** Runs on the camera thread. */
    fun process(bitmap: Bitmap, timeMs: Long) {
        synchronized(lock) {
            if (closed) return
            val timestamp = if (timeMs <= lastTimestamp) lastTimestamp + 1 else timeMs
            lastTimestamp = timestamp
            val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
            val landmarks = result.faceLandmarks().firstOrNull()?.map { Pt(it.x(), it.y()) }
            val skin = if (landmarks != null && frameCount++ % SKIN_SAMPLE_EVERY == 0) sampleSkin(bitmap, landmarks) else null
            _frames.value = TrackedFrame(bitmap, landmarks, skin, timestamp)
        }
    }

    /** Averages small patches on both cheeks and the forehead. */
    private fun sampleSkin(bitmap: Bitmap, landmarks: List<Pt>): Int? {
        val radius = max(3, bitmap.width / 80)
        val size = radius * 2 + 1
        val all = ArrayList<Int>()
        for (index in SKIN_POINTS) {
            val cx = (landmarks[index].x * bitmap.width).toInt()
            val cy = (landmarks[index].y * bitmap.height).toInt()
            if (cx - radius < 0 || cy - radius < 0 || cx + radius >= bitmap.width || cy + radius >= bitmap.height) continue
            val patch = IntArray(size * size)
            bitmap.getPixels(patch, 0, size, cx - radius, cy - radius, size, size)
            all += ColourSampler.circle(patch, size, size, radius, radius, radius).toList()
        }
        return if (all.isEmpty()) null else ColourSampler.average(all.toIntArray())
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            landmarker.close()
        }
    }

    private companion object {
        const val SKIN_SAMPLE_EVERY = 10
        val SKIN_POINTS = listOf(50, 280, 151)
    }
}
