package nz.myinspection.core.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TD15 has no app JVM runtime because Bitmap is Android-only. This narrow architecture guard protects the explicit
 * card forbid that a later edit must not accumulate JPEG bytes and then merely write them in chunks. The real stream
 * behavior and every failure branch are covered by [StreamDigestsTest] and [StreamFileStagerTest].
 */
class PhotoStreamingWiringTest {
    @Test
    fun `both ingest pipelines wire the verified stream stage without a JPEG byte accumulator`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val encoder = Files.readString(appMedia.resolve("PhotoJpegEncoder.kt"))
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))
        val fileStore = Files.readString(appMedia.resolve("MediaFileStore.kt"))

        assertFalse(encoder.contains("ByteArrayOutputStream"), "JPEG encoding must not accumulate a full in-memory buffer")
        assertFalse(encoder.contains("toByteArray("), "JPEG encoding must not copy an accumulated buffer")
        assertFalse(encoder.contains("fun encode("), "the legacy ByteArray-returning encoder API must be gone")
        assertTrue(encoder.contains("fun encodeInto("), "the encoder must expose an output-stream API")
        assertTrue(
            encoder.contains("bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)"),
            "Bitmap.compress must receive the stream supplied by the stager",
        )
        assertTrue(camera.contains("stageVerifiedAsset"), "camera must stage its one-pass JPEG stream before DB recording")
        assertTrue(imported.contains("stageVerifiedAsset"), "import must stage its one-pass JPEG stream before DB recording")
        assertTrue(encoder.contains("check(ok)"), "Bitmap.compress=false must fail before any stage can publish or record")
        assertEquals(1, occurrences(encoder, "bitmap.compress("), "one encoder invocation must mean one JPEG pass")
        assertEquals(1, occurrences(camera, "PhotoJpegEncoder.encodeInto("), "camera must not encode once for hash and again for disk")
        assertEquals(1, occurrences(imported, "PhotoJpegEncoder.encodeInto("), "import must not encode once for hash and again for disk")
        assertFalse(fileStore.contains("fun writeNewAsset("), "the legacy whole-ByteArray write entry point must be gone")
    }

    @Test
    fun `camera uses the final JPEG digest while import preserves the original source digest for its DB plan`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))

        assertTrue(camera.contains("staged.digest.sha256"), "camera content_hash must come from the final streamed JPEG")
        assertTrue(imported.contains("DigestInputStream(sourceStream, digest)"), "import must continue hashing original source bytes")
        assertTrue(imported.contains("val contentHash = ContentHash.hex(digest.digest())"), "import DB plan must keep the original-source digest")
    }

    @Test
    fun `import scratch cleanup and source close preserve their active failure`() {
        val imported = Files.readString(androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media/PhotoImportPipeline.kt"))
        val fileStore = Files.readString(androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media/MediaFileStore.kt"))

        assertTrue(imported.contains("var scratchPrimary: Throwable? = null"), "scratch cleanup needs its own active-failure owner")
        assertTrue(imported.contains("scratchPrimary = failure"), "scratch failure must remain available to its finally block")
        assertTrue(imported.contains("if (!MediaFileStore.deleteIfPresent(tempFile))"), "ordinary false-delete logging remains observable")
        assertTrue(imported.contains("if (failure == null) throw cleanupFailure"), "a lone cleanup failure must surface")
        assertTrue(
            imported.contains("failure.addSuppressed(cleanupFailure)"),
            "scratch deletion must suppress instead of replace an active failure",
        )
        assertTrue(
            imported.contains("failure.addSuppressed(closeFailure)"),
            "source close must suppress instead of replace an active failure",
        )
        assertTrue(fileStore.contains("var copyPrimary: Throwable? = null"), "copyInto needs its own active-failure owner")
        assertTrue(fileStore.contains("copyPrimary = failure"), "copy failure must remain available to its finally block")
        assertTrue(fileStore.contains("deleteTemp(temp)"), "ordinary false-delete logging stays in copyInto")
        assertTrue(fileStore.contains("failure.addSuppressed(cleanupFailure)"), "copy cleanup must not replace an active failure")
    }

    @Test
    fun `both paths stage then publish then record within a scope that always discards the stage`() {
        val appMedia = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")
        val camera = Files.readString(appMedia.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(appMedia.resolve("PhotoImportPipeline.kt"))
        val outcome = Files.readString(appMedia.resolve("PhotoIngestOutcome.kt"))

        assertOrdered(camera, "stageVerifiedAsset", "useStaged(staged)", "publishStaged", "recordLanded")
        assertOrdered(imported, "stageVerifiedAsset", "useStaged(staged)", "publishStaged", "recordLanded")
        assertTrue(
            camera.contains("if (plan is PhotoIngestPlan.WriteNewAsset)"),
            "camera reuse must leave its staged JPEG to useStaged cleanup rather than publish it",
        )
        assertTrue(
            outcome.contains("MediaFileStore.discardIn(mediaRoot)"),
            "a DB record failure after publish must retain the existing recorder compensation path",
        )
    }

    private fun assertOrdered(source: String, vararg fragments: String) {
        var previous = -1
        for (fragment in fragments) {
            val index = source.indexOf(fragment)
            assertTrue(index > previous, "expected $fragment after the preceding lifecycle step")
            previous = index
        }
    }

    private fun occurrences(source: String, fragment: String): Int =
        source.windowed(fragment.length, partialWindows = false).count { it == fragment }

    private fun androidRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("app/src/main/kotlin/nz/myinspection/app/media")) }
            ?: error("could not locate android/app media sources from ${System.getProperty("user.dir")}")
}
