package io.github.nugggy.makeupmirror.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/** An image from assets/styles/, or a blank tile if it is missing. */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        runCatching { context.assets.open("styles/$path").use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
