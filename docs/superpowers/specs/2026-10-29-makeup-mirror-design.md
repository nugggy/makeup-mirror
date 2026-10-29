# Makeup Mirror design

Date: 29/10/2026
Repo: github.com/nugggy/makeup-mirror (public)
App name: Makeup Mirror
Package: `io.github.nugggy.makeupmirror`

## Purpose

A personal Android app for one user (the owner's daughter). She records the makeup she owns, previews makeup looks on her live face using augmented reality, picks the look she likes, and is then taught step by step how to apply it with her own products. The app suggests looks that suit her face where it can.

The app is not distributed beyond this user. The repo is public so the in-app update check can read GitHub Releases without a token.

## Success criteria

- She can add a product by type and capture its shade from a real swatch in under a minute.
- Try On shows only looks achievable with her kit, rendered on her live face at a smooth frame rate (target 24 fps or better on a mid-range phone).
- Overlays stay attached to the correct facial features as she moves her head.
- Each look has a guided lesson that highlights where each product goes on her live face.
- Opening the app detects a newer GitHub release and installs it in two taps.
- No camera frames or face data leave the phone.

## Platform and stack

- Kotlin, Jetpack Compose (Material 3), single `app` module.
- minSdk 26 (Android 8.0), targetSdk and compileSdk 35.
- CameraX (front camera, `ImageAnalysis` plus `PreviewView`).
- MediaPipe Tasks Vision `FaceLandmarker` (478 landmarks, live stream mode, bundled `face_landmarker.task` model).
- Room for local storage.
- Android `TextToSpeech` for read-aloud.
- OkHttp plus kotlinx.serialization for the update check.
- Gradle wrapper, JDK 17. Built locally and by GitHub Actions.

## Components

Each component lives in its own package with a narrow interface.

### update

- `UpdateChecker.check(): UpdateInfo?` calls `https://api.github.com/repos/nugggy/makeup-mirror/releases/latest`, reads `tag_name` (format `vMAJOR.MINOR.PATCH`) and the first `.apk` asset.
- `Version.compare(a, b)` does semantic version comparison against `BuildConfig.VERSION_NAME`.
- Runs once per launch in the background with a 10 second timeout. Failure is logged and ignored.
- `UpdateInstaller` downloads the APK to app cache with progress, then launches the package installer through a `FileProvider` URI. It needs `REQUEST_INSTALL_PACKAGES`. If installs from this source are not allowed, it opens the relevant settings screen with an explanation.
- Settings has a manual "Check for updates" button.

### release pipeline

- `.github/workflows/release.yml` triggers on `v*` tags. It builds `assembleRelease`, signs with a keystore held in repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), and creates a GitHub Release with `makeup-mirror-<version>.apk` attached, plus an identical copy named `makeup-mirror.apk` so the link `https://github.com/nugggy/makeup-mirror/releases/latest/download/makeup-mirror.apk` always points to the newest build.
- `versionName` comes from the tag. `versionCode` = MAJOR*10000 + MINOR*100 + PATCH.
- The keystore is generated once locally and never committed. The owner keeps a backup, because losing it means future updates cannot install over the existing app.
- `.github/workflows/ci.yml` runs unit tests and a debug build on pushes and pull requests.

### face

- `FaceTracker` wraps CameraX and FaceLandmarker and exposes `StateFlow<FaceFrame?>`. `FaceFrame` holds normalised landmarks, image size, mirror flag and timestamp.
- `FaceRegions` defines landmark index lists for lips (outer and inner), left and right eyelid and crease, lash lines, brows, cheek apples, cheekbones, jawline, temples, nose bridge, forehead and the face oval.
- `FaceAnalyser` produces a `FaceProfile` from a few stable frames:
  - face shape (oval, round, square, heart, long) from ratios of face length, cheekbone width, jaw width and forehead width
  - eye shape (almond, round, hooded, monolid, upturned, downturned) from eye aspect ratio, canthal tilt and lid-to-crease distance
  - lip fullness (thin, medium, full)
  - skin undertone (warm, cool, neutral) and depth (fair, light, medium, tan, deep) from the average colour of cheek and forehead patches in Lab space, with a note that lighting affects this
- The profile is cached and can be refreshed from Settings.

### kit

- Room entity `Product(id, type, name, colourArgb, finish, createdAt)`.
- `ProductType` enum: FOUNDATION, CONCEALER, POWDER, BLUSH, BRONZER, CONTOUR, HIGHLIGHTER, EYESHADOW, EYELINER, MASCARA, BROW, LIPSTICK, LIP_GLOSS, LIP_LINER.
- `Finish` enum: MATTE, SATIN, SHIMMER, GLOSS.
- `SwatchSampler` screen uses the back camera by default, with a switch to the front. A centre circle averages pixels with outlier rejection, and there is a manual HSV picker fallback. It shows the sampled colour next to the live view.

### styles

- Looks are defined in `assets/styles/looks.json`. Each `Look` has id, name, description, tags, reference image path, `required` and `optional` product types, colour roles (for example `lip`, `lid`, `crease`, `cheek`) with a target colour family, face-fit rules, and ordered `LessonStep`s.
- `LessonStep` has the product type, region id, instruction text, optional tip and optional reference image.
- The initial library has about 18 looks: natural no-makeup, fresh dewy, soft glam, classic red lip, berry lip, nude glam, sun-kissed bronze, rosy romantic, peachy fresh, smoky eye, brown smoky, cut crease, winged liner, graphic liner, glitter party, soft pink monochrome, festival colour, school-day subtle.

### matcher

- `StyleMatcher.match(kit, profile, looks): List<MatchedLook>`
  - A look is available when every `required` type is in the kit.
  - For each colour role it chooses the kit product of that type closest to the role's target family (CIEDE2000 distance).
  - The fit score comes from rules: undertone against warm or cool colour families, eye shape against liner and shadow techniques, face shape against contour and blush placement, and skin depth against highlighter and lip intensity. Each rule that fires adds a short human-readable reason.
  - Results are sorted by fit score. The top three get a "Suits you" badge with their reasons.
- Pure Kotlin, with no Android dependencies, so it is fully unit-testable.

### render

- `MakeupRenderer` draws on a Compose `Canvas` overlaid on `PreviewView` and maps landmarks to view coordinates, handling mirroring and crop scaling.
- Layers are drawn in application order: foundation tint, contour, bronzer, blush, highlighter, eyeshadow (lid, crease), eyeliner (including wing), mascara (darkened lash line), brows, lip liner, lipstick, gloss.
- Techniques:
  - filled region paths from landmark polygons
  - radial or blurred gradients for blush, bronzer and highlight
  - `BlendMode.Multiply` or `Overlay` for pigment, and `Screen` for highlight and gloss
  - finish-dependent shine
  - landmark smoothing (one-euro filter) to reduce jitter
- Overlay strength is set by a global slider in Settings, with a per-look intensity.
- `RenderPlan` is produced from a `MatchedLook` (layer, region, colour, opacity) so the renderer has no knowledge of looks or the kit.
- Highlight mode: `renderHighlight(regionId)` draws a pulsing outline used by the lesson player.

### lesson

- `LessonPlayer` holds step index state. Each step renders the look progressively: steps already done are drawn faintly, the current zone is highlighted, and future steps are hidden.
- The UI shows step text with her product name and colour chip ("Use your *Rose Petal* blush"), a reference image, read-aloud via `TextToSpeech` (on by default, toggle in Settings), and Back and Next.
- The final screen shows a snapshot of her live face beside the look's reference image, with buttons to retry or finish.

## Screens and flow

1. **Launch** starts the update check in the background. If an update is found, an Update / Later dialog appears.
2. **First run** shows a welcome, asks for camera permission, and goes to My Kit.
3. **My Kit** shows a product grid with a + button. Adding a product goes type picker, swatch sampler, name and finish, save. Long press edits or deletes.
4. **Try On** shows the live mirror with an overlay and a carousel of matched looks (badge plus reasons). It has a before/after toggle, a "show looks I can't do yet" toggle that lists the missing products, and a Choose button that opens the lesson.
5. **Lesson** is as described above.
6. **Settings** has read-aloud, overlay strength, re-analyse face, check for updates, a **Share app** button, version and image sources. Share app opens the Android share sheet with the latest-APK link above.

Bottom navigation: Try On, My Kit, Settings.

## Images

Look reference and step images are collected with Claude in Chrome from the web, including Pinterest and beauty brand sites, at the owner's direction. They are stored in `app/src/main/assets/styles/img/` and resized to 800 px wide JPEG. `SOURCES.md` records the original URL for each image so any image can be removed quickly on request. Icons and region diagrams are drawn in code or as vector drawables.

## Privacy

- Camera frames are processed in memory only and are never saved, except the optional end-of-lesson snapshot, which stays in app storage until deleted.
- The face profile and kit are stored locally.
- The only network call is the GitHub Releases check and APK download.
- No analytics and no accounts.

## Error handling

- Camera permission denied: an explanation screen with a button to app settings.
- No face detected for more than 1 second: the overlay hides and a "Move into frame / try better light" hint shows.
- Empty kit: Try On shows a prompt to add products.
- No looks available: the app shows the closest looks and which products are missing.
- Update check failure: silent. Download failure: a retry button. Installs blocked: a settings walkthrough.
- MediaPipe initialisation failure: an error screen with details and a retry.

## Testing

- Unit tests (JVM): `Version` comparison, `StyleMatcher` availability, colour assignment and fit rules, colour maths (sRGB to Lab, CIEDE2000), `FaceAnalyser` classification on fixed landmark fixtures, `looks.json` schema validation (every region id, product type and image path exists).
- Instrumented or Robolectric: `RenderPlan` to canvas on sample landmark sets, with snapshot comparison.
- CI runs unit tests and assembles a debug build on every push.
- Manual verification on the emulator with a webcam or a physical device covers tracking, overlay alignment, lessons, and the update path (install v0.1.0, publish v0.1.1, confirm the prompt and install).

## Out of scope

- Accounts, cloud sync and social features (the only sharing is the Share app link).
- Before-and-after automated feedback on application quality.
- iOS.
- Play Store distribution.
