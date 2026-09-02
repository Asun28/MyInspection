package nz.myinspection.core.report.pdf

import nz.myinspection.core.media.ImportBounds

/**
 * How many pixels a picture box is worth, and what that costs to decode. Every value here is arithmetic over
 * the plan's millimetres and the chosen profile's dpi: this module decodes nothing and knows no Android type.
 *
 * A source image's own pixel dimensions are deliberately not an input to the plan. They are only known after
 * a bounds-only decode on the device, so the split is: this module states the target and the rule, the device
 * reads the source bounds, and [inSampleSize] and [decodedBytes] turn the two into a decode parameter and a
 * memory bound without either side inventing a policy of its own.
 */
object PdfImageSampling {
    /** 25.4 mm to the inch, scaled by ten so the whole conversion stays in integers. */
    private const val TENTHS_MM_PER_INCH = 254L

    /** Outer bounds so the conversion cannot overflow for any accepted pair; beyond them is a caller error. */
    private const val MAX_BOX_MM = 10_000
    private const val MAX_DPI = 10_000

    /** Pixels a box [lengthMm] long deserves at [dpi], rounded **up**: under-sampling is visibly soft. */
    fun targetPixels(lengthMm: Int, dpi: Int): Int {
        require(lengthMm in 1..MAX_BOX_MM) { "a picture box must be 1..$MAX_BOX_MM mm: $lengthMm" }
        require(dpi in 1..MAX_DPI) { "a sampling density must be 1..$MAX_DPI dpi: $dpi" }
        return ceilDiv(lengthMm.toLong() * dpi * 10, TENTHS_MM_PER_INCH).toInt()
    }

    /**
     * `BitmapFactory.Options.inSampleSize`: the decoder rounds any value down to a power of two, so the only
     * useful values are powers of two, and the right one is the largest that still leaves **both** decoded
     * dimensions at or above the target. A source already no larger than its target therefore samples at 1 -
     * subsampling it would throw away detail the report was meant to show.
     */
    fun inSampleSize(sourceWidthPx: Int, sourceHeightPx: Int, targetWidthPx: Int, targetHeightPx: Int): Int {
        require(sourceWidthPx > 0 && sourceHeightPx > 0) {
            "source pixels must be positive: ${sourceWidthPx}x$sourceHeightPx"
        }
        require(targetWidthPx > 0 && targetHeightPx > 0) {
            "target pixels must be positive: ${targetWidthPx}x$targetHeightPx"
        }
        // `size * 2` can only reach 2^31 on the final failing check, where size is 2^30; it wraps to
        // Int.MIN_VALUE there and the division yields 0, which fails the condition and ends the loop on the
        // same answer widening to Long would give. `an enormous source still resolves to a power of two`
        // pins that boundary rather than leaving it to the reader.
        var size = 1
        while (sourceWidthPx / (size * 2) >= targetWidthPx && sourceHeightPx / (size * 2) >= targetHeightPx) {
            size *= 2
        }
        return size
    }

    /**
     * What one decoded bitmap occupies. Dimensions round **up**, the opposite of [targetPixels]'s reason: this
     * is a bound, and a decoder that hands back the ceiling must not overrun a budget computed from the floor.
     * `ARGB_8888` is the config the photo pipeline already budgets against, so the two agree by construction.
     *
     * A pixel count whose byte cost will not fit a `Long` saturates to [Long.MAX_VALUE] rather than wrapping,
     * the same answer `ImportBounds` gives: a bound that went negative would read as "free" to every caller.
     */
    fun decodedBytes(sourceWidthPx: Int, sourceHeightPx: Int, sampleSize: Int): Long {
        require(sourceWidthPx > 0 && sourceHeightPx > 0) {
            "source pixels must be positive: ${sourceWidthPx}x$sourceHeightPx"
        }
        require(sampleSize >= 1) { "a sample size is at least 1: $sampleSize" }
        // Two positive Int dimensions multiply safely in Long; multiplying their pixel count by four may not.
        val pixels = ceilDiv(sourceWidthPx.toLong(), sampleSize.toLong()) *
            ceilDiv(sourceHeightPx.toLong(), sampleSize.toLong())
        if (pixels > Long.MAX_VALUE / ImportBounds.BYTES_PER_PIXEL) return Long.MAX_VALUE
        return pixels * ImportBounds.BYTES_PER_PIXEL
    }

    private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor
}
