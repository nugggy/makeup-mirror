package io.github.nugggy.makeupmirror

import android.app.Application

class MakeupMirrorApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
