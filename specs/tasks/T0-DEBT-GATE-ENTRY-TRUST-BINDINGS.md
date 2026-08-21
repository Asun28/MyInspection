---
id: T0-DEBT-GATE-ENTRY-TRUST-BINDINGS
title: 把 fail-closed 新入口信任绑定纪律晋升为必须层（偿还 TD154 / L164）
depends_on: [T0-DEBT-UNSAFE-PATH-DELEGATION]
status: todo
branch: T0-DEBT-GATE-ENTRY-TRUST-BINDINGS
worktree: C:\wt\T0-DEBT-GATE-ENTRY-TRUST-BINDINGS
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 提高必须层上限，或删除/降级现有 must lesson
  - 修改 scope、task、selftest、CI 或其它生产脚本
  - 弱化既有范围闸、不可变 OID 或受信基线防线
non_goals:
  - 晋升其它已达门槛的 lesson
  - 重构范围检查器或新增 mutation harness
diagnosis:
  root_cause: L164 已记录把 ship 内联 fail-closed 范围闸抽成第二入口时只复制可见判定逻辑，却遗漏原入口由运行位置隐式获得的判定对象、基线卡、检查器来源、提交身份与 TOCTOU 绑定，导致新入口出现多条空 diff 或自证式 fail-open 路径；它虽已有 selftest 15s 行为闸，仍停留 ledger。
  same_class: L164 与已晋升 L171 都要求在新增入口或把能力委托下游前显式钉住信任边界；将两者合并为 [L164][L171]，不把安全入口纪律塞进无关的测试或脚本编码铁律。
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((pwsh -NoProfile -File scripts/triage.ps1 scan -NoWrite | Select-String -SimpleMatch 'lessons-promote L164 ')) { throw '[TD154-L164-STILL-LEDGER]' }
dod_exit: 0
dod_assert: L164 meta 精确变为 tier=must；CLAUDE 的 [L171] 原位合并为 [L164][L171]，保留委托路径须在唤起前校验与响应单源，并明确新增 fail-closed 入口须把判定对象钉不可变 id、判定标准取受信基线、检查器及依赖取受信检出、入参只收纯名或完整 OID 并按提交身份精确比较、引用只解析一次且全程钉 SHA；lessons check 证明 ledger must=17、CLAUDE 铁律条目仍=10、cap=10；triage 不再报告 L164
review_gate: codex {verdict:pass}
hygiene: 只扩一条现有信任边界铁律，不复制 L164 十一轮事故长文，不新增脚本或测试
doc_sync: merge 后将 TD154 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-GATE-ENTRY-TRUST-BINDINGS

## 产出

把 L164 的 fail-closed 新入口信任绑定放进每轮必载上下文，并与 L171 的下游委托边界保持单一铁律。

## RED-first

在实现 worktree 先运行卡片 DoD。当前 L164 仍为 `tier=ledger`，triage 必须以 `lessons-promote L164` 令 DoD 非零；若没有这条 RED，不得编辑 `CLAUDE.md` 或 LEDGER。

## 最小实现

把 L164 meta 改为 `must`，把现有 `[L171]` 标题改为 `[L164][L171]`，正文追加 L164 五项信任绑定。保留 L171 已有的“不安全路径不交下游、唤起前校验、响应单源”全文语义。

## 被否决方案

- 不新增第 11 条 CLAUDE bullet：两条 lesson 同属入口/委托信任边界，可原位合并。
- 不只写“使用受信来源”：必须展开对象、标准、检查器、入参身份、SHA 钉扎五项，否则仍会漏隐式绑定。
- 不顺手修改 scope/selftest：生产防线已有 15s 行为闸，本卡只偿还默认上下文债。
