---
id: T0-ASCII-CARD-SECRET-CODES
title: 将任务卡与防泄露脚本的机器判定迁到 ASCII 状态码
depends_on: [T0-ASCII-SHIP-CODES]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
status: todo
branch: T0-ASCII-CARD-SECRET-CODES
worktree: C:\wt\T0-ASCII-CARD-SECRET-CODES
allow_paths:
  - scripts/check-cards.ps1
  - scripts/check-secrets.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
forbid:
  - 改变任务卡 schema、secret 模式、扫描范围、退出码或 fail-closed 语义
  - 顺手迁移 review/archive/init 或增加新 gate
non_goals:
  - 修改用户可读语言政策
  - 重构 check-cards 解析器或 check-secrets 扫描算法
  - 实现新的 secret detector
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/check-cards.ps1 -SimpleMatch '[CARD-CHECK-PASS]') -and (Select-String -Path scripts/check-secrets.ps1 -SimpleMatch '[SECRETS-CHECK-PASS]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SECRETS-SKIP-NONGIT]'))) { exit 1 }"
dod_exit: 0
dod_assert: check-cards 与 check-secrets 的 pass/fail/skip 和具名关键诊断具有唯一 ASCII code；现有 CARD-TOKEN-LITERAL 等 code 保持兼容；selftest 只锚 code/命令并证明所有原决策与退出码不变。
review_gate: codex {verdict:pass}
hygiene: 迁移前后以场景矩阵对比 exit/code；删除关键 code 发射点的最小变异必须命中专属断言
doc_sync: QUALITY-RUBRIC 补充状态码命名与兼容约束
---

# T0-ASCII-CARD-SECRET-CODES

## 产出

状态码第二波的窄卡，只处理任务卡校验与防泄露两个高频确定性入口。它依赖第一波建立命名和测试模式，但不触碰 ship 行为。
