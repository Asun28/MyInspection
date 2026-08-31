---
id: T0-CI-PAGED-CONTRACT
title: 候选 CI 分页读取的形态、总数、稳定身份与跨页重放契约
depends_on: [T0-CI-MERGE-GATE, T0-CI-HARDENING-SPLIT-PLAN]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 三个分页 endpoint（check-runs / workflow-runs / jobs）共用同一读取函数，数组形态、严格 total_count、有效分页与 target-reaching 失败各有命中实际 endpoint 哨兵的正反夹具"
  - "A2 每个分页条目必须带正整数 id；缺 id、null、字符串 \"11\"、小数 11.0、0、负数、超 Int64 与非对象条目一律 fail-closed，且不得进入 items 累积"
  - "A3 跨页重放与同页重复的 id 一律拒绝：重放夹具须证明 total_count 被凑满却仍不 merge（merge-reached 哨兵缺席），而非仅非零退出"
  - "A4 删除 $seen.Add 这一条语句后，A3 的重放夹具必须变红；删除 id 整数校验后 A2 必须变红"
  - "A5 仓内既有 mock 的 check-runs / jobs 载荷须与真实 API 形态一致（带 id），不得靠放宽生产侧校验迁就不忠实的夹具"
status: todo
branch: T0-CI-PAGED-CONTRACT
worktree: C:\wt\T0-CI-PAGED-CONTRACT
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
forbid:
  - 修改 ci.yml job 集或业务验收内容
  - 降低 mandatory R3、真实 diff 预算或现有本地确定性闸
  - 用固定 PR 号、run id、head SHA 或只搜错误文本的 vacuous fixture
  - 为迁就夹具而放宽生产侧的 id / 形态校验
non_goals:
  - workflow run 身份绑定、jobs 漂移、deadline 与最终 exact-head/base 快照；由后继 T0-CI-IDENTITY-DEADLINE 承接
  - receipt-loss 恢复策略；由 T0-RECEIPT-LOSS-FAIL-CLOSED 承接
  - 自动重跑、取消或修复 GitHub Actions
  - 两份运维文档的候选 CI 章节同步（随 T0-CI-IDENTITY-DEADLINE 一并落地）
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/API-CONTRACT OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }
dod_exit: 0
dod_assert: seeded-remote 分片真实执行且退出 0；输出必须含**大小写敏感**的成功哨兵 `T37-CIGATE/API-CONTRACT OK` 与 `selftest: PASS`。要求的是该闸的**正向成功证据**：闸被跳过、或闸整个不存在，都不会打印该哨兵，故 DoD 在实现落地前必红。
review_gate: codex {verdict:pass}
hygiene: 每个负例配对应正例或单点变异；重放夹具必须证明命中的是 id 去重出口，而非更早的 total 漂移 / count>total / 通用 API 错误。
doc_sync: 本卡不改文档；候选 CI 的文档同步由 T0-CI-IDENTITY-DEADLINE 承担。
---

# T0-CI-PAGED-CONTRACT

## 产出

`scripts/task.ps1` 中 `Get-GhPagedCollectionBeforeDeadline` 的分页读取契约，以及 `scripts/selftest.ps1`
中 `T37-CIGATE/API-CONTRACT` 闸的正反夹具矩阵。三个分页 endpoint 共用这一个函数，故契约在函数内一次收口、
三处同时生效。

## 已验证的前置工作（可直接承接，不必从零重做）

本卡从 `T0-CI-HARDENING-MATRIX` 拆出。原卡的分页部分**已实现并经独立复核**，成果保存在：

- 分支 `wip/T0-CI-hardening-validated`（tip `2ce7aa0f`）
- patch series `pr214-validated-work/0001..0005`

其中与本卡相关的是 `$seen` 去重守卫、三个 endpoint 的 `page-replay` 夹具，以及旧 mock 的 check-runs / jobs
补 id。承接时须自行重跑 DoD 与 `selftest -Shard seeded-remote`，不得以「上游已验证」替代本卡自己的机检。
**并行卡的实测背书**：`T4-SCHEDULE-REMINDER-CONTRACTS`（PR #215）R3 前两轮 5 条 finding 里有 **4 条是被保全的
种子代码里的缺陷**——那份种子此前通过了它自己的 DoD、看上去已完工（其一把一个合法但无关的 UUID 当作
已校验的关联发布，其二让「已授予通知权限」走进永久 Retry）。故种子按**未经审阅的新代码**逐行读，
上游绿相不构成关于它的任何证据。

## 为什么 id 是必需的而非可选的

分页条目没有稳定身份时，一页被重放或与下一页重叠，会把同一个绿 run 计成两条、凑满 `total_count`、
从而**掩盖一个从未被读到的红 run 并走到 merge**。`total_count` 与 `items.Count` 相等只证明数量对得上，
不证明读到的是 N 个**不同**的 run。真实 GitHub API 的三个 endpoint 都返回 `id`，故「要求 id」是向真实
形态收紧，不是新增假设。

## 禁止

确定性 / 离线；不碰 ci.yml 的 job 集；不放宽 R3、diff 预算或既有本地闸；不得为让不忠实的夹具通过而放宽
生产侧校验——夹具不忠实就修夹具。

## 非目标（本卡刻意不做的能力）

workflow 身份绑定 / jobs 漂移 / deadline / 最终快照 / 文档同步 / receipt-loss，均见 front-matter `non_goals`。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
$t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/API-CONTRACT OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }
```

- 期望退出码：0
- 断言：见 `dod_assert`。DoD **执行**闸门而非搜索字符串：只有 seeded-remote 真跑通、
  且 API-CONTRACT 闸没被任何 reason 跳过，才算通过。A4 的单点删除变异须在本命令下变红。
