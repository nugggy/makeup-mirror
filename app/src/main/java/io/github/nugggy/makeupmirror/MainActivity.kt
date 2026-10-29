package io.github.nugggy.makeupmirror

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nugggy.makeupmirror.ui.AppRoot
import io.github.nugggy.makeupmirror.ui.MakeupMirrorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) graph.updates.checkNow()
        setContent { MakeupMirrorTheme { AppRoot(graph) } }
    }

    override fun onResume() {
        super.onResume()
        graph.updates.permissionMaybeGranted()
    }
}
