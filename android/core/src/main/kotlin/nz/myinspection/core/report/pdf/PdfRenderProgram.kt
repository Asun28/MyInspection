package nz.myinspection.core.report.pdf

import nz.myinspection.core.report.A4_HEIGHT_MM
import nz.myinspection.core.report.A4_WIDTH_MM
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.ImagePurpose
import nz.myinspection.core.report.TextLanguage
import nz.myinspection.core.report.TextStyle

/** Millimetres to PDF points (1/72 inch), the one conversion this module performs. */
object PdfGeometry {
    private const val POINTS_PER_INCH = 72L
    /** 25.4 mm to the inch, scaled by ten so the conversion stays in integers. */
    private const val TENTHS_MM_PER_INCH = 254L

    /**
     * Rounds to nearest, which is what makes A4's 210 x 297 mm land on its conventional 595 x 842 pt.
     * Takes a page coordinate, which the builder has already refused if it were negative or off the page;
     * a second guard here could not be reached, so there is not one.
     */
    fun mmToPt(mm: Int): Int =
        ((mm * POINTS_PER_INCH * 10 + TENTHS_MM_PER_INCH / 2) / TENTHS_MM_PER_INCH).toInt()

    val A4_WIDTH_PT: Int = mmToPt(A4_WIDTH_MM)
    val A4_HEIGHT_PT: Int = mmToPt(A4_HEIGHT_MM)
}

/**
 * What one operation means to a reader, with the decode parameters projected out. Two programs built from one
 * plan at two qualities must agree on this exactly: that is what "the quality changes nothing you can see"
 * means, and comparing whole values rather than a hand-listed set of fields keeps it true as fields are added.
 */
sealed interface PdfDrawSemantics

sealed interface PdfDrawOp {
    val semantics: PdfDrawSemantics
}

/**
 * One measured line, already positioned. Text overrunning [widthPt] is ellipsised at the tail by whoever
 * executes the program; re-wrapping would be the renderer overruling the layout.
 */
data class PdfTextOp(
    val text: String,
    val language: TextLanguage,
    val style: TextStyle,
    val xPt: Int,
    val yPt: Int,
    val widthPt: Int,
    val heightPt: Int,
) : PdfDrawOp, PdfDrawSemantics {
    override val semantics: PdfDrawSemantics get() = this
}

/** Where a picture goes and which picture it is - everything about it except how finely it is sampled. */
data class PdfImagePlacement(
    val photoId: String,
    val purpose: ImagePurpose,
    val xPt: Int,
    val yPt: Int,
    val widthPt: Int,
    val heightPt: Int,
) : PdfDrawSemantics

data class PdfImageOp(
    val placement: PdfImagePlacement,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
) : PdfDrawOp {
    override val semantics: PdfDrawSemantics get() = placement
}

/** A source image's own dimensions, read from a bounds-only decode on the device. */
data class PdfSourcePixels(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "source pixels must be positive: ${width}x$height" }
    }
}

/**
 * What the document claims about itself. [dataHash] attests the finalized native evidence and is restated
 * from the plan, never recomputed; [semanticFingerprint] identifies the shared projection both this format
 * and the HTML one came from. Neither is a rendering decision, and neither differs between qualities.
 */
data class PdfDocumentIdentity(
    val inspectionId: String,
    val audience: Audience,
    val dataHash: String,
    val semanticFingerprint: String,
)

data class PdfPageProgram(val number: Int, val ops: List<PdfDrawOp>) {
    val widthPt: Int get() = PdfGeometry.A4_WIDTH_PT
    val heightPt: Int get() = PdfGeometry.A4_HEIGHT_PT
    val imageOps: List<PdfImageOp> get() = ops.filterIsInstance<PdfImageOp>()

    /**
     * The most decoded bitmap memory this page needs at once: the **largest** single picture, not the total.
     * That holds only for an executor that decodes one picture, draws it, recycles it and moves on, which is
     * the contract this bound states. A sum would describe a renderer holding every bitmap alive all page.
     * A picture whose source dimensions are unknown is refused, not counted as free.
     */
    fun decodedByteBound(sourcePixels: Map<String, PdfSourcePixels>): Long =
        imageOps.maxOfOrNull { op ->
            val photoId = op.placement.photoId
            val source = requireNotNull(sourcePixels[photoId]) {
                "page $number has no source dimensions for picture $photoId"
            }
            PdfImageSampling.decodedBytes(
                source.width,
                source.height,
                PdfImageSampling.inSampleSize(source.width, source.height, op.targetWidthPx, op.targetHeightPx),
            )
        } ?: 0L
}

data class PdfRenderProgram(
    val identity: PdfDocumentIdentity,
    val quality: PdfExportQuality,
    val pages: List<PdfPageProgram>,
) {
    fun imageOps(): List<PdfImageOp> = pages.flatMap { it.imageOps }

    /** Every page's operations in order, sampling projected out. See [PdfDrawSemantics]. */
    fun drawableSemantics(): List<List<PdfDrawSemantics>> = pages.map { page -> page.ops.map { it.semantics } }
}
