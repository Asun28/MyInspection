---
id: T0-CI-HARDENING-SPLIT-PLAN
title: 将候选 CI 硬化卡拆为分页契约与身份/deadline 两张可读串行卡
depends_on: [T0-CI-MERGE-GATE]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 退役未合并的 T0-CI-HARDENING-MATRIX，并在 TASK-BOARD 钉住 PR #214 的精确证据：exact head 2ce7aa0f 的本地量测 61092 字符超 60000 闸、reviewed head cce6fa5e、verify green、R3 第 1 轮 block 原文"
  - "A2 PAGED-CONTRACT → IDENTITY-DEADLINE → RECEIPT-LOSS 构成一条串行依赖链，两张新卡 parallelizable_with 均为空（允许共享 task.ps1/selftest.ps1，因串行无并发写风险）"
  - "A3 两张承接卡各自保留 1000/60000 预算闸与 mandatory R3；其 dod_command 必须**执行** seeded-remote 分片并要求各目标闸的大小写敏感成功哨兵 `<gate> OK`（正向证据，闸缺失即红），而非搜索字符串或仅拒绝跳过记录（本卡 DoD 机检这一点）"
  - "A4 下游 T0-RECEIPT-LOSS-FAIL-CLOSED 的 depends_on 重新指向链条终点 T0-CI-IDENTITY-DEADLINE，依赖图与 TASK-BOARD 同步"
  - "A5 已验证成果以只读方式保全（分支 wip/T0-CI-hardening-validated tip 2ce7aa0f 与 patch series），两张承接卡各自声明可承接的部分，且仍须自行重跑 DoD 与 selftest"
status: todo
branch: T0-CI-HARDENING-SPLIT-PLAN
worktree: C:\wt\T0-CI-HARDENING-SPLIT-PLAN
allow_paths:
  - specs/tasks/T0-CI-HARDENING-MATRIX.md
  - specs/tasks/T0-CI-HARDENING-SPLIT-PLAN.md
  - specs/tasks/T0-CI-PAGED-CONTRACT.md
  - specs/tasks/T0-CI-IDENTITY-DEADLINE.md
  - specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md
  - docs/TASK-BOARD.md
  - specs/tech-debt-tracker.md
forbid:
  - 修改任何 scripts/ 生产代码、测试或 ci.yml
  - 提高 R3 的 1000 行或 60000 字符预算
  - 整体 cherry-pick、force-push 或合并 PR #214 的提交链
non_goals:
  - 实现分页契约、身份绑定、deadline 或最终快照本身
  - 在两张承接卡完成抽取前删除 wip/T0-CI-hardening-validated 的只读证据
  - receipt-loss 恢复策略
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((Test-Path 'specs/tasks/T0-CI-HARDENING-MATRIX.md') -or -not (Test-Path 'specs/tasks/T0-CI-PAGED-CONTRACT.md') -or -not (Test-Path 'specs/tasks/T0-CI-IDENTITY-DEADLINE.md')) { exit 1 }; if (-not (Get-Content -Raw 'specs/tasks/T0-CI-PAGED-CONTRACT.md').Contains('depends_on: [T0-CI-MERGE-GATE, T0-CI-HARDENING-SPLIT-PLAN]')) { exit 1 }; if (-not (Get-Content -Raw 'specs/tasks/T0-CI-IDENTITY-DEADLINE.md').Contains('depends_on: [T0-CI-PAGED-CONTRACT]')) { exit 1 }; if (-not (Get-Content -Raw 'specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md').Contains('depends_on: [T0-CI-IDENTITY-DEADLINE]')) { exit 1 }; $b = Get-Content -Raw 'docs/TASK-BOARD.md'; foreach ($e in @('~~T0-CI-HARDENING-MATRIX~~','2ce7aa0f','cce6fa5e','61092','T0-CI-PAGED-CONTRACT','T0-CI-IDENTITY-DEADLINE')) { if (-not $b.Contains($e)) { exit 1 } }; foreach ($c in @('specs/tasks/T0-CI-PAGED-CONTRACT.md','specs/tasks/T0-CI-IDENTITY-DEADLINE.md')) { $x = Get-Content -Raw $c; if (-not $x.Contains('selftest.ps1 -Shard seeded-remote') -or -not $x.Contains('-cnotmatch') -or -not $x.Contains(' OK')) { exit 1 } }
dod_exit: 0
dod_assert: 原硬化卡退出活目录；两张承接卡存在且串行依赖成链、下游已重指向；TASK-BOARD 钉住 PR #214 的精确未合并证据与两张承接卡；两张承接卡的 dod_command 均真实执行 seeded-remote 并以大小写敏感的 `<gate> OK` 成功哨兵为通过条件。
review_gate: codex {verdict:pass}
hygiene: check-cards 校验全部活卡；拆分卡只改卡片元数据与看板，不借拆分放宽任何质量闸。
doc_sync: TASK-BOARD 记录 split-plan 合并 OID 与依赖图；本规划卡 R5 归档，两张承接卡保持 todo。
---

# T0-CI-HARDENING-SPLIT-PLAN

## Light Plan Forge 结论

`T0-CI-HARDENING-MATRIX`（PR #214）在 reviewed head `cce6fa5e` 量测 59713 字符、勉强入 60000 闸，
R3 第 1 轮以「分页条目无稳定身份 ⇒ 重放页可掩盖未读到的红 run 并走到 merge」block。修复该发现本身
只需 +336 字符，但修复触发的两处**必须一并解决**的真缺陷把总量推到 **61092**，超闸 1092：

1. **PR 关联判定大小写不敏感**：`$_.number` 使一个只带 `Number` 的条目通过 PR 关联校验，
   不属于本 PR 的 workflow run 可借此过闸。修法 +116 字符。
2. **仓内旧 mock 的 check-runs / jobs 载荷不带 id**：新的 id 契约正确地拒绝了它，
   导致 `T37-REMOTEMX/3` 未触达 merge。修夹具只改 2 行，但那两行位于长 JSON 字面量区，
   新 hunk 拖入约 1265 字符上下文，**实际代价 1281 字符**。

第 2 条说明本卡的体量瓶颈不是行数（524/1000，绰绰有余）而是**字符数与 hunk 上下文**：在长行密集区
改动 2 行的代价与改动数十行相当。继续压缩只会重蹈原卡「靠折叠 param 块、删守卫、单行化换预算」的老路，
而那正是本次拆卡要根除的形态。

## 冻结决策

`T0-CI-PAGED-CONTRACT` → `T0-CI-IDENTITY-DEADLINE` → `T0-RECEIPT-LOSS-FAIL-CLOSED`

按**闸门关注点**切，而非按「生产码 / 测试」切。后者不可行：所有闸门夹具都经
`task.ps1 -Phase ship` 端到端驱动、以 merge 哨兵断言，分页函数无法脱离编排单独观测，
硬切会立刻复现 R3 的「Tests missing」。

切法可行的前提是 **master 已具备候选 CI 闸与分页函数**（`T0-CI-MERGE-GATE` 已合并，
`CI-GATE-*` 与 `Get-GhPagedCollectionBeforeDeadline` 均在 master 上）：两张卡都是在既有骨架上**硬化**，
故可各自独立落地。此前的拆分尝试（`wip/T0-CI-split-staging` → `wip/T0-CI-card1-min`）失败正因其
把「运行时核心」整体搬迁，card1 达 68863 字符、比不拆还大。

预估体量：PAGED-CONTRACT 约 31k、IDENTITY-DEADLINE 约 27k，均留足 R3 往返余量。

## 已验证成果的保全与承接

分支 `wip/T0-CI-hardening-validated`（tip `2ce7aa0f`）与 patch series `pr214-validated-work/0001..0005`
只读保留至两张承接卡抽取完成。两次独立复核（Sol Max，high effort）在正确 HEAD 上均无发现：

- 分页守卫正确拒绝非对象、缺 id、`"11"`、`11.0`、0、负数与超 Int64（反序列化为 BigInteger）条目；
  同页与跨页重复均因 `$seen` 逐条更新而失败；id 不同但内容相同的条目被接受，在「id 即稳定身份」的
  契约下正确。
- 被丢弃的两处未请求改动确认应当丢弃：`$proc.Kill($true)` 已终止整棵进程树，嵌套 `pwsh` 不增加任何
  包容性；`-notin` 大小写不敏感会静默放宽路径身份闸，且实测本仓 915 个 run 与三个大型公开仓库近 300 个
  PR run 均未出现 `@ref` 路径后缀（GitHub 文档在另一种响应形态下有 `@main` 示例，故置信高但非绝对）。

承接卡**不得**以「上游已验证」替代自身机检：各自重跑 DoD 与 `selftest -Shard seeded-remote`。

## 非目标（本卡刻意不做的能力）

见 front-matter `non_goals`。本卡只动卡片元数据与看板，不碰 `scripts/`。

## 验收（DoD = 命令 + 退出码 + 断言）

见 front-matter `dod_command`；期望退出码 0，断言见 `dod_assert`。
