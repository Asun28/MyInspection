---
id: T3-REPORT-CONTENT-ADAPTER
title: Adapt shared semantic report content into the existing A4 layout plan
depends_on: [T3-REPORT-CONTENT-CONTRACT]
parallelizable_with: []
status: merged
branch: T3-REPORT-CONTENT-ADAPTER
worktree: C:\wt\T3-REPORT-CONTENT-ADAPTER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportComposer.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportModel.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportContentAdapter.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/DocumentPlan.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/
forbid:
  - Audience or privacy filtering after ReportContent, HTML or PDF bytes, Android imports, schema changes, or geometry owned by a renderer
  - Recomputing native data hash from a filtered snapshot or relabelling provenance as native integrity
non_goals:
  - DOCX import, HTML serialization, PDF drawing, export receipts, navigation, or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 ReportComposer consumes only ReportContent semantics through the adapter and no longer owns audience or privacy decisions"
  - "A2 the existing fixed report fixture retains its exact page count, block sequence, geometry, full native data hash, disclaimer, and photo back-references"
  - "A3 summary and provenance blocks come from the shared content in stable order and remain separately labelled"
  - "A4 tenant and landlord layout plans cannot contain a field absent from their input ReportContent"
  - "A5 all existing A1 through A18 report-composer guards remain green without weakening literal expectations"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: hardened composer suite proves semantic adapter ownership while preserving every existing golden layout and integrity invariant
review_gate: codex {verdict:pass}
hygiene: no existing behaviour test is deleted unless a stronger real-output assertion kills the same mutation
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-CONTENT-ADAPTER

## Deliverable

Move semantic projection upstream and keep the mature A4 paginator focused on layout. This is the compatibility bridge that lets the PDF renderer consume the same reviewed content as HTML without changing report meaning.

## 交付记录（R5）

**merged** 2026-09-02，master `4ec952df`，PR #225，R3 pass 于第 **2** 轮。

- `ReportComposer` 的排版入口收为 `compose(content: ReportContent)`——签名里**没有** `Audience`、也没有
  `ReportOptions`，于是「受众/隐私在排版层被再判一次」不是被规则禁止，而是**写不出来**。快照入口保留，
  经 `ReportContentAdapter` 投影后转调同一个方法。
- 页脚哈希改读 `content.nativeIntegrity.dataHash`，不再由过滤后的快照重算（卡片 forbid 第二条）。
- composer 与 projector 重复的 `validateProjection` 整段删除，单一权威归 projector。随之上移的
  「渲染时刻必须为正」判据，其用例断言改为 projector 的确切文案——守卫未删也未弱化，**M16 变异**
  （把该 require 放宽成 `> -1`）证明它仍然活着且仍被本套件捕获。
- 新增 `ProvenanceBlock` 与独立标注的 provenance 分节，仅 `importProvenance` 非空时发出；原生报告的
  页数、块序、几何与黄金布局逐字不变（既有 46 个 composer 测试全绿，无一条断言被放宽）。
- **allow_paths 曾扩一次（用户裁定）**：A3 的 provenance block 是 `DocumentBlock` 密封层级成员，须与
  12 个同级块同文件，故 `DocumentPlan.kt` 于开卡前并入 allow_paths（master `e237545a`）。

**R4**：17 枚单点变异全部 KILLED，每枚还原后核 SHA-256 与批次基线一致；收据表在
`ReportContentAdapterTest.kt` 末尾。**M4 首轮存活**并暴露一处真实覆盖缺口——套件里每个 photo-only room
夹具都只有一张照片，因而无人能区分「房间开场照被取出后续跑」与「没取」；补了双照片夹具与顺序断言，
并顺带加 M15 覆盖有 items 那条分支的同一处剪裁。

**R3 第 1 轮 block 一条**（直接入口 `compose(content)` 对「既无 items 又无照片的房间」无守卫）：该状态
**不可表达**——`ReportContent` 构造器私有、唯一出生点是 projector，而 projector 正是在那里把这种房间丢掉的。
按评审者给出的备选（「若构造期已强制，请给出证明该状态不可表达的测试」）补测试而非补一道任何变异都杀不掉的
死守卫，并以 **M17**（让 projector 不再丢弃该房间）证明该测试确实会红。
