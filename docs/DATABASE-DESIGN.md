# Database design baseline

> Status: design baseline · 2026-08-20. This document reviews the current schema; it does not authorize an unreviewed migration. Existing frozen SQLDelight schema changes require version review and TD4 migration verification first.

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

### Must change in the next reviewed schema window

- Add active-by-ID reads for property, tenancy, and template selection; use them in all new-work paths.
- Replace the generic tenancy baseline setter with two explicit operations:
  - initial INGOING assignment when the pointer is empty;
  - finalized fallback baseline assignment for an eligible inspection from the same property/tenancy.
- Enforce the terminal purge invariant: once `purged_at` is set, `tenant_name` and `contact` remain NULL.
- Prevent updates to deleted `property_item_override` rows.
- Give every multi-row read that affects output/hash/progress an explicit total order.

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
| `id` | TEXT, required, PK | UUIDv7; process-run identity and deterministic tie-breaker |
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
| `id` | TEXT, required, PK | UUIDv7; deterministic tie-breaker |
| `occurred_at` | INTEGER, required | UTC epoch milliseconds |
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

Recommended indexes:

1. `(occurred_at DESC, id DESC) WHERE deleted_at IS NULL` for bounded timeline reads/pruning.
2. `(run_id, occurred_at, id) WHERE deleted_at IS NULL` for one process lifetime.
3. `(correlation_id, occurred_at, id) WHERE deleted_at IS NULL` for reconstructing one operation.
4. `(operation_code, outcome, occurred_at DESC) WHERE deleted_at IS NULL` for failure diagnosis.

No foreign keys point into the evidence database. Both tables are append-only for normal operations; only retention maintenance may set `updated_at`/`deleted_at` and later physically purge soft-deleted rows. All ordinary reads include `deleted_at IS NULL`.

### Retention and failure behavior

- Create each `diagnostic_run` and its first `APP_START` event in one diagnostics-database transaction. If opening or writing that transaction fails, abandon that logging attempt; never leave an intentional run-only row and never fail app startup.
- As crash/corruption recovery defence, retention also soft-deletes any run with no event once `started_at` is older than the startup grace window. This rule covers legacy rows and interruption before a transaction commit.
- Keep at most 90 days and 20,000 active event rows, whichever limit is reached first. Retention first soft-deletes expired events, then soft-deletes run rows after their final active event is gone, and finally purges bounded batches of soft-deleted rows.
- Prune in small batches after app start and successful event batches; never during a critical evidence commit.
- Event recording is best-effort and isolated. A full/corrupt diagnostics database cannot fail capture, finalization, backup, or restore.
- Critical facts remain in domain tables. For example, finalization is proven by `finalized_at/data_hash`, not by a `FINALIZE_SUCCESS` event.
- No per-row hash chain is added. On a single offline device it would detect some accidental edits but would not create honest non-repudiation and would add hot-write/concurrency complexity. The exported diagnostic package instead carries a manifest and file hash.

Initial operation families: app start/slow start, previous-crash recovery marker, inspection create/finalize, supplement append/verify, photo/audio ingest, notice generate/delivery record, contact purge, backup start/verify/fail, restore preflight/commit/rollback, local-media cleanup/rehydration, full local-data erasure outcome, diagnostics export, and database integrity check. Do not log item autosave keystrokes, content, per-frame samples, or per-image performance events; performance stores only bounded aggregates and threshold reason codes.

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
- A logger failure leaves the business operation successful and evidence unchanged.
- Killing the process before the first diagnostics transaction commits leaves no run row; a seeded legacy zero-event run is soft-deleted by `started_at` after the startup grace window and later purged.
- Diagnostic exports contain only allowlisted fields and work in airplane mode.

## 10. References

- [SQLite partial indexes](https://www.sqlite.org/partialindex.html)
- [SQLite foreign-key behavior](https://www.sqlite.org/foreignkeys.html)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
