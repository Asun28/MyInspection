---
id: T0-SCAFFOLD-SYNC-043
title: 装上 fleet 双向回路，并回填上游 v0.43.0 里由本项目提出的三条修复
depends_on: []
parallelizable_with: []
status: in-progress
branch: T0-SCAFFOLD-SYNC-043
worktree: C:\wt\T0-SCAFFOLD-SYNC-043
allow_paths:
  # 横切卡（L97）：改的是「必须层驻留规则怎么计量」这条规则本身，教它的面一次扫齐——
  # 判定核 / 两个消费者（lessons.ps1 + triage.ps1）/ 接线闸 / 两处教学面 / 决策账。
  - scripts/_lessons.ps1
  - scripts/scaffold-sync.ps1
  - scripts/_config.ps1
  - scripts/_cards.ps1
  - scripts/lessons.ps1
  - scripts/triage.ps1
  - scripts/selftest.ps1
  - docs/SCAFFOLD-SYNC.md
  - docs/LOOP-ENGINEERING.md
  - docs/DELIVERY-CHAINS.md
  - .claude/skills/triage/SKILL.md
  - CLAUDE.md
  - docs/lessons/LEDGER.md
  - specs/tasks/T0-SCAFFOLD-SYNC-043.md
forbid:
  - 未授权的运行期出站网络 / 写登录态 / 自动发布（`scaffold-sync.ps1` 的 `check -Fetch` 与 `report -Send` 均须显式旗标）
  - 改动冻结契约或 schema（本卡不碰 android/）
  - 把 `ScaffoldVersion` 从 0.29.0 改掉——它是**生成来源**戳，不是「已回填到哪版」；后者在决策账里
  - 为了让 `lessons.ps1 check` 变绿而抬高 `LessonsMustCap`——那正是本卡要修的静默失效
non_goals:
  - 上游 v0.30.0 / v0.31.0 的 R3 降级与其防御拆除（本项目刻意分叉，理由写进决策账，不再复议）
  - v0.34.0 / v0.36.0 的 ASCII 状态码迁移（约 60 处再锚定，另开卡）
  - v0.35.0 的经验冷热分离（`T0-LESSONS-COLD-RECALL` PR #51 已在独立实现，勿重复施工）
  - v0.43.0 的 #186/#187/#191/#192/#193/#194/#195、#180、#181/#182（逐项理由见决策账「Still open」表）
  - 为降层而**改写任何一条经验的 rule 正文**（只动 tier 与 CLAUDE.md 的驻留面；LEDGER 里的条目一字不改，降层不是删除）
dod_command: pwsh -NoProfile -Command "if (-not ((& pwsh -NoProfile -File scripts/triage.ps1 selfcheck | Select-String -SimpleMatch 'triage selfcheck: PASS') -and (& pwsh -NoProfile -File scripts/scaffold-sync.ps1 selfcheck | Select-String -SimpleMatch 'scaffold-sync selfcheck: PASS') -and (& pwsh -NoProfile -File scripts/lessons.ps1 check | Select-String -SimpleMatch '驻留 id=9'))) { exit 1 }"
dod_exit: 0
dod_assert: 两个 selfcheck 各打印自己的 PASS 行（覆盖新探针 10/11/12 与 fleet 解析核），且 lessons.ps1 check 在生产路径上按**驻留 id** 报出 9（不是按条目报 7）——证明新口径不只在夹具里生效
review_gate: codex {verdict:pass}
hygiene: 7 枚单句变异逐一击杀（M1 封顶退回按条目计数 / M2 撤 enforced_by 闸 / M3 enforced_by 正则退回跨行 / M4 delivery-blocked 不再按 verdict 过滤 / M5 降层不再要求守卫 / M6 selfcheck 不再注入夹具总账 / M7 卡片解析去掉 \uFEFF? 锚点），每枚还原后核 SHA256 逐字节一致
doc_sync: CLAUDE.md 权威文档索引 + 资产沉淀归位第四去处 · docs/SCAFFOLD-SYNC.md 决策账 · docs/LOOP-ENGINEERING.md 与 triage skill 的探针枚举 · docs/DELIVERY-CHAINS.md 心跳行（R5）
---

# T0-SCAFFOLD-SYNC-043

## 产出

把本项目与上游脚手架之间的关系从「一次性快照 + 手工记忆」变成**有账可查的回路**，并把上游 v0.43.0
里**由本项目提出**的三条修复接回来。

- `scripts/scaffold-sync.ps1` + `_config.ps1` 的 `UpstreamRepo` + 心跳探针 12 `scaffold-stale`
  + `docs/SCAFFOLD-SYNC.md`（链文档与决策账合一，v0.30.0–v0.43.0 **逐版**已判）。
- `scripts/_lessons.ps1`：必须层驻留规则的**唯一判定核**，两个消费者共用，不会漂移。
- 上游 #188：封顶按**驻留经验 id** 计，不按 markdown 条目计。
- 上游 #189：晋升探针读 `enforced_by`（已有机械守卫的坑不该再花每轮上下文），并补上逆向探针 10 `lessons-demote`。
- 上游 #190：探针 11 `delivery-blocked` —— 唯一读**交付**状态的探针。
- 上游 v0.41.0 TD130：卡片 front-matter 解析容忍前导 U+FEFF。

## 为什么这三条是本项目的事

2026-08-21 从本仓向上游提了三个 issue：#183（晋升探针无视自己声称支持的 `enforced_by`）、
#184（必须层封顶数的是条目、不是它要管的上下文成本）、#185（十个探针全都对交付状态失明）。
v0.43.0 的 #189/#188/#190 就是这三条的修复。**回填它们不是跟版本号，是把自己报上去的洞补上。**

`#184 在本仓是活的**：`origin/master` 的 `CLAUDE.md` 经验铁律小节是 **10 条 bullet 承载 19 个
经验 id**，而 `LessonsMustCap = 10`。按条目计恒绿，按 id 计已是上限的近两倍——本卡把计量单位改对，
于是这条一直存在的超限**第一次变红**。**红是正确结果**，不在本卡里靠抬高上限抹平。

## 禁止

见 front-matter `forbid`。特别地：`check -Fetch` 与 `report -Send` 是仅有的两个网络出口，
且都要显式旗标；心跳探针 12 只读**已经取到本地**的 ref，绝不 fetch——`docs/LOOP-ENGINEERING.md`
的「心跳只读、离线、确定性」是刻意不变量。

## 非目标（本卡刻意不做的能力）

见 front-matter `non_goals`。每一条在 `docs/SCAFFOLD-SYNC.md` 的决策账里都有对应行与理由——
**不回填是一等结果，不登记不是**。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -File scripts\triage.ps1 selfcheck
pwsh -NoProfile -File scripts\scaffold-sync.ps1 selfcheck
pwsh -NoProfile -File scripts\triage.ps1 scan -NoWrite
```
- 期望退出码：0（三者皆是 reporter，恒 0；断言看输出）
- 断言：`triage selfcheck: PASS` + `scaffold-sync selfcheck: PASS` + 生产扫描里出现 `lessons-cap`
- 变异证据：见 `hygiene`，7 枚单句变异各自被点名的用例杀死；每枚还原后 SHA256 逐字节核对

## 必须层减法（同批完成）

按驻留 id 计数一开，`CLAUDE.md` 经验铁律小节露出 **19 个驻留 id / 上限 10**——10 条 bullet 把 19 条规则
藏成了「10 条」。同批做完减法，**依据全部取自 LEDGER 自己的字段**，不凭手感：

- **已有确定性守卫（4 条）** —— L3 · L164 · L171 · L181：`enforced_by` 指向真实闸门，每轮上下文买的是机器
  已经在做的事（心跳探针 10 `lessons-demote` 正是按这条判据提名的）。
- **`recurrence: 1`（6 条）** —— L95 · L162 · L167 · L172 · L177 · L214：登记至今只响过一次，够不上「踩过**且会复发**」。
- **留任 9 条**：L97(8) · L196(6) · L205(5) · L1(3) · L17(3) · L193(3) · L21(2) · L165(2) · L190(2)。

降层 = `tier: must` → `tier: ondemand`，**条目正文一字未动**（降层不是删除，`search`/`promote` 照常找得到）。
合并 bullet 拆分时，留任条目的正文逐句按各自 `rule:` 字段归属核过，不是整段砍。
结果 **9 个驻留 id / 7 条目**，`lessons.ps1 check` 与 selftest 闸 2 同时转绿。