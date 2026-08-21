---
id: T0-DEBT-MUTATION-RESTORE-SAFETY
title: 把未提交 mutation 还原防丢纪律晋升为必须层（偿还 TD147 / L214）
depends_on: []
status: todo
branch: T0-DEBT-MUTATION-RESTORE-SAFETY
worktree: C:\wt\T0-DEBT-MUTATION-RESTORE-SAFETY
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级其它必须层经验来腾位置
  - 改 mutation、task、ship、selftest 或 GitHub 控制流
  - 用 git checkout、git restore 或 reset 处理任何未提交工作
non_goals:
  - 晋升其余已达门槛的 lesson
  - 解决 L216 的 RED 证据 SHA 与手工提交冲突
diagnosis:
  root_cause: L214 已因 severity=blocking 达到必须层客观门槛，但仍停留在 ledger；因此每轮默认上下文没有“变异前保全未提交工作、还原后核 SHA256”的防丢纪律，triage 持续报告 lessons-promote L214。
  same_class: 本卡只偿还 L214；其余 lessons-promote findings 逐条独立分诊，不借本卡批量改写常驻规则。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L214 ')) { throw '[TD147-L214-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L214 的 ledger meta 精确变为 tier=must，CLAUDE.md 必须层新增一条与其 rule 同义且包含变异前保全和还原后 SHA256 核验的铁律；必须层恰为上限 10；lessons check 通过且 triage 不再报告 L214。
review_gate: codex {verdict:pass}
hygiene: 只晋升一条既有经验，不复制 symptom/root_cause 长文，不新增脚本或测试
doc_sync: merge 后将 TD147 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-MUTATION-RESTORE-SAFETY

## 产出

把已造成真实未提交查询丢失的 L214 从总账晋升到每轮必载的经验铁律：变异前必须先保全被测文件，变异后必须按 SHA256 证明逐字节恢复。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L214 仍为 `tier=ledger`，triage 必须以 `lessons-promote L214` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

只把 L214 meta 的 tier 改为 `must`，并在 `CLAUDE.md` 必须层增加一条精炼规则。现有必须层 9/10，晋升后恰为 10，无需提高上限或淘汰其它条目。

## 被否决方案

- 不批量晋升其余 22 条：每条的复发面、常驻价值与替换成本不同，批量操作无法独立评审。
- 不新增 mutation helper：L214 是跨语言/跨 runner 的操作纪律，当前债务是未晋升，不是缺少某个统一执行器。
- 不用 `git checkout`/`restore` 演示 RED：那会真实销毁 worktree 未提交内容，违背本卡要建立的边界。
