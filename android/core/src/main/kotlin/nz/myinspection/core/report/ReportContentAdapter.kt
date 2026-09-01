package nz.myinspection.core.report

import nz.myinspection.core.report.content.LegacyImportProvenance
import nz.myinspection.core.report.content.ReportContent
import nz.myinspection.core.report.content.ReportContentProjector

/**
 * The one bridge from a report snapshot to the A4 layout engine.
 *
 * Audience and privacy are decided upstream, once, by [ReportContentProjector], and the resulting
 * [ReportContent] is what the paginator and every other renderer consume. What is left for this class is
 * the small set of preconditions that are about *laying the report out* rather than about its meaning, and
 * that therefore have to be evaluated while the snapshot is still in view.
 */
class ReportContentAdapter {
    private val projector = ReportContentProjector()

    fun adapt(
        report: ReportSnapshot,
        audience: Audience,
        options: ReportOptions = ReportOptions(),
        provenance: LegacyImportProvenance? = null,
    ): ReportContent {
        validateLayoutPreconditions(report)
        return projector.project(report, audience, options, provenance)
    }

    /**
     * A room carrying nothing at all is a malformed projection for a paginated format: its heading could
     * only ever be an orphan. The shared projection drops such a room instead, which is the right answer
     * for a format with no headings to strand, so the refusal lives here - and it has to run before the
     * projection, because afterwards the room the author declared is simply gone.
     *
     * A room whose photography is entirely privacy-filtered is a different case: that is the default
     * projection of a room full of the tenant's belongings, and the layout skips it silently.
     */
    private fun validateLayoutPreconditions(report: ReportSnapshot) {
        report.rooms.forEach { room ->
            require(room.items.isNotEmpty() || room.photos.isNotEmpty()) {
                "room ${room.id} has neither items nor photos; it would render as an orphan heading"
            }
        }
    }
}
