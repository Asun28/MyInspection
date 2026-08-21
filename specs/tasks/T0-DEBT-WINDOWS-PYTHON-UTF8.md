---
id: T0-DEBT-WINDOWS-PYTHON-UTF8
title: 把 Windows Python UTF-8 工具纪律合并晋升为必须层（偿还 TD152 / L162）
depends_on: [T0-DEBT-POWERSHELL-DETACHED-ENCODING]
status: todo
branch: T0-DEBT-WINDOWS-PYTHON-UTF8
worktree: C:\wt\T0-DEBT-WINDOWS-PYTHON-UTF8
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 新增独立 CLAUDE 铁律条目，使常驻条目超过 10
  - 改 Python、PowerShell、selftest、CI 或其它生产脚本
non_goals:
  - 晋升其它已达门槛的 lesson
  - 重写第三方插件工具或新增 Python 运行时依赖
diagnosis:
  root_cause: L162 已记录 Windows Python 的 locale 默认编码使第三方/插件工具读取仓库中文 JSON/MD 时 UnicodeDecodeError，以及管道块缓冲令长驻进程看似静默失败的 blocking 事故；它仍停留 ledger，默认上下文没有正确的 UTF-8 模式与无缓冲启动纪律。
  same_class: L162 与既有 L17/L172/L177 都是脚本工具的编码/输出机制与被测仓库不同源而产生假结论，合并为一个四 ID 铁律；不把 Unicode sanitizer、日志脱敏或 Python 项目工程化 lesson 混入本卡。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L162 ')) { throw '[TD152-L162-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L162 meta 精确变为 tier=must；CLAUDE 的 [L17][L172][L177] 条目原位合并为 [L17][L162][L172][L177]，保留“.ps1 一律用 PowerShell、不用 Bash”，并明确第三方/插件 Python 读仓库文件前设 PYTHONUTF8=1、自有文件 I/O 显式 UTF-8、长驻进程即时输出用 python -u 且避免管道；lessons check 证明 ledger must=15、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L162。
review_gate: codex {verdict:pass}
hygiene: 只合并一条近义脚本编码铁律，不复制 L162 的事故长文，不新增脚本或测试
doc_sync: merge 后将 TD152 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-WINDOWS-PYTHON-UTF8

## 产出

把 L162 的 Windows Python 防编码/缓冲误诊纪律放进每轮必载上下文，同时保留既有 PowerShell 绝对边界。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L162 仍为 `tier=ledger`，triage 必须以 `lessons-promote L162` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L162 meta 改为 `must`，并把现有 `[L17][L172][L177]` 标题合并为 `[L17][L162][L172][L177]`；正文仅补齐 `PYTHONUTF8=1`、显式 UTF-8 文件 I/O 与 `python -u` 无缓冲纪律。`lessons.ps1 check` 按 CLAUDE bullet 数封顶，因此 15 个 must lesson 可经近义合并保持 10 条常驻规则。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：违反明确 cap，且四条 lesson 都是脚本工具不同源造成假结论。
- 不把 Python 规则改写成 PowerShell 特例：`PYTHONIOENCODING` 不影响 `open()`，L162 的机制不同但防线同属工具编码边界。
- 不批量晋升其它 lesson：每条需独立核验是否与现有 must 规则同类。
