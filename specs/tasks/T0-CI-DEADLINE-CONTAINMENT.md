---
id: T0-CI-DEADLINE-CONTAINMENT
title: 候选 CI 的单一 wall-clock deadline 扩面与 fail-closed 进程树容纳
depends_on: [T0-CI-IDENTITY-DEADLINE]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 CI 闸内**每一个**外部子进程（gh 与 git fetch/rev-parse）都在同一个 wall-clock deadline 之内启动；任何路径都不得无界等待，收尾等待只花该 deadline 的剩余时间加**一份**具名清理余量"
  - "A2 清理余量按一个绝对期限计：杀进程与收流共享其剩余时间；隔离计时用例（不经 ship，时序与前段管线无关）须能把 deadline+1×余量 与 deadline+2×余量 区分开"
  - "A3 进程树容纳 fail-closed：容纳原语不可用（Add-Type 失败 / job 创建或并入失败 / 平台无此原语）时闸必须显式失败或以同等强度的替代机制兜住，不得静默降级成只沿活父子链走的 Kill(tree)"
  - "A4 子进程**先于**孙进程退出这一形态必须被容纳：孙进程继承重定向句柄并在其后写完成哨兵的夹具，须证明哨兵永不出现；另配失败路径夹具（并入失败时）同样证明无残留"
  - "A5 assign-before-execute 竞态要么被消除（挂起创建后并入再恢复），要么由机检证明其窗口内不可能派生后代；创建成功但并入失败时句柄必须被关闭，不得泄漏"
status: todo
branch: T0-CI-DEADLINE-CONTAINMENT
worktree: C:\wt\T0-CI-DEADLINE-CONTAINMENT
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - 静默吞掉容纳原语的失败（Add-Type / CreateJobObject / AssignProcessToJobObject 任一失败都不得只记账继续）
  - 用「进程已退出就不必清理」的判据跳过整组结束——孙进程正是在那一刻仍活着
  - 把同一份清理余量串行花两遍，或用宽到分辨不出倍数的墙钟余量充当上限断言
  - 降低前置卡已落地的身份链、终局 exact-head/base 快照或 -NoAutoMerge 语义
non_goals:
  - run 身份绑定、大小写敏感身份比较、终局快照；均由前置 T0-CI-IDENTITY-DEADLINE 承接
  - API 侧 job 集漂移（T0-CI-JOBS-DRIFT）与分页契约（T0-CI-PAGED-CONTRACT）
  - 把 CI 闸移植到非 Windows 平台的完整支持；若本卡选择「非 Windows 显式拒绝」，须在文档写明并给恢复路由
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/DEADLINE OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/WORKFLOW-BINDING OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }
dod_exit: 0
dod_assert: seeded-remote 真实执行且退出 0；输出须含大小写敏感哨兵 `T37-CIGATE/DEADLINE OK`、`T37-CIGATE/WORKFLOW-BINDING OK` 与 `selftest: PASS`——前置卡那枚一并断言，证明本卡没有为了自己变绿而放松已落地的身份链。
review_gate: codex {verdict:pass}
hygiene: 每条负例配对应正例或单点变异；容纳失败路径必须有夹具，不能只靠代码读起来对。
doc_sync: DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 的候选 CI 段把 deadline 扩面与进程树容纳补回（前置卡已把该句降级为「既有 gh 侧 deadline + 指向本卡」）。
---

# T0-CI-DEADLINE-CONTAINMENT

## 产出

把候选 CI 闸的 wall-clock deadline 扩到**全部**外部子进程（gh + git），并给它一个**fail-closed 的进程树容纳**
机制，配隔离计时与失败路径夹具。

## 拆卡缘由（三轮 R3 的实质 finding 全落在这段机器上）

本卡从 `T0-CI-IDENTITY-DEADLINE` 拆出，是那张卡的**第二次**拆分（第一次拆出 `T0-CI-JOBS-DRIFT`）。
原卡的 A1/A2/A4/A5（身份链、大小写敏感、终局快照、-NoAutoMerge）三轮评审零实质 finding 并已合并；
而 deadline / 进程树这一段连吃三轮：

1. **r1**：`WaitForExit()` 无参调用无界等待（孙进程握着重定向管道即永不返回）；超时夹具分辨不出
   「一个共享预算」与「每次调用各起新超时」；git 腿完全无覆盖。
2. **r2**：子进程先退出时 `if (-not HasExited) { Kill(tree) }` 一次也不执行，孙进程被漏杀；
   一份清理余量被串行花了两遍（真实上限 deadline+2×，声明的是 1×）。
3. **r3**：容纳仍非 fail-closed——`Add-Type` 失败静默吞、job 创建/并入失败降级、非 Windows 恒降级、
   进程先启动后并入留竞态窗口、并入失败还漏关已创建的 job 句柄。

结论是这不是「再修一处」的问题，而是一个自带平台原语选型的独立子问题（Windows job object vs POSIX
进程组、挂起创建、失败注入夹具），值得独立预算与独立评审轮次。

## 承接材料

前一轮的完整实现（`Invoke-ExternalBeforeDeadline` 泛化、`Get-GitOidBeforeDeadline`、job object P/Invoke、
gh-hang / gh-orphan / gh-slow / git-leg 四类夹具、隔离计时用例）存档于会话 scratchpad
`pre-a3-split.patch`；**它带着 r3 点名的全部缺陷**，只可当作起点，不可当作结论。已知它在
`selftest: PASS` 下跑通，但那恰恰说明当时的夹具覆盖不到 r3 指出的失败路径。

## 禁止

见 front-matter `forbid`。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
$t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/DEADLINE OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/WORKFLOW-BINDING OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }
```

- 期望退出码：0
- 断言：见 `dod_assert`。DoD **执行**闸门而非搜索字符串：两闸被任何 reason 跳过即判失败。
