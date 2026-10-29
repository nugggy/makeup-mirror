package io.github.nugggy.makeupmirror.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val apk: File) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/** Drives the update flow. Android specifics are injected so the flow can be unit tested. */
class UpdateController(
    private val scope: CoroutineScope,
    private val check: suspend () -> CheckResult,
    private val download: suspend (UpdateInfo, (Float) -> Unit) -> File,
    private val canInstall: () -> Boolean,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun checkNow(manual: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = when (val result = check()) {
                is CheckResult.Available -> UpdateState.Available(result.info)
                CheckResult.UpToDate -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                CheckResult.Failed -> if (manual) UpdateState.CheckFailed else UpdateState.Idle
            }
        }
    }

    fun startDownload() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        scope.launch {
            _state.value = try {
                val apk = download(info) { progress -> _state.value = UpdateState.Downloading(info, progress) }
                if (canInstall()) UpdateState.ReadyToInstall(info, apk) else UpdateState.NeedsPermission(info, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(info, e.message ?: "Download failed")
            }
        }
    }

    /** Call when returning from the "install unknown apps" settings screen. */
    fun permissionMaybeGranted() {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (canInstall()) _state.value = UpdateState.ReadyToInstall(s.info, s.apk)
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
