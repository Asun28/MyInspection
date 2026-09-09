---
id: T4-SYMBOL-MARKDOWN-PARSER
title: Provide a visible top-level Markdown contract parser for symbol acceptance
status: merged
depends_on: []
parallelizable_with: []
branch: T4-SYMBOL-MARKDOWN-PARSER
worktree: C:\wt\T4-SYMBOL-MARKDOWN-PARSER
allow_paths:
  - scripts/_symbol-markdown.ps1
  - scripts/symbol-markdown-check.ps1
  - specs/tasks/T4-SYMBOL-MARKDOWN-PARSER.md
forbid:
  - Changing shared ship/review gates, adding dependencies, or touching product code and design rules
  - Merging or approving PR263 while its visibility finding remains unresolved
  - Removing existing symbol obligations or historical review evidence
non_goals:
  - PR263 integration, symbol design publication, general HTML rendering or execution of extracted code
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: Raw-regex extraction in PR263 cannot distinguish visible Markdown contracts from nested comments/fences. This prerequisite supplies independently tested structural extraction; all five real consumer entry points remain a required subsequent PR263 repair.
acceptance:
  - "A1 only one matching genuine top-level closed fenced block is extractable, with exact original content offsets"
  - "A2 visible projection excludes comments, code, quoted/list-nested content and unsupported inline HTML while retaining visible headings and table text"
  - "A3 variable backtick/tilde fences and comments are interpreted structurally; unclosed markup is rejected without rejecting comment-like code literals"
  - "A4 real parser tests and targeted guard mutations pass; no new runtime package or network access is introduced"
dod_command: pwsh -NoProfile -File scripts/symbol-markdown-check.ps1
dod_exit: 0
dod_assert: SYMBOL-MARKDOWN-TEST PASS; literal positive controls and named visibility/uniqueness/closure failures pass, and every declared guard mutation is killed
review_gate: codex {verdict:pass}
hygiene: literal fixtures exercise actual helper functions; in-memory guard mutations use isolated modules, never rewrite the tracked helper
doc_sync: record helper API and runtime assumptions here; PR263 integration is explicitly pending, and this card's merged status denotes only the prerequisite
---

# T4-SYMBOL-MARKDOWN-PARSER

Historical-context note: the dated sections below retain their original checkpoint wording. Their pending states and earlier counters are historical; the final remote receipt at the end records the completed prerequisite delivery. PR263 integration and design publication remain separate and pending.

The user approved this bounded prerequisite PR on 2026-09-08 after PR263 reached 795 changed lines / 58,699 characters. It does not reset or supersede PR263's BLOCK history. The separately authorized PR263 review remains a later action.

## API and runtime

PowerShell's bundled ConvertFrom-Markdown supplies the Markdown AST; no package is installed. Local discovery used PowerShell 7.6.5 / Markdig.Signed 0.44.0. The DoD exercises the actual API and fails if unavailable; it does not assume other runtime versions were tested.

Get-SymbolMarkdownBlock takes Text and Label and returns Value, Index and Length for exactly one visible top-level fenced block. It never executes the content. Get-SymbolMarkdownVisible takes Text and returns a position-preserving text projection for subsequent heading/table checks. Its explicit IncludeListText switch retains legitimate numbered/bullet clauses for source-document consumers; the default metadata view still excludes lists. Even with that switch, nested headings, quotes, HTML and code are excluded. This is a constrained Markdown contract view, not a browser renderer. Non-comment inline HTML excludes its containing visible block; inline comments are masked. Code literals do not open comments.

The AST distinguishes code and hierarchy; source-aware comment ranges handle inline/multiline comments and multiple openers on one line. A closed comment followed by an unclosed comment must fail even when Markdig groups both into one HtmlBlock. No raw-regex fallback is used for fenced-block discovery.

The subsequent PR263 must replace both embedded runner loaders, both manifest readers, and checkpoint/delta extraction, then exercise each actual consumer. This prerequisite alone does not fix or approve those callers.

## Budget

Planned complete payload: approximately 300–500 changed lines / 15–30k characters including helper, tests and this card. Official complete-diff limits remain 1000 lines / 60000 characters.

## Local verification evidence (2026-09-08)

RED was recorded at eecb282dd788b9b35d634997215c4a74d3d6b375 before the helper existed. The first implementation passed 54 behavior cases and 14 guard mutations; independent preflight then found the same-line closed-comment/new-opener defect. The actual helper reproduced that defect before repair, alongside hidden checkpoint and multiline-comment cases. The repaired checker now passes 68 behavior cases and kills 19 named guard mutations in isolated in-memory modules. Every mutation requires a unique source target, valid syntax and a failure from its specific real fixture; setup failures are not accepted as behavioral kills. This is local prerequisite evidence, not formal R3 approval or PR263 integration acceptance.

These results bind to helper SHA-256 563C51C98FBD04D41680FD83669B21B2A83D48501A1257EF5764CF96C88474F4 and checker SHA-256 1684C0369CC3513B74403E31682CBAB468074D6C49801AF212B41ACCB4F4E209. Tests never mutate tracked files. Full traces remain under the worktree's ignored .review directory.

## PR274 first review and repair

Normal ship at b3a9daaf6528341c686efafe4aea378fc5560c0b passed DoD, project verify (934 tests including E2E; zero failures/errors, four existing skips), scope, license, secrets and the official 342-line / 25,879-character budget. Exact-head CI run 34212081811 succeeded. Formal R3 returned BLOCK for bare-CR/mixed line boundaries and comment-like text inside HTML attributes; that review and its counter remain preserved, not reset. The preceding 68/19 evidence applies to that earlier source and did not cover those defects.

The repair first reproduced ten failing cases through the actual helper. Extraction and list masking now locate LF, CRLF and CR boundaries without normalizing original text. Comment scanning uses the existing Markdig HtmlHelper.TryParseHtmlTag(ref StringSlice, out string) lexer for non-comment tokens; live reflection and invocation verified that public signature on the same 0.44.0 assembly. It skips only the parsed token, not a whole HTML block: real comments inside unsupported HTML still require closure and still hide a later contract.

Current local repair verification passes 83 behavior cases and kills 23 named guard mutations, including each of the three line-boundary sites and HTML-attribute classification. Mixed-ending bodies use literal expected content, and visible projection checks retain every original CR/LF position. The previous content-start mutation was retargeted to CRLF advancement because its old +1-to-+2 perturbation became behaviorally equivalent after the repair. No assertion or original fixture was removed.

These repair results bind to helper SHA-256 AA20115E8FFE38E2D13B4BD5D83B518D3BCA49EFB0FD3A146C83261D1122BBCB and checker SHA-256 4A9439B3725CB437A4C41FCF005927FFC91ADA202E75AA312E81028A346BDDB5. Repair-source full verify and second formal R3 are pending; earlier CI does not approve these new bytes. PR263 integration remains separately pending.

## PR274 second review and local repair

The second normal ship at d2a6c44d6d87e79f1c4f36dcc8ef859c89433865, after ordinary merge of base fcdb4d8ca5f4d76c2fe73fc6177bc828e239ce6c, passed DoD83/23, project verify934 (zero failures/errors, four existing skips), scope, licenses, secrets and the official 382-line / 31,092-character budget. CI34219113137 succeeded. Formal R3 still returned BLOCK: literal comment openers in Markdown link/image/reference-definition titles were misclassified. Both outcomes remain preserved and the counter is two; no reset or third formal review has occurred.

Actual helper regressions first reproduced the three reported title contexts, all three title delimiters, multiline definitions and literal link destinations. The repair uses the bundled AST's UrlSpan, TitleSpan and LabelSpan rather than another delimiter grammar. Reference uses take their title/destination from the actual definition; synthetic heading references and synthetic reference-group spans are not treated as source text. Follow-on real regressions caught incorrect masking of an earlier visible heading and full/collapsed/shortcut reference labels before those cases were fixed.

The visible projection masks non-rendered destination/title/reference-key spans while retaining rendered labels and original character/newline offsets. LiteralInline text classified within an actual link stays literal; genuine HtmlInline comments in link labels and comments around whole contracts remain excluded. The projection is still constrained source text, not rendered HTML: Markdown punctuation is retained.

The current local checker passes 108 behavior cases and kills 32 named mutations. All prior fixtures remain. This evidence binds to helper SHA-256 C7C1C71209A5B588FF3976B65779524E6FF4B1CC85F19B0C15AD79455AC128AA and checker SHA-256 5E17BA30B8D2A253A6D86108F1C9DC57336F86EEDD895EBBBE9BE99B9F53187D. These latest repair bytes have not been through full verify, push or another formal R3. Further review requires explicit human authorization at the two-BLOCK cap; PR263 authorization is separate.

## Authorized continuation (2026-09-09)

The user explicitly approved preserving both BLOCK records, one official counter reset and exactly one additional formal R3 for PR274. This does not authorize another review if that invocation BLOCKs and does not consume PR263's separate grant. The coordinator released base ae7c97b05148373d5c3f8a1118fa7522ae0999b0 after PR281 completed. Ordinary merges retain all history; helper/checker hashes above remain unchanged. The next normal ship must revalidate DoD, full verify, scope, licenses, secrets, complete diff budget, independent review and exact-head/stable-base CI before merging. The preceding pending statements are historical checkpoints, not claims that the latest candidate has passed these gates.

## PR274 third review and coverage evidence

Normal ship at 762dcba5bbbe316ee98b60edda8505001e7427f8 against ae7c97b05148373d5c3f8a1118fa7522ae0999b0 passed DoD108/32, project verify934 (zero failures/errors, four existing skips), three-path scope, licenses, secrets and official455lines/39591chars. Exact-head CI34275835089 succeeded. Formal R3 BLOCKed on processing-instruction/declaration/CDATA comment-literal handling and missing dedicated fixtures. The one authorized reset and review were consumed; all three verdicts and the old rounds2 snapshot remain preserved.

Read-only actual calls on that exact helper contradicted the alleged rejection: `<?x <!-- ?>`, `<!A <!-- >` and `<![CDATA[<!--]]>` each parsed as HtmlBlock followed by a genuine fenced contract, extracted its literal body and retained a later visible heading. Independent calls to bundled HtmlHelper.TryParseHtmlTag returned true and the whole token for each, with final StringSlice.Start values11/10/16 on PowerShell7.6.5 / Markdig.Signed0.44.0.0. These are observed runtime results, not a waiver of the formal BLOCK.

The user subsequently authorized tests/card-only coverage work and ONE further normal-gated formal R3, without another reset or helper change. Nine added cases exercise extraction, visible projection and a genuine unclosed comment after each raw token. Three additional class-specific in-memory mutations bypass only that token class's existing lexer path; each is killed by its named real extraction fixture. The expanded suite passes117 behavior cases/35 mutations, with all prior fixtures retained. This is characterization/coverage of existing correct behavior, not a newly reproduced production defect or a new production RED claim.

The helper remains SHA-256 C7C1C71209A5B588FF3976B65779524E6FF4B1CC85F19B0C15AD79455AC128AA; the expanded checker is SHA-256 4DF09C64D29E352BE4E2E9E5E47EA0962EFD79CD67557EC6D5F148633612DE23. Focused trace: .review/parser-raw-html-coverage-20260909.log. Full ship/review/CI for this expanded checker are pending; prior CI covers only762dcba5. PR263 integration remains separate and pending.

## Final remote delivery receipt (2026-09-09)

[PR274](https://github.com/Asun28/MyInspection/pull/274) merged at2026-09-08T23:46:40Z as18741ac29a20da426ffe99c02c924f2d1b3b29f5. Reviewed head3b7d51f1519f4b4817289edf2d14c2a68173474e against base7c5b38613e9b7a56f4e0264ae1ba4d8937d0d6ed received independent Sol high R3 PASS with empty reasons; [exact-head CI34291612516](https://github.com/Asun28/MyInspection/actions/runs/34291612516) completed SUCCESS. Reviewed and merged trees both equal0aecf5fe5e76bc5d9f9e9494e20d3c1bd25cebad.

Normal ship36066 exited0 after117 behavior cases/35 named guard mutations, project verify934 tests (zero failures/errors, four existing skips, including Golden Evidence E2E), scope/licenses/secrets and official484changedlines/42859chars passed. Official cleanup then exited0 with lessonscheckPASS; the exact original worktree and local branch were verified absent. All24 review files were copied and hash-compared before cleanup, with T24/T35 receipts retained separately; the three preceding BLOCK outcomes and original RED remain preserved. No additional counter reset was used for the final successful iteration.

Preserved evidence SHA-256: final verdict243E45FCCA0161D8D0C8D30F163167B4CD2A52F4D46326AAAE742C654A2FD416; T24 receipt6FC6AE24B528C745278C969225C5792EB9DAF78A6B86D9FF686281001C12D9F0; T35 receipt2CAC1A33A955C88E26D836E48FA70FEA84E2DB34DBCAE0E348D605A8D3C1B07A; cleanup log11DC63E05DDC07B67C78E3EFBFA3C9851DE8179991FA47C55F0B72DFBD1768D8; final ship log624167D292AD2A51BB6218E08F79C0F724BCADF0F19C685DE45592CFD6B0F1B3. These logs remain in the delivery owner's ignored evidence directory, not a new runtime dependency or an assertion that a later environment reran them. The helper/checker hashes above are unchanged.

This closes only the Markdown prerequisite. PR263's actual-consumer repair and the symbol design publication are not delivered by this receipt. R5.5 reuses the existing L26 pinned-runtime API verification rule; no duplicate lesson is added.
