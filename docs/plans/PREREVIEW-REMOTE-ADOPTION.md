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

## Durable policy source evidence

The original local policy documents are preserved byte-for-byte as historical data under `scripts/fixtures/prereview/policy-source/raw/`: `PREREVIEW-PROTOCOL.txt` and `PREREVIEW-CHECKLISTS.txt`. Their original paths, source commit/blob identities, raw SHA-256 values and lengths are recorded in the sibling `scripts/fixtures/prereview/policy-source/manifest.json`. The old wording and citations are provenance, not active instructions; the source bytes are never corrected to match current rules.

The same manifest declares every counted replacement from those originals to the approved remote-adoption bodies. `pwsh -NoProfile -File scripts/fixtures/prereview/policy-source/verify.ps1` verifies source identity and the complete reconstructed body digests using only committed files. Once POLICY is present, add `-CandidateRoot .` to compare both actual document bodies before their receipt markers. `selfcheck.ps1` checks positive reconstruction and four named negative probes on temporary copies, without mutating the committed inputs.

This evidence delivery introduces no worker, runner, active policy document or network call. The POLICY repair points to these committed artifacts after this prerequisite is remotely merged; its two earlier R3 blocks remain in the delivery history and further review requires separate authorization.
## CHECK acceptance receipt — 2026-09-18

Fresh checks ran on commit `4ed27566099fdc540cfb6e2e808a1fe8ee805fdb`, tree `566bfeab6e1e20fc9523ba9193996c76c3122037`. Before/after identities matched; the original RED, first R3 block and T35 receipt were retained. Results bind the five input blobs below; changing any input requires fresh evidence.
Inputs are relative to `scripts/fixtures/prereview/policy-source/`:
| Input | Bytes | Git blob | SHA-256 |
| --- | ---: | --- | --- |
| `manifest.json` | 7102 | `e9abb611eded4f230d9f88d93ca89b1ed62fa579` | `79EA6AB2F52FCE4BA000A78F13391EC50F26F91739153007B742C2CB02A5022F` |
| `verify.ps1` | 3057 | `c8b9dfdadf46dd3d7f20506526ea7e5235719884` | `7325CBA264C70FF7C1D4D227D02589E4789C1DF7D38D91FC73040EE78C523C61` |
| `selfcheck.ps1` | 3765 | `32469874fb7ce4e2c6847ed3e6b43d0c5fd3b7d2` | `28A70D83392D2D20D6C555B3A6C8A876B3467E1ACA90EC561DB8F764DB199BF7` |
| `raw/PREREVIEW-PROTOCOL.txt` | 29016 | `0bbfb4e51b08624c23f38a8e388aad485e05b1b8` | `1CBF12A29E6CCC67BCFAEEEAE93C0CE65E00D151CCDCC67F885421BE5F744457` |
| `raw/PREREVIEW-CHECKLISTS.txt` | 12238 | `836a99e8a4b756fb1f78e92159e86eb4e48a06c3` | `C0C8ADACC6F77EF8D067F2987E69398E1ABDEF40E10C46B1AD32BF511B6181F1` |

Reproduce from the repository root (the hash must equal the base-card A1 constant):
- `(Get-FileHash -LiteralPath scripts/fixtures/prereview/policy-source/manifest.json -Algorithm SHA256).Hash` → `79EA6AB2F52FCE4BA000A78F13391EC50F26F91739153007B742C2CB02A5022F`. The capture wrapper enforced this equality before continuing.
- `pwsh -NoProfile -File scripts/fixtures/prereview/policy-source/verify.ps1`
- `pwsh -NoProfile -File scripts/fixtures/prereview/policy-source/selfcheck.ps1`

| Command | UTC start → end, 2026-09-18 | Native exit | Raw stdout SHA-256 |
| --- | --- | ---: | --- |
| `manifest-hash` | 09:24:38.6769579 → 09:24:39.4293281 | 0 | `74DD545C0F131C1362C305B519C34DB85275172053147ED2E9B618AE3E773A99` |
| `default-verifier` | 09:24:39.4581860 → 09:24:40.4155579 | 0 | `9D21ADE7D52A535B3465E76ECD9ABCB74446C1FC3D577DE58B89757DDBF6687C` |
| `selfcheck` | 09:24:40.4193783 → 09:24:48.1435291 | 0 | `B450BCCC2F73920A22493C540388024D59B83C825803A6EB28EE572176FD627E` |

Complete stdout follows in table order; the hash line is labelled by the capture wrapper. Displayed line endings are normalized to LF; the hashes above bind the original stream bytes.
```text
[POLICY-SOURCE-MANIFEST-HASH] SHA256=79EA6AB2F52FCE4BA000A78F13391EC50F26F91739153007B742C2CB02A5022F
[POLICY-SOURCE-FILE-PASS] docs/PREREVIEW-PROTOCOL.md source=1cbf12a29e6ccc67bcfaeeeae93c0ce65e00d151ccdcc67f885421be5f744457 adopted-body=5948ca1be9474a3fdedb4c2b5aea1380aae86de19a3799ecec98f31df0d0e2e8
[POLICY-SOURCE-FILE-PASS] docs/PREREVIEW-CHECKLISTS.md source=c0c8adacc6f77ef8d067f2987e69398e1abdef40e10c46b1ad32bf511b6181f1 adopted-body=1a46b3031d6781769280838decf1640100f8b5f7254eb719b89742a9a7e7e84c
[POLICY-SOURCE-EVIDENCE-PASS] two approved sources and complete replay verified
[POLICY-SOURCE-FILE-PASS] docs/PREREVIEW-PROTOCOL.md source=1cbf12a29e6ccc67bcfaeeeae93c0ce65e00d151ccdcc67f885421be5f744457 adopted-body=5948ca1be9474a3fdedb4c2b5aea1380aae86de19a3799ecec98f31df0d0e2e8
[POLICY-SOURCE-FILE-PASS] docs/PREREVIEW-CHECKLISTS.md source=c0c8adacc6f77ef8d067f2987e69398e1abdef40e10c46b1ad32bf511b6181f1 adopted-body=1a46b3031d6781769280838decf1640100f8b5f7254eb719b89742a9a7e7e84c
[POLICY-SOURCE-EVIDENCE-PASS] two approved sources and complete replay verified
[POLICY-SOURCE-NEGATIVE-PASS] source-byte
[POLICY-SOURCE-NEGATIVE-PASS] source-byte-checklists
[POLICY-SOURCE-NEGATIVE-PASS] replacement-count
[POLICY-SOURCE-NEGATIVE-PASS] replayed-digest
[POLICY-SOURCE-FILE-PASS] docs/PREREVIEW-PROTOCOL.md source=1cbf12a29e6ccc67bcfaeeeae93c0ce65e00d151ccdcc67f885421be5f744457 adopted-body=5948ca1be9474a3fdedb4c2b5aea1380aae86de19a3799ecec98f31df0d0e2e8
[POLICY-SOURCE-FILE-PASS] docs/PREREVIEW-CHECKLISTS.md source=c0c8adacc6f77ef8d067f2987e69398e1abdef40e10c46b1ad32bf511b6181f1 adopted-body=1a46b3031d6781769280838decf1640100f8b5f7254eb719b89742a9a7e7e84c
[POLICY-SOURCE-EVIDENCE-PASS] two approved sources and complete replay verified
[POLICY-SOURCE-NEGATIVE-PASS] candidate-body
[POLICY-SOURCE-SELFCHECK-PASS] original replay, candidate comparison and five negative probes across four semantic classes
```

All three stderr streams were empty (SHA-256 `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`). The unchanged selfcheck exercised default replay and exact candidate comparison, plus five named probes across four semantic classes; it required each negative child to return nonzero with the intended guard reason. The exported native exit above is the selfcheck process exit, not fabricated per-probe exit values.
Retained raw proof: `check-r1-fresh-evidence-v1`, 25-file manifest SHA-256 `07015CBCC89ECED3279E86F73A216E5323E11C6506941D030FC16632744F2B67`, containing the invoked capture scripts, original streams, native UTC/exit receipts and complete input copies. The visible results above do not require the reviewer to execute commands or access that local archive.
