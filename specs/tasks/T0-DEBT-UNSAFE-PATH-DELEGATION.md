---
id: T0-DEBT-UNSAFE-PATH-DELEGATION
title: 把不安全路径不得委托下游纪律晋升为必须层（偿还 TD153 / L171）
depends_on: [T0-DEBT-MUTATION-RESTORE-SAFETY]
status: todo
branch: T0-DEBT-UNSAFE-PATH-DELEGATION
worktree: C:\wt\T0-DEBT-UNSAFE-PATH-DELEGATION
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 修改 review、selftest、CI、链接防护或其它生产脚本
  - 弱化既有 [R3-REVIEW-DIR-UNSAFE] fail-closed 行为
non_goals:
  - 晋升其它已达门槛的 lesson
  - 重做评审路径安全实现或增加新的 mutation harness
diagnosis:
  root_cause: L171 已记录“本进程不写不安全路径，却把路径经 REVIEW_OUT 交给评审者代写”的 blocking 信任边界事故；同一错误又在陈旧裁决删除站点复发。规则虽已有 selftest 17t 行为闸，但仍停留 ledger，默认上下文没有“委托前校验、失败不唤起、响应单源复用”的纪律。
  same_class: L171 需要独立安全铁律，不能硬塞进无关规则；为保持十条上限，只把现有 L196 与 L214 两条同属“mutation 前保全、异常后按 SHA 还原”的近义铁律合并，逐项保留两者内容。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L171 ')) { throw '[TD153-L171-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L171 meta 精确变为 tier=must；CLAUDE 新增独立 [L171] 条目，明确不安全路径不得交给子进程/下游、判不过须在唤起前中止、同一安全响应集中复用；原 [L196] 与 [L214] 合并为一条且两者的 mutation 保全/续接/还原 SHA 纪律均保留；lessons check 证明 ledger must=16、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L171
review_gate: codex {verdict:pass}
hygiene: 只重排两条近义 mutation 还原铁律并新增一条短安全铁律，不复制 L171 事故长文，不新增脚本或测试
doc_sync: merge 后将 TD153 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-UNSAFE-PATH-DELEGATION

## 产出

把 L171 的路径委托信任边界放进每轮必载上下文，同时保持常驻铁律十条封顶。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L171 仍为 `tier=ledger`，triage 必须以 `lessons-promote L171` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L171 meta 改为 `must`，新增一条短 `[L171]` 铁律。先把近义的 `[L196]` 与 `[L214]` 原位合并，释放一个常驻条目位置；合并不得丢掉未提交内容保全、会话续接前核基线 SHA、逐字节副本还原、还原后核 SHA256 的任一要求。

## 被否决方案

- 不把 L171 硬并进 L97/L165：文档同步或 mutation 判据与路径委托的安全语义不同。
- 不新增第 11 条 CLAUDE bullet：违反明确 cap。
- 不顺手修改 review/selftest：生产防线已有 17t 行为闸，本卡只偿还默认上下文债。
