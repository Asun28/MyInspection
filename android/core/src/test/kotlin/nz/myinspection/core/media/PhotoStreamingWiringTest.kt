package nz.myinspection.core.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bitmap has no JVM test runtime in this project. Keep this as a narrow adapter guard only; lifecycle behavior is
 * executable in [VerifiedAssetWorkflowTest] and [StreamFileStagerTest].
 */
class PhotoStreamingWiringTest {
    @Test
    fun `the Android adapter streams one JPEG directly into the shared core workflow`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val encoder = Files.readString(appMedia.resolve("PhotoJpegEncoder.kt"))
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))

        assertFalse(encoder.contains("ByteArrayOutputStream"), "JPEG encoding must not accumulate a full in-memory buffer")
        assertFalse(encoder.contains("toByteArray("), "JPEG encoding must not copy an accumulated buffer")
        assertFalse(encoder.contains(": ByteArray"), "the adapter must not return a whole JPEG byte array")
        assertFalse(encoder.contains("private const val QUALITY"), "quality belongs to the shared profile, not a fixed Android-only constant")
        assertTrue(encoder.contains("StreamEncoder<Bitmap>"), "the thin Bitmap adapter must implement the shared encoder port")
        assertTrue(encoder.contains("PhotoQualityProfile"), "the Android encoder must take the shared core profile")
        assertTrue(
            encoder.contains("bitmap.compress(Bitmap.CompressFormat.JPEG, qualityProfile.jpegQuality, output)"),
            "Bitmap.compress must receive both the shared profile quality and the stager-owned output stream",
        )
        assertTrue(encoder.contains("check(ok)"), "Bitmap.compress=false must fail before staging can publish or record")
        assertEquals(1, occurrences(encoder, "bitmap.compress("), "one adapter invocation means one JPEG encode")
        assertEquals(
            1,
            occurrences(camera, "VerifiedAssetWorkflow.encodeStagePublishRecord("),
            "camera must use the executable shared workflow once",
        )
        assertEquals(
            1,
            occurrences(imported, "VerifiedAssetWorkflow.encodeStagePublishRecord("),
            "import's final-JPEG branch must use the executable shared workflow once",
        )
    }

    @Test
    fun `camera and import freeze the persistent profile once then bake scale and encode in that order`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))
        val settingsFile = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/feature/settings/media/PhotoQualitySettings.kt")

        assertTrue(Files.isRegularFile(settingsFile), "new-photo quality must have a persistent settings adapter")
        val settings = Files.readString(settingsFile)
        assertTrue(settings.contains("SharedPreferences"), "the settings adapter must use Android's persistent preference storage")
        assertTrue(settings.contains("getSharedPreferences("), "the settings adapter must open its dedicated preference file")
        assertTrue(settings.contains("PhotoQualityProfile.fromStoredValue("), "missing or unknown persisted values must resolve through the core default")
        assertTrue(
            settings.contains("putString(PHOTO_QUALITY_PROFILE_KEY, profile.storedValue)"),
            "changing the setting must persist the core profile's stable value for future operations",
        )

        assertFrozenProfileFlowsThrough(camera, "camera")
        assertFrozenProfileFlowsThrough(imported, "import")
        assertUsesDynamicPeakBudget(
            camera,
            "camera",
            "capturedBitmap.width",
            "capturedBitmap.height",
            "exifOrientation",
            orientationReadBeforeBudget = false,
        )
        assertUsesDynamicPeakBudget(
            imported,
            "import",
            "bounds.outWidth",
            "bounds.outHeight",
            "orientation",
            orientationReadBeforeBudget = true,
        )
    }

    @Test
    fun `camera keeps the final JPEG digest while import keeps its original source digest`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))

        assertTrue(camera.contains("staged.digest.sha256"), "camera content_hash must come from the final streamed JPEG")
        assertTrue(imported.contains("DigestInputStream(sourceStream, digest)"), "import must continue hashing original source bytes")
        assertTrue(imported.contains("val contentHash = ContentHash.hex(digest.digest())"), "import DB plan must keep the original-source digest")
    }

    @Test
    fun `camera and import scope active asset lookup to their resolved property`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))

        for ((pipeline, source) in listOf("camera" to camera, "import" to imported)) {
            assertTrue(
                source.contains("activeAssetLookup: (propertyId: String, contentHash: String) -> List<String>"),
                "$pipeline lookup contract must require the authoritative property together with the hash",
            )
            assertTrue(
                source.contains("activeAssetLookup(propertyId, contentHash)"),
                "$pipeline must query with the property resolved from its target room, not hash alone",
            )
        }
    }

    private fun occurrences(source: String, fragment: String): Int =
        source.windowed(fragment.length, partialWindows = false).count { it == fragment }

    private fun assertFrozenProfileFlowsThrough(source: String, pipeline: String) {
        assertTrue(
            source.contains("qualityProfileSource: PhotoQualityProfileSource"),
            "$pipeline must require the shared profile source rather than fall back to a hidden fixed quality",
        )
        assertEquals(
            1,
            occurrences(source, "qualityProfileSource.snapshotForNewPhoto()"),
            "$pipeline must read the setting exactly once and keep that snapshot for the whole operation",
        )
        val orientation = source.indexOf("PhotoOrientationBaker.bake")
        val scaling = source.indexOf("PhotoBitmapScaler.scaleDown")
        val encoding = source.indexOf("PhotoJpegEncoder(qualityProfile)")
        assertTrue(orientation >= 0 && orientation < scaling, "$pipeline must scale only after EXIF orientation is baked")
        assertTrue(scaling >= 0 && scaling < encoding, "$pipeline must encode the scaled bitmap with the same frozen profile")
    }

    private fun assertUsesDynamicPeakBudget(
        source: String,
        pipeline: String,
        width: String,
        height: String,
        orientationArgument: String,
        orientationReadBeforeBudget: Boolean,
    ) {
        val check = source.indexOf("ImportBounds.check(")
        val bake = source.indexOf("PhotoOrientationBaker.bake")
        assertTrue(check >= 0, "$pipeline must check the exact operation peak before it decodes or allocates another bitmap")
        if (orientationReadBeforeBudget) {
            val orientation = source.indexOf("val orientation = PhotoExifReader.readOrientation")
            assertTrue(orientation >= 0 && orientation < check, "$pipeline must read EXIF orientation before budgeting its bake allocation")
        }
        assertTrue(check < bake, "$pipeline must reject before its orientation bake allocates")
        val budgetCall = source.substring(check, bake)
        assertTrue(budgetCall.contains("width = $width"), "$pipeline must budget its actual decoded width")
        assertTrue(budgetCall.contains("height = $height"), "$pipeline must budget its actual decoded height")
        assertTrue(budgetCall.contains("profile = qualityProfile"), "$pipeline must budget the same frozen profile it encodes")
        assertTrue(
            budgetCall.contains("exifOrientation = $orientationArgument"),
            "$pipeline must budget the EXIF bake it performs",
        )
    }

    private fun androidRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("app/src/main/kotlin/nz/myinspection/app/media")) }
            ?: error("could not locate android/app media sources from ${System.getProperty("user.dir")}")
}
