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

    /**
     * The pixel count a box [lengthMm] long deserves at [dpi], rounded **up**. A picture sampled fractionally
     * under its box is visibly soft, while one extra row of pixels costs nothing worth naming.
     */
    fun targetPixels(lengthMm: Int, dpi: Int): Int {
        require(lengthMm > 0) { "a picture box must have a positive length: $lengthMm mm" }
        require(dpi > 0) { "a sampling density must be positive: $dpi dpi" }
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
     */
    fun decodedBytes(sourceWidthPx: Int, sourceHeightPx: Int, sampleSize: Int): Long {
        require(sourceWidthPx > 0 && sourceHeightPx > 0) {
            "source pixels must be positive: ${sourceWidthPx}x$sourceHeightPx"
        }
        require(sampleSize >= 1) { "a sample size is at least 1: $sampleSize" }
        val width = ceilDiv(sourceWidthPx.toLong(), sampleSize.toLong())
        val height = ceilDiv(sourceHeightPx.toLong(), sampleSize.toLong())
        return width * height * ImportBounds.BYTES_PER_PIXEL
    }

    private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor
}
