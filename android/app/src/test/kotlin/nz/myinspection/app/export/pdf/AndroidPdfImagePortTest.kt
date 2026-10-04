package nz.myinspection.app.export.pdf

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * [AndroidPdfImagePort] is device code: BitmapFactory and Canvas do not run in a JVM test (L280), so this test pins the
 * reviewed source instead of scanning it. The adapter file must equal [REVIEWED_SOURCE] exactly, CRLF line endings
 * aside, so any edit (including one inside a comment or a string) fails it until the new text is reviewed and copied
 * here. The pinned text contains the required BitmapFactory, Canvas and stream calls and none of the forbidden
 * document, font, executor, rotation, rescaling or storage APIs. It proves nothing about how Android decodes or draws.
 */
class AndroidPdfImagePortTest {
    @Test
    fun `the adapter source is exactly the reviewed BitmapFactory and Canvas binding`() {
        val relative = "src/main/kotlin/nz/myinspection/app/export/pdf/AndroidPdfImagePort.kt"
        val source = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: fail("cannot locate $relative from ${System.getProperty("user.dir")}")
        assertEquals(REVIEWED_SOURCE, source.replace("\r\n", "\n"))
    }
}

/*
 * R4 (T3-PDF-IMAGE-BRIDGE): 22/22 single-point compiling source mutants of AndroidPdfImagePort.kt killed by the one
 * test above. The adapter is device code, so a kill shows the pin notices the change, not that Android behaves
 * differently. Kill evidence per mutant is the TestNG report for AndroidPdfImagePortTest being produced (so the mutant
 * compiled; 1 test ran) with that test failing by java.lang.AssertionError. Production AndroidPdfImagePort.kt
 * SHA-256 at batch time and after each restore:
 *   C16C5A50BF90C28B9CF2E8A2D13641F80CA75945E892ACFEC6616B538B27EE0A
 * Command per mutant: cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache -q
 *   :app:testDebugUnitTest --tests nz.myinspection.app.export.pdf.AndroidPdfImagePortTest
 * M01 bounds decode without inJustDecodeBounds
 * M02 sample size not forwarded
 * M03 undecodable-bounds check removed (PdfSourcePixels would then throw on -1 instead of the port returning null)
 * M04 a rescaling API call introduced (same size, so an identity on device; the pin must still reject it)
 * M05 source rectangle not passed unchanged
 * M06 destination rectangle not passed unchanged
 * M07 width read from the bitmap height
 * M08 recycle does not recycle the bitmap
 * M09 closeSource does not close the stream
 * M10 source opened from a file path instead of the caller access
 * M11 Matrix import added
 * M12 canvas rotated before drawing
 * M13 canvas scaled before drawing
 * M14 density rescaling set on the sampled decode
 * M15 EXIF-style rotation through the project baker
 * M16 storage lookup through a Context content resolver
 * M17 stream closed on a new thread
 * M18 canvas rotation hidden in a string template that looks like a line comment
 * M19 decode option hidden between string literals that look like a block comment
 * M20 canvas scale hidden between a line comment that ends in a block-comment opener and a later empty block comment
 * M21 bounds Options chained to a sampled decode (shrunken bounds)
 * M22 source rectangle narrowed by a chained apply
 */

/** The reviewed AndroidPdfImagePort.kt, byte for byte (LF line endings). */
private const val REVIEWED_SOURCE = """package nz.myinspection.app.export.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import java.io.InputStream
import nz.myinspection.core.report.pdf.PdfSourcePixels

/**
 * [PdfImagePort] over [BitmapFactory] and a page [Canvas]. [sourceOf] is the caller's authorized access to
 * the bytes behind a photoId and must open a new stream on every call: this class searches no album, chooses
 * no path and opens no document. The bridge opens one stream per purpose (bounds, then decode) and closes
 * each through [closeSource]; the bitmap a decode returns belongs to the bridge until it recycles it.
 * No EXIF rotation is applied: the camera and import pipelines bake orientation into the stored pixels
 * (PhotoOrientationBaker). No JVM test executes this class (BitmapFactory and Canvas are device code, L280);
 * AndroidPdfImagePortTest reads its source text.
 */
class AndroidPdfImagePort(
    private val sourceOf: (photoId: String) -> InputStream,
    private val canvas: Canvas,
) : PdfImagePort<InputStream, AndroidDecodedImage> {
    override fun openSource(photoId: String): InputStream = sourceOf(photoId)

    override fun closeSource(source: InputStream) = source.close()

    override fun readBounds(source: InputStream): PdfSourcePixels? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(source, null, options)
        // BitmapFactory sets both to -1 when it cannot decode the bytes.
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return PdfSourcePixels(options.outWidth, options.outHeight)
    }

    override fun decode(source: InputStream, sampleSize: Int): AndroidDecodedImage? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeStream(source, null, options)?.let(::AndroidDecodedImage)
    }

    override fun draw(image: AndroidDecodedImage, sourceRect: PdfPixelRect, destination: PdfPointRect) {
        canvas.drawBitmap(
            image.bitmap,
            Rect(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom),
            RectF(destination.left, destination.top, destination.right, destination.bottom),
            null,
        )
    }
}

/** A [Bitmap] under the bridge's ownership; dimensions are read from the bitmap itself, never predicted. */
class AndroidDecodedImage(val bitmap: Bitmap) : PdfDecodedImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override fun recycle() = bitmap.recycle()
}
"""
