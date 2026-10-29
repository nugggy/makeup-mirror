package io.github.nugggy.makeupmirror.update

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class UpdateControllerTest {
    private val info = UpdateInfo(Version(0, 2, 0), "https://example.com/a.apk", "")
    private val apk = File("a.apk")

    @Test
    fun automaticCheckWithNoUpdateStaysIdle() = runTest {
        val c = UpdateController(this, check = { null }, download = { _, _ -> apk }, canInstall = { true })
        c.checkNow()
        advanceUntilIdle()
        assertEquals(UpdateState.Idle, c.state.value)
    }

    @Test
    fun manualCheckWithNoUpdateReportsUpToDate() = runTest {
        val c = UpdateController(this, check = { null }, download = { _, _ -> apk }, canInstall = { true })
        c.checkNow(manual = true)
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, c.state.value)
    }

    @Test
    fun foundUpdateDownloadsAndIsReadyToInstall() = runTest {
        val progress = mutableListOf<Float>()
        val c = UpdateController(this, check = { info }, download = { _, report -> report(0.5f); progress += 0.5f; apk }, canInstall = { true })
        c.checkNow()
        advanceUntilIdle()
        assertEquals(UpdateState.Available(info), c.state.value)
        c.startDownload()
        advanceUntilIdle()
        assertEquals(listOf(0.5f), progress)
        assertEquals(UpdateState.ReadyToInstall(info, apk), c.state.value)
    }

    @Test
    fun blockedInstallAsksForPermissionThenRecovers() = runTest {
        var allowed = false
        val c = UpdateController(this, check = { info }, download = { _, _ -> apk }, canInstall = { allowed })
        c.checkNow()
        advanceUntilIdle()
        c.startDownload()
        advanceUntilIdle()
        assertEquals(UpdateState.NeedsPermission(info, apk), c.state.value)
        c.permissionMaybeGranted()
        assertEquals(UpdateState.NeedsPermission(info, apk), c.state.value)
        allowed = true
        c.permissionMaybeGranted()
        assertEquals(UpdateState.ReadyToInstall(info, apk), c.state.value)
    }

    @Test
    fun failedDownloadCanBeRetried() = runTest {
        var attempts = 0
        val c = UpdateController(
            this,
            check = { info },
            download = { _, _ -> attempts++; if (attempts == 1) throw IOException("No connection") else apk },
            canInstall = { true },
        )
        c.checkNow()
        advanceUntilIdle()
        c.startDownload()
        advanceUntilIdle()
        assertTrue(c.state.value is UpdateState.Failed)
        c.startDownload()
        advanceUntilIdle()
        assertEquals(UpdateState.ReadyToInstall(info, apk), c.state.value)
    }

    @Test
    fun dismissReturnsToIdle() = runTest {
        val c = UpdateController(this, check = { info }, download = { _, _ -> apk }, canInstall = { true })
        c.checkNow()
        advanceUntilIdle()
        c.dismiss()
        assertEquals(UpdateState.Idle, c.state.value)
    }
}
