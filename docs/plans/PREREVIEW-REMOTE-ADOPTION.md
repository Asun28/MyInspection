# PR review v2: remote adoption

The user requested push, PR creation and remote merge on 2026-09-17 after locally approving and completing the hunk-ordinal schema revision and FACTS-LIB. Remote master has none of the prereview foundation. Adopt the reviewed final contract directly as schema_version 1 / schema_revision 1, then its policy documents, then the library. Do not replay revision 0, import the divergent local master, or claim that the original RECORDS / UNIT-ID-REVISION card graph has shipped remotely.

## Delivery sequence

1. T0-PREREVIEW-REMOTE-SCHEMA: final record schema, checker, schema fixtures including the worker-envelope projection, and the existing Prereview configuration defaults. Source local tree `4c9735ef`, schema origin `3f055aa7`, approved revision `269d5268`.
2. T0-PREREVIEW-POLICY-SOURCE: publish only the two exact historical source files in a dedicated raw fixture directory, independently pinned by inventory, length, Git blob and SHA-256. Original wording and references remain unchanged.
3. T0-PREREVIEW-POLICY-SOURCE-CHECK: publish the complete audited replacement recipe, offline source-identity/replay verifier and four negative probes. These two pending prerequisites address POLICY PR #308's durable-source evidence gap.
4. T0-PREREVIEW-REMOTE-POLICY: protocol and checklists from that same tree (original deliveries `37881cf6` and `69bd4e20`, ordinal wording revised in `269d5268`). Protocol capability descriptions remain design contracts; this adoption does not activate workers or the runner.
5. T0-PREREVIEW-REMOTE-FACTS: the two FACTS-LIB files from reviewed local tip `91bf3cbe`, retaining the corrected ignored-file and hunk-ordinal acceptance. Production SHA-256 `23A38171AAAF97303EDA973586041A07F8500EE7BF38275AA01CCFE156F7ACC2`.

The source commits above identify local provenance, not remotely merged PRs or current-candidate acceptance. Each remote candidate is reviewed again with the current baseline harness, required CI and its computed selftest tier. Keep source byte identity for implementation/fixtures except for review corrections recorded in the adoption card, and distinguish reused mutation evidence from newly run checks. PR 302 requires a checker conditional-fragment fix and correction of the schema comment locator; neither changes revision 1 validation fields or unit identity shape.

## Dependency boundary

FACTS-LIB loads the existing `_gitbase.ps1` API, reads FrozenPaths and four Prereview config values, and hashes the raw checklist then schema bytes from the merge-base tree. Both policy files must land before the library. The original revision also migrated the existing local RECORDS consumer; remote master has no such consumer, so publishing it is outside this adoption. RECORDS, workers, runner, pack/state, Phase 1b and changes to the R3/ship chain remain pending. FrozenPaths / DocSyncMap registration remains with the original Phase-1b integration contract; the schema is a reviewed contract and later changes still require version review.

The local source calls its ignored-file / hunk-identity corrections TD176 and its deleted-path candidate-anchor follow-up TD177. Those numbers already identify different remote debts. They are provenance only: do not overwrite the remote rows. The deleted-path candidate/anchor contract remains unresolved before future worker/prompt integration; this adoption preserves the approved library behavior and does not implement those consumers.

## Verification

Schema: checker default modes, rejection inventory, hunk ordinal and revision 0/1 cases; configuration values remain unchanged. Policy: schema status-code anchor table plus all four nonempty checklist lenses and existing-reference checks. Facts: all existing Windows selfchecks plus actual adopted-repository base/config/policy-hash smoke checks. The literal-quote filename case is Unix-only and is not claimed on Windows. No live worker, credentials or outbound model call is part of these tests.

POLICY source comparison must be reproducible from committed files. Ignored local source copies establish provenance during preparation but do not satisfy this delivery requirement. Register and merge the SOURCE and SOURCE-CHECK prerequisites before resuming POLICY; preserve both earlier real R3 verdicts and obtain separate authorization for further formal review. The source text is historical data under the existing prereview fixture tree, not a new active policy.

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile landed the local-only parts of local master's PR review v2 phase 1a chain on master: PROTOCOL-DOC (`a66af219`), CHECKLISTS (`2782b55b`), RECORDS (`dec30514`), FACTS-LIB (`b675d6a6`) and STATE-1A (`62ec5f3b`). For the schema and its checker (SCHEMA and UNIT-ID-REVISION), master keeps origin's versions from T0-PREREVIEW-REMOTE-SCHEMA. `docs/PREREVIEW-PROTOCOL.md`, `docs/PREREVIEW-CHECKLISTS.md` and `scripts/_prereview-facts.ps1` are therefore on master at their local merged bytes, and the statements above that master has none of that foundation describe origin before the reconcile. How the remaining remote adoption cards proceed is a user decision.
