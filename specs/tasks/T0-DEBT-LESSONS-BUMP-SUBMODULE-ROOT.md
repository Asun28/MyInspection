---
id: T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT
title: 让 lessons bump 在 submodule 中解析自己的主检出账本
depends_on: []
status: todo
branch: T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT
worktree: C:\\wt\\T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT
plan_ref: specs/tech-debt-tracker.md#td145
allow_paths:
  - scripts/lessons.ps1
  - scripts/selftest.ps1
  - docs/LESSONS.md
  - CLAUDE.md
forbid:
  - 改动 add、promote、archive、冷存选择器或 lesson schema
  - 改动 task.ps1 中独立的收据与 saga git-common-dir 逻辑
  - 非 git 夹具失去既有本地 fallback，或任何已解析 git 仓静默回落到当前检出账本
  - 新增依赖、联网、修改冻结路径或重写整个 lessons 测试区
non_goals:
  - 支持畸形 git porcelain 输出、损坏仓库或缺失主账本时的猜测性恢复
  - 清理历史归档卡片中对旧实现的描述
  - 偿还 TD162 或改造 selftest mutation inventory
diagnosis: Resolve-BumpLedger 由 git-common-dir 的父目录猜主检出；submodule 的 common dir 位于 superproject .git/modules 下，因此安全地 fail-closed、但无法 bump 自己的账本。Git 已提供 worktree list porcelain 的主检出身份，不需要从内部 git 目录结构反推。
acceptance:
  - "A1 真实嵌套 submodule 夹具从 submodule 内运行 bump L1 后，只把 submodule 主检出 LEDGER 的 recurrence 从 3 改为 4；superproject LEDGER 与 submodule 之外文件的 SHA-256 均逐字节不变"
  - "A2 Resolve-BumpLedger 先清理 GIT_DIR、GIT_COMMON_DIR、GIT_WORK_TREE，再用 git -C Root worktree list --porcelain 的第一条 worktree 记录解析主检出；带空格的绝对路径仍正确"
  - "A3 git 非零或无输出继续回落到调用方 Fallback，保持既有非 git 2b/2c 夹具；git 成功但没有合法首条 worktree 记录时以 [LSN-PLANE-UNRESOLVED] 非零退出且零写入"
  - "A4 linked worktree、主检出直跑、继承 GIT_COMMON_DIR 劫持和主账本缺失四类既有 2d 证据继续全绿；已解析 git 仓缺账本仍禁止回落"
  - "A5 删除 worktree 主检出解析、submodule 写入归属断言或 superproject 零写入断言中的任一句，各自让 core selftest 以专属 [LSN-PLANE-SUBMODULE] 诊断变红"
  - "A6 docs/LESSONS.md、CLAUDE.md 与 selftest 2d 当前行为说明同步改为 worktree porcelain 主检出，不把历史归档卡改写成现在时"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch 'worktree list --porcelain') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-PLANE-UNRESOLVED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[LSN-PLANE-SUBMODULE]'))) { exit 1 }"
dod_exit: 0
dod_assert: 轻量 DoD 钉住生产解析入口与两个 ASCII 诊断；A1–A6 的行为真相由 core selftest 的真实 git/submodule 夹具与既有 2d 回归共同证明
review_gate: codex {verdict:pass}
hygiene: 扩展既有 selftest 闸 2d，不建平行测试文件；三枚单句删除变异分别证明解析、归属与零写入断言
doc_sync: 合并后在 master 将 TD145 置 paid 并归档、TASK-BOARD 记录 PR/commit；本卡正文归档
---

# T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT

## 目标

偿还 TD145：`bump` 不再从 Git 内部目录布局猜主检出，而是消费 Git 自己声明的 primary worktree。保持原有 linked-worktree、非 git fallback、环境变量隔离与 fail-closed 边界。

## Light Plan Forge 拆解

1. 在既有 selftest 闸 2d 加真实 superproject + submodule RED 夹具，并证明写入归属与零旁写。
2. 用 `git worktree list --porcelain` 的首条 worktree 记录替换 common-dir 父目录推导，保持现有失败分流。
3. 跑 core selftest，逐一执行解析/归属/零写入的单句删除变异并还原。
4. 同步当前行为文档，过 DoD、verify、范围/许可/防泄露、R3、PR/merge 与 R5。

## 被否决方案

- 继续识别 `.git/modules/...` 并手工回溯 superproject：依赖 Git 内部布局，仍是猜测。
- 在 submodule 中回落到 `$PSScriptRoot`：会重新打开 linked worktree 把 recurrence 写进卡分支的旧缺陷。
- 把 TD162 一并收进来：不同根因与验证面，会扩大 selftest 冲突和评审体量。
