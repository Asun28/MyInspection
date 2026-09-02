package nz.myinspection.core.report.pdf

import nz.myinspection.core.report.Audience

/**
 * Where one exported report lives under the app's private storage root, as a relative path only - the root
 * is injected by `:app`, exactly as `MediaPaths` does for photographs. This is the sole derivation point for
 * report artifact paths; nothing else may assemble one from string pieces.
 *
 * The audience and the quality are both in the name because both are separately verified artifacts of one
 * inspection: re-exporting at Medium to share must not destroy the High copy kept as evidence, and the
 * landlord copy must not destroy the tenant copy. Two audiences times four qualities is eight coexisting
 * artifacts of one inspection.
 */
object PdfArtifactPaths {
    private val Audience.storedValue: String get() = name.lowercase()

    /**
     * Derivation and recognition share this one pattern, and its two variable tokens are built from the
     * same enums the derivation writes - so a new audience or quality cannot reach one half and not the
     * other. The anchors mirror `MediaPaths`, but [matchEntire] is what carries the guarantee: `$` alone
     * matches before a final line terminator, so a stored value with a trailing newline would pass it.
     */
    private val REPORT_REL_PATH_PATTERN = Regex(
        "^reports/([^/]+)/([^/]+)-(${Audience.entries.joinToString("|") { it.storedValue }})" +
            "-(${PdfExportQuality.entries.joinToString("|") { it.storedValue }})\\.pdf$",
    )

    fun reportRelPath(
        propertyId: String,
        inspectionId: String,
        audience: Audience,
        quality: PdfExportQuality,
    ): String {
        requireSafeSegment("propertyId", propertyId)
        requireSafeSegment("inspectionId", inspectionId)
        return "reports/$propertyId/$inspectionId-${audience.storedValue}-${quality.storedValue}.pdf"
    }

    /**
     * Whether a relative path is a report artifact of this namespace. Callers must pass this before acting
     * on a stored path - publishing over it, deleting it, counting it as a verified export - because the
     * database column it came from constrains nothing to have been produced here. Same reason, and same
     * shape of guard, as `MediaPaths.isPhotoRelPathShape`.
     */
    fun isReportRelPathShape(relPath: String): Boolean {
        val match = REPORT_REL_PATH_PATTERN.matchEntire(relPath) ?: return false
        return isSafeSegment(match.groupValues[1]) && isSafeSegment(match.groupValues[2])
    }

    /**
     * Both ids are UUIDv7 in practice; the guard is against a corrupt or hostile value smuggling a
     * separator or a traversal segment into a path a caller is about to write a file to.
     */
    private fun requireSafeSegment(name: String, value: String) {
        require(isSafeSegment(value)) { "$name is not a safe path segment: $value" }
    }

    private fun isSafeSegment(value: String): Boolean =
        value.isNotBlank() && !value.contains('/') && !value.contains('\\') && value != "." && value != ".."
}
