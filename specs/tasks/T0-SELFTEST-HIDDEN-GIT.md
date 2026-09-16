---
id: T0-SELFTEST-HIDDEN-GIT
title: Keep gate 15 product verify fixtures runnable when git and its PATH directories are hidden
status: in-progress
branch: T0-SELFTEST-HIDDEN-GIT
worktree: C:\wt\T0-SELFTEST-HIDDEN-GIT
allow_paths:
  - scripts/selftest.ps1
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Only '8,15' -IncludeMeta
dod_exit: 0
review_gate: codex {verdict:pass}
acceptance:
  - "A1 On Ubuntu, hiding git through the same PATH-directory filter as 8.2j leaves git unavailable and selftest -Only 15 -IncludeMeta exits 0 with its declared skip announcements. [gate 8.2j]"
  - "A2 Gate 15f still executes its Python and frontend controls; 15x still proves the real product verify missing, green and red outcomes with git hidden and present. [gates 15f and 15x]"
  - "A3 Normal Windows and Ubuntu behavior is retained; fixture PATH additions are restored in finally, and gate 8.2j reports ENV-SKIP-EXERCISED with all reachable meta checks run. [gates 8 and 15]"
budget: 100
tier: S
forbid: [Weakening or removing assertions; changing production verify, workflows, configuration, or product code; restoring git to the hidden PATH]
non_goals: [Reconciling local master with origin; repairing the independent PSGallery provisioning outage; redesigning the environmental PATH filter]
diagnosis:
  root_cause: On origin f6fdaf6a, 8.2j strips the git directories /usr/bin and /bin on Ubuntu, also hiding sh. Product verify calls bare sh for Gradle but Set-ProjectGate2Stub15 provides only the gradlew stub. Windows does not share that dependency.
  same_class: Audited 15e, 15f(a,b,c), 15x and their finally blocks; shell-script stubs already use absolute /bin/sh shebangs, and 15f(c) intentionally restores /usr/bin and /bin in its child-only uv-free PATH.
dod_assert: gates 8 and 15 PASS with IncludeMeta; Ubuntu standalone hidden-git reproduction exits 0; product verification assertions remain active; full Tier S acceptance over the frozen candidate passes.
hygiene: Remove each fixture shell PATH addition on a disposable candidate copy and confirm the standalone Ubuntu hidden-git run fails at 15f(a) or 15x(b), then confirm the original file hash is unchanged.
doc_sync: Record verification and remaining CI provisioning risk in this card at closure; leave unrelated local reconcile notes untouched.
---

# T0-SELFTEST-HIDDEN-GIT

Origin baseline: f6fdaf6a559e68e6e715756ee904fdbfbec25ad3. The request calls the failures #216/#217;
the fetched run metadata shows #216 was a successful push and #217/#218 were failing scheduled runs.
Ubuntu #217 (34885588013) failed 8.2j; its Windows light leg independently failed PSGallery provisioning.
The original D:/Projects/MyInspection checkout is divergent and dirty and remains untouched.

Implementation: supply a fixture-owned POSIX sh forwarder, expose it only during the 15f and product
15x controls, and restore PATH in finally. Extend 8.2j to require the non-git 15f control still executes.
No new dependency or production verification behavior is introduced. Expected diff below 100 lines.

RED: pristine Windows hidden-git run exits 0; pristine Ubuntu hidden-git run exits 1 at 15f(a) and
15x(b). Both use the existing Get-ScaffoldPathWithoutDirs isolation and -Only 15 -IncludeMeta.

## Verification and integration handoff (2026-09-16)

Candidate: 2c8739c7d7d54224fa8645347e7b5dab73906308. Source SHA256:
3E8D8B9908F2EB15C0A472CF6C24069E9B61981C37C3B3847017D39DA1F4099F.

- RED was repeated after adding the 8.2j regression assertions and before the fixture repair.
- Ubuntu hidden-git control: exit 0; git and sh absent before launch, 15f/15x still execute.
- Windows full Tier S proof: `-Parallel -IncludeMeta`, exit 0, all five shards/all 17 gates pass,
  26 meta sites run and zero skipped, wall 1186.6s.
- Ubuntu present-git DoD: `-Only '8,15' -IncludeMeta`, exit 0, 12/12 meta sites run,
  gate 8 PASS 253.0s and gate 15 PASS 599.1s. 8.2j exercises five hidden-tool branches on both OSes.
- Two isolated Linux mutants are killed: removing the 15f(a) ShellBin hookup fails 15f(a);
  removing the 15x PATH addition fails 15x(b). Each restores PATH and original source hash.
- Product verify (Android core + Golden Evidence E2E), licenses, secrets, card, syntax and exact-tip
  scope checks pass. Diff budget is 23/100; the advisory meter trips at zero under existing config,
  not an over-budget finding. No split is warranted for these two adjacent fixture consumers.
- Base-pinned R3 round 1: gpt-5.6-sol, candidate above, run_status success, spec pass and standards
  pass, no findings. Its additional sandbox rerun passes 8.2j/15f/15x but gate 15i is red because
  Git sh.exe cannot create its signal pipe (Win32 error 5), aborting local-remote refresh before
  the intended push failure. This unchanged 15i passes in the full Windows and Ubuntu runs above;
  the reviewer rerun is not claimed as a green DoD.

Logs and the machine verdict remain in the candidate's `_local/` and `.review/`; a portable patch,
this card and the verification/evidence bundle are delivered in the requesting task's outputs.
No push, PR or merge was performed. Keep status in-progress pending origin reconciliation and
post-merge nightly observation. The original dirty/divergent checkout and its handoff remain intact.
The separate #217 Windows PSGallery provisioning failure is outside this code repair.
