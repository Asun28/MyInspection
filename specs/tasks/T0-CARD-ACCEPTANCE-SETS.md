---
id: T0-CARD-ACCEPTANCE-SETS
title: 给两张 round-cap 卡补封闭 acceptance 清单，并记录「轮次通胀 ≠ 颗粒度」的判据
depends_on: []
parallelizable_with: []
status: todo
branch: T0-CARD-ACCEPTANCE-SETS
worktree: C:\wt\T0-CARD-ACCEPTANCE-SETS
allow_paths:
  - specs/tasks/T0-CARD-ACCEPTANCE-SETS.md
  - specs/tasks/T3-REPORT-COMPOSER.md
  - specs/tasks/T4-COMPLIANCE-ENGINE.md
  - docs/lessons/LEDGER.md
forbid:
  - 改任何实现码或测试（本卡只动卡片与总账）
  - 动 T3/T4 两张卡的 allow_paths / forbid / non_goals / dod_command（验收集合是新增字段，不改既有契约面）
non_goals:
  - 按新清单实现 T3/T4 的缺失测试（那是两张卡自己的 PR #39 / #43）
  - 改 QUALITY-RUBRIC.md 的 #6 / #8 判定（上游 Asun28/claude-devops-scaffold#203 #204 未落地前不本地分叉）
  - 退役 master 上 5 张 *-R3-CLOSURE 卡（R5 的活）
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T3-REPORT-COMPOSER.md' -Pattern '^acceptance:' -Quiet) -and (Select-String -Path 'specs/tasks/T4-COMPLIANCE-ENGINE.md' -Pattern '^acceptance:' -Quiet) -and (Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L241$' -Quiet) -and -not (Select-String -Path 'specs/tasks/T4-COMPLIANCE-ENGINE.md' -Pattern 'R3 round-cap' -Quiet))) { exit 1 }"
dod_exit: 0
dod_assert: 两张卡各含 `acceptance:` 块；T4 卡内已无 `R3 round-cap` 残留节；总账含 L241
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 T3-REPORT-COMPOSER 卡含 acceptance 块，条目覆盖其 R3 六条 finding（几何/图槽不可分/短哈希/孤行/投影校验/封面统计与时间渲染）**且**覆盖卡片原有 dod_assert 的每一项"
  - "A2 T4-COMPLIANCE-ENGINE 卡含 acceptance 块，条目覆盖其 R3 四条 finding（非默认配置值/改期身份/拒绝集/不可变视图）**且**覆盖需求 §10 四条法定规则与 DST 语义"
  - "A3 T4 卡内不再有 `## R3 round-cap 后续` 节（该节按路线 1 声称四类缺口归 T4-COMPLIANCE-ENGINE-R3-CLOSURE 收口，与用户已定的路线 2 及新清单自相矛盾）"
  - "A4 两张卡的 allow_paths / forbid / non_goals / dod_command 逐字节未变（只新增字段，不改既有契约面）"
  - "A5 总账新增 L241（judgment 类）：先声明**入选规则**（已合并产品卡 且 TASK-BOARD/CLAUDE.md 留有轮次记录；T0 卡与 *-R3-CLOSURE 承接卡排除），按该规则取满 **14 行**，记录 **r=0.6316 / r^2=0.3989**（逐行可复算），并**点名**三张合规则但无轮次记录、故无法取数的卡。结论按此数据下调：规模解释约 40% 的轮次方差＝**中等相关**，故拆卡是次要杠杆而非无关；另记两个不依赖入选口径的极端（277 行/9 轮 vs 2740 行/4 轮）证明规模不**决定**轮次。**订正轨迹**：初稿 r^2=0.18（照 CLAUDE.md 概述误算）→ 第 2 轮订正为 0.1906（八行）→ 第 3 轮评审指出八行无入选规则且漏卡，补齐至 14 行后为 0.3989——每次都是让契约对齐数据，不是把靶子挪到已达成的位置"
  - "A6 L241 的 id 避开主检出未提交总账已占用的 238/239/240，不制造重号"
  - "A7 check-cards 与 lessons.ps1 check 均 PASS"
review_gate: codex {verdict:pass}
hygiene: 无测试产出（纯卡片/总账卡），不适用 mutation 剪枝
doc_sync: 无（本卡不改 CLAUDE.md；T3/T4 合并时各自的 doc_sync 照旧）
---

# T0-CARD-ACCEPTANCE-SETS

## 产出

给 `T3-REPORT-COMPOSER` 与 `T4-COMPLIANCE-ENGINE` 两张 round-cap 卡各补一份**编号封闭的 `acceptance:` 清单**，
并把「R3 轮次通胀不是颗粒度问题」的实测判据入总账（L241）。

## 为什么（实测判据，可复算）

两张卡各自两轮 R3 触顶。通行归因是「卡太大、拆细就好」。实测不支持：

| 卡 | PR | 新增行 | R3 轮次 |
|---|---|---:|---:|
| T2-PHOTO-PROPERTY-DEDUPE | #116 | 163 | 1 |
| T1-SKELETON-E2E | 本地合并 19fd908 | 258 | 2 |
| T2-ROUTINE-CONTENT | #5 | 277 | 9 |
| T1-CANON-HASH | #2 | 556 | 2 |
| T2-PHOTO-QUALITY-PROFILES | #30 | 751 | 2 |
| T5-RETENTION | #11 | 791 | 5 |
| T2-PHRASELIB | #10 | 867 | 4 |
| T1-TEMPLATE-ENGINE | #3 | 1008 | 5 |
| T2-PHOTO-STREAMING-ENCODE | #29 | 1151 | 2 |
| T2-CAPTURE-CORE | #8 | 1862 | 6 |
| T3-FINALIZE | #7 | 2027 | 15 |
| T2-PHOTO-PIPELINE | #6 | 2168 | 11 |
| T1-SCHEMA-CORE | 本地合并 fcdc88d | 2632 | 17 |
| T5-BACKUP-FORMAT | #9 | 2740 | **4** |

**入选规则（先定口径，再取数——这是 R3 第 3 轮 finding #1 的直接修正）**：入选 = 「已合并的**产品卡**
（`android/**` 或 `data/templates/**` 的领域实现，即 T1–T5 前缀），**且**在 `docs/TASK-BOARD.md` 或
`CLAUDE.md` 至少一处留有 R3 轮次记录」。T0 harness/脚手架卡整体排除（被审面是脚本与文档，与产品卡
不同质）；`*-R3-CLOSURE` 承接卡排除（它们本身是另一张卡轮次的产物，计入会重复计数）。
**按此规则被排除、且须点名的三张**（合规则的产品卡，但两处权威面都没有轮次记录，故无法取数——
列出而非静默丢弃）：`T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER`(#32, 3282 行)、
`T2-PHOTO-DIRECTORY-DURABILITY`(#35, 334 行)、`T2-FIELD-LEDGER-THEME`(#55，只记「R3 全绿」无轮次)。
其中 #32 是全部产品卡里最大的一张，它缺记录这件事本身即本表的最大不确定性来源，须明写。

Pearson **r = 0.6316，r^2 = 0.3989**（14 行；旧的八行值 r=0.4365 / r^2=0.1906 已作废，见下「复算结果与结论修正」）——体量解释约 **四成**的轮次方差＝**中等相关**，但**全表最大**的 diff（`T5-BACKUP-FORMAT` 2740 行）只跑 4 轮，低于 14 行的轮次中位数 4.5，故规模不**决定**轮次。（旧稿称这 4 轮「第二少」是八行时代的排名，14 行里的轮次层级是 1 / 2 / 4 / 5 / 6 / 9 / 11 / 15 / 17，4 是第三档、并列第 6–7 位，已订正。）

**来源与勘误（逐行可核 · 14 行全覆盖）**：新增行 = `gh pr view <n> --json additions`，**12 行**如此取得；
**两行无 PR、走本地合并**，取 `git show --stat` 的 insertions——`T1-SKELETON-E2E`(19fd908, 258) 与
`T1-SCHEMA-CORE`(fcdc88d, 2632)。（旧稿只点了 `T1-SCHEMA-CORE` 一处，漏了 `T1-SKELETON-E2E`，已订正。）
轮次的来源**分两类，逐行点名**：**10 行取自 `docs/TASK-BOARD.md` 的逐轮记述**——
`T2-PHOTO-PROPERTY-DEDUPE`(1) / `T2-ROUTINE-CONTENT`(9) / `T2-PHOTO-QUALITY-PROFILES`(2) /
`T5-RETENTION`(5) / `T2-PHRASELIB`(4) / `T2-PHOTO-STREAMING-ENCODE`(2) / `T2-CAPTURE-CORE`(6) /
`T3-FINALIZE`(15) / `T2-PHOTO-PIPELINE`(11) / `T5-BACKUP-FORMAT`(4)；**4 行在 TASK-BOARD 里没有轮次记录**，
只能取自 `CLAUDE.md` 的「当前阶段」节——`T1-SKELETON-E2E`(2) / `T1-CANON-HASH`(2) /
`T1-TEMPLATE-ENGINE`(5) / `T1-SCHEMA-CORE`(17)，那是它们唯一的记录。
（旧稿写「五行 TASK-BOARD + 三行 CLAUDE.md」，那是八行时代的账，14 行里有 6 行当时无来源声明，已补齐。）
故优先级规则的准确表述是「**两者都有时以 TASK-BOARD 为准**」，而非
「一律以 TASK-BOARD 为准」。初稿有两处取值错误，**它们的来源不同、须分开说**（在一段专讲逐行溯源的
文字里含混带过，正是第 1 轮被打回的那一类问题）：`T2-CAPTURE-CORE` 的 7 确实照 `CLAUDE.md` 概述取值
（该节原文「76 测试，7 轮」），而 TASK-BOARD 记的是 6；`T2-PHOTO-PIPELINE` 的 9 **不来自 `CLAUDE.md`**
（其概述写的是「5 轮 block」），而来自 TASK-BOARD 的「R3 第 9 轮触 `ReviewRoundCap`」——即误取了触顶那一轮，
漏掉人裁后又跑的 round 10 / 11，实为 11。R3 第 1 轮据仓库记录当场指出这两处，**均已订正**，当时结论不变
（r^2 从误算的 0.18 变为 0.1906）。**该值此后再次作废**：R3 第 3 轮指出八行样本没有入选规则且漏掉合规则的卡，补齐至 14 行后为 **r=0.6316 / r^2=0.3989**，结论亦随之由「基本不解释」下调为「中等相关」。**A5 两次修订都是让契约追上数据**：第 2 轮 0.18→0.1906（修误算），第 3 轮 0.1906→0.3989（补样本）。全卡与 L241 现统一记 **0.3989**，前两个值只作为订正史保留、不得再被引用为结论。

**复算结果与结论修正（R3 第 3 轮 finding #1）**：按上面的入选规则取满 14 行后，
r=0.6316 / **r^2=0.3989**——不是初稿八行的 0.1906。评审给出的中间值（补进
`T1-SKELETON-E2E`/`T2-PHRASELIB`/`T5-RETENTION` 三行后 r^2≈0.3326）经独立复算完全一致。
**这个差别是实质性的，本卡据此下调原结论**：初稿说「规模基本不解释轮次」（0.19 ≈ 五分之一），
14 行的实测是规模解释了约 **40%** 的轮次方差——那不是「基本不解释」，是**中等相关**。
故 L241 的措辞由「别把拆卡当轮次通胀的解药」改为「拆卡是有用的次要杠杆，但不是主因」，
并写明剩余约 60% 由别的东西驱动。
**不依赖入选口径的判据是这一对反向观测**（无论怎么定 cohort 都在表内）：
`T5-BACKUP-FORMAT` 是全表**最大**的一张（2740 行）却只跑 4 轮（低于中位数 4.5）；
`T2-ROUTINE-CONTENT` 只有 277 行——按体量是**第 3 小**——却跑了 9 轮，是**第 4 高**。
规模差 9.9 倍而轮次反向。（旧稿把 277 行称作「极端/最小」，14 行里比它更小的还有 163 与 258，已订正。）它证明的是「规模不**决定**轮次」，这比相关系数弱但更稳，
且与 r^2=0.40 并不矛盾：两者一起读作「规模是真实因素之一，但单卡的轮次主要由别的东西决定」。
初稿把「不决定」写成了「基本无关」，是过度解读，本次一并订正。

真正的判别式**可能**是验收集合封不封闭。24 份终局裁决共 25 条 finding，其中 **18 条（72%）** 是 rubric #6
「测试缺失」；#6 的搜索面在卡片只声明行为、不枚举验收事实时**无界**。对照：`T1-CANON-HASH` 把哈希域
逐字段列全 → 2 轮收；`T4-COMPLIANCE-ENGINE` 只写「加载器做校验」→ 评审逐条列出 12 个卡片从未提过的
拒绝用例，全部属实。

**这组计数的证据强度低于上表，须显式降级**：24/25/18 取自各 worktree 的 `.review/<branch>.json`，这些产物
**按设计不入库**、每轮覆写、且是**终局快照而非普查**，读者无法从本仓历史复现——**当指向性线索读，不是可审计
数据集**。故本卡的两级结论要分开：**「体量不**决定**轮次」有完整可复算数据**（上表 14 行 + 入选规则 + 逐行来源，任何人可重算 r 与 r^2；注意结论是「不决定」而非「不解释」——r^2=0.3989 说明它解释了约四成），据此主张「先把清单写出来」；**「#6 无界是主因」只有上述指向性证据**，本卡**不据它改变任何评审规则**
（`docs/QUALITY-RUBRIC.md` 一字未动）。同一降级写法也在 L241 里，两处口径一致。

## 精度是这份清单的全部要害

本卡第一稿写的是**名字黑名单式**的粗清单，R3 第 2 轮两条 #6 全部打在清单自己身上，且全部属实：
名字黑名单（`disable/skip/force`）证明不了不存在别名旁路；两条清单项在「默认值 vs 非默认值」上互相矛盾；
不可变项没要求「被拒绝的修改之后原集合仍未变」。

同期 PR#126（`24c0a33`）把 `T0-R3-DIFF-BUDGET` 拆成三张卡，**三张都带 `acceptance:` 清单**且条目精确到
夹具级。对比同一件事的两种写法：

| | 写法 | 能否被证伪 |
|---|---|---|
| 粗 | `A14 不存在任何关闭/绕过开关：断言公开 API 面无 disable/skip/force 形态参数` | 否——改个参数名即绕过 |
| 精 | `A2 放行边界：恰好 999 changed lines 放行、恰好 60000 字符放行，各断言精确度量值（不只断言 exit 0）` | 是 |

**结论：第 2 轮证明的是「粗清单招 #6」，不是「清单机制无效」。** 故本卡按 #126 的精度重写这两份清单，
而不是撤回它们。**本次交付的精度口径**（写下来是为了让读者能核，而不是让它成为下一轮无界搜索的题库）：
每条点名精确边界值、精确 ASCII 哨兵 / 异常类型 / 枚举值，需要非默认配置或反射的条目**另点名注入机制**；
**「能杀死该守卫的单句变异」以每卡一条总括项交付**（T3 A19 / T4 A21，要求 A1–A18 / A1–A20 逐条配变异并把
对照表贴进 PR body），只有在变异靶点不显然时才在该条内单独写出（如 T3 A11、T3 A17、T4 A2、T4 A19）——
逐条重复同一句变异要求会把清单本身撑成新的核对题库，而总括项的判据（L165 分类器）是同一条、且可一次核完。

两份清单的条目来源是三方交叉的：**(a)** 各卡历史 R3 裁决（`.review/<branch>.json`——同上，非入库、每轮覆写，
只作线索，不是本卡任何主张的可审计依据）、**(b)** 各自 `*-R3-CLOSURE` 卡已写下的「单一产出 / 验收证据」节
（**入库、可审计**，是清单里 T3 A13(c)、T4 A13/A17 等条的直接出处）、**(c)** 2026-08-23 对两个 PR 全量 diff 的
独立复核（复核当日看到的形态记在 T3 卡正文「为什么这些条目存在」一节，带日期，与条目本身分开）。

## 与上游的关系

`acceptance:` 是本仓先行的**下游试验**，同时已作为提案提给上游：Asun28/claude-devops-scaffold **#203**
（封闭验收集合 → #6 有不动点）、**#204**（rubric 瘦身 + #8 优先于 #6 的处置动词）、**#205**（评审按内容分流）。
上游未落地前**不改本地 `QUALITY-RUBRIC.md`**，避免与上游分叉；清单靠人/评审读，不靠机检。

清单**怎么抵达 R3**，机制要说准：`review.ps1:338` 用 `git show "${baseOid}:$cardRelPath"` 从**钉住的 base 提交**
读整张卡（`:354` 的 worktree `Get-Content` 只是 base 上无卡时的回退，正常路径走不到），`:444` 再把卡片原文
整段注入评审者提示词。所以这两份清单**要等本卡合并进 master 之后**才抵达 PR #39 / #43 的评审者——
在飞分支上改卡对本轮评审不可见，这也正是本卡必须独立于那两张卡先合的原因。

（字段本身的登记与形态机检另立 `T0-CARD-ACCEPTANCE-FIELD`：`specs/README.md` 的 front-matter 字段表
与 `_TEMPLATE.md` 里此前都没有这个字段。本卡不做那件事。）

## 不做什么

- **不动评审语义**：「清单外一律 FOLLOW-UP」「#8 优先于 #6」这类改判，`docs/QUALITY-RUBRIC.md` 一字未动。
- **不实现 T3/T4 的缺失测试**：那是 PR #39 / #43 自己的活，本卡只写清单。
- **不动两张卡的 `allow_paths` / `forbid` / `non_goals` / `dod_command`**：验收集合是新增字段，既有契约面逐字节不变。

## 验收 / 执行建议
dod 见 front-matter。纯卡片改动，无实现码。
