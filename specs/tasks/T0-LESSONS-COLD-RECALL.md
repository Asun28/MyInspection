---
id: T0-LESSONS-COLD-RECALL
title: 让一次性 lessons 可安全归冷且仍能统一检索
depends_on: []
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: [T0-HARNESS-SUBTRACTION-PROTOCOL]
status: in-progress
branch: T0-LESSONS-COLD-RECALL
worktree: C:\wt\T0-LESSONS-COLD-RECALL
allow_paths:
  - scripts/lessons.ps1
  - scripts/archive.ps1
  - scripts/selftest.ps1
  - docs/LESSONS.md
  - specs/archive/README.md
  - specs/tasks/T0-LESSONS-COLD-RECALL.md   # 本卡自身：验收集合与闸号分配随实现同步修订
forbid:
  - 复制上游具体 lesson 内容、归档结果或按固定数量搬运本仓条目
  - 自动归档 must/ondemand、当前最大 ID 或被常驻 CLAUDE 文件引用的条目
  - 新写第二套搬运器绕过 archive.ps1 的幂等和 fail-closed 保护
non_goals:
  - 降级 L165/L196
  - 删除任何 lesson 或改变 Next-Id 单调性
  - 同卡修改 task/review/CI merge 行为
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试；
  # 清单外的缺口记 [FOLLOW-UP] 开新卡，不在本卡 block。
  # 本清单由 2026-08-23 的 pre-R3 独立复核（Opus 5）产出。本卡是**搬运数据**的动作，
  # 故所有「谁会被搬走」的判定都按数据丢失级要求可证伪，不接受「整份 stdout 里出现过」这种宽断言。
  - "A1 候选集精确相等：hermetic 夹具下 archive -DryRun 的输出用 [LSN-ARCHIVE-DRYRUN] candidates= 后的判定串提取，再与**期望候选串逐字相等**比对（判等，不得用子串包含——那样任何一行无关告警提到该 id 都算过）。夹具须同时含「该进候选」与「被排除」两类条目，故期望串不止一个 id；条目增减时**同步改期望串**。本卡新增的 selftest 闸只许取闸号协调分配给本卡的 2f / 2g / 16a；确需另一枚时取当前空号 2i 并同步登记，不得占用未分配号"
  - "A2 排除面逐条可杀：同一夹具内 tier=must、tier=ondemand、recurrence=2、当前最大 ID 各一条，断言四者都不在 candidates 里，且实跑后其 (?m)^##\\s+L<id> 仍在 LEDGER"
  - "A3 常驻引用排除须覆盖**裸引用**形态：夹具 CLAUDE.md 分别写 [L5]、见 L6 之理（裸）、L7–L8（EN DASH 范围）三种，断言四个 id 全不进 candidates；另配 scripts/x.ps1:L9（冒号前缀＝源码行号）与 L10-13（ASCII 连字符＝行段）两条**不算引用**的否定形态，断言二者仍**进** candidates。判定式 (?<![A-Za-z0-9:])L(\d+)\b(?!-\d) 全仓**只保留一份字面量**；出生点放哪个文件由实现方选定并在该处注释点名，另一处须从出生点取用、取不到即 fail-closed 变红。另配一条断言，证明两处对同一份 CLAUDE.md 推出逐条相同的 id 集合。范围简写不保护中间 id 这一点写进 docs/LESSONS.md（本条不为该句要求机检）"
  - "A4 正文诱饵 tier 单独可杀：夹具 L2 的规范 meta 行**只**缺 tier、其余合法，正文含 tier: ledger；断言 L2 不进 candidates、[LSN-META-INVALID] 点名 L2、实跑后仍在 LEDGER"
  - "A5 正文诱饵 recurrence 单独可杀：夹具 L20 的 meta 行**只**缺 recurrence，正文 - rule: 行含 recurrence: 1；断言三项同 A4（与 A4 必须是两枚夹具，共用一枚则两道守卫无法各自被变异杀死）"
  - "A6 无 meta 行与缩进近似形态：一条整块无任何 - date: 行、一条只有行首两空格的缩进 meta 行；两条都须被 [LSN-META-INVALID] 点名、都不进 candidates、实跑后都仍在 LEDGER"
  - "A7 重复 meta 行读写两侧：读侧留热已证；写侧追加断言 bump 该 id 非零退出且 LEDGER 字节 SHA256 前后不变（Replace 无次数上限时两条 meta 行会被同时改写）"
  - "A8 非法值面六枚逐一可杀：同名字段重复、date: 2026-8-1、severity: fatal、kind: bogus、recurrence: many、recurrence 超 Int32 上界，各一条夹具；六条全部被 [LSN-META-INVALID] 点名且实跑后仍在 LEDGER。溢出那条另断言**只读召回面不被一条坏条目废掉**：list 与 search **exit 0** 且输出不含裸 .NET 转换异常文本。check 按 A10 仍须**非零退出**——本条的「可用」指不抛裸异常，不指 exit 0，二者不冲突"
  - "A9 bump 只信规范 meta 行：夹具 meta 行无 recurrence、正文含 recurrence: 7；断言 bump 非零退出并输出 [LSN-META-INVALID]，且 LEDGER 字节 SHA256 前后不变（当前实现会打印绿字 7 → 8、exit 0、实际零写入，并建议 promote）"
  - "A10 check 两向：含 A4–A8 任一非法条目时 check 非零退出且输出含 [LSN-META-INVALID]；同时对真仓全量 LEDGER 跑 check 仍 exit 0，防新校验器误伤既有账本"
  - "A11 冷项 round-trip：搬冷后 search 在冷段以 [archived] L<id> 召回；bump 与 promote 均非零退出，输出含 [LSN-ARCHIVED-READONLY] 与「移回 docs/lessons/LEDGER.md」的修法"
  - "A12 闸 16 并集接线非真空：夹具仓造只存在于冷库的 ## L903 并从 docs/ 引用它；把 Get-LessonDefinitionIdSet 调用处的 -ArchivePath 实参删掉即变红（只直测 helper 证明不了接线）"
  - "A13 预览与实跑同口径：archive -DryRun 须透传到 archive.ps1 -DryRun。经本入口**实际可达**的搬运器拒绝共两类，各配一枚夹具、均须在预览里出现搬运器自己的 ASCII 哨兵、同样非零退出、零写入：① 非规范别名 id（前导零）；② 冷热两侧并存但内容不逐字一致。另两类（拒最高 id / 拒未知 id）经本入口**结构上不可达**——最高 id 已被选择器先行排除，候选恒取自热账本标题——故**不要求**夹具，但须在 docs/LESSONS.md 写明其不可达的理由。另注入一次同名 .tmp 目录令暂存写失败，断言非零退出且热/冷两侧 SHA256 均不变"
  - "A14 幂等含非平凡分支：无新候选时重跑零写入；追加把已归档条目整块粘回 LEDGER 造两侧并存态，重跑须自愈补齐、归档侧标题恰 1 次、归档字节 SHA256 不变"
  - "A15 存在不可解析条目时 archive 必须非零退出（预览与实跑同口径）——不得沿用「有 [LSN-META-INVALID] 仍 exit 0」，那与同文件 12e⑥ 对 archive.ps1 的 fail-closed 契约直接矛盾，且会让 check 与 archive 对同一份账本给出相反退出码"
  - "A16 定义常驻引用的文件缺失时 fail-closed：`-RepoRoot` 指向一棵有 LEDGER 但**没有 CLAUDE.md** 的树时，archive 不得把「读不到受保护集合」当成「没有条目被引用」——须以 [LSN-RESIDENT-SOURCE-MISSING] 非零退出且零搬运，一枚夹具断言之。这是本卡唯一的 fail-open 残留面：其余路径都 fail-closed，而这一条会让整批 tier=ledger/recurrence=1/非最高 id 的条目静默进候选并被搬冷——与 A3 同一后果、不同入口"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-ARCHIVE-DRYRUN]') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch '[archived]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'lessons-archive.md'))) { exit 1 }"
dod_exit: 0
dod_assert: archive -DryRun 只选择 tier=ledger、recurrence=1、非最大 ID、且未被 CLAUDE.md/CLAUDE.template.md 引用的条目；真实执行复用 archive.ps1 -LessonIds；search 横跨热账本与冷库并标记 archived；check/gate16 的定义 ID 集合为两库并集，bump/promote 命中冷项时给出移回热区的明确修法。
review_gate: codex {verdict:pass}
hygiene: 用 hermetic RepoRoot 夹具证明候选、排除、dry-run 零写入、幂等、冷检索和 ID 并集；不把当前约 160 个候选数写死进测试
doc_sync: LESSONS 与 archive README 同步热/冷职责、检索保证和人工归档边界
---

# T0-LESSONS-COLD-RECALL

## 产出

选择性回填上游 v0.35 的 selector、cold search 和 ID union，复用本仓已经存在的 `archive.ps1 -LessonIds` 搬运器。

## 当前基线

只读审计时本仓有 213 条 lesson、LEDGER 约 273 KB；按候选规则约 160 条可进入冷库。该数字只是容量证据，不是稳定验收值，实施时必须重新计算。

## 资源冲突

本卡与 `T0-LESSONS-CAP-UNIT`(PR #127) 共写**三个**文件：`scripts/selftest.ps1`（两边都往 2x 区插闸块）、`scripts/lessons.ps1`（两边都改 `check` 主体）与 `docs/LESSONS.md`（本卡改 Tier-3 行，对方改 Tier-1 容量格）——其中 `docs/LESSONS.md` 第 12 行的「封顶 N 条」是对方闸 14g① 的判据之一，本卡若后合且不同步该行，会让 master 上的 14g① 变红。此外本卡与 `T0-R3-DIFF-BUDGET`、`T0-CI-MERGE-GATE`、后续状态码卡都写 `scripts/selftest.ps1`。它没有业务硬依赖，但这些卡不得同时向同一基线合并；执行器须串行占用该文件或在合并前重放完整验收。

## PR #51 R3 round-cap 记录

PR #51 已完成两轮 R3。第 1 轮闭合了组合归档误触 tracker/card、夹具未证明旁域不变与文档措辞漂移；第 2 轮仍发现选择器用未锚定正则读取 `tier` / `recurrence`，正文诱饵可能补齐缺失或非法元数据。

按两轮硬上限，不在 PR #51 继续第 3 轮。原 PR 只能经人裁决定是否合并；剩余解析缺口**已改由本卡承接**（见验收 A4–A10 与 selftest 闸 2e 的十五枚敌意夹具）——用户 2026-08-23 裁定走「在原卡上修 + -ResetRounds」这条路线，故 `T0-LESSONS-COLD-RECALL-R3-CLOSURE` 已被架空，待本卡合并后于 R5 退役。**本段保留是为说明那张卡为何不再启动**，不表示本卡范围外扩。
