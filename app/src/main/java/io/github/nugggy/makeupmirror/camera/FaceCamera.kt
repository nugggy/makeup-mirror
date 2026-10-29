package io.github.nugggy.makeupmirror.camera

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

data class FaceCameraState(val frame: TrackedFrame?, val error: String?)

/** Front camera plus face tracking for as long as the caller is composed. Bump [attempt] to retry. */
@Composable
fun rememberFaceCamera(attempt: Int): FaceCameraState {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val created = remember(attempt) { runCatching { FaceTracker(context) } }
    val tracker = created.getOrNull()
    val feed = remember(attempt) { CameraFeed(context) }
    var cameraError by remember(attempt) { mutableStateOf<String?>(null) }

    DisposableEffect(owner, tracker) {
        if (tracker != null) {
            feed.start(owner, CameraSelector.LENS_FACING_FRONT, tracker::process) { cameraError = it.message ?: "The camera isn't available" }
        }
        onDispose {
            feed.stop()
            tracker?.close()
        }
    }

    val empty = remember { MutableStateFlow<TrackedFrame?>(null) }
    val frame by (tracker?.frames ?: empty).collectAsStateWithLifecycle()
    val error = created.exceptionOrNull()?.let { "Face tracking couldn't start (${it.message})" } ?: cameraError
    return FaceCameraState(frame, error)
}
