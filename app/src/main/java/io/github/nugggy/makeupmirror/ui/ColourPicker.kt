package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Hue, saturation and brightness sliders, for adjusting a captured shade by hand. */
@Composable
fun ColourPicker(colour: Int, onChange: (Int) -> Unit) {
    val hsv = remember(colour) { FloatArray(3).also { android.graphics.Color.colorToHSV(colour, it) } }
    val channels = listOf(Triple("Hue", 0, 0f..360f), Triple("Saturation", 1, 0f..1f), Triple("Brightness", 2, 0f..1f))
    Column {
        channels.forEach { (name, index, range) ->
            Text(name, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hsv[index],
                valueRange = range,
                onValueChange = { value ->
                    val next = hsv.copyOf()
                    next[index] = value
                    onChange(android.graphics.Color.HSVToColor(next))
                },
            )
        }
    }
}
