package io.github.nugggy.makeupmirror.styles

import android.content.Context
import kotlinx.serialization.json.Json

object LookLibrary {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<Look> = json.decodeFromString(text)

    fun load(context: Context): List<Look> =
        context.assets.open("styles/looks.json").bufferedReader().use { parse(it.readText()) }
}
