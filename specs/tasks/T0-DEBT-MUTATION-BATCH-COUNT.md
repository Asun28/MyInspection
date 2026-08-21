---
id: T0-DEBT-MUTATION-BATCH-COUNT
title: 把 mutation 批次条数自证纪律合并晋升为必须层（偿还 TD148 / L177）
depends_on: [T0-DEBT-MUTATION-RESTORE-SAFETY]
status: todo
branch: T0-DEBT-MUTATION-BATCH-COUNT
worktree: C:\wt\T0-DEBT-MUTATION-BATCH-COUNT
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 新增独立 CLAUDE 铁律条目，使常驻条目超过 10
  - 改 mutation、task、ship、selftest 或 PowerShell 生产脚本
non_goals:
  - 晋升其它已达门槛的 lesson
  - 建通用 mutation runner 或重跑历史 mutation campaign
diagnosis:
  root_cause: L177 已记录 PowerShell 变量名大小写不敏感导致集合变量被循环变量覆盖、16 枚 mutation 实际只跑 1 枚却汇总为全绿的 blocking 假证据；它仍停留 ledger，默认上下文没有“循环变量不得与集合仅大小写不同、实跑条数必须等于计划条数”的纪律。
  same_class: L177 与既有 L17 都是 PowerShell 工具语义陷阱，合并为一个双 ID 铁律；不把其它 mutation 分类器、还原或编码 lesson 混入本卡。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L177 ')) { throw '[TD148-L177-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L177 meta 精确变为 tier=must；CLAUDE 的原 L17 条目原位合并为 [L17][L177] 且明确 PowerShell 变量名大小写不敏感、循环变量不得与集合变量仅大小写不同、批次记录数必须等于计划数；lessons check 证明 ledger must=11、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L177。
review_gate: codex {verdict:pass}
hygiene: 只合并一条近义 PowerShell 铁律，不复制 L177 的 symptom/root_cause 长文，不新增脚本或测试
doc_sync: merge 后将 TD148 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-MUTATION-BATCH-COUNT

## 产出

把 L177 的两条防假证据规则放进每轮必载上下文：PowerShell 循环变量不得与集合变量仅大小写不同；mutation 批次结束时，实际记录条数必须等于计划条数。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L177 仍为 `tier=ledger`，triage 必须以 `lessons-promote L177` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L177 meta 改为 `must`，并把现有 L17 标题从 `[L17]` 合并为 `[L17][L177]`，在同一 PowerShell 铁律末尾加入条数自证纪律。`lessons.ps1 check` 按 CLAUDE bullet 数封顶，因此 11 个 must lesson 可经近义合并保持 10 条常驻规则。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：违反明确 cap，且 L17/L177 可自然合并。
- 不把 L17 降级：它复发 3 次，且本卡不是用新债替换旧债。
- 不只改 L177 为 ondemand：blocking lesson 已达 must 门槛，且可在不增加条目的前提下常驻。
