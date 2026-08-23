---
id: T0-DEBT-UNICODE-SANITIZER-CATEGORIES
title: 把 Unicode sanitizer 类别纪律合并晋升为必须层（偿还 TD151 / L181）
depends_on: []
status: merged
branch: T0-DEBT-UNICODE-SANITIZER-CATEGORIES
worktree: C:\wt\T0-DEBT-UNICODE-SANITIZER-CATEGORIES
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 新增独立 CLAUDE 铁律条目，使常驻条目超过 10
  - 改 review、selftest、sanitizer 或其它生产脚本
non_goals:
  - 晋升其它已达门槛的 lesson
  - 重写 Unicode sanitizer 或重跑历史变异批次
diagnosis:
  root_cause: L181 已记录 .NET 正则按 UTF-16 码元匹配、宽泛 `\p{C}` 会把合法增补平面字符的代理对两半当 `Cs` 清除的 blocking 数据损坏事故；它仍停留 ledger，默认上下文没有“只剥 Cc/Cf、保留增补平面字符”的纪律。
  same_class: L181 与既有 L193 都是不可见/非 BMP 码位在工具与 UTF-16 边界被静默改写，合并为一个双 ID Unicode 铁律；不把 Unicode oracle 全集、日志脱敏或其它 sanitizer lesson 混入本卡。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L181 ')) { throw '[TD151-L181-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L181 meta 精确变为 tier=must；CLAUDE 的原 L193 条目原位合并为 [L181][L193]，且明确剥控制/格式字符只用 [\p{Cc}\p{Cf}]、禁止使用会命中 Cs/Co/Cn 的宽泛 \p{C}，并要求增补平面字符原样留存回归；lessons check 证明 ledger must=14、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L181。
review_gate: codex {verdict:pass}
hygiene: 只合并一条近义 Unicode 铁律，不复制 L181 的事故长文，不新增脚本或测试
doc_sync: merge 后将 TD151 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-UNICODE-SANITIZER-CATEGORIES

## 产出

把 L181 的 Unicode 防数据损坏纪律放进每轮必载上下文：控制字符 sanitizer 只能剥 Cc/Cf，且必须证明增补平面合法字符原样留存。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L181 仍为 `tier=ledger`，triage 必须以 `lessons-promote L181` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L181 meta 改为 `must`，并把现有 L193 标题合并为 `[L181][L193]`；正文仅补齐 `Cc|Cf` 精确类别、禁止 `\p{C}` 与增补平面留存回归。`lessons.ps1 check` 按 CLAUDE bullet 数封顶，因此 14 个 must lesson 可经近义合并保持 10 条常驻规则。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：违反明确 cap，且 L181/L193 都是 Unicode 码位被工具层静默破坏。
- 不改 sanitizer/selftest：L181 已有具体执行闸，本卡偿还的是经验未进入默认上下文。
- 不批量晋升其它 lesson：每条需独立核验是否与现有 must 规则同类。
