---
id: T4-SCHEDULE-REMINDER-RECEIPTS
title: 提醒耐久回执、损坏隔离与 generation CAS
depends_on: [T4-SCHEDULE-REMINDER-CONTRACTS]
status: merged
branch: T4-SCHEDULE-REMINDER-RECEIPTS
worktree: C:\wt\T4-SCHEDULE-REMINDER-RECEIPTS
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt
forbid:
  - WorkManager 注册、通知发布、UI、依赖、数据库 schema 或跨进程 CAS 声称
  - 把保留 sentinel/admission evidence 的损坏 store 当作全新 occurrence
  - read-back 冒充 commit durability 或持久化失败后继续外部副作用
non_goals:
  - Worker delivery、scheduler callback/flight 与 Android notification adapter
  - 检测完整 preference 文件或某 occurrence 三键同时被彻底删除
acceptance:
  - "A1 a credential-encrypted private v1 store atomically commits sentinel, immutable admitted marker, seen marker and full canonical ReminderSpec receipt for fresh generation zero only"
  - "A2 only a blank store or a valid store with all three queried occurrence keys absent is MISSING; a second occurrence stays MISSING while another complete occurrence remains present"
  - "A3 every retained partial key set, missing/invalid sentinel, malformed or noncanonical UTF-8/base64, future envelope, identity/generation mismatch, unknown cause or invalid phase/cause pair becomes typed QUARANTINED"
  - "A4 false/throwing commits become write-uncertain, poison later same-process mutations, preserve the prior durable receipt when one exists and never use read-back as durability proof"
  - "A5 one process-wide lock linearizes exact occurrence+generation+work-id+phase CAS, old generations cannot overwrite new permission recovery, and final-snapshot semantic mutations prove A1-A4"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderReceiptStoreTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box JVM tests exercise the production preference port seam, strict codec, partial-key matrix, generation recovery and CAS effects without source inspection; assembleDebug compiles the private SharedPreferences adapter.
review_gate: codex {verdict:pass}
hygiene: normal Kotlin only; no typealias, semicolon packing or line over 120 characters; final mutation receipts include selector, production effect, RED exit and identical before/after SHA-256.
doc_sync: TASK-BOARD records merge OID; archive this card and make T4-SCHEDULE-REMINDER-DELIVERY ready.
---

# T4-SCHEDULE-REMINDER-RECEIPTS

## Light Plan Forge 2/3

Consume the merged contract types and own the only durable application-private receipt protocol. The store sentinel is exactly `store=reminder-receipts/v1`; occurrence evidence uses immutable `admitted:{occurrenceId}=v1`, `seen:{occurrenceId}=v1` and `record:{occurrenceId}=strict-v1-envelope` keys.

First admission accepts generation zero only. The sole generation increment API accepts a fully validated `PERMISSION_BLOCKED` receipt, freshly derives generation `n+1`, and writes `ADMISSION_PENDING`; overflow, identity drift and every other transition fail closed. All operations share one process-wide lock. The implementation claims only in-process linearizable CAS in the default app process.

`commit()==false` or any thrown write is uncertain. The current action stops; a same-process guard prevents later mutation/notification. If a prior durable receipt exists, read-only lookup may expose it with a `writeUncertain` flag; if no prior receipt exists, lookup is quarantined. Complete deletion of all evidence remains explicitly indistinguishable from fresh app data.

Runtime acceptance tests are black-box behavioral tests. They inject the production-used preference port, assert only lookup/transition results and recorded commits, and never inspect source/resources/compiled artifacts. A1-A4 each require a production semantic mutation on the final snapshot with a named failing selector and byte-identical SHA-256 restoration.


## 交付记录（merged: master `3c08c2dd`, PR #217）

**R3 两轮 block 后达轮次上限（`ReviewRoundCap=2`），经用户人裁合并。** 两轮共 4 条 finding，逐条按卡核对
后**全部属实、全部已修**，无一条被辩掉：

1. **#9/#6（第 1 轮）** 提交失败后 `lookupLocked` 仍回读偏好文件、并信任其中任何完整记录。但
   `commit()==false` 的写入**可能已经落盘**，于是一次未确认的写入会被当作耐久回执读回来；本卡自己的
   APPLY_THEN_FAIL 测试还把这个错误行为写成了断言。按卡片原文（无先前回执即 quarantine、有则暴露
   **先前**那份）改为：失败提交把「写入前那份耐久回执」记进毒化表，被毒化的 occurrence **完全不再查文件**。
2. **#9（第 1 轮）** 毒化集是实例级的，靠「工厂 memoise 出唯一实例」这一约定维持，构造器仍是公开的。
   改为按 `ReminderPreferencePort.backingStore` **身份**（`IdentityHashMap`，避免 seed 那版 `WeakHashMap`
   的 equals 陷阱）共享，memoise 工厂随之删除——共享从此是结构性的而非约定。
3. **#12（第 1 轮，取一半）** `catch (_: Throwable)` 连 `Error` 一并吞掉，三处已收窄为 `Exception`。
   **结构化日志一半未做**：store 把失败转成**带类型的结果**交给调用方，而持有 `ReminderDiagnosticPort`
   的那一层才有 stage/generation/work id 等本文件永远看不到的上下文，两边都记会把同一次失败记两遍且这边
   更少信息；理由写进类注释。第 2 轮评审未再提出此点。
4. **#9/#6（第 2 轮）** 毒化只覆盖失败那一个 occurrence，但 `SharedPreferences.commit()` **重写整个文件**
   ——一次未确认提交之后，同文件里其它 occurrence 的内容同样不再是证据。改为整文件毒化：任何 occurrence 的
   admit/CAS/recover 一律 `WriteUncertain`，lookup 仅暴露本进程见过耐久的那份，其余 quarantine。

**种子审计**：继承的 613 行 seed 通过了它自己的 DoD，仍藏 6 个真缺陷（generation 非零可当首次准入 · 解码
非规范可延展 · cause 无封闭词汇表故 A3 的「未知 cause / 非法 phase-cause 对」根本没有实现 · recovery 收
调用方构造的 next 使身份漂移可表达 · 毒化表 equals 键 · quarantine 原因无类型），全部在本卡修掉。

**R4 变异证据**：48 枚单点语义变异，覆盖本文件每个函数，逐一施加于终态快照、跑测试期望非零退出、再还原并
复核哈希。**48/48 全部击杀，无幸存**。生产文件变异前后同一 SHA-256 `3d243ad1c5d7949897e7d6e67c754d4ccf2b8c41e815ded74eace892d2620785`。
> 其中 **M49 首轮幸存**且最要紧：它把整文件毒化改回第 2 轮刚被驳的「按 occurrence 毒化」，而当时所有跨
> occurrence 断言都走写入侧的守卫、没有一条经由被变异的**读取**路径，故全绿。补一条「B 已耐久 → A 提交
> 失败 → B 不再是证据」的测试后击杀。

收据表（下表是本卡的 `hygiene` 记录：**diff 已顶到 R3 的 1000 行硬上限**，无处安放，故记在卡内而非测试文件）：

| id | criterion | production effect | red test | exit |
|---|---|---|---|---|
| M01 | A1 | admit drops the generation-zero gate | only a fresh generation zero receipt is admissible | 1 |
| M02 | A1 | admit drops the admission-pending phase gate | only a fresh generation zero receipt is admissible | 1 |
| M03 | A1 | admit stops validating the receipt | only a fresh generation zero receipt is admissible | 1 |
| M04 | A1 | admit stops requiring the occurrence to be missing | admission is refused whenever any evidence for the occurrence survives | 1 |
| M05 | A1 | admission commit omits the seen marker | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M06 | A1 | admission commit omits the store sentinel | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M07 | A4 | admission reports success on a failed commit | a false or throwing first admission leaves no receipt and quarantines the occurrence | 1 |
| M08 | A4 | compareAndSet reports an uncertain file as a plain rejection | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M09 | A5 | compareAndSet stops comparing the work request id | compare and set advances only on the exact expected tuple | 1 |
| M10 | A5 | compareAndSet stops comparing the generation | compare and set advances only on the exact expected tuple | 1 |
| M11 | A5 | compareAndSet stops comparing the phase | compare and set advances only on the exact expected tuple | 1 |
| M12 | A5 | compareAndSet stops consulting the transition table | only the declared transitions and their own causes advance a receipt | 1 |
| M13 | A5 | compareAndSet stops validating the next receipt | only the declared transitions and their own causes advance a receipt | 1 |
| M14 | A5 | compareAndSet calls an untrusted lookup stale rather than rejected | a compare and set without a trusted receipt is rejected | 1 |
| M15 | A5 | recovery stops matching the caller receipt against the store | an old generation cannot overwrite the recovered generation | 1 |
| M16 | A5 | recovery stops requiring the permission-blocked phase | recovery outside a matched permission blocked receipt fails closed | 1 |
| M17 | A5 | recovery drops the generation overflow guard | recovery at the maximum generation is rejected instead of wrapping | 1 |
| M18 | A4 | recovery reports an uncertain file as a plain rejection | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M19 | A5 | recovery reuses the current generation instead of deriving the next | an old generation cannot overwrite the recovered generation | 1 |
| M20 | A4 | a transition reports success on a failed commit | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M21 | A5 | operations stop taking the process lock | the process lock linearises concurrent compare and set attempts | 1 |
| M22 | A3 | lookup accepts an occurrence id that is not a digest | an unreadable occurrence id or preference file is quarantined | 1 |
| M23 | A3 | an unreadable preference file reads as an empty one | an unreadable occurrence id or preference file is quarantined | 1 |
| M24 | A4 | lookup consults the file again after an unconfirmed write | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M25 | A2 | a blank store no longer short circuits to absent | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M26 | A3 | lookup stops checking the store sentinel | corrupt and non canonical evidence is quarantined with a typed reason | 1 |
| M27 | A3 | lookup stops checking both occurrence markers | a compare and set without a trusted receipt is rejected | 1 |
| M28 | A3 | lookup stops binding the record to the queried occurrence | corrupt and non canonical evidence is quarantined with a typed reason | 1 |
| M29 | A3 | lookup stops validating the decoded receipt | corrupt and non canonical evidence is quarantined with a typed reason | 1 |
| M31 | A4 | a failed commit stops poisoning the occurrence | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M32 | A4 | a throwing commit is treated as a successful one | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M33 | A3 | receipt validation stops checking the phase and cause pair | corrupt and non canonical evidence is quarantined with a typed reason | 1 |
| M34 | A3 | receipt validation stops binding the spec to the occurrence id | only a fresh generation zero receipt is admissible | 1 |
| M35 | A1 | receipt validation stops requiring a canonical spec | only a fresh generation zero receipt is admissible | 1 |
| M36 | A3 | receipt validation stops deriving the work request id | only a fresh generation zero receipt is admissible | 1 |
| M37 | A1 | receipt validation drops the non-negative generation guard | only a fresh generation zero receipt is admissible | 1 |
| M38 | A1 | receipt validation drops the occurrence id shape guard | only a fresh generation zero receipt is admissible | 1 |
| M39 | A5 | delivery uncertainty can no longer resolve to delivered | only the declared transitions and their own causes advance a receipt | 1 |
| M40 | A5 | terminal phases stop being terminal | only the declared transitions and their own causes advance a receipt | 1 |
| M41 | A1 | encode stamps a different envelope | admission commits the sentinel both markers and the canonical record together | 1 |
| M42 | A3 | encode emits padded base64 | admission commits the sentinel both markers and the canonical record together | 1 |
| M43 | A3 | decode drops the canonical round trip comparison | corrupt and non canonical evidence is quarantined with a typed reason | 1 |
| M44 | A4 | an unconfirmed first write reads as fresh app data | a false or throwing first admission leaves no receipt and quarantines the occurrence | 1 |
| M45 | A4 | a preserved prior receipt stops being flagged uncertain | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M46 | A4 | the poisoned occurrence remembers the attempted write, not the prior receipt | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M47 | A4 | poison is kept per store object instead of per backing file | write uncertainty poisons every store over the same backing file | 1 |
| M48 | A4 | admission reports an uncertain file as a plain rejection | a failed transition preserves the prior receipt and poisons later mutations | 1 |
| M49 | A4 | uncertainty is scoped to one occurrence instead of the whole file | an unconfirmed commit stops the whole file being evidence, not just its own occurrence | 1 |
