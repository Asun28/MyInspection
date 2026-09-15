# PR review v2, Phase 1a: the discovery packet protocol

> **What this is**: the human-facing protocol for the advisory discovery packet that an author runs before the first
> `ship` of a product card. Two workers read the same prompt in parallel and return structured records; the packet
> lists what they found and what they covered. It is **not a gate**: codex R3 stays the only merge gate, the packet
> never enters the codex prompt, no record has a field named `verdict`, and no status value is `pass` or `block`.
> **Status**: a human-facing description of the contracts the Phase-1a cards carry (`docs/TASK-BOARD.md`, section
> "PR review v2"; the cards are self-contained and win on any discrepancy, and known wording gaps in sibling cards
> are listed as follow-ups in this card's body); the scripts land card by card and nothing described here runs before
> the RUN card merges.
> **Conventions**: commands are named by subcommand (`prereview.ps1 run`); script paths are cited only for files that
> exist today. Truth sources: `specs/prereview-record.schema.json` (frozen record contract), `scripts/_config.ps1`
> (`Prereview*` knobs, values only), `docs/PREREVIEW-CHECKLISTS.md` (worker-facing checklists, named here and never
> reproduced). Outbound trust surfaces: `docs/TRUST-MANIFEST.md`.

## 1. Two workers, no vote

| worker | provider | required | sees | returns |
|---|---|---|---|---|
| discoverer | Anthropic `claude` CLI, subscription OAuth (`PrereviewDiscoverCommand` empty = built-in adapter) | yes: its failure makes the run `incomplete` | the whole pack, including the exported snapshot tree (`tree/`), read-only | candidates + one coverage record per changed unit |
| lens | DeepSeek, the `api.deepseek.com` anthropic-compatible messages endpoint (`PrereviewLensCommand` empty = built-in adapter) | no: its failure makes it `skipped` with a `skip_code`, its records are discarded whole, the packet proceeds | the model receives `prompt.txt` only (nothing from `tree/` is posted) | candidates + coverage, additive |

Both workers receive byte-identical `prompt.txt`. Records are merged programmatically (section 5); nobody counts votes,
nobody verifies anybody. The lens is one composed lens per run (sections chosen by path class, section 3), not one lens
per class. The outbound kill switch is `PrereviewEnabled=false`: `run` exits `[PRE-RUN-DISABLED]` before building a pack
or spawning anything.

## 2. Canonical order (1a)

The packet runs **from the main checkout**, like `task.ps1`, never from inside the reviewed worktree (L86). The card's
own order is unchanged: RED at the branch start, implementation left uncommitted in the worktree, `ship` makes the commit.

1. A card worktree with a green DoD and an uncommitted implementation.
2. `prereview.ps1 run -TaskId <id> -Base <name> [-Local]`. Pass `-Local` when the card will ship `-Local` (this
   repository's usual mode): base resolution follows the ship mode through `Resolve-ScaffoldBaseRef -PreferLocal`
   (`scripts/_gitbase.ps1`, dot-sourced, never a second implementation) and records `base_mode` (`local` | `remote`)
   plus the pinned base OID. No merge-base with HEAD stops with `[PRE-NO-MERGE-BASE]` before any file is written.
3. The run builds the pack (section 3), writes one `prompt.txt`, launches both workers through the bounded runner
   (both started before either is awaited), validates every record, mints ids, synthesises `missing` coverage, writes
   `state.json` by temp file plus atomic rename and renders `packet.md` next to it, deletes the pack, and prints
   `[PRE-PACKET-READY]` with the paths.
4. The author reads `packet.md`, fixes in the worktree what the packet convinced them of, and ships as today; a run
   after the fixes is a full run over the new snapshot (only then does the state describe the shipped tree). 1a records
   nothing about what the author did with a candidate.
5. After **every** R3 block: `prereview.ps1 link-r3 -TaskId <id> -Round <n>` right away, before the next round
   overwrites the verdict file (section 8).
6. After at least five product cards: `prereview.ps1 recall -Out <path>` decides the checkpoint (section 9).

Where things land, and why:

- The pack: `<temp root>/prereview/<id>/<snapshot_tree>/` (temp root = `[IO.Path]::GetTempPath()`, portable to the
  ubuntu CI legs); outside the worktree so neither `git add -A` nor the next snapshot can pick it up; removed after
  the run.
- State and packet: `<git common dir>/scaffold-prereview/<id>/` holding `state.json` (the single persisted document,
  carrying `snapshot_tree`), `packet.md` and `workers/<worker>-<batch>.*`. Deleting the worktree keeps the state;
  nothing in 1a removes the plane.
- **Nothing is written into the reviewed worktree or its `.review/`**. The codex reviewer runs read-only inside that
  worktree and may open any file there (`docs/TRUST-MANIFEST.md`, R3 row), so the packet and the worker outputs are
  kept out of the tree it is pointed at and out of the prompt it is given. That is the whole guarantee: the scaffold
  never hands the packet to the reviewer; it does not claim the sandbox denies a read of the git common directory.

## 3. What enters the pack and what each worker sees

The snapshot is `snapshot_tree` = `git write-tree` on a temporary index built from `read-tree HEAD` plus `add -A`:
dirty and untracked non-ignored files are included; an untracked file that matches an ignore rule (`.env`, `_local/`,
`.secrets/`, `.review/`) cannot enter. Everything HEAD tracks stays in the snapshot whether or not it matches an
ignore rule (`add -A` never drops a tracked path), so the guarantee for tracked content rests on the repository's own
leak gate: `scripts/check-secrets.ps1` (`task.ps1 ship` and the pre-push hook; `-Strict` before going public) fails
when a secret-shaped path is tracked, so for tracked paths the pack copies nothing the repository does not already
carry; untracked additions are in the diff and scanned (below). The reviewer-identical facts come from `review.ps1 -FactsOut <dir> -Tree <snapshot_tree>` (the FACTS
card): the fence-hardened diff of `merge-base..<tree>`, `--stat`, `--numstat`, the card and the rubric at base, the
FrozenPaths clause, the pinned base ref and OID. There is no second diff pipeline.

| pack entry | content | discoverer (readable in the pack) | lens (posted to the model) |
|---|---|---|---|
| `prompt.txt` | the composed prompt (below) | stdin | stdin, and the only thing posted |
| `facts.json` | `snapshot_tree`, `head_sha`, `base_oid`, `base_mode`, `merge_base`, `policy_hash`, `rubric_sha`, `models`, `risk_class`, `pack_layout_version` | readable | no |
| `units.json` | one unit per hunk: `unit_id` = `<path>#<sha256(normalised hunk body)[:12]>`, `file`, `hunk_header`, `body_sha256`; binary or over-`PrereviewMaxFileBytes` files degrade to `<path>#file` (identity as defined by FACTS-LIB A5; two byte-identical hunks in one file share an id, a collision that card owns, see this card's follow-ups) | readable | no |
| `diff.patch` | the same fence-hardened diff bytes the codex reviewer gets | readable | via the prompt |
| `stat.txt`, `numstat.txt` | the reviewer's `--stat` and `--numstat` | readable | no |
| `card.md`, `rubric.md`, `acceptance.json` | card at base, `docs/QUALITY-RUBRIC.md` at base, the card's acceptance list projected as JSON | readable | via the prompt |
| `checklists.md` | `docs/PREREVIEW-CHECKLISTS.md` (it enters the pack and the policy hash) | readable | selected sections via the prompt |
| `files/**` | full text of every changed file up to `PrereviewMaxFileBytes`; `docs/**` and `context/**` up to `PrereviewMaxDocBytes`; larger files are not copied and are listed as truncated with their size (the SLICES card) | readable | via the prompt (changed-file slice) |
| `tree/**` | the snapshot exported with `git -c core.autocrlf=false checkout-index --prefix`: exactly the snapshot tree's content, so every tracked file plus untracked non-ignored files; an ignored untracked file cannot enter | readable, `Read`/`Grep`/`Glob` | **never** |

`prompt.txt` is composed by `Build-PrereviewPrompt` from: the checklist sections whose path class appears among the
changed files (`code` = `android/**` production, `tests` = `*Test*` files, selftest fixtures and receipts, `prose` =
comments, KDoc, `docs/**`, `context/**`, `specs/**`, `scripts` = `scripts/**`, `.github/**`, `.claude/hooks/**`; the
card's own `specs/tasks/<id>.md`, `_local/**` and `.review/**` are ignored for the selection), the rubric, the card at
base, `acceptance.json`, the fence-hardened diff, the changed-file slice and the tree index. The R3 verdict-output
instruction is stripped; every data segment is wrapped in per-run nonce fences by the same helpers `review.ps1` uses,
so a "report no findings" line planted in the diff stays inside a fenced data segment. A card may pin the section list
with the optional front-matter key `review_lens`.

Guards before any worker starts: a secret scan with both the content patterns and the path patterns of
`scripts/check-secrets.ps1 -AsLibrary` over the changed-path inputs `diff.patch`, `files/**`, `card.md` and the path
list of `units.json` (`[PRE-SECRETS]`: the pack directory is deleted, the run stops). The scan does not read
`tree/**`: an entry there that is not in the diff is merge-base content already in the repository history, and a new
untracked file is in the diff and therefore scanned by path and, where the diff carries it, by content. A pack size
ceiling (`PrereviewMaxPackBytes`, the
message names the largest entries). `policy_hash` is the SHA-256 over `docs/PREREVIEW-CHECKLISTS.md` plus
`specs/prereview-record.schema.json` at the merge-base commit, so a wording change in either is a policy change by
construction. `risk_class` routes the discoverer model: a changed path under FrozenPaths or `PrereviewRiskyExtraPaths`
selects `PrereviewRiskyModel`, otherwise `PrereviewModel` (FrozenPaths is read at run time, never copied).

## 4. Worker command contract

A worker is one command started by the bounded runner (the RUNNER card; the same runner `review.ps1` uses):

- **Input**: the prompt on stdin (`PRE_FACTS/prompt.txt`), cwd = the pack directory, and the variables below. Workers
  never receive the worktree path.
- **Environment**: cleared, then an allowlist only: the Windows process essentials (`SystemRoot`, `windir`, `COMSPEC`,
  `PATHEXT`, `APPDATA`, `LOCALAPPDATA`, `ProgramData`, `TEMP`, `TMP`), `PATH`, `HOME`, `USERPROFILE`, proxy and CA
  variables (`HTTPS_PROXY`, `HTTP_PROXY`, `NO_PROXY`, `SSL_CERT_FILE`, `NODE_EXTRA_CA_CERTS`), `PRE_*`, and
  `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`. `ANTHROPIC_API_KEY`, `GH_TOKEN` and every other variable except
  `DEEPSEEK_API_KEY` (next sentence) are absent from every worker. `DEEPSEEK_API_KEY` is absent from the claude worker
  and from custom commands; how the built-in deepseek adapter obtains it is that adapter's contract
  (WORKERS-DEEPSEEK: read at call time, reaching that adapter only, never the claude worker).
- **Output**: exactly one envelope JSON `{schema_version, schema_revision, records[]}` written to `PRE_OUT` (custom
  commands) or extracted from the CLI wrapper or the HTTP response (built-in adapters); the adapter unpacks `records[]`
  to JSONL and **stamps provenance** (`snapshot_tree`, `worker_id`, `model_id`, `lens`, `schema_version`,
  `schema_revision`) from the environment and `facts.json`. A record that already carries provenance, an `id`, or a
  `missing` status is rejected (`[PRE-BAD-RECORD]`): models never author provenance.

| variable | meaning |
|---|---|
| `PRE_FACTS` | the pack directory |
| `PRE_OUT` | where a custom command writes its envelope |
| `PRE_MODEL`, `PRE_EFFORT` | the routed model and effort for this worker |
| `PRE_LENS` | the value the adapter stamps as `lens` (for the lens worker: the composed section list) |
| `PRE_TIMEOUT_SEC` | the per-worker ceiling (`PrereviewTimeoutSec`); at expiry the process tree is killed, exit 124 |
| `PRE_LIVE` | `1` only when a built-in adapter may go outbound (writer rule below) |

`PRE_LIVE` has one writer on the live path: `prereview.ps1 run`, on its non-`-SelfCheck` path, when neither `CI` nor
`GITHUB_ACTIONS` is set. No `-SelfCheck` and no selftest fixture sets it, with three registered in-process exceptions
inside adapter self-checks: two cases in WORKERS-CLAUDE (set only after asserting the fake `claude` first on a private
PATH, `CI=1` and the loopback `HTTPS_PROXY` record-and-refuse sink; cleared when the case ends) and one in
WORKERS-DEEPSEEK (one direct adapter call with the key removed and the replay listener running, proving the production
host stays fixed and the listener records zero requests). A built-in adapter requested without `PRE_LIVE=1` yields `[PRE-LIVE-REFUSED]`
and spawns nothing. Under `CI`/`GITHUB_ACTIONS` the run prints `[PRE-NO-NETWORK-IN-CI]` and does not spawn a built-in
adapter (discoverer `incomplete`, lens `skipped`); custom `Prereview*Command` values and fixture shims still run, so
every self-check passes under CI. Live model runs are never part of a DoD, `verify` or CI (L25).

**Built-in discoverer** (`PrereviewDiscoverCommand` = `''`): `claude` in print mode with safe and restricted mode,
tools limited to `Read`, `Grep`, `Glob`, no permission prompts, no session persistence, an inline settings object
equal to the project's `.claude/settings.json` `permissions.deny` list (secret-shaped paths), JSON output constrained
by the inline envelope projection
`scripts/fixtures/prereview/schema/worker-envelope.min.json`, model and effort from `PRE_MODEL`/`PRE_EFFORT`; the
executable is resolved to an absolute path before spawning. Auth is the subscription login state in the user profile,
so the worker runs without `ANTHROPIC_API_KEY`; `--bare` is not used because it skips that login state. The exact flag
set is pinned by the WORKERS-CLAUDE card's offline argv probe, not by this document.

**Built-in lens** (`PrereviewLensCommand` = `''`): an in-repo adapter run as a child process (timeout and `PRE_LIVE`
gating apply) that posts `prompt.txt` to the `api.deepseek.com` anthropic-compatible messages endpoint with `PRE_MODEL`
(`PrereviewLensModel` by default, `PrereviewLensDocModel` for documents over `PrereviewMaxFileBytes`), passes
`-Proxy $env:HTTPS_PROXY` explicitly, logs one ASCII line `lens_endpoint=production|override` before any call and
before reading the key, and parses one envelope JSON object out of the response text. `PRE_LENS_ENDPOINT` (the offline
replay listener) is honoured only while `PRE_LIVE` is unset; with `PRE_LIVE=1` the production host is fixed. The
endpoint has no structured-output guarantee, so a bad envelope is a lens `skipped` with `[PRE-BAD-RECORD]`, never a
blocker. A missing key yields `[PRE-WORKER-MISSING]`; the key value never appears in stdout, stderr or worker logs.

**Failure mapping** (runner outcomes to codes, mutually exclusive): nothing to parse, that is no output file, empty
output, or a CLI wrapper without a structured-output field: `[PRE-NO-OUTPUT]`; output produced but it is not a valid
envelope, or a record in it fails validation or names an unknown unit or local id: `[PRE-BAD-RECORD]`; timeout
`[PRE-TIMEOUT]`;
command or key not found `[PRE-WORKER-MISSING]`; built-in without `PRE_LIVE` `[PRE-LIVE-REFUSED]`. For the discoverer each of these
makes the run `incomplete`; for the lens each makes the worker `skipped` with that code as `skip_code`.
`PrereviewLensEnabled=false` is `[PRE-LENS-SKIPPED]`.

## 5. Record and coverage shapes (`schema_version` 1)

The frozen contract is `specs/prereview-record.schema.json` (`additionalProperties: false` everywhere; enum values are
lower_snake except the identifier enums `category` and `status_code`; `schema_revision` is bumped by any in-window
patch so a stale fixture fails explicitly). The model-facing projection passed to the discoverer is
`scripts/fixtures/prereview/schema/worker-envelope.min.json` (content fields only, at most 4096 bytes).

- **Envelope** (`worker_output`): `schema_version`, `schema_revision`, `records[]` of candidate and coverage records.
- **candidate**, model-authored: `local_id`, `kind` (`defect` | `question` | `suggestion`), `severity_guess`
  (`critical` | `high` | `medium` | `low` | null), `category` (C1, C2, C3, C4, C5, C7), `anchor` (`hunk` | `file` |
  `absent`), `file` (any path of the snapshot tree), `symbol` (non-empty, or null with `line_start` required),
  `line_start`, `line_end`, `unit_ids[]` (may be empty; every entry must exist in `units.json`), `trigger`, `expected`,
  `actual`, `impact`, `contract_ref` (`lesson:L<n>` | `rubric:<n>` | `acceptance:A<n>` or `R<n>` | `frozen:<path>` |
  `none`), `evidence_refs[]`, `evidence_needed[]`, `introduced_or_worsened` (`introduced` | `worsened` |
  `pre_existing`), `suggested_fix_direction`. Adapter-stamped: the provenance of section 4 (`snapshot_tree`,
  `worker_id`, `model_id`, `lens`, `schema_version`, `schema_revision`). Core-added: `id` (`C-<n>`), `fingerprint`,
  `root_group`, `related_to[]`. The schema closes the model-authored object, so a stamped or core field inside a
  worker's `records[]` is itself a violation.
- **coverage**, one per changed unit: `unit_id` (must exist in `units.json`), `categories_checked[]` (subset of C1,
  C2, C3, C4, C5, C7), `status` (`checked_no_finding` | `finding` | `not_applicable` | `blocked`),
  `candidate_local_ids[]` (each must name a record of the same batch), `missing_context[]` (a `blocked` record names
  the location it needed). The status `missing` is state-only: synthesised by the core, rejected from a worker.
- **facts** and **units**: the `facts.json` and `units.json` shapes of section 3, validated like records.
- **Forbidden by schema** (reject samples in `scripts/fixtures/prereview/schema/`): any field named `verdict`; any
  status value `pass` or `block`; a worker-emitted `id`, `missing` status or provenance field; unknown fields anywhere.

Core normalisation (the RECORDS card): ids `C-<n>` are minted monotonically within one state's life and never reused;
exact duplicates (same batch and same `file|symbol|category|contract_ref|expected|actual` after NFC and whitespace
normalisation, the key RECORDS A4 defines; it carries no line or anchor, so two findings that state the same
`expected`/`actual` about the same symbol merge, a locality decision that card owns) merge into one state candidate
that names both contributing workers (the adapter stamps a single
`worker_id` per record, which is why merging is a core step and the merged shape lives in the state, not in a worker
record); near duplicates are kept and share one `root_group`; `fingerprint` = `file|category|symbol|contract_ref` is a grouping hint, never identity; a `missing`
coverage row is synthesised for every unit whose discoverer coverage lacks C1, C2 or C3.

The 1a state (`state_version` 1, `state.schema.json` in the state module's fixture folder, never frozen): `task_id`,
`snapshot_tree`, `head_sha`, `base_oid`, `base_mode`, `merge_base`, `policy_hash`, `rubric_sha`, `workers[]` (`id`,
`model`, `effort`, `lens`, `required`, `status` in `complete` | `incomplete` | `skipped`, `skip_code`, `duration_s`,
`exit_code`, `usage`), `units[]`, `candidates[]`, `coverage[]` (worker statuses plus `missing`), `disputes[]` (written
empty by `run`, appended by `link-r3`), `stop_reason`; the exact shape is the STATE-1A card's, and any field that
exists only to serve the 1b gate is outside this document. The 1a `packet.md` lists candidates, coverage and `missing`
units and carries no gate semantics.

## 6. Finding taxonomy C1..C7 (repo-specific)

| category | meaning | lessons |
|---|---|---|
| C1 | written guarantee exceeds evidence: comment, KDoc, test name, card invariant or acceptance row wider than the code or the assertion; a universal claim contradicted by an instance elsewhere in the tree; a sibling-clause contradiction | L309, L317, L321, L224 |
| C2 | real production defect: fail-open branch, overflow, data loss, missing guard a red test can show, a guard on one entry point while another entry in the tree lacks it | L228 |
| C3 | weak evidence: assertion face wider than the contract, expected value copied from the implementation, compile-kill mutant, missing negative case in the sibling test file, a receipt not matching what it describes | L165, L225, L282 |
| C4 | base or process drift: branch behind base, evidence pinned to another tree, PR-body-only evidence | L148, L310, L227 |
| C5 | scope or placement: `allow_paths`, `non_goals`, naming, file placement, card front-matter drift | |
| C6 | repeated dispute; **derived from dispute records only**, never emitted by a worker | |
| C7 | other, named by the worker: spec gap, licence, layout, logging, single source, undecidable rule | |

Coverage rule: the discoverer returns one coverage record per unit in `units.json` with `categories_checked` covering
C1, C2 and C3 (the core synthesises `missing` otherwise). C1 to C3 need the tree, which the discoverer has. The
per-check wording lives in `docs/PREREVIEW-CHECKLISTS.md`.

## 7. Reading the packet

`packet.md` is rendered from `state.json`: the workers, every candidate with its `C-<n>` id, the coverage rows and
the `missing` units; it carries no gate semantics: the schema forbids a field named `verdict` and the status values
`pass` and `block`, and no packet field is a ruling (any word-level check on the rendered packet is the STATE-1A
renderer's guard, not a guarantee over what a model writes). Read it as findings to check, not as a ruling: a candidate is a claim from one model
over a snapshot of your tree; confirm it against the tree (or against a red test) before fixing, and let the R3 block
reasons, linked in section 8, be the measure of what the packet was worth. Fixes stay in the worktree; a second `run`
after a fix batch is a full run over the new snapshot and mints new candidates (no carry-forward in 1a). Evidence that
must reach the codex reviewer still goes into the diff (L227): the packet is never part of what the scaffold gives R3.

## 8. `link-r3` and `recall`

`prereview.ps1 link-r3 -TaskId <id> -Round <n>` reads the current verdict file `.review/<branch>.json` of the task's
worktree (1a keeps no per-round archive, hence "right after the block"), lists `reasons[i]` with their SHA-256, and
records one dispute per reason through the state writer: `candidate_id` (null for `new`), `r3_round`, `r3_sha` (the
verdict's sha), `reason_index`, `reason_sha256`, `relation` in `same` | `related` | `new`, `by`, `at`. An unknown
candidate id or an out-of-range reason index is rejected without writing; linking the same round twice replaces that
round's disputes instead of duplicating them. The relations (the enum of STATE-1A) mean: `same` = the reason is the
candidate; `related` = the same defect class at the same location, differently stated; `new` = no candidate covers
it. The author links, never the model.

`prereview.ps1 recall -Out <path>` reads every state under the common-dir plane and computes, per card, linked reasons
with relation `same` or `related` over all linked reasons; pools over cards; reports per worker (a reason linked to
candidates from both workers counts for both; `unique` marks reasons only one worker had); writes one JSON snapshot to
`-Out`. By convention snapshots go to `_local/review-metrics/recall-<date>.json` (gitignored); no self-check ever
writes `_local/`.

## 9. The 1a checkpoint rule

At least five product cards run the packet before their first ship and `link-r3` after every block. Pooled recall of
at least 50% (relation `same` or `related`) opens Phase 1b; below that the packet stays advisory (kept, measured
further) or is retired, and no further investment is made: this is the kill criterion. Recorded alongside, not part
of the predicate: packet wall time P50 and the `[PRE-BAD-RECORD]` count on live runs. The baseline the checkpoint is
measured against (143 archived cards: first-round pass 32.9%, 3.9 blocks per card on average) is stated in the
"PR review v2" section of `docs/TASK-BOARD.md`.

## 10. First live run checklist

Live behaviour is card evidence, never a DoD, `verify` or CI step. The RUN and LINK-RECALL cards record the results of
the first live runs in their bodies:

1. `run` with `PrereviewEnabled=false` prints `[PRE-RUN-DISABLED]` and spawns nothing.
2. The discoverer authenticates through the subscription login without `ANTHROPIC_API_KEY` in its environment.
3. A read outside the pack is refused (RUN card evidence).
4. With `DEEPSEEK_API_KEY` absent the lens is `skipped` with `[PRE-WORKER-MISSING]` and the packet is still ready;
   with the key present the lens completes and `lens_endpoint=production` was logged.
5. `git status --porcelain --ignored` of the worktree is byte-identical before and after the run.
6. `state.json` validates and the pack directory is gone; when the last `run` was on the final tree (no edit after it,
   or a re-run after the fixes) `HEAD^{tree}` after the ship commit equals `state.snapshot_tree`.
7. Output token counts (`usage`) and wall time (`duration_s`) per worker are recorded in the state, and the packet
   wall time in the card body.
8. `link-r3` after the card's first R3 block links every reason; `recall -Out` reproduces the per-card number by hand.

## Status codes

Codes are matched by ASCII code, never by prose (L165). The enum lives in `specs/prereview-record.schema.json`
(`$defs/status_code`); `scripts/check-prereview-schema.ps1 -Anchors docs/PREREVIEW-PROTOCOL.md` checks that the table
below (the first table under this heading; the code is the first `[PRE-…]` token of column 1) lists every enum member
exactly once and nothing outside the enum. Owners name the card or component that emits the code.

| code | owner | phase | meaning | counts as |
|---|---|---|---|---|
| `[PRE-NO-MERGE-BASE]` | FACTPACK | 1a | base and HEAD share no merge-base | stop before any file is written; no worker |
| `[PRE-SECRETS]` | FACTPACK-SLICES | 1a | secret scan hit (content or path pattern) over `diff.patch`, `files/**`, `card.md`, `units.json` paths | stop; pack deleted; no worker |
| `[PRE-NO-NETWORK-IN-CI]` | RUN | 1a | `CI` or `GITHUB_ACTIONS` set and a built-in adapter would be needed | built-in workers refused (discoverer `incomplete`, lens `skipped`); custom commands and shims proceed |
| `[PRE-LIVE-REFUSED]` | runner / adapters | 1a | built-in command requested without `PRE_LIVE=1` | discoverer `incomplete`; lens `skipped` |
| `[PRE-WORKER-MISSING]` | runner / adapters | 1a | configured CLI or key not found | discoverer `incomplete`; lens `skipped` |
| `[PRE-NO-OUTPUT]` | runner / adapters | 1a | nothing to parse: no output file, empty output, or a CLI wrapper without a structured-output field | discoverer `incomplete`; lens `skipped` |
| `[PRE-TIMEOUT]` | runner | 1a | process tree killed at `PRE_TIMEOUT_SEC`, exit 124 | discoverer `incomplete`; lens `skipped` |
| `[PRE-BAD-RECORD]` | RECORDS | 1a | output produced but not a valid envelope, or a record in it failed validation or references an unknown unit or local id | discoverer `incomplete`; lens `skipped`, its records discarded whole |
| `[PRE-LENS-SKIPPED]` | WORKERS | 1a | `PrereviewLensEnabled=false` | lens `skipped`; discoverer runs |
| `[PRE-PACKET-READY]` | RUN | 1a | state and packet written, pack removed | informational |
| `[PRE-RUN-DISABLED]` | RUN | 1a | `PrereviewEnabled=false`; nothing built, nothing spawned | stop (kill switch) |
| `[PRE-BUDGET-AFTER-FIX]` | RUN (1b BATCH) | 1b | `review.ps1 -SizeOnly -Tree` over budget on the new snapshot | stop; split before any push |
| `[PRE-BATCH-CAP]` | RUN (1b BATCH) | 1b | the (cap+1)-th run since the last merge | stop; user |
| `[PRE-MISSING]` | ship gate | 1b | no state for the task | ship refused |
| `[PRE-STALE]` | ship gate | 1b | tree, policy or `base_mode` mismatch | ship refused |
| `[PRE-OPEN]` | ship gate | 1b | a candidate without disposition | ship refused |
| `[PRE-INCOMPLETE]` | ship gate | 1b | `review_status` incomplete (worker, units, needs_human) | ship refused |
| `[PRE-SKIPPED]` | ship gate | 1b | `-SkipPrereview` | ship continues; ledger |
| `[PRE-DISABLED]` | ship gate | 1b | `PrereviewGateEnforced=false` | ship continues; ledger |
| `[PRE-GATE-PASS]` | ship gate | 1b | fresh, dispositioned, covered | ship continues; ledger |

## Phase 1b (pointer only)

The 1b rows above belong to dispositions, the batch loop, the ship gate leg and the metrics. Their prose (disposition
states and evidence, batch semantics and caps, gate precedence, dispute escalation, the finding-shaped-line rule, the
metrics rule) is written by the 1b LOOP-DOCS card after the checkpoint of section 9 opens 1b; until then the table
rows are the only 1b content of this document, `PrereviewGateEnforced` stays `false`, and `ship` behaves exactly as
today.
