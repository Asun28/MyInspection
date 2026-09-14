# Anchors fixture

## Not the status table

| decoy | note |
|---|---|
| `[PRE-DECOY-BEFORE]` | ignored |

Prose may mention `[PRE-PACKET-READY]` without being a row.

## Status codes

| code | owner | phase | meaning | counts as |
|---|---|---|---|---|
| `[PRE-NO-MERGE-BASE]` | FACTPACK | 1a | m | stop |
| `[PRE-SECRETS]` | FACTPACK | 1a | m | stop |
| `[PRE-NO-NETWORK-IN-CI]` | RUN | 1a | m | not spawned |
| `[PRE-LIVE-REFUSED]` | runner | 1a | m | incomplete |
| `[PRE-WORKER-MISSING]` | runner | 1a | m | incomplete |
| `[PRE-NO-OUTPUT]` | runner | 1a | m | incomplete |
| `[PRE-TIMEOUT]` | runner | 1a | m | incomplete |
| `[PRE-BAD-RECORD]` | RECORDS | 1a | m | incomplete |
| `[PRE-LENS-SKIPPED]` | WORKERS | 1a | m | skipped |
| `[PRE-PACKET-READY]` | RUN | 1a | m | info |
| `[PRE-RUN-DISABLED]` | RUN | 1a | m | stop |
| `[PRE-BUDGET-AFTER-FIX]` | RUN | 1b | m | stop |
| `[PRE-BATCH-CAP]` | RUN | 1b | m | stop |
| `[PRE-MISSING]` | ship gate | 1b | m | refused |
| `[PRE-STALE]` | ship gate | 1b | m | refused |
| `[PRE-OPEN]` | ship gate | 1b | m | refused |
| `[PRE-INCOMPLETE]` | ship gate | 1b | m | refused |
| `[PRE-SKIPPED]` | ship gate | 1b | m | continues |
| `[PRE-DISABLED]` | ship gate | 1b | m | continues |
| `[PRE-GATE-PASS]` | ship gate | 1b | m | continues |

Only the first table counts:

| later | note |
|---|---|
| `[PRE-DECOY-AFTER]` | ignored |
