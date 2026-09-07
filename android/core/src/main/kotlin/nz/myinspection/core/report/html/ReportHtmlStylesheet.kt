package nz.myinspection.core.report.html

/** The document's only stylesheet. Content, reading order and privacy remain the renderer's inputs. */
internal object ReportHtmlStylesheet {

    val css: String = """
        .${HtmlClass.REPORT.cssName}, .${HtmlClass.REPORT.cssName} * { box-sizing: border-box; }
        .${HtmlClass.REPORT.cssName} {
          --paper: #ffffff; --ink: #182b32; --muted: #415960; --line: #718a93; --wash: #edf3f4;
          color-scheme: light; color: var(--ink); background: var(--paper);
          font-family: system-ui, -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
          font-size: 100%; line-height: 1.6; overflow-wrap: anywhere;
          max-width: 76rem; margin: 0 auto; padding: 2rem;
        }
        .${HtmlClass.IDENTITY.cssName} { border-bottom: 0.2rem solid var(--ink); padding-bottom: 1rem; }
        .${HtmlClass.IDENTITY.cssName} h1 { font-size: 1.8rem; line-height: 1.3; margin: 0 0 1rem; }
        .${HtmlClass.IDENTITY_FIELD.cssName} {
          display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: 0.25rem 1.5rem;
        }
        .${HtmlClass.IDENTITY_FIELD.cssName} dd { margin: 0; }
        .${HtmlClass.SECTION.cssName} { margin-block: 2rem; }
        .${HtmlClass.SECTION.cssName} h2 { font-size: 1.4rem; line-height: 1.4; }
        .${HtmlClass.GLOSSARY_ENTRY.cssName} { margin-block: 0.75rem; }
        .${HtmlClass.GLOSSARY_ENTRY.cssName} dd { margin-inline-start: 1rem; }
        .${HtmlClass.SUMMARY_COUNTS.cssName}, .${HtmlClass.ITEM_TABLE.cssName} {
          border-collapse: collapse; width: 100%; max-width: 100%;
        }
        .${HtmlClass.SUMMARY_COUNTS.cssName} th, .${HtmlClass.SUMMARY_COUNTS.cssName} td,
        .${HtmlClass.ITEM_TABLE.cssName} th, .${HtmlClass.ITEM_TABLE.cssName} td {
          border: 1px solid var(--line); padding: 0.6rem; text-align: start; vertical-align: top;
        }
        .${HtmlClass.SUMMARY_COUNTS.cssName} caption, .${HtmlClass.ITEM_TABLE.cssName} caption {
          text-align: start; font-weight: 600; padding-block: 0.5rem;
        }
        .${HtmlClass.SUMMARY_ADVERSE.cssName} { padding-inline-start: 1.5rem; }
        .${HtmlClass.SUMMARY_ADVERSE.cssName} li { margin-block: 0.5rem; }
        .${HtmlClass.ROOM.cssName} { border-top: 1px solid var(--line); padding-top: 0.5rem; }
        .${HtmlClass.ITEM_TABLE.cssName} { display: block; overflow-x: auto; }
        .${HtmlClass.ITEM_ROW.cssName} { background: var(--paper); }
        .${HtmlClass.ITEM_STATUS.cssName} {
          font-weight: 700; white-space: normal; background: var(--wash); border: 1px solid var(--line);
        }
        .${HtmlClass.ITEM_NOTE.cssName}, .${HtmlClass.ITEM_WEAR.cssName} { margin: 0 0 0.75rem; }
        .${HtmlClass.ITEM_WEAR.cssName} { border-inline-start: 0.2rem solid var(--line); padding-inline-start: 0.5rem; }
        .${HtmlClass.EVIDENCE_GALLERY.cssName} {
          display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr)); gap: 1rem;
        }
        .${HtmlClass.EVIDENCE_FIGURE.cssName} { margin: 0; width: 100%; max-width: 20rem; }
        .${HtmlClass.EVIDENCE_FIGURE.cssName} img { display: block; width: 100%; height: auto; }
        .${HtmlClass.EVIDENCE_CAPTION.cssName} { margin-top: 0.4rem; font-size: 0.9rem; }
        .${HtmlClass.EVIDENCE_MISSING.cssName} { border: 1px dashed var(--line); padding: 0.75rem; }
        .${HtmlClass.SUPPLEMENT.cssName} { border-inline-start: 0.2rem solid var(--line); padding-inline-start: 0.75rem; }
        .${HtmlClass.REMEDIATION.cssName} { background: var(--wash); padding: 1rem; margin-block: 1rem; }
        .${HtmlClass.REMEDIATION_URGENCY.cssName} { font-weight: 700; margin-top: 0; }
        .${HtmlClass.PROVENANCE.cssName} { border: 1px solid var(--line); padding: 1rem; }
        .${HtmlClass.PROVENANCE.cssName} dd { margin: 0 0 0.75rem; }
        .${HtmlClass.DISCLAIMER.cssName}, .${HtmlClass.TENANT_AGREEMENT.cssName} { line-height: 1.7; }
        .${HtmlClass.INTEGRITY.cssName} { border-top: 0.2rem solid var(--ink); padding-top: 1rem; }
        .${HtmlClass.INTEGRITY_LABEL.cssName} { margin-block: 0.75rem; }
        .${HtmlClass.INTEGRITY_LABEL.cssName} dd { margin: 0; font-family: ui-monospace, monospace; }
        .${HtmlClass.TEXT_EN.cssName} { font-weight: 600; }
        .${HtmlClass.TEXT_ZH.cssName} { color: var(--muted); }
        .${HtmlClass.TEXT_ORIGINAL.cssName} { white-space: pre-wrap; }

        @media screen and (max-width: 40rem) {
          .${HtmlClass.REPORT.cssName} { padding: 0.75rem; }
          .${HtmlClass.IDENTITY_FIELD.cssName} { grid-template-columns: minmax(0, 1fr); gap: 0.25rem; }
          .${HtmlClass.IDENTITY_FIELD.cssName} dd { margin-bottom: 0.75rem; }
          .${HtmlClass.ITEM_TABLE.cssName} th, .${HtmlClass.ITEM_TABLE.cssName} td { padding: 0.4rem; }
        }

        @media screen and (prefers-color-scheme: dark) {
          .${HtmlClass.REPORT.cssName} {
            color-scheme: dark;
            --paper: #182226; --ink: #edf4f5; --muted: #c0d1d5; --line: #7b979f; --wash: #25343a;
          }
        }

        @media (forced-colors: active) {
          .${HtmlClass.REPORT.cssName} {
            forced-color-adjust: auto;
            --paper: Canvas; --ink: CanvasText; --muted: CanvasText; --line: CanvasText; --wash: Canvas;
          }
          .${HtmlClass.ITEM_STATUS.cssName} { border-color: CanvasText; }
        }

        @media print {
          @page { size: A4; margin: 14mm; }
          .${HtmlClass.REPORT.cssName} {
            color-scheme: light;
            --paper: #ffffff; --ink: #000000; --muted: #222222; --line: #555555; --wash: #ffffff;
            max-width: none; margin: 0; padding: 0;
          }
          .${HtmlClass.ITEM_TABLE.cssName} { display: table; overflow: visible; table-layout: fixed; }
          .${HtmlClass.ITEM_TABLE.cssName} th, .${HtmlClass.ITEM_TABLE.cssName} td { width: 25%; padding: 0.35rem; }
          .${HtmlClass.EVIDENCE_GALLERY.cssName} { display: block; }
          .${HtmlClass.EVIDENCE_FIGURE.cssName} { break-inside: avoid; margin-block: 0.5rem; }
          .${HtmlClass.ITEM_ROW.cssName} { break-inside: avoid; }
          /* Inline spans cannot prevent a page break; keep their existing block parent together. */
          *:has(> .${HtmlClass.TEXT_EN.cssName} + .${HtmlClass.TEXT_ZH.cssName}) { break-inside: avoid; }
          .${HtmlClass.SECTION.cssName} h2, .${HtmlClass.SECTION.cssName} h3 { break-after: avoid; }
        }
    """.trimIndent()
}
