package nz.myinspection.app.export.pdf

import nz.myinspection.core.report.pdf.PdfImagePlacement

/** Page points; fractional because a fitted picture rarely lands on whole points. */
data class PdfPointRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The one fit policy: scale the whole picture to touch its frame on the tighter axis, then centre it. The frame
 * is the composer's placement, and the dimensions are the ones the decoder returned, never the source bounds or a
 * predicted size. There is no fit mode to choose. A frame without finite edges and positive extents, or a picture
 * without pixels, is refused, and every edge of the result is clamped into the frame, so no caller receives an
 * infinite or NaN rectangle or one that leaves its frame.
 */
internal object PdfFitCenter {
    /**
     * The placement as a frame; a placement without positive width and height is refused. The frame equals the
     * placement exactly while xPt, yPt, widthPt, heightPt and the far edges xPt + widthPt and yPt + heightPt all have
     * magnitude at most 2^24 points, far beyond any page: every operand and every sum is then a Float.
     */
    fun frame(placement: PdfImagePlacement): PdfPointRect {
        require(placement.widthPt > 0 && placement.heightPt > 0) {
            "picture ${placement.photoId} has an empty frame: ${placement.widthPt}x${placement.heightPt} pt"
        }
        val left = placement.xPt.toFloat()
        val top = placement.yPt.toFloat()
        return PdfPointRect(left, top, left + placement.widthPt, top + placement.heightPt)
    }

    fun fit(frame: PdfPointRect, imageWidth: Int, imageHeight: Int): PdfPointRect {
        require(imageWidth > 0 && imageHeight > 0) { "decoded picture has no pixels: ${imageWidth}x$imageHeight" }
        require(frame.left.isFinite() && frame.top.isFinite() && frame.right.isFinite() && frame.bottom.isFinite()) {
            "frame has a non-finite edge: $frame"
        }
        require(frame.right > frame.left && frame.bottom > frame.top) { "frame has no area: $frame" }
        // Extents and edges are computed in Double, where values of Float magnitude cannot overflow, so no edge is
        // NaN; in Float, frame.right - frame.left can overflow and Inf - Inf would follow. Double rounding in the
        // extents, the scaled size and the edge sums can still put an edge outside the frame, slightly on a page (a
        // 1x11 picture in (0, 0, 100, 50) gives top -3.6e-15) and far at extreme magnitudes, so each edge is clamped
        // into the frame after conversion to Float.
        val frameWidth = frame.right.toDouble() - frame.left
        val frameHeight = frame.bottom.toDouble() - frame.top
        val scale = minOf(frameWidth / imageWidth, frameHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val left = frame.left + (frameWidth - width) / 2
        val top = frame.top + (frameHeight - height) / 2
        return PdfPointRect(
            left.toFloat().coerceIn(frame.left, frame.right),
            top.toFloat().coerceIn(frame.top, frame.bottom),
            (left + width).toFloat().coerceIn(frame.left, frame.right),
            (top + height).toFloat().coerceIn(frame.top, frame.bottom),
        )
    }
}
