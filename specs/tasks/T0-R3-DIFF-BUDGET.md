---
id: T0-R3-DIFF-BUDGET
title: 在 push/R3 前按真实 diff 预算硬阻断超大任务卡
depends_on: [T0-DEBT-R3-CARD-BASELINE,T0-DEBT-SELFTEST-CRITICAL-PATH]
status: todo
branch: T0-R3-DIFF-BUDGET
worktree: C:\wt\T0-R3-DIFF-BUDGET
allow_paths:
  - scripts/review.ps1
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
  - docs/DEVOPS-WORKFLOW.md
forbid:
  - 降低 R3 模型、effort、验证深度或 fail-closed 语义
  - 用 allow_paths 数量代替真实 diff 度量
  - 给被审分支或 agent 一个无需基线批准即可绕过预算的字段/开关
non_goals:
  - 硬化 diff 输入的可信度（外部 diff/textconv/attributes/二进制）——见 T0-R3-DIFF-INPUT-TRUST
  - 把「被测量的提交」贯穿到 push/评审/合并——见 T0-R3-MEASURED-OID-BINDING
  - 拆分或实现 TD2 的四张许可卡
  - 改 ReviewRoundCap、ReviewTimeoutSec 或 60000 字符 prompt 截断实现之外的 prompt 内容
  - 追溯性拒绝已经 merged 的历史大 PR
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试；
  # 清单外的缺口记 [FOLLOW-UP] 开新卡，不在本卡 block（上游提案 Asun28/claude-devops-scaffold#203）。
  - "A1 度量口径：review.ps1 对 pinned base...HEAD 求 additions+deletions（来自 --numstat）与未截断 unified diff 的字符数，两个数各有一条断言其精确值的夹具"
  - "A2 放行边界：恰好 999 changed lines 放行、恰好 60000 字符放行，各断言精确度量值（不只断言 exit 0）"
  - "A3 阻断边界：1001 changed lines 与 60001 字符各自以 [R3-DIFF-TOO-LARGE] 阻断，且诊断文本含实测值与上限"
  - "A4 先于 reviewer：超限时 reviewer 不被唤起（断言输出中无评审者启动哨兵），且 .review 轮次计数不增加"
  - "A5 numstat 不可解析即 fail-closed：畸形 --numstat 行以 [R3-DIFF-NUMSTAT-INVALID] 阻断（用 git shim 注入，不用 diff 配置）"
  - "A6 diff 命令失败即 fail-closed：git diff 非零退出以 [R3-DIFF-COMMAND-FAILED] 阻断（用 git shim 注入）"
  - "A7 CLI 只许收紧：-MaxChangedLines 1001 与 -MaxDiffChars 60001 各被参数校验拒绝"
  - "A8 -SizeOnly 只量不审：不唤起 reviewer、不消费 round、exit 0 表示在预算内；与 -ResetRounds 同时给出时以 [R3-DIFF-ARGS-INVALID] 拒绝"
  - "A9 ship 前置：task.ps1 在 push 与建 PR **之前**调用同一 -SizeOnly 路径；一枚真实 ship 夹具证明超限时零 push、零 PR、零 merge"
  - "A10 效果账本：预算阻断写一条 gate=review-size 记录（真实 ship 夹具断言其新增）"
  - "A11 saga 腿完整：'真实 diff 预算' 同时出现在 $sagaLegs 与 $sagaDone，且一枚预算失败夹具断言报告点名该腿失败、而非误报为 push+PR 或 R3 评审"
  - "A12 状态码文档：QUALITY-RUBRIC §5 状态表为本卡新增的每个码各有一行，闸 17t(doc) 的码↔行一一对应成立"
  - "A13 流程枚举同步：DEVOPS-WORKFLOW 的 ship 流程摘要与 task.ps1 的流程注释/相位标签均含预算闸，且 selftest 对这些枚举各有断言（改一处漏一处即红）"
  - "A14 allow_paths 不是规模证明：文档显式说明其只用于建卡期提示与所有权，真实 diff 预算才决定能否进 PR/R3"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/review.ps1 -SimpleMatch '[R3-DIFF-TOO-LARGE]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '-SizeOnly') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'R3-DIFF-TOO-LARGE'))) { exit 1 }"
dod_exit: 0
dod_assert: 验收集合 A1–A14 每条都有可证伪测试；`selftest.ps1 -Shard workflow` 与 `-Shard seeded` 均 exit 0。本卡只负责「按真实体量拦住超大卡」这一件事：diff 输入的可信度与被测提交的身份分别由 T0-R3-DIFF-INPUT-TRUST 与 T0-R3-MEASURED-OID-BINDING 承接，两者在本卡合并后各自独立评审。
review_gate: codex {verdict:pass}
hygiene: 预算边界与接线各保留一枚最小行为夹具；用 PR #20 的 2422 行作为诊断证据，不把远端 PR 状态写进确定性测试
doc_sync: QUALITY-RUBRIC 记录预算与本卡状态码；DEVOPS-WORKFLOW 把真实 diff 预算列为 pre-push 硬闸并说明 allow_paths 只是建卡期启发式
---

# T0-R3-DIFF-BUDGET

## 问题

`check-cards.ps1` 只在 `allow_paths > 5` 时 advisory warning。PR #20 恰好只有 5 个路径，却有 2,422 changed lines、约 182k 字符；因此任务能一路 push、开 PR，再让 R3 读取被截断的约三分之一 diff 并反复超时。

## 决策

真实体量只能在实现后度量，所以保留建卡期 warning，但在两个执行入口加同一硬闸：

1. `task.ps1 ship`：commit/scope 后、push/开 PR 前运行 `review.ps1 -SizeOnly`。
2. `review.ps1`：每次正常评审都自动执行同一预算判定，覆盖手工调用。

默认标准预算为 1,000 changed lines 且 60,000 diff chars。两者是 AND 放行、OR 阻断；字符上限与 reviewer 首屏 cap 对齐，避免默认路径 knowingly 提交截断 diff。

## 为什么不是把 warning 改 error

文件数与评审量没有稳定关系：5 个文件可以有 2,422 行，7 个小文档也可能不到 100 行。`allow_paths` 继续用于早期提示和范围所有权，真实 diff 预算才决定是否允许进入 PR/R3。

## 拆分依据（2026-08-23）

原卡在四轮 R3 里被 block 四次、每次都是真缺陷，且修复本身把 diff 推到 61,674 字符——**超出它自己引入的 60,000 上限**，于是正常评审路径以 `[R3-DIFF-TOO-LARGE]` 拒绝评审它自己。按本卡自己的教义（超限就拆，不许提高上限），沿三条互不重叠的契约缝拆成三张：

| 卡 | 单一契约 |
|---|---|
| **T0-R3-DIFF-BUDGET**（本卡） | 按真实体量拦住超大卡：度量、边界、先于 reviewer、ship 前置、saga 与文档接线 |
| **T0-R3-DIFF-INPUT-TRUST** | 那份度量的**输入**必须来自 git 自己、且不可被被审仓库的配置或属性缩小 |
| **T0-R3-MEASURED-OID-BINDING** | 被**测量**的提交必须就是被 push、被评审、被合并的那一个 |

三张卡的验收集合互不相交，故任意一张 block 都不会波及另外两张；依赖是 1 → 2、1 → 3（2 与 3 之间无依赖，可并行）。

被否决的替代方案：① 提高上限——本卡 `forbid` 明文禁止，且会让「读到截断 diff」重新成为默认路径；② 靠删注释压到线下——那是把说明性证据换成 3% 的余量，且第四轮 R3 的 finding #3 正是由此产生（为省字符回退的文档行，恰是 doc_sync 要求的那两行）。
