---
id: T0-CARD-ACCEPTANCE-SETS
title: 试过「封闭验收清单」并撤回——留下 T4 卡的路线修正与 L241 判据
depends_on: []
parallelizable_with: []
status: todo
branch: T0-CARD-ACCEPTANCE-SETS
worktree: C:\wt\T0-CARD-ACCEPTANCE-SETS
allow_paths:
  - specs/tasks/T0-CARD-ACCEPTANCE-SETS.md
  - specs/tasks/T4-COMPLIANCE-ENGINE.md
  - docs/lessons/LEDGER.md
forbid:
  - 改任何实现码或测试（本卡只动卡片与总账）
  - 改 docs/QUALITY-RUBRIC.md（评审语义一律不动）
  - 替别的卡决定要不要用 acceptance 字段（本卡只撤回自己给 T3/T4 加的那两份；PR#126 那三张卡自带清单，不在本卡管辖内）
non_goals:
  - 修 T3/T4 的缺失测试（那是两张卡自己的 PR #39 / #43）
  - 退役 master 上 5 张 *-R3-CLOSURE 卡（R5 的活）
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L241$' -Quiet) -and -not (Select-String -Path 'specs/tasks/T4-COMPLIANCE-ENGINE.md' -Pattern 'R3 round-cap' -Quiet) -and -not (Select-String -Path 'specs/tasks/T3-REPORT-COMPOSER.md' -Pattern '^acceptance:' -Quiet) -and -not (Select-String -Path 'specs/tasks/T4-COMPLIANCE-ENGINE.md' -Pattern '^acceptance:' -Quiet))) { exit 1 }"
dod_exit: 0
dod_assert: 总账含 L241；T4 卡内已无 `R3 round-cap` 残节；T3/T4 两卡均**不含** acceptance 字段（实验已撤回）
review_gate: codex {verdict:pass}
hygiene: 无测试产出（纯卡片/总账卡），不适用 mutation 剪枝
doc_sync: 无（本卡不改 CLAUDE.md）
---

# T0-CARD-ACCEPTANCE-SETS

## 这张卡最终交付什么

两件小事，都是实验的**残值**，不是实验本身：

1. **删掉 `T4-COMPLIANCE-ENGINE` 卡尾的 `## R3 round-cap 后续` 节**——它按路线 1 声称四类缺口由 `T4-COMPLIANCE-ENGINE-R3-CLOSURE` 收口，而用户已裁定路线 2（在原卡内修）。`review.ps1` 读的正是这份卡，留着就是在告诉评审者「这些不在范围内」。
2. **总账 L241**：记录「轮次通胀 ≠ 颗粒度」的可复算判据，**以及本卡自己的反证**。

## 实验、以及它为什么被撤回

原计划：给 `T3-REPORT-COMPOSER`(#39) 与 `T4-COMPLIANCE-ENGINE`(#43) 各写一份编号封闭的 `acceptance:` 清单（15/16 条，覆盖各自历史 R3 finding 与卡片原有 `dod_assert`），假设是「把验收集合封闭 ⇒ rubric #6 的搜索面有界 ⇒ 评审收敛」。

**实测结果（本卡自己的 PR#124，两轮 R3）**：

| 轮次 | finding 数 | 其中 #6 | #6 打在哪 |
|---|---:|---:|---|
| 1 | 2 | **0** | —（两条是范围越界 + 可追溯） |
| 2 | 4 | **2** | **清单本身** |

第 2 轮那两条 #6 全部属实：名字黑名单（`disable/skip/force`）证明不了不存在别名旁路；两条清单项在「默认值 vs 非默认值」上互相矛盾；不可变项没要求「被拒绝的修改之后原集合仍未变」。

初步读法是「**无界搜索被上移了一层**——从『有没有漏测』变成『清单有没有漏项』」。

**但这个读法有一个同期反例，必须并列写下**：同一时间 PR#126（`24c0a33`，另一会话）把 `T0-R3-DIFF-BUDGET` 拆成三张卡，**三张都带 `acceptance:` 清单**，而它们的条目精确到夹具级：

> A2 放行边界：恰好 999 changed lines 放行、恰好 60000 字符放行，各断言精确度量值（不只断言 exit 0）

对比本卡写的：

> A14 不存在任何关闭/绕过开关：断言公开 API 面无 disable/skip/force 形态参数

**所以本卡的第 2 轮更可能证明了「粗清单招 #6」，而不是「清单机制无效」。** 判别要看 #126 那三张卡实际跑出多少轮，本卡无从得知。

据此人裁：**撤回本卡给 T3/T4 加的那两份清单**（它们确实太粗，重写到夹具级是另一件事、不在本卡范围），**但不对 acceptance 机制本身下结论、也不替别的卡决定**。只留上面两件残值。

## 「轮次 ≠ 体量」这一半仍然成立（完整数据，可复算）

| 卡 | PR | 新增行 | R3 轮次 |
|---|---|---:|---:|
| T2-ROUTINE-CONTENT | #5 | 277 | 9 |
| T1-CANON-HASH | #2 | 556 | 2 |
| T1-TEMPLATE-ENGINE | #3 | 1008 | 5 |
| T2-CAPTURE-CORE | #8 | 1862 | 6 |
| T3-FINALIZE | #7 | 2027 | 15 |
| T2-PHOTO-PIPELINE | #6 | 2168 | 11 |
| T1-SCHEMA-CORE | 本地合并 fcdc88d | 2632 | 17 |
| T5-BACKUP-FORMAT | #9 | 2740 | **4** |

Pearson **r = 0.4365，r^2 = 0.1906**。体量只解释约两成的轮次方差；最大的 diff 是**第二少**轮次。

**来源与勘误**：新增行 = `gh pr view <n> --json additions`；`T1-SCHEMA-CORE` 无 PR，取 `git show --stat fcdc88d` 的 insertions（来源与其余七行不同）。轮次以 **`docs/TASK-BOARD.md` 的逐轮记述为准**——本卡初稿照 `CLAUDE.md` 概述取值，把 `T2-CAPTURE-CORE` 记成 7（实为 6）、把 `T2-PHOTO-PIPELINE` 记成触顶那轮 9（实为人裁后又跑到 11），R3 第 2 轮据仓库记录当场指出。**两处已订正**，结论不变（r^2 从误算的 0.18 变为 0.19）。

## 不做什么

- **不动评审语义**：「清单外一律 FOLLOW-UP」「#8 优先于 #6」这类改判，`docs/QUALITY-RUBRIC.md` 一字未动。提案留在上游 Asun28/claude-devops-scaffold **#203 / #204 / #205**，本卡两轮的实测（含上面那个反例）作为反馈补进 #203。
- **不替 `acceptance:` 字段本身定生死**：本卡只撤回自己加的那两份粗清单。PR#126 的三张卡自带精确清单、已在 master 上，本卡不碰、不评价、不要求它们跟进。
- **不改 T3-REPORT-COMPOSER**：它已回到基线，本卡 `allow_paths` 里也不再列它。

## 验收 / 执行建议
dod 见 front-matter。纯卡片改动，无实现码。
