---
id: T0-CI-IDENTITY-DEADLINE
title: 候选 CI 的 run 身份绑定、jobs 漂移、单一 deadline 与最终 exact-head/base 快照
depends_on: [T0-CI-PAGED-CONTRACT]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 reviewed local SHA、PR number/head、workflow path/event/PR association、返回 run id 与最终 merge 参数逐层绑定，任一层不匹配即 fail-closed"
  - "A2 身份比较一律大小写敏感：路径、事件、PR 关联的属性名各有一条大小写变体负例，且该负例只能被身份闸拦下"
  - "A3 单一 wall-clock deadline 杀进程树，重试 sleep 只用剩余预算；超时夹具须证明 API 已启动、子进程树未留下完成哨兵，且墙钟小于配置上限加明确清理余量"
  - "A4 最终 exact-head/base 快照后才决策：review 中移动本地 HEAD、CI 中前移 base、快照阶段 retarget 或 head moved 均不触发 merge"
  - "A5 -NoAutoMerge 只跳过 merge，不放松上述任何一层；正常与 alternate identity 两条绿路使用不同 PR/run id，stub 拒绝硬编码"
status: todo
branch: T0-CI-IDENTITY-DEADLINE
worktree: C:\wt\T0-CI-IDENTITY-DEADLINE
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - 修改 ci.yml job 集或业务验收内容
  - 降低 mandatory R3、真实 diff 预算或现有本地确定性闸
  - 用固定 PR 号、run id、head SHA 或只搜错误文本的 vacuous fixture
  - 用大小写不敏感的运算符（-in / -notin / -contains / -eq）承担身份比较
non_goals:
  - 分页读取的形态、总数与 id 重放契约；由前置 T0-CI-PAGED-CONTRACT 承接，本卡不重复实现
  - receipt-loss 恢复策略；由 T0-RECEIPT-LOSS-FAIL-CLOSED 承接
  - 自动重跑、取消或修复 GitHub Actions
  - 把 scaffold-selftest.yml 放回 PR 关键路径
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -match 'gate=T37-CIGATE/WORKFLOW-BINDING reason=' -or $t -match 'gate=T37-CIGATE/JOBS-DRIFT reason=') { exit 1 }; if ($t -notmatch 'selftest: PASS') { exit 1 }; if (-not (Select-String -Path docs/DEVOPS-WORKFLOW.md -SimpleMatch 'candidate CI')) { exit 1 }
dod_exit: 0
dod_assert: seeded-remote 分片真实执行且退出 0，输出含 selftest: PASS；WORKFLOW-BINDING 与 JOBS-DRIFT 两闸均实际跑过、未被任何 reason 跳过；DEVOPS-WORKFLOW 已同步候选 CI 章节。
review_gate: codex {verdict:pass}
hygiene: 每个负例配对应正例或单点变异；大小写负例必须证明是被身份闸拦下，而非更早的通用错误。
doc_sync: DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 同步候选 CI 身份、deadline、最终 exact-head/base 快照和 NoAutoMerge 契约。
---

# T0-CI-IDENTITY-DEADLINE

## 产出

候选 CI 的**身份与时序**收口：workflow run 身份逐层绑定、jobs 漂移检测、单一 wall-clock deadline 与进程树
清理、最终 exact-head/base 快照，以及 `T37-CIGATE/WORKFLOW-BINDING` 与 `T37-CIGATE/JOBS-DRIFT` 两闸的
正反夹具；并同步两份运维文档。

## 已验证的前置工作（可直接承接）

本卡从 `T0-CI-HARDENING-MATRIX` 拆出。原卡的身份部分成果保存在分支 `wip/T0-CI-hardening-validated`
（tip `2ce7aa0f`）与 patch series `pr214-validated-work/0001..0005`，其中与本卡相关的是 PR 关联属性名的
大小写敏感修复、`workflow-path-case` / `workflow-case-pr` 两条负例，以及两份文档的候选 CI 段落。
承接时须自行重跑 DoD 与 `selftest -Shard seeded-remote`。

## 大小写敏感是本卡的硬约束（有实际缺陷背书）

PowerShell 的属性访问与 `-in` / `-notin` / `-contains` / `-eq` **默认大小写不敏感**，而 `-cne` / `-ccontains`
敏感。原卡实测踩中两处：

1. `pull_requests[]` 的关联判定读 `$_.number`，于是一个只带 `Number` 的条目照样通过 PR 关联校验——
   一个并不属于本 PR 的 workflow run 可借此过闸。修法是按 `-ccontains` 核属性名。
2. 曾有人把路径判定从 `-cne '.github/workflows/ci.yml'` 改成 `-notin @(...)` 以接受 `ci.yml@<base>` 形态；
   因 `-notin` 不敏感，`CI.yml@MASTER` 一并被接受，身份闸被静默放宽。

故本卡 `forbid` 明列：身份比较不得由大小写不敏感的运算符承担。这与仓内既有教训同源
（`-contains` 恒不敏感 / `String.StartsWith(string)` 恒敏感，见 T0-GATE-FIXFORWARD）。

## 禁止

见 front-matter `forbid`。

## 非目标（本卡刻意不做的能力）

分页形态 / 总数 / id 重放契约（前置卡已收口）、receipt-loss、Actions 自动运维，见 front-matter `non_goals`。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
$t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -match 'gate=T37-CIGATE/WORKFLOW-BINDING reason=' -or $t -match 'gate=T37-CIGATE/JOBS-DRIFT reason=') { exit 1 }; if ($t -notmatch 'selftest: PASS') { exit 1 }; if (-not (Select-String -Path docs/DEVOPS-WORKFLOW.md -SimpleMatch 'candidate CI')) { exit 1 }
```

- 期望退出码：0
- 断言：见 `dod_assert`。DoD **执行**闸门而非搜索字符串：两闸被任何 reason 跳过即判失败。
