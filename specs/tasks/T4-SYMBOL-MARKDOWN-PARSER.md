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
