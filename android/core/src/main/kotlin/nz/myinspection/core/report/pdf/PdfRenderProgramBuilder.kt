package nz.myinspection.core.report.pdf

import nz.myinspection.core.report.A4_HEIGHT_MM
import nz.myinspection.core.report.A4_WIDTH_MM
import nz.myinspection.core.report.DocumentPlan
import nz.myinspection.core.report.ImageSlotBlock
import nz.myinspection.core.report.ItemRowBlock
import nz.myinspection.core.report.PagePlan
import nz.myinspection.core.report.PlacedBlock
import nz.myinspection.core.report.TextBearingBlock
import nz.myinspection.core.report.TextRun

/**
 * Translates a finished layout plan into the ordered draw operations an Android executor replays.
 *
 * The whole of this class is a coordinate lift plus a sampling lookup. It makes no layout decision: it does
 * not wrap, split, re-order, paginate or resize anything, and a block that does not fit its page is reported
 * rather than clipped, because a clipped block is a defect nobody would ever see.
 *
 * What it does draw is fixed by the plan's own contract: a text-bearing block contributes exactly its
 * [TextBearingBlock.textRuns], and everything else on a block - an address, a status, a note, a digest - is
 * identifying metadata. Drawing one would print it once per chunk of a block split across pages.
 */
class PdfRenderProgramBuilder {
    fun build(
        plan: DocumentPlan,
        inspectionId: String,
        semanticFingerprint: String,
        quality: PdfExportQuality,
    ): PdfRenderProgram {
        // Audience and hash come from the plan rather than from parameters, so no caller can render a
        // document whose stated identity disagrees with the layout it is rendering.
        val identity = PdfDocumentIdentity(inspectionId, plan.audience, plan.dataHash, semanticFingerprint)
        return PdfRenderProgram(identity, quality, plan.pages.map { page(it, quality) })
    }

    private fun page(page: PagePlan, quality: PdfExportQuality): PdfPageProgram =
        PdfPageProgram(page.number, page.blocks.flatMap { operations(page.number, it, quality) })

    private fun operations(pageNumber: Int, placed: PlacedBlock, quality: PdfExportQuality): List<PdfDrawOp> {
        val content = placed.content
        val label = content::class.simpleName ?: "block"
        // Reported for its own geometry even when it draws nothing, so a stray block cannot hide by being empty.
        boxOf(pageNumber, label, placed.xMm, placed.yMm, placed.widthMm, placed.heightMm)

        val text = (content as? TextBearingBlock)?.textRuns.orEmpty().map { textOp(pageNumber, label, placed, it) }
        val pictures = when (content) {
            // A slot placed in its own right: its caption runs are already among the block's text runs above.
            is ImageSlotBlock -> listOf(imageOp(pageNumber, label, placed, content, quality))
            // A thumbnail nested in an item row keeps its caption to itself, so that caption is drawn here or
            // nowhere: it is not part of the row's own runs.
            is ItemRowBlock -> content.thumbnails.flatMap { thumbnail ->
                thumbnail.textRuns.map { textOp(pageNumber, label, placed, it) } +
                    imageOp(pageNumber, label, placed, thumbnail, quality)
            }
            else -> emptyList()
        }
        return text + pictures
    }

    private fun textOp(pageNumber: Int, label: String, placed: PlacedBlock, run: TextRun): PdfTextOp {
        val box = boxOf(pageNumber, label, placed.xMm + run.xMm, placed.yMm + run.yMm, run.widthMm, run.heightMm)
        return PdfTextOp(run.text, run.language, run.style, box.xPt, box.yPt, box.widthPt, box.heightPt)
    }

    /**
     * The picture box is [ImageSlotBlock.imageHeightMm], not the slot's total height: the remainder of the
     * slot is its caption, which is drawn as text and must not be sampled into the photograph.
     */
    private fun imageOp(
        pageNumber: Int,
        label: String,
        placed: PlacedBlock,
        slot: ImageSlotBlock,
        quality: PdfExportQuality,
    ): PdfImageOp {
        val box = boxOf(
            pageNumber,
            label,
            placed.xMm + slot.xMm,
            placed.yMm + slot.yMm,
            slot.widthMm,
            slot.imageHeightMm,
        )
        val dpi = quality.dpiFor(slot.purpose)
        return PdfImageOp(
            PdfImagePlacement(slot.photoId, slot.purpose, box.xPt, box.yPt, box.widthPt, box.heightPt),
            targetWidthPx = PdfImageSampling.targetPixels(slot.widthMm, dpi),
            targetHeightPx = PdfImageSampling.targetPixels(slot.imageHeightMm, dpi),
        )
    }

    /**
     * Converts a millimetre box edge to edge rather than as an origin plus an independently rounded length.
     * Two boxes that touch in millimetres still touch in points, and a box ending on the page edge lands on
     * it instead of one rounded point past it - which also makes the containment check below exact.
     *
     * The diagnostic names the block type, the page and the geometry, and never the block's text: report
     * content is the tenant's and the landlord's, and a log is not a place for it.
     */
    private fun boxOf(pageNumber: Int, label: String, xMm: Int, yMm: Int, widthMm: Int, heightMm: Int): PointBox {
        require(xMm >= 0 && yMm >= 0 && widthMm >= 0 && heightMm >= 0) {
            "$label on page $pageNumber has negative geometry: ${xMm},$yMm sized ${widthMm}x$heightMm mm"
        }
        require(xMm + widthMm <= A4_WIDTH_MM && yMm + heightMm <= A4_HEIGHT_MM) {
            "$label on page $pageNumber at ${xMm},$yMm sized ${widthMm}x$heightMm mm reaches past the " +
                "${A4_WIDTH_MM}x$A4_HEIGHT_MM mm page"
        }
        val xPt = PdfGeometry.mmToPt(xMm)
        val yPt = PdfGeometry.mmToPt(yMm)
        return PointBox(
            xPt = xPt,
            yPt = yPt,
            widthPt = PdfGeometry.mmToPt(xMm + widthMm) - xPt,
            heightPt = PdfGeometry.mmToPt(yMm + heightMm) - yPt,
        )
    }

    private data class PointBox(val xPt: Int, val yPt: Int, val widthPt: Int, val heightPt: Int)
}
