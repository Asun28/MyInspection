package nz.myinspection.app.export.pdf

import nz.myinspection.core.report.pdf.PdfImageOp
import nz.myinspection.core.report.pdf.PdfImageSampling
import nz.myinspection.core.report.pdf.PdfSourcePixels

/** A decoded picture the bridge owns until it calls [recycle]. Its dimensions are the decoder's, not a forecast. */
interface PdfDecodedImage {
    val width: Int
    val height: Int
    fun recycle()
}

/** Whole pixels of a decoded picture. */
data class PdfPixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The platform seam, kept narrow enough that the bridge's ownership rules run unchanged in a JVM test. Whoever
 * builds the port binds each photoId to authorized bytes; the port opens them, reads or decodes with the parameter
 * it is handed and draws where it is told. It decides nothing about the picture.
 */
interface PdfImagePort<Source, Image : PdfDecodedImage> {
    fun openSource(photoId: String): Source
    fun closeSource(source: Source)

    /** Bounds-only decode; null when the bytes are not a picture the platform can decode. */
    fun readBounds(source: Source): PdfSourcePixels?

    /**
     * Sampled decode; null when the decoder gives up. A picture becomes the bridge's when this returns it,
     * so a port that throws or returns null must not have left one behind.
     */
    fun decode(source: Source, sampleSize: Int): Image?

    fun draw(image: Image, sourceRect: PdfPixelRect, destination: PdfPointRect)
}

/** The source could not be decoded, or decoded to nothing. A picture is never drawn in its place. */
class PdfImageBridgeException(message: String) : RuntimeException(message)

/**
 * One [PdfImageOp] at a time: read the source bounds, hand them with the op's unchanged targets to the
 * delivered [PdfImageSampling], decode once at that sample size, draw the whole decoded picture at the
 * [PdfFitCenter] rectangle inside the placement, recycle. Sampling and fit arithmetic are not repeated here.
 *
 * Every stream this bridge opens through the port receives a close attempt, and every decoded picture a
 * recycle attempt, before the next step or after a failure. Failures propagate to the caller: a close that
 * fails after a successful decode still recycles the picture first, and a close or recycle that fails under an
 * earlier failure is attached to it as suppressed rather than replacing it. One [draw] call holds at most one
 * decoded picture and attempts its recycle before returning, so calls made one after another never hold two.
 * That is the ownership property [nz.myinspection.core.report.pdf.PdfPageProgram.decodedByteBound] assumes, not
 * a bound on native memory: a recycle that throws is not proof the picture was released.
 */
class PdfImageBridge<Source, Image : PdfDecodedImage>(private val port: PdfImagePort<Source, Image>) {
    fun draw(op: PdfImageOp) {
        val frame = PdfFitCenter.frame(op.placement)
        val photoId = op.placement.photoId
        val source = withSource(photoId, release = {}) { stream ->
            port.readBounds(stream) ?: throw PdfImageBridgeException("picture $photoId has no decodable bounds")
        }
        val sampleSize = PdfImageSampling.inSampleSize(source.width, source.height, op.targetWidthPx, op.targetHeightPx)
        val image = withSource(photoId, release = { it.recycle() }) { stream ->
            port.decode(stream, sampleSize) ?: throw PdfImageBridgeException("picture $photoId did not decode")
        }
        val failure = runCatching {
            if (image.width <= 0 || image.height <= 0) {
                throw PdfImageBridgeException("picture $photoId decoded to no pixels: ${image.width}x${image.height}")
            }
            val destination = PdfFitCenter.fit(frame, image.width, image.height)
            port.draw(image, PdfPixelRect(0, 0, image.width, image.height), destination)
        }.exceptionOrNull()
        image.recycleUnder(failure)
        if (failure != null) throw failure
    }

    /** Opens, runs [block], closes. On a close failure after success, [release] gives back what [block] acquired. */
    private fun <T> withSource(photoId: String, release: (T) -> Unit, block: (Source) -> T): T {
        val stream = port.openSource(photoId)
        val result = try {
            block(stream)
        } catch (failure: Throwable) {
            runCatching { port.closeSource(stream) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        try {
            port.closeSource(stream)
        } catch (closeFailure: Throwable) {
            runCatching { release(result) }.exceptionOrNull()?.let(closeFailure::addSuppressed)
            throw closeFailure
        }
        return result
    }

    /** Recycles; a recycle failure is thrown on its own or attached to the [failure] already in flight. */
    private fun Image.recycleUnder(failure: Throwable?) {
        try {
            recycle()
        } catch (recycleFailure: Throwable) {
            if (failure == null) throw recycleFailure
            failure.addSuppressed(recycleFailure)
        }
    }
}
