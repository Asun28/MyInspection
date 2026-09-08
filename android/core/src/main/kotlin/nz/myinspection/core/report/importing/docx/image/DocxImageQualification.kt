package nz.myinspection.core.report.importing.docx.image

data class DocxImageDimensions(val width: Int, val height: Int)

/** Neither disposition authorizes exclusion; every image remains subject to review. */
enum class DocxImageDisposition { REVIEW_REQUIRED, VALIDATED_SMALL_CANDIDATE }

/** Proven header dimensions are independent of the bounded small-PNG validation result. */
data class DocxImageQualification(
    val dimensions: DocxImageDimensions?,
    val disposition: DocxImageDisposition,
)

class DocxImagePixelLimitException : RuntimeException("DOCX_IMAGE_PIXELS")
