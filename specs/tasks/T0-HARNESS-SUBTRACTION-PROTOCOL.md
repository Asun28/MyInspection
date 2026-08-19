---
id: T0-HARNESS-SUBTRACTION-PROTOCOL
title: 为常驻 harness 文本增加量化、可回滚的减负协议
depends_on: []
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: [T0-R3-DIFF-BUDGET,T0-LESSONS-COLD-RECALL]
status: todo
branch: T0-HARNESS-SUBTRACTION-PROTOCOL
worktree: C:\wt\T0-HARNESS-SUBTRACTION-PROTOCOL
allow_paths:
  - docs/HARNESS-REVIEW.md
forbid:
  - 在本卡中实际删除、降级或搬迁任何现有 hard rule、lesson 或机器锚点
  - 把 filtered selftest 当最终验收或把字节减少本身当成功
non_goals:
  - 精简 CLAUDE.md 或 CLAUDE.template.md
  - 降级 L165/L196 或改变任何脚本行为
  - 建自动删文脚本
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path docs/HARNESS-REVIEW.md -SimpleMatch 'Quantified subtraction protocol') -and (Select-String -Path docs/HARNESS-REVIEW.md -SimpleMatch 'One group per commit') -and (Select-String -Path docs/HARNESS-REVIEW.md -SimpleMatch 'load-bearing'))) { exit 1 }"
dod_exit: 0
dod_assert: HARNESS-REVIEW 定义候选分组与 backing、删除前迁移核验和机器锚点盘点、一组一提交、完整验收加真实任务回放、按组记录 byte/line delta 并在退化时 revert/load-bearing 的五步协议。
review_gate: codex {verdict:pass}
hygiene: 纯方法论卡只做指针完整性与关键步骤静态断言，不制造模拟收益数据
doc_sync: none；本卡自身即权威方法文档
---

# T0-HARNESS-SUBTRACTION-PROTOCOL

## 产出

选择性回填上游 v0.33 的五步减负协议，作为以后精简常驻 instruction 的正门。本卡只建协议，不提前执行删减，因此可以与不触碰本文件的实现卡并行。

## 必须保留的边界

1. 无机械闸或权威迁移目标的红线不是候选。
2. 删除前先打开目标并确认内容真实存在，再盘点 literal/heading/count 等机器锚点。
3. 一组一个 commit，允许独立 revert。
4. 最终验收必须跑完整套件，并回放近期真实卡确认指针可达。
5. 每组记录前后字节/行数；质量下降即回滚并标记 load-bearing。
