package io.github.nugggy.makeupmirror.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(val version: Version, val apkUrl: String, val notes: String)

/** Outcome of asking GitHub for the latest release. */
sealed interface CheckResult {
    data class Available(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data object Failed : CheckResult
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

/** Asks GitHub Releases whether a newer build than [currentVersion] exists. Never throws. */
class UpdateChecker(
    private val client: OkHttpClient,
    private val currentVersion: String,
    private val latestUrl: String = LATEST_URL,
) {
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    !response.isSuccessful || body == null -> CheckResult.Failed
                    else -> parseRelease(body, currentVersion)?.let { CheckResult.Available(it) } ?: CheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            CheckResult.Failed
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"
        const val LATEST_URL = "https://api.github.com/repos/nugggy/makeup-mirror/releases/latest"
        const val SHARE_URL = "https://github.com/nugggy/makeup-mirror/releases/latest/download/makeup-mirror.apk"
        private val json = Json { ignoreUnknownKeys = true }

        fun parseRelease(body: String, currentVersion: String): UpdateInfo? {
            val release = runCatching { json.decodeFromString<ReleaseDto>(body) }.getOrNull() ?: return null
            if (release.draft || release.prerelease) return null
            val latest = Version.parse(release.tagName) ?: return null
            val current = Version.parse(currentVersion) ?: return null
            if (latest <= current) return null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
            return UpdateInfo(latest, apk.url, release.body.orEmpty())
        }
    }
}
