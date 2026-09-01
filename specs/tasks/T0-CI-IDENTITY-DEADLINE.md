---
id: T0-CI-IDENTITY-DEADLINE
title: 候选 CI 的 run 身份绑定、单一 deadline 与最终 exact-head/base 快照
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
  - 候选 ci.yml 声明 job 集 ↔ run 返回 job 集的漂移判定（API 侧那一平面）与 `T37-CIGATE/JOBS-DRIFT` 闸；
    实现期实测本卡 diff 达 61233 字符、超 R3 完整读取预算 60000，按 DEVOPS-WORKFLOW §35「超限卡必须拆卡」
    拆给承接卡 T0-CI-JOBS-DRIFT（用户 2026-09-01 裁定）。本卡只保留既有的 ci.yml 声明面解析闸
  - receipt-loss 恢复策略；由 T0-RECEIPT-LOSS-FAIL-CLOSED 承接
  - 自动重跑、取消或修复 GitHub Actions
  - 把 scaffold-selftest.yml 放回 PR 关键路径
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/WORKFLOW-BINDING OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }; foreach ($d in @('docs/DEVOPS-WORKFLOW.md','docs/DELIVERY-CHAINS.md')) { $hit = @(Get-Content $d | Where-Object { $_.Contains('candidate CI') -and $_.Contains('exact-head') -and $_.Contains('deadline') -and $_.Contains('-NoAutoMerge') }); if ($hit.Count -lt 1) { exit 1 } }
dod_exit: 0
dod_assert: seeded-remote 真实执行且退出 0；输出必须含大小写敏感哨兵 `T37-CIGATE/WORKFLOW-BINDING OK` 与 `selftest: PASS`；**两份**文档 DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 均须含 candidate CI / exact-head / deadline / -NoAutoMerge 四个契约要素，缺一即红。
review_gate: codex {verdict:pass}
hygiene: 每个负例配对应正例或单点变异；大小写负例必须证明是被身份闸拦下，而非更早的通用错误。
doc_sync: DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 同步候选 CI 身份、deadline、最终 exact-head/base 快照和 NoAutoMerge 契约。
---

# T0-CI-IDENTITY-DEADLINE

## 产出

候选 CI 的**身份与时序**收口：workflow run 身份逐层绑定、单一 wall-clock deadline 与进程树清理、
最终 exact-head/base 快照，以及 `T37-CIGATE/WORKFLOW-BINDING` 闸的正反夹具；并同步两份运维文档。

> **拆卡记录（2026-09-01，用户裁定）**：本卡原含 API 侧 job 集漂移判定与 `T37-CIGATE/JOBS-DRIFT` 闸。
> 实现完成、R3 首轮三条 finding 全部修完后实测 diff = **61233 字符 > 60000 预算**（行数 675/1000 不超）。
> 按 `docs/DEVOPS-WORKFLOW.md` §35「超限卡必须拆成有依赖的 1→N 卡，不能扩 allow_paths 或提高 CLI 参数绕过」，
> 把该平面整体拆给承接卡 **`T0-CI-JOBS-DRIFT`**。seam 干净的依据：jobs 漂移**不在** A1–A5 任何一条里，
> 它只出现在原标题与 DoD 哨兵中。本卡保留既有的 ci.yml **声明面**解析闸（那一枚 `[CI-GATE-JOBS-DRIFT]`
> 抛点在 master 上早已存在、本卡未改）。

## 已验证的前置工作（可直接承接）

本卡从 `T0-CI-HARDENING-MATRIX` 拆出。原卡的身份部分成果保存在分支 `wip/T0-CI-hardening-validated`
（tip `2ce7aa0f`）与 patch series `pr214-validated-work/0001..0005`，其中与本卡相关的是 PR 关联属性名的
大小写敏感修复、`workflow-path-case` / `workflow-case-pr` 两条负例，以及两份文档的候选 CI 段落。
承接时须自行重跑 DoD 与 `selftest -Shard seeded-remote`，不得以「上游已验证」替代本卡自己的机检。
**并行卡的实测背书**：`T4-SCHEDULE-REMINDER-CONTRACTS`（PR #215）R3 前两轮 5 条 finding 里有 **4 条是被保全的
种子代码里的缺陷**——那份种子此前通过了它自己的 DoD、看上去已完工（其一把一个合法但无关的 UUID 当作
已校验的关联发布，其二让「已授予通知权限」走进永久 Retry）。故种子按**未经审阅的新代码**逐行读，
上游绿相不构成关于它的任何证据。

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
$t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/WORKFLOW-BINDING OK\s*$' -or $t -cnotmatch '(?m)^\s*T37-CIGATE/JOBS-DRIFT OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }; foreach ($d in @('docs/DEVOPS-WORKFLOW.md','docs/DELIVERY-CHAINS.md')) { $hit = @(Get-Content $d | Where-Object { $_.Contains('candidate CI') -and $_.Contains('exact-head') -and $_.Contains('deadline') -and $_.Contains('-NoAutoMerge') }); if ($hit.Count -lt 1) { exit 1 } }
```

- 期望退出码：0
- 断言：见 `dod_assert`。DoD **执行**闸门而非搜索字符串：两闸被任何 reason 跳过即判失败。
