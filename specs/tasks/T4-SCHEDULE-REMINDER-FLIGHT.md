---
id: T4-SCHEDULE-REMINDER-FLIGHT
title: 注册合流、异步 callback flight 与单调 watchdog
depends_on: [T4-SCHEDULE-REMINDER-SCHEDULER]
status: todo
branch: T4-SCHEDULE-REMINDER-FLIGHT
worktree: C:\wt\T4-SCHEDULE-REMINDER-FLIGHT
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络、依赖/schema 或 UI 变更
  - 把 callback 异常重标为 enqueue 失败，或因一个 waiter 抛错而饿死其余 waiter
  - watchdog 二次 enqueue，或在 deadline 未真正到达时就 settle
non_goals:
  - 改写 delivery 卡已冻结的身份、回执序列化、worker 或通知文案合同
  - 重做 scheduler 卡已合并的 WorkRequest 构造与保留工作查询表
  - 根导航与启动时权限请求
  - 权限恢复、诊断 JSON 渲染与 TD167 definitive-admission recovery（全部归 T4-SCHEDULE-REMINDER-RECOVERY）
acceptance:
  - "A1 concurrent registrations of one occurrence coalesce into a single reservation, query and enqueue, and every waiter of that flight settles exactly once with the same exact cause"
  - "A2 the enqueue seam becomes asynchronous: registration returns after submission, the operation callback settles the flight with its exact class and cause, and a waiter that throws Throwable never starves the remaining waiters or the diagnostic"
  - "A3 a matching worker that leaves ADMISSION_PENDING before the callback settles every waiter as admitted, later callback failures record ENQUEUE_CALLBACK_AFTER_WORKER_STARTED with their original class without downgrading a proved admission, and a callback whose generation the receipt has already moved past writes no receipt at all: while its flight is still the active one it settles that flight own waiters as superseded and clears the flight so the next registration re-coordinates, and once that flight is no longer the active one it changes neither waiter nor receipt and is recorded as a late arrival"
  - "A4 one monotonic 30 second watchdog per flight settles all waiters as a retryable timeout only while this generation own receipt still sits exactly where the flight submitted it and no worker has moved it, reports an unreadable, write uncertain or superseded receipt under that receipt own cause instead of a timeout, writes no receipt at all, clears the flight, never enqueues a second time, and reschedules rather than settling when the monotonic deadline has not actually passed"
  - "A5 a callback arriving after settlement changes no waiter or receipt yet is still recorded exactly, and fatal enqueue failures clear the active flight so the next registration re-coordinates instead of joining a dead one"
  - "A6 runtime acceptance tests invoke the compiled scheduler and the production delivery runner over one shared receipt store and scheduler-owned flight with concrete inputs and production-used injected enqueue, query, deadline and clock ports, assert only domain results and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box host JVM drives coalesced registrations,每种 callback 结局、worker-before-callback、迟到与跨代 callback 及 watchdog 提前/到期两态，asserts waiter results, receipt phases and recorded effects only, and records A1-A5 semantic mutations without a source-derived oracle; assembleDebug compiles the WorkManager adapter.
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；callback 的同线程同步、返回后异步、活动期跨线程、waiter 抛错与 watchdog 两态均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，T4-SCHEDULE-REMINDER-RECOVERY 转为 ready。
---

# T4-SCHEDULE-REMINDER-FLIGHT

承接 `T4-SCHEDULE-REMINDER-SCHEDULER` 已合并的同步注册路径，把 enqueue seam 改成**异步 callback**并补上并发边界。查询恢复表、WorkRequest 构造与回执写法均已冻结在上一张卡里，本卡只改「谁在等、谁来 settle、什么时候超时」。

## 拆分依据（动手前按预算拆，L266/L276）

上一张卡自身已顶到 996 行 / 51k 字符，并按 R3 预算把三项留给本卡。开工前按「一条验收一个黑盒用例」估本卡体量（14 个用例、约 700 行测试增量 + 250 行产线改动 ≈ **950 行 / 66k 字符**），**超出 `review.ps1` 的 60000 字符硬闸**——该闸 fail-closed 且只许收紧，顶破即评审者根本不会被唤起。故本卡再拆一次，用户裁定按二分执行：

- **本卡**保留 A1–A5 的 flight 机制（异步 callback seam、合流、worker-before-callback 优先、单调 watchdog、迟到/跨代 callback、fatal 清 flight）——恰是上面那句「谁在等、谁来 settle、什么时候超时」。
- **承接卡 `T4-SCHEDULE-REMINDER-RECOVERY`** 拿走三项继承项：权限恢复、诊断 JSON 渲染（含 `retained_work_request_id` 与失败类别 `cause_code`）、TD167 definitive-admission recovery 与有界重读耗尽。三者都落在 `coordinate()` 的 PERMISSION_BLOCKED 分支、诊断渲染与 `reread()` 上，与 flight 协调器互不重叠，故可串行两卡各自成立。

顺序不可反：诊断渲染要渲染的正是本卡产出的 settlement（含 `ENQUEUE_CALLBACK_AFTER_WORKER_STARTED` 这类新 cause），故 flight 先落地、渲染后接。

## 合流与 waiter 隔离

`register` 提交后即返回，结果经 waiter callback 交付。同一 occurrence 的并发注册**合流**到一个 flight：只做一次 reservation、一次 query、一次 enqueue，全部 waiter 拿到同一个 cause。waiter 在 flight 之外被调用（settle 时先在锁内取快照并清 flight，再逐个 invoke），任一 waiter 抛出的 Throwable 都被隔离，不影响其余 waiter 与诊断——这条边界必须是**结构性**的，不是调用方需要记得的约定。

合流之后，同一 occurrence 的并发注册不再在 store 上相撞（后来者是 waiter，不是第二个 CAS），故 `RECEIPT_CONTENDED` 这条竞争路径的可证伪性由 **worker 与 callback 的真实竞速**（A3）承担，而不再由并发注册用例承担。

## Callback 与 watchdog 的线性化

Callback 与 watchdog 都在 store 的原子结果上线性化：仍 pending 的 `SUCCESS` CAS `phase=ENQUEUED` 并以 `CALLBACK_CONFIRMED_ADMISSION` settle 全 waiter；仍 pending 的 null、operation 报错或读取结果时抛出的 Throwable 保持精确 class/cause，CAS `RETRYABLE` 并以 `ENQUEUE_CALLBACK_NULL/ERROR/THROWABLE` settle，不得统称 `ENQUEUE_FAILED`。

Matching worker 若先看到 `ADMISSION_PENDING`，以其 `actualId == generationId` 证明 WorkManager 已 admission 并自行 CAS `ENQUEUED` 后再 delivery；随后 callback success、callback Throwable/null/error 或 watchdog 都重读回执：若当前 generation 已离开 pending，全部 waiter 得 `ADMITTED`（`WORKER_CONFIRMED_ADMISSION`）并清 flight，callback 异常只以原始 class/cause 记 `ENQUEUE_CALLBACK_AFTER_WORKER_STARTED`，**不得把已证实的 admission 改成失败**。Worker、callback、watchdog 三者第一个成功的 transition 决定 flight；已经 settle 后的迟到 callback 不改 waiter/receipt，仍须记录精确 late 诊断。

单调 30 秒 watchdog 每个 flight 一枚：唤醒时先用**同一个单调源**核对 deadline 是否真的到达，未到即按剩余量重新排期而不 settle；到达且回执仍为 `ADMISSION_PENDING`、无 worker proof 时，才以 `ENQUEUE_CALLBACK_TIMEOUT/RETRYABLE_FAILURE` 结束全部 waiter、清 flight 并保持 pending，本次不得再次 enqueue。deadline 与其核对必须来自同一个注入的时间源，否则排期与判定会各按一把钟走。

## A4 的措辞修订（R3 第 1 轮）

首轮 R3 正确指出 `expire` 把「未被证明」一律折成 timeout，于是 lookup 不可读、write-uncertain 与**跨代**三种读数都会被报成可重试超时——其中跨代与 callback 路径（`proved`）对同一情形的判定直接矛盾。三处均属实，已修为逐读数穷尽分类。

同时把 A4 原文的「still ADMISSION_PENDING」改为「still sits exactly where the flight submitted it」：`place` 会从已 `ENQUEUED`/`RETRYABLE` 的回执**恢复**并重新提交（SCHEDULER 卡已合并并冻结的路径，且本卡 `non_goals` 明令不得重做），故 watchdog 合法地会遇到非 pending 的起点。原措辞假定 flight 总是从新预留出发，过窄；修订后的措辞**更严**——它额外点名了不可读/不确定/跨代三种必须各报其因的读数。

## A3 的措辞修订（R3 第 3 轮 · 用户裁定）

R3 第 3 轮正确指出**测试缺口**：原「跨代 callback」用例先让 watchdog 清掉 flight 再 supersede，于是只测到
「flight 已非活动」这条守卫，从未覆盖「flight 仍活动、回执却已跨代」。该用例已改为直接覆盖后者。

但评审同时要求该路径「change no waiter」，用户裁定**不采纳**，理由两条：① 让 callback 变成纯 no-op 只是把
同一个 `GENERATION_SUPERSEDED` 结论推迟给 30 秒后的 watchdog（`expire` 对跨代给出的正是同一个 cause），
waiter 白等一轮超时；② 已合并的 SCHEDULER 卡在同一情形下由 `reread` **同步**返回 `GENERATION_SUPERSEDED`，
而本卡 `non_goals` 明令不得重做那张表——异步路径若改成 no-op，两条路径对同一事实会给出不同答案。

故 A3 改为写明真实合同：跨代 callback **一律不写回执**；flight 仍活动时结算它自己的 waiter 并清空 flight
（下一次注册遂在新 generation 上重新协调），flight 已不活动时则什么都不改、只记 late。这比原措辞更具体，
且把「不写回执」这条真正的不变量单独拎了出来。

## R4 语义变异收据（15/15 全杀）

每一枚**单独**施加到 `ReminderScheduler.kt` 的 SHA-256
`b118a0d349c5ea794f720281a7a3e3d83cd2e996a2b280c82e823b5037e1d86e`，跑本卡 test 任务后还原并重新哈希得**同一值**
（`FINAL-SHA … baseline-match=True`）。判为 kill 的条件是 exit 1 **且**日志中无编译诊断——排除「变异根本没跑」的假杀；
每行的具名失败用例即该变异的专属判据。收据落在卡内而非测试文件，因 PR diff 已逼近 R3 的 60000 字符硬闸（同 RECEIPTS 卡先例）。

| id | 验收 | 变异（生产语义） | 击杀它的用例 |
|---|---|---|---|
| M01 | A1 | 从不加入已存在的 flight | concurrent registrations … exactly once |
| M02 | A1 | 只 settle flight 的第一个 waiter | concurrent registrations …；waiter that throws |
| M03 | A2 | 把平台报错读成「操作不存在」 | each enqueue callback outcome settles under its own cause |
| M04 | A2 | 不再隔离抛错的 waiter | a waiter that throws starves neither … |
| M05 | A2 | 提交失败后仍留着 flight | a fatal submission …, and both clear the flight |
| M06 | A3 | 把已证实的 admission 降级成 callback 失败 | a worker that proved admission is never downgraded … |
| M07 | A3 | 把跨代回执读成本代 | a registration overtaken by a newer generation … |
| M08 | A3 | 允许任意 flight 去 settle 当前 flight | a callback naming a superseded generation …；late callback |
| M09 | A4 | deadline 未到也 settle | a watchdog that wakes early reschedules … |
| M10 | A4 | 重排整轮 watchdog 而非剩余量 | a watchdog that wakes early reschedules … |
| M11 | A4 | 把未变动的回执读成 worker proof | expired watchdog（两个用例）；superseded/late callback |
| M12 | A5 | 丢掉迟到 callback 的诊断 | a callback that arrives after the flight settled … |
| M13 | A4 | 对读不出的证据报 timeout | an expired watchdog reports unreadable, uncertain and superseded … |
| M14 | A4 | 对跨代回执报 timeout | an expired watchdog reports unreadable, uncertain and superseded … |
| M15 | A4 | 相信 store 从未确认的写入 | an expired watchdog reports unreadable, uncertain and superseded … |

R4 剪枝：`registration returns once the submission is accepted` 一条被删——15 枚变异无一只被它杀掉，而它断言的「提交已记录、waiter 尚未被回答」已由并发用例在更强的前提下断言（8 个注册全部返回后 `settled` 仍为空且恰有一次提交）。R3 第 2 轮的两条 finding 均属实并已修：waiter 断言改为**逐 waiter 计数**（原先只比总数，重复调用一个而饿死另一个照样通过），并让 callback 由**另一条线程**在 flight 活动期作答，补上 `hygiene` 早已声明却未覆盖的「活动期跨线程」。

M13–M15 是 R3 首轮 finding 的专属反证：三者各自只被新增的那条用例杀掉，故该用例确实在测「逐读数分类」这件事本身。

## 测试纪律

Runtime acceptance tests are black-box behavioral tests：测试调用 compiled scheduler entry point，并在同一 fake receipt store/flight 上调用 delivery 卡的 compiled production delivery runner 来制造真实 worker-admission transition；production-used injected ports 覆盖 enqueue/query/deadline/clock，只断言领域结果与记录的边界 effects。不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。A1–A5 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

测试必须覆盖并发注册只 enqueue 一次且全 waiter 同 cause、waiter 抛 Throwable 后其余 waiter 与诊断仍完成、四种 callback 结局、worker-before-callback 的 success/error/Throwable/null/watchdog 五种 settle、迟到 callback、跨代 callback、watchdog 提前唤醒重排与到期 settle、watchdog 后 flight 已清故下一次 register 重新协调、以及 fatal enqueue 失败清 flight。
