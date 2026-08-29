---
id: T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT
title: 让 lessons bump 在 submodule 中解析自己的主检出账本
depends_on: []
status: merged
branch: T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT
worktree: C:\\wt\\T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT
plan_ref: specs/tech-debt-tracker.md#td145
allow_paths:
  - scripts/lessons.ps1
  - scripts/selftest.ps1
  - docs/LESSONS.md
  - CLAUDE.md
  - specs/tasks/T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT.md
forbid:
  - 改动 add、promote、archive、冷存选择器或 lesson schema
  - 改动 task.ps1 中独立的收据与 saga git-common-dir 逻辑
  - 非 git 夹具失去既有本地 fallback，或任何已解析 git 仓静默回落到当前检出账本
  - 新增依赖、联网、修改冻结路径或重写整个 lessons 测试区
non_goals:
  - 支持畸形 git porcelain 输出、损坏仓库或缺失主账本时的猜测性恢复
  - 清理历史归档卡片中对旧实现的描述
  - 偿还 TD162 或改造 selftest mutation inventory
diagnosis: Resolve-BumpLedger 由 git-common-dir 的父目录猜主检出；submodule 的 common dir 位于 superproject .git/modules 下，因此安全地 fail-closed、但无法 bump 自己的账本。真实夹具进一步证明 submodule 的 worktree list 首条仍是 common dir；实际 checkout 由 Git 的 core.worktree 声明（相对 common dir），不能只取首条记录或 show-toplevel。
acceptance:
  - "A1 真实嵌套 submodule 夹具从 submodule 主检出运行 bump L1 后只把自身 recurrence 从 3 改为 4；再从该 submodule 的 linked worktree 运行只把主检出从 4 改为 5，linked ledger、superproject LEDGER 与 seed 源账本的 SHA-256 均逐字节不变"
  - "A2 Resolve-BumpLedger 在同一段 GIT_DIR、GIT_COMMON_DIR、GIT_WORK_TREE 清理窗口内先把 git-common-dir 按各自 -C 根规范成绝对路径，再读 worktree list 与 repository-local core.worktree：首条 worktree 是同仓真实 checkout 时使用它；首条等于 common dir 的 submodule 形态要求非空 core.worktree 并按其相对 common dir 的语义解析；最终候选须以 show-toplevel 与同一 normalized common-dir 反向验证，带空格路径仍正确"
  - "A3 只有初始 git-common-dir 仓库探针命令不存在或非零才回落调用方 Fallback，保持既有非 git 2b/2c 夹具；仓库已识别后，worktree/config/验证命令失败、成功但空/畸形/歧义输出、跨仓或 common-dir 内候选均以 [LSN-PLANE-UNRESOLVED] 非零退出且零写入"
  - "A4 普通 linked worktree、主检出直跑、submodule 主检出与 submodule linked worktree、全探针窗口的 GIT_COMMON_DIR/GIT_CONFIG_* 劫持和主账本缺失证据全绿；已解析 git 仓缺账本仍禁止回落"
  - "A5 三组变异证据真实区分生产与测试 oracle：删除 core.worktree 主检出赋值须让 core 以 2d(s/target) 专属 [LSN-PLANE-SUBMODULE] 变红；归属与零写入各先注入一枚仅对应断言可见的坏写并观察专属红，再单句删除该 Fail 断言并观察 mutant survivor，证明断言不可缺，最后逐组哈希还原"
  - "A6 docs/LESSONS.md 与 selftest 2d/2ds 的当前行为说明同步为 worktree list + core.worktree 主检出；CLAUDE.md 的 T0-LESSONS-BUMP-PLANE 已合并历史段及历史归档卡保持原时点事实，不改写成现在时"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch 'worktree list --porcelain') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch 'core.worktree') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-PLANE-UNRESOLVED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[LSN-PLANE-SUBMODULE]'))) { exit 1 }"
dod_exit: 0
dod_assert: 轻量 DoD 钉住生产解析入口与两个 ASCII 诊断；A1–A6 的行为真相由 core selftest 的真实 git/submodule 夹具与既有 2d 回归共同证明
review_gate: codex {verdict:pass}
hygiene: 扩展既有 selftest 闸 2d，不建平行测试文件；一枚生产单句删除变异加两组「坏写 RED → 单句删除断言后 survivor」证明解析、归属与零写入 oracle，逐组以脚本 SHA-256 前后一致证明还原
doc_sync: 合并后在 master 将 TD145 置 paid 并归档、TASK-BOARD 记录 PR/commit；本卡正文归档
---

# T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT

## 目标

偿还 TD145：`bump` 不再从 Git 内部目录布局猜主检出，而是消费 Git 的 worktree 列表与 `core.worktree` 声明。保持原有 linked-worktree、非 git fallback、环境变量隔离与 fail-closed 边界。

## Light Plan Forge 拆解

1. 在既有 selftest 闸 2d 加真实 superproject + submodule RED 夹具，并证明写入归属与零旁写。
2. 普通仓使用 `git worktree list --porcelain` 首条记录；Git 声明 `core.worktree` 时按其相对 common dir 的定义解析 submodule 主检出，保持现有失败分流。
3. 跑 core selftest；执行一枚解析单句删除变异，以及归属/零写入两组「坏写 RED → 单句删除断言后 survivor」，逐组哈希还原。
4. 同步当前行为文档，过 DoD、verify、范围/许可/防泄露、R3、PR/merge 与 R5。

## 被否决方案

- 继续识别 `.git/modules/...` 并手工回溯 superproject：依赖 Git 内部布局，仍是猜测。
- 在 submodule 中回落到 `$PSScriptRoot`：会重新打开 linked worktree 把 recurrence 写进卡分支的旧缺陷。
- 把 TD162 一并收进来：不同根因与验证面，会扩大 selftest 冲突和评审体量。
