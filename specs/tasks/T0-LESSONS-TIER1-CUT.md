---
id: T0-LESSONS-TIER1-CUT
title: 必须层减法——驻留经验 id 从 19 降到 9，依据全取自 LEDGER 自己的字段
depends_on: []
parallelizable_with: []
status: in-progress
branch: T0-LESSONS-TIER1-CUT
worktree: C:\wt\T0-LESSONS-TIER1-CUT
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
  - specs/tasks/T0-LESSONS-TIER1-CUT.md
  - specs/tasks/T0-SCAFFOLD-SYNC-043.md
forbid:
  - 抬高 `LessonsMustCap`（那是把超限抹平，不是做减法）
  - 改写任何一条经验的 `rule` 正文——降层不是删除，只动 `tier` 与 CLAUDE.md 的驻留面
  - 凭手感挑降层对象：每一条都须能指回 LEDGER 里的 `enforced_by` 或 `recurrence` 字段
non_goals:
  - 把封顶的**计量单位**从条目改成驻留 id（那是 `T0-LESSONS-CAP-UNIT`，本卡的下一张）
  - 心跳探针的任何改动（同上）
dod_command: pwsh -NoProfile -Command "if (-not ((((Select-String -Path docs/lessons/LEDGER.md -Pattern 'tier: must' -AllMatches).Matches.Count) -eq 9) -and (((& pwsh -NoProfile -File scripts/lessons.ps1 check) -match 'check: PASS').Count -eq 1))) { exit 1 }"
dod_exit: 0
dod_assert: LEDGER 恰 9 处 `tier: must`（降层前 19），且 `lessons.ps1 check` 打印 ASCII 哨兵 `check: PASS`——后者同时钉住「LEDGER 的 must 集合 ↔ CLAUDE.md 铁律小节」双向一致，少改一边即红
review_gate: codex {verdict:pass}
hygiene: 无新增断言逻辑，故不配变异；证伪面 = check 的双向一致校验（任删一边即红，已实测）
doc_sync: CLAUDE.md 经验铁律小节即本卡产出本体（R5 无额外同步面）
---

# T0-LESSONS-TIER1-CUT

## 产出

`CLAUDE.md` 经验铁律小节从 **10 条 bullet / 19 个驻留 id** 降到 **7 条 bullet / 9 个驻留 id**，
对应 LEDGER 里 10 条经验 `tier: must` → `tier: ondemand`。

## 为什么现在做

封顶写着 10，实际驻留 19——因为计数器数的是 markdown 条目，而一条写着 `[L17][L162][L172][L177]`
的 bullet 对计数器是 1 条、对模型是 4 条规则。**计量单位的修复是下一张卡**；本卡先把超限本身还掉，
这样下一张卡把单位改对时不会当场把闸变红。**顺序反过来就会得到一条红着的分支**。

## 顺带退役合并态卡

`specs/tasks/T0-SCAFFOLD-SYNC-043.md` 是这批工作最初的**合并态**卡片，其 diff 达 125,742 字符、
超 R3 的 60,000 字符预算一倍有余——评审只会读到被截断的 diff。故按自然接缝拆成三张卡
（本卡 → `T0-LESSONS-CAP-UNIT` → `T0-SCAFFOLD-FLEET-LOOP`），原卡在本卡一并退役。

## 降层依据（逐条可指回 LEDGER 字段，不凭手感）

- **已有确定性守卫（4 条）** —— L3 · L164 · L171 · L181：`enforced_by` 指向真实闸门
  （`_guard.ps1`/各 gh 脚本 · selftest 闸 15s 哨兵族 · 闸 17t 两处）。每轮上下文换来的是机器已经在做的事，
  而 `docs/HARNESS-REVIEW.md` 两处都写着这种条目可以离开必须层。
- **`recurrence: 1`（6 条）** —— L95 · L162 · L167 · L172 · L177 · L214：登记至今只响过一次，
  够不上「踩过**且会复发**」这条必须层的入场券。
- **留任 9 条**：L97(8) · L196(6) · L205(5) · L1(3) · L17(3) · L193(3) · L21(2) · L165(2) · L190(2)。

## 合并 bullet 怎么拆

四条 bullet 承载了多条经验，拆分时**逐句按各自 `rule:` 字段归属核过**，不是整段砍：
`[L165][L167]` 去掉判据分类器那句 · `[L17][L162][L172][L177]` 只留 Bash/PowerShell 那句 ·
`[L196][L214]` 去掉「还原后再核」那半 · `[L181][L190][L193]` 去掉 Cc/Cf 类别那句。

## 禁止 / 非目标

见 front-matter。特别地：**降层不是删除**——条目原文一字未动，`lessons.ps1 search`/`promote` 照常找得到，
前提回来了就再升回去。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -File scripts\lessons.ps1 check
```
- 期望退出码：0
- 断言：LEDGER 恰 9 处 `tier: must` + 输出含 `check: PASS`