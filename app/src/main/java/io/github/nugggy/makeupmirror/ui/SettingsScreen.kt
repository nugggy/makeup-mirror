package io.github.nugggy.makeupmirror.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.BuildConfig
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.face.label
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(graph: AppGraph) {
    val readAloud by graph.prefs.readAloud.collectAsStateWithLifecycle()
    val strength by graph.prefs.strength.collectAsStateWithLifecycle()
    val profile by graph.prefs.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Read lesson steps aloud", Modifier.weight(1f))
            Switch(checked = readAloud, onCheckedChange = graph.prefs::setReadAloud)
        }

        Text("Makeup strength in the preview: ${(strength * 100).roundToInt()}%")
        Slider(value = strength, onValueChange = graph.prefs::setStrength, valueRange = 0.3f..1.5f)

        HorizontalDivider()
        Text("Face analysis", style = MaterialTheme.typography.titleMedium)
        Text(profile?.let(::describe) ?: "Not analysed yet. Open Try on and look at the camera in good light.")
        Text(
            "These are rough guides based on the camera, and lighting can change the undertone result.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { graph.prefs.setProfile(null) }, enabled = profile != null) { Text("Analyse my face again") }

        HorizontalDivider()
        Button(onClick = { context.startActivity(shareAppIntent()) }) {
            Icon(Icons.Default.Share, contentDescription = null)
            Text("  Share app")
        }
        OutlinedButton(onClick = { graph.updates.checkNow(manual = true) }) { Text("Check for updates") }
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nugggy/makeup-mirror/blob/main/SOURCES.md")))
        }) { Text("Image sources") }
    }
}

private fun describe(p: FaceProfile): String = buildString {
    append("Face shape: ${p.faceShape.label()}. Eyes: ${p.eyeShape.label()}. Lips: ${p.lips.label()}.")
    p.undertone?.let { append(" Undertone: ${it.label()}.") }
    p.depth?.let { append(" Skin depth: ${it.label()}.") }
}
