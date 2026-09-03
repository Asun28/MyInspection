package nz.myinspection.core.report.html

/**
 * Every class name the report document may carry, in one place: the renderer emits no bare class string
 * and the stylesheet builds its selectors from these entries, so neither side can misspell a name.
 *
 * That is a guarantee about *names* only. Nothing here couples emission to selection - a declared class
 * no rule styles is perfectly representable, and the baseline stylesheet does style only two entries.
 * Set parity between these entries and the classes a document emits is enforced by the test named
 * `the classes the renderer emits and the classes HtmlClass declares are the same set`, not by the enum;
 * `T3-REPORT-HTML-PRESENTATION` adds the stylesheet side.
 *
 * [cssName] is derived, not declared per entry: an entry cannot drift from its own selector, and adding
 * one cannot introduce an underscore-versus-hyphen typo. `lowercase()` without a locale is the
 * locale-independent overload, so the derivation is the same on a Turkish device as on an English one.
 */
enum class HtmlClass {
    REPORT,
    IDENTITY,
    IDENTITY_FIELD,
    SECTION,
    GLOSSARY_ENTRY,
    SUMMARY_COUNTS,
    SUMMARY_ADVERSE,
    ROOM,
    ITEM_TABLE,
    ITEM_ROW,
    ITEM_STATUS,
    ITEM_NOTE,
    ITEM_WEAR,
    EVIDENCE_GALLERY,
    EVIDENCE_FIGURE,
    EVIDENCE_CAPTION,
    EVIDENCE_MISSING,
    SUPPLEMENT,
    REMEDIATION,
    REMEDIATION_URGENCY,
    PROVENANCE,
    DISCLAIMER,
    TENANT_AGREEMENT,
    INTEGRITY,
    INTEGRITY_LABEL,
    TEXT_EN,
    TEXT_ZH,
    TEXT_ORIGINAL,
    ;

    val cssName: String = name.lowercase().replace('_', '-')
}
