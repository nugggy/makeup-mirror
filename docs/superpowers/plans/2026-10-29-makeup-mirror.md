# Makeup Mirror Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and publish a personal Android app that previews makeup looks on the user's live face using only the products she owns, then teaches her step by step how to apply the chosen look.

**Architecture:** A single-module Kotlin and Jetpack Compose app. CameraX analysis frames go through MediaPipe Face Landmarker. The app draws the camera frame and the makeup layers itself on one canvas, so blend modes work and the overlay stays in sync with the face. All decision logic (colour maths, face analysis, matching, render planning, lessons, update parsing) is pure Kotlin with JVM unit tests. Android code is a thin shell around it. GitHub Actions builds signed release APKs, and the app checks GitHub Releases for updates on launch.

**Tech Stack:** Kotlin 2.2.20, AGP 8.13.0, Gradle 8.14.3, Compose BOM 2026.09.00 (Material 3), CameraX 1.5.x, MediaPipe tasks-vision 0.10.26, Room 2.8.x (KSP), OkHttp 4.12, kotlinx.serialization 1.9, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-29-makeup-mirror-design.md`

## Conventions for this plan

- `$MAIN` means `app/src/main/java/io/github/nugggy/makeupmirror` and `$TEST` means `app/src/test/java/io/github/nugggy/makeupmirror`.
- Every file is given in full, introduced by a `<!-- file: path -->` marker. The helper `node <scratchpad>/extract.mjs <plan> <task> [paths...]` writes the files for a task (or only the listed paths) so code is never retyped by hand. Within a task, extract the test file first, run it and watch it fail, then extract the implementation.
- All Gradle commands run from the repo root with `./gradlew` (Git Bash). Unit tests: `./gradlew testDebugUnitTest`. A single class: `./gradlew testDebugUnitTest --tests '*VersionTest'`.
- If a dependency version in `libs.versions.toml` fails to resolve, use the nearest published version and note the change in the commit message.
- Commit messages end with the attribution lines from the session.

## Global Constraints

- Package and applicationId: `io.github.nugggy.makeupmirror`. App name: Makeup Mirror. Repo: `github.com/nugggy/makeup-mirror` (public).
- minSdk 26 (Android 8.0). targetSdk 35. compileSdk 36 (the platform installed locally. The spec said 35, and 36 is a compatible superset).
- JDK 17. Gradle wrapper 8.14.3.
- Release tags are `vMAJOR.MINOR.PATCH`. versionName comes from the tag. versionCode = MAJOR*10000 + MINOR*100 + PATCH. Local builds default to 0.1.0.
- Release assets: `makeup-mirror-<version>.apk` plus an identical `makeup-mirror.apk`. Share link: `https://github.com/nugggy/makeup-mirror/releases/latest/download/makeup-mirror.apk`.
- The only network calls are `https://api.github.com/repos/nugggy/makeup-mirror/releases/latest` and the APK download. No analytics and no accounts.
- Camera frames are never written to storage.
- All user-facing text uses Australian English (colour, organise, centre) and sentence case.
- Pure logic packages (`colour`, `face` except `FaceTracker`, `styles` except `LookLibrary.load`, `matcher`, `render` except `MakeupRenderer`/`MirrorView`, `update.Version`, `update.UpdateChecker.parseRelease`, `update.UpdateController`) must not touch Android APIs, so they run in JVM tests.

## Deliberate departures from the spec

- The camera picture is drawn by the app on a Compose canvas rather than shown in a `PreviewView`. Blend modes can only mix with pixels in the same canvas, and this also keeps the overlay perfectly in step with the frame it was computed from.
- The end of the lesson shows the live mirror next to the look's reference image, with a toggle to overlay the target look. No snapshot is saved, which is more private and needs no storage clean-up.
- Render correctness is tested at the geometry and plan level in JVM tests (regions, mapping, layer order, blend choice) instead of pixel snapshot tests, plus a manual check on device.
- On Android 8 and 9 (API 26 to 28) makeup layers use normal alpha blending. True multiply, screen and colour blending needs API 29.

## Review Focus

1. **Face leaves the frame or the head turns away:** overlays disappear (no stale makeup frozen on screen), a "move into frame" hint appears after 1 second, and when the face returns smoothing restarts rather than sliding from the old position. Pinned by `LandmarkSmootherTest.gapResetsFilters` (Task 11) and the `faceMissing` logic in Task 15.
2. **Kit has one eyeshadow but the look uses separate lid and crease shades:** the look is still available and the same shadow is used for both. Pinned by `StyleMatcherTest.singleProductCoversTwoRolesOfSameType` (Task 9).
3. **GitHub returns something unexpected** (no release, malformed tag, prerelease, no APK asset, rate limit 403, or no internet): no prompt, no crash. Pinned by `UpdateCheckerTest` (Task 2).
4. **Swatch sampled with glare or a bit of background in the circle:** the colour comes from the dominant pixels, not a muddy average. Pinned by `ColourSamplerTest.glareAndBackgroundAreIgnored` (Task 4).
5. **Head tilted sideways during face analysis:** eye shape and face shape do not change because of roll. Pinned by `FaceAnalyserTest.headRollDoesNotChangeClassification` (Task 8).

---

### Task 1: Project scaffold, launcher icon and CI

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `local.properties` (not committed)
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values/colors.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/xml/file_paths.xml`
- Create: `$MAIN/MainActivity.kt` (temporary, replaced in Task 17), `$MAIN/MakeupMirrorApp.kt` (temporary, replaced in Task 13)
- Create: `.github/workflows/ci.yml`
- Create: Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- Create: `app/src/main/assets/face_landmarker.task` (downloaded)

**Interfaces:**
- Produces: a buildable app module with `BuildConfig.VERSION_NAME`, the FileProvider authority `${applicationId}.files`, and the model asset `face_landmarker.task`.

- [ ] **Step 1: Write the build files**

<!-- file: settings.gradle.kts -->
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "makeup-mirror"
include(":app")
```

<!-- file: build.gradle.kts -->
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

<!-- file: gradle.properties -->
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

<!-- file: gradle/libs.versions.toml -->
```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
ksp = "2.2.20-2.0.3"
coreKtx = "1.17.0"
activityCompose = "1.11.0"
composeBom = "2026.09.00"
lifecycle = "2.9.4"
navigation = "2.9.5"
camerax = "1.5.1"
mediapipe = "0.10.26"
room = "2.8.2"
okhttp = "4.12.0"
serialization = "1.9.0"
coroutines = "1.10.2"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
camerax-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
mediapipe-tasks-vision = { module = "com.google.mediapipe:tasks-vision", version.ref = "mediapipe" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

<!-- file: app/build.gradle.kts -->
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release builds get their version from the git tag (v1.2.3) via RELEASE_VERSION.
val appVersion = (System.getenv("RELEASE_VERSION") ?: "0.1.0").removePrefix("v")
val versionParts = appVersion.split(".").map { it.toInt() }
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "io.github.nugggy.makeupmirror"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nugggy.makeupmirror"
        minSdk = 26
        targetSdk = 35
        versionName = appVersion
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystoreFile != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "task"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
```

<!-- file: app/proguard-rules.pro -->
```text
# Minification is off. This file exists so the release build type has a rules file if it is turned on later.
```

<!-- file: .gitignore -->
```text
.gradle/
.kotlin/
build/
/local.properties
.idea/
*.iml
.DS_Store
captures/
*.jks
*.keystore
```

- [ ] **Step 2: Write the manifest and resources**

<!-- file: app/src/main/AndroidManifest.xml -->
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.camera.any" android:required="true" />

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
        android:name=".MakeupMirrorApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MakeupMirror">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

<!-- file: app/src/main/res/values/strings.xml -->
```xml
<resources>
    <string name="app_name">Makeup Mirror</string>
</resources>
```

<!-- file: app/src/main/res/values/themes.xml -->
```xml
<resources>
    <style name="Theme.MakeupMirror" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

<!-- file: app/src/main/res/values/colors.xml -->
```xml
<resources>
    <color name="ic_launcher_background">#C2185B</color>
</resources>
```

<!-- file: app/src/main/res/drawable/ic_launcher_foreground.xml -->
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M54,28a17,17 0,1 1,0 34a17,17 0,1 1,0 -34z" />
    <path android:fillColor="#F7C6D0" android:pathData="M54,32.5a12.5,12.5 0,1 1,0 25a12.5,12.5 0,1 1,0 -25z" />
    <path android:fillColor="#FFFFFF" android:pathData="M51,61h6v17a3,3 0,0 1,-6 0z" />
</vector>
```

<!-- file: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml -->
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

<!-- file: app/src/main/res/xml/file_paths.xml -->
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="updates" path="updates/" />
</paths>
```

- [ ] **Step 3: Write the temporary entry points**

<!-- file: $MAIN/MakeupMirrorApp.kt -->
```kotlin
package io.github.nugggy.makeupmirror

import android.app.Application

class MakeupMirrorApp : Application()
```

<!-- file: $MAIN/MainActivity.kt -->
```kotlin
package io.github.nugggy.makeupmirror

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Makeup Mirror") } }
    }
}
```

- [ ] **Step 4: Write the CI workflow**

<!-- file: .github/workflows/ci.yml -->
```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Unit tests and debug build
        run: ./gradlew --no-daemon testDebugUnitTest assembleDebug
```

- [ ] **Step 5: Generate the Gradle wrapper, local.properties and the face model**

Generate the wrapper in an empty temp folder (so it does not try to configure this build), then copy it in:

```bash
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.14.3-bin/*/gradle-8.14.3)
T=$(mktemp -d) && (cd "$T" && touch settings.gradle && "$G/bin/gradle" wrapper --gradle-version 8.14.3 --distribution-type bin -q)
cp "$T/gradlew" "$T/gradlew.bat" . && mkdir -p gradle/wrapper && cp "$T"/gradle/wrapper/* gradle/wrapper/
printf 'sdk.dir=%s\n' "$(cygpath -m "$LOCALAPPDATA/Android/Sdk")" > local.properties
mkdir -p app/src/main/assets
curl -fL -o app/src/main/assets/face_landmarker.task https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
ls -l app/src/main/assets/face_landmarker.task
```

Expected: the model is about 3.7 MB.

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit**

```bash
git add -A && git update-index --chmod=+x gradlew
git commit -m "Scaffold Android project with CI"
```

---

### Task 2: Version parsing and update check

**Files:**
- Create: `$MAIN/update/Version.kt`, `$MAIN/update/UpdateChecker.kt`
- Test: `$TEST/update/VersionTest.kt`, `$TEST/update/UpdateCheckerTest.kt`

**Interfaces:**
- Produces: `data class Version(major: Int, minor: Int, patch: Int) : Comparable<Version>` with `Version.parse(text: String): Version?`. `data class UpdateInfo(version: Version, apkUrl: String, notes: String)`. `class UpdateChecker(client: OkHttpClient, currentVersion: String, latestUrl: String = UpdateChecker.LATEST_URL)` with `suspend fun check(): UpdateInfo?` and `companion fun parseRelease(body: String, currentVersion: String): UpdateInfo?`. Constant `UpdateChecker.SHARE_URL`.

- [ ] **Step 1: Write the failing tests**

<!-- file: $TEST/update/VersionTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun parsesWithAndWithoutPrefix() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(0, 10, 0), Version.parse("0.10.0"))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(Version.parse("1.2"))
        assertNull(Version.parse("v1.2.3-beta"))
        assertNull(Version.parse("latest"))
    }

    @Test
    fun comparesNumericallyNotAlphabetically() {
        assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
        assertEquals(0, Version(1, 0, 0).compareTo(Version(1, 0, 0)))
    }
}
```

<!-- file: $TEST/update/UpdateCheckerTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private fun release(
        tag: String,
        assets: String = """[{"name":"makeup-mirror-${tag.removePrefix("v")}.apk","browser_download_url":"https://example.com/a.apk"}]""",
        extra: String = "",
    ) = """{"tag_name":"$tag","body":"Notes","assets":$assets$extra}"""

    @Test
    fun newerReleaseWithApkIsOffered() {
        val info = UpdateChecker.parseRelease(release("v0.2.0"), "0.1.0")
        assertEquals(Version(0, 2, 0), info?.version)
        assertEquals("https://example.com/a.apk", info?.apkUrl)
        assertEquals("Notes", info?.notes)
    }

    @Test
    fun sameOrOlderReleaseIsIgnored() {
        assertNull(UpdateChecker.parseRelease(release("v0.1.0"), "0.1.0"))
        assertNull(UpdateChecker.parseRelease(release("v0.0.9"), "0.1.0"))
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(UpdateChecker.parseRelease(release("v0.2.0", assets = "[]"), "0.1.0"))
    }

    @Test
    fun malformedTagOrBodyIsIgnored() {
        assertNull(UpdateChecker.parseRelease(release("nightly"), "0.1.0"))
        assertNull(UpdateChecker.parseRelease("not json", "0.1.0"))
        assertNull(UpdateChecker.parseRelease("""{"message":"API rate limit exceeded"}""", "0.1.0"))
    }

    @Test
    fun prereleaseAndDraftAreIgnored() {
        assertNull(UpdateChecker.parseRelease(release("v0.2.0", extra = ""","prerelease":true"""), "0.1.0"))
        assertNull(UpdateChecker.parseRelease(release("v0.2.0", extra = ""","draft":true"""), "0.1.0"))
    }

    @Test
    fun httpErrorReturnsNull() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"rate limited"}"""))
        server.start()
        val checker = UpdateChecker(OkHttpClient(), "0.1.0", server.url("/latest").toString())
        assertNull(checker.check())
        server.shutdown()
    }

    @Test
    fun successfulCheckReturnsInfo() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(release("v1.0.0")))
        server.start()
        val checker = UpdateChecker(OkHttpClient(), "0.1.0", server.url("/latest").toString())
        assertEquals(Version(1, 0, 0), checker.check()?.version)
        server.shutdown()
    }

    @Test
    fun unreachableServerReturnsNull() = runTest {
        val checker = UpdateChecker(OkHttpClient(), "0.1.0", "http://127.0.0.1:1/latest")
        assertNull(checker.check())
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew testDebugUnitTest --tests '*update*'`
Expected: compilation fails with `Unresolved reference: Version`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/update/Version.kt -->
```kotlin
package io.github.nugggy.makeupmirror.update

data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun parse(text: String): Version? {
            val match = pattern.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return Version(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
```

<!-- file: $MAIN/update/UpdateChecker.kt -->
```kotlin
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
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) null else parseRelease(body, currentVersion)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            null
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
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew testDebugUnitTest --tests '*update*'`
Expected: 11 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add GitHub release update check"
```

---

### Task 3: Update controller and installer

**Files:**
- Create: `$MAIN/update/UpdateController.kt`, `$MAIN/update/UpdateInstaller.kt`, `$MAIN/update/UpdateDialog.kt`
- Test: `$TEST/update/UpdateControllerTest.kt`

**Interfaces:**
- Consumes: `UpdateInfo`, `Version` (Task 2).
- Produces: `sealed interface UpdateState` (`Idle`, `Checking`, `UpToDate`, `Available(info)`, `Downloading(info, progress)`, `NeedsPermission(info, apk)`, `ReadyToInstall(info, apk)`, `Failed(info, message)`). `class UpdateController(scope, check: suspend () -> UpdateInfo?, download: suspend (UpdateInfo, (Float) -> Unit) -> File, canInstall: () -> Boolean)` with `state: StateFlow<UpdateState>`, `checkNow(manual: Boolean = false)`, `startDownload()`, `permissionMaybeGranted()`, `dismiss()`. `class UpdateInstaller(context, client)` with `suspend download(info, onProgress): File`, `canInstall(): Boolean`, `install(apk: File)`, `installPermissionIntent(): Intent`. `@Composable UpdateDialog(state, onUpdate, onInstall: (File) -> Unit, onOpenPermission, onDismiss)`.

- [ ] **Step 1: Write the failing test**

<!-- file: $TEST/update/UpdateControllerTest.kt -->
```kotlin
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
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*UpdateControllerTest'`
Expected: compilation fails with `Unresolved reference: UpdateController`.

- [ ] **Step 3: Implement the controller, installer and dialog**

<!-- file: $MAIN/update/UpdateController.kt -->
```kotlin
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
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val apk: File) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/** Drives the update flow. Android specifics are injected so the flow can be unit tested. */
class UpdateController(
    private val scope: CoroutineScope,
    private val check: suspend () -> UpdateInfo?,
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
            val info = check()
            _state.value = when {
                info != null -> UpdateState.Available(info)
                manual -> UpdateState.UpToDate
                else -> UpdateState.Idle
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
```

<!-- file: $MAIN/update/UpdateInstaller.kt -->
```kotlin
package io.github.nugggy.makeupmirror.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class UpdateInstaller(private val context: Context, private val client: OkHttpClient) {

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "makeup-mirror-${info.version}.apk")
        val request = Request.Builder().url(info.apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Download was empty")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
        target
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

<!-- file: $MAIN/update/UpdateDialog.kt -->
```kotlin
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
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}
```

- [ ] **Step 4: Run and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*UpdateControllerTest' assembleDebug`
Expected: 6 tests pass and the build succeeds.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add update download and install flow"
```

---

### Task 4: Colour maths and swatch sampling

**Files:**
- Create: `$MAIN/colour/ColourMath.kt`, `$MAIN/colour/ColourSampler.kt`
- Test: `$TEST/colour/ColourMathTest.kt`, `$TEST/colour/ColourSamplerTest.kt`

**Interfaces:**
- Produces: `object ColourMath` with `data class Lab(l, a, b)`, `red/green/blue(argb): Int`, `argb(r, g, b): Int`, `toLab(argb): Lab`, `deltaE2000(x: Lab, y: Lab): Double`, `deltaE(c1: Int, c2: Int): Double`, `parseHex(hex: String): Int`, `toHex(argb: Int): String`, `hueDegrees(lab: Lab): Double`. `object ColourSampler` with `average(pixels: IntArray, tolerance: Int = 40): Int` and `circle(pixels, width, height, cx, cy, radius): IntArray`.

- [ ] **Step 1: Write the failing tests**

<!-- file: $TEST/colour/ColourMathTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.colour

import io.github.nugggy.makeupmirror.colour.ColourMath.Lab
import org.junit.Assert.assertEquals
import org.junit.Test

class ColourMathTest {
    @Test
    fun whiteHasFullLightnessAndNoChroma() {
        val lab = ColourMath.toLab(0xFFFFFFFF.toInt())
        assertEquals(100.0, lab.l, 0.01)
        assertEquals(0.0, lab.a, 0.01)
        assertEquals(0.0, lab.b, 0.01)
    }

    @Test
    fun pureRedMatchesReferenceLab() {
        val lab = ColourMath.toLab(0xFFFF0000.toInt())
        assertEquals(53.24, lab.l, 0.05)
        assertEquals(80.09, lab.a, 0.05)
        assertEquals(67.20, lab.b, 0.05)
    }

    @Test
    fun deltaE2000MatchesSharmaReferenceData() {
        assertEquals(2.0425, ColourMath.deltaE2000(Lab(50.0, 2.6772, -79.7751), Lab(50.0, 0.0, -82.7485)), 1e-4)
        assertEquals(1.0000, ColourMath.deltaE2000(Lab(50.0, 2.5, 0.0), Lab(50.0, 3.1736, 0.5854)), 1e-4)
        assertEquals(27.1492, ColourMath.deltaE2000(Lab(50.0, 2.5, 0.0), Lab(73.0, 25.0, -18.0)), 1e-4)
    }

    @Test
    fun identicalColoursHaveZeroDistance() {
        val c = ColourMath.parseHex("#B3121E")
        assertEquals(0.0, ColourMath.deltaE(c, c), 1e-9)
    }

    @Test
    fun hexRoundTrips() {
        assertEquals("#B3121E", ColourMath.toHex(ColourMath.parseHex("#b3121e")))
        assertEquals(0xFF, ColourMath.red(ColourMath.parseHex("#FF0000")))
    }
}
```

<!-- file: $TEST/colour/ColourSamplerTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.colour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourSamplerTest {
    private val lipstick = ColourMath.argb(180, 30, 50)

    @Test
    fun uniformPatchReturnsItsColour() {
        assertEquals(lipstick, ColourSampler.average(IntArray(50) { lipstick }))
    }

    @Test
    fun glareAndBackgroundAreIgnored() {
        val pixels = IntArray(100) { i ->
            when {
                i < 10 -> ColourMath.argb(255, 255, 255) // glare
                i < 18 -> ColourMath.argb(20, 20, 20) // shadow at the edge
                else -> ColourMath.argb(178 + i % 5, 28 + i % 5, 48 + i % 5)
            }
        }
        val result = ColourSampler.average(pixels)
        assertTrue(ColourMath.deltaE(result, lipstick) < 2.0)
    }

    @Test
    fun circleIsClippedAtImageEdges() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { it }
        val inside = ColourSampler.circle(pixels, width, height, cx = 0, cy = 0, radius = 2)
        // Quarter disc of radius 2 at the corner: (0,0),(1,0),(2,0),(0,1),(1,1),(0,2)
        assertEquals(setOf(0, 1, 2, 10, 11, 20), inside.toSet())
    }
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew testDebugUnitTest --tests '*colour*'`
Expected: compilation fails with `Unresolved reference: ColourMath`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/colour/ColourMath.kt -->
```kotlin
package io.github.nugggy.makeupmirror.colour

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** sRGB (D65) to CIELAB conversion and CIEDE2000 colour difference. Colours are packed ARGB ints. */
object ColourMath {
    data class Lab(val l: Double, val a: Double, val b: Double)

    fun red(argb: Int) = (argb shr 16) and 0xFF
    fun green(argb: Int) = (argb shr 8) and 0xFF
    fun blue(argb: Int) = argb and 0xFF
    fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun parseHex(hex: String): Int {
        val digits = hex.removePrefix("#")
        require(digits.length == 6) { "Expected #RRGGBB but got $hex" }
        return (0xFF000000L or digits.toLong(16)).toInt()
    }

    fun toHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    fun toLab(argb: Int): Lab {
        fun linear(channel: Int): Double {
            val v = channel / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(red(argb))
        val g = linear(green(argb))
        val b = linear(blue(argb))
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun hueDegrees(lab: Lab): Double {
        val h = Math.toDegrees(atan2(lab.b, lab.a))
        return if (h < 0) h + 360 else h
    }

    fun deltaE(c1: Int, c2: Int): Double = deltaE2000(toLab(c1), toLab(c2))

    fun deltaE2000(c1: Lab, c2: Lab): Double {
        val avgL = (c1.l + c2.l) / 2
        val avgC = (hypot(c1.a, c1.b) + hypot(c2.a, c2.b)) / 2
        val g = 0.5 * (1 - sqrt(avgC.pow(7) / (avgC.pow(7) + 25.0.pow(7))))
        val a1p = c1.a * (1 + g)
        val a2p = c2.a * (1 + g)
        val c1p = hypot(a1p, c1.b)
        val c2p = hypot(a2p, c2.b)
        val avgCp = (c1p + c2p) / 2
        fun hp(b: Double, ap: Double): Double {
            if (b == 0.0 && ap == 0.0) return 0.0
            val h = Math.toDegrees(atan2(b, ap))
            return if (h < 0) h + 360 else h
        }
        val h1p = hp(c1.b, a1p)
        val h2p = hp(c2.b, a2p)
        val dLp = c2.l - c1.l
        val dCp = c2p - c1p
        var dhp = h2p - h1p
        if (c1p * c2p == 0.0) dhp = 0.0 else if (dhp > 180) dhp -= 360 else if (dhp < -180) dhp += 360
        val dHp = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2))
        val avgHp = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(Math.toRadians(avgHp - 30)) +
            0.24 * cos(Math.toRadians(2 * avgHp)) +
            0.32 * cos(Math.toRadians(3 * avgHp + 6)) -
            0.20 * cos(Math.toRadians(4 * avgHp - 63))
        val dTheta = 30 * exp(-((avgHp - 275) / 25).pow(2))
        val rc = 2 * sqrt(avgCp.pow(7) / (avgCp.pow(7) + 25.0.pow(7)))
        val sl = 1 + (0.015 * (avgL - 50).pow(2)) / sqrt(20 + (avgL - 50).pow(2))
        val sc = 1 + 0.045 * avgCp
        val sh = 1 + 0.015 * avgCp * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        return sqrt(
            (dLp / sl).pow(2) + (dCp / sc).pow(2) + (dHp / sh).pow(2) + rt * (dCp / sc) * (dHp / sh),
        )
    }
}
```

<!-- file: $MAIN/colour/ColourSampler.kt -->
```kotlin
package io.github.nugggy.makeupmirror.colour

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ColourSampler {
    /**
     * Averages pixels after discarding outliers (glare, shadow, background) that sit more than
     * [tolerance] away from the per-channel median on any channel.
     */
    fun average(pixels: IntArray, tolerance: Int = 40): Int {
        require(pixels.isNotEmpty()) { "No pixels to sample" }
        val mr = median(pixels, ColourMath::red)
        val mg = median(pixels, ColourMath::green)
        val mb = median(pixels, ColourMath::blue)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (p in pixels) {
            val pr = ColourMath.red(p)
            val pg = ColourMath.green(p)
            val pb = ColourMath.blue(p)
            if (abs(pr - mr) <= tolerance && abs(pg - mg) <= tolerance && abs(pb - mb) <= tolerance) {
                r += pr
                g += pg
                b += pb
                n++
            }
        }
        if (n == 0) return ColourMath.argb(mr, mg, mb)
        return ColourMath.argb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Pixels of a row-major [width] x [height] image that fall inside the given circle. */
    fun circle(pixels: IntArray, width: Int, height: Int, cx: Int, cy: Int, radius: Int): IntArray {
        val out = ArrayList<Int>()
        for (y in max(0, cy - radius)..min(height - 1, cy + radius)) {
            for (x in max(0, cx - radius)..min(width - 1, cx + radius)) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= radius * radius) out += pixels[y * width + x]
            }
        }
        return out.toIntArray()
    }

    private fun median(pixels: IntArray, channel: (Int) -> Int): Int {
        val values = IntArray(pixels.size) { channel(pixels[it]) }
        values.sort()
        return values[values.size / 2]
    }
}
```

- [ ] **Step 4: Run and watch them pass**

Run: `./gradlew testDebugUnitTest --tests '*colour*'`
Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add colour maths and swatch sampling"
```

---

### Task 5: Makeup kit data layer

**Files:**
- Create: `$MAIN/kit/Product.kt`, `$MAIN/kit/ProductDao.kt`, `$MAIN/kit/AppDatabase.kt`, `$MAIN/kit/ProductForm.kt`
- Test: `$TEST/kit/ProductFormTest.kt`

**Interfaces:**
- Produces: `@Serializable enum class ProductType(val label: String)` with FOUNDATION, CONCEALER, POWDER, BLUSH, BRONZER, CONTOUR, HIGHLIGHTER, EYESHADOW, EYELINER, MASCARA, BROW, LIPSTICK, LIP_GLOSS, LIP_LINER. `enum class Finish(val label: String)` with MATTE, SATIN, SHIMMER, GLOSS. `@Entity data class Product(id: Long = 0, type: ProductType, name: String, colour: Int, finish: Finish = Finish.MATTE, createdAt: Long = System.currentTimeMillis())`. `ProductDao` with `observeAll(): Flow<List<Product>>`, `suspend get(id): Product?`, `suspend upsert(product): Long`, `suspend delete(product)`. `AppDatabase.create(context)` and `products(): ProductDao`. `ProductForm.defaultName(type, existing): String`.
- Note: Room DAO behaviour needs an Android runtime to test. It is a thin generated layer and is exercised in the manual run in Task 17.

- [ ] **Step 1: Write the failing test**

<!-- file: $TEST/kit/ProductFormTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductFormTest {
    @Test
    fun defaultNameNumbersProductsOfTheSameType() {
        val kit = listOf(
            Product(1, ProductType.BLUSH, "Rose", 0),
            Product(2, ProductType.BLUSH, "Peach", 0),
            Product(3, ProductType.LIPSTICK, "Red", 0),
        )
        assertEquals("Blush 3", ProductForm.defaultName(ProductType.BLUSH, kit))
        assertEquals("Mascara 1", ProductForm.defaultName(ProductType.MASCARA, kit))
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*ProductFormTest'`
Expected: compilation fails with `Unresolved reference: Product`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/kit/Product.kt -->
```kotlin
package io.github.nugggy.makeupmirror.kit

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ProductType(val label: String) {
    FOUNDATION("Foundation"),
    CONCEALER("Concealer"),
    POWDER("Powder"),
    BLUSH("Blush"),
    BRONZER("Bronzer"),
    CONTOUR("Contour"),
    HIGHLIGHTER("Highlighter"),
    EYESHADOW("Eyeshadow"),
    EYELINER("Eyeliner"),
    MASCARA("Mascara"),
    BROW("Brow pencil"),
    LIPSTICK("Lipstick"),
    LIP_GLOSS("Lip gloss"),
    LIP_LINER("Lip liner"),
}

enum class Finish(val label: String) {
    MATTE("Matte"),
    SATIN("Satin"),
    SHIMMER("Shimmer"),
    GLOSS("Gloss"),
}

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ProductType,
    val name: String,
    val colour: Int,
    val finish: Finish = Finish.MATTE,
    val createdAt: Long = System.currentTimeMillis(),
)
```

<!-- file: $MAIN/kit/ProductDao.kt -->
```kotlin
package io.github.nugggy.makeupmirror.kit

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY type, name")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: Long): Product?

    @Upsert
    suspend fun upsert(product: Product): Long

    @Delete
    suspend fun delete(product: Product)
}
```

<!-- file: $MAIN/kit/AppDatabase.kt -->
```kotlin
package io.github.nugggy.makeupmirror.kit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Product::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "makeup-mirror.db").build()
    }
}
```

<!-- file: $MAIN/kit/ProductForm.kt -->
```kotlin
package io.github.nugggy.makeupmirror.kit

object ProductForm {
    /** Name used when the user leaves the name blank, e.g. "Blush 3". */
    fun defaultName(type: ProductType, existing: List<Product>): String =
        "${type.label} ${existing.count { it.type == type } + 1}"
}
```

- [ ] **Step 4: Run and watch it pass, and check Room code generation**

Run: `./gradlew testDebugUnitTest --tests '*ProductFormTest' assembleDebug`
Expected: 1 test passes and the build succeeds. KSP generates `AppDatabase_Impl`, which proves the entity, DAO and enum columns are valid.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add makeup kit storage"
```

---

### Task 6: Look library and lesson instructions

**Files:**
- Create: `$MAIN/face/Region.kt`, `$MAIN/styles/Look.kt`, `$MAIN/styles/LookLibrary.kt`, `$MAIN/styles/DefaultInstructions.kt`, `app/src/main/assets/styles/looks.json`
- Test: `$TEST/styles/LooksJsonTest.kt`

**Interfaces:**
- Consumes: `ProductType` (Task 5), `ColourMath.parseHex` (Task 4).
- Produces: `@Serializable enum class Region` with FACE, UNDER_EYE, CONTOUR, BRONZE, CHEEKS, HIGHLIGHT, LIDS, CREASE, LOWER_LASH_LINE, UPPER_LINER, WING, LASHES, BROWS, LIP_LINE, LIPS, LIP_GLOSS. `@Serializable data class Look(id, name, description, tags: List<String>, image: String, layers: List<LookLayer>, steps: List<LessonStep>)` with `requiredTypes: Set<ProductType>` and `layer(role): LookLayer?`. `@Serializable data class LookLayer(role, type: ProductType, region: Region, colour: String, opacity: Float = 0.5f, optional: Boolean = false)`. `@Serializable data class LessonStep(role, instruction: String? = null, tip: String? = null)`. `LookLibrary.parse(text): List<Look>` and `LookLibrary.load(context): List<Look>`. `DefaultInstructions.forLayer(layer): String`.
- Look image paths are relative to `assets/styles/` (for example `img/natural.jpg`). The images arrive in Task 18.

- [ ] **Step 1: Write the failing test**

<!-- file: $TEST/styles/LooksJsonTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.styles

import io.github.nugggy.makeupmirror.colour.ColourMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LooksJsonTest {
    private val looks = LookLibrary.parse(File("src/main/assets/styles/looks.json").readText())

    @Test
    fun libraryHasAtLeastEighteenUniqueLooks() {
        assertTrue(looks.size >= 18)
        assertEquals(looks.size, looks.map { it.id }.toSet().size)
    }

    @Test
    fun everyLayerHasExactlyOneStepAndEveryStepHasALayer() {
        looks.forEach { look ->
            val roles = look.layers.map { it.role }
            assertEquals("${look.id}: duplicate layer roles", roles.size, roles.toSet().size)
            look.steps.forEach { assertNotNull("${look.id}: step ${it.role} has no layer", look.layer(it.role)) }
            assertEquals("${look.id}: steps must match layers", roles.toSet(), look.steps.map { it.role }.toSet())
            assertEquals("${look.id}: duplicate steps", look.steps.size, look.steps.map { it.role }.toSet().size)
        }
    }

    @Test
    fun coloursAndOpacitiesAreValid() {
        looks.flatMap { it.layers }.forEach { layer ->
            ColourMath.parseHex(layer.colour)
            assertTrue("${layer.role} opacity ${layer.opacity}", layer.opacity in 0.05f..1f)
        }
    }

    @Test
    fun everyLookNeedsAtLeastOneProduct() {
        looks.forEach { assertTrue(it.id, it.requiredTypes.isNotEmpty()) }
    }

    @Test
    fun everyStepHasUsefulInstructions() {
        looks.forEach { look ->
            look.steps.forEach { step ->
                val text = step.instruction ?: DefaultInstructions.forLayer(look.layer(step.role)!!)
                assertTrue("${look.id}/${step.role}", text.length > 30)
            }
        }
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*LooksJsonTest'`
Expected: compilation fails with `Unresolved reference: LookLibrary`.

- [ ] **Step 3: Implement the model, library and default instructions**

<!-- file: $MAIN/face/Region.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import kotlinx.serialization.Serializable

/** Areas of the face a makeup layer can be applied to. Shapes live in [FaceRegions]. */
@Serializable
enum class Region {
    FACE,
    UNDER_EYE,
    CONTOUR,
    BRONZE,
    CHEEKS,
    HIGHLIGHT,
    LIDS,
    CREASE,
    LOWER_LASH_LINE,
    UPPER_LINER,
    WING,
    LASHES,
    BROWS,
    LIP_LINE,
    LIPS,
    LIP_GLOSS,
}
```

<!-- file: $MAIN/styles/Look.kt -->
```kotlin
package io.github.nugggy.makeupmirror.styles

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.ProductType
import kotlinx.serialization.Serializable

@Serializable
data class Look(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val image: String,
    val layers: List<LookLayer>,
    val steps: List<LessonStep>,
) {
    val requiredTypes: Set<ProductType>
        get() = layers.filterNot { it.optional }.map { it.type }.toSet()

    fun layer(role: String): LookLayer? = layers.firstOrNull { it.role == role }
}

/** One coat of one product type on one region. [colour] is the target shade as #RRGGBB. */
@Serializable
data class LookLayer(
    val role: String,
    val type: ProductType,
    val region: Region,
    val colour: String,
    val opacity: Float = 0.5f,
    val optional: Boolean = false,
)

/** A lesson step for the layer with the same [role]. A null [instruction] uses [DefaultInstructions]. */
@Serializable
data class LessonStep(
    val role: String,
    val instruction: String? = null,
    val tip: String? = null,
)
```

<!-- file: $MAIN/styles/LookLibrary.kt -->
```kotlin
package io.github.nugggy.makeupmirror.styles

import android.content.Context
import kotlinx.serialization.json.Json

object LookLibrary {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<Look> = json.decodeFromString(text)

    fun load(context: Context): List<Look> =
        context.assets.open("styles/looks.json").bufferedReader().use { parse(it.readText()) }
}
```

<!-- file: $MAIN/styles/DefaultInstructions.kt -->
```kotlin
package io.github.nugggy.makeupmirror.styles

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.ProductType

/** Standard how-to text for each product, used when a look does not give its own. */
object DefaultInstructions {
    private val byTypeAndRegion = mapOf(
        (ProductType.EYESHADOW to Region.LIDS) to
            "Pat eyeshadow across your eyelid, from your lash line up to the crease, with a flat brush or your fingertip.",
        (ProductType.EYESHADOW to Region.CREASE) to
            "With a fluffy brush, blend this shade into the fold of your eyelid using small side-to-side windscreen-wiper movements.",
        (ProductType.EYESHADOW to Region.LOWER_LASH_LINE) to
            "Smudge a little shadow along your lower lash line with a small brush. Start at the outer corner and fade it out before the middle.",
        (ProductType.EYELINER to Region.WING) to
            "Look straight ahead. Draw a short flick from your outer corner pointing towards the end of your brow, then join it back to your lash line and fill it in.",
    )

    private val byType = mapOf(
        ProductType.FOUNDATION to
            "Dot foundation on your forehead, cheeks, nose and chin. Blend it outwards with a damp sponge or brush until there are no edges, especially along your jaw and hairline.",
        ProductType.CONCEALER to
            "Dab concealer under each eye in a small upside-down triangle and gently tap it in with your ring finger. Cover any spots the same way.",
        ProductType.POWDER to
            "Press a light layer of powder onto your forehead, nose and chin so your base lasts all day.",
        ProductType.CONTOUR to
            "Find the hollow under your cheekbone by sucking in your cheeks. Sweep contour along it from your ear towards the middle of your cheek, then blend upwards until there are no lines.",
        ProductType.BRONZER to
            "With a big fluffy brush, sweep bronzer in a 3 shape on each side: along your forehead near the hairline, under your cheekbone and along your jaw.",
        ProductType.BLUSH to
            "Smile and tap blush onto the round apples of your cheeks, then blend it up and back towards your ears.",
        ProductType.HIGHLIGHTER to
            "Tap highlighter onto the tops of your cheekbones and a little down the bridge of your nose, where light naturally hits.",
        ProductType.EYESHADOW to
            "Pat eyeshadow onto your eyelid with a flat brush or your fingertip, then soften the edges.",
        ProductType.EYELINER to
            "Rest your elbow on a table to keep your hand steady. Draw a thin line along your upper lash line from the inner corner to the outer corner, as close to the lashes as you can.",
        ProductType.MASCARA to
            "Look down into the mirror, wiggle the wand at the root of your top lashes and sweep up to the tips. Add a second coat once the first is dry.",
        ProductType.BROW to
            "Brush your brows upwards. Fill any gaps with short, light strokes that follow the way your hairs grow. Keep the front softer than the tail.",
        ProductType.LIP_LINER to
            "Outline your lips with liner. Start with a small X at your Cupid's bow and work out to the corners, then soften the line so there's no hard edge.",
        ProductType.LIPSTICK to
            "Start in the centre of your top lip and work out to each corner, then fill in your bottom lip. Press your lips together and tidy the edges.",
        ProductType.LIP_GLOSS to
            "Dab gloss onto the centre of your lips and press them together to spread it evenly.",
    )

    fun forLayer(layer: LookLayer): String =
        byTypeAndRegion[layer.type to layer.region] ?: byType.getValue(layer.type)
}
```

- [ ] **Step 4: Write the look library**

Steps are ordered base, then eyes, then cheeks, then lips. Optional layers are skipped when the kit lacks that product.

<!-- file: app/src/main/assets/styles/looks.json -->
```json
[
  {
    "id": "natural",
    "name": "No-makeup makeup",
    "description": "Even skin, tidy brows and a hint of colour. You, only fresher.",
    "tags": ["subtle", "neutral"],
    "image": "img/natural.jpg",
    "layers": [
      {"role": "conceal", "type": "CONCEALER", "region": "UNDER_EYE", "colour": "#E8C4A8", "opacity": 0.35},
      {"role": "brows", "type": "BROW", "region": "BROWS", "colour": "#6B4A3A", "opacity": 0.35, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#1E1A1A", "opacity": 0.5, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#E8A3A0", "opacity": 0.25, "optional": true},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#D99A94", "opacity": 0.3, "optional": true}
    ],
    "steps": [
      {"role": "conceal", "instruction": "Dab a tiny amount of concealer only where you need it, such as under your eyes or on a spot, and tap it in with your ring finger until it disappears.", "tip": "Less is more. You want your real skin to show through."},
      {"role": "brows"},
      {"role": "lashes", "instruction": "Wiggle the mascara wand at the root of your top lashes and sweep up to the tips. One coat is enough for this look."},
      {"role": "blush", "instruction": "Smile and tap the smallest amount of blush onto the apples of your cheeks, then blend until it looks like a natural flush."},
      {"role": "gloss"}
    ]
  },
  {
    "id": "school-day",
    "name": "School-day subtle",
    "description": "Quick, light and hard to spot. Mascara, a touch of colour and tinted gloss.",
    "tags": ["subtle", "gloss"],
    "image": "img/school-day.jpg",
    "layers": [
      {"role": "conceal", "type": "CONCEALER", "region": "UNDER_EYE", "colour": "#E8C4A8", "opacity": 0.3, "optional": true},
      {"role": "brows", "type": "BROW", "region": "BROWS", "colour": "#6B4A3A", "opacity": 0.3, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#2A2220", "opacity": 0.45},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#EBA9A5", "opacity": 0.2, "optional": true},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#E9B2AE", "opacity": 0.3}
    ],
    "steps": [
      {"role": "conceal"},
      {"role": "brows", "instruction": "Brush your brows up and fill only the gaps with a few light strokes. Keep it soft so it looks like your natural brows."},
      {"role": "lashes", "instruction": "Apply one coat of mascara to your top lashes only, wiggling at the roots and sweeping up.", "tip": "Wipe the wand on the tube opening first so it doesn't clump."},
      {"role": "blush", "instruction": "Tap a very small amount of blush onto the apples of your cheeks and blend it well."},
      {"role": "gloss"}
    ]
  },
  {
    "id": "dewy",
    "name": "Fresh and dewy",
    "description": "Glowing skin, rosy cheeks and shiny lips, like you've just come back from a walk.",
    "tags": ["subtle", "highlight", "gloss"],
    "image": "img/dewy.jpg",
    "layers": [
      {"role": "base", "type": "FOUNDATION", "region": "FACE", "colour": "#E2B99A", "opacity": 0.2},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#1E1A1A", "opacity": 0.45, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#F2A0A0", "opacity": 0.3, "optional": true},
      {"role": "glow", "type": "HIGHLIGHTER", "region": "HIGHLIGHT", "colour": "#FFF1D6", "opacity": 0.45},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#E7A1A0", "opacity": 0.35, "optional": true}
    ],
    "steps": [
      {"role": "base", "instruction": "Mix a drop of foundation with moisturiser if you have it, then press it on with your fingers so skin still shows through. Keep it sheer."},
      {"role": "lashes"},
      {"role": "blush", "instruction": "Tap blush onto the apples of your cheeks with your fingertips for a soft, glowing flush."},
      {"role": "glow", "tip": "Tap with your fingertip rather than sweeping for a wet-look glow."},
      {"role": "gloss"}
    ]
  },
  {
    "id": "soft-glam",
    "name": "Soft glam",
    "description": "Polished and pretty. Warm brown eyes, sculpted cheeks and a rosy-nude lip.",
    "tags": ["neutral", "highlight", "contour"],
    "image": "img/soft-glam.jpg",
    "layers": [
      {"role": "base", "type": "FOUNDATION", "region": "FACE", "colour": "#DDB394", "opacity": 0.3},
      {"role": "brows", "type": "BROW", "region": "BROWS", "colour": "#5E4033", "opacity": 0.4, "optional": true},
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#C9A28A", "opacity": 0.4},
      {"role": "crease", "type": "EYESHADOW", "region": "CREASE", "colour": "#8F6250", "opacity": 0.35},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#2A1E1A", "opacity": 0.7, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#141010", "opacity": 0.55},
      {"role": "contour", "type": "CONTOUR", "region": "CONTOUR", "colour": "#8E6450", "opacity": 0.3, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#D88C83", "opacity": 0.3, "optional": true},
      {"role": "glow", "type": "HIGHLIGHTER", "region": "HIGHLIGHT", "colour": "#F8E3C8", "opacity": 0.4, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#B97A72", "opacity": 0.5}
    ],
    "steps": [
      {"role": "base"},
      {"role": "brows"},
      {"role": "crease", "tip": "Do the crease first. It's easier to blend before the lid colour goes on."},
      {"role": "lid"},
      {"role": "liner"},
      {"role": "lashes"},
      {"role": "contour"},
      {"role": "blush"},
      {"role": "glow"},
      {"role": "lip"}
    ]
  },
  {
    "id": "classic-red",
    "name": "Classic red lip",
    "description": "Clean liner and a statement red lip. Timeless.",
    "tags": ["bold-lip", "cool", "wing"],
    "image": "img/classic-red.jpg",
    "layers": [
      {"role": "base", "type": "FOUNDATION", "region": "FACE", "colour": "#E0B898", "opacity": 0.25, "optional": true},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.8},
      {"role": "wing", "type": "EYELINER", "region": "WING", "colour": "#111111", "opacity": 0.8},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.55, "optional": true},
      {"role": "lipliner", "type": "LIP_LINER", "region": "LIP_LINE", "colour": "#A0141E", "opacity": 0.6, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#B3121E", "opacity": 0.65}
    ],
    "steps": [
      {"role": "base"},
      {"role": "liner"},
      {"role": "wing", "tip": "Keep the wing small. With a red lip, the lip is the star."},
      {"role": "lashes"},
      {"role": "lipliner", "instruction": "Line your lips with the red liner, then colour in the whole lip lightly with it. This stops red lipstick from bleeding or fading."},
      {"role": "lip", "instruction": "Apply red lipstick from the centre out, then blot on a tissue and apply a second layer. Clean any edges with a cotton bud and a dab of concealer.", "tip": "Red shows every mistake, so go slowly and use a mirror in good light."}
    ]
  },
  {
    "id": "berry",
    "name": "Berry lip",
    "description": "A deep berry lip with soft eyes. Great for winter and evenings.",
    "tags": ["bold-lip", "cool"],
    "image": "img/berry.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#A8838C", "opacity": 0.35, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#141010", "opacity": 0.5, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#C97B8E", "opacity": 0.28, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#7E1F3E", "opacity": 0.6}
    ],
    "steps": [
      {"role": "lid", "instruction": "Wash a soft mauve shade over your lids with a fluffy brush. Keep it sheer so it doesn't compete with your lips."},
      {"role": "lashes"},
      {"role": "blush"},
      {"role": "lip", "tip": "For a softer, stained look, tap the lipstick on with your fingertip instead."}
    ]
  },
  {
    "id": "nude-glam",
    "name": "Nude glam",
    "description": "Defined, warm and grown-up. Sculpted cheeks with a lined nude lip.",
    "tags": ["neutral", "warm", "contour"],
    "image": "img/nude-glam.jpg",
    "layers": [
      {"role": "base", "type": "FOUNDATION", "region": "FACE", "colour": "#D9AE8C", "opacity": 0.3, "optional": true},
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#C8A07F", "opacity": 0.35},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#141010", "opacity": 0.55, "optional": true},
      {"role": "contour", "type": "CONTOUR", "region": "CONTOUR", "colour": "#8A6048", "opacity": 0.35},
      {"role": "lipliner", "type": "LIP_LINER", "region": "LIP_LINE", "colour": "#9A6450", "opacity": 0.5, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#B88570", "opacity": 0.5}
    ],
    "steps": [
      {"role": "base"},
      {"role": "lid"},
      {"role": "lashes"},
      {"role": "contour"},
      {"role": "lipliner", "tip": "Choose a liner a shade deeper than your lipstick for definition."},
      {"role": "lip"}
    ]
  },
  {
    "id": "sun-kissed",
    "name": "Sun-kissed bronze",
    "description": "Warm, golden and glowy, like a weekend at the beach.",
    "tags": ["warm", "highlight"],
    "image": "img/sun-kissed.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#B9804F", "opacity": 0.35, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#2A1E1A", "opacity": 0.5, "optional": true},
      {"role": "bronze", "type": "BRONZER", "region": "BRONZE", "colour": "#A86B45", "opacity": 0.35},
      {"role": "glow", "type": "HIGHLIGHTER", "region": "HIGHLIGHT", "colour": "#F7DDB0", "opacity": 0.45, "optional": true},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#D18E74", "opacity": 0.35, "optional": true}
    ],
    "steps": [
      {"role": "lid", "instruction": "Sweep a warm bronze shadow over your lid with your fingertip. Blend the edges so it melts into your skin."},
      {"role": "lashes"},
      {"role": "bronze", "tip": "Tap off the extra bronzer from your brush first. It's easier to add more than take it away."},
      {"role": "glow"},
      {"role": "gloss"}
    ]
  },
  {
    "id": "rosy",
    "name": "Rosy romantic",
    "description": "Pink cheeks, soft pink eyes and a rose lip. Sweet and pretty.",
    "tags": ["cool", "blush-forward"],
    "image": "img/rosy.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#D9A1AE", "opacity": 0.35, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#141010", "opacity": 0.5, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#E07A93", "opacity": 0.4},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#C9667E", "opacity": 0.5}
    ],
    "steps": [
      {"role": "lid"},
      {"role": "lashes"},
      {"role": "blush", "instruction": "Smile and tap blush onto the apples of your cheeks, then blend it outwards across your cheeks. Build it up a little more than usual for this look."},
      {"role": "lip"}
    ]
  },
  {
    "id": "peachy",
    "name": "Peachy fresh",
    "description": "Peach cheeks and a juicy peach lip. Bright and summery.",
    "tags": ["warm", "subtle", "gloss"],
    "image": "img/peachy.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#E8B58F", "opacity": 0.3, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#2A1E1A", "opacity": 0.45, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#F2A07B", "opacity": 0.35},
      {"role": "glow", "type": "HIGHLIGHTER", "region": "HIGHLIGHT", "colour": "#FCE3C4", "opacity": 0.35, "optional": true},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#EE9F86", "opacity": 0.4}
    ],
    "steps": [
      {"role": "lid"},
      {"role": "lashes"},
      {"role": "blush"},
      {"role": "glow"},
      {"role": "gloss"}
    ]
  },
  {
    "id": "smoky",
    "name": "Classic smoky eye",
    "description": "Dark, blended, smouldering eyes with a quiet nude lip.",
    "tags": ["smoky", "cool"],
    "image": "img/smoky.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#3A3A40", "opacity": 0.55},
      {"role": "crease", "type": "EYESHADOW", "region": "CREASE", "colour": "#6E6670", "opacity": 0.4},
      {"role": "lower", "type": "EYESHADOW", "region": "LOWER_LASH_LINE", "colour": "#3A3A40", "opacity": 0.45},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.8, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.6},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#C2A49A", "opacity": 0.4, "optional": true}
    ],
    "steps": [
      {"role": "crease", "instruction": "Blend the medium grey shade into your crease and slightly above it with a fluffy brush. This is the soft cloud the dark shade fades into."},
      {"role": "lid", "instruction": "Pack the darkest shade on your lid, close to your lashes, then blend it upwards so it fades softly into the crease colour.", "tip": "Hold a tissue under your eye to catch any fallout."},
      {"role": "lower"},
      {"role": "liner", "instruction": "Line your upper lash line, then smudge the line with a small brush so there are no sharp edges."},
      {"role": "lashes"},
      {"role": "lip"}
    ]
  },
  {
    "id": "brown-smoky",
    "name": "Brown smoky eye",
    "description": "A warmer, softer smoky eye that works for day or night.",
    "tags": ["smoky", "warm"],
    "image": "img/brown-smoky.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#5A3A2A", "opacity": 0.5},
      {"role": "crease", "type": "EYESHADOW", "region": "CREASE", "colour": "#9B6B4C", "opacity": 0.4},
      {"role": "lower", "type": "EYESHADOW", "region": "LOWER_LASH_LINE", "colour": "#5A3A2A", "opacity": 0.4},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#3A2418", "opacity": 0.7, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#141010", "opacity": 0.55},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#B07C6A", "opacity": 0.4, "optional": true}
    ],
    "steps": [
      {"role": "crease"},
      {"role": "lid", "instruction": "Press the deep brown onto your lid near the lashes, then blend upwards into the crease colour until you can't see where one ends."},
      {"role": "lower"},
      {"role": "liner"},
      {"role": "lashes"},
      {"role": "lip"}
    ]
  },
  {
    "id": "cut-crease",
    "name": "Cut crease",
    "description": "A sharp, bright lid under a dark crease. Dramatic and photo-ready.",
    "tags": ["cut-crease", "wing"],
    "image": "img/cut-crease.jpg",
    "layers": [
      {"role": "base", "type": "FOUNDATION", "region": "FACE", "colour": "#DDB394", "opacity": 0.3, "optional": true},
      {"role": "crease", "type": "EYESHADOW", "region": "CREASE", "colour": "#7A5040", "opacity": 0.45},
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#F0D2B8", "opacity": 0.5},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.8},
      {"role": "wing", "type": "EYELINER", "region": "WING", "colour": "#111111", "opacity": 0.8, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.6, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#A9686A", "opacity": 0.5, "optional": true}
    ],
    "steps": [
      {"role": "base"},
      {"role": "crease", "instruction": "Blend the darker shade into the fold of your eye and a little above it. Build it up until it's clearly defined."},
      {"role": "lid", "instruction": "Look straight ahead. With a flat brush, paint concealer or your lightest shadow in a curve just above the fold of your eye, then fill in the lid below it. Keep the curve sharp.", "tip": "Keep your eyes open and looking ahead while you cut the crease, so the line sits in the right place."},
      {"role": "liner"},
      {"role": "wing"},
      {"role": "lashes"},
      {"role": "lip"}
    ]
  },
  {
    "id": "winged-liner",
    "name": "Winged liner",
    "description": "A classic cat-eye flick with fresh skin. Simple but striking.",
    "tags": ["wing"],
    "image": "img/winged-liner.jpg",
    "layers": [
      {"role": "brows", "type": "BROW", "region": "BROWS", "colour": "#5E4033", "opacity": 0.35, "optional": true},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.85},
      {"role": "wing", "type": "EYELINER", "region": "WING", "colour": "#111111", "opacity": 0.85},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.55},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#D99A94", "opacity": 0.3, "optional": true}
    ],
    "steps": [
      {"role": "brows"},
      {"role": "wing", "instruction": "Start with the wing. Look straight ahead and draw a short line from your outer corner pointing towards the end of your brow.", "tip": "Use a piece of sticky tape as a guide if your hand wobbles."},
      {"role": "liner", "instruction": "Now draw along your upper lash line from the inner corner, getting slightly thicker as you go, and join it to the wing. Fill in any gaps."},
      {"role": "lashes"},
      {"role": "gloss"}
    ]
  },
  {
    "id": "graphic-liner",
    "name": "Graphic liner",
    "description": "A long wing with a floating line above the crease. Bold and artsy.",
    "tags": ["wing", "festival"],
    "image": "img/graphic-liner.jpg",
    "layers": [
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.85},
      {"role": "wing", "type": "EYELINER", "region": "WING", "colour": "#111111", "opacity": 0.85},
      {"role": "floating", "type": "EYELINER", "region": "CREASE", "colour": "#111111", "opacity": 0.55},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.5, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#C2A49A", "opacity": 0.4, "optional": true}
    ],
    "steps": [
      {"role": "liner"},
      {"role": "wing", "instruction": "Draw a longer, sharper wing than usual, angled towards the end of your brow."},
      {"role": "floating", "instruction": "With your eyes open, trace a thin line just above the fold of your eyelid, from about a third of the way in out to meet the tip of your wing.", "tip": "Draw it in small dashes first, then join them up."},
      {"role": "lashes"},
      {"role": "lip"}
    ]
  },
  {
    "id": "glitter-party",
    "name": "Glitter party",
    "description": "Sparkly gold lids, glowing cheeks and a pop of colour. For birthdays and discos.",
    "tags": ["highlight", "festival"],
    "image": "img/glitter-party.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#D9B36C", "opacity": 0.55},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.75, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.6},
      {"role": "glow", "type": "HIGHLIGHTER", "region": "HIGHLIGHT", "colour": "#FFF0D0", "opacity": 0.5, "optional": true},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#A84D5D", "opacity": 0.5, "optional": true}
    ],
    "steps": [
      {"role": "lid", "instruction": "Press (don't sweep) the shimmery shade onto the centre of your lid with your fingertip, then tap it out towards the corners.", "tip": "A dab of lip balm on the lid first makes shimmer stick and sparkle more."},
      {"role": "liner"},
      {"role": "lashes"},
      {"role": "glow"},
      {"role": "lip"}
    ]
  },
  {
    "id": "pink-monochrome",
    "name": "Soft pink monochrome",
    "description": "The same soft pink on eyes, cheeks and lips. Easy and matching.",
    "tags": ["cool", "subtle", "blush-forward"],
    "image": "img/pink-monochrome.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#E8A6B6", "opacity": 0.35},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#2A2220", "opacity": 0.45, "optional": true},
      {"role": "blush", "type": "BLUSH", "region": "CHEEKS", "colour": "#E58FA5", "opacity": 0.35},
      {"role": "lip", "type": "LIPSTICK", "region": "LIPS", "colour": "#D9798F", "opacity": 0.45}
    ],
    "steps": [
      {"role": "lid", "instruction": "Tap your pink shade over your lids with a fingertip and blend the edges. If your blush is a cream, you can use it here too.", "tip": "Using one colour everywhere is what makes this look work."},
      {"role": "lashes"},
      {"role": "blush"},
      {"role": "lip", "instruction": "Tap your pink lipstick onto your lips with your fingertip for a soft, blurred finish that matches your cheeks."}
    ]
  },
  {
    "id": "festival",
    "name": "Festival colour",
    "description": "Bright blue lids with a purple pop underneath. Fun and loud.",
    "tags": ["festival", "highlight"],
    "image": "img/festival.jpg",
    "layers": [
      {"role": "lid", "type": "EYESHADOW", "region": "LIDS", "colour": "#3FA7C9", "opacity": 0.5},
      {"role": "lower", "type": "EYESHADOW", "region": "LOWER_LASH_LINE", "colour": "#B05BC8", "opacity": 0.45, "optional": true},
      {"role": "liner", "type": "EYELINER", "region": "UPPER_LINER", "colour": "#111111", "opacity": 0.75, "optional": true},
      {"role": "lashes", "type": "MASCARA", "region": "LASHES", "colour": "#111111", "opacity": 0.55},
      {"role": "glow", "type": "HIGHLIGHTER", "region": "HIGHLIGHT", "colour": "#FFF0D0", "opacity": 0.45, "optional": true},
      {"role": "gloss", "type": "LIP_GLOSS", "region": "LIPS", "colour": "#E7A1A0", "opacity": 0.3, "optional": true}
    ],
    "steps": [
      {"role": "lid", "instruction": "Pack your brightest shadow all over your lid with a flat brush, then soften the top edge with a clean fluffy brush."},
      {"role": "lower", "instruction": "Smudge a contrasting colour along your lower lash line, starting at the outer corner."},
      {"role": "liner"},
      {"role": "lashes"},
      {"role": "glow"},
      {"role": "gloss"}
    ]
  }
]
```

That is 18 looks.

- [ ] **Step 5: Run and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*LooksJsonTest'`
Expected: 5 tests pass. If `libraryHasAtLeastEighteenUniqueLooks` fails, count the entries in the JSON.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "Add look library with lesson steps"
```

---

### Task 7: Face regions and geometry

**Files:**
- Create: `$MAIN/face/Pt.kt`, `$MAIN/face/FaceRegions.kt`, `$MAIN/face/RegionGeometry.kt`
- Test: `$TEST/face/RegionGeometryTest.kt`

**Interfaces:**
- Consumes: `Region` (Task 6).
- Produces: `data class Pt(x: Float, y: Float)` with `+`, `-`, `* Float`, `dist(o)`, `normalised()`, `rotate(rad: Double, about: Pt)`. `sealed interface RegionShape` (`Polygon(outline, holes)`, `Blob(centre, towards, radius, stretch)`, `Stroke(path, width)`, `Wing(outerCorner, lidPoint, browTail, length)`). Sizes are fractions of face width. `FaceRegions.shapes(region): List<RegionShape>` plus landmark index lists. `sealed interface Resolved` (`Poly(points, holes)`, `Ellipse(centre, rx, ry, angleDeg)`, `Line(points, width)`). `RegionGeometry.faceWidth(pts): Float`, `RegionGeometry.resolve(region, pts): List<Resolved>`, `RegionGeometry.resolve(shape, pts, faceWidth): Resolved`.
- Landmark indices are MediaPipe Face Mesh indices (478 points including irises). "Right" means the subject's right, which appears on the left of an un-mirrored image.

- [ ] **Step 1: Write the failing test**

<!-- file: $TEST/face/RegionGeometryTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionGeometryTest {
    private fun blankFace(): MutableList<Pt> = MutableList(478) { Pt(0f, 0f) }.also {
        it[234] = Pt(0f, 0f)
        it[454] = Pt(100f, 0f)
    }

    @Test
    fun everyRegionHasShapesWithValidIndices() {
        Region.entries.forEach { region ->
            val shapes = FaceRegions.shapes(region)
            assertTrue("$region has no shapes", shapes.isNotEmpty())
            val indices = shapes.flatMap { shape ->
                when (shape) {
                    is RegionShape.Polygon -> shape.outline + shape.holes.flatten()
                    is RegionShape.Blob -> listOf(shape.centre, shape.towards)
                    is RegionShape.Stroke -> shape.path
                    is RegionShape.Wing -> listOf(shape.outerCorner, shape.lidPoint, shape.browTail)
                }
            }
            assertTrue("$region index out of range", indices.all { it in 0 until 478 })
        }
    }

    @Test
    fun blobScalesWithFaceWidthAndPointsTowardsTarget() {
        val pts = blankFace()
        pts[50] = Pt(50f, 50f)
        pts[34] = Pt(50f, 10f)
        val e = RegionGeometry.resolve(RegionShape.Blob(50, 34, radius = 0.1f, stretch = 2f), pts, 100f) as Resolved.Ellipse
        assertEquals(20f, e.rx, 1e-4f)
        assertEquals(10f, e.ry, 1e-4f)
        assertEquals(-90f, e.angleDeg, 1e-3f)
    }

    @Test
    fun wingTipPointsFromOuterCornerTowardsBrowTail() {
        val pts = blankFace()
        pts[33] = Pt(10f, 50f)
        pts[246] = Pt(14f, 47f)
        pts[46] = Pt(0f, 40f)
        val wing = RegionGeometry.resolve(RegionShape.Wing(33, 246, 46, length = 0.07f), pts, 100f) as Resolved.Poly
        val tip = wing.points[1]
        assertEquals(7f, tip.dist(pts[33]), 1e-3f)
        assertTrue(tip.x < pts[33].x && tip.y < pts[33].y)
    }

    @Test
    fun strokeWidthScalesWithFaceWidth() {
        val pts = blankFace()
        val line = RegionGeometry.resolve(RegionShape.Stroke(listOf(1, 2), 0.01f), pts, 250f) as Resolved.Line
        assertEquals(2.5f, line.width, 1e-4f)
    }

    @Test
    fun lipsHaveTheMouthOpeningAsAHole() {
        val poly = RegionGeometry.resolve(Region.LIPS, blankFace()).single() as Resolved.Poly
        assertEquals(FaceRegions.LIPS_OUTER.size, poly.points.size)
        assertEquals(1, poly.holes.size)
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*RegionGeometryTest'`
Expected: compilation fails with `Unresolved reference: Pt`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/face/Pt.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(s: Float) = Pt(x * s, y * s)

    fun dist(o: Pt): Float = hypot(x - o.x, y - o.y)

    fun normalised(): Pt {
        val length = hypot(x, y)
        return if (length == 0f) this else Pt(x / length, y / length)
    }

    fun rotate(rad: Double, about: Pt): Pt {
        val c = cos(rad)
        val s = sin(rad)
        val dx = x - about.x
        val dy = y - about.y
        return Pt((about.x + dx * c - dy * s).toFloat(), (about.y + dx * s + dy * c).toFloat())
    }
}
```

<!-- file: $MAIN/face/FaceRegions.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

/** Region shapes in terms of landmark indices. Sizes are fractions of the face width. */
sealed interface RegionShape {
    data class Polygon(val outline: List<Int>, val holes: List<List<Int>> = emptyList()) : RegionShape
    data class Blob(val centre: Int, val towards: Int, val radius: Float, val stretch: Float = 1f) : RegionShape
    data class Stroke(val path: List<Int>, val width: Float) : RegionShape
    data class Wing(val outerCorner: Int, val lidPoint: Int, val browTail: Int, val length: Float) : RegionShape
}

object FaceRegions {
    val FACE_OVAL = listOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
    )
    val LIPS_OUTER = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185)
    val LIPS_INNER = listOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191)
    val RIGHT_EYE = listOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7)
    val LEFT_EYE = listOf(263, 466, 388, 387, 386, 385, 384, 398, 362, 382, 381, 380, 374, 373, 390, 249)
    val RIGHT_UPPER_LID = listOf(33, 246, 161, 160, 159, 158, 157, 173, 133)
    val LEFT_UPPER_LID = listOf(263, 466, 388, 387, 386, 385, 384, 398, 362)
    val RIGHT_LOWER_LID = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133)
    val LEFT_LOWER_LID = listOf(263, 249, 390, 373, 374, 380, 381, 382, 362)
    val RIGHT_CREASE = listOf(113, 225, 224, 223, 222, 221, 189)
    val LEFT_CREASE = listOf(342, 445, 444, 443, 442, 441, 413)
    val RIGHT_BROW = listOf(46, 53, 52, 65, 55, 107, 66, 105, 63, 70)
    val LEFT_BROW = listOf(276, 283, 282, 295, 285, 336, 296, 334, 293, 300)
    private val RIGHT_UNDER_EYE = RIGHT_LOWER_LID.reversed() + listOf(111, 117, 118, 119, 120, 121, 128)
    private val LEFT_UNDER_EYE = LEFT_LOWER_LID.reversed() + listOf(340, 346, 347, 348, 349, 350, 357)

    fun shapes(region: Region): List<RegionShape> = when (region) {
        Region.FACE -> listOf(RegionShape.Polygon(FACE_OVAL, listOf(RIGHT_EYE, LEFT_EYE, LIPS_OUTER)))
        Region.UNDER_EYE -> listOf(RegionShape.Polygon(RIGHT_UNDER_EYE), RegionShape.Polygon(LEFT_UNDER_EYE))
        Region.CONTOUR -> listOf(
            RegionShape.Blob(147, 234, radius = 0.055f, stretch = 2.4f),
            RegionShape.Blob(376, 454, radius = 0.055f, stretch = 2.4f),
        )
        Region.BRONZE -> listOf(
            RegionShape.Blob(54, 103, radius = 0.06f, stretch = 1.5f),
            RegionShape.Blob(284, 332, radius = 0.06f, stretch = 1.5f),
            RegionShape.Blob(147, 234, radius = 0.06f, stretch = 2.2f),
            RegionShape.Blob(376, 454, radius = 0.06f, stretch = 2.2f),
            RegionShape.Blob(172, 136, radius = 0.05f, stretch = 2f),
            RegionShape.Blob(397, 365, radius = 0.05f, stretch = 2f),
        )
        Region.CHEEKS -> listOf(
            RegionShape.Blob(50, 34, radius = 0.085f, stretch = 1.35f),
            RegionShape.Blob(280, 264, radius = 0.085f, stretch = 1.35f),
        )
        Region.HIGHLIGHT -> listOf(
            RegionShape.Blob(117, 34, radius = 0.045f, stretch = 2f),
            RegionShape.Blob(346, 264, radius = 0.045f, stretch = 2f),
            RegionShape.Stroke(listOf(168, 6, 197, 195), width = 0.018f),
        )
        Region.LIDS -> listOf(
            RegionShape.Polygon(RIGHT_UPPER_LID + RIGHT_CREASE.reversed()),
            RegionShape.Polygon(LEFT_UPPER_LID + LEFT_CREASE.reversed()),
        )
        Region.CREASE -> listOf(
            RegionShape.Stroke(RIGHT_CREASE, width = 0.035f),
            RegionShape.Stroke(LEFT_CREASE, width = 0.035f),
        )
        Region.LOWER_LASH_LINE -> listOf(
            RegionShape.Stroke(RIGHT_LOWER_LID.take(6), width = 0.012f),
            RegionShape.Stroke(LEFT_LOWER_LID.take(6), width = 0.012f),
        )
        Region.UPPER_LINER -> listOf(
            RegionShape.Stroke(RIGHT_UPPER_LID.reversed(), width = 0.009f),
            RegionShape.Stroke(LEFT_UPPER_LID.reversed(), width = 0.009f),
        )
        Region.WING -> listOf(
            RegionShape.Wing(33, 246, 46, length = 0.07f),
            RegionShape.Wing(263, 466, 276, length = 0.07f),
        )
        Region.LASHES -> listOf(
            RegionShape.Stroke(RIGHT_UPPER_LID, width = 0.016f),
            RegionShape.Stroke(LEFT_UPPER_LID, width = 0.016f),
        )
        Region.BROWS -> listOf(RegionShape.Polygon(RIGHT_BROW), RegionShape.Polygon(LEFT_BROW))
        Region.LIP_LINE -> listOf(RegionShape.Stroke(LIPS_OUTER + LIPS_OUTER.first(), width = 0.008f))
        Region.LIPS -> listOf(RegionShape.Polygon(LIPS_OUTER, listOf(LIPS_INNER)))
        Region.LIP_GLOSS -> listOf(RegionShape.Blob(16, 291, radius = 0.03f, stretch = 2.2f))
    }
}
```

<!-- file: $MAIN/face/RegionGeometry.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import kotlin.math.atan2

/** A region shape resolved to concrete coordinates, ready to draw. */
sealed interface Resolved {
    data class Poly(val points: List<Pt>, val holes: List<List<Pt>>) : Resolved
    data class Ellipse(val centre: Pt, val rx: Float, val ry: Float, val angleDeg: Float) : Resolved
    data class Line(val points: List<Pt>, val width: Float) : Resolved
}

object RegionGeometry {
    fun faceWidth(pts: List<Pt>): Float = pts[234].dist(pts[454])

    fun resolve(region: Region, pts: List<Pt>): List<Resolved> {
        val width = faceWidth(pts)
        return FaceRegions.shapes(region).map { resolve(it, pts, width) }
    }

    fun resolve(shape: RegionShape, pts: List<Pt>, faceWidth: Float): Resolved = when (shape) {
        is RegionShape.Polygon -> Resolved.Poly(shape.outline.map(pts::get), shape.holes.map { hole -> hole.map(pts::get) })
        is RegionShape.Blob -> {
            val centre = pts[shape.centre]
            val towards = pts[shape.towards]
            val angle = Math.toDegrees(atan2((towards.y - centre.y).toDouble(), (towards.x - centre.x).toDouble())).toFloat()
            val r = shape.radius * faceWidth
            Resolved.Ellipse(centre, r * shape.stretch, r, angle)
        }
        is RegionShape.Stroke -> Resolved.Line(shape.path.map(pts::get), shape.width * faceWidth)
        is RegionShape.Wing -> {
            val outer = pts[shape.outerCorner]
            val direction = (pts[shape.browTail] - outer).normalised()
            val tip = outer + direction * (shape.length * faceWidth)
            Resolved.Poly(listOf(pts[shape.lidPoint], tip, outer), emptyList())
        }
    }
}
```

- [ ] **Step 4: Run and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*RegionGeometryTest'`
Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add face regions and geometry"
```

---

### Task 8: Face analysis

**Files:**
- Create: `$MAIN/face/FaceProfile.kt`, `$MAIN/face/FaceAnalyser.kt`, `$MAIN/face/ProfileCollector.kt`
- Test: `$TEST/face/FakeFace.kt`, `$TEST/face/FaceAnalyserTest.kt`, `$TEST/face/ProfileCollectorTest.kt`

**Interfaces:**
- Consumes: `Pt` (Task 7), `ColourMath` (Task 4).
- Produces: `@Serializable` enums `FaceShape {OVAL, ROUND, SQUARE, HEART, LONG}`, `EyeShape {ALMOND, ROUND, HOODED, MONOLID, UPTURNED, DOWNTURNED}`, `LipFullness {THIN, MEDIUM, FULL}`, `Undertone {WARM, COOL, NEUTRAL}`, `SkinDepth {FAIR, LIGHT, MEDIUM, TAN, DEEP}`. `@Serializable data class FaceProfile(faceShape, eyeShape, lips, undertone: Undertone?, depth: SkinDepth?)`. Extension `Enum<*>.label(): String` (lower-case words). `FaceAnalyser.analyse(landmarks: List<Pt>, skinColour: Int?): FaceProfile`, where landmarks are in pixel units of the un-mirrored upright image. `class ProfileCollector(needed: Int = 15)` with `add(profile): FaceProfile?`.
- The thresholds are heuristics. They are constants at the top of `FaceAnalyser` so they can be tuned after trying the app.

- [ ] **Step 1: Write the test fixture and failing tests**

<!-- file: $TEST/face/FakeFace.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import kotlin.math.tan

/** Builds a synthetic 478-point face with only the landmarks FaceAnalyser reads. */
object FakeFace {
    fun build(
        faceLength: Float = 140f,
        cheekWidth: Float = 100f,
        jawWidth: Float = 80f,
        foreheadWidth: Float = 80f,
        eyeWidth: Float = 30f,
        eyeHeight: Float = 9f,
        tiltDeg: Float = 3f,
        browGap: Float = 24f,
        lipHeight: Float = 16f,
        mouthWidth: Float = 45f,
        rollDeg: Float = 0f,
    ): List<Pt> {
        val p = MutableList(478) { Pt(0f, 0f) }
        val cx = 200f
        val eyeY = 200f
        p[10] = Pt(cx, eyeY - faceLength * 0.4f)
        p[152] = Pt(cx, eyeY + faceLength * 0.6f)
        p[234] = Pt(cx - cheekWidth / 2, eyeY + 10)
        p[454] = Pt(cx + cheekWidth / 2, eyeY + 10)
        p[172] = Pt(cx - jawWidth / 2, eyeY + 70)
        p[397] = Pt(cx + jawWidth / 2, eyeY + 70)
        p[54] = Pt(cx - foreheadWidth / 2, eyeY - 40)
        p[284] = Pt(cx + foreheadWidth / 2, eyeY - 40)
        val rise = (eyeWidth * tan(Math.toRadians(tiltDeg.toDouble()))).toFloat()
        // The subject's right eye is on the image left, so its outer corner (33) has the smaller x.
        fun eye(outer: Int, inner: Int, top: Int, bottom: Int, brow: Int, innerX: Float, outward: Float) {
            val innerPt = Pt(innerX, eyeY)
            val outerPt = Pt(innerX + outward * eyeWidth, eyeY - rise)
            p[inner] = innerPt
            p[outer] = outerPt
            val mid = Pt((innerPt.x + outerPt.x) / 2, (innerPt.y + outerPt.y) / 2)
            p[top] = Pt(mid.x, mid.y - eyeHeight / 2)
            p[bottom] = Pt(mid.x, mid.y + eyeHeight / 2)
            p[brow] = Pt(mid.x, mid.y - eyeHeight / 2 - browGap)
        }
        eye(outer = 33, inner = 133, top = 159, bottom = 145, brow = 105, innerX = cx - 15, outward = -1f)
        eye(outer = 263, inner = 362, top = 386, bottom = 374, brow = 334, innerX = cx + 15, outward = 1f)
        val mouthY = eyeY + 60
        p[61] = Pt(cx - mouthWidth / 2, mouthY)
        p[291] = Pt(cx + mouthWidth / 2, mouthY)
        p[0] = Pt(cx, mouthY - lipHeight / 2)
        p[17] = Pt(cx, mouthY + lipHeight / 2)
        val roll = Math.toRadians(rollDeg.toDouble())
        val centre = Pt(cx, eyeY)
        return p.map { it.rotate(roll, centre) }
    }
}
```

<!-- file: $TEST/face/FaceAnalyserTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import io.github.nugggy.makeupmirror.colour.ColourMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceAnalyserTest {
    private fun analyse(face: List<Pt>, skin: Int? = null) = FaceAnalyser.analyse(face, skin)

    @Test
    fun averageFaceIsOvalAlmondMedium() {
        val p = analyse(FakeFace.build())
        assertEquals(FaceShape.OVAL, p.faceShape)
        assertEquals(EyeShape.ALMOND, p.eyeShape)
        assertEquals(LipFullness.MEDIUM, p.lips)
    }

    @Test
    fun faceShapes() {
        assertEquals(FaceShape.LONG, analyse(FakeFace.build(faceLength = 160f)).faceShape)
        assertEquals(FaceShape.ROUND, analyse(FakeFace.build(faceLength = 115f, jawWidth = 85f)).faceShape)
        assertEquals(FaceShape.SQUARE, analyse(FakeFace.build(faceLength = 125f, jawWidth = 95f)).faceShape)
        assertEquals(FaceShape.HEART, analyse(FakeFace.build(foreheadWidth = 95f, jawWidth = 70f)).faceShape)
    }

    @Test
    fun eyeShapes() {
        assertEquals(EyeShape.HOODED, analyse(FakeFace.build(browGap = 15f)).eyeShape)
        assertEquals(EyeShape.MONOLID, analyse(FakeFace.build(eyeHeight = 6f)).eyeShape)
        assertEquals(EyeShape.UPTURNED, analyse(FakeFace.build(tiltDeg = 10f)).eyeShape)
        assertEquals(EyeShape.DOWNTURNED, analyse(FakeFace.build(tiltDeg = -4f)).eyeShape)
        assertEquals(EyeShape.ROUND, analyse(FakeFace.build(eyeHeight = 12f)).eyeShape)
    }

    @Test
    fun headRollDoesNotChangeClassification() {
        for (roll in listOf(-15f, 15f)) {
            assertEquals(EyeShape.UPTURNED, analyse(FakeFace.build(tiltDeg = 10f, rollDeg = roll)).eyeShape)
            assertEquals(EyeShape.ALMOND, analyse(FakeFace.build(rollDeg = roll)).eyeShape)
            assertEquals(FaceShape.OVAL, analyse(FakeFace.build(rollDeg = roll)).faceShape)
        }
    }

    @Test
    fun lipFullness() {
        assertEquals(LipFullness.THIN, analyse(FakeFace.build(lipHeight = 12f)).lips)
        assertEquals(LipFullness.FULL, analyse(FakeFace.build(lipHeight = 20f)).lips)
    }

    @Test
    fun skinToneFromSampledColour() {
        val warm = analyse(FakeFace.build(), ColourMath.parseHex("#E0AC7E"))
        assertEquals(Undertone.WARM, warm.undertone)
        val cool = analyse(FakeFace.build(), ColourMath.parseHex("#E8B4B8"))
        assertEquals(Undertone.COOL, cool.undertone)
        assertEquals(SkinDepth.FAIR, analyse(FakeFace.build(), ColourMath.parseHex("#F5E1D3")).depth)
        assertEquals(SkinDepth.DEEP, analyse(FakeFace.build(), ColourMath.parseHex("#5A3825")).depth)
    }

    @Test
    fun noSkinSampleMeansNoToneResult() {
        val p = analyse(FakeFace.build())
        assertNull(p.undertone)
        assertNull(p.depth)
    }
}
```

<!-- file: $TEST/face/ProfileCollectorTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileCollectorTest {
    private val almond = FaceProfile(FaceShape.OVAL, EyeShape.ALMOND, LipFullness.MEDIUM, Undertone.WARM, SkinDepth.LIGHT)

    @Test
    fun returnsMajorityAfterEnoughSamples() {
        val collector = ProfileCollector(needed = 5)
        repeat(3) { assertNull(collector.add(almond)) }
        assertNull(collector.add(almond.copy(eyeShape = EyeShape.HOODED, undertone = null)))
        val result = collector.add(almond.copy(eyeShape = EyeShape.HOODED, undertone = null))
        assertEquals(almond, result)
    }
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew testDebugUnitTest --tests '*FaceAnalyserTest' --tests '*ProfileCollectorTest'`
Expected: compilation fails with `Unresolved reference: FaceAnalyser`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/face/FaceProfile.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import kotlinx.serialization.Serializable

@Serializable enum class FaceShape { OVAL, ROUND, SQUARE, HEART, LONG }

@Serializable enum class EyeShape { ALMOND, ROUND, HOODED, MONOLID, UPTURNED, DOWNTURNED }

@Serializable enum class LipFullness { THIN, MEDIUM, FULL }

@Serializable enum class Undertone { WARM, COOL, NEUTRAL }

@Serializable enum class SkinDepth { FAIR, LIGHT, MEDIUM, TAN, DEEP }

@Serializable
data class FaceProfile(
    val faceShape: FaceShape,
    val eyeShape: EyeShape,
    val lips: LipFullness,
    val undertone: Undertone?,
    val depth: SkinDepth?,
)

fun Enum<*>.label(): String = name.lowercase().replace('_', ' ')
```

<!-- file: $MAIN/face/FaceAnalyser.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import io.github.nugggy.makeupmirror.colour.ColourMath
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Classifies face, eye and lip shape from landmarks, and skin tone from a sampled colour.
 * Landmarks must be in pixel units of the un-mirrored, upright camera image.
 */
object FaceAnalyser {
    private const val LONG_RATIO = 1.5f
    private const val HEART_FOREHEAD_TO_JAW = 1.3f
    private const val SQUARE_JAW_TO_CHEEK = 0.9f
    private const val SQUARE_MAX_RATIO = 1.35f
    private const val ROUND_MAX_RATIO = 1.25f
    private const val MONOLID_ASPECT = 0.24f
    private const val HOODED_BROW_GAP = 0.62f
    private const val UPTURNED_DEG = 7f
    private const val DOWNTURNED_DEG = -1f
    private const val ROUND_EYE_ASPECT = 0.36f
    private const val THIN_LIPS = 0.30f
    private const val FULL_LIPS = 0.42f

    fun analyse(landmarks: List<Pt>, skinColour: Int?): FaceProfile {
        val pts = level(landmarks)
        return FaceProfile(
            faceShape = faceShape(pts),
            eyeShape = eyeShape(pts),
            lips = lips(pts),
            undertone = skinColour?.let(::undertone),
            depth = skinColour?.let(::depth),
        )
    }

    /** Rotates the face so the eyes are level, removing head roll. */
    private fun level(pts: List<Pt>): List<Pt> {
        val right = mid(pts[33], pts[133])
        val left = mid(pts[263], pts[362])
        val angle = atan2((left.y - right.y).toDouble(), (left.x - right.x).toDouble())
        return pts.map { it.rotate(-angle, right) }
    }

    private fun faceShape(p: List<Pt>): FaceShape {
        val length = p[10].dist(p[152])
        val cheek = p[234].dist(p[454])
        val jaw = p[172].dist(p[397])
        val forehead = p[54].dist(p[284])
        val ratio = length / cheek
        return when {
            ratio >= LONG_RATIO -> FaceShape.LONG
            forehead / jaw >= HEART_FOREHEAD_TO_JAW -> FaceShape.HEART
            jaw / cheek >= SQUARE_JAW_TO_CHEEK && ratio < SQUARE_MAX_RATIO -> FaceShape.SQUARE
            ratio < ROUND_MAX_RATIO -> FaceShape.ROUND
            else -> FaceShape.OVAL
        }
    }

    private fun eyeShape(p: List<Pt>): EyeShape {
        val width = (p[33].dist(p[133]) + p[263].dist(p[362])) / 2
        val height = (p[159].dist(p[145]) + p[386].dist(p[374])) / 2
        val aspect = height / width
        val browGap = (p[159].dist(p[105]) + p[386].dist(p[334])) / 2 / width
        val tilt = (tiltDeg(inner = p[133], outer = p[33]) + tiltDeg(inner = p[362], outer = p[263])) / 2
        return when {
            aspect < MONOLID_ASPECT -> EyeShape.MONOLID
            browGap < HOODED_BROW_GAP -> EyeShape.HOODED
            tilt > UPTURNED_DEG -> EyeShape.UPTURNED
            tilt < DOWNTURNED_DEG -> EyeShape.DOWNTURNED
            aspect >= ROUND_EYE_ASPECT -> EyeShape.ROUND
            else -> EyeShape.ALMOND
        }
    }

    /** Positive when the outer corner sits higher than the inner corner (image y grows downwards). */
    private fun tiltDeg(inner: Pt, outer: Pt): Float =
        Math.toDegrees(atan2((inner.y - outer.y).toDouble(), abs(outer.x - inner.x).toDouble())).toFloat()

    private fun lips(p: List<Pt>): LipFullness {
        val ratio = p[0].dist(p[17]) / p[61].dist(p[291])
        return when {
            ratio < THIN_LIPS -> LipFullness.THIN
            ratio > FULL_LIPS -> LipFullness.FULL
            else -> LipFullness.MEDIUM
        }
    }

    private fun undertone(colour: Int): Undertone {
        val hue = ColourMath.hueDegrees(ColourMath.toLab(colour))
        return when {
            hue > 60 -> Undertone.WARM
            hue < 50 -> Undertone.COOL
            else -> Undertone.NEUTRAL
        }
    }

    private fun depth(colour: Int): SkinDepth {
        val l = ColourMath.toLab(colour).l
        return when {
            l > 75 -> SkinDepth.FAIR
            l > 65 -> SkinDepth.LIGHT
            l > 52 -> SkinDepth.MEDIUM
            l > 40 -> SkinDepth.TAN
            else -> SkinDepth.DEEP
        }
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
}
```

<!-- file: $MAIN/face/ProfileCollector.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

/** Smooths per-frame analysis by taking the most common answer over [needed] frames. */
class ProfileCollector(private val needed: Int = 15) {
    private val samples = mutableListOf<FaceProfile>()

    fun add(profile: FaceProfile): FaceProfile? {
        samples += profile
        if (samples.size < needed) return null
        val result = FaceProfile(
            faceShape = mode { it.faceShape }!!,
            eyeShape = mode { it.eyeShape }!!,
            lips = mode { it.lips }!!,
            undertone = mode { it.undertone },
            depth = mode { it.depth },
        )
        samples.clear()
        return result
    }

    private fun <T : Any> mode(select: (FaceProfile) -> T?): T? =
        samples.mapNotNull(select).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
```

- [ ] **Step 4: Run and watch them pass**

Run: `./gradlew testDebugUnitTest --tests '*FaceAnalyserTest' --tests '*ProfileCollectorTest'`
Expected: 8 tests pass. If a skin-tone assertion fails, print `ColourMath.hueDegrees(ColourMath.toLab(colour))` for that colour. Only adjust the test colour if it is outside the documented band (warm > 60°, cool < 50°). Do not move the thresholds to fit a single sample.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add face shape and skin tone analysis"
```

---

### Task 9: Style matcher

**Files:**
- Create: `$MAIN/matcher/MatchedLook.kt`, `$MAIN/matcher/FitRules.kt`, `$MAIN/matcher/StyleMatcher.kt`
- Test: `$TEST/matcher/StyleMatcherTest.kt`

**Interfaces:**
- Consumes: `Look`, `LookLayer` (Task 6), `Product`, `ProductType` (Task 5), `FaceProfile` and its enums (Task 8), `ColourMath` (Task 4).
- Produces: `data class MatchedLook(look, assignments: Map<String, Product>, missing: Set<ProductType>, score: Double, reasons: List<String>, suitsYou: Boolean = false)` with `available: Boolean`. `StyleMatcher.match(kit: List<Product>, profile: FaceProfile?, looks: List<Look>): List<MatchedLook>`, sorted with available looks first and then by score. The top three available looks that have at least one fit reason get `suitsYou = true`.

- [ ] **Step 1: Write the failing test**

<!-- file: $TEST/matcher/StyleMatcherTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.EyeShape
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.face.FaceShape
import io.github.nugggy.makeupmirror.face.LipFullness
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.face.SkinDepth
import io.github.nugggy.makeupmirror.face.Undertone
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.styles.LessonStep
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleMatcherTest {
    private fun layer(role: String, type: ProductType, colour: String, optional: Boolean = false) =
        LookLayer(role, type, Region.LIPS, colour, 0.5f, optional)

    private fun look(id: String, tags: List<String>, vararg layers: LookLayer) =
        Look(id, id, "", tags, "img/$id.jpg", layers.toList(), layers.map { LessonStep(it.role) })

    private fun product(id: Long, type: ProductType, colour: String) =
        Product(id, type, "$type $id", ColourMath.parseHex(colour))

    private val warmProfile = FaceProfile(FaceShape.OVAL, EyeShape.ALMOND, LipFullness.MEDIUM, Undertone.WARM, SkinDepth.LIGHT)

    @Test
    fun lookIsUnavailableWhenARequiredProductIsMissing() {
        val red = look("red", emptyList(), layer("liner", ProductType.EYELINER, "#111111"), layer("lip", ProductType.LIPSTICK, "#B3121E"))
        val result = StyleMatcher.match(listOf(product(1, ProductType.LIPSTICK, "#B3121E")), null, listOf(red)).single()
        assertFalse(result.available)
        assertEquals(setOf(ProductType.EYELINER), result.missing)
    }

    @Test
    fun missingOptionalProductKeepsLookAvailable() {
        val l = look("l", emptyList(), layer("lip", ProductType.LIPSTICK, "#B3121E"), layer("gloss", ProductType.LIP_GLOSS, "#FFFFFF", optional = true))
        val result = StyleMatcher.match(listOf(product(1, ProductType.LIPSTICK, "#B3121E")), null, listOf(l)).single()
        assertTrue(result.available)
        assertEquals(setOf("lip"), result.assignments.keys)
    }

    @Test
    fun closestShadeIsAssigned() {
        val kit = listOf(product(1, ProductType.LIPSTICK, "#C2A49A"), product(2, ProductType.LIPSTICK, "#B01020"))
        val red = look("red", emptyList(), layer("lip", ProductType.LIPSTICK, "#B3121E"))
        assertEquals(2L, StyleMatcher.match(kit, null, listOf(red)).single().assignments.getValue("lip").id)
    }

    @Test
    fun singleProductCoversTwoRolesOfSameType() {
        val smoky = look("smoky", emptyList(), layer("lid", ProductType.EYESHADOW, "#3A3A40"), layer("crease", ProductType.EYESHADOW, "#6E6670"))
        val result = StyleMatcher.match(listOf(product(7, ProductType.EYESHADOW, "#8A6A5A")), null, listOf(smoky)).single()
        assertTrue(result.available)
        assertEquals(7L, result.assignments.getValue("lid").id)
        assertEquals(7L, result.assignments.getValue("crease").id)
    }

    @Test
    fun availableLooksComeFirst() {
        val needsLiner = look("a", emptyList(), layer("liner", ProductType.EYELINER, "#111111"))
        val needsLipstick = look("b", emptyList(), layer("lip", ProductType.LIPSTICK, "#B3121E"))
        val result = StyleMatcher.match(listOf(product(1, ProductType.LIPSTICK, "#B3121E")), null, listOf(needsLiner, needsLipstick))
        assertEquals(listOf("b", "a"), result.map { it.look.id })
    }

    @Test
    fun warmProfileRanksWarmLookFirstWithAReason() {
        val kit = listOf(product(1, ProductType.BRONZER, "#A86B45"))
        val warm = look("warm", listOf("warm"), layer("b", ProductType.BRONZER, "#A86B45"))
        val cool = look("cool", listOf("cool"), layer("b", ProductType.BRONZER, "#A86B45"))
        val result = StyleMatcher.match(kit, warmProfile, listOf(cool, warm))
        assertEquals("warm", result.first().look.id)
        assertTrue(result.first().suitsYou)
        assertTrue(result.first().reasons.first().contains("warm undertone"))
        assertFalse(result.last().suitsYou)
    }

    @Test
    fun noProfileMeansNoBadges() {
        val kit = listOf(product(1, ProductType.BRONZER, "#A86B45"))
        val warm = look("warm", listOf("warm", "wing"), layer("b", ProductType.BRONZER, "#A86B45"))
        val result = StyleMatcher.match(kit, null, listOf(warm)).single()
        assertTrue(result.reasons.isEmpty())
        assertFalse(result.suitsYou)
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*StyleMatcherTest'`
Expected: compilation fails with `Unresolved reference: StyleMatcher`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/matcher/MatchedLook.kt -->
```kotlin
package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.styles.Look

/** A look paired with the kit products that will be used for each of its layer roles. */
data class MatchedLook(
    val look: Look,
    val assignments: Map<String, Product>,
    val missing: Set<ProductType>,
    val score: Double,
    val reasons: List<String>,
    val suitsYou: Boolean = false,
) {
    val available: Boolean get() = missing.isEmpty()
}
```

<!-- file: $MAIN/matcher/FitRules.kt -->
```kotlin
package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.face.EyeShape
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.face.FaceShape
import io.github.nugggy.makeupmirror.face.LipFullness
import io.github.nugggy.makeupmirror.face.SkinDepth
import io.github.nugggy.makeupmirror.face.Undertone
import io.github.nugggy.makeupmirror.styles.Look

/** Common makeup-artist rules of thumb, scored against a look's tags. */
internal object FitRules {
    fun score(look: Look, profile: FaceProfile?): Pair<Double, List<String>> {
        if (profile == null) return 0.0 to emptyList()
        val tags = look.tags.toSet()
        var score = 0.0
        val reasons = mutableListOf<String>()
        fun add(points: Double, reason: String? = null) {
            score += points
            if (reason != null && points > 0) reasons += reason
        }

        when (profile.undertone) {
            Undertone.WARM -> if ("warm" in tags) add(2.0, "Warm shades flatter your warm undertone") else if ("cool" in tags) add(-1.0)
            Undertone.COOL -> if ("cool" in tags) add(2.0, "Cool shades flatter your cool undertone") else if ("warm" in tags) add(-1.0)
            Undertone.NEUTRAL -> if ("warm" in tags || "cool" in tags) add(1.0, "Your neutral undertone suits warm and cool shades")
            null -> Unit
        }
        when (profile.eyeShape) {
            EyeShape.HOODED -> {
                if ("smoky" in tags) add(2.0, "Blended smoky shadow opens up hooded eyes")
                if ("cut-crease" in tags) add(-2.0)
            }
            EyeShape.MONOLID -> {
                if ("smoky" in tags) add(2.0, "A soft gradient of shadow suits your eye shape")
                if ("cut-crease" in tags) add(-1.0)
            }
            EyeShape.ALMOND -> if ("wing" in tags) add(2.0, "Almond eyes are perfect for a winged liner")
            EyeShape.ROUND -> if ("wing" in tags) add(2.0, "A longer wing lengthens round eyes")
            EyeShape.DOWNTURNED -> if ("wing" in tags) add(2.0, "A lifted wing balances downturned eyes")
            EyeShape.UPTURNED -> if ("smoky" in tags || "wing" in tags) add(1.0, "Your upturned eyes suit defined liner and shadow")
        }
        when (profile.faceShape) {
            FaceShape.ROUND -> if ("contour" in tags) add(1.0, "Soft contour adds definition to a round face")
            FaceShape.SQUARE -> if ("contour" in tags) add(1.0, "Soft contour softens a strong jaw")
            FaceShape.LONG -> if ("blush-forward" in tags) add(1.0, "Blush across the cheeks balances a longer face")
            FaceShape.HEART -> if ("bold-lip" in tags) add(1.0, "A bold lip balances a heart-shaped face")
            FaceShape.OVAL -> if ("highlight" in tags) add(0.5, "Your oval face shows off highlighter well")
        }
        when (profile.lips) {
            LipFullness.THIN -> if ("gloss" in tags) add(1.0, "Gloss makes lips look fuller")
            LipFullness.FULL -> if ("bold-lip" in tags) add(1.0, "Full lips carry a bold colour beautifully")
            LipFullness.MEDIUM -> Unit
        }
        when (profile.depth) {
            SkinDepth.TAN, SkinDepth.DEEP -> if ("highlight" in tags) add(1.0, "Highlighter glows beautifully on deeper skin")
            SkinDepth.FAIR -> if ("subtle" in tags) add(1.0, "Soft, sheer colour looks fresh on fair skin")
            else -> Unit
        }
        return score to reasons
    }
}
```

<!-- file: $MAIN/matcher/StyleMatcher.kt -->
```kotlin
package io.github.nugggy.makeupmirror.matcher

import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer

object StyleMatcher {
    /** How many CIEDE2000 units of average shade mismatch cost one point of fit score. */
    private const val COLOUR_PENALTY_DIVISOR = 25.0
    private const val BADGE_COUNT = 3

    fun match(kit: List<Product>, profile: FaceProfile?, looks: List<Look>): List<MatchedLook> {
        val kitTypes = kit.map { it.type }.toSet()
        val matched = looks.map { look ->
            val assignments = look.layers.mapNotNull { layer -> closest(kit, layer)?.let { layer.role to it } }.toMap()
            val mismatch = assignments.map { (role, product) ->
                ColourMath.deltaE(product.colour, ColourMath.parseHex(look.layer(role)!!.colour))
            }.average().takeUnless { it.isNaN() } ?: 0.0
            val (fit, reasons) = FitRules.score(look, profile)
            MatchedLook(
                look = look,
                assignments = assignments,
                missing = look.requiredTypes - kitTypes,
                score = fit - mismatch / COLOUR_PENALTY_DIVISOR,
                reasons = reasons,
            )
        }
        val sorted = matched.sortedWith(compareByDescending<MatchedLook> { it.available }.thenByDescending { it.score })
        val badged = sorted.filter { it.available && it.reasons.isNotEmpty() }.take(BADGE_COUNT).map { it.look.id }.toSet()
        return sorted.map { if (it.look.id in badged) it.copy(suitsYou = true) else it }
    }

    private fun closest(kit: List<Product>, layer: LookLayer): Product? {
        val target = ColourMath.parseHex(layer.colour)
        return kit.filter { it.type == layer.type }.minByOrNull { ColourMath.deltaE(it.colour, target) }
    }
}
```

- [ ] **Step 4: Run and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*StyleMatcherTest'`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add style matcher with face-fit rules"
```

---

### Task 10: Render plan and lesson builder

**Files:**
- Create: `$MAIN/render/RenderPlan.kt`, `$MAIN/render/RenderPlanner.kt`, `$MAIN/lesson/LessonBuilder.kt`
- Test: `$TEST/render/RenderPlannerTest.kt`, `$TEST/lesson/LessonBuilderTest.kt`

**Interfaces:**
- Consumes: `MatchedLook` (Task 9), `Region` (Task 6), `ProductType`, `Finish`, `Product` (Task 5), `DefaultInstructions` (Task 6).
- Produces: `enum class Blend { NORMAL, MULTIPLY, SCREEN, TINT }`. `data class RenderLayer(role, region, colour: Int, opacity: Float, blend: Blend, softness: Float, shimmer: Boolean)`, where softness is a fraction of face width. `data class RenderPlan(layers)` with `EMPTY` and `forLessonStep(stepRoles: List<String>, stepIndex: Int, doneFade: Float = 0.6f): RenderPlan`. `RenderPlanner.plan(match, strength = 1f): RenderPlan`. `data class LessonCard(role, region, product, instruction, tip)`. `LessonBuilder.build(match): List<LessonCard>`.

- [ ] **Step 1: Write the failing tests**

<!-- file: $TEST/render/RenderPlannerTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Finish
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.styles.LessonStep
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlannerTest {
    private val lipLayer = LookLayer("lip", ProductType.LIPSTICK, Region.LIPS, "#B3121E", 0.6f)
    private val baseLayer = LookLayer("base", ProductType.FOUNDATION, Region.FACE, "#E0B898", 0.3f)
    private val glowLayer = LookLayer("glow", ProductType.HIGHLIGHTER, Region.HIGHLIGHT, "#FFF1D6", 0.4f, optional = true)
    private val look = Look("l", "L", "", emptyList(), "img/l.jpg", listOf(lipLayer, baseLayer, glowLayer), listOf(LessonStep("base"), LessonStep("lip"), LessonStep("glow")))
    private val lipstick = Product(1, ProductType.LIPSTICK, "Ruby", ColourMath.parseHex("#AA1122"), Finish.SATIN)
    private val foundation = Product(2, ProductType.FOUNDATION, "Base", ColourMath.parseHex("#E0B898"))
    private val match = MatchedLook(look, mapOf("lip" to lipstick, "base" to foundation), emptySet(), 0.0, emptyList())

    @Test
    fun layersFollowApplicationOrderAndSkipUnassignedRoles() {
        val plan = RenderPlanner.plan(match)
        assertEquals(listOf("base", "lip"), plan.layers.map { it.role })
    }

    @Test
    fun layerUsesTheKitColourNotTheTargetColour() {
        assertEquals(lipstick.colour, RenderPlanner.plan(match).layers.last().colour)
    }

    @Test
    fun blendModesSuitEachProduct() {
        assertEquals(Blend.TINT, RenderPlanner.blendFor(ProductType.LIPSTICK))
        assertEquals(Blend.NORMAL, RenderPlanner.blendFor(ProductType.FOUNDATION))
        assertEquals(Blend.MULTIPLY, RenderPlanner.blendFor(ProductType.EYELINER))
        assertEquals(Blend.SCREEN, RenderPlanner.blendFor(ProductType.HIGHLIGHTER))
    }

    @Test
    fun strengthScalesOpacityWithinBounds() {
        assertEquals(0.3f, RenderPlanner.plan(match, strength = 0.5f).layers.last().opacity, 1e-4f)
        assertEquals(1f, RenderPlanner.plan(match, strength = 3f).layers.last().opacity, 1e-4f)
    }

    @Test
    fun shimmerAndGlossFinishesShine() {
        val glossy = match.copy(assignments = mapOf("lip" to lipstick.copy(finish = Finish.GLOSS)))
        assertTrue(RenderPlanner.plan(glossy).layers.single().shimmer)
    }

    @Test
    fun lessonStepShowsDoneFadedCurrentFullAndHidesFuture() {
        val plan = RenderPlanner.plan(match)
        val step0 = plan.forLessonStep(listOf("base", "lip"), 0)
        assertEquals(listOf("base"), step0.layers.map { it.role })
        val step1 = plan.forLessonStep(listOf("base", "lip"), 1)
        assertEquals(listOf("base", "lip"), step1.layers.map { it.role })
        assertEquals(0.3f * 0.6f, step1.layers.first().opacity, 1e-4f)
        assertEquals(0.6f, step1.layers.last().opacity, 1e-4f)
    }
}
```

<!-- file: $TEST/lesson/LessonBuilderTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.lesson

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.styles.DefaultInstructions
import io.github.nugggy.makeupmirror.styles.LessonStep
import io.github.nugggy.makeupmirror.styles.Look
import io.github.nugggy.makeupmirror.styles.LookLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonBuilderTest {
    private val blush = LookLayer("blush", ProductType.BLUSH, Region.CHEEKS, "#E8A3A0")
    private val lip = LookLayer("lip", ProductType.LIPSTICK, Region.LIPS, "#B3121E")
    private val gloss = LookLayer("gloss", ProductType.LIP_GLOSS, Region.LIPS, "#FFFFFF", optional = true)
    private val look = Look(
        "l", "L", "", emptyList(), "img/l.jpg", listOf(blush, lip, gloss),
        listOf(LessonStep("blush"), LessonStep("lip", instruction = "Custom lip step.", tip = "Go slowly."), LessonStep("gloss")),
    )
    private val kitBlush = Product(1, ProductType.BLUSH, "Rose Petal", 0)
    private val kitLip = Product(2, ProductType.LIPSTICK, "Ruby", 0)

    @Test
    fun buildsCardsInStepOrderAndSkipsUnassignedOptionalSteps() {
        val match = MatchedLook(look, mapOf("blush" to kitBlush, "lip" to kitLip), emptySet(), 0.0, emptyList())
        val cards = LessonBuilder.build(match)
        assertEquals(listOf("blush", "lip"), cards.map { it.role })
        assertEquals(kitBlush, cards[0].product)
        assertEquals(Region.CHEEKS, cards[0].region)
        assertEquals(DefaultInstructions.forLayer(blush), cards[0].instruction)
        assertEquals("Custom lip step.", cards[1].instruction)
        assertEquals("Go slowly.", cards[1].tip)
    }
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew testDebugUnitTest --tests '*RenderPlannerTest' --tests '*LessonBuilderTest'`
Expected: compilation fails with `Unresolved reference: RenderPlanner`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/render/RenderPlan.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Region

/**
 * How a layer mixes with the skin. TINT keeps the skin's light and shade but takes the product's
 * hue, and darker shades get an extra multiply pass.
 */
enum class Blend { NORMAL, MULTIPLY, SCREEN, TINT }

data class RenderLayer(
    val role: String,
    val region: Region,
    val colour: Int,
    val opacity: Float,
    val blend: Blend,
    /** Edge blur as a fraction of face width. */
    val softness: Float,
    val shimmer: Boolean,
)

data class RenderPlan(val layers: List<RenderLayer>) {
    /** Layers for a lesson: finished steps faded, the current step at full strength, later steps hidden. */
    fun forLessonStep(stepRoles: List<String>, stepIndex: Int, doneFade: Float = 0.6f): RenderPlan {
        val done = stepRoles.take(stepIndex).toSet()
        val current = stepRoles.getOrNull(stepIndex)
        return RenderPlan(
            layers.mapNotNull { layer ->
                when (layer.role) {
                    current -> layer
                    in done -> layer.copy(opacity = layer.opacity * doneFade)
                    else -> null
                }
            },
        )
    }

    companion object {
        val EMPTY = RenderPlan(emptyList())
    }
}
```

<!-- file: $MAIN/render/RenderPlanner.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Finish
import io.github.nugggy.makeupmirror.kit.ProductType
import io.github.nugggy.makeupmirror.matcher.MatchedLook

object RenderPlanner {
    /** The order products go on, so later layers draw on top. */
    private val applicationOrder = listOf(
        ProductType.FOUNDATION, ProductType.CONCEALER, ProductType.POWDER, ProductType.CONTOUR,
        ProductType.BRONZER, ProductType.BLUSH, ProductType.HIGHLIGHTER, ProductType.EYESHADOW,
        ProductType.EYELINER, ProductType.MASCARA, ProductType.BROW, ProductType.LIP_LINER,
        ProductType.LIPSTICK, ProductType.LIP_GLOSS,
    )

    fun plan(match: MatchedLook, strength: Float = 1f): RenderPlan = RenderPlan(
        match.look.layers
            .sortedBy { applicationOrder.indexOf(it.type) }
            .mapNotNull { layer ->
                val product = match.assignments[layer.role] ?: return@mapNotNull null
                RenderLayer(
                    role = layer.role,
                    region = layer.region,
                    colour = product.colour,
                    opacity = (layer.opacity * strength).coerceIn(0f, 1f),
                    blend = blendFor(product.type),
                    softness = softnessFor(layer.region),
                    shimmer = product.finish == Finish.SHIMMER || product.finish == Finish.GLOSS,
                )
            },
    )

    internal fun blendFor(type: ProductType): Blend = when (type) {
        ProductType.FOUNDATION, ProductType.CONCEALER, ProductType.POWDER -> Blend.NORMAL
        ProductType.CONTOUR, ProductType.BRONZER, ProductType.EYELINER, ProductType.MASCARA, ProductType.BROW -> Blend.MULTIPLY
        ProductType.HIGHLIGHTER -> Blend.SCREEN
        ProductType.BLUSH, ProductType.EYESHADOW, ProductType.LIPSTICK, ProductType.LIP_LINER, ProductType.LIP_GLOSS -> Blend.TINT
    }

    internal fun softnessFor(region: Region): Float = when (region) {
        Region.FACE -> 0.04f
        Region.UNDER_EYE -> 0.025f
        Region.CONTOUR, Region.BRONZE, Region.CHEEKS, Region.HIGHLIGHT -> 0f // drawn as soft gradients already
        Region.LIDS -> 0.012f
        Region.CREASE -> 0.02f
        Region.LOWER_LASH_LINE -> 0.006f
        Region.UPPER_LINER, Region.WING, Region.LASHES -> 0.0015f
        Region.BROWS -> 0.004f
        Region.LIP_LINE -> 0.002f
        Region.LIPS -> 0.003f
        Region.LIP_GLOSS -> 0.01f
    }
}
```

<!-- file: $MAIN/lesson/LessonBuilder.kt -->
```kotlin
package io.github.nugggy.makeupmirror.lesson

import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.styles.DefaultInstructions

data class LessonCard(
    val role: String,
    val region: Region,
    val product: Product,
    val instruction: String,
    val tip: String?,
)

object LessonBuilder {
    /** One card per step whose role has a kit product assigned, in the look's step order. */
    fun build(match: MatchedLook): List<LessonCard> = match.look.steps.mapNotNull { step ->
        val layer = match.look.layer(step.role) ?: return@mapNotNull null
        val product = match.assignments[step.role] ?: return@mapNotNull null
        LessonCard(
            role = step.role,
            region = layer.region,
            product = product,
            instruction = step.instruction ?: DefaultInstructions.forLayer(layer),
            tip = step.tip,
        )
    }
}
```

- [ ] **Step 4: Run and watch them pass**

Run: `./gradlew testDebugUnitTest --tests '*RenderPlannerTest' --tests '*LessonBuilderTest'`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add render planning and lesson builder"
```

---

### Task 11: Landmark smoothing and screen mapping

**Files:**
- Create: `$MAIN/face/OneEuroFilter.kt`, `$MAIN/face/LandmarkSmoother.kt`, `$MAIN/render/LandmarkMapper.kt`
- Test: `$TEST/face/LandmarkSmootherTest.kt`, `$TEST/render/LandmarkMapperTest.kt`

**Interfaces:**
- Consumes: `Pt` (Task 7).
- Produces: `class OneEuroFilter(minCutoff = 1.0, beta = 0.007, dCutoff = 1.0)` with `filter(x: Double, tSeconds: Double): Double`. `class LandmarkSmoother(minCutoff = 1.5, beta = 0.01, resetAfterSeconds = 0.5)` with `smooth(points: List<Pt>, tSeconds: Double): List<Pt>`. `class LandmarkMapper(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float, mirror: Boolean)` with `scale`, `offsetX`, `offsetY`, `map(nx, ny): Pt` and `mapAll(normalised: List<Pt>): List<Pt>`. It maps normalised landmarks to view pixels for a centre-cropped (fill) image.

- [ ] **Step 1: Write the failing tests**

<!-- file: $TEST/face/LandmarkSmootherTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkSmootherTest {
    @Test
    fun steadyInputStaysPut() {
        val f = OneEuroFilter()
        repeat(30) { assertEquals(5.0, f.filter(5.0, it / 30.0), 1e-9) }
    }

    @Test
    fun jumpIsFollowedGradually() {
        val f = OneEuroFilter()
        f.filter(0.0, 0.0)
        val first = f.filter(10.0, 1 / 30.0)
        assertTrue(first > 0.0 && first < 10.0)
        var last = first
        for (i in 2..60) last = f.filter(10.0, i / 30.0)
        assertEquals(10.0, last, 0.05)
    }

    @Test
    fun gapResetsFilters() {
        val s = LandmarkSmoother()
        s.smooth(listOf(Pt(0f, 0f)), 0.0)
        s.smooth(listOf(Pt(0f, 0f)), 0.033)
        // Face lost for a second, then found somewhere else: jump straight there.
        val after = s.smooth(listOf(Pt(100f, 50f)), 1.2).single()
        assertEquals(Pt(100f, 50f), after)
    }
}
```

<!-- file: $TEST/render/LandmarkMapperTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Pt
import org.junit.Assert.assertEquals
import org.junit.Test

class LandmarkMapperTest {
    // 480x640 portrait frame drawn to fill a 1080x1920 view: scale 3, image 1440 wide, cropped 180 each side.
    private val plain = LandmarkMapper(480, 640, 1080f, 1920f, mirror = false)
    private val mirrored = LandmarkMapper(480, 640, 1080f, 1920f, mirror = true)

    @Test
    fun fillScaleAndCropOffset() {
        assertEquals(3f, plain.scale, 1e-4f)
        assertEquals(-180f, plain.offsetX, 1e-3f)
        assertEquals(0f, plain.offsetY, 1e-3f)
    }

    @Test
    fun centreMapsToCentre() {
        assertEquals(Pt(540f, 960f), plain.map(0.5f, 0.5f))
        assertEquals(Pt(540f, 960f), mirrored.map(0.5f, 0.5f))
    }

    @Test
    fun mirrorFlipsHorizontally() {
        assertEquals(180f, plain.map(0.25f, 0.5f).x, 1e-3f)
        assertEquals(900f, mirrored.map(0.25f, 0.5f).x, 1e-3f)
    }
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew testDebugUnitTest --tests '*LandmarkSmootherTest' --tests '*LandmarkMapperTest'`
Expected: compilation fails with `Unresolved reference: OneEuroFilter`.

- [ ] **Step 3: Implement**

<!-- file: $MAIN/face/OneEuroFilter.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

import kotlin.math.PI
import kotlin.math.abs

/** One Euro filter (Casiez et al. 2012): smooths jitter when still and keeps up when moving fast. */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.007,
    private val dCutoff: Double = 1.0,
) {
    private var previous: Double? = null
    private var previousDerivative = 0.0
    private var previousTime = 0.0

    fun filter(x: Double, tSeconds: Double): Double {
        val prev = previous
        if (prev == null) {
            previous = x
            previousTime = tSeconds
            return x
        }
        val dt = (tSeconds - previousTime).coerceAtLeast(1e-3)
        val derivative = lowPass((x - prev) / dt, previousDerivative, alpha(dt, dCutoff))
        val cutoff = minCutoff + beta * abs(derivative)
        val result = lowPass(x, prev, alpha(dt, cutoff))
        previous = result
        previousDerivative = derivative
        previousTime = tSeconds
        return result
    }

    private fun alpha(dt: Double, cutoff: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun lowPass(x: Double, previous: Double, alpha: Double) = alpha * x + (1 - alpha) * previous
}
```

<!-- file: $MAIN/face/LandmarkSmoother.kt -->
```kotlin
package io.github.nugggy.makeupmirror.face

/** Smooths every landmark coordinate, starting fresh after the face has been lost for a while. */
class LandmarkSmoother(
    private val minCutoff: Double = 1.5,
    private val beta: Double = 0.01,
    private val resetAfterSeconds: Double = 0.5,
) {
    private var xs: Array<OneEuroFilter> = emptyArray()
    private var ys: Array<OneEuroFilter> = emptyArray()
    private var lastTime: Double? = null

    fun smooth(points: List<Pt>, tSeconds: Double): List<Pt> {
        val last = lastTime
        if (xs.size != points.size || last == null || tSeconds - last > resetAfterSeconds) {
            xs = Array(points.size) { OneEuroFilter(minCutoff, beta) }
            ys = Array(points.size) { OneEuroFilter(minCutoff, beta) }
        }
        lastTime = tSeconds
        return points.mapIndexed { i, p ->
            Pt(xs[i].filter(p.x.toDouble(), tSeconds).toFloat(), ys[i].filter(p.y.toDouble(), tSeconds).toFloat())
        }
    }
}
```

<!-- file: $MAIN/render/LandmarkMapper.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import io.github.nugggy.makeupmirror.face.Pt
import kotlin.math.max

/**
 * Maps normalised landmarks from an upright camera image to view pixels, for an image drawn
 * centre-cropped to fill the view and optionally mirrored like a real mirror.
 */
class LandmarkMapper(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val viewWidth: Float,
    private val viewHeight: Float,
    private val mirror: Boolean,
) {
    val scale: Float = max(viewWidth / imageWidth, viewHeight / imageHeight)
    val offsetX: Float = (viewWidth - imageWidth * scale) / 2
    val offsetY: Float = (viewHeight - imageHeight * scale) / 2

    fun map(nx: Float, ny: Float): Pt {
        val x = nx * imageWidth * scale + offsetX
        return Pt(if (mirror) viewWidth - x else x, ny * imageHeight * scale + offsetY)
    }

    fun mapAll(normalised: List<Pt>): List<Pt> = normalised.map { map(it.x, it.y) }
}
```

- [ ] **Step 4: Run and watch them pass**

Run: `./gradlew testDebugUnitTest --tests '*LandmarkSmootherTest' --tests '*LandmarkMapperTest'`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add landmark smoothing and view mapping"
```

---

### Task 12: Camera feed, face tracker and makeup renderer

**Files:**
- Create: `$MAIN/camera/CameraFeed.kt`, `$MAIN/camera/FaceTracker.kt`, `$MAIN/camera/FaceCamera.kt`, `$MAIN/render/MakeupRenderer.kt`, `$MAIN/render/MirrorView.kt`

**Interfaces:**
- Consumes: `ColourSampler`, `ColourMath` (Task 4). `Pt`, `Region`, `RegionGeometry`, `Resolved`, `LandmarkSmoother` (Tasks 6, 7, 11). `RenderPlan`, `Blend`, `LandmarkMapper` (Tasks 10, 11).
- Produces: `class CameraFeed(context)` with `start(owner: LifecycleOwner, lensFacing: Int, onFrame: (Bitmap, Long) -> Unit, onError: (Throwable) -> Unit)` and `stop()`. Frames are upright and not mirrored. `data class TrackedFrame(bitmap, landmarks: List<Pt>?, skinColour: Int?, timeMs: Long)`, with landmarks normalised 0 to 1. `class FaceTracker(context) : Closeable` with `frames: StateFlow<TrackedFrame?>` and `process(bitmap, timeMs)`. `data class FaceCameraState(frame: TrackedFrame?, error: String?)` and `@Composable rememberFaceCamera(attempt: Int): FaceCameraState`. `class MakeupRenderer` with `draw(canvas: android.graphics.Canvas, plan, landmarks)` and `drawHighlight(canvas, region, landmarks, pulse: Float)`. `@Composable MirrorView(frame, plan, highlight: Region?, showMakeup: Boolean, modifier)`.
- These classes wrap camera hardware and GPU drawing, so they have no JVM tests. Their logic dependencies are already tested. They are verified by building here and running on a device in Task 17.

- [ ] **Step 1: Camera feed**

<!-- file: $MAIN/camera/CameraFeed.kt -->
```kotlin
package io.github.nugggy.makeupmirror.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Delivers upright (not mirrored) RGBA frames from one camera on a background thread. */
class CameraFeed(private val context: Context) {
    private var executor: ExecutorService? = null
    private var analysis: ImageAnalysis? = null
    private var provider: ProcessCameraProvider? = null

    fun start(
        owner: LifecycleOwner,
        lensFacing: Int,
        onFrame: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val worker = Executors.newSingleThreadExecutor().also { executor = it }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get().also { provider = it }
                val useCase = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                useCase.setAnalyzer(worker) { proxy ->
                    proxy.use {
                        val raw = it.toBitmap()
                        val rotation = it.imageInfo.rotationDegrees
                        val upright = if (rotation == 0) {
                            raw
                        } else {
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        }
                        onFrame(upright, it.imageInfo.timestamp / 1_000_000)
                    }
                }
                analysis = useCase
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                cameraProvider.bindToLifecycle(owner, selector, useCase)
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        analysis?.let { useCase ->
            useCase.clearAnalyzer()
            provider?.unbind(useCase)
        }
        analysis = null
        executor?.shutdown()
        executor = null
    }
}
```

- [ ] **Step 2: Face tracker and composable state holder**

<!-- file: $MAIN/camera/FaceTracker.kt -->
```kotlin
package io.github.nugggy.makeupmirror.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import io.github.nugggy.makeupmirror.colour.ColourSampler
import io.github.nugggy.makeupmirror.face.Pt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import kotlin.math.max

/** A camera frame with the face landmarks found in it, normalised to 0..1 of the upright image. */
data class TrackedFrame(
    val bitmap: Bitmap,
    val landmarks: List<Pt>?,
    val skinColour: Int?,
    val timeMs: Long,
)

class FaceTracker(context: Context) : Closeable {
    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )
    private val lock = Any()
    private var closed = false
    private var lastTimestamp = -1L
    private var frameCount = 0

    private val _frames = MutableStateFlow<TrackedFrame?>(null)
    val frames: StateFlow<TrackedFrame?> = _frames

    /** Runs on the camera thread. */
    fun process(bitmap: Bitmap, timeMs: Long) {
        synchronized(lock) {
            if (closed) return
            val timestamp = if (timeMs <= lastTimestamp) lastTimestamp + 1 else timeMs
            lastTimestamp = timestamp
            val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
            val landmarks = result.faceLandmarks().firstOrNull()?.map { Pt(it.x(), it.y()) }
            val skin = if (landmarks != null && frameCount++ % SKIN_SAMPLE_EVERY == 0) sampleSkin(bitmap, landmarks) else null
            _frames.value = TrackedFrame(bitmap, landmarks, skin, timestamp)
        }
    }

    /** Averages small patches on both cheeks and the forehead. */
    private fun sampleSkin(bitmap: Bitmap, landmarks: List<Pt>): Int? {
        val radius = max(3, bitmap.width / 80)
        val size = radius * 2 + 1
        val all = ArrayList<Int>()
        for (index in SKIN_POINTS) {
            val cx = (landmarks[index].x * bitmap.width).toInt()
            val cy = (landmarks[index].y * bitmap.height).toInt()
            if (cx - radius < 0 || cy - radius < 0 || cx + radius >= bitmap.width || cy + radius >= bitmap.height) continue
            val patch = IntArray(size * size)
            bitmap.getPixels(patch, 0, size, cx - radius, cy - radius, size, size)
            all += ColourSampler.circle(patch, size, size, radius, radius, radius).toList()
        }
        return if (all.isEmpty()) null else ColourSampler.average(all.toIntArray())
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            landmarker.close()
        }
    }

    private companion object {
        const val SKIN_SAMPLE_EVERY = 10
        val SKIN_POINTS = listOf(50, 280, 151)
    }
}
```

<!-- file: $MAIN/camera/FaceCamera.kt -->
```kotlin
package io.github.nugggy.makeupmirror.camera

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

data class FaceCameraState(val frame: TrackedFrame?, val error: String?)

/** Front camera plus face tracking for as long as the caller is composed. Bump [attempt] to retry. */
@Composable
fun rememberFaceCamera(attempt: Int): FaceCameraState {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val created = remember(attempt) { runCatching { FaceTracker(context) } }
    val tracker = created.getOrNull()
    val feed = remember(attempt) { CameraFeed(context) }
    var cameraError by remember(attempt) { mutableStateOf<String?>(null) }

    DisposableEffect(owner, tracker) {
        if (tracker != null) {
            feed.start(owner, CameraSelector.LENS_FACING_FRONT, tracker::process) { cameraError = it.message ?: "The camera isn't available" }
        }
        onDispose {
            feed.stop()
            tracker?.close()
        }
    }

    val empty = remember { MutableStateFlow<TrackedFrame?>(null) }
    val frame by (tracker?.frames ?: empty).collectAsStateWithLifecycle()
    val error = created.exceptionOrNull()?.let { "Face tracking couldn't start (${it.message})" } ?: cameraError
    return FaceCameraState(frame, error)
}
```

- [ ] **Step 3: Makeup renderer and mirror view**

<!-- file: $MAIN/render/MakeupRenderer.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.Pt
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.face.RegionGeometry
import io.github.nugggy.makeupmirror.face.Resolved

/** Draws a [RenderPlan] onto a canvas that already holds the camera image, using view-space landmarks. */
class MakeupRenderer {
    private enum class Mode { NORMAL, MULTIPLY, SCREEN, COLOUR }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(canvas: Canvas, plan: RenderPlan, landmarks: List<Pt>) {
        if (plan.layers.isEmpty()) return
        val faceWidth = RegionGeometry.faceWidth(landmarks)
        for (layer in plan.layers) {
            val shapes = RegionGeometry.resolve(layer.region, landmarks)
            val blur = layer.softness * faceWidth
            when (layer.blend) {
                Blend.NORMAL -> drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.NORMAL, blur)
                Blend.MULTIPLY -> drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.MULTIPLY, blur)
                Blend.SCREEN -> drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.SCREEN, blur)
                Blend.TINT -> {
                    drawShapes(canvas, shapes, layer.colour, layer.opacity, Mode.COLOUR, blur)
                    if (ColourMath.toLab(layer.colour).l < 55) {
                        drawShapes(canvas, shapes, layer.colour, layer.opacity * 0.5f, Mode.MULTIPLY, blur)
                    }
                }
            }
            if (layer.shimmer) drawShapes(canvas, shapes, 0xFFFFFFFF.toInt(), layer.opacity * 0.25f, Mode.SCREEN, blur)
        }
    }

    /** A pulsing dashed outline showing where the current lesson step goes. [pulse] runs 0..1. */
    fun drawHighlight(canvas: Canvas, region: Region, landmarks: List<Pt>, pulse: Float) {
        val faceWidth = RegionGeometry.faceWidth(landmarks)
        outline.color = 0xFFFFFFFF.toInt()
        outline.alpha = (120 + 135 * pulse).toInt().coerceIn(0, 255)
        outline.strokeWidth = faceWidth * 0.006f
        outline.pathEffect = DashPathEffect(floatArrayOf(faceWidth * 0.02f, faceWidth * 0.012f), 0f)
        for (shape in RegionGeometry.resolve(region, landmarks)) {
            when (shape) {
                is Resolved.Poly -> canvas.drawPath(polygonPath(shape.points, emptyList()), outline)
                is Resolved.Line -> canvas.drawPath(linePath(shape.points), outline)
                is Resolved.Ellipse -> {
                    canvas.save()
                    canvas.translate(shape.centre.x, shape.centre.y)
                    canvas.rotate(shape.angleDeg)
                    canvas.drawOval(-shape.rx, -shape.ry, shape.rx, shape.ry, outline)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawShapes(canvas: Canvas, shapes: List<Resolved>, colour: Int, alpha: Float, mode: Mode, blur: Float) {
        for (shape in shapes) {
            paint.reset()
            paint.isAntiAlias = true
            applyMode(paint, mode)
            when (shape) {
                is Resolved.Poly -> {
                    paint.style = Paint.Style.FILL
                    paint.color = withAlpha(colour, alpha)
                    if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(polygonPath(shape.points, shape.holes), paint)
                }
                is Resolved.Line -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = shape.width
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = withAlpha(colour, alpha)
                    if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(linePath(shape.points), paint)
                }
                is Resolved.Ellipse -> {
                    paint.style = Paint.Style.FILL
                    paint.shader = RadialGradient(
                        0f, 0f, 1f,
                        intArrayOf(withAlpha(colour, alpha), withAlpha(colour, alpha * 0.55f), withAlpha(colour, 0f)),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    canvas.save()
                    canvas.translate(shape.centre.x, shape.centre.y)
                    canvas.rotate(shape.angleDeg)
                    canvas.scale(shape.rx, shape.ry)
                    canvas.drawCircle(0f, 0f, 1f, paint)
                    canvas.restore()
                }
            }
        }
    }

    /** True blend modes need API 29. Older phones fall back to plain alpha blending. */
    private fun applyMode(p: Paint, mode: Mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            p.blendMode = when (mode) {
                Mode.NORMAL -> BlendMode.SRC_OVER
                Mode.MULTIPLY -> BlendMode.MULTIPLY
                Mode.SCREEN -> BlendMode.SCREEN
                Mode.COLOUR -> BlendMode.COLOR
            }
        }
    }

    private fun polygonPath(points: List<Pt>, holes: List<List<Pt>>) = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addContour(points)
        holes.forEach { addContour(it) }
    }

    private fun Path.addContour(points: List<Pt>) {
        if (points.isEmpty()) return
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        close()
    }

    private fun linePath(points: List<Pt>) = Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (colour and 0xFFFFFF)
}
```

<!-- file: $MAIN/render/MirrorView.kt -->
```kotlin
package io.github.nugggy.makeupmirror.render

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import io.github.nugggy.makeupmirror.camera.TrackedFrame
import io.github.nugggy.makeupmirror.face.LandmarkSmoother
import io.github.nugggy.makeupmirror.face.Region

/** The camera image as a mirror, with makeup and an optional lesson highlight drawn on the face. */
@Composable
fun MirrorView(
    frame: TrackedFrame?,
    plan: RenderPlan,
    highlight: Region?,
    showMakeup: Boolean,
    modifier: Modifier = Modifier,
) {
    val smoother = remember { LandmarkSmoother() }
    val renderer = remember { MakeupRenderer() }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val pulse = rememberInfiniteTransition(label = "highlight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val f = frame ?: return@Canvas
        val mapper = LandmarkMapper(f.bitmap.width, f.bitmap.height, size.width, size.height, mirror = true)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(-1f, 1f, size.width / 2, size.height / 2)
            native.drawBitmap(
                f.bitmap,
                null,
                RectF(mapper.offsetX, mapper.offsetY, mapper.offsetX + f.bitmap.width * mapper.scale, mapper.offsetY + f.bitmap.height * mapper.scale),
                bitmapPaint,
            )
            native.restore()
            val landmarks = f.landmarks ?: return@drawIntoCanvas
            val points = smoother.smooth(mapper.mapAll(landmarks), f.timeMs / 1000.0)
            if (showMakeup) renderer.draw(native, plan, points)
            if (highlight != null) renderer.drawHighlight(native, highlight, points, pulse.value)
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, and all earlier tests still pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add camera feed, face tracking and makeup renderer"
```

---

### Task 13: App services, settings, permission gate and share button

**Files:**
- Create: `$MAIN/AppGraph.kt`, `$MAIN/Prefs.kt`, `$MAIN/ui/Theme.kt`, `$MAIN/ui/CameraPermissionGate.kt`, `$MAIN/ui/WelcomeScreen.kt`, `$MAIN/ui/SettingsScreen.kt`, `$MAIN/ui/ShareApp.kt`
- Modify: `$MAIN/MakeupMirrorApp.kt` (full replacement)

**Interfaces:**
- Consumes: `UpdateChecker`, `UpdateController`, `UpdateInstaller` (Tasks 2 and 3), `AppDatabase` (Task 5), `LookLibrary`, `Look` (Task 6), `FaceProfile`, `label()` (Task 8).
- Produces: `class AppGraph(app: Application)` with `prefs: Prefs`, `db: AppDatabase`, `looks: List<Look>` (lazy), `installer: UpdateInstaller` and `updates: UpdateController`. `val Context.graph: AppGraph`. `class Prefs(context)` with `profile: StateFlow<FaceProfile?>`/`setProfile`, `strength: StateFlow<Float>`/`setStrength`, `readAloud: StateFlow<Boolean>`/`setReadAloud`, and `var onboarded: Boolean`. `@Composable MakeupMirrorTheme(content)`. `@Composable CameraPermissionGate(content)`. `@Composable WelcomeScreen(onDone)`. `@Composable SettingsScreen(graph)`. `fun shareAppIntent(): Intent`.

- [ ] **Step 1: Services and preferences**

<!-- file: $MAIN/Prefs.kt -->
```kotlin
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
```

<!-- file: $MAIN/AppGraph.kt -->
```kotlin
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
```

<!-- file: $MAIN/MakeupMirrorApp.kt -->
```kotlin
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
```

- [ ] **Step 2: Theme, permission gate and welcome**

<!-- file: $MAIN/ui/Theme.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF8E4A5C),
    background = Color(0xFFFFF8F9),
    surface = Color(0xFFFFF8F9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    secondary = Color(0xFFE3BDC6),
)

@Composable
fun MakeupMirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
```

<!-- file: $MAIN/ui/CameraPermissionGate.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Shows [content] once the camera permission is granted, otherwise explains why it is needed. */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose {}
    }

    if (granted) {
        content()
        return
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Makeup Mirror needs the camera", style = MaterialTheme.typography.titleLarge)
        Text("The camera works as your mirror so you can see makeup on your face. Nothing is recorded or sent anywhere.")
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        if (asked) {
            Text("If Android won't ask again, turn the camera on in the app's settings.")
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Open app settings") }
        }
    }
}
```

<!-- file: $MAIN/ui/WelcomeScreen.kt -->
```kotlin
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
```

- [ ] **Step 3: Share and settings**

<!-- file: $MAIN/ui/ShareApp.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import android.content.Intent
import io.github.nugggy.makeupmirror.update.UpdateChecker

/** Android share sheet with a link that always downloads the newest APK. */
fun shareAppIntent(): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Makeup Mirror")
        .putExtra(Intent.EXTRA_TEXT, "Try Makeup Mirror on your Android phone: ${UpdateChecker.SHARE_URL}")
    return Intent.createChooser(send, "Share Makeup Mirror")
}
```

<!-- file: $MAIN/ui/SettingsScreen.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.BuildConfig
import io.github.nugggy.makeupmirror.face.FaceProfile
import io.github.nugggy.makeupmirror.face.label
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(graph: AppGraph) {
    val readAloud by graph.prefs.readAloud.collectAsStateWithLifecycle()
    val strength by graph.prefs.strength.collectAsStateWithLifecycle()
    val profile by graph.prefs.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Read lesson steps aloud", Modifier.weight(1f))
            Switch(checked = readAloud, onCheckedChange = graph.prefs::setReadAloud)
        }

        Text("Makeup strength in the preview: ${(strength * 100).roundToInt()}%")
        Slider(value = strength, onValueChange = graph.prefs::setStrength, valueRange = 0.3f..1.5f)

        HorizontalDivider()
        Text("Face analysis", style = MaterialTheme.typography.titleMedium)
        Text(profile?.let(::describe) ?: "Not analysed yet. Open Try on and look at the camera in good light.")
        Text(
            "These are rough guides based on the camera, and lighting can change the undertone result.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { graph.prefs.setProfile(null) }, enabled = profile != null) { Text("Analyse my face again") }

        HorizontalDivider()
        Button(onClick = { context.startActivity(shareAppIntent()) }) {
            Icon(Icons.Default.Share, contentDescription = null)
            Text("  Share app")
        }
        OutlinedButton(onClick = { graph.updates.checkNow(manual = true) }) { Text("Check for updates") }
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nugggy/makeup-mirror/blob/main/SOURCES.md")))
        }) { Text("Image sources") }
    }
}

private fun describe(p: FaceProfile): String = buildString {
    append("Face shape: ${p.faceShape.label()}. Eyes: ${p.eyeShape.label()}. Lips: ${p.lips.label()}.")
    p.undertone?.let { append(" Undertone: ${it.label()}.") }
    p.depth?.let { append(" Skin depth: ${it.label()}.") }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add app services, settings and share button"
```

---

### Task 14: My kit screens and swatch sampler

**Files:**
- Create: `$MAIN/ui/AssetImage.kt`, `$MAIN/ui/KitScreen.kt`, `$MAIN/ui/EditProductScreen.kt`, `$MAIN/ui/SwatchSampler.kt`, `$MAIN/ui/ColourPicker.kt`

**Interfaces:**
- Consumes: `AppGraph` (Task 13), `Product`, `ProductType`, `Finish`, `ProductForm`, `ProductDao` (Task 5), `CameraFeed` (Task 12), `ColourSampler` (Task 4), `CameraPermissionGate` (Task 13).
- Produces: `@Composable KitScreen(graph, onEdit: (Long?) -> Unit)`, `@Composable EditProductScreen(graph, productId: Long?, onDone: () -> Unit)`, `@Composable SwatchSampler(onCapture: (Int) -> Unit, onCancel: () -> Unit)`, `@Composable ColourPicker(colour: Int, onChange: (Int) -> Unit)`, and `@Composable AssetImage(path: String, modifier)`, which loads `assets/styles/<path>`.

- [ ] **Step 1: Write the screens**

<!-- file: $MAIN/ui/AssetImage.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/** An image from assets/styles/, or a blank tile if it is missing. */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        runCatching { context.assets.open("styles/$path").use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
```

<!-- file: $MAIN/ui/KitScreen.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.kit.Product

@Composable
fun KitScreen(graph: AppGraph, onEdit: (Long?) -> Unit) {
    val products by graph.db.products().observeAll().collectAsStateWithLifecycle(emptyList())
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add product") },
            )
        },
    ) { padding ->
        if (products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Your kit is empty. Tap Add product, then point the camera at each product's colour.",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(products, key = { it.id }) { product -> ProductCard(product) { onEdit(product.id) } }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(product.colour))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${product.type.label} · ${product.finish.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

<!-- file: $MAIN/ui/ColourPicker.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Hue, saturation and brightness sliders, for adjusting a captured shade by hand. */
@Composable
fun ColourPicker(colour: Int, onChange: (Int) -> Unit) {
    val hsv = remember(colour) { FloatArray(3).also { android.graphics.Color.colorToHSV(colour, it) } }
    val channels = listOf(Triple("Hue", 0, 0f..360f), Triple("Saturation", 1, 0f..1f), Triple("Brightness", 2, 0f..1f))
    Column {
        channels.forEach { (name, index, range) ->
            Text(name, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hsv[index],
                valueRange = range,
                onValueChange = { value ->
                    val next = hsv.copyOf()
                    next[index] = value
                    onChange(android.graphics.Color.HSVToColor(next))
                },
            )
        }
    }
}
```

<!-- file: $MAIN/ui/SwatchSampler.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nugggy.makeupmirror.camera.CameraFeed
import io.github.nugggy.makeupmirror.colour.ColourSampler
import kotlin.math.min

/** Live camera with a target circle. The colour inside the circle is averaged, ignoring glare. */
@Composable
fun SwatchSampler(onCapture: (Int) -> Unit, onCancel: () -> Unit) {
    CameraPermissionGate {
        val context = LocalContext.current
        val owner = LocalLifecycleOwner.current
        var lens by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
        var frame by remember { mutableStateOf<Bitmap?>(null) }
        var sampled by remember { mutableStateOf<Int?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val feed = remember { CameraFeed(context) }

        DisposableEffect(owner, lens) {
            error = null
            feed.start(owner, lens, onFrame = { bitmap, _ ->
                sampled = sampleCentre(bitmap)
                frame = bitmap
            }, onError = { error = "Couldn't open that camera. Try switching cameras." })
            onDispose { feed.stop() }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            frame?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.align(Alignment.Center).size(72.dp).border(3.dp, Color.White, CircleShape))
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Swatch a little of the product on white paper or the back of your hand, then fill the circle with it. Use even daylight if you can.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(sampled?.let { Color(it) } ?: Color.Gray))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Cancel") }
                        TextButton(onClick = {
                            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        }) { Text("Switch camera") }
                        Button(onClick = { sampled?.let(onCapture) }, enabled = sampled != null) { Text("Capture") }
                    }
                }
            }
        }
    }
}

private fun sampleCentre(bitmap: Bitmap): Int {
    val radius = min(bitmap.width, bitmap.height) / 20
    val size = radius * 2 + 1
    val pixels = IntArray(size * size)
    bitmap.getPixels(pixels, 0, size, bitmap.width / 2 - radius, bitmap.height / 2 - radius, size, size)
    return ColourSampler.average(ColourSampler.circle(pixels, size, size, radius, radius, radius))
}
```

<!-- file: $MAIN/ui/EditProductScreen.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.kit.Finish
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductForm
import io.github.nugggy.makeupmirror.kit.ProductType
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProductScreen(graph: AppGraph, productId: Long?, onDone: () -> Unit) {
    val dao = graph.db.products()
    val scope = rememberCoroutineScope()
    val kit by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    var existing by remember { mutableStateOf<Product?>(null) }
    var loaded by remember { mutableStateOf(productId == null) }
    var type by rememberSaveable { mutableStateOf<ProductType?>(null) }
    var colour by rememberSaveable { mutableIntStateOf(0xFFC48A7A.toInt()) }
    var name by rememberSaveable { mutableStateOf("") }
    var finish by rememberSaveable { mutableStateOf(Finish.MATTE) }
    var sampling by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(productId) {
        if (productId != null) {
            dao.get(productId)?.let {
                existing = it
                type = it.type
                colour = it.colour
                name = it.name
                finish = it.finish
            }
        }
        loaded = true
    }
    if (!loaded) return
    if (sampling) {
        SwatchSampler(onCapture = { colour = it; sampling = false }, onCancel = { sampling = false })
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (existing == null) "Add product" else "Edit product", style = MaterialTheme.typography.headlineSmall)
        Text("What is it?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
        }
        val chosen = type ?: return@Column

        Text("Shade", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color(colour))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Button(onClick = { sampling = true }) { Text("Capture with camera") }
        }
        Text("Fine-tune the shade if the camera didn't get it quite right:", style = MaterialTheme.typography.bodySmall)
        ColourPicker(colour) { colour = it }

        Text("Finish", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Finish.entries.forEach { f -> FilterChip(selected = finish == f, onClick = { finish = f }, label = { Text(f.label) }) }
        }

        val defaultName = ProductForm.defaultName(chosen, kit.filter { it.id != existing?.id })
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            placeholder = { Text(defaultName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            existing?.let { product ->
                OutlinedButton(onClick = { scope.launch { dao.delete(product); onDone() } }) { Text("Delete") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Cancel") }
            Button(onClick = {
                scope.launch {
                    dao.upsert(
                        Product(
                            id = existing?.id ?: 0,
                            type = chosen,
                            name = name.trim().ifEmpty { defaultName },
                            colour = colour,
                            finish = finish,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    onDone()
                }
            }) { Text("Save") }
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add makeup kit screens with swatch capture"
```

---

### Task 15: Try on screen

**Files:**
- Create: `$MAIN/ui/Common.kt`, `$MAIN/ui/TryOnScreen.kt`

**Interfaces:**
- Consumes: `AppGraph` (Task 13), `StyleMatcher`, `MatchedLook` (Task 9), `RenderPlanner`, `RenderPlan`, `MirrorView` (Tasks 10 and 12), `rememberFaceCamera` (Task 12), `FaceAnalyser`, `ProfileCollector`, `Pt` (Tasks 7 and 8), `CameraPermissionGate`, `AssetImage` (Tasks 13 and 14).
- Produces: `@Composable TryOnScreen(graph, onAddProducts: () -> Unit, onChoose: (lookId: String) -> Unit)`. In `Common.kt`: `@Composable Hint(text)` and `@Composable ErrorPanel(message, onRetry)`.

- [ ] **Step 1: Write the screen**

<!-- file: $MAIN/ui/Common.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A small floating message over the mirror. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleLarge)
        Text(message)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
```

<!-- file: $MAIN/ui/TryOnScreen.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.camera.rememberFaceCamera
import io.github.nugggy.makeupmirror.face.FaceAnalyser
import io.github.nugggy.makeupmirror.face.ProfileCollector
import io.github.nugggy.makeupmirror.face.Pt
import io.github.nugggy.makeupmirror.matcher.MatchedLook
import io.github.nugggy.makeupmirror.matcher.StyleMatcher
import io.github.nugggy.makeupmirror.render.MirrorView
import io.github.nugggy.makeupmirror.render.RenderPlan
import io.github.nugggy.makeupmirror.render.RenderPlanner
import kotlinx.coroutines.delay

@Composable
fun TryOnScreen(graph: AppGraph, onAddProducts: () -> Unit, onChoose: (String) -> Unit) {
    val kit by graph.db.products().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val profile by graph.prefs.profile.collectAsStateWithLifecycle()
    val strength by graph.prefs.strength.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var before by remember { mutableStateOf(false) }

    val products = kit ?: return
    if (products.isEmpty()) {
        EmptyKit(onAddProducts)
        return
    }
    val matches = remember(products, profile) { StyleMatcher.match(products, profile, graph.looks) }
    val shown = if (showAll) matches else matches.filter { it.available }
    val selected = shown.firstOrNull { it.look.id == selectedId } ?: shown.firstOrNull()
    val plan = remember(selected, strength) {
        selected?.takeIf { it.available }?.let { RenderPlanner.plan(it, strength) } ?: RenderPlan.EMPTY
    }

    CameraPermissionGate {
        var attempt by remember { mutableIntStateOf(0) }
        val camera = rememberFaceCamera(attempt)
        val error = camera.error
        if (error != null) {
            ErrorPanel(error) { attempt++ }
            return@CameraPermissionGate
        }
        val frame = camera.frame
        val hasFace = frame?.landmarks != null
        var faceMissing by remember { mutableStateOf(false) }
        LaunchedEffect(hasFace, frame != null) {
            faceMissing = false
            if (frame != null && !hasFace) {
                delay(1000)
                faceMissing = true
            }
        }

        // Build the face profile once, from the most common answer over several frames.
        val collector = remember { ProfileCollector() }
        LaunchedEffect(frame) {
            val f = frame ?: return@LaunchedEffect
            val landmarks = f.landmarks ?: return@LaunchedEffect
            val skin = f.skinColour ?: return@LaunchedEffect
            if (graph.prefs.profile.value != null) return@LaunchedEffect
            val pixels = landmarks.map { Pt(it.x * f.bitmap.width, it.y * f.bitmap.height) }
            collector.add(FaceAnalyser.analyse(pixels, skin))?.let(graph.prefs::setProfile)
        }

        Box(Modifier.fillMaxSize()) {
            MirrorView(frame, plan, highlight = null, showMakeup = !before, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                when {
                    faceMissing -> Hint("Move your face into the frame, or try better light")
                    profile == null && hasFace -> Hint("Analysing your face for suggestions")
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showAll, onClick = { showAll = !showAll }, label = { Text("Show looks I can't do yet") })
                    FilterChip(selected = before, onClick = { before = !before }, label = { Text("Before") })
                }
                if (shown.isEmpty()) {
                    Text(
                        "None of the looks can be made with your kit yet. Turn on \"Show looks I can't do yet\" to see what's missing.",
                        Modifier.padding(horizontal = 12.dp),
                    )
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.look.id }) { match ->
                        LookCard(match, selected = match.look.id == selected?.look?.id) { selectedId = match.look.id }
                    }
                }
                selected?.let { match ->
                    if (match.available) {
                        Button(onClick = { onChoose(match.look.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Text("Teach me this look")
                        }
                    } else {
                        Text("To do this look you need: ${missingText(match)}", Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LookCard(match: MatchedLook, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(170.dp),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        AssetImage(match.look.image, Modifier.fillMaxWidth().height(96.dp))
        Column(Modifier.padding(8.dp)) {
            Text(match.look.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                match.suitsYou -> {
                    Text("Suits you", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(match.reasons.first(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                !match.available -> Text("Needs ${missingText(match)}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else -> Text(match.look.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun missingText(match: MatchedLook) = match.missing.joinToString { it.label.lowercase() }

@Composable
private fun EmptyKit(onAddProducts: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Add your makeup first", style = MaterialTheme.typography.titleLarge)
        Text("Try on only shows looks you can make with the products you own.", textAlign = TextAlign.Center)
        Button(onClick = onAddProducts) { Text("Go to My kit") }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add try on screen with live look previews"
```

---

### Task 16: Lesson screen with read-aloud

**Files:**
- Create: `$MAIN/lesson/Speaker.kt`, `$MAIN/ui/LessonScreen.kt`

**Interfaces:**
- Consumes: `LessonBuilder`, `LessonCard` (Task 10), `RenderPlanner` and `RenderPlan.forLessonStep` (Task 10), `StyleMatcher` (Task 9), `MirrorView`, `rememberFaceCamera` (Task 12), `AssetImage`, `Hint`, `ErrorPanel`, `CameraPermissionGate` (Tasks 13 to 15).
- Produces: `class Speaker(context)` with `speak(text)`, `stop()` and `shutdown()`, plus `@Composable rememberSpeaker(): Speaker`. `@Composable LessonScreen(graph, lookId: String, onExit: () -> Unit)`.

- [ ] **Step 1: Write the speaker and screen**

<!-- file: $MAIN/lesson/Speaker.kt -->
```kotlin
package io.github.nugggy.makeupmirror.lesson

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Text-to-speech in Australian English, falling back to British English. */
class Speaker(context: Context) {
    private var ready = false
    private var pending: String? = null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                if (tts.setLanguage(Locale.forLanguageTag("en-AU")) < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.UK)
                ready = true
                pending?.let(::speak)
                pending = null
            }
        }
    }

    fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lesson") else pending = text
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
```

<!-- file: $MAIN/ui/LessonScreen.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.camera.rememberFaceCamera
import io.github.nugggy.makeupmirror.lesson.LessonBuilder
import io.github.nugggy.makeupmirror.lesson.LessonCard
import io.github.nugggy.makeupmirror.lesson.rememberSpeaker
import io.github.nugggy.makeupmirror.matcher.StyleMatcher
import io.github.nugggy.makeupmirror.render.MirrorView
import io.github.nugggy.makeupmirror.render.RenderPlanner

@Composable
fun LessonScreen(graph: AppGraph, lookId: String, onExit: () -> Unit) {
    val kit by graph.db.products().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val profile by graph.prefs.profile.collectAsStateWithLifecycle()
    val strength by graph.prefs.strength.collectAsStateWithLifecycle()
    val readAloud by graph.prefs.readAloud.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var compare by rememberSaveable { mutableStateOf(false) }
    val speaker = rememberSpeaker()

    val products = kit ?: return
    val match = remember(products, profile) {
        StyleMatcher.match(products, profile, graph.looks).firstOrNull { it.look.id == lookId }
    }
    if (match == null || !match.available) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text("This look needs products that aren't in your kit any more.")
            Button(onClick = onExit) { Text("Back") }
        }
        return
    }
    val cards = remember(match) { LessonBuilder.build(match) }
    val fullPlan = remember(match, strength) { RenderPlanner.plan(match, strength) }
    val finished = step >= cards.size
    val card = cards.getOrNull(step)

    LaunchedEffect(step, readAloud) {
        if (readAloud && card != null) speaker.speak(spokenText(step, card)) else speaker.stop()
    }
    DisposableEffect(Unit) { onDispose { speaker.stop() } }

    CameraPermissionGate {
        var attempt by remember { mutableIntStateOf(0) }
        val camera = rememberFaceCamera(attempt)
        val error = camera.error
        if (error != null) {
            ErrorPanel(error) { attempt++ }
            return@CameraPermissionGate
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val plan = if (finished) fullPlan else fullPlan.forLessonStep(cards.map { it.role }, step)
                MirrorView(
                    frame = camera.frame,
                    plan = plan,
                    highlight = card?.region,
                    showMakeup = !finished || compare,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Top) {
                    TextButton(onClick = onExit) { Text("Exit") }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetImage(
                            match.look.image,
                            Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(12.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
                        )
                        if (!finished) Hint("Step ${step + 1} of ${cards.size}")
                    }
                }
                if (camera.frame != null && camera.frame.landmarks == null) {
                    Hint("Move your face into the frame", Modifier.align(Alignment.Center))
                }
            }
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (card != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(card.product.colour)))
                            Spacer(Modifier.width(8.dp))
                            Text("Your ${card.product.name}", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(card.instruction)
                        card.tip?.let { Text("Tip: $it", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { step-- }, enabled = step > 0) { Text("Back") }
                            TextButton(onClick = { speaker.speak(spokenText(step, card)) }) { Text("Read aloud") }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { step++ }) { Text(if (step == cards.lastIndex) "Finish" else "Next") }
                        }
                    } else {
                        Text("You've finished ${match.look.name}", style = MaterialTheme.typography.titleLarge)
                        Text("Compare your face with the picture in the corner. Turn on the overlay to see the target look on your face.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = compare, onClick = { compare = !compare }, label = { Text("Show target look") })
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { step = 0; compare = false }) { Text("Start again") }
                            Button(onClick = onExit) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
}

private fun spokenText(step: Int, card: LessonCard) =
    "Step ${step + 1}. Use your ${card.product.name}. ${card.instruction} ${card.tip?.let { "Tip. $it" } ?: ""}"
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add guided lesson screen with read-aloud"
```

---

### Task 17: Navigation, update prompt and first run on the emulator

**Files:**
- Create: `$MAIN/ui/AppRoot.kt`
- Modify: `$MAIN/MainActivity.kt` (full replacement)

**Interfaces:**
- Consumes: every screen (Tasks 13 to 16), `UpdateDialog` (Task 3), `AppGraph.updates` and `AppGraph.installer` (Task 13).
- Produces: the finished app. The launch update check runs once per cold start, and returning from the install-permission screen re-checks the permission.

- [ ] **Step 1: Write navigation and the activity**

<!-- file: $MAIN/ui/AppRoot.kt -->
```kotlin
package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.update.UpdateDialog

private object Routes {
    const val WELCOME = "welcome"
    const val TRY_ON = "tryon"
    const val KIT = "kit"
    const val SETTINGS = "settings"
    const val EDIT = "kit/edit?id={id}"
    const val LESSON = "lesson/{lookId}"
    fun edit(id: Long?) = "kit/edit?id=${id ?: -1}"
    fun lesson(lookId: String) = "lesson/$lookId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TRY_ON, "Try on", Icons.Default.Face),
    Tab(Routes.KIT, "My kit", Icons.AutoMirrored.Filled.List),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun AppRoot(graph: AppGraph) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val updateState by graph.updates.state.collectAsStateWithLifecycle()
    UpdateDialog(
        state = updateState,
        onUpdate = graph.updates::startDownload,
        onInstall = { apk ->
            graph.installer.install(apk)
            graph.updates.dismiss()
        },
        onOpenPermission = { context.startActivity(graph.installer.installPermissionIntent()) },
        onDismiss = graph.updates::dismiss,
    )

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.TRY_ON) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (graph.prefs.onboarded) Routes.TRY_ON else Routes.WELCOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(onDone = {
                    graph.prefs.onboarded = true
                    nav.navigate(Routes.KIT) { popUpTo(Routes.WELCOME) { inclusive = true } }
                })
            }
            composable(Routes.TRY_ON) {
                TryOnScreen(graph, onAddProducts = { nav.navigate(Routes.KIT) }, onChoose = { nav.navigate(Routes.lesson(it)) })
            }
            composable(Routes.KIT) { KitScreen(graph, onEdit = { nav.navigate(Routes.edit(it)) }) }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                EditProductScreen(graph, id, onDone = { nav.popBackStack() })
            }
            composable(Routes.LESSON) { entry ->
                LessonScreen(graph, entry.arguments?.getString("lookId").orEmpty(), onExit = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsScreen(graph) }
        }
    }
}
```

<!-- file: $MAIN/MainActivity.kt -->
```kotlin
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
```

- [ ] **Step 2: Build and run all tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: all unit tests pass and the build succeeds.

- [ ] **Step 3: Run on the emulator and walk through the app**

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/emulator/emulator" -avd Medium_Phone_API_36.1 -camera-front webcam0 -camera-back virtualscene -no-snapshot-save &
"$SDK/platform-tools/adb" wait-for-device && sleep 20
./gradlew installDebug
"$SDK/platform-tools/adb" shell am start -n io.github.nugggy.makeupmirror/.MainActivity
"$SDK/platform-tools/adb" exec-out screencap -p > "$SCRATCH/01-welcome.png"
```

If `webcam0` is not available, use `-camera-front emulated`. Face tracking then has no face to find, and the face checks move to the device test below.

Walk through these steps, taking a screenshot after each one with `adb exec-out screencap -p` and reading it. Drive taps with `adb shell input tap X Y` using coordinates from the screenshot.
1. Welcome shows, and Get started opens My kit (empty state text).
2. Add product: pick Lipstick, allow the camera, capture (or use the sliders), save. Then add Eyeliner and Mascara. The grid shows three cards with colour dots and default names such as "Lipstick 1".
3. Try on: the carousel shows looks, including Classic red lip and Winged liner. "Show looks I can't do yet" adds more looks with "Needs ..." text. With a face (webcam), the makeup follows the face, Before hides it, and after about 10 seconds the analysing hint disappears and some cards show "Suits you".
4. Teach me this look: the steps advance, the highlight pulses on the right region, read-aloud speaks, and Finish shows the comparison panel.
5. Settings: the share sheet opens with the latest-APK link, Check for updates shows "You're up to date" (or "New version" once releases exist), and the version reads 0.1.0.
6. Check for crashes: `adb logcat -d -s AndroidRuntime:E` must be empty.

Fix any problem with a test first where the logic is pure. Commit fixes separately.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "Wire up navigation and launch update check"
```

---

### Task 18: Look images

**Files:**
- Create: `app/src/main/assets/styles/img/<look-id>.jpg` (18 images), `SOURCES.md`
- Test: `$TEST/styles/LookAssetsTest.kt`

**Interfaces:**
- Consumes: `LookLibrary.parse` (Task 6).
- Produces: one reference photo per look, at most 800 px wide, JPEG quality 82. `SOURCES.md` lists the source of each image.

- [ ] **Step 1: Write the failing test**

<!-- file: $TEST/styles/LookAssetsTest.kt -->
```kotlin
package io.github.nugggy.makeupmirror.styles

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LookAssetsTest {
    private val looks = LookLibrary.parse(File("src/main/assets/styles/looks.json").readText())

    @Test
    fun everyLookImageExists() {
        looks.forEach { assertTrue("missing ${it.image}", File("src/main/assets/styles/${it.image}").isFile) }
    }

    @Test
    fun everyImageIsCreditedInSources() {
        val sources = File("../SOURCES.md").readText()
        looks.forEach { assertTrue("${it.image} not in SOURCES.md", it.image in sources) }
    }
}
```

Run: `./gradlew testDebugUnitTest --tests '*LookAssetsTest'`
Expected: FAIL with `missing img/natural.jpg`.

- [ ] **Step 2: Collect images with Claude in Chrome**

Load the Chrome tools in one ToolSearch call: `tabs_context_mcp`, `tabs_create_mcp`, `navigate`, `computer`, `javascript_tool`, `read_page` and `tabs_close_mcp`. Open a new tab. For each look in `looks.json` (id and name):
1. Navigate to `https://www.pinterest.com.au/search/pins/?q=<name> makeup look` (URL-encoded). If Pinterest shows a login wall, use the brand or editorial sites from Google Images results instead (for example `https://www.google.com/search?tbm=isch&q=<name>+makeup+look`).
2. List candidates with `javascript_tool`: `[...document.querySelectorAll('img')].filter(i => i.naturalWidth >= 400).slice(0, 12).map(i => ({src: i.currentSrc || i.src, alt: i.alt}))`. On Pinterest, swap `/236x/` for `/736x/` in the URL to get the larger version.
3. Pick a single-face, front-facing, clearly lit photo whose alt text matches the look. Download it with `curl -fL -o "$SCRATCH/raw/<id>.jpg" "<url>"` and view it with Read to confirm it shows the look.
4. Record the page URL and image URL for `SOURCES.md`.

- [ ] **Step 3: Resize into the assets folder**

<!-- file: tools/resize-image.ps1 -->
```powershell
# Resizes an image to at most $Width pixels wide and saves it as JPEG (quality 82).
param([Parameter(Mandatory)][string]$In, [Parameter(Mandatory)][string]$Out, [int]$Width = 800)
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile((Resolve-Path $In))
$w = [Math]::Min($Width, $img.Width)
$h = [int]($img.Height * $w / $img.Width)
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, $w, $h)
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 82L
$bmp.Save($Out, $codec, $params)
$g.Dispose(); $bmp.Dispose(); $img.Dispose()
```

For each image, run `powershell -File tools/resize-image.ps1 -In "$SCRATCH/raw/<id>.jpg" -Out app/src/main/assets/styles/img/<id>.jpg`. If a download is WebP, which System.Drawing can't read, re-fetch the `.jpg` variant or pick another image.

- [ ] **Step 4: Write `SOURCES.md`**

The file starts with this heading and paragraph, followed by one table row per image (all 18):

```text
# Image sources

The look reference photos in app/src/main/assets/styles/img/ were collected from the web for personal, non-commercial use in this app. Each is credited below. If you own one of these images and want it removed, open an issue and it will be taken down.

| Look | File | Found on | Image URL |
|------|------|----------|-----------|
| No-makeup makeup | img/natural.jpg | <page URL> | <image URL> |
```

- [ ] **Step 5: Run the test and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*LookAssetsTest' assembleDebug`
Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "Add look reference images with sources"
```

---

### Task 19: Publish to GitHub with signed releases, and prove the update path

**Files:**
- Create: `.github/workflows/release.yml`, `README.md`
- External: the public repo `nugggy/makeup-mirror`, the release keystore (outside the repo, at the location the user chose), and the repository secrets.

**Interfaces:**
- Consumes: the `RELEASE_VERSION`/`KEYSTORE_*` environment contract in `app/build.gradle.kts` (Task 1), and `UpdateChecker.LATEST_URL`/`SHARE_URL` (Task 2).
- Produces: GitHub Releases `v0.1.0` and `v0.1.1`, each with `makeup-mirror-<version>.apk` and `makeup-mirror.apk`.

- [ ] **Step 1: Write the release workflow and README**

<!-- file: .github/workflows/release.yml -->
```yaml
name: Release

on:
  push:
    tags: ['v*.*.*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode signing key
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"
      - name: Test and build signed APK
        env:
          RELEASE_VERSION: ${{ github.ref_name }}
          KEYSTORE_FILE: ${{ runner.temp }}/release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew --no-daemon testDebugUnitTest assembleRelease
      - name: Name the APKs
        run: |
          VERSION="${GITHUB_REF_NAME#v}"
          cp app/build/outputs/apk/release/app-release.apk "makeup-mirror-$VERSION.apk"
          cp app/build/outputs/apk/release/app-release.apk makeup-mirror.apk
      - uses: softprops/action-gh-release@v2
        with:
          files: |
            makeup-mirror-*.apk
            makeup-mirror.apk
          generate_release_notes: true
```

<!-- file: README.md -->
```markdown
# Makeup Mirror

An Android app that shows makeup looks on your face through the front camera, using only the makeup you own, then teaches you step by step how to apply the look you pick.

## Install

On your Android phone (Android 8 or newer), download the latest APK:

https://github.com/nugggy/makeup-mirror/releases/latest/download/makeup-mirror.apk

Open it and allow your browser or file manager to install apps when Android asks.

## Updates

The app checks this repository's latest release each time it opens. When a newer version is published it offers to download and install it. Your saved makeup kit is kept. You can also share the download link from Settings.

## Privacy

The camera is used as a live mirror. Frames are processed on the phone and never saved or uploaded. The only internet access is the update check and download from GitHub.

## Building

Requires JDK 17 and the Android SDK. Run ./gradlew testDebugUnitTest assembleDebug. Releases are built by GitHub Actions when a vMAJOR.MINOR.PATCH tag is pushed.

## Credits

Face tracking uses Google MediaPipe Face Landmarker. Look reference image sources are listed in SOURCES.md.
```

- [ ] **Step 2: Create the public repo and push**

```bash
git add -A && git commit -m "Add release workflow and README"
gh repo create nugggy/makeup-mirror --public --source . --remote origin --description "AR makeup try-on and step-by-step lessons for Android" --push
gh run watch "$(gh run list --workflow CI --limit 1 --json databaseId -q '.[0].databaseId')" --exit-status
```

Expected: the CI run finishes green.

- [ ] **Step 3: Create the signing key and store it as secrets**

Use the backup folder the user chose (`$KEY_DIR`). Generate a strong password, then create the key:

```bash
KEY_PASS=$(node -e "console.log(require('crypto').randomBytes(24).toString('base64url'))")
keytool -genkeypair -v -keystore "$KEY_DIR/makeup-mirror-release.jks" -alias makeupmirror -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$KEY_PASS" -keypass "$KEY_PASS" -dname "CN=Makeup Mirror"
printf 'Keystore: makeup-mirror-release.jks\nAlias: makeupmirror\nPassword: %s\n' "$KEY_PASS" > "$KEY_DIR/makeup-mirror-release-password.txt"
base64 -w0 "$KEY_DIR/makeup-mirror-release.jks" | gh secret set KEYSTORE_BASE64
printf '%s' "$KEY_PASS" | gh secret set KEYSTORE_PASSWORD
printf '%s' "$KEY_PASS" | gh secret set KEY_PASSWORD
printf 'makeupmirror' | gh secret set KEY_ALIAS
gh secret list
```

Expected: four secrets are listed. Never print `$KEY_PASS` to the transcript. Tell the user where the key and password file are, and recommend moving the password into a password manager.

- [ ] **Step 4: Release v0.1.0**

```bash
git tag v0.1.0 && git push origin v0.1.0
gh run watch "$(gh run list --workflow Release --limit 1 --json databaseId -q '.[0].databaseId')" --exit-status
gh release view v0.1.0 --json assets -q '.assets[].name'
curl -sIL https://github.com/nugggy/makeup-mirror/releases/latest/download/makeup-mirror.apk | grep -i '^HTTP'
```

Expected: the assets are `makeup-mirror-0.1.0.apk` and `makeup-mirror.apk`, and the share link ends in `HTTP/2 200`.

- [ ] **Step 5: Prove the update path on the emulator**

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
$ADB uninstall io.github.nugggy.makeupmirror || true   # the debug build is signed with a different key
curl -fL -o "$SCRATCH/v010.apk" https://github.com/nugggy/makeup-mirror/releases/download/v0.1.0/makeup-mirror-0.1.0.apk
$ADB install "$SCRATCH/v010.apk"
```

Make a small real change for v0.1.1 (for example a README note), then commit, tag `v0.1.1`, push and wait for the Release run as in Step 4. Then launch the app on the emulator:

```bash
$ADB shell am force-stop io.github.nugggy.makeupmirror
$ADB shell am start -n io.github.nugggy.makeupmirror/.MainActivity
sleep 5 && $ADB exec-out screencap -p > "$SCRATCH/update-prompt.png"
```

Expected: the screenshot shows "New version available, Version 0.1.1". Tap Update. The first time, the "One more step" permission dialog appears, so open settings, allow, and come back. Then tap Install and confirm. Afterwards `$ADB shell dumpsys package io.github.nugggy.makeupmirror | grep versionName` shows `versionName=0.1.1`, and the kit is still there.

- [ ] **Step 6: Tell the user how to install on the phone**

Give the user the share link and explain how to install it the first time (allow the browser to install apps). Explain that later updates are offered inside the app. Ask the user to test face tracking on the real phone in good light, and to report any looks where the overlay sits in the wrong place, because landmark indices and thresholds may need tuning on a real face.
