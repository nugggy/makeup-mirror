package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Welcome to Makeup Mirror", style = MaterialTheme.typography.headlineMedium)
        Text("1. Add the makeup you own to My kit, and capture each shade with the camera.")
        Text("2. Try different looks on your face in Try on. Swipe to compare them.")
        Text("3. Pick your favourite and follow the step-by-step lesson.")
        Text("Everything stays on your phone.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("Get started") }
    }
}
