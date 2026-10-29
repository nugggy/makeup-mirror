package io.github.nugggy.makeupmirror

import android.app.Application
import android.content.Context
import io.github.nugggy.makeupmirror.kit.AppDatabase
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLibrary
import io.github.nugggy.makeupmirror.update.UpdateChecker
import io.github.nugggy.makeupmirror.update.UpdateController
import io.github.nugggy.makeupmirror.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** App-wide singletons. */
class AppGraph(app: Application) {
    val prefs = Prefs(app)
    val db = AppDatabase.create(app)
    val looks: List<Look> by lazy { LookLibrary.load(app) }

    private val checkClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val downloadClient = checkClient.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val installer = UpdateInstaller(app, downloadClient)
    val updates = UpdateController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        check = UpdateChecker(checkClient, BuildConfig.VERSION_NAME)::check,
        download = installer::download,
        canInstall = installer::canInstall,
    )
}

val Context.graph: AppGraph
    get() = (applicationContext as MakeupMirrorApp).graph
