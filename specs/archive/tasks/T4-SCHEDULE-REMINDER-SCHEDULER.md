---
id: T4-SCHEDULE-REMINDER-SCHEDULER
title: WorkRequest 构造、注册预留与保留工作恢复
depends_on: [T4-SCHEDULE-REMINDER-DELIVERY]
status: merged
branch: T4-SCHEDULE-REMINDER-SCHEDULER
worktree: C:\wt\T4-SCHEDULE-REMINDER-SCHEDULER
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络、依赖/schema 或 UI 变更
  - 把 enqueue 结果统称为一个失败码，或在保留工作判定完成前先 enqueue
  - 用源码字符串断言、自写磁盘队列或陈旧权限快照替代行为测试
non_goals:
  - 改写 delivery 卡已冻结的身份、回执序列化、worker 或通知文案合同
  - 并发注册合流、waiter callback、单调 watchdog、迟到 callback 与权限恢复（均归 T4-SCHEDULE-REMINDER-FLIGHT）
  - 根导航与启动时权限请求
acceptance:
  - "A1 registration builds one real unique KEEP OneTimeWorkRequest per canonical occurrence carrying exact route data, the store-derived generation work request id, and an enqueue-time Clock delay rounded upward below one millisecond and clamped without overflow"
  - "A2 registration reports admission only, never reaches WorkManager before a durable receipt reservation, reserves generation zero for a fresh occurrence, and fails closed or skips exactly as specified on corrupt, write-uncertain, delivered, uncertain, terminal and quarantined evidence"
  - "A3 the retained work query decides every enqueue: all six WorkInfo states of the current work request id reconcile without false admission, foreign active or duplicate retained ids quarantine without enqueueing, an absent current id with no other active work is the only enqueue path, and a failed query is retryable"
  - "A4 confirmed, absent, reported, raised and timed-out enqueue outcomes settle as exact correlated causes without a shared ENQUEUE_FAILED label, only a fatal submission failure closes the receipt as terminal while a transient one leaves it pending, an interrupted query keeps its retryable answer and hands the interrupt back, and a lost compare-and-set is re-read once and reported truthfully as worker-proved admission, a superseded generation, a closed occurrence or contention"
  - "A5 runtime acceptance tests invoke the compiled scheduler and the production delivery runner over one shared receipt store with concrete inputs and production-used injected enqueue, query, permission and clock ports, assert only domain results and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box host JVM captures the scheduler-produced request spec and the real OneTimeWorkRequest, drives every retained-work and enqueue-outcome branch, verifies exact correlated causes and diagnostics, and records A1-A5 semantic mutations without a source-derived oracle; assembleDebug compiles the WorkManager adapter.
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；delay 取整与钳制、六种保留状态、五种 enqueue 结局与 CAS 失败重读均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，FLIGHT 卡转为 ready。
---

# T4-SCHEDULE-REMINDER-SCHEDULER

消费已合并 delivery 合同，RED-first 实现同步注册路径。真实 WorkManager adapter 使用 pinned 2.11.2 API；host JVM seam 只替换平台调用，不复制业务判断。

**本卡是拆分后的第一张**（原卡按 DEVOPS-WORKFLOW 的 diff 预算拆为两张）：本卡交付「一次注册」的完整正确系统——预留、查询恢复、构造并提交 WorkRequest、按结局落回执；并发合流、异步 callback flight 与单调 watchdog 归 `T4-SCHEDULE-REMINDER-FLIGHT`。

Scheduler 必须按 delivery 卡的冻结回执表执行。`ReminderReceiptStore` 的 causeCode 词表是封闭的且仅 `TERMINAL` 携带原因，故本卡的丰富原因码只出现在**注册结果与诊断**里，回执只写 phase。KEEP 完成只在查询到 retained UUID 与当前 generation 完全相等后才承认 admission。`PERMISSION_BLOCKED` 在本卡按 closed phase 跳过——它的恢复要读新鲜授权并铸新 generation，与承接卡的 generation 机制同源，故整体归 `T4-SCHEDULE-REMINDER-FLIGHT`。

Scheduler 与 compiled delivery runner/Worker 共用同一个 `ReminderReceiptStore`，一切跨执行者的线性化都走它的 `admit`/`compareAndSet`/`recoverPermissionBlocked` 原子结果，不另造第二把锁。所有更新都以 occurrenceId + generationNumber + expected phase compare-and-set；CAS 输时重读一次当前回执并据实回报：当前 generation 已离开 `ADMISSION_PENDING` 即 worker 已证明 admission，回执落到更高 generation 即本次注册已被取代，二者都不得被改写成失败；同代仍可调度但被别的注册改动过则记 contention（retryable），由调用方从头再协调，不在此自旋。`DELIVERED/TERMINAL/PERMISSION_BLOCKED/DELIVERY_UNCERTAIN/QUARANTINED` 才是可原样返回的 closed phase；旧 generation 永不覆盖新状态。

enqueue seam 是一次调用：提交 unique KEEP work 并交回该 operation 自己的结局。`SUCCESS` CAS `ADMISSION_PENDING → ENQUEUED` 并以 `CALLBACK_CONFIRMED_ADMISSION` 承认 admission；operation 报出的 null、失败与读取结果时抛出的 Throwable 分别保持精确 class/cause，CAS `RETRYABLE` 并以 `ENQUEUE_CALLBACK_NULL/ERROR/THROWABLE` settle，超出等待预算则以 `ENQUEUE_CALLBACK_TIMEOUT` settle 且保持 pending，**四者不得统称 `ENQUEUE_FAILED`**；只有 enqueue 调用在返回 operation 前同步抛出的明确 permanent/fatal failure 才 CAS `TERMINAL`，transient 同步失败保持 pending 且可重试。

| query 结果 | 唯一合法恢复 |
|---|---|
| 当前 UUID 恰一条 `ENQUEUED` 或 `RUNNING` | CAS receipt 为 `ENQUEUED`，承认 admission |
| 当前 UUID 恰一条 `BLOCKED` | 本卡 request 无 prerequisite，故 quarantine `RETAINED_WORK_BLOCKED`，不得承认 admission |
| 当前 UUID 恰一条 `SUCCEEDED` | 重读同 generation receipt；closed phase 原样返回，任一 active phase CAS quarantine `RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT`，竞争后仍 active 则重试协调 |
| 当前 UUID 恰一条 `FAILED` 或 `CANCELLED` | 把任一 active phase CAS `TERMINAL`，分别记录 `RETAINED_WORK_FAILED` / `RETAINED_WORK_CANCELLED`；竞争后 closed phase 可返回，仍 active 则重试协调 |
| 无当前 UUID，且无其它 active UUID | 只有此情形才以同 generation UUID enqueue；旧 generation 的 terminal history 可忽略 |
| 任一其它 UUID 为 `ENQUEUED`/`RUNNING`/`BLOCKED`，或当前 UUID 重复 | quarantine `RETAINED_WORK_ID_MISMATCH` / `RETAINED_WORK_DUPLICATE`；不得 enqueue |

每次注册恰好记录一条 typed settlement（`occurrence_id` / type / `generation_number` / cause），**绝不记录 propertyId、日期、URI 或异常文本**；无法派生 occurrence 时 occurrence 与 generation 一律缺省，不发布只对得上一半的身份。**沿用 delivery 精确 JSON 字段的渲染、`retained_work_request_id` 与失败类别 `cause_code` 一并归 `T4-SCHEDULE-REMINDER-FLIGHT`**（该卡的迟到/跨代/超时诊断正需要这两个字段），本卡只定 typed 记录与「一次注册一条」。

Runtime acceptance tests are black-box behavioral tests：测试调用 compiled scheduler entry point，并在同一 receipt store 上调用 delivery 卡的 compiled production delivery runner，以证明 scheduler 构造的 WorkRequest 正是 Worker 会接受的那一份、并制造真实 worker transition；production-used injected ports 覆盖 enqueue/query/permission/clock，只断言领域结果与记录的边界 effects。不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。A1–A5 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

测试必须覆盖 reservation 写失败零 enqueue、fresh 与 **persisted `ENQUEUED`/`RETRYABLE` 恢复**、其它 active retained-ID mismatch（含与当前 UUID **并存**时）、当前 UUID duplicate、别代已 finished 的历史不挡本代 enqueue、六种 WorkInfo 状态各自的黑盒结果与稳定 cause、把某态误标 `ENQUEUED` 的 semantic mutation、query 抛错与**被中断**、五种 enqueue 结局、fatal 与 transient 同步失败、**scheduler 自身 CAS 落库不确定**、CAS 失败后重读出 worker 证据与更高 generation，以及 delay `-1ns/0/+1ns/亚毫秒/Long.MAX` 与溢出/钳制边界。每条 settle 的 cause 同时是被记录的那一条。

## 交付记录（R5）

**merged**：master `afc0c3c2`，PR #222，**R3 五轮后经人裁合并**（CI `verify` 绿、全部确定性闸绿、
`codex-review` 为 block，理由与合并依据见 PR #222 的人裁评论）。diff 996 行 / 约 51k 字符。

### 落地形态
`ReminderScheduler.register(PendingReminder)` 一次阻塞式后台注册：预留（generation 0 的
`ADMISSION_PENDING`）→ 查询 WorkManager 在该 unique name 下**仍保留什么** → 只有「当前 UUID 无保留、
且无其它未结束保留」才提交 → 按 operation 结局落回执。请求 id 由 `reminderGenerationId(occurrence, gen)`
派生（Worker 会拿平台实际运行的 UUID 与同一派生比对，不派生就每次都在 INPUT 阶段被拒）。

**注入端口三个**：enqueue（提交并交回该 operation 自己的结局：Confirmed/Absent/Reported/Raised/TimedOut）、
query（retained work）、`java.time.Clock`；外加共用的 `ReminderReceiptStore` 与一个 typed 诊断端口。

### 不变量
- **查询决定每一次提交**：KEEP 永远不会被读成「本代的 work 已被接纳」——外来 active/重复保留一律 quarantine。
- **绝不高报 admission**：只有自身正在确立 admission 的调用点（confirm / holdForRetry）在重读到**同代
  `ENQUEUED`** 时才记 `ADMISSION_ALREADY_RECORDED`；携带 retained 反证的 quarantine/terminate 调用点不得
  由相位反推 admission（R3 第 4 轮 finding，M21 变异守住）。
- **身份整体可空**：`ReminderRegistrationIdentity` 把 occurrence 与 generation 绑成一个值，无法发布「只对得上
  一半」的身份（R3 第 3 轮维度 #12）。
- 只有**同步抛出的 permanent** 提交失败才把回执关到 `TERMINAL`；transient 与 `TimedOut` 都保持预留不动。
- delay 向上取整（永不提前交给平台）并钳制而非溢出——WorkManager 自己会拒绝会溢出其排期算术的 delay。

### 证据
16 个 JVM 黑盒测试（含把**每一种 retained 答案**都竞速到 `ENQUEUED` 的表驱动用例，证明没有一种会变成
假 admission；以及用 delivery 卡的 **compiled production runner** 跑本卡构造的真实 `OneTimeWorkRequest`，
证明 Worker 确实接受它）。**21 枚语义变异逐一击杀**，收据钉生产文件 SHA-256
`ab224f64c0dc418092616b071c5da3e6193b0dd95f79f28c7ec947681690b648`（变异前后同值），写在测试文件末尾。

### R3 五轮与人裁
五轮 block 全部命中同一个设计点——**一次 CAS 失败后本次注册可以断定什么**——而评审者在该点上先后给出四种
互不相同的立场（2：同代任何变化都只能记 contention；3：同代 `ENQUEUED` 是 admission；4：只限 confirm 路径；
5：该路径上 `RETRYABLE` 也应算）。第 2 轮与第 5 轮互为反向，按 CLAUDE.md「同一争点两轮互不认可即停、排队人裁」
转人裁合并。前四轮的 finding **全部属实且已修**（缺 persisted 恢复/并存保留/自身 CAS 不确定/被中断 query 的用例、
`place` 吞 `InterruptedException` 不还原中断位、半截身份诊断、由相位反推的假 admission）。
第 5 轮的剩余缺口是**低报**（该记 admission 处记了 contention），方向安全且自愈：调用方重试后由 retained-work
查询以证据给出 `RETAINED_WORK_ENQUEUED`。细化归 FLIGHT 卡（TD167）。

### 两次按预算拆卡（动手前 + 首轮后）
自下而上估算 1250 行 > R3 1000 行预算，故 **动手前**先拆出 `T4-SCHEDULE-REMINDER-FLIGHT`（并发合流、异步
callback flight、单调 watchdog）；实现后仍贴住上限，又把**诊断 JSON 渲染**与**权限恢复**移入该卡。教训：
L266 的「写 RED 之前先量」抓住了第一次，但没抓住第二次——量的是**产出**，没量**评审要求的覆盖面**。
