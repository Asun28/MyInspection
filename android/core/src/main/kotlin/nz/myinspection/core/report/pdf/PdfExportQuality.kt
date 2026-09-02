package nz.myinspection.core.report.pdf

import nz.myinspection.core.report.ImagePurpose

/**
 * The quality chosen for one export. A profile changes how densely a photograph is sampled into the page and
 * nothing else: not the report's content, not its layout, not the footer hash, and never the stored photo.
 *
 * The dpi pairs are requirement section 8, decided 2026-08-19. Inline pictures sit in the item and room flow
 * and are read on screen; appendix plates are the evidence copy and are sampled harder at every profile.
 * `High` is the archive recommendation, `Medium` the sharing default.
 */
enum class PdfExportQuality(
    val storedValue: String,
    val inlineDpi: Int,
    val appendixDpi: Int,
) {
    LOW(storedValue = "low", inlineDpi = 96, appendixDpi = 120),
    MEDIUM(storedValue = "medium", inlineDpi = 120, appendixDpi = 160),
    HIGH(storedValue = "high", inlineDpi = 150, appendixDpi = 200),
    EXTRA_HIGH(storedValue = "extra_high", inlineDpi = 200, appendixDpi = 300),
    ;

    fun dpiFor(purpose: ImagePurpose): Int = when (purpose) {
        ImagePurpose.INLINE -> inlineDpi
        ImagePurpose.APPENDIX -> appendixDpi
    }

    companion object {
        val DEFAULT: PdfExportQuality = MEDIUM

        /**
         * Missing or unknown persisted values retain the published default rather than inventing a profile,
         * matching how `PhotoQualityProfile` reads the stored capture setting.
         */
        fun fromStoredValue(value: String?): PdfExportQuality =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}
