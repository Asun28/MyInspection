---
id: T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE
title: Remote delivery of pure-data typography profile and signed metric guard
status: merged
branch: T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE
worktree: C:\wt\T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE
depends_on: [T3-PDF-RENDERER]
parallelizable_with: []
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportTypography.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportTypographyTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportSourcePurityTest.kt
forbid:
  - changing TextMeasurer, MeasuredText, TextRun, ReportComposer, PdfTextOp, builder, existing fakes or core e2e
  - Android imports, font assets, actual glyph measurement, wrapping, pagination or a second default table
  - claiming pure-data tests prove composed appendix capacity, Android glyphs, CJK coverage or visual clipping
non_goals:
  - request binding, snapshot propagation and caption elision metrics (T3-PDF-MEASUREMENT-BINDING)
  - PdfTextOp forwarding and actual rounded placed-box validation (T3-PDF-TEXT-METRICS-OPS)
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 immutable ReportTypography fixes TITLE=12pt/5mm, BODY=11pt/6mm and CAPTION=9pt/4mm; explicit custom profiles preserve their own rows without changing DEFAULT"
  - "A2 EN resolves to LATIN_SANS and ZH/ORIGINAL/NEUTRAL resolve to CJK_FALLBACK without character inspection"
  - "A3 inert TextMetricSnapshot carries style, language, role, font size, baseline offset and signed glyph top/bottom; malformed data may be constructed, then the internal line-box guard rejects non-finite point values, nonpositive size, negative baseline, wrong bound signs or either escaping edge"
  - "A4 direct tests isolate the guard from any profile binding check, exercise each field with NaN and both infinities, accept negative glyphTopPt and exact edge equality, and reject zero/negative font sizes"
  - "A5 the closed ReportSourcePurityTest inventory explicitly adds ReportTypographyTest.kt; keep its complete source scan, exact inventory assertion and all forbidden-reference checks unchanged."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: direct profile/role/signed-metric tests pass and the unchanged report suite and core e2e remain green; no claim of composition or platform glyph verification.
review_gate: codex {verdict:pass}
hygiene: named compiling assertion mutations change a default row or role, remove positive-size or finite checks, relax signed/edge guards, and reject valid edge equality; keep evidence concise and never count compiler failures as assertion coverage.
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge
---

# T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE

This card adds only the immutable profile, font-role enum, inert metric snapshot and shared line-box guard. Existing TextStyle and TextLanguage are reused. A malformed snapshot is data until a consumer explicitly calls the guard; do not reject in the snapshot constructor or silently substitute defaults.

All point values are finite. fontSizePt > 0, baselineOffsetPt >= 0, glyphTopPt <= 0 and glyphBottomPt >= 0. A glyph fits a supplied point-height exactly when baselineOffsetPt + glyphTopPt >= 0 and baselineOffsetPt + glyphBottomPt <= heightPt. Negative top is valid; equality on both edges is valid. The helper neither derives metrics nor converts geometry.

The DEFAULT rows remain 12pt/5mm, 11pt/6mm and 9pt/4mm. Their assembled two-photo appendix regression belongs intact to T3-PDF-MEASUREMENT-BINDING: (2*5+2)+2*(108+3*4+2)=256mm within the existing 257mm body, with two maximum-three-line captions on the same page. This card alone does not establish that assembly works.

## Remote delivery provenance

This is the remote-delivery alias of locally completed T3-PDF-TYPOGRAPHY-CONTRACT, not another product feature. Port only the original three source/test files from feature 9edb9b2698db7ee9314cd371b7cebeff1922e5a5. Local merge 8779d05dfde5e4e57fef4c3fafaf16d27c8a7e46 and archive commit 3e67ba716fe9b5c16ff37df8c7e0af6f3d80d845 remain historical evidence; they are not proof that the new remote baseline passes.

The original local delivery passed five direct tests, 328 report tests, six core e2e tests and 12 named compiling assertion mutations. Preserve those records. On the fresh remote-based alias worktree, record a genuine RED before restoring the exact production file, then run the complete DoD, verify, scope, license, secrets, diff-budget, independent R3 and candidate CI gates. Remote merge must bind the reviewed and tested head. Do not rewrite the original feature history or use its unrelated local ancestors as remote-delivery scope.

Original feature budget: three files, 137 additions, 7469 LF-normalized unified-diff characters. Any repair is included in the actual remote diff budget. The existing 1000-line / 60000-character hard gate remains in force. All acceptance above remains unchanged; composition, binding and real-device evidence remain outside this alias.

## Remote delivery receipt (2026-09-17 NZ)

PR [#305](https://github.com/Asun28/MyInspection/pull/305); reviewed head 9f06217c8c521e72ce9faca2513bad0d18a46fa2; squash merge 3351c06c99ba8d85e3e008b7a89cdac43bb2470d. Formal Sol/high attempt 1 PASS, empty reasons; exact-candidate CI [35159732680](https://github.com/Asun28/MyInspection/actions/runs/35159732680) passed verify and required.

Remote execution at baseline 1ce3f5ae passed genuine RED, 255 report tests, six E2E tests and 12 compiling mutations, each killed by a named assertion with exact restoration. Before ship, the branch fast-forwarded to cd7e2016; comparison found no report production/test/E2E or Gradle/dependency input change. A new original-controller RED at cd7e2016 failed for the missing API, followed by exact production restoration and full ship DoD.
The test header alone was corrected to distinguish remote 255 tests from historical local 328. Mutation input test SHA-256 769249A43320D4A10EBAB3CB2DCDE7F7E8162FCB629A36156540178603C08AF7 and final header-corrected test SHA-256 5476FC14D285D2FCC79AEBD8190FA1DCD78A6FCDA284E0672C0058AD408D4A44 have identical executable bodies. The 12 mutations are preserved as the actual earlier remote runs with unchanged relevant inputs; no post-alignment rerun is claimed.
Final DoD passed 255 report and six E2E tests with no failures/errors/skips; verify additionally ran 976 core tests, with four pre-existing media skips and no failures. Normal DoD, verify, scope, licenses, secrets and budget gates passed: three files, 138 changed lines and 7,576 diff characters. Local feature 9edb9b26 / merge 8779d05d and its 328-test evidence remain separate history. Composition, snapshot binding, platform glyphs, clipping and device acceptance remain pending.

Original-main task-loop cleanup exited 0 at 2026-09-16T23:03:37.8710478+00:00; canonical worktree and local branch are absent. The controller independently checked the copied evidence before cleanup. Evidence below is retained in the ignored local delivery ledger; these hashes identify the checked receipts and are not a claim that the files are published by this metadata PR.

- `_local/rotating-card-orchestrator/pdf-evidence/profile-remote/final-ship/manifest.json` — SHA-256 `FCD91778D25EF7A938C02D9EC627879A67FFB1FAFCC51837609E74EAC714EF74`.
- `_local/rotating-card-orchestrator/pdf-evidence/profile-remote/root-cleanup-audit/audit.json` — SHA-256 `F4B5DB52EB4F608278945261D6E67936C7A3F6820985855E0821133BD108CF6B`.
- `_local/rotating-card-orchestrator/pdf-evidence/profile-remote/root-cleanup-audit/cleanup-result.json` — SHA-256 `8BED38F114F152A386DD963B8F5F15DE70470C2238A1F6F742E1033FB986235F`.
