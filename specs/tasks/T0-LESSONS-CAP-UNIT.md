---
id: T0-LESSONS-CAP-UNIT
title: 必须层封顶改按驻留经验 id 计量，并把 enforced_by 做成双向判据（横切卡，尺寸见 allow_paths）
depends_on: [T0-LESSONS-TIER1-CUT]
parallelizable_with: []
status: in-progress
branch: T0-LESSONS-CAP-UNIT
worktree: C:\wt\T0-LESSONS-CAP-UNIT
allow_paths:
  # 横切卡（L97）：改的是「必须层驻留规则怎么计量」这条规则本身，教它的面一次扫齐——
  # 判定核 / 两个消费者 / 接线闸 / 教学面。>5 是横切的固有形态，非 scoping 失误。
  - scripts/_lessons.ps1
  - scripts/lessons.ps1
  - scripts/_cards.ps1
  - scripts/selftest.ps1
  - scripts/triage.ps1
  - CLAUDE.md
  - docs/LOOP-ENGINEERING.md
  - docs/DELIVERY-CHAINS.md
  - .claude/skills/triage/SKILL.md
  - specs/tasks/T0-LESSONS-CAP-UNIT.md
  # 2026-08-23 扩围：pre-R3 独立复核（Opus 5）扫出 L97 清单**漏了五处权威面**，它们仍在教被本卡
  # 废止的「条数」单位、或仍按旧探针数枚举心跳。L97 明写这类面须**一次性纳入 allow_paths**，
  # 分两张卡扫必然多打一轮 R3，故就地扩围而非另开卡（扩围落在 pinned base = master，符合范围闸语义）。
  - docs/LESSONS.md                     # :10 三层表 Tier-1 容量格仍写「封顶 N 条」——权威文档 #4
  - .claude/skills/lessons/SKILL.md     # :18 同上；:33 PURIFY 步骤写「必须层≤上限」
  - scripts/_config.ps1                 # :140 LessonsMustCap 定义处注释仍写「封顶条数」；本卡注释三次指向它
  - docs/HARNESS-REVIEW.md              # :137 自动触发点只列三枚探针，新探针的 next 串却指回本文
  - docs/scaffold-architecture.html     # :468 心跳节点枚举 8 枚探针，缺新增两枚
forbid:
  - 抬高 `LessonsMustCap`
  - 让心跳探针打网络或调 gh（`delivery-blocked` 只读 `.review/*.json`；心跳恒离线、退出码恒 0）
  - 新增第二处「驻留 id 怎么数」的实现——判定核只有 `scripts/_lessons.ps1` 一处
non_goals:
  - fleet 回路与探针 12 `scaffold-stale`（那是 `T0-SCAFFOLD-FLEET-LOOP`，本卡的下一张）
  - 必须层减法本身（上一张卡 `T0-LESSONS-TIER1-CUT` 已做完）
dod_command: pwsh -NoProfile -Command "if (-not ((((& pwsh -NoProfile -File scripts/triage.ps1 selfcheck) -match 'triage selfcheck: PASS').Count -eq 1) -and (((& pwsh -NoProfile -File scripts/lessons.ps1 check) -match 'id=9').Count -eq 1))) { exit 1 }"
dod_exit: 0
dod_assert: `triage selfcheck` 打印 ASCII 哨兵 PASS（覆盖新探针 10/11 与改口径后的探针 1/5，含批量窗口两侧边界与主检出 `.review` 取证路径，全部走 hermetic 夹具；`_cards.ps1` 的 BOM 分支由 selftest 闸 10d(BOM/纯函数) 常设守住），且 `lessons.ps1 check` 在**生产路径**上按驻留 id 报出 9——不是按条目报 7，证明新口径不只在夹具里生效
review_gate: codex {verdict:pass}
hygiene: 12 枚单句变异逐一击杀，每枚还原后核 SHA256 逐字节一致——封顶退回按条目计数 / 撤 enforced_by 闸 / enforced_by 正则退回跨行 / delivery-blocked 不再按 verdict 过滤 / 降层不再要求守卫 / selfcheck 不再注入夹具总账 / 批量阈值 `-gt` 退化成 `-ge`（off-by-one）/ 摘掉主检出 `.review` 取证路径 / `_cards.ps1` 去掉 `\uFEFF?` 锚点后带 BOM 的卡 front-matter 解析成 null
doc_sync: CLAUDE.md 计量单位说明 · LOOP-ENGINEERING 与 triage skill 的探针枚举与计数 · DELIVERY-CHAINS 心跳行（R5）
---

# T0-LESSONS-CAP-UNIT

## 产出

把「必须层封顶」的**计量单位**从 markdown 条目改成**驻留的经验 id**，并让 `enforced_by`
在晋升与降层两个方向上都作数。

- `scripts/_lessons.ps1`（新）—— 判定核**只此一处**，`lessons.ps1 check` 与心跳探针 5 共用，不会漂移。
- 探针 1 `lessons-promote` 加 `enforced_by` 闸与批量窗口；新增探针 10 `lessons-demote`（逆向）。
- 新增探针 11 `delivery-blocked` —— 唯一读**交付**状态的探针，离线读 `.review/*.json`。
- `_cards.ps1` front-matter 容忍前导 U+FEFF（上游 v0.41.0 TD130）。

## 这三条修复的来历

2026-08-21 从本仓向上游提了三个 issue：#184（封顶数的是条目、不是它要管的上下文成本）、
#183（晋升探针无视自己声称支持的 `enforced_by`）、#185（十个探针全都对交付状态失明）。
上游 v0.43.0 的 #188/#189/#190 就是这三条的修复。**回填它们不是跟版本号，是把自己报上去的洞补上。**

## 为什么依赖上一张卡

改对单位后，`CLAUDE.md` 的真实驻留数会第一次被如实报出。上一张卡已把它从 19 降到 9，
所以本卡落地即绿；**反过来先改单位，selftest 闸 2 会当场红在 19>10 上**。

## 禁止 / 非目标

见 front-matter。心跳的「只读、离线、确定性」是刻意不变量（`docs/LOOP-ENGINEERING.md`）：
`delivery-blocked` 不调 gh，因为 `review.ps1` 每次跑都已把归一化裁决写在本地。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -File scripts\triage.ps1 selfcheck
pwsh -NoProfile -File scripts\lessons.ps1 check
```
- 期望退出码：0
- 断言：`triage selfcheck: PASS` + `lessons.ps1 check` 报驻留 `id=9`