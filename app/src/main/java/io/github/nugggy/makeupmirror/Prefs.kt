package io.github.nugggy.makeupmirror

import android.content.Context
import androidx.core.content.edit
import io.github.nugggy.makeupmirror.face.FaceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(
        sp.getString(KEY_PROFILE, null)?.let { runCatching { Json.decodeFromString<FaceProfile>(it) }.getOrNull() },
    )
    val profile: StateFlow<FaceProfile?> = _profile

    fun setProfile(value: FaceProfile?) {
        sp.edit { if (value == null) remove(KEY_PROFILE) else putString(KEY_PROFILE, Json.encodeToString(value)) }
        _profile.value = value
    }

    private val _strength = MutableStateFlow(sp.getFloat(KEY_STRENGTH, 1f))
    val strength: StateFlow<Float> = _strength

    fun setStrength(value: Float) {
        sp.edit { putFloat(KEY_STRENGTH, value) }
        _strength.value = value
    }

    private val _readAloud = MutableStateFlow(sp.getBoolean(KEY_READ_ALOUD, true))
    val readAloud: StateFlow<Boolean> = _readAloud

    fun setReadAloud(value: Boolean) {
        sp.edit { putBoolean(KEY_READ_ALOUD, value) }
        _readAloud.value = value
    }

    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit { putBoolean(KEY_ONBOARDED, value) }

    private companion object {
        const val KEY_PROFILE = "faceProfile"
        const val KEY_STRENGTH = "overlayStrength"
        const val KEY_READ_ALOUD = "readAloud"
        const val KEY_ONBOARDED = "onboarded"
    }
}
