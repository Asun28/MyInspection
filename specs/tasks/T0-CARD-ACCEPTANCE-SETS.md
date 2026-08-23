---
id: T0-CARD-ACCEPTANCE-SETS
title: 给两张 round-cap 卡补编号 acceptance 清单（声明性，不改评审语义），并记录「轮次通胀 ≠ 颗粒度」的判据
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
  - "A1 T3-REPORT-COMPOSER 卡含 acceptance 块，条目覆盖其 R3 六条 finding（几何/图槽不可分/短哈希/孤行/投影校验/封面统计与时间渲染）**且**覆盖卡片原有 dod_assert 的每一项"
  - "A2 T4-COMPLIANCE-ENGINE 卡含 acceptance 块，条目覆盖其 R3 四条 finding（非默认配置值/改期身份/拒绝集/不可变视图）**且**覆盖需求 §10 四条法定规则与 DST 语义"
  - "A3 T4 卡内不再有 `## R3 round-cap 后续` 节（该节按路线 1 声称四类缺口归 T4-COMPLIANCE-ENGINE-R3-CLOSURE 收口，与用户已定的路线 2 及新清单自相矛盾）"
  - "A4 两张卡的 allow_paths / forbid / non_goals / dod_command 逐字节未变（只新增字段，不改既有契约面）"
  - "A5 总账新增 L241（judgment 类）：记录 r^2=0.18 的实测与 #6/#8 不对称，并显式限定 L206「拆卡」的适用面"
  - "A6 L241 的 id 避开主检出未提交总账已占用的 238/239/240，不制造重号"
  - "A7 check-cards 与 lessons.ps1 check 均 PASS"
review_gate: codex {verdict:pass}
hygiene: 无测试产出（纯卡片/总账卡），不适用 mutation 剪枝
doc_sync: 无（本卡不改 CLAUDE.md；T3/T4 合并时各自的 doc_sync 照旧）
---

# T0-CARD-ACCEPTANCE-SETS

## 产出
给 `T3-REPORT-COMPOSER` 与 `T4-COMPLIANCE-ENGINE` 两张 round-cap 卡各补一份**编号的 `acceptance:` 清单**，并把「R3 轮次通胀不是颗粒度问题」的实测判据入总账（L241）。

**清单是声明，不是判据**：它写下作者认为「完成」需要哪些事实，供实现者与评审者对照；**裁决仍完全按 `docs/QUALITY-RUBRIC.md` 现行 rubric**，本卡不改任何评审语义（见 `non_goals`）。

## 为什么（实测判据）
两张卡各自两轮 R3 触顶。通行归因是「卡太大、拆细就好」。实测不支持：

**完整数据集（八张全列，无取舍）**：

| 卡 | PR | 新增行 | R3 轮次 |
|---|---|---:|---:|
| T2-ROUTINE-CONTENT | #5 | 277 | 9 |
| T1-CANON-HASH | #2 | 556 | 2 |
| T1-TEMPLATE-ENGINE | #3 | 1008 | 5 |
| T2-CAPTURE-CORE | #8 | 1862 | 7 |
| T3-FINALIZE | #7 | 2027 | 15 |
| T2-PHOTO-PIPELINE | #6 | 2168 | 9 |
| T1-SCHEMA-CORE | 本地合并 fcdc88d | 2632 | 17 |
| T5-BACKUP-FORMAT | #9 | 2740 | **4** |

对这八对 (x=新增行, y=轮次) 算 Pearson：**r = 0.42，r^2 = 0.18**。卡片体量只解释不到两成的轮次方差，且最大的 diff 反而是第四少轮次。

**数据来源与其限度（可复算）**：
- 新增行 = `gh pr view <n> --json additions`；`T1-SCHEMA-CORE` 无 PR（早于公开仓，走本地合并），取 `git show --stat fcdc88d` 的 insertions，**与其余七行来源不同**，单列注明。
- 轮次 = 各卡自己的合并记录 / `docs/TASK-BOARD.md` / `CLAUDE.md`「当前阶段」里写下的轮次，属**叙述性记录**而非机器产物。
- finding 分布 = 各 worktree `.review/<branch>.json` 共 24 份**终局**裁决（13 block / 11 pass）、25 条 finding，其中 18 条（72%）点名 rubric #6，`#1 #2 #3 #5 #8 #11 #13 #14 #15 #16` 十维零命中。**这些 .review 产物按设计不入库**（每轮覆写、随 worktree 生灭），故此处只能给出计数与复算方法，读者无法从本仓历史复现——把它当**指向性证据**，不是可审计数据集。同理，24 份是终局快照，不是历次轮次的普查。

结论按证据强度分两级：**「体量不解释轮次」有完整可复算数据支撑**；**「#6 无界是主因」只有上述指向性证据**，本卡不据后者改变任何评审规则，只据前者主张「写清单」这一条不依赖 rubric 的做法。

对照仍是最直观的：`T1-CANON-HASH` 把哈希域逐字段列全 → 2 轮收；`T4-COMPLIANCE-ENGINE` 只写「加载器做校验」→ 评审逐条列出 12 个卡片从未提过的拒绝用例，全部属实。

本卡把那些事实**一次性写出来**。价值不依赖任何 rubric 改动：作者一次想清「完成=哪些事实」，比在 N 轮评审里被动发现便宜，实现者与评审者也多了一张可对照的清单。至于「清单外一律不 block」——那是上游提案，**本卡不采用**。

## 与上游的关系
写清单这件事本卡就做了。**把清单变成排他性判据**则只是提案，已提给上游：Asun28/claude-devops-scaffold **#203**（封闭验收集合 → #6 有不动点）、**#204**（rubric 瘦身 + #8 优先于 #6 的处置动词）、**#205**（评审按内容分流）。**上游落地前本仓一律不采用这些语义**，也不改本地 `QUALITY-RUBRIC.md`；清单靠人与评审读，不当机检、不当裁决依据。

## 验收 / 执行建议
dod 见 front-matter。纯卡片改动，无实现码。
