package nz.myinspection.core.media

/** Positive pixel dimensions after the EXIF-corrected bitmap is capped for a new photo. */
data class PhotoDimensions(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "photo width must be positive: $width" }
        require(height > 0) { "photo height must be positive: $height" }
    }
}

/**
 * The one contract for newly written photo bytes. Existing assets retain their already-recorded bytes and hashes;
 * callers snapshot one profile at the start of a new camera/import operation.
 */
enum class PhotoQualityProfile(
    val storedValue: String,
    val maximumLongEdgePx: Int,
    val jpegQuality: Int,
) {
    LOW(storedValue = "low", maximumLongEdgePx = 1280, jpegQuality = 75),
    MEDIUM(storedValue = "medium", maximumLongEdgePx = 1920, jpegQuality = 82),
    HIGH(storedValue = "high", maximumLongEdgePx = 2560, jpegQuality = 88),
    EXTRA_HIGH(storedValue = "extra_high", maximumLongEdgePx = 4096, jpegQuality = 92),
    ;

    /** Caps an EXIF-corrected bitmap proportionally. Sources already within the profile cap are left untouched. */
    fun scaledDimensions(sourceWidth: Int, sourceHeight: Int): PhotoDimensions {
        require(sourceWidth > 0) { "photo width must be positive: $sourceWidth" }
        require(sourceHeight > 0) { "photo height must be positive: $sourceHeight" }

        val sourceLongEdge = maxOf(sourceWidth, sourceHeight)
        if (sourceLongEdge <= maximumLongEdgePx) return PhotoDimensions(sourceWidth, sourceHeight)
        return PhotoDimensions(
            width = scaleDimension(sourceWidth, sourceLongEdge),
            height = scaleDimension(sourceHeight, sourceLongEdge),
        )
    }

    private fun scaleDimension(sourceDimension: Int, sourceLongEdge: Int): Int =
        ((sourceDimension.toLong() * maximumLongEdgePx + sourceLongEdge / 2) / sourceLongEdge)
            .toInt()
            .coerceAtLeast(1)

    companion object {
        val DEFAULT: PhotoQualityProfile = MEDIUM

        /** Missing/unknown persisted values safely retain the published default rather than inventing a new profile. */
        fun fromStoredValue(value: String?): PhotoQualityProfile = entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

/**
 * Port for the app's persistent setting. Each ingest operation calls [snapshotForNewPhoto] exactly once, then passes
 * the returned immutable profile through orientation correction, scaling, and encoding.
 */
fun interface PhotoQualityProfileSource {
    fun snapshotForNewPhoto(): PhotoQualityProfile
}
