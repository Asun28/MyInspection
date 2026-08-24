---
id: T0-LESSONS-BUMP-PLANE
title: bump 写主检出账本，复发计数不再随卡片 diff 丢失
depends_on: []
parallelizable_with: []
status: merged
branch: T0-LESSONS-BUMP-PLANE
worktree: C:\wt\T0-LESSONS-BUMP-PLANE
allow_paths:
  - scripts/lessons.ps1
  - scripts/selftest.ps1
  - .claude/hooks/lessons-reminder.ps1
  - docs/lessons/LEDGER.md
  - docs/LESSONS.md
forbid:
  - fail-closed 之外的任何兜底（尤禁「主检出账本缺失就静默回落到 worktree 账本」——那正是本卡要修的 bug 原样复活）
  - 改 MustCap、降级 L165 的单句删除变异要求、或用「看起来对」代替夹具证明
  - 把裁断/证据写进 allow_paths 之外的文件（CLAUDE.md 经验铁律本卡不动）
  - 未授权的运行期出站网络 / 写登录态 / 自动发布
non_goals:
  - 改 add 的写入平面（经验随卡入库是既有实践，L241 经 PR#124 入库即先例；本卡只收 bump 这条纯元数据路径）
  - 改 promote 的行为（实测只打印晋升建议、不写盘，无同类缺陷）
  - 给 L226 建机械守卫（写回前后核前 3 字节的 BOM 闸）——另开卡，本卡只补 enforced_by 的书面裁断
  - 动 Tier-1 名额或改 CLAUDE.md 经验铁律（本卡的晋升结论是「两条都不进必须层」，故不需要该文件）
  - 给 LEDGER 加锁或解决并发会话同时改账本的文本冲突
diagnosis:
  root_cause: scripts/lessons.ps1:64 把 `$Ledger` 绑到 `$PSScriptRoot/..`，于是**写入平面 = 脚本所在检出**。recurrence 是仓库级元数据、与任何卡片无关，但在 linked worktree 里跑 bump 就写进了卡片分支的 LEDGER.md，被范围闸与 R3 #7（夹带无关改动）正确拒绝。丢失因此是**结构性**的：card 工作全在 worktree 里做，所以「工作中发现的复发」几乎必然记不上，晋升门槛（recurrence≥2）被系统性低估。
  same_class: 已排查全部三个写/读账本的子命令。`add` 同样从 `$RepoRoot` 派生但**属有意随卡入库**（见 non_goals），不改；`promote` 只 Write-Host 建议不写盘；`check`/`list`/`search` 只读。故同类站点仅 bump 一处，非「一处 patch 掩盖 N 处缺陷」。
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch '--git-common-dir') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-PLANE-UNRESOLVED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'LSN-PLANE-WORKTREE') -and (Select-String -Path .claude/hooks/lessons-reminder.ps1 -SimpleMatch 'bump'))) { exit 1 }"
dod_exit: 0
dod_assert: 静态哨兵四项齐（lessons.ps1 走 --git-common-dir 解析且带 ASCII 失败码 [LSN-PLANE-UNRESOLVED]；selftest 有 LSN-PLANE-WORKTREE 夹具；Stop 钩子模板出现 bump）。行为真相由 selftest 闸 2d 的 hermetic 夹具证明：在 linked worktree 里跑 bump，主检出账本 +1 且 worktree 账本逐字节不变；主检出账本缺失时非零退出并打印 [LSN-PLANE-UNRESOLVED]，绝不静默回落。既有闸 2b/2c（非 git 临时夹具）保持绿。
review_gate: codex {verdict:pass}
hygiene: 三枚单句删除变异各配判据分类器（非零且命中指定失败文案才算击杀）：①删 git-common-dir 解析 → 闸2d 正向红；②把 fail-closed 抛错换成回落 `$RepoRoot` → 闸2d 反向红；③删钩子模板里的 bump 行 → 闸9f 扩展断言红。不把当前 recurrence 数值写死进夹具。
doc_sync: docs/LESSONS.md 写明「复发计数=仓库级元数据，写主检出、永不进卡片 diff」；R5 另在 CLAUDE.md 当前阶段与 docs/TASK-BOARD.md 记状态（卡外）
---

# T0-LESSONS-BUMP-PLANE

## 产出

三件，围绕同一条因果链：复发计数记不上 → 晋升门槛失真。

1. **`bump` 的写入平面改为主检出**（`scripts/lessons.ps1`）。在 linked worktree 里跑也立刻落账，且永远不进卡片 diff。
2. **Stop 钩子模板补 bump 入口**（`.claude/hooks/lessons-reminder.ps1`）。现模板只讲 `add` 与 `promote`，从头到尾没出现过 `bump` 这个词，于是「复发要计数」这条路径在提醒里是隐形的。
3. **L226 / L106 的晋升判定各留一句书面裁断**（`docs/lessons/LEDGER.md`），把本次 recurrence 跃迁的证据与结论钉在条目里。

## 当前基线（触发本卡的真实事件）

2026-08-23：L226 当天触发 3 次、L106 触发 2 次。两笔 bump 被 R3 判为卡 1 范围之外并**正确**拦下，计数遂无处可去。人裁后在主检出补记：L226 `1 → 4`、L106 `2 → 4`，`lessons.ps1 check` PASS。这两笔已落盘（未提交），本卡不重复补记，只承接「下次别再丢」。

## 设计契约（单一出生点）

`bump` 解析账本路径的唯一函数（建议 `Resolve-LedgerPlane`），四步、顺序不可换：

1. `git -C <RepoRoot> rev-parse --git-common-dir`。
2. **git 不可用或非仓库**（非零退出或空输出）→ 回落 `$RepoRoot`。这一步**必需**：既有闸 2b/2c 的 hermetic 夹具是「只拷 `scripts/` 的非 git 临时目录」，此处硬失败会把两枚既有闸打红。
3. 有输出 → 主检出 = 该路径的父目录。**注意相对形态**：主检出里跑时 git 返回相对的 `.git`（`Split-Path '.git' -Parent` 得空串），必须先相对 `$RepoRoot` 解析成绝对路径再取父级；linked worktree 里才返回主仓 `.git` 的绝对路径。两种形态都要有夹具覆盖。
4. 解析出的主检出下 `docs/lessons/LEDGER.md` **不存在** → **fail closed**：抛 `[LSN-PLANE-UNRESOLVED]` 并给出可操作修法。**禁止**回落到 `$RepoRoot` 的账本。

仅作用于 `bump`。`add`/`promote`/`check` 路径逐字不动。

## 晋升裁断（本卡的结论，写进各自 LEDGER 块）

- **L226**（minor · recurrence 4 · `enforced_by` 空）：**不进必须层**，改为补 `enforced_by` 指向待建的 BOM 写回闸。理由：它的规则纯机械可检（写回前后核前 3 字节），Tier-1 的 10 个名额（master 现 9）应留给不可机检的判断型铁律；「写完立刻复核」这一通用形态已被 L165 覆盖。
- **L106**（major · recurrence 4）：**维持不 promote**，结论不变。其块内已有 2026-07-23 的书面裁断（已被 L157「落盘改动先对 diff --stat」覆盖，同 L61/L148 降级先例），本卡只把 recurrence 4 的新证据追加进去。

两条都不动 Tier-1，故 `CLAUDE.md` 不在 `allow_paths`。若评审或用户要推翻其中任一条，属能力级扩张，记 `[FOLLOW-UP]` 另开卡。

## 资源冲突

`scripts/selftest.ps1` 与 `T0-R3-DIFF-BUDGET`、`T0-CI-MERGE-GATE`、`T0-LESSONS-COLD-RECALL` 重叠；`scripts/lessons.ps1`、`docs/LESSONS.md` 与 `T0-LESSONS-COLD-RECALL` 重叠。故 `parallelizable_with: []`：不得与上述卡同时向同一基线合并，须串行占用或合并前重放完整验收。`docs/lessons/LEDGER.md` 另有并发会话在改，合并时文本冲突按「两块都保留」处理。

## 禁止

见 front-matter `forbid`。额外一条形态提醒：`.ps1` 一律走 PowerShell 工具（L17）；重写既有脚本前后核 BOM（L226 本人）。

## 非目标（本卡刻意不做的能力）

见 front-matter `non_goals`。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch '--git-common-dir') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-PLANE-UNRESOLVED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'LSN-PLANE-WORKTREE') -and (Select-String -Path .claude/hooks/lessons-reminder.ps1 -SimpleMatch 'bump'))) { exit 1 }"
```

- 期望退出码：0
- 断言：见 front-matter `dod_assert`。DoD 是沙箱可复跑的静态代理（L60）；行为强制点在 `scripts\selftest.ps1 -Shard core` 的闸 2d 与扩展后的闸 9f。

### 闸 2d 夹具形状（实现指引）

沿用闸 2b/2c 的手法（忠实 `Copy-Item scripts/`），但这次夹具必须**是个 git 仓**并挂一棵 linked worktree：

- 建临时仓 → 种一条 `recurrence: 3` 的 fixture 经验 → `git commit` → `git worktree add`。
- 从 **worktree 那份** `scripts/lessons.ps1` 跑 `bump L1`：断言主检出账本 meta 行变 4，且 worktree 账本 SHA256 与 bump 前逐字节相同。
- 反向：删掉主检出账本再跑 → 断言非零退出且 stdout/stderr 含 `[LSN-PLANE-UNRESOLVED]`。
- 主检出直跑一次（覆盖 `--git-common-dir` 返回相对 `.git` 的形态）→ 断言仍正确 +1。
