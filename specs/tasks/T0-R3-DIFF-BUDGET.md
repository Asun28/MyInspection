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
  - 拆分或实现 TD2 的四张许可卡
  - 改 ReviewRoundCap、ReviewTimeoutSec 或 60000 字符 prompt 截断实现之外的 prompt 内容
  - 追溯性拒绝已经 merged 的历史大 PR
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/review.ps1 -SimpleMatch '[R3-DIFF-TOO-LARGE]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '-SizeOnly') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'R3-DIFF-TOO-LARGE'))) { exit 1 }"
dod_exit: 0
dod_assert: review.ps1 对 pinned base...HEAD 计算 additions+deletions 与未截断 diff 字符数；默认上限分别为 1000 与 60000，任一超限即在 reviewer 调用前以 [R3-DIFF-TOO-LARGE] fail-closed。task.ps1 在 push/建 PR 之前调用同一 SizeOnly 路径；手工 review 仍自动复核。夹具必须证明 999/60000 以内放行、1001 行或 60001 字符阻断、二进制 numstat 不被误解析、diff/基线命令失败阻断，且超限不启动 reviewer、不增加 round。
review_gate: codex {verdict:pass}
hygiene: 预算边界和接线各保留一枚最小行为夹具；用 PR #20 的 2422 行作为诊断证据，不把远端 PR 状态写进确定性测试
doc_sync: QUALITY-RUBRIC 记录预算/状态码；DEVOPS-WORKFLOW 把真实 diff 预算列为 pre-push 硬闸并说明 allow_paths 只是建卡期启发式
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
