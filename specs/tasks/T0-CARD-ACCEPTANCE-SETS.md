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
  - "A5 总账新增 L241（judgment 类）：记录 r^2=0.18 的实测与 #6/#8 不对称，并显式限定 L206「拆卡」的适用面"
  - "A6 L241 的 id 避开主检出未提交总账已占用的 238/239/240，不制造重号"
  - "A7 check-cards 与 lessons.ps1 check 均 PASS"
review_gate: codex {verdict:pass}
hygiene: 无测试产出（纯卡片/总账卡），不适用 mutation 剪枝
doc_sync: 无（本卡不改 CLAUDE.md；T3/T4 合并时各自的 doc_sync 照旧）
---

# T0-CARD-ACCEPTANCE-SETS

## 产出
给 `T3-REPORT-COMPOSER` 与 `T4-COMPLIANCE-ENGINE` 两张 round-cap 卡各补一份**编号封闭的 `acceptance:` 清单**，并把「R3 轮次通胀不是颗粒度问题」的实测判据入总账（L241）。

## 为什么（实测判据）
两张卡各自两轮 R3 触顶。通行归因是「卡太大、拆细就好」。实测不支持：

| 卡 | PR 新增行 | R3 轮次 |
|---|---:|---:|
| T2-ROUTINE-CONTENT | 277 | 9 |
| T1-CANON-HASH | 556 | 2 |
| T3-FINALIZE | 2027 | 15 |
| T1-SCHEMA-CORE | 2632 | 17 |
| T5-BACKUP-FORMAT | 2740 | **4** |

八张已合并卡 Pearson r=0.42、**r^2=0.18**——卡片体量只解释不到两成的轮次方差，最大的 diff 反而第四少轮次。

真正的判别式是**验收集合封不封闭**。24 份终局裁决共 25 条 finding，其中 **18 条（72%）** 是 rubric #6「测试缺失」；#6 的搜索面在卡片只声明行为、不枚举验收事实时**无界**。对照：`T1-CANON-HASH` 把哈希域逐字段列全 → 2 轮收；`T4-COMPLIANCE-ENGINE` 只写「加载器做校验」→ 评审逐条列出 12 个卡片从未提过的拒绝用例，全部属实。

本卡把那些集合**一次性封闭**，让下一轮 R3 从「无界搜索」变成「照单核对」。

## 与上游的关系
`acceptance:` 是本仓先行的**下游试验**，同时已作为提案提给上游：Asun28/claude-devops-scaffold **#203**（封闭验收集合 → #6 有不动点）、**#204**（rubric 瘦身 + #8 优先于 #6 的处置动词）、**#205**（评审按内容分流）。上游未落地前**不改本地 `QUALITY-RUBRIC.md`**，避免与上游分叉；清单靠人/评审读，不靠机检。

## 验收 / 执行建议
dod 见 front-matter。纯卡片改动，无实现码。
