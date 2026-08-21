---
id: T0-DEBT-POWERSHELL-DETACHED-ENCODING
title: 把 detached PowerShell 编码纪律合并晋升为必须层（偿还 TD150 / L172）
depends_on: [T0-DEBT-MUTATION-BATCH-COUNT]
status: todo
branch: T0-DEBT-POWERSHELL-DETACHED-ENCODING
worktree: C:\wt\T0-DEBT-POWERSHELL-DETACHED-ENCODING
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 新增独立 CLAUDE 铁律条目，使常驻条目超过 10
  - 改 scripts/_encoding.ps1、selftest、task、ship 或其它生产脚本
non_goals:
  - 晋升其它已达门槛的 lesson
  - 重跑历史 detached mutation campaign 或改现有子进程实现
diagnosis:
  root_cause: L172 已记录 detached/计划任务/CI 外壳启动的 pwsh 默认 OEM 编码令中文断言 mojibake、整晚 mutation 批次假红的 blocking 事故；它仍停留 ledger，默认上下文没有“外部启动先载入 _encoding.ps1、机检用 ASCII 哨兵”的纪律。
  same_class: L172 与既有 L17/L177 都是 PowerShell 工具与编码不同源导致假结论，合并为一个三 ID 铁律；不把 Python 编码、Unicode sanitizer 或其它进程边界 lesson 混入本卡。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L172 ')) { throw '[TD150-L172-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L172 meta 精确变为 tier=must；CLAUDE 的 [L17][L177] 条目原位合并为 [L17][L172][L177]，且明确 harness 外启动 pwsh 必须先 dot-source scripts/_encoding.ps1、机检文本优先 ASCII 哨兵；lessons check 证明 ledger must=13、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L172。
review_gate: codex {verdict:pass}
hygiene: 只合并一条近义 PowerShell 编码铁律，不复制 L172 的事故长文，不新增脚本或测试
doc_sync: merge 后将 TD150 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-POWERSHELL-DETACHED-ENCODING

## 产出

把 L172 的 detached PowerShell 防假红纪律放进每轮必载上下文：harness 外启动本仓 pwsh 前先载入统一编码设置，机器判据优先使用 ASCII 哨兵。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L172 仍为 `tier=ledger`，triage 必须以 `lessons-promote L172` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L172 meta 改为 `must`，并把现有 `[L17][L177]` 标题合并为 `[L17][L172][L177]`；正文仅加入 harness 外 pwsh 的 `_encoding.ps1` 前奏与 ASCII 机检纪律。`lessons.ps1 check` 按 CLAUDE bullet 数封顶，因此 13 个 must lesson 可经近义合并保持 10 条常驻规则。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：违反明确 cap，且 L17/L172/L177 都是 PowerShell 不同源假结论。
- 不改 `_encoding.ps1` 或 selftest：L172 的载体和具体回归已存在，本卡偿还的是经验未进入默认上下文。
- 不批量晋升其它 lesson：每条需独立核验是否与现有 must 规则同类。
