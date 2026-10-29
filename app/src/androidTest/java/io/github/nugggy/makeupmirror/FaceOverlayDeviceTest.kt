package io.github.nugggy.makeupmirror

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nugggy.makeupmirror.camera.FaceTracker
import io.github.nugggy.makeupmirror.colour.ColourMath
import io.github.nugggy.makeupmirror.face.Pt
import io.github.nugggy.makeupmirror.face.Region
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.matcher.StyleMatcher
import io.github.nugggy.makeupmirror.render.MakeupRenderer
import io.github.nugggy.makeupmirror.render.RenderPlanner
import io.github.nugggy.makeupmirror.styles.LookLibrary
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real face tracker and makeup renderer on a bundled face photo.
 * The rendered images are saved to the app's external files folder for a visual check.
 */
@RunWith(AndroidJUnit4::class)
class FaceOverlayDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(context.getExternalFilesDir(null), "overlay-check").apply { mkdirs() }

    private fun photo(name: String): Bitmap =
        context.assets.open("styles/img/$name.jpg").use(BitmapFactory::decodeStream).copy(Bitmap.Config.ARGB_8888, true)

    private fun landmarks(bitmap: Bitmap): List<Pt> {
        FaceTracker(context).use { tracker ->
            repeat(3) { tracker.process(bitmap, 1L + it * 33) }
            val found = tracker.frames.value?.landmarks
            assertNotNull("No face found in photo", found)
            return found!!.map { Pt(it.x * bitmap.width, it.y * bitmap.height) }
        }
    }

    @Test
    fun drawsEachLookOnARealFace() {
        val face = photo("natural")
        val points = landmarks(face)
        val looks = LookLibrary.load(context)
        for (id in listOf("classic-red", "smoky", "sun-kissed", "rosy", "winged-liner")) {
            val look = looks.first { it.id == id }
            val kit = look.layers.mapIndexed { i, layer -> Product(i + 1L, layer.type, layer.role, ColourMath.parseHex(layer.colour)) }
            val match = StyleMatcher.match(kit, null, listOf(look)).single()
            val out = face.copy(Bitmap.Config.ARGB_8888, true)
            MakeupRenderer().draw(Canvas(out), RenderPlanner.plan(match), points)
            File(outDir, "$id.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (id == "classic-red") {
                val mouth = Pt((points[13].x + points[14].x) / 2, (points[0].y + points[13].y) / 2)
                val before = face.getPixel(mouth.x.toInt(), mouth.y.toInt())
                val after = out.getPixel(mouth.x.toInt(), mouth.y.toInt())
                assertTrue("Lipstick did not change the top lip", ColourMath.deltaE(before, after) > 5)
            }
        }
    }

    @Test
    fun drawsRegionOutlinesForAlignmentCheck() {
        val face = photo("natural")
        val points = landmarks(face)
        val out = face.copy(Bitmap.Config.ARGB_8888, true)
        val renderer = MakeupRenderer()
        Region.entries.forEach { renderer.drawHighlight(Canvas(out), it, points, 1f) }
        File(outDir, "regions.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
