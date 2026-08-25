---
id: T0-RECONCILE-ROADMAP-INDEX
title: 将离线安全与诊断卡投影到任务表和技术债索引
depends_on: [T0-RECONCILE-T1-SECURITY-CARDS, T0-RECONCILE-T5-DIAGNOSTIC-CARDS]
status: todo
branch: T0-RECONCILE-ROADMAP-INDEX
worktree: C:\wt\T0-RECONCILE-ROADMAP-INDEX
allow_paths:
  - docs/TASK-BOARD.md
  - specs/tech-debt-tracker.md
forbid:
  - 修改产品代码或把 todo 设计描述为已实现
  - 复活已归档的旧任务卡或复制已由上游合并的脚手架改动
non_goals:
  - 创建或实现七张新卡
  - 修改 UI 设计真相源或 lessons 账本
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'docs/TASK-BOARD.md' -Pattern '^\| W1 \| T1-LOCAL-DATA-SECURITY \|') -and (Select-String -Path 'docs/TASK-BOARD.md' -Pattern '^\| W5 \| T5-OPERATION-EVENT-STORE \|') -and (Select-String -Path 'specs/tech-debt-tracker.md' -Pattern '^\| TD160 \|') -and (Select-String -Path 'specs/tech-debt-tracker.md' -Pattern '^\| TD161 \|'))) { exit 1 }"
dod_exit: 0
dod_assert: 七张边界明确的未来实现卡均被 Task Board 收录，TD160/TD161 与其偿还路径一致，所有依赖 id 可由 check-cards 解析
review_gate: codex {verdict:pass}
hygiene: 任务表只投影卡片事实，不复制完整实现规格
doc_sync: Task Board、tech-debt tracker 与七张卡互相引用一致（R5）
---

# T0-RECONCILE-ROADMAP-INDEX

## 产出

把前两张卡已经登记的七个未来实现单元投影到当前任务图，并修正与现有备份/媒体生命周期决策冲突的技术债描述。完整规格继续以任务卡为准。

## 验收

执行 front matter 的 `dod_command`，随后运行 `scripts/check-cards.ps1` 与 `scripts/verify.ps1`。
