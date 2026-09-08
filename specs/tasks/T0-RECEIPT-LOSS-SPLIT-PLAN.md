---
id: T0-RECEIPT-LOSS-SPLIT-PLAN
title: 将 receipt-loss 交付拆为授权位、运行时停止与源码合同三张串行卡
depends_on: [T0-CI-IDENTITY-DEADLINE]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 既有 T0-RECEIPT-LOSS-FAIL-CLOSED 保留原 id/path 并改为依赖 T0-RECEIPT-AUTHORIZATION-BIT"
  - "A2 T0-RECEIPT-AUTHORIZATION-BIT -> T0-RECEIPT-LOSS-FAIL-CLOSED -> T0-RECEIPT-LOSS-SOURCE-CONTRACT -> T0-ASCII-SHIP-CODES 是唯一串行链"
  - "A3 A/B/C 均有编号验收、可执行单行 DoD、空 parallelizable_with 与包含自身卡路径的精确 allow_paths"
  - "A4 TASK-BOARD 只把 c53ec489 超限 WIP、session 54615 旧行为 RED 与 4495fae8 prototype 记为只读设计证据，不冒充正式 RED/GREEN"
  - "A5 注册 diff 只含本卡声明的六个 metadata/card/board 路径，不修改 scripts、运行时 docs 或 tech-debt"
status: merged
branch: T0-RECEIPT-LOSS-SPLIT-PLAN
worktree: C:\wt\T0-RECEIPT-LOSS-SPLIT-PLAN
allow_paths:
  - specs/tasks/T0-RECEIPT-LOSS-SPLIT-PLAN.md
  - specs/tasks/T0-RECEIPT-AUTHORIZATION-BIT.md
  - specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md
  - specs/tasks/T0-RECEIPT-LOSS-SOURCE-CONTRACT.md
  - specs/tasks/T0-ASCII-SHIP-CODES.md
  - docs/TASK-BOARD.md
forbid:
  - 修改 scripts、运行时 docs、质量预算或任何 gate 行为
  - 删除或改名既有 T0-RECEIPT-LOSS-FAIL-CLOSED
  - 把超限 WIP、prototype 或旧行为 RED 当成正式实现/终态验证
non_goals:
  - 实现 A/B/C 的 runtime、测试或文档行为
  - 清理只读 WIP/RED 设计证据
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; $p = Get-Content 'specs/tasks/T0-RECEIPT-LOSS-SPLIT-PLAN.md' -Raw; $a = Get-Content 'specs/tasks/T0-RECEIPT-AUTHORIZATION-BIT.md' -Raw; $b = Get-Content 'specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md' -Raw; $c = Get-Content 'specs/tasks/T0-RECEIPT-LOSS-SOURCE-CONTRACT.md' -Raw; $z = Get-Content 'specs/tasks/T0-ASCII-SHIP-CODES.md' -Raw; if (-not $a.Contains('depends_on: [T0-RECEIPT-LOSS-SPLIT-PLAN]') -or -not $b.Contains('depends_on: [T0-RECEIPT-AUTHORIZATION-BIT]') -or -not $c.Contains('depends_on: [T0-RECEIPT-LOSS-FAIL-CLOSED]') -or -not $z.Contains('depends_on: [T0-RECEIPT-LOSS-SOURCE-CONTRACT]')) { exit 1 }; foreach ($card in @($p,$a,$b,$c,$z)) { if (-not $card.Contains('parallelizable_with: []')) { exit 1 } }; if (-not $p.Contains('status: merged') -or @($a,$b,$c,$z | Where-Object { $_.Contains('status: todo') }).Count -ne 4) { exit 1 }; $board = Get-Content 'docs/TASK-BOARD.md' -Raw; foreach ($e in @('c53ec489','session 54615','4495fae8','T0-RECEIPT-AUTHORIZATION-BIT','T0-RECEIPT-LOSS-SOURCE-CONTRACT')) { if (-not $board.Contains($e)) { exit 1 } }
dod_exit: 0
dod_assert: check-cards 通过；A、既有 B、C、ASCII-SHIP 构成唯一串行链；A/B/C 的验收与 DoD 已冻结；看板钉住三份只读证据且未宣称正式 GREEN。
review_gate: codex {verdict:pass}
hygiene: 逐卡核对唯一 id、exact depends_on、空 parallelizable_with、实际 allow_paths 与可执行单行 DoD；只记录已验证证据。
doc_sync: 本登记 PR 同步 TASK-BOARD 与本卡 status=merged，作为合并后状态投影；不预填未来 squash SHA，不在六路径外自归档。actual ship 后只做 official cleanup，卡片由未来常规归档批次搬迁。
---

# T0-RECEIPT-LOSS-SPLIT-PLAN

## Light Plan Forge 结论

串行链固定为：

`T0-RECEIPT-AUTHORIZATION-BIT -> T0-RECEIPT-LOSS-FAIL-CLOSED -> T0-RECEIPT-LOSS-SOURCE-CONTRACT -> T0-ASCII-SHIP-CODES`

A 修复同一轮已验证/已铸 receipt 的内存授权；既有 B 承担已发布 receipt 四类失效态的运行时 fail-closed、真实 T37 与文档；C 只承担源码合同、enum/discovery 和 mutation 防回归。三卡共享 `scripts/selftest.ps1`，A/B 还共享 `scripts/task.ps1`，因此必须串行。

## 已拒绝方案

- 不交付 117k 超限 WIP；它只作为覆盖映射来源。
- 不按生产码/测试拆分；每张行为卡都必须携带能先红后绿的可证伪测试。
- 不并行 A/B/C；共享写路径会破坏精确基线和中间 GREEN。
- 不让 C 修 B 的运行时或文档；否则职责与字符预算重新合并。

## 证据边界

- `c53ec489f7bb4b89dbe81ec7273deb037bd2e65d`：超 60,000 字符的 reviewed WIP，只读。
- `session 54615`：旧代码真实 receipt 删除后的行为 RED；raw SHA256 `A6388DA76D404F6292E2905F5E5E7C3E7D904F67B2889CE6ACDB8139EC801637`，exit 1，HEAD PRE=POST `ceb2685e9e3ada76a503377584d512c0c6d2af4d`，唯一失败 `15r(e)B`。它只证明缺陷可观测；A 仍须在正式工作树 fresh RED。
- `afced255 -> 309a51a1 -> 4495fae854777eb4592d0b5223a9981de13f0ac4`：A prototype 的实现、授权测试加固、真实 scope-core 删除夹具修正；最终设计证据钉在末个提交，不可 cherry-pick 代替 TDD。

## 验收

见 front matter。注册卡只改六个 metadata/card/board 路径；A/B/C 均保持仓库正式 `1000/60000` 硬闸，C 再自限 `300/35000`。
