---
id: T0-DEBT-MUTATION-EVIDENCE-CLASSIFIER
title: 把 mutation 判据分类器纪律合并晋升为必须层（偿还 TD149 / L167）
depends_on: [T0-DEBT-MUTATION-BATCH-COUNT]
status: todo
branch: T0-DEBT-MUTATION-EVIDENCE-CLASSIFIER
worktree: C:\wt\T0-DEBT-MUTATION-EVIDENCE-CLASSIFIER
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 新增独立 CLAUDE 铁律条目，使常驻条目超过 10
  - 改 mutation、task、ship、selftest 或其它生产脚本
non_goals:
  - 晋升其它已达门槛的 lesson
  - 建通用 mutation runner 或重跑历史 mutation campaign
diagnosis:
  root_cause: L167 已记录多批 mutation 仅因语法错误、StrictMode 异常或更早闸抢先失败便被误报为“全红”的 blocking 假证据；它仍停留 ledger，默认上下文没有“非零且命中指定断言才算 mutation 被杀”的完整纪律。
  same_class: L167 是既有 L165“删除变异必须精确证明断言承重”的判据分类器部分，合并为一个双 ID 铁律；不把其它 assertion、编码或恢复 lesson 混入本卡。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L167 ')) { throw '[TD149-L167-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L167 meta 精确变为 tier=must；CLAUDE 的原 L165 条目原位合并为 [L165][L167]，且明确只有“非零且命中指定断言”才算 mutation 被杀，靶未命中、parser 失败、运行时异常或更早闸抢先均须分类为无效证据；lessons check 证明 ledger must=12、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L167。
review_gate: codex {verdict:pass}
hygiene: 只合并一条近义 mutation 证据铁律，不复制 L167 的 symptom/root_cause 长文，不新增脚本或测试
doc_sync: merge 后将 TD149 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-MUTATION-EVIDENCE-CLASSIFIER

## 产出

把 L167 的防假 mutation 证据规则放进每轮必载上下文：只有目标变异使指定断言失败才算证据，语法错误、运行时异常、靶未命中或更早闸抢先都必须单独分类。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L167 仍为 `tier=ledger`，triage 必须以 `lessons-promote L167` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L167 meta 改为 `must`，并把现有 L165 标题从 `[L165]` 合并为 `[L165][L167]`；正文仅补齐靶未命中、parser、运行时与更早闸抢先的分类要求。`lessons.ps1 check` 按 CLAUDE bullet 数封顶，因此 12 个 must lesson可经近义合并保持 10 条常驻规则。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：违反明确 cap，且 L165/L167 是同一 mutation 证据合同。
- 不改 `scripts/selftest.ps1`：本卡偿还的是经验未进入默认上下文，既有具体闸已具备分类器。
- 不批量晋升其它 lesson：每条需独立核验是否与现有 must 规则同类。
