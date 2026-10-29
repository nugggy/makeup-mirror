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
