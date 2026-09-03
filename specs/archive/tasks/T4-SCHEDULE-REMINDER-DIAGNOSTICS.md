---
id: T4-SCHEDULE-REMINDER-DIAGNOSTICS
title: 注册诊断渲染：delivery 字段词汇、真实失败类别与原子身份
depends_on: [T4-SCHEDULE-REMINDER-RECOVERY]
status: merged
branch: T4-SCHEDULE-REMINDER-DIAGNOSTICS
worktree: C:\wt\T4-SCHEDULE-REMINDER-DIAGNOSTICS
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt
forbid:
  - 新依赖、运行期网络、schema 或 UI 变更
  - 在注册诊断里发布 property/date/path/异常文本，或发布关联不上的 occurrence/generation/work id
  - 改写 RECOVERY 卡已合并的恢复循环、重读判定策略、late callback 分类与 waiter 隔离
non_goals:
  - 重做 delivery 卡已冻结的 `reminderLogMessage`（它在 allow_paths 之外，只可复用其词汇与判据形态）
  - 注册侧行为变更：本卡只决定「已记录的 record 如何发布」，不改任何 record 的产生条件
acceptance:
  - "A1 registration diagnostics render in the delivery card's exact JSON field vocabulary, keeping error_code and cause_code as two distinct fields: error_code names this registration's own closed vocabulary answer, cause_code names the shared failure class, and a cause that carried a real Throwable classifies that Throwable rather than its own class of answer"
  - "A2 identity is validated atomically: an occurrence that is not the irreversible digest shape or a negative generation nulls BOTH halves and every id derived from them, because half an identity correlates with nothing"
  - "A3 the conflicting retained_work_request_id is published only when it correlates with a whole identity and is not this registration's own work request id, and is dropped rather than published otherwise"
  - "A4 runtime acceptance tests drive the compiled scheduler over production-used injected ports, assert exact rendered diagnostic strings written out as literals rather than rebuilt from the object under test, and carry executable semantic mutation receipts for A1-A3 with identical before/after production SHA-256 plus a separate compile-only probe"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box host JVM asserts每个渲染字段在关联成立与不成立两侧的逐字精确输出、error_code 与 cause_code 两个字段各自的词汇、以及携带真实 Throwable 的 cause 取到的失败类别；mutation 收据覆盖 A1-A3 且不以源码文本为 oracle；assembleDebug 编译。
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；两个字段的词汇、原子身份的两侧、retained id 的两条丢弃判据均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档。
---

# T4-SCHEDULE-REMINDER-DIAGNOSTICS

承接 `T4-SCHEDULE-REMINDER-RECOVERY`，只做一件事：把该卡已经**记录下来**的
`ReminderRegistrationRecord` 按 delivery 卡的字段词汇**发布**出去。

## 拆分依据（RECOVERY 卡 R3 首轮后，用户裁定）

RECOVERY 的 R3 首轮出 4 条 finding，全部属实。其中两条**只落在渲染器**上：

1. **`cause_code` 被挪用**：卡片 A2 写的是「失败类别 cause_code」，即 delivery 侧的
   `FailureCauseCode`（io / security / illegal-state / invalid-input / unknown）；原实现把
   **注册 cause** 放进了该字段，并整个丢掉了 `error_code`。于是一个 `IOException` 的 callback
   记成 `cause_code=enqueue-callback-error` 而非 `io`——真实失败类别在 query / submission /
   callback / receipt / permission / waiter 六条路径上一律丢失。
2. **身份两半各自校验**：occurrence 不合形状时仍发布 `generation_number:0`，generation 为负时
   仍发布 occurrence 摘要。这与 `ReminderRegistrationIdentity` 自己的不变量（「半个身份与任何东西
   都关联不上」）直接冲突，且**原测试把这个泄漏写成了期望值**——期望值是照着实现写的，不是照着
   契约写的（L165 的典型踩法）。

修掉这两条需要把真实 `Throwable` 的分类从 signal 一路穿到 record，连同 `error_code` 一起渲染并
补失败类别测试，估算 +3.9k 字符；与 RECOVERY 卡自己必须落地的确定性修复与变异收据相加达约 64.3k，
**超出 `review.ps1` 的 60000 字符硬闸**（fail-closed，顶破即评审者不会被唤起）。按
`docs/DEVOPS-WORKFLOW.md`「超限必须拆卡，不得放宽限额」，用户裁定把渲染整体移入本卡。

**接缝**：RECOVERY 决定「什么被记录」（record 的字段与产生条件），本卡决定「它如何被发布」。
两卡 `allow_paths` 相同、故**严格串行**，RECOVERY 合并后本卡才开工。

## 落点

RECOVERY 合并后，`AndroidReminderSchedulerDiagnosticPort` 停在 `record.toString()`；
`ReminderRegistrationRecord` 已带 `retainedWorkRequestId` 与 `note` 两个字段但无人渲染。
本卡加回 `reminderRegistrationMessage` 及其 stage / 词汇拼写辅助，并按上面两条 finding 落地。

## 字段合同

| 字段 | 取值 | 判据 |
|---|---|---|
| `event` / `stage` / `type` | 同 delivery 卡 | stage 取 `LogStage` 的既有值，不新造 |
| `occurrence_id` / `generation_number` | 原子校验，任一不成立则**两者皆 null** | 摘要形状 + 非负 |
| `work_request_id` | 由上面两者派生，不收调用方拼写 | 身份为空即 null |
| `retained_work_request_id` | 本次结算撞上的**别人的** work id | 身份为空、或等于自己的 work id 即丢弃 |
| `error_code` | 注册侧自己的封闭词汇（`ReminderRegistrationCause`） | 恒有值 |
| `cause_code` | **共享失败类别**（`FailureCauseCode`）；无失败即 null | 携带 Throwable 的 cause 取该 Throwable 的分类 |
| `callback_cause_code` / `note` | 本链诊断所需的两项补充 | 无即 null |

property / date / path 与异常文本永不出现——`ReminderRegistrationRecord` 本就不带它们，
故这条由类型保证而非由渲染器过滤。

## 测试纪律

同 RECOVERY 卡：黑盒驱动 compiled scheduler，只断言领域结果、渲染出的诊断字符串与记录的边界
effects；期望字符串**写成字面量**，不由被测对象回拼（L165/L281）。A1–A3 各至少一枚 production
semantic mutation 必须让具名测试变红，收据钉生产文件的 SHA-256（变异前后同值），并另跑一遍
**只编译**的探针逐枚要求 exit 0，两条结论分开写（L282）。

## R5 交付记录

**merged** 2026-09-03，master `e7adb439`，PR #233，R3 第 **3** 轮 pass（零 finding）。
40 个 JVM 测试、**26 枚单点语义变异逐一击杀**、26 枚只编译探针全 exit 0，收据钉生产文件
SHA-256 `bd643c0588353e05e40a1d7884cb8a18e4a5c66dd2bddce9310f173d96b89818`（变异前后同值）。
630 changed lines / 52190 字符，R3 预算闸内。

### 落地形状

`error_code` 与 `cause_code` 是两个字段答两个问题：前者是注册侧自己的封闭词汇
（`ReminderRegistrationCause`），后者是与 delivery 共享的 `FailureCauseCode`。携带真实 Throwable
的结算发布该 Throwable 的分类，故**同一个 error_code 可渲染出两种 cause_code**——`IOException`
的 callback 是 `io`，`SecurityException` 的 callback 是 `security`。为此把 `causeClass` 从
signal 经 `Settlement` / `advance` / `proved` / `Flight.record` / `lateRecord` 一路穿到 record。

身份**作为一个值**判定：occurrence 不是摘要形状、或 generation 为负，则两半与其派生 id **皆 null**。
这两个条件恰是 `reminderGenerationId` 自己的前置条件，故渲染器拒绝发布的身份也永远不会被拿去派生。
`retained_work_request_id` 只在身份完整、且不等于本次注册自己的 work id 时发布。

`occurrence_id` 缺失时渲染 `null` 而非 delivery 的 `"missing"`——依本卡字段合同表与 A2 的
「两者皆 null」，此处刻意不抄 delivery 的拼写。

### R3 三轮，两条真 finding，都落在同一类错误上

① **waiter 路径未修**：`publish` 仍吞掉 waiter 的 Throwable，成功注册的 waiter 抛错后
`cause_code` 发布成 null。我按 A1 字面「a **cause** that carried a real Throwable」把 waiter
排除在外，但**拆分依据枚举的六条路径里 waiter 就是其中之一**——卡片理由里的枚举是清单，不是散文。
修法：该记录发布 waiter 自己的分类，结算记录先前已带自己的分类发出，两条记录各自命名各自的失败。

② **分类随竞速顺序漂移**：`Absent` callback 自己结算注册时渲染 `unknown`，但同一个 callback
在 worker 已证明 admission 后到达时渲染 `null`——因为 `reportedClass()` 只答真实 Throwable，
而记录随后被归到 admission 名下，admission 不是失败、不带分类。修法：`reportedClass()` 对每个
signal 都作答（抛出的取抛出的分类，没抛的取其**答案自身**的分类），分类随 callback 旅行，
而不是从它最终被归入的答案反推。

第 3 轮零 finding。**轮次上限经用户裁定 `ResetRounds`**：两轮提的是互不相同的真缺陷、各自被接受
并修复、各自带来新的击杀变异，不属该上限要止住的「同一争点拉锯」（同
`T3-REPORT-HTML-CHARACTER-POLICY` 先例）；`ResetRounds` 只清计数、不跳过评审。
