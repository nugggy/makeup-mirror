package io.github.nugggy.makeupmirror.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.camera.rememberFaceCamera
import io.github.nugggy.makeupmirror.lesson.LessonBuilder
import io.github.nugggy.makeupmirror.lesson.LessonCard
import io.github.nugggy.makeupmirror.lesson.rememberSpeaker
import io.github.nugggy.makeupmirror.matcher.StyleMatcher
import io.github.nugggy.makeupmirror.render.MirrorView
import io.github.nugggy.makeupmirror.render.RenderPlanner

@Composable
fun LessonScreen(graph: AppGraph, lookId: String, onExit: () -> Unit) {
    val kit by graph.db.products().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val profile by graph.prefs.profile.collectAsStateWithLifecycle()
    val strength by graph.prefs.strength.collectAsStateWithLifecycle()
    val readAloud by graph.prefs.readAloud.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var compare by rememberSaveable { mutableStateOf(false) }
    val speaker = rememberSpeaker()

    val products = kit ?: return
    val match = remember(products, profile) {
        StyleMatcher.match(products, profile, graph.looks).firstOrNull { it.look.id == lookId }
    }
    if (match == null || !match.available) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text("This look needs products that aren't in your kit any more.")
            Button(onClick = onExit) { Text("Back") }
        }
        return
    }
    val cards = remember(match) { LessonBuilder.build(match) }
    val fullPlan = remember(match, strength) { RenderPlanner.plan(match, strength) }
    val finished = step >= cards.size
    val card = cards.getOrNull(step)

    LaunchedEffect(step, readAloud) {
        if (readAloud && card != null) speaker.speak(spokenText(step, card)) else speaker.stop()
    }
    DisposableEffect(Unit) { onDispose { speaker.stop() } }

    CameraPermissionGate {
        var attempt by remember { mutableIntStateOf(0) }
        val camera = rememberFaceCamera(attempt)
        val error = camera.error
        if (error != null) {
            ErrorPanel(error) { attempt++ }
            return@CameraPermissionGate
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val plan = if (finished) fullPlan else fullPlan.forLessonStep(cards.map { it.role }, step)
                MirrorView(
                    frame = camera.frame,
                    plan = plan,
                    highlight = card?.region,
                    showMakeup = !finished || compare,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Top) {
                    TextButton(onClick = onExit) { Text("Exit") }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetImage(
                            match.look.image,
                            Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(12.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
                        )
                        if (!finished) Hint("Step ${step + 1} of ${cards.size}")
                    }
                }
                if (camera.frame != null && camera.frame.landmarks == null) {
                    Hint("Move your face into the frame", Modifier.align(Alignment.Center))
                }
            }
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (card != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(card.product.colour)))
                            Spacer(Modifier.width(8.dp))
                            Text("Your ${card.product.name}", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(card.instruction)
                        card.tip?.let { Text("Tip: $it", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { step-- }, enabled = step > 0) { Text("Back") }
                            TextButton(onClick = { speaker.speak(spokenText(step, card)) }) { Text("Read aloud") }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { step++ }) { Text(if (step == cards.lastIndex) "Finish" else "Next") }
                        }
                    } else {
                        Text("You've finished ${match.look.name}", style = MaterialTheme.typography.titleLarge)
                        Text("Compare your face with the picture in the corner. Turn on the overlay to see the target look on your face.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = compare, onClick = { compare = !compare }, label = { Text("Show target look") })
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { step = 0; compare = false }) { Text("Start again") }
                            Button(onClick = onExit) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
}

private fun spokenText(step: Int, card: LessonCard) =
    "Step ${step + 1}. Use your ${card.product.name}. ${card.instruction} ${card.tip?.let { "Tip. $it" } ?: ""}"
