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
