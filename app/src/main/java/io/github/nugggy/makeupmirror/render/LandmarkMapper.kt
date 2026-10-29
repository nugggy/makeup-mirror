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
