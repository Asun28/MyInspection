package nz.myinspection.core.report.importing.docx.image

data class DocxImageDimensions(val width: Int, val height: Int)

enum class DocxImageDisposition { REVIEW_REQUIRED, SHIM_QUALIFIED }

/** Header dimensions are candidates, independent of the narrow exclusion qualification. */
data class DocxImageQualification(
    val dimensions: DocxImageDimensions?,
    val disposition: DocxImageDisposition,
)

class DocxImagePixelLimitException internal constructor() : IllegalArgumentException("DOCX_IMAGE_PIXELS")
