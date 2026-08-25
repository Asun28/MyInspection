---
id: T0-SCAFFOLD-FLEET-LOOP
title: fleet 双向回路——逐版决定、回填 v0.44 账域修复并留账
depends_on: [T0-LESSONS-CAP-UNIT]
parallelizable_with: []
status: in-progress
branch: T0-SCAFFOLD-FLEET-LOOP
worktree: C:\wt\T0-SCAFFOLD-FLEET-LOOP
allow_paths:
  # 横切卡（L97）：新增一个探针要同步「探针数 + 探针名」的全部教学面，缺一处即闸 14a/14d 红。
  - scripts/scaffold-sync.ps1
  - scripts/_config.ps1
  - scripts/triage.ps1
  - docs/SCAFFOLD-SYNC.md
  - docs/LOOP-ENGINEERING.md
  - docs/DELIVERY-CHAINS.md
  # Reference-only teaching surfaces for future harness changes; they do not add product coding requirements.
  - docs/HARNESS-REVIEW.md
  - docs/scaffold-architecture.html
  - .claude/skills/triage/SKILL.md
  - CLAUDE.md
  - specs/tasks/T0-SCAFFOLD-FLEET-LOOP.md
forbid:
  - 把 `ScaffoldVersion` 从 0.29.0 改掉——它是**生成来源**戳，不是「已回填到哪版」，后者只在决策账里
  - 让探针 12 `scaffold-stale` fetch——心跳的「只读、离线、确定性」是刻意不变量，刷新归显式的 `check -Fetch`
  - 自动打补丁：拿哪一版是判断题，脚本不替人做
non_goals:
  - 回填上游 v0.30.0 / v0.31.0（R3 降级与防御拆除）——本项目刻意分叉，理由已写进决策账，不再复议
  - v0.34.0 / v0.36.0 的 ASCII 状态码迁移、v0.43.0 的 #186/#187/#191/#192/#193/#194/#195 等——逐条理由见决策账「Still open」表
  - v0.44.0 的 card-validation / shared-core selfcheck / handoff throttle 三组——各自需要独立范围与迁移测试，理由见决策账
dod_command: pwsh -NoProfile -Command "if (-not ((((& pwsh -NoProfile -File scripts/scaffold-sync.ps1 selfcheck) -match 'scaffold-sync selfcheck: PASS').Count -eq 1) -and (((Select-String -Path scripts/triage.ps1 -Pattern 'Invoke-Probe[A-Za-z]+ \{' -AllMatches).Count) -eq 11))) { exit 1 }"
dod_exit: 0
dod_assert: `scaffold-sync selfcheck` 打印 ASCII 哨兵 PASS（覆盖版本解析 / 更新集 / Downstream 块切割 / 决策账高水位与账域四负例 / 心跳离线不变量），且 `triage.ps1` 恰 11 个探针函数——闸 14a/14d 据此反查 docs 计数与探针名枚举，少同步一处即红
acceptance:
  - "A1 账域上界：sentinel 上方放一条形如 | v0.99.0 | applied | ... | 的合法账行，Get-SyncedVersion 仍精确返回账内 v0.42.0"
  - "A2 账域下界：sentinel 下方另表第二列提及 v0.99.0，Get-SyncedVersion 仍精确返回账内 v0.42.0"
  - "A3 缺 sentinel fail-closed：只有 v0.99.0 applied 表但无 sentinel 时精确回退 provenance 0.30.0"
  - "A4 行形：sentinel 下方以 v0.99.0 开头但第二列是日期而非 applied/partial/skipped 时精确返回账内 v0.42.0"
  - "A5 真实决策账：v0.44.0 行为 partial 且点名已取 sync-ledger 组、三组 deferred 与各自本地理由；ScaffoldVersion 仍精确为 0.29.0"
  - "A6 探针清单：selftest core 的 14g② 对五处枚举面逐一比对，11 枚探针均在场且 docs/HARNESS-REVIEW.md 与 docs/scaffold-architecture.html 都点名 scaffold-stale"
  - "A7 决策账只认唯一整行 marker 与 canonical decision；本地 tag 缺行、坏 decision、重复版本均 fail-closed"
  - "A8 公共 report 同扫标题/正文；scanner 不可用、命中 secret 或配置解析失败时 -Send 必拦，旧配置缺可选 getter 仍兼容"
  - "A9 scaffold-stale 的 self-repo/no-tags/current/behind 四态、DESCRIPTION 与末行调用锚定、for-each-ref→fetch 变异均有 hermetic 断言"
review_gate: codex {verdict:pass}
hygiene: v0.44.0 四枚账域负例已先红后绿；删除 sentinel 状态门、决策枚举判断或首列版本解析中的任一条，至少一枚夹具转红；真实账无行时仍回退溯源戳 0.29.0
doc_sync: CLAUDE.md 权威文档索引 + 资产沉淀归位第四去处 · LOOP-ENGINEERING 与 triage skill 的探针枚举与计数 · DELIVERY-CHAINS 心跳行（R5）
---

# T0-SCAFFOLD-FLEET-LOOP

## 产出

把本项目与生成它的脚手架之间的关系，从「一次性快照 + 手工记忆」变成**有账可查的回路**。

- `scripts/scaffold-sync.ps1`：`check` 只打印落后区间每一版的 CHANGELOG「Downstream 块」——
  **那里的耦合组是 raw `git diff` 永远推不出来的信息**，而只拿一半是回填最贵的失败模式；
  `report` 把元层缺陷反哺成上游 issue（过防泄露闸，须显式 `-Send`）；`selfcheck` 是 hermetic 自检。
- `_config.ps1` 新增 `UpstreamRepo` + `Get-ScaffoldUpstreamRepo`（`ContainsKey` 守卫，旧 `_config` 在 StrictMode 下不抵）。
- 心跳探针 12 `scaffold-stale`：只读**已取到本地**的 ref 与决策账，**绝不 fetch**。
- `docs/SCAFFOLD-SYNC.md`：链文档与决策账合一，**v0.30.0–v0.44.0 逐版已判**。

## 为什么账比补丁重要

`init` 是一次性快照，放着不管两个方向同时腐化：上游的修复到不了下游，下游发现的问题回不了上游。
**不回填是一等结果**——上游改动可以**对本项目而言**是错的（本仓与上游在「R3 是否为强制合并闸」上
刻意分叉，上游自己的 CHANGELOG 就写着「想要强制评审的下游应 pin ≤0.30.0」）。写下理由即算议完，
`check` 不再提它；**跳过是正当决定，不登记不是**。

## 这条回路已经付过两次费

- 本仓 2026-08-21 提的 #183/#184/#185，回来变成上游 v0.43.0 的 #189/#188/#190（由前两张卡回填）。
- 装回路的过程本身又抓到回路自己的缺陷：`Get-SyncedVersion` 无视自己的 `SCAFFOLD-SYNC-LEDGER`
  哨兵、读遍全文所有表格，于是账外任何一处裸版本字样都可能劫持高水位、把落后报成 current。
  已反哺上游 **#201**；v0.44.0 的修复已按本项目账形回填，并由四枚对抗夹具常设验证。


## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -File scripts\scaffold-sync.ps1 selfcheck
pwsh -NoProfile -File scripts\triage.ps1 scan -NoWrite
```
- 期望退出码：0
- 断言：`scaffold-sync selfcheck: PASS` + `triage.ps1` 恰 11 个探针函数
