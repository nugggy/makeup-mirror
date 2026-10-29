package io.github.nugggy.makeupmirror.ui

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nugggy.makeupmirror.camera.CameraFeed
import io.github.nugggy.makeupmirror.colour.ColourSampler
import kotlin.math.min

/** Live camera with a target circle. The colour inside the circle is averaged, ignoring glare. */
@Composable
fun SwatchSampler(onCapture: (Int) -> Unit, onCancel: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val owner = LocalLifecycleOwner.current
        var lens by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
        var frame by remember { mutableStateOf<Bitmap?>(null) }
        var sampled by remember { mutableStateOf<Int?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val feed = remember { CameraFeed(context) }

        DisposableEffect(owner, lens) {
            error = null
            feed.start(owner, lens, onFrame = { bitmap, _ ->
                sampled = sampleCentre(bitmap)
                frame = bitmap
            }, onError = { error = "Couldn't open that camera. Try switching cameras." })
            onDispose { feed.stop() }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            frame?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.align(Alignment.Center).size(72.dp).border(3.dp, Color.White, CircleShape))
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Swatch a little of the product on white paper or the back of your hand, then fill the circle with it. Use even daylight if you can.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(sampled?.let { Color(it) } ?: Color.Gray))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Cancel") }
                        TextButton(onClick = {
                            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        }) { Text("Switch camera") }
                        Button(onClick = { sampled?.let(onCapture) }, enabled = sampled != null) { Text("Capture") }
                    }
                }
            }
        }
    }
}

private fun sampleCentre(bitmap: Bitmap): Int {
    val radius = min(bitmap.width, bitmap.height) / 20
    val size = radius * 2 + 1
    val pixels = IntArray(size * size)
    bitmap.getPixels(pixels, 0, size, bitmap.width / 2 - radius, bitmap.height / 2 - radius, size, size)
    return ColourSampler.average(ColourSampler.circle(pixels, size, size, radius, radius, radius))
}
