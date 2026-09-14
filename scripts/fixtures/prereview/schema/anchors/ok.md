# Anchors fixture

Prose may mention `[PRE-PACKET-READY]` without being a row.

## Status codes

| code | owner | phase | meaning | counts as |
|---|---|---|---|---|
| `[PRE-NO-MERGE-BASE]` | | | | stop |
| `[PRE-SECRETS]` | | | | stop |
| `[PRE-NO-NETWORK-IN-CI]` | | | | not spawned |
| `[PRE-LIVE-REFUSED]` | | | | incomplete |
| `[PRE-WORKER-MISSING]` | | | | incomplete |
| `[PRE-NO-OUTPUT]` | | | | incomplete |
| `[PRE-TIMEOUT]` | | | | incomplete |
| `[PRE-BAD-RECORD]` | | | | incomplete |
| `[PRE-LENS-SKIPPED]` | | | | skipped |
| `[PRE-PACKET-READY]` | | | | info |
| `[PRE-RUN-DISABLED]` | | | | stop |
| `[PRE-BUDGET-AFTER-FIX]` | | | | stop |
| `[PRE-BATCH-CAP]` | | | | stop |
| `[PRE-MISSING]` | | | | refused |
| `[PRE-STALE]` | | | | refused |
| `[PRE-OPEN]` | | | | refused |
| `[PRE-INCOMPLETE]` | | | | refused |
| `[PRE-SKIPPED]` | | | | continues |
| `[PRE-DISABLED]` | | | | continues |
| `[PRE-GATE-PASS]` | | | | continues |

Only the first table counts:

| later | note |
|---|---|
| `[PRE-DECOY-AFTER]` | ignored |
