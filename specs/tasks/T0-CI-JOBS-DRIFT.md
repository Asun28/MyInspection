---
id: T0-CI-JOBS-DRIFT
title: 候选 run 返回 job 集与 ci.yml 声明集的漂移判定（API 侧平面）
depends_on: [T0-CI-IDENTITY-DEADLINE]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 候选 ci.yml 声明的 job 集与该 run 返回的 job 集须逐名**大小写敏感**地相等；不等即 [CI-GATE-JOBS-DRIFT] 当场 fail-closed，不得并入等待清单拖到 deadline 耗尽"
  - "A2 四条 API 侧负例各只动一处（改名致缺席 / 只改大小写 / 多一个未声明的 / 同名重复），各自停在稳定态 jobs 判定（读计数 1/1/1）且未触达合并"
  - "A3 另有一条「首读合法、终局那读才漂」的负例走到读计数 2/2/2 —— 没有它，终局那次集合判定删掉也全绿"
  - "A4 判定器是全函数：条目形态/类型不符、status 无法归类一律判漂移而非默认放行（L228）"
  - "A5 绿路正例证明「集合相等」不是恒假谓词：ci.yml 多声明一个 audit、run 返回恰等于 {verify, audit} 即放行并合并，且 jobs 按本夹具自己的 run id/attempt 取"
status: todo
branch: T0-CI-JOBS-DRIFT
worktree: C:\wt\T0-CI-JOBS-DRIFT
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - 用大小写不敏感的运算符（-in / -notin / -contains / -eq / Compare-Object 缺 -CaseSensitive）承担 job 名比较
  - 把集合不等并进等待清单、靠 deadline 超时兜底（那会误导诊断并烧满整个预算）
  - 降低前置卡已落地的身份链、单一 deadline 或终局快照任何一层
  - 用固定 PR 号、run id、head SHA 或只搜错误文本的 vacuous fixture
non_goals:
  - run 身份绑定、大小写敏感身份比较、单一 deadline 与终局 exact-head/base 快照；均由前置 T0-CI-IDENTITY-DEADLINE 承接
  - ci.yml **声明面**解析闸（矩阵/重名/形态外）；那一枚抛点早已在 master，本卡只加 API 侧平面
  - 分页读取契约（T0-CI-PAGED-CONTRACT）与 receipt-loss（T0-RECEIPT-LOSS-FAIL-CLOSED）
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/JOBS-DRIFT OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/WORKFLOW-BINDING OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }
dod_exit: 0
dod_assert: seeded-remote 真实执行且退出 0；输出须含大小写敏感哨兵 `T37-CIGATE/JOBS-DRIFT OK`、`T37-CIGATE/WORKFLOW-BINDING OK` 与 `selftest: PASS`——前置卡那枚一并断言，证明本卡没有为了自己变绿而放松已落地的身份链。
review_gate: codex {verdict:pass}
hygiene: 每条负例配对应正例或单点变异；去掉 `Compare-Object -CaseSensitive` 须能让 jobs-name-case 由红转绿（前置卡实测该变异 KILLED，收据在其 diff 内）。
doc_sync: DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 的候选 CI 段补回 job 集逐名相等这一层。
---

# T0-CI-JOBS-DRIFT

## 产出

候选 CI 闸的 **API 侧 job 集漂移**判定：`Get-ExactCandidateJobState`（逐名大小写敏感的集合相等 +
漂移/阻断/待定三态全函数分类器）接进稳定态与终局快照两处调用点，配 `T37-CIGATE/JOBS-DRIFT` 闸的
四条负例 + 一条终局漂移负例 + 一条绿路正例。

## 承接来源（成果已实现并实测全绿，但**须按未经审阅的新代码**逐行读）

本卡从 `T0-CI-IDENTITY-DEADLINE` 拆出。拆分原因是**预算**而非质量：该卡实现完成、R3 首轮三条 finding
全部修完后实测 diff = 61233 字符 > 60000 的 R3 完整读取预算（行数 675/1000 不超），按
`docs/DEVOPS-WORKFLOW.md` §35 必须拆卡而不得放宽限额（用户 2026-09-01 裁定）。

被拆出的实现连同其夹具**已在该卡的分支上跑到 `selftest: PASS`**，完整工作树 delta 存档于
`_local` 之外的会话 scratchpad `pre-split-full.patch`（若已失效，改从前置卡合并前的分支提交取）。
**但绿相不构成关于它的证据**：承接时按本仓一贯纪律逐行重读、自行重跑 DoD 与
`selftest -Shard seeded-remote`，不得以「上游已验证」替代本卡自己的机检（同 T4-SCHEDULE-REMINDER 链
的种子教训：看上去已完工、且通过了它自己 DoD 的种子里出过 4 个真缺陷）。

## 为什么「集合不等」必须当场 fail-closed

jobs 腿**只在 workflow run 自身已 completed+success 之后**才读，那一刻 job 集已经终局。故「声明集 ≠
返回集」是**漂移**，不是「还没跑出来」。旧实现把它并进等待清单，于是「多一个未声明的 job」或「少一个
已声明的 job」都只是让闸静静等到 deadline 耗尽，最后报的还是超时——既误导诊断，又白烧整个预算。

## 为什么需要「首读合法、终局才漂」那一条

四条常规负例都在**第一次** jobs 判定就被拦下，于是终局快照那次集合判定删掉也照样全绿。A3 那条专打它：
首读返回合法集合、次读才改名，读计数 2/2/2 即「走到了终局才被拦」的证据。

## 禁止

见 front-matter `forbid`。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
$t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); if ($LASTEXITCODE -ne 0) { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/JOBS-DRIFT OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^\s*T37-CIGATE/WORKFLOW-BINDING OK\s*$') { exit 1 }; if ($t -cnotmatch '(?m)^selftest: PASS\s*$') { exit 1 }
```

- 期望退出码：0
- 断言：见 `dod_assert`。DoD **执行**闸门而非搜索字符串：两闸被任何 reason 跳过即判失败。
