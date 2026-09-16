---
id: T4-SYMBOL-REMOTE-CONTRACT
title: Recover the approved symbol chrome V2 delivery contract with explicit scope
status: in-progress
depends_on: []
parallelizable_with: []
branch: T4-SYMBOL-REMOTE-CONTRACT
worktree: C:\wt\T4-SYMBOL-REMOTE-CONTRACT
allow_paths:
  - specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md
  - specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md
forbid:
  - Changing design documents, Kotlin, Gradle, schema, tokens, dependencies, shared workflows or shared gate implementations
  - Claiming local historical tests or human adjudication as current remote implementation or R3 approval
  - Weakening the original acceptance obligations, discarding earlier review history or bypassing a failed gate
non_goals:
  - Publishing the symbol design itself, UI implementation, archive closeout or unrelated local work
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: PR260 lacked matching scope and row checks; PR263 exposed file-wide location blindness, syntax-marker counting, then raw-regex discovery of runners, manifests and checkpoint/delta inside hidden Markdown. This recovery preserves the exact obligations and uses the merged shared parser at every affected consumer.
acceptance:
  - "A1 the entire change is confined to this scope card and T4-DESIGN-SYMBOL-CHROME-V2, whose status remains todo pending design publication"
  - "A2 the target card preserves approved OD-1/OD-2 decisions and every original acceptance check, adds 15 unique row checks for all identified repairs, and distinguishes historical tables from final substitution deltas"
  - "A3 PR260 and its two BLOCK outcomes remain traceable; the user-approved additional review is not represented as a fresh unlimited review allowance"
  - "A4 card schema, archive index, secret scan, complete-diff budget, formal independent review and candidate CI pass before remote merge"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-SYMBOL-REMOTE-CONTRACT; if($LASTEXITCODE){exit 1}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-DESIGN-SYMBOL-CHROME-V2; if($LASTEXITCODE){exit 1}; $ErrorActionPreference='Stop'; . ./scripts/_symbol-markdown.ps1; $c=Get-Content -LiteralPath 'specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md' -Raw; & ([scriptblock]::Create((Get-SymbolMarkdownBlock $c 'powershell symbol-metadata').Value)); pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; exit $LASTEXITCODE
dod_exit: 0
dod_assert: both cards validate; target remains todo with exact original25+repair15 tuples and all12 final-delta rows; 248 adversarial metadata cases and46 real-consumer visibility cases enforce specific rejection categories; archive projection is unchanged
review_gate: codex {verdict:pass}
hygiene: card/index checks are reused; embedded acceptance and adversarial checks stay inside these two cards, with no product code, dependency or shared testing framework changes
doc_sync: include this management card's post-merge status projection in its own PR and retain PR260/history links; the symbol design card stays todo until its separate implementation PR succeeds
---

# T4-SYMBOL-REMOTE-CONTRACT

## Registration of the existing recovery scope

This is the baseline registration required by T241 for the existing open PR #263. It records the already approved two-card scope and the unchanged acceptance contract. It publishes no new runner, test implementation, product design, PASS verdict or merge claim. The executable `symbol-metadata` block is delivered by PR #263; until then this card's implementation DoD is expected to fail at that missing block. Card schema validation must pass.

## Preserved review history and authorization

PR #260 recorded two BLOCK outcomes: missing row-level acceptance at `5d2481a98f60c7d9c6816db5bed1ed2a511011ea`, then missing matching task scope at `d09414d034f6d700a31953c8923ed750a9a0558b`. The user authorized the existing PR #263 recovery and one additional formal review without rewriting those outcomes. Its `4b42c03da86adbd7224d8e9030ab102b203dc569` review passed, but delivery stopped at `CI-GATE-BASE-MOVED`; it did not merge.

A separately authorized additional review at `da28f192` returned BLOCK on file-wide placement blindness and syntax-marker counting. The next authorized repair review at `af230c16cd39c88c601a4af983c1a0f777f52c5e` returned BLOCK on outer-Markdown visibility at both runner loaders, both manifest consumers and checkpoint/delta projection. These remain failed historical verdicts.

The user subsequently authorized one counter reset and ONE further formal review for the PR #263 repair. That scoped grant remains unused at this registration; the preserved counter is 2. This registration itself consumes neither operation and authorizes no additional review after another BLOCK. No new branch, reset or retry may be used to evade the remaining limit.

PR #274 supplied the shared Markdown parser and PR #285 closed its metadata. Separately authorized PR #289 preserved the existing local R4 receipt and eight R3 rounds verbatim at `1747a4de20e7e62c7205d83feb2a456ee242976e`; its own review grant is complete and cannot be reused for PR #263. The history remains local-delivery evidence, not current remote design approval. The original target design card stays `todo` until its separate implementation succeeds.

## Delivery boundary

PR #263 must preserve the original 25 checks, the 15 distinct repair-row checks, all 12 final-delta rows and historical evidence, implement the declared visibility checks, and pass current DoD, verify, scope, license, secrets, complete-diff budget, the authorized independent R3, and candidate CI before remote merge. Registering this card on the baseline supplies the scope standard to those gates; it does not bypass any gate or preapprove the implementation.
