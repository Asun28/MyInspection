package nz.myinspection.core.report.pdf

import nz.myinspection.core.report.Audience

/**
 * Where one exported report lives under the app's private storage root, as a relative path only - the root is
 * injected by `:app`, exactly as `MediaPaths` does for photographs.
 *
 * Both the audience and the quality are in the name because both are real, separately verified artifacts of
 * one inspection: re-exporting at Medium to share must not destroy the High copy kept as evidence, and the
 * landlord copy must not destroy the tenant copy.
 */
object PdfArtifactPaths {
    fun reportRelPath(
        propertyId: String,
        inspectionId: String,
        audience: Audience,
        quality: PdfExportQuality,
    ): String {
        requireSafeSegment("propertyId", propertyId)
        requireSafeSegment("inspectionId", inspectionId)
        return "reports/$propertyId/$inspectionId-${audience.storedValue()}-${quality.storedValue}.pdf"
    }

    private fun Audience.storedValue(): String = name.lowercase()

    /**
     * These are UUIDv7 in practice; the guard is against a corrupt or hostile value smuggling a separator or
     * a traversal segment into a path the caller is about to write to.
     */
    private fun requireSafeSegment(name: String, value: String) {
        require(value.isNotBlank() && !value.contains('/') && !value.contains('\\') && value != "." && value != "..") {
            "$name is not a safe path segment: $value"
        }
    }
}
