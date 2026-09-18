# Round45 source approval and DoD review receipt

This is the actual root approval and first full DoD run for this whole-card candidate. The run precedes this receipt; final-tree DoD is separately required and is not claimed here.

## Original independent root approval (complete JSON)

```json
{
  "schemaVersion": 1,
  "task": "T0-REMOTE-ROUND45-CONTRACT-EVIDENCE",
  "repository": "D:/Projects/MyInspection",
  "ref": "refs/heads/master",
  "path": "specs/tasks/T0-REMOTE-ROUND45-CONTRACT-EVIDENCE.md",
  "commit": "1fa7f3095f9daf40fb739c45a996c6795e96d9ee",
  "blob": "500b8ca838fceff26996c42ce8591346ea50286f",
  "sha256": "E13D176F64DE72E4974670CF28B45E4A5EE39B6CF8BAD36353F6106B3A47F426",
  "allow_paths": [
    "docs/evidence/round45-approval-and-dod-receipt.md",
    "docs/evidence/round45-contracts/T1-APP-STORAGE-ANDROID.registered.txt",
    "docs/evidence/round45-contracts/T3-PDF-DEVICE-FIXTURE.registered.txt",
    "docs/evidence/round45-contracts/T3-PDF-MEASUREMENT-BINDING.registered.txt",
    "docs/evidence/round45-contracts/T3-PDF-TEXT-METRICS-OPS.registered.txt",
    "docs/evidence/round45-contracts/manifest.json",
    "specs/tasks/T0-REMOTE-ROUND45-CONTRACT-EVIDENCE.md"
  ],
  "rootAuthorization": "D:/Projects/MyInspection/_local/rotating-card-orchestrator/round45-source-block2-root-grant.json",
  "approvedUtc": "2026-09-18T10:59:13.467191+00:00",
  "base": "c0afac77175786b55c61c6ebb0292a64a33431a8",
  "sourceAudit": "D:/Projects/MyInspection/_local/rotating-card-orchestrator/round45-source-root-source-audit.json",
  "sourceAuditSHA256": "CDD621DFBF2B3CBD51F7C3D98D353379A4CE94192BF355D0735EFB03FE5B6519"
}
```

Approval raw bytes: 1343; SHA-256 `9A56AA3393CE4F32831E90B5D0B8AE79892822E91216CEEEDD3D14A20D00FD48`. Approved-card copy SHA-256 `E13D176F64DE72E4974670CF28B45E4A5EE39B6CF8BAD36353F6106B3A47F426`.

## Original-D historical Git identities

| commit:path | resolved blob | type |
| --- | --- | --- |
| `a718507f31ededba510d1be9335f3c9683e0650b:specs/tasks/T1-APP-STORAGE-ANDROID.md` | `684adfb406263a55c7ac4d6ad2a268053487f500` | `blob` |
| `9635a6503c3a25efa017a40e6da628f5f0dfa57e:specs/tasks/T3-PDF-MEASUREMENT-BINDING.md` | `ac802da62d7a007fe5c26cadbde29ab665cfeb91` | `blob` |
| `6ff56fc2bf829f496f197b74171e989b2941e40e:specs/tasks/T3-PDF-DEVICE-FIXTURE.md` | `1decac7622657aeb228de6c06addcc66e1feefe0` | `blob` |
| `e5cf6339223031519b6c41a734d1a6b67d0cd815:specs/tasks/T3-PDF-TEXT-METRICS-OPS.md` | `38c45eed3271051d5322b18650b89259c728ba7b` | `blob` |

## Actual first full DoD native receipt

```json
{
  "argv": [
    "pwsh",
    "-NoProfile",
    "-File",
    "D:\\Projects\\MyInspection\\_local\\rotating-card-orchestrator\\round45-source-publication-execution\\run-canonical-metadata-dod.ps1"
  ],
  "cwd": "C:\\wt\\T0-REMOTE-ROUND45-CONTRACT-EVIDENCE",
  "startedUtc": "2026-09-18T10:59:15.986661+00:00",
  "endedUtc": "2026-09-18T10:59:23.998748+00:00",
  "nativeExit": 0,
  "stdoutPath": "D:/Projects/MyInspection/_local/rotating-card-orchestrator/round45-source-block2-repair-execution/native/026-first-full-metadata-dod.stdout.raw",
  "stdoutBytes": 413576,
  "stdoutSHA256": "AE6DD0E250375054021D959D45170E3E3E10A94DAC6C19AF7057C2CE76EFE3A8",
  "stderrPath": "D:/Projects/MyInspection/_local/rotating-card-orchestrator/round45-source-block2-repair-execution/native/026-first-full-metadata-dod.stderr.raw",
  "stderrBytes": 0,
  "stderrSHA256": "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"
}
```

Complete raw stdout/stderr are retained at the paths in the native receipt, with byte counts and SHA-256. They are not reproduced or compressed in this diff.

Exact success excerpts only (not the complete stdout):

```text
[ROUND45-SOURCE-PUBLICATION-OK] four historical commit:path contracts, seven-path scope and whitespace
check-cards: PASS（校验 97 张卡）
[ROUND45-SOURCE-ACTUAL-DOD-PASS]
```

Run final full DoD after this receipt is staged and committed; its raw receipt remains external to this nonrecursive review-data document.
