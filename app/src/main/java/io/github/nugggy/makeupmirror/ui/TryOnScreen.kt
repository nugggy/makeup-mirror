package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.camera.rememberFaceCamera
import io.github.nugggy.makeupmirror.face.FaceAnalyser
import io.github.nugggy.makeupmirror.face.ProfileCollector
import io.github.nugggy.makeupmirror.face.Pt
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.matcher.StyleMatcher
import io.github.nugggy.makeupmirror.render.MirrorView
import io.github.nugggy.makeupmirror.render.RenderPlan
import io.github.nugggy.makeupmirror.render.RenderPlanner
import kotlinx.coroutines.delay

@Composable
fun TryOnScreen(graph: AppGraph, onAddProducts: () -> Unit, onChoose: (String) -> Unit) {
    val kit by graph.db.products().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val profile by graph.prefs.profile.collectAsStateWithLifecycle()
    val strength by graph.prefs.strength.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var before by remember { mutableStateOf(false) }

    val products = kit ?: return
    if (products.isEmpty()) {
        EmptyKit(onAddProducts)
        return
    }
    val matches = remember(products, profile) { StyleMatcher.match(products, profile, graph.looks) }
    val shown = if (showAll) matches else matches.filter { it.available }
    val selected = shown.firstOrNull { it.look.id == selectedId } ?: shown.firstOrNull()
    val plan = remember(selected, strength) {
        selected?.takeIf { it.available }?.let { RenderPlanner.plan(it, strength) } ?: RenderPlan.EMPTY
    }

    CameraPermissionGate {
        var attempt by remember { mutableIntStateOf(0) }
        val camera = rememberFaceCamera(attempt)
        val error = camera.error
        if (error != null) {
            ErrorPanel(error) { attempt++ }
            return@CameraPermissionGate
        }
        val frame = camera.frame
        val hasFace = frame?.landmarks != null
        var faceMissing by remember { mutableStateOf(false) }
        LaunchedEffect(hasFace, frame != null) {
            faceMissing = false
            if (frame != null && !hasFace) {
                delay(1000)
                faceMissing = true
            }
        }

        // Build the face profile once, from the most common answer over several frames.
        val collector = remember { ProfileCollector() }
        LaunchedEffect(frame) {
            val f = frame ?: return@LaunchedEffect
            val landmarks = f.landmarks ?: return@LaunchedEffect
            val skin = f.skinColour ?: return@LaunchedEffect
            if (graph.prefs.profile.value != null) return@LaunchedEffect
            val pixels = landmarks.map { Pt(it.x * f.bitmap.width, it.y * f.bitmap.height) }
            collector.add(FaceAnalyser.analyse(pixels, skin))?.let(graph.prefs::setProfile)
        }

        Box(Modifier.fillMaxSize()) {
            MirrorView(frame, plan, highlight = null, showMakeup = !before, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                when {
                    faceMissing -> Hint("Move your face into the frame, or try better light")
                    profile == null && hasFace -> Hint("Analysing your face for suggestions")
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showAll, onClick = { showAll = !showAll }, label = { Text("Show looks I can't do yet") })
                    FilterChip(selected = before, onClick = { before = !before }, label = { Text("Before") })
                }
                if (shown.isEmpty()) {
                    Text(
                        "None of the looks can be made with your kit yet. Turn on \"Show looks I can't do yet\" to see what's missing.",
                        Modifier.padding(horizontal = 12.dp),
                    )
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.look.id }) { match ->
                        LookCard(match, selected = match.look.id == selected?.look?.id) { selectedId = match.look.id }
                    }
                }
                selected?.let { match ->
                    if (match.available) {
                        Button(onClick = { onChoose(match.look.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Text("Teach me this look")
                        }
                    } else {
                        Text("To do this look you need: ${missingText(match)}", Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LookCard(match: MatchedLook, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(170.dp),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        AssetImage(match.look.image, Modifier.fillMaxWidth().height(96.dp))
        Column(Modifier.padding(8.dp)) {
            Text(match.look.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                match.suitsYou -> {
                    Text("Suits you", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(match.reasons.first(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                !match.available -> Text("Needs ${missingText(match)}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else -> Text(match.look.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun missingText(match: MatchedLook) = match.missing.joinToString { it.label.lowercase() }

@Composable
private fun EmptyKit(onAddProducts: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Add your makeup first", style = MaterialTheme.typography.titleLarge)
        Text("Try on only shows looks you can make with the products you own.", textAlign = TextAlign.Center)
        Button(onClick = onAddProducts) { Text("Go to My kit") }
    }
}
