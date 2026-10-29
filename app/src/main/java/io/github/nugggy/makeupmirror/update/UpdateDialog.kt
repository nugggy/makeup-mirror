package io.github.nugggy.makeupmirror.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("New version available") },
            text = { Text("Version ${state.info.version} is ready to download.\n\n${state.info.notes}".trim()) },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Update") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = { LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ready to install") },
            text = { Text("Tap Install, then confirm on the next screen. Your makeup kit will be kept.") },
            confirmButton = { TextButton(onClick = { onInstall(state.apk) }) { Text("Install") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        )
        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("One more step") },
            text = { Text("Android needs your permission for Makeup Mirror to install its own updates. Turn on \"Allow from this source\", then come back to the app.") },
            confirmButton = { TextButton(onClick = onOpenPermission) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("The update didn't download") },
            text = { Text("${state.message}. Check your internet connection and try again.") },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("You're up to date") },
            text = { Text("You have the latest version of Makeup Mirror.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        UpdateState.CheckFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't check for updates") },
            text = { Text("Check your internet connection and try again later.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}
