package nz.myinspection.app.export.pdf

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
