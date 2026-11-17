---
id: T0-CI-SELFTEST-ADOPTION-REPAIR
title: Repair the four scaffold-selftest regressions the upstream adoption (#297) left on the CI matrix
status: merged
branch: T0-CI-SELFTEST-ADOPTION-REPAIR
worktree: C:\wt\T0-CI-SELFTEST-ADOPTION-REPAIR
allow_paths:
  - scripts/selftest.ps1
  - scripts/_guard.ps1
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Only '8,14,15,17t,17ac' -IncludeMeta
dod_exit: 0
review_gate: codex {verdict:pass}
acceptance:
  - "A1 Gate 15b's e2e fixture ships to a local merge commit without running the product's Gradle license scan: the check-licenses.ps1 stub is in the fixture's base commit, and a fixture whose base commit lacks it is reported by name. [dod arm 15]"
  - "A2 Gate 15e reads the product verify (:core:e2eTest + [GATE2-MISSING] + [GATE2-NOT-RUN] in scripts/verify.ps1) and proves its degraded path on a bare copy of the script alone (non-zero, [GATE2-MISSING], [GATE2-NOT-RUN]); it never executes scripts/verify.ps1 in $RepoRoot. A verify without that contract keeps the upstream dry-run arm. [dod arm 15]"
  - "A3 Gate 14m@header-form reports no header outside the verdict: the post-init 14e block is written in the continuation form and Get-ScaffoldContinuedSubGateLabel declares 14e continued once, so the sub-gate label count stays 151. [dod arm 14]"
  - "A4 Gate 17ac(o)1 announces [SELFTEST-POSTINIT-MUT-TSV-SKIP] on an initialized project (no CLAUDE.template.md) whose specs/mutations/T63-TD119-MUTATION-RUNNER-results.tsv is absent, and 17ac(o)2 still runs its hermetic RESET fixture; a meta repository keeps the presence assertion. [dod arm 17ac]"
  - "A5 Gate 8.2j's children -Only 14 -IncludeMeta and -Only 15 -IncludeMeta exit 0 with git hidden and print every declared announcement, so gate 8 prints [ENV-SKIP-EXERCISED]. [dod arm 8]"
budget: 120
tier: S
forbid: [Weakening or deleting any gate assertion that is green on the nightly today; changing scripts/verify.ps1, scripts/check-licenses.ps1, the CI workflows, or any product code; adding fabricated evidence files under specs/mutations/]
non_goals: [Reconciling the diverged local master with origin/master; re-porting the upstream selftest; making the scaffold-selftest runner provision a JDK/Android SDK; fixing the upstream repository itself (reported via scaffold-sync instead)]
diagnosis:
  root_cause: PR #297 replaced the downstream selftest with the upstream one and dropped three downstream adaptations that made it independent of the runner toolchain and of meta-repository files (15b's check-licenses stub; 15e's bare-project fixture; 17ac(o)①'s tracked TSV is stripped by init-scaffold), and its post-init 14e block used a header form 14m's projection cannot see. Push runs red on gate 15 (34522511015); the nightly, the only face that passes -IncludeMeta, also reds gates 8, 14 and 17t+17ac (34629163877, 34706654845, 34771405641).
  same_class: the nightly run 34771405641 is the census - every other gate in all five shards on both OS is green, so no other adopted sub-gate executes the product toolchain in $RepoRoot or asserts a meta-only file. 15x already uses isolated fixtures for the same verify; 17cc's scanner fixture is hermetic.
dod_assert: the scoped run of the exact failing shards (light's 8 and 14, e2e's 15, seed-b's 17t,17ac) on the nightly face exits 0 with [GATE-RESULT] PASS for 8, 14, 15 and 17t+17ac; Tier S proof is one full -Parallel run over the frozen candidate; the e2e shard is additionally dispatched on the real runner for the PR head.
hygiene: single-line deletion mutants for each new guard (stub line, stub-in-baseline guard, bare-fixture assertion, continuation declaration, post-init condition) must each turn the scoped run red; record the batch in the card body.
doc_sync: status -> merged; CLAUDE.md current-stage entry; report the two upstream defects (17ac(o)① post-init, 15e $RepoRoot assumption) through scripts/scaffold-sync.ps1 report and record them in docs/SCAFFOLD-SYNC.md; archive after merge.
---

# T0-CI-SELFTEST-ADOPTION-REPAIR

## Deliverable

`scaffold-selftest.yml` green again on both faces: the push face (`-Only` shards without meta) and the
nightly face (`schedule` passes `-IncludeMeta`). Since `d991cc92` (#297) the push face reds on the `e2e`
shard and the nightly reds on `light`, `e2e` and `seed-b` on both OS. All four defects live in the adopted
`scripts/selftest.ps1` (one continuation declaration in `scripts/_guard.ps1`); nothing in the product or
the workflows changes.

## Evidence (RED, measured before any edit, on the origin/master tree `39c072e2`)

| Shard | Gate | Failure | Where it reproduces |
|---|---|---|---|
| e2e (push + nightly) | 15b | `task.ps1 -Phase ship -Local` exits 1: the saga stops at `license-gate` because the fixture copies `android/` and `check-licenses.ps1` resolves the four Gradle graphs for real (`[GRADLE-METADATA]` on every androidx POM in the copy; `GRADLE-CACHE-OFFLINE` on a runner with no cache) | CI both OS; locally (`-Only 15`, 326 s) |
| e2e (push + nightly) | 15e | `scripts/verify.ps1` dry-run in `$RepoRoot` exits 1: this verify runs Gradle `:core:check` + `:core:e2eTest`, which no scaffold-selftest runner can build | CI both OS only (locally the toolchain is present, so the arm is green and slow) |
| light (nightly) | 14m@header-form | `[SUBGATE-FORM] scripts/selftest.ps1:6954 declares sub-gate '14e' in a form the projection cannot see` | CI both OS; locally (`-Only 14 -IncludeMeta`) |
| light (nightly) | 8.2j | children `-Only 14` and `-Only 15` with git hidden exit 1 (the two rows above) | CI both OS; locally with git hidden |
| seed-b (nightly) | 17ac(o)① | `仓内 tracked 结果 TSV 缺失`: `specs/mutations/T63-TD119-MUTATION-RUNNER-results.tsv` is upstream evidence that `init-scaffold.ps1` keeps out of every downstream | CI both OS; locally (`-Only 17t,17ac -IncludeMeta`, 270 s) |

## Acceptance (DoD = command + exit code + assertion; paired with the closed `acceptance:` list)

```powershell
pwsh -NoProfile -File scripts/selftest.ps1 -Only '8,14,15,17t,17ac' -IncludeMeta
```

- Expected exit code: 0
- Assertion: `[GATE-RESULT] 8 PASS`, `[GATE-RESULT] 14 PASS`, `[GATE-RESULT] 15 PASS`, `[GATE-RESULT] 17t+17ac PASS`
  and `[GATE-RESULT-SUMMARY] failed=none`; the run prints `[ENV-SKIP-EXERCISED]`,
  `14m header form OK`, `动态 E2E OK（ship -Local）`, `15e 真实 verify 裸项目 OK` and
  `[SELFTEST-POSTINIT-MUT-TSV-SKIP]`.
- Tier S proof (ADR 0016): one full `-Parallel` run green over the frozen candidate, recorded with
  `git rev-parse HEAD` + `git status --porcelain` at launch.
- Runner proof: `gh workflow run scaffold-selftest.yml --ref T0-CI-SELFTEST-ADOPTION-REPAIR -f shard=e2e`
  green on both OS for the PR head (the dispatch face carries no `-IncludeMeta`, so it proves the two
  environment-dependent 15 arms, not the meta arms; those are proven locally and by the first nightly after merge).

## Delivery evidence

- [PR #299](https://github.com/Asun28/MyInspection/pull/299) merged as `8d673edb` (squash of `77a639e8`; base `30db78c0`).
  R3 round 1 at `e3b09f75`: `spec=pass`, `standards=block` (one finding: the `Get-ScaffoldContinuedSubGateLabel`
  description named only 9g/15f/15r) - fixed in `77a639e8`, docstring only. R3 round 2 at `77a639e8`: `pass` on
  both axes (gpt-5.6-sol). `[CI-GATE-PASS]` bound PR head `77a639e8` to ci.yml run `34796936991/1` and base `30db78c0`.
- RED (official, unpatched worktree): `failed=8,14,17t+17ac`. GREEN: the dod_command exit 0 with `8 PASS 176.3s`,
  `14 PASS 192.3s`, `15 PASS 640.2s`, `17t+17ac PASS 375.6s`, `[META-SUMMARY] declared=22 ran=22 skipped=0`.
- R4: five single-line deletion mutants, 5/5 killed (M1 15b stub -> guard reds; M2 `'14e' = 1` -> unprojected header;
  M3 continuation header -> count mismatch; M4 post-init condition -> 17ac(o)1 reds; M5 15e Copy-Item -> sentinel
  assertion reds). M4's first row was BAD-EVIDENCE from a wrong regex, corrected and re-run; M2 re-run after the
  round-1 docstring commit. Both targets hash back to the commit after every mutant. Rows in the PR body.
- Tier S: `selftest.ps1 -Parallel -IncludeMeta` green over `e3b09f75` (1033 s wall) and again over `77a639e8`
  (979 s wall), HEAD and porcelain identical at launch and end each time.
- Runner: dispatch runs 34792758080 (`e3b09f75`) and 34796934215 (`77a639e8`), e2e shard `15 PASS` on ubuntu and
  windows. The push-face matrix on the merge commit is run 34799076882; the meta arms are re-proven by the first
  nightly after merge.
- Not evidence: gate 15 reported PASS inside the combined RED run because `$fail` is a monotonic latch and 15b's
  body is `-not $fail`-guarded; the first ship attempt stopped at the license gate because Gradle's 30-day cache
  cleanup had removed the POM files the scanner reads (restored by an online prewarm, one `--configuration` per
  invocation; no repository change). Both recorded as TD176/TD177.
