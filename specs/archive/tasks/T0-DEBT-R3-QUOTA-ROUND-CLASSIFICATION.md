---
id: T0-DEBT-R3-QUOTA-ROUND-CLASSIFICATION
title: 把 R3 配额/轮次证据分类纪律晋升为必须层（偿还 TD155 / L21）
depends_on: []
status: merged
branch: T0-DEBT-R3-QUOTA-ROUND-CLASSIFICATION
worktree: C:\wt\T0-DEBT-R3-QUOTA-ROUND-CLASSIFICATION
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 修改 review、round cap、ResetRounds、配额探测、selftest 或 CI 行为
  - 把评审者未运行伪装成真实 R3 分歧，或允许绕过 R3 合并
non_goals:
  - 晋升其它已达门槛的 lesson
  - 自动重置 R3 轮次、自动切换模型或处理账号配额
  - 重做 L205 的本地对抗评审流程
diagnosis:
  root_cause: L21 已两次记录 Codex 配额耗尽令评审者未产出裁决，却被无输出 fail-closed 与 rounds 计数表象误读成真实代码 block；继续重跑 ship 会消耗轮次并最终触发 round cap，掩盖外部故障。规则已被 review timeout/ResetRounds 路由支撑，但仍停留 ledger。
  same_class: L21 与现有 L205 都约束 R3 迭代证据的可信性：先区分评审者是否真正运行，再对真实修复 diff 做独立复核；合并为 [L21][L205] 可保持十条上限，而不把配额诊断塞进无关的编码或路径安全铁律。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L21 ')) { throw '[TD155-L21-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L21 meta 精确变为 tier=must；CLAUDE 的 [L205] 原位合并为 [L21][L205]，保留修复轮 fresh-context 对抗复核，并明确 R3 无裁决/配额错误先用独立 reviewer probe 确诊、不得靠重复 ship 烧 rounds、评审者未真正运行不算真实分歧、确诊外部故障后才可 ResetRounds 重试且绝不绕过 R3；lessons check 证明 ledger must=18、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L21
review_gate: codex {verdict:pass}
hygiene: 只扩一条现有 R3 轮次证据铁律，不复制 L21 的命令长文，不新增脚本或测试
doc_sync: merge 后将 TD155 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-R3-QUOTA-ROUND-CLASSIFICATION

## 产出

把“评审者未真正运行不等于真实 R3 分歧”的纪律放进每轮必载上下文，并与 L205 的修复轮复核保持单一铁律。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L21 仍为 `tier=ledger`，triage 必须以 `lessons-promote L21` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L21 meta 改为 `must`，把现有 `[L205]` 标题改为 `[L21][L205]`。正文先要求对无裁决/配额类失败做独立 probe，不以重跑 ship 消耗 rounds；只有确诊外部故障后才可 ResetRounds 重试，且仍须经过 R3。保留 L205 对大修复轮的 fresh-context 复核全文语义。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：违反明确 cap。
- 不把 L21 合并进 L95：vacuous RED 与 reviewer operational failure 不是同一证据阶段。
- 不顺手修改 review/selftest：本卡偿还默认上下文债，既有机械路由保持不变。
