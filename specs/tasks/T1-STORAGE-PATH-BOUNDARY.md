---
id: T1-STORAGE-PATH-BOUNDARY
title: Verified storage path snapshots and checked child directories
status: todo
depends_on: [T1-SPIKE-PLATFORM]
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/StoragePathBoundary.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/StoragePathBoundaryTest.kt
forbid:
  - Android imports, new dependencies, filesystem writes or operating-system processes in production
  - AppStorageEnvironment, namespace/media routing, Android adapter or business assembly implementation
  - Treating canonicalFile or lexical normalization as proof of resolved filesystem containment
  - Weakening absent-directory support, skipping required link tests, changing privileges or system policy
non_goals:
  - Locking inode or volume identity, atomic filesystem snapshots, eliminating TOCTOU or guaranteeing later I/O
  - Android getter provenance, writable/space mapping, Keystore, media policy or tenant data migration
acceptance:
  - "A1 Independent internal StoragePathBoundary has a private constructor, nullable create(candidate, appDataDir, deviceProtectedDataDir), a saved resolved directory and nullable resolveChild(File). It imports no Policy types. Reject each blank raw root before resolving any root; candidate must be a strict descendant of resolved app root and outside resolved DP root. Save the exact candidate Path used in those comparisons, plus the resolved DP exclusion path; never validate then resolve again for storage."
  - "A2 Resolve each returned child against the saved root and DP path: reject exact root, prefix sibling, outside no-backup including CE siblings, external and DP; accept an in-root alias and return the same resolved File that was checked. An input alias retargeted after create does not change the saved directory; replacing the saved directory name with an outside alias makes child resolution refuse. A previously absent suffix replaced by an outside alias also refuses."
  - "A3 Preserve nonexistent roots and multilevel nonexistent children without creating them. Resolve ordered absolute path segments with real filesystem observations; handle dot and parent after resolving aliases. Distinguish missing/../existing-alias/child and alias/../child from premature normalization or one-shot missing-suffix reconstruction."
  - "A4 Use NOFOLLOW attributes and toRealPath for existing entries and confirm directory targets. Only NoSuchFileException from the entry probe means an absent component; access denial, other I/O/security failures, regular files, dangling aliases and cycles fail closed. Never use exists()==false to classify every failure as missing. A narrow internal attribute-read test seam is allowed; production public entrypoints always use actual Files operations."
  - "A5 create/resolveChild turn ordinary Exception into null without retaining sensitive exceptions or paths; fatal Error propagates by identity. The resolver itself propagates errors to these boundaries. Direct fault tests distinguish the attribute-read failure branch from File.toPath failures; no canonical getter stub may falsely claim to exercise NIO."
  - "A6 Migrate the complete direct root/blank/normalization/DP/error acceptance from the five original Policy test blocks. Add real Windows Junction/POSIX directory-symlink fixtures with independent target markers and real-path assertions; link creation or cleanup failures fail tests, never skip. Delete links themselves before owned target cleanup, without traversing them. Keep the small fixture helpers internal to the app test source set so the Policy successor can reuse them without copying process/cleanup logic or editing this predecessor. The current Windows environment must execute its actual branch; report other-platform execution honestly."
  - "A7 Compile-success named AssertionError mutations cover candidate/app/DP resolution, all blank/strict/containment/exclusion guards, exact saved snapshot, child anchor/return/boundary, real alias resolution, ordered missing/parent handling and ordinary/fatal failure classification. Preserve old Policy verdicts/pins and map migrated obligations to newly executed mutations; historical canonicalFile results do not verify this implementation."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: direct real-filesystem boundary tests plus all app JVM regressions and assemble pass; required host link fixtures have no skips; final-source mutations and restored DoD pass. No Android or later-I/O completion claim.
review_gate: codex {verdict:pass}
hygiene: retain independent behavioral vectors and final-source mutation evidence; no source-text or compile-error substitute for guard tests
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-STORAGE-PATH-BOUNDARY

Complete predecessor extracted before further implementation after StoragePolicy's fifth formal R3 identified an unchecked category-directory alias. A bounded JDK 17.0.20 / Windows 11 experiment proved that File.canonicalFile preserves a Junction alias while Path.toRealPath resolves its target; after retargeting, the saved canonical alias followed the new target while the saved real path remained at the original directory. This diagnostic is not product acceptance.

The predecessor owns all real path resolution, CE/DP root verification, immutable verified-path snapshots, checked-child semantics and their direct tests. Policy retains environment conversion, six namespace mappings, closed media states, redacted external exceptions and black-box integration tests proving both primitive calls happen. Do not split implementation from its missing tests or delete Policy tests before this predecessor is delivered.

API: internal StoragePathBoundary with private constructor; create(File, File, File): StoragePathBoundary?; directory: File; resolveChild(File): File?. An internal resolveStorageDirectory(File): Path helper and a narrow optional attribute-read seam may support direct tests. The core algorithm walks segments without early normalize: existing entries resolve through actual filesystem targets, missing entries remain candidates, parent segments can return to existing paths which must be probed again. No general filesystem abstraction is needed.

The guarantee is path containment at checking time, not file-handle authority or race elimination. Keep the saved comparison anchor immutable even if that pathname later becomes an alias. Support uncreated roots/children as before; do not tighten that contract to existing directories merely to use toRealPath.

Measured migration base fd1dd18c: 15 helper lines / 630 LF characters and five direct test blocks totaling 173 lines / 8,213 LF characters. Full predecessor forecast560–610 lines /31k–35k diff characters includes new resolver, native fixtures, failure seam, all tests and R4 receipts. Initial full-candidate ceiling615/36k preserves at least25% capacity within an early stop of820/48k; absolute R3 limit remains1000/60k. This single filesystem capability is assigned to GPT-6 Astra/high, with independent GPT-5.6 Sol/high formal review. Recompute before RED and stop for a new scope decision if the full candidate forecast exceeds615/36k; do not compress code or remove cases.

Existing evidence remains with the blocked Policy branch and preserved handoff; it is historical only. Build this independent card from current master with the original task-loop scripts. After actual R3/merge/R5 closure, Policy may absorb the prerequisite by a non-rewriting merge and then replace its embedded path implementation with this API, rerunning RED/regression, DoD, final-pin R4 and formal R3 for its actual successor diff.
