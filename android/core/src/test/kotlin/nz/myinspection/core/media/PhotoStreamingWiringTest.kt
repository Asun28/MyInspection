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
        assertTrue(encoder.contains("StreamEncoder<Bitmap>"), "the thin Bitmap adapter must implement the shared encoder port")
        assertTrue(
            encoder.contains("bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)"),
            "Bitmap.compress must receive the stager-owned output stream",
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
    fun `camera keeps the final JPEG digest while import keeps its original source digest`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))

        assertTrue(camera.contains("staged.digest.sha256"), "camera content_hash must come from the final streamed JPEG")
        assertTrue(imported.contains("DigestInputStream(sourceStream, digest)"), "import must continue hashing original source bytes")
        assertTrue(imported.contains("val contentHash = ContentHash.hex(digest.digest())"), "import DB plan must keep the original-source digest")
    }

    private fun occurrences(source: String, fragment: String): Int =
        source.windowed(fragment.length, partialWindows = false).count { it == fragment }

    private fun androidRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("app/src/main/kotlin/nz/myinspection/app/media")) }
            ?: error("could not locate android/app media sources from ${System.getProperty("user.dir")}")
}
