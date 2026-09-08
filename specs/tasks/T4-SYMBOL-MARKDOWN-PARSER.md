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

Get-SymbolMarkdownBlock takes Text and Label and returns Value, Index and Length for exactly one visible top-level fenced block. It never executes the content. Get-SymbolMarkdownVisible takes Text and returns a position-preserving text projection for subsequent heading/table checks. This is a constrained Markdown contract view, not a browser renderer: block quotes, lists, raw HTML and code blocks are not top-level contract evidence. Non-comment inline HTML excludes its containing visible block; inline comments are masked. Code literals do not open comments.

The subsequent PR263 must replace both embedded runner loaders, both manifest readers, and checkpoint/delta extraction, then exercise each actual consumer. This prerequisite alone does not fix or approve those callers.

## Budget

Planned complete payload: approximately 300–500 changed lines / 15–30k characters including helper, tests and this card. Official complete-diff limits remain 1000 lines / 60000 characters.
