---
id: T0-ASCII-REVIEW-ARCHIVE-CODES
title: 将 review、archive 与 init 的剩余机器判定迁到 ASCII 状态码
depends_on: [T0-ASCII-CARD-SECRET-CODES]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
status: todo
branch: T0-ASCII-REVIEW-ARCHIVE-CODES
worktree: C:\wt\T0-ASCII-REVIEW-ARCHIVE-CODES
allow_paths:
  - scripts/review.ps1
  - scripts/archive.ps1
  - init-scaffold.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
forbid:
  - 删除本仓 mandatory reviewer sandbox/hardening、修改 verdict schema 或放宽 archive/init 安全边界
  - 改 ReviewTimeoutSec、ReviewRoundCap、ScaffoldVersion 或 init 模板载荷
non_goals:
  - 改 review prompt、archive 选择策略或 init 功能
  - 迁移已经由前两波覆盖的 task/check-cards/check-secrets 消息
  - 降级 L165/L196
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/review.ps1 -SimpleMatch '[R3-TIMEOUT-BUDGET]') -and (Select-String -Path scripts/archive.ps1 -SimpleMatch '[ARCHIVE-PASS]') -and (Select-String -Path init-scaffold.ps1 -SimpleMatch '[INIT-PASS]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[R3-TIMEOUT-BUDGET]'))) { exit 1 }"
dod_exit: 0
dod_assert: review/archive/init 的机器消费者全部改锚唯一 ASCII code，尤其 timeout budget、归档结果/拒绝和 init 完成/失败；现有 reviewer 防御、archive 幂等/暂存安全、init 输出和退出语义逐场景保持不变。
review_gate: codex {verdict:pass}
hygiene: 用调用点清单证明没有遗漏的机器 prose 消费者；每个迁移族做删除 code 的最小回归，不复制上游已删除的 reviewer 防御测试
doc_sync: QUALITY-RUBRIC 完成状态码 roster，并记录本仓刻意保留的 mandatory R3 差异
---

# T0-ASCII-REVIEW-ARCHIVE-CODES

## 产出

状态码第三波，只迁移上游 v0.36 中对本仓仍有价值的观测面。上游同时删除的 reviewer hardening、RED/receipt 和本地化政策均不属于本卡。

## TD134 总验收

本卡合并不自动宣告 TD134 paid。编排者还须确认 `T0-CI-MERGE-GATE`、`T0-HARNESS-SUBTRACTION-PROTOCOL`、`T0-LESSONS-COLD-RECALL` 与三张状态码卡全部 merged，运行完整 selftest、lessons check 与一次真实小卡 ship 回放后，才可关闭总债。
