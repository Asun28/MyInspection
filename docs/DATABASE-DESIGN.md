# Database design baseline

> Status: design baseline · 2026-08-20; lifecycle hardening implemented in schema v3 by PR #191 on 2026-08-29. This document does not authorize an unreviewed migration. Existing frozen SQLDelight schema changes require version review and TD4 migration verification first.

## 1. Verdict

The current database is structurally strong. UUIDv7 keys, active-row partial unique indexes, deterministic evidence ordering, draft-only writes, finalization hashes, supplement chaining, and snapshot records are appropriate for an offline inspection app.

The priority is not a broad normalization rewrite. It is to close three narrow gaps:

1. Make active versus historical reads explicit so deleted parents cannot start new work.
2. Give each sensitive field one typed write owner; generated SQL is not a public permission API.
3. Add a separate, bounded, sanitized diagnostics database for local support logs.

The app remains single-user and offline-first. “Admin” means the device owner explicitly exports a diagnostic package to support. It does not mean accounts, roles, remote access, or an evidence-edit bypass.

## 2. Database boundaries

| Store | Purpose | Backup | Authority |
| --- | --- | --- | --- |
| Main evidence database | Properties, tenancies, templates, inspections, evidence assets, notices | Included in encrypted full `.mibk` | Domain services only |
| Diagnostics database | Bounded operational events for debugging | Excluded | OperationEventRecorder appends; maintenance prunes; exporter reads |
| File storage | Photos/audio/PDF and backup artifacts | By explicit backup/export contract | Storage adapters only |

The diagnostics database is separate because it must be able to record a main-database failure, must never roll back a successful inspection operation, and should not reappear as stale history after restore. It lives in credential-encrypted internal/no-backup storage and has no network sender.

## 3. Table lifecycle and ownership

| Table | Lifecycle | Write owner | Sensitive fields / rule |
| --- | --- | --- | --- |
| `property` | Mutable while active; soft-hidden, never cascaded | Property use case | `address`; new work requires active row |
| `tenancy` | Mutable lifecycle; contact purge is terminal | Tenancy use case + retention service | `tenant_name`, `contact`; `purged_at != NULL` requires both NULL |
| `template_version` | Immutable version | `TemplateStore` | New inspections use active version; history may read deleted version |
| `check_item_def` | Immutable with its version | `TemplateStore` | Historical render must never silently lose definitions |
| `inspection` | Draft aggregate → immutable finalized evidence | `InspectionRepository` + finalizer | `finalized_at/data_hash` written atomically once |
| `room_instance` | Draft child; historical after finalization | `InspectionRepository` | Ordered by template room order then `instance_no` |
| `inspection_item` | Draft autosave; immutable after finalization | `InspectionRepository` | `note`, `status`, `wear_or_damage`; draft guard required |
| `photo` | Draft association/soft removal; immutable after finalization | Photo recorder | Paths/hashes never enter diagnostic logs |
| `audio` | Draft append; retained as source evidence | Audio recorder | No generic delete path |
| `supplement` | Append-only after finalization | `SupplementChainService` | Current chain tail + strict timestamp checked in one transaction |
| `notice` | Immutable generated snapshot + one delivery transition | Notice use case | `full_text` and validation snapshot are intentional evidence copies |
| `property_item_override` | Mutable active property setting | `InspectionRepository` | Reversible `suppressed`; deleted row cannot be mutated |
| `phrase_entry` | Seed/version content | Phrase library loader | JSON status set is acceptable bounded configuration |

## 4. Field write-authority matrix

| Field group | Create | Update | Read | Forbidden |
| --- | --- | --- | --- | --- |
| IDs / `created_at` | Owning service only | Never | Domain readers | UI-supplied IDs/timestamps |
| `updated_at` | Owning service clock | Same transaction as real mutation | Diagnostics may expose only aggregate timing | Updating it without a domain change |
| `deleted_at` | NULL | Dedicated soft-hide use case only | Active queries exclude; history queries include | Cascade-delete finalized evidence |
| Tenancy contact | Tenancy use case | Before purge | Retention UI and authorized domain flow | Re-populate after `purged_at` |
| Tenancy baseline pointer | Inspection creation or dedicated baseline assignment | Guarded typed operation | Capture/compare | Generic raw setter; different property/tenancy; invalid type |
| Draft inspection evidence | Capture/media owner | Only while inspection is DRAFT | Capture/finalizer | Any post-finalize update, including admin/support |
| Finalization fields | Finalizer | Exactly once | History/report/verification | Manual repair or overwrite |
| Supplement hashes | Chain service | Append only | Report/verification | Update/delete/reorder |
| Notice delivery fields | Notice service | Exactly once | Notice/history | Second delivery overwrite |
| Diagnostic event | Event recorder | Never | Local exporter | UI/raw SQL append; remote collection |

SQLDelight generates callable query methods, but generation does not grant authority. Production code should receive narrow repositories/services rather than a general database handle wherever practical.

## 5. Active versus historical reads

Use names that expose lifecycle intent:

- `selectActiveById`: required for starting new inspections, changing active property settings, choosing a tenancy, and choosing a template.
- `selectAnyById`: restricted to report reconstruction, retention/privacy cleanup, chain verification, restore validation, and diagnostics.
- Existing ambiguous `selectById` methods should be migrated caller-by-caller, then treated as internal compatibility APIs.

Important exception: contact retention must still purge personal data from a soft-hidden tenancy. Soft deletion is not permission to retain expired PII.

Logical foreign keys remain intentional. Soft-deleted parents must stay referenceable by historical evidence; cross-row compatibility is enforced by the typed transactional owner rather than cascading physical foreign keys.

## 6. Confirmed hardening work

### Implemented in schema v3 (PR #191)

- Active-by-ID reads now guard property, tenancy, and template selection in new-work paths; historical and retention paths use explicit any-lifecycle reads.
- The generic tenancy baseline setter is replaced by two explicit operations:
  - initial INGOING assignment when the pointer is empty;
  - finalized fallback baseline assignment for an eligible inspection from the same property/tenancy.
- The schema enforces the terminal purge invariant: once `purged_at` is set, `tenant_name` and `contact` remain NULL.
- Deleted `property_item_override` rows cannot be updated.
- Lifecycle and baseline guards are covered by positive, negative, migration, and single-clause mutation tests; `:core:check` and the project verify gate passed at merge commit `3d50f690`.

### Keep as designed

- `notice.full_text`, `scheduled_at`, and `validation_snapshot` are snapshots, not wasteful duplication.
- `notice.lead_hours` is a compliance decision snapshot; the notice owner must derive it from the stored schedule and actual delivery time.
- Template/phrase status arrays may remain JSON because they are bounded versioned configuration, not high-volume relational facts.
- Do not add `created_by`, `updated_by`, `admin_id`, role tables, ACL tables, or account tables to a single-user offline app.
- Do not add database triggers that mutate finalized evidence or hide domain behavior from tests.

## 7. Diagnostics database schema

### `diagnostic_run`

Process-stable environment fields are normalized into one row per app process rather than repeated on every event.

| Field | Type / nullability | Contract |
| --- | --- | --- |
| `id` | TEXT, required, PK | UUIDv7 process-run identity; ordering comes from its `APP_START` event sequence |
| `started_at` | INTEGER, required | UTC epoch milliseconds |
| `app_version` | TEXT, required | User-visible app version |
| `app_build` | INTEGER, required | Build/version code |
| `main_schema_version` | INTEGER, required | Evidence database schema at process start |
| `os_api_level` | INTEGER, required | Android API level; no device unique ID |
| `device_model` | TEXT, required | Bounded model name for compatibility diagnosis |
| `updated_at` | INTEGER, required | UTC epoch milliseconds; initially equals `started_at`, changed only by retention maintenance |
| `deleted_at` | INTEGER, optional | Soft-delete marker set only by retention maintenance |

There is no `ended_at`: Android may kill the process without a callback, so a nullable end time would mostly encode unreliable absence. A new run row is created at the next process start.

### `operation_event`

| Field | Type / nullability | Contract |
| --- | --- | --- |
| `id` | TEXT, required, PK | UUIDv7 event identity; never used to infer causal order |
| `sequence_no` | INTEGER, required, UNIQUE | Database-assigned durable insertion order; callers cannot supply or reuse it |
| `occurred_at` | INTEGER, required | UTC epoch milliseconds for display and age calculations only |
| `operation_code` | TEXT, required | Closed typed code, max 64 ASCII chars |
| `outcome` | TEXT, required | `SUCCESS`, `FAILURE`, `REJECTED`, `CANCELLED` |
| `reason_code` | TEXT, optional | Required for non-success; closed sanitized code |
| `run_id` | TEXT, required | Logical reference to one `diagnostic_run` |
| `correlation_id` | TEXT, required | Opaque ID shared by events from one operation |
| `scope_type` | TEXT, optional | Closed type such as `INSPECTION`, `BACKUP`, `RESTORE` |
| `scope_id` | TEXT, optional | Opaque ID; present only with `scope_type` |
| `duration_ms` | INTEGER, optional | Non-negative |
| `item_count` | INTEGER, optional | Non-negative aggregate only |
| `context_json` | TEXT, required, default `{}` | Valid JSON, max 2 KiB, event-specific allowlisted keys only |
| `updated_at` | INTEGER, required | UTC epoch milliseconds; initially equals `occurred_at`, changed only by retention maintenance |
| `deleted_at` | INTEGER, optional | Soft-delete marker set only by retention maintenance |

Deliberately absent: `created_at` (duplicates `occurred_at`), user/admin IDs, severity (derived from outcome/reason), raw stack traces, free-form messages, paths, URIs, hashes, addresses, names, contact details, notes, transcriptions, media, credentials, and provider bodies.

`diagnostic_counter` keeps UUIDv7 `id` PK, unique `counter_code`, positive `next_value`, `updated_at`, and `deleted_at`; `EVENT_SEQUENCE` allocation increments and returns the value in the event transaction. The main receipt DB uses the same UUID-keyed counter shape for `VERIFICATION_REVISION`. Counter rows cannot be deleted, and rolled-back allocations do not escape.

Recommended indexes:

1. `(sequence_no DESC) WHERE deleted_at IS NULL` for bounded timeline reads/pruning.
2. `(run_id, sequence_no) WHERE deleted_at IS NULL` for one process lifetime.
3. `(correlation_id, sequence_no) WHERE deleted_at IS NULL` for reconstructing one operation.
4. `(operation_code, outcome, sequence_no DESC) WHERE deleted_at IS NULL` for failure diagnosis.
5. Unique `(correlation_id) WHERE operation_code = 'BACKUP_RESULT'` so retries cannot append two terminal backup outcomes.
6. Unique `(correlation_id) WHERE operation_code = 'PREVIOUS_CRASH'` so restart recovery cannot report one exit twice.

No foreign keys point into the evidence database. Both tables are append-only for normal operations; only retention maintenance may set `updated_at`/`deleted_at` and later physically purge soft-deleted rows. All ordinary reads include `deleted_at IS NULL`.

`sequence_no`, never `occurred_at` or UUID lexical order, decides latest/later and state-machine transitions. The recorder inserts the event and applies any health projection in one diagnostics transaction; a transition is accepted only when its `sequence_no` exceeds the projection's stored sequence. Clock rollback and equal-millisecond events therefore change display/age data only, never causal order.

### `diagnostic_health`

This bounded materialized projection is intentional: event rows are diagnostic history with finite retention, while an actionable health latch must survive until its declared clear transition.

| Field | Type / nullability | Contract |
| --- | --- | --- |
| `id` | TEXT, required, PK | UUIDv7 row identity |
| `state_code` | TEXT, required | `FINALIZE_FAILED`, `PDF_FAILED`, `BACKUP_LAST_FAILED`, `BACKUP_FAILED_3X`, `INTEGRITY_FAILED`, `RESTORE_FAILED`, `RESTORE_ROLLED_BACK`, `PREVIOUS_CRASH`, or `STARTUP_SLOW` |
| `scope_type` | TEXT, required, default `NONE` | `NONE` or the registry's closed scope type |
| `scope_id` | TEXT, required, default empty | Empty iff `scope_type = NONE`; otherwise opaque ID |
| `dimension_key` | TEXT, required, default empty | Closed discriminator: PDF uses `LANDLORD`/`TENANT`, integrity uses `MAIN_DB`/`BACKUP`/`RESTORE`, otherwise empty |
| `is_active` | INTEGER, required | Boolean latch |
| `failure_streak` | INTEGER, required, default 0 | `0..3`; non-zero only for `BACKUP_FAILED_3X` |
| `transition_sequence_no` | INTEGER, required | Last applied `operation_event.sequence_no`; remains meaningful after source-event pruning |
| `authority_revision` | INTEGER, optional | Required only for `INTEGRITY_FAILED` BACKUP/RESTORE receipt sources; last applied monotonic receipt revision |
| `activated_at` | INTEGER, optional | UTC epoch milliseconds for display only |
| `updated_at` | INTEGER, required | UTC epoch milliseconds for display only |
| `deleted_at` | INTEGER, optional | Set only when pruning an inactive scoped row |

`(state_code, scope_type, scope_id, dimension_key)` is unique among non-deleted rows. `BACKUP_STALE_7D` is absent because it comes from the receipt and current time. Active rows are never pruned; inactive scoped rows may soft-delete after 90 days, while global rows remain. Event pruning never clears this table.

### Diagnostic registry v1

The registry version is stored with the diagnostics schema and exported manifest. Unknown operation codes, reason codes, context keys, enum values, or registry versions are rejected before insert. A success has `reason_code = NULL`; `CANCELLED` requires `USER_CANCELLED`; `REJECTED` accepts only precondition reasons; `FAILURE` accepts only execution reasons. App/startup markers permit `SUCCESS` only; a previous-crash marker is a typed `FAILURE` about the preceding process.

| Operation code(s) | Allowed outcomes | Non-success reason set | `context_json` schema | Scope |
| --- | --- | --- | --- | --- |
| `APP_START` | `SUCCESS` | none | `{}` | none |
| `PREVIOUS_CRASH` | `FAILURE` | `CRASH` | `crash_context` | none |
| `STARTUP_SLOW` | `SUCCESS` | none | `threshold_ms` | none |
| `INSPECTION_CREATE`, `INSPECTION_FINALIZE`, `SUPPLEMENT_APPEND`, `SUPPLEMENT_VERIFY`, `NOTICE_GENERATE`, `NOTICE_DELIVERY_RECORD` | all four | `DOMAIN` | `{}` | `INSPECTION` + opaque ID required |
| `CONTACT_PURGE` | all four | `DOMAIN` | `{}` | `TENANCY` + opaque ID required |
| `PHOTO_INGEST`, `AUDIO_INGEST` | all four | `MEDIA` | `{}` | `INSPECTION` + opaque ID required |
| `MEDIA_CLEANUP`, `MEDIA_REHYDRATE` | all four | `MEDIA` | `media_kind` | none |
| `PDF_GENERATE` | all four | `REPORT` | `report_variant` | `INSPECTION` + opaque ID required |
| `BACKUP_RESULT` | all four; exactly one terminal result per correlation ID | `BACKUP` | `scope_kind` | `BACKUP` + opaque ID required |
| `RESTORE_PREFLIGHT`, `RESTORE_COMMIT` | all four | `RESTORE` | `scope_kind` | `RESTORE` + opaque ID required |
| `RESTORE_ROLLBACK` | `SUCCESS`, `FAILURE` | `RESTORE_RUNTIME` | `scope_kind` | `RESTORE` + opaque ID required |
| `LOCAL_DATA_ERASE` | `CANCELLED`, `REJECTED`, `FAILURE`; persisted `SUCCESS` forbidden | `ERASURE` | `erasure_category` | none |
| `DIAGNOSTICS_EXPORT` | all four | `EXPORT` | `window_days` | none |
| `DATABASE_INTEGRITY_CHECK` | `SUCCESS`, `FAILURE` | `INTEGRITY` | `integrity_context` | conditional by source |

Reason sets are closed unions, never persisted as values themselves:

| Set | Exact allowed reason codes |
| --- | --- |
| `DOMAIN` | rejected: `VALIDATION_FAILED`, `NOT_FOUND`, `STATE_CONFLICT`; failure: `INVARIANT_FAILED`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `CRASH` | failure: `UNCAUGHT_EXCEPTION`, `NATIVE_CRASH`, `ANR`, `INITIALIZATION_FAILURE`, `RESOURCE_LIMIT` |
| `MEDIA` | rejected: `VALIDATION_FAILED`, `NOT_FOUND`, `PERMISSION_DENIED`, `LOW_STORAGE`; failure: `IO_ERROR`, `MEDIA_UNAVAILABLE`, `HASH_MISMATCH`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `REPORT` | rejected: `VALIDATION_FAILED`, `LOW_STORAGE`, `PERMISSION_DENIED`; failure: `IO_ERROR`, `MEDIA_UNAVAILABLE`, `RENDER_FAILED`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `BACKUP` | rejected: `LOW_STORAGE`, `AUTHORIZATION_REVOKED`, `NEEDS_UNLOCK`, `NEEDS_PASSPHRASE`, `PERMISSION_DENIED`; failure: `IO_ERROR`, `PROVIDER_UNAVAILABLE`, `HASH_MISMATCH`, `INTEGRITY_FAILED`, `PROCESS_INTERRUPTED`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `RESTORE` | rejected: `LOW_STORAGE`, `AUTHORIZATION_REVOKED`, `NEEDS_UNLOCK`, `WRONG_PASSPHRASE`, `UNSUPPORTED_FORMAT`, `UNSUPPORTED_SCHEMA`, `PERMISSION_DENIED`; failure: `IO_ERROR`, `PROVIDER_UNAVAILABLE`, `CORRUPT_ARCHIVE`, `HASH_MISMATCH`, `INTEGRITY_FAILED`, `PROCESS_INTERRUPTED`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `RESTORE_RUNTIME` | failure: `IO_ERROR`, `INTEGRITY_FAILED`, `PROCESS_INTERRUPTED`, `UNKNOWN_SAFE` |
| `ERASURE` | rejected: `VALIDATION_FAILED`, `PERMISSION_DENIED`; failure: `IO_ERROR`, `DELETE_INCOMPLETE`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `EXPORT` | rejected: `AUTHORIZATION_REVOKED`, `PERMISSION_DENIED`; failure: `IO_ERROR`, `PROVIDER_UNAVAILABLE`, `UNKNOWN_SAFE`; cancelled: `USER_CANCELLED` |
| `INTEGRITY` | failure: `INTEGRITY_FAILED`, `CORRUPT_ARCHIVE`, `HASH_MISMATCH`, `IO_ERROR`, `UNKNOWN_SAFE` |

`context_json` has no implicit keys. `threshold_ms` is an integer millisecond duration in `1..120000`; `scope_kind` is `FULL` or `PROPERTY`; `media_kind` is `PHOTO` or `AUDIO`; `report_variant` is `LANDLORD` or `TENANT`; `window_days` is `7`, `30`, or `90`; `check_kind` is `QUICK_CHECK`, `MANIFEST`, or `FILE_HASH`; `erasure_category` is `CONFIRMATION`, `MAIN_DB`, `DIAGNOSTICS_DB`, `MEDIA`, `REPORTS`, `SETTINGS`, `KEYSTORE`, `CACHE`, `JOURNAL`, or `URI_GRANTS`; cancellation/rejection uses `CONFIRMATION`, failure uses its affected category. `crash_context` contains exactly: required `source_kind` (`ANDROID_EXIT_INFO` or `LOCAL_UNCAUGHT_MARKER`), required `build_id` (`[A-Za-z0-9._-]{1,64}`), required `exception_class_code` (`ILLEGAL_STATE`, `ILLEGAL_ARGUMENT`, `IO`, `SQLITE`, `OUT_OF_MEMORY`, `SECURITY`, or `UNKNOWN`), and required `frame_ids` (array of 0–8 allowlisted identifiers, each `[A-Za-z0-9_.$#-]{1,96}`). It never contains an exception message, class name outside the closed mapping, line number, path, URI, business value, payload, or stack dump.

`integrity_context` supersedes bare `check_kind`: MAIN_DB is exactly `{source_kind: MAIN_DB, check_kind: QUICK_CHECK}` and unscoped; BACKUP/RESTORE is exactly `{source_kind, check_kind: ARCHIVE_VERIFICATION, authority_revision}` with matching scope and receipt ID. Revision is a positive database counter; mismatched source/check/scope or extra/missing keys fail.

Each operation accepts exactly the listed context schema or `{}`; additional/missing keys fail validation. `duration_ms` and `item_count` use their typed columns, respectively milliseconds and unitless row/file counts, never duplicate context keys. JSON is canonical UTF-8 and remains within 2 KiB. The closed non-null `scope_type` values are `INSPECTION`, `TENANCY`, `BACKUP`, and `RESTORE`; each requires an opaque `scope_id`, while unscoped operations require both fields NULL.

Health transitions are closed and deterministic. Event-driven transitions are materialized into `diagnostic_health` in `sequence_no` order; retention never derives state again from the surviving event window. Direct operation results drive the same UI immediately, so diagnostics failure cannot hide or alter the business result.

Backup/restore receipts retain UUIDv7 `id` PK plus `updated_at`/`deleted_at`; their transaction allocates a unique integer `verification_revision` from `VERIFICATION_REVISION` without making it a key. A mirror integrity event copies it; each source accepts only a greater revision. Startup reconciles a missed mirror from the highest receipt revision, while the receipt remains UI authority.

Projection keys are exact: finalize = `(FINALIZE_FAILED, INSPECTION, inspection_id, '')`; PDF = `(PDF_FAILED, INSPECTION, inspection_id, variant)`; integrity = `(INTEGRITY_FAILED, NONE, '', source_kind)`; every other materialized state = `(state_code, NONE, '', '')`. BACKUP/RESTORE event scope IDs remain diagnostic correlation only, so a later occurrence updates/clears the same global row.

| Health state | Source and activation | Clear condition |
| --- | --- | --- |
| `BACKUP_STALE_7D` | no authoritative VerifiedBackupReceipt, or `now - verified_at >= 604800000` ms | a newer verified receipt |
| `FINALIZE_FAILED` | `INSPECTION_FINALIZE/FAILURE` or `/REJECTED`, keyed by inspection | later `INSPECTION_FINALIZE/SUCCESS` for that inspection |
| `PDF_FAILED` | `PDF_GENERATE/FAILURE` or `/REJECTED`, keyed by inspection + `report_variant` | later `PDF_GENERATE/SUCCESS` for the same inspection + variant |
| `BACKUP_LAST_FAILED` | `BACKUP_RESULT/FAILURE` or `/REJECTED` | later `BACKUP_RESULT/SUCCESS` |
| `BACKUP_FAILED_3X` | each non-cancelled `BACKUP_RESULT/FAILURE` or `/REJECTED` increments the materialized streak, capped at 3; reaching 3 activates | `BACKUP_RESULT/SUCCESS` atomically resets streak to 0 and clears |
| `INTEGRITY_FAILED` | source-matched `DATABASE_INTEGRITY_CHECK/FAILURE`, or highest-revision authoritative failed backup/restore verification receipt, keyed independently by `MAIN_DB`, `BACKUP`, or `RESTORE` | a later event sequence for MAIN_DB, or greater successful receipt revision for the same BACKUP/RESTORE source |
| `RESTORE_FAILED` | `RESTORE_PREFLIGHT` or `RESTORE_COMMIT` is `FAILURE`/`REJECTED`, or `RESTORE_ROLLBACK/FAILURE` | later `RESTORE_COMMIT/SUCCESS` |
| `RESTORE_ROLLED_BACK` | `RESTORE_ROLLBACK/SUCCESS` | later `RESTORE_COMMIT/SUCCESS` |
| `PREVIOUS_CRASH` | current run records `PREVIOUS_CRASH/FAILURE` with valid `CRASH` reason and `crash_context` | next run's atomic `APP_START` transaction when it has no marker |
| `STARTUP_SLOW` | current run records `STARTUP_SLOW/SUCCESS`; measured startup exceeds stored `threshold_ms` | next run's atomic `APP_START` transaction when it has no slow marker |

Registry tests must reject every unknown code/key/value and every illegal outcome/reason pair, verify milliseconds versus count units, and exercise the activation and explicit clear sequence for every health state. Ordering tests insert causally later events with an earlier wall-clock value and equal timestamps, proving the database-assigned sequence alone wins. Retention tests prune every source event for each active state at both the age and row-count boundary, prove the projection remains active, then apply only its declared clear transition and prove it clears. PDF tests cover both variants, every outcome/reason rule, per-inspection isolation, one-second UI projection, and unknown context rejection. Backup tests prove `BACKUP_START` is unknown, two weekly logical occurrences use different UUIDv7 correlation IDs, a process-death retry reuses its occurrence ID, and the unique terminal-result rule rejects a second `BACKUP_RESULT`. Crash tests reject messages, raw/unmapped class names, paths, oversized/build-invalid values, more than eight frames, and unsafe frame characters. Scope tests prove `CONTACT_PURGE` accepts a real tenancy with zero or many inspections and rejects an inspection scope. Logger failure still leaves business results unchanged.

Integrity tests reject source/check/scope mismatches, cross-source clearing and stale revisions, then reconcile a missed mirror. Crash tests cover every mapping, normal exits, pre-30 normal death, repeated launch, and kills around the durable claim; one exit creates at most one alert/event.

Projection tests clear global backup/restore states from a later event with a different occurrence scope ID; failed rollback activates only `RESTORE_FAILED`, while successful rollback activates only `RESTORE_ROLLED_BACK`.

#### Crash evidence and deduplication

- Android 11+ reads at most 32 own-package `ApplicationExitInfo` rows. CRASH/CRASH_NATIVE/ANR/INITIALIZATION_FAILURE/EXCESSIVE_RESOURCE map to the five `CRASH` reasons in order; all other reasons, including low-memory, user stop and package update, never alert. `source_id = SHA-256("aei-v1", package/process name, timestamp, pid, reason, status)` uses only immutable fields of that exit record, so repeated launches reproduce it. Description, trace, memory data and process-state summary are never read; system-sourced context uses build/class `UNKNOWN` and no frames.
- Android 10 and below never infers from missing clean shutdown: only an atomic delegated uncaught-Java marker (random ID, bounded build, closed class, ≤8 safe app frames) qualifies; its hashed ID is `source_id`. An atomic no-backup ledger claims all inspected IDs before best-effort event insertion, retains them while present and ≥90 days, and uses `source_id` as correlation. Thus a kill may lose a log but cannot repeat an alert, even after diagnostics DB rebuild; normal/native/ANR/system deaths never alert pre-30.

### Retention and failure behavior

- Create each `diagnostic_run`, its first `APP_START` event, any already-determined `PREVIOUS_CRASH` marker, and the corresponding crash/slow latch transitions in one diagnostics-database transaction. The transaction clears the prior run's crash/slow latches first and reactivates only markers present for the new run. If opening or writing that transaction fails, abandon that logging attempt; never leave an intentional run-only row and never fail app startup.
- As crash/corruption recovery defence, retention also soft-deletes any run with no event once `started_at` is older than the startup grace window. This rule covers legacy rows and interruption before a transaction commit.
- Keep at most 90 days and 20,000 active event rows, whichever limit is reached first. Retention first soft-deletes expired events, then soft-deletes run rows after their final active event is gone, and finally purges bounded batches of soft-deleted rows. It never clears or rebuilds `diagnostic_health`; an active latch survives source-event deletion until its explicit transition, while the streak counter survives below-threshold failures across pruning.
- Prune in small batches after app start and successful event batches; never during a critical evidence commit.
- Event recording is best-effort and isolated. A full/corrupt diagnostics database cannot fail capture, finalization, backup, or restore.
- Critical facts remain in domain tables. For example, finalization is proven by `finalized_at/data_hash`, not by a `FINALIZE_SUCCESS` event.
- No per-row hash chain is added. On a single offline device it would detect some accidental edits but would not create honest non-repudiation and would add hot-write/concurrency complexity. The exported diagnostic package instead carries a manifest and file hash.

Full local erasure has special ordering. Cancellation/rejection and failures before diagnostics deletion may append `LOCAL_DATA_ERASE` only while that existing database remains valid. Once the user confirms, an opaque `erasure-in-progress` marker is durably written; cancellation then stops. App-owned categories are removed and verified, diagnostics DB/WAL/SHM are closed and deleted last, and no logger may reopen them. A post-diagnostics failure is returned directly and the marker makes next launch resume erasure before any database initialization. Success means every category and marker is absent, no success event/database exists, and the UI enters first-run state.

Backup diagnostics intentionally record only the terminal `BACKUP_RESULT`, never a start event. The periodic WorkManager request is only a cadence trigger; it does not perform backup and its stable request ID is never a correlation ID. For each due weekly occurrence it atomically creates an app-private `BackupJobEnvelope` (`occurrence_id` UUIDv7, trigger kind, trigger key, `PENDING`) before enqueueing unique one-time work named by that occurrence ID. The weekly trigger key is its persisted cadence due bucket; finalize uses `FINALIZE:<inspection UUID>:<finalized_at>`. A new trigger key creates a new envelope/ID; duplicate invocations of the same key use `KEEP` and cannot create a second occurrence. Thus consecutive weekly buckets and distinct finalized evidence always get fresh IDs, while redelivery/retry of one logical occurrence does not. Startup and the cadence trigger re-enqueue any `PENDING`/`RUNNING` envelope without a terminal result, so process-death retries reuse that occurrence ID; after a terminal result the envelope is marked complete. Envelope writes use credential-encrypted `noBackupFilesDir`, atomic temp-write/rename, contain no address/contact/media data, and are deleted by `Delete all local data`.

The one-time worker appends the terminal event only when the logical occurrence reaches `SUCCESS`, `FAILURE`, `REJECTED`, or `CANCELLED`. `RUNNING` comes from that one-time WorkInfo/UI operation state, not event history, so there is no orphan diagnostic start to age or reconcile. Manual work uses a fresh ID per invocation and the same terminal-only rule, but its running state is process-local. Acceptance runs two distinct weekly due buckets and proves distinct IDs/results; it then kills one occurrence after bytes are written but before verification, proves no terminal event or false Verified receipt exists, restarts/re-enqueues the envelope with the same ID, and proves exactly one terminal result. Logger failure may leave no terminal event and never changes the authoritative receipt or business result.

Initial operation families: app start/slow start, previous-crash recovery marker, inspection create/finalize, PDF generation, supplement append/verify, photo/audio ingest, notice generate/delivery record, contact purge, terminal backup verify/fail/cancel/reject, restore preflight/commit/rollback, local-media cleanup/rehydration, full local-data erasure outcome, diagnostics export, and database integrity check. Do not log item autosave keystrokes, content, per-frame samples, or per-image performance events; performance stores only bounded aggregates and threshold reason codes.

## 8. Diagnostic export contract

The Settings action `Export diagnostic report` is local and user-initiated:

1. Show exactly what is included and excluded.
2. Default to the last 7 days; allow up to the retained 90 days.
3. Run read-only integrity summaries (`quick_check` result, schema/app version, table counts and verification result counts), never row content.
4. Export a versioned manifest plus sanitized events using SAF or temporary read-only `content://` sharing.
5. Include OS API/device model only; exclude serial, advertising ID, account identity, exact storage paths, and network identifiers.
6. Log only the export outcome/reason/count, never its destination URI or file name.

Support/admin receives no database console and no mutation endpoint. Any repair feature added later must be a separate reviewed domain use case and cannot alter finalized evidence.

## 9. Acceptance gates

- Deleting the active-row predicate from each new-work parent lookup makes a test fail.
- Deleted tenancy contact can still be purged, while deleted tenancy cannot receive a new baseline.
- Cross-property/cross-tenancy/unfinalized fallback baselines are rejected; valid initial INGOING assignment still works.
- Any attempt to repopulate contact after purge fails.
- Removing each finalized/draft guard makes a focused invariant test fail.
- Logger tests inject full disk, corrupt diagnostics DB, invalid context keys, oversized JSON, CR/LF, and every forbidden sensitive field.
- Registry mutation tests remove each operation/reason/context/health mapping in turn and must fail; three backup failures followed by success activates then clears `BACKUP_FAILED_3X` deterministically.
- Sequence tests cover clock rollback and same-millisecond events; retention tests cross both limits for every active latch and prove only its explicit clear event deactivates it.
- Schema tests require UUIDv7 PK plus `updated_at`/`deleted_at` on every table; concurrent counter allocation is unique/ordered and a rolled-back allocation leaves no committed event/revision.
- PDF failure produces a scoped actionable state within one second; a successful regeneration of the same variant clears it without affecting another inspection or variant.
- Two weekly due buckets create different backup occurrence IDs; killing and retrying one bucket reuses its ID and still emits at most one terminal result.
- A logger failure leaves the business operation successful and evidence unchanged.
- Erasure tests prove pre-delete cancellation/failure may log, a kill at every deletion boundary resumes from the marker, diagnostics deletion failure never recreates storage, and success leaves no diagnostic DB/event and boots first-run.
- Killing the process before the first diagnostics transaction commits leaves no run row; a seeded legacy zero-event run is soft-deleted by `started_at` after the startup grace window and later purged.
- Diagnostic exports contain only allowlisted fields and work in airplane mode.

## 10. References

- [SQLite partial indexes](https://www.sqlite.org/partialindex.html)
- [SQLite foreign-key behavior](https://www.sqlite.org/foreignkeys.html)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
