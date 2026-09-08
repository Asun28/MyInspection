---
id: T4-SYMBOL-MARKDOWN-PARSER
title: Provide a visible top-level Markdown contract parser for symbol acceptance
status: in-progress
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
