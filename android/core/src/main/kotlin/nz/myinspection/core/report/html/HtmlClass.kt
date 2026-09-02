package nz.myinspection.core.report.html

/**
 * Every class name the report document may carry. The renderer emits no bare class string and the
 * stylesheet writes no selector that is not one of these, so "a class nobody styles" and "a selector
 * pointing at a class nobody emits" are both unrepresentable rather than merely discouraged.
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
