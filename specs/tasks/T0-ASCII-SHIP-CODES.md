---
id: T0-ASCII-SHIP-CODES
title: 将 ship saga 与 CI gate 的机器判定迁到稳定 ASCII 状态码
depends_on: [T0-RECEIPT-LOSS-FAIL-CLOSED]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
status: todo
branch: T0-ASCII-SHIP-CODES
worktree: C:\wt\T0-ASCII-SHIP-CODES
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/QUALITY-RUBRIC.md
forbid:
  - 改变任何 gate 决策、顺序、退出码、恢复路径或 mandatory R3 语义
  - 让 selftest 同时接受旧中文锚点和新状态码而形成双重真相源
non_goals:
  - 迁移 check-cards、check-secrets、review、archive 或 init 的消息
  - 重写 ship saga、移除 RED/waterline receipt 或修改 CI gate 行为
  - 统一所有人类可读文案为英文
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/task.ps1 -SimpleMatch '[SAGA-DONE]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '[SAGA-FAIL]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '[CI-GATE-RED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SAGA-RESUME]'))) { exit 1 }"
dod_exit: 0
dod_assert: task.ps1 的 saga 进度、merge 守卫、scope/push/PR/CI 失败与恢复指令各有唯一 ASCII code；selftest 的正负断言锚到 code 或可执行命令而非本地化 prose；逐场景验证迁移前后决策、退出码与 saga leg 顺序不变。
review_gate: codex {verdict:pass}
hygiene: 先盘点所有运行时消息与测试消费者，再逐族迁移；每个 code 至少有一条生产发射点和一条精确测试，删除发射点须令对应场景红
doc_sync: DEVOPS-WORKFLOW 记录 code roster；QUALITY-RUBRIC 规定机器消费 code、人类阅读 prose
---

# T0-ASCII-SHIP-CODES

## 产出

在 `T0-RECEIPT-LOSS-FAIL-CLOSED` 完成整条 CI/恢复链后，选择性回填上游 v0.34 的 ship 状态码思路。先稳定 saga、候选 CI 与 receipt-loss，再迁移机械锚点，避免功能实现与状态码变更混在同一 PR。

## 验收原则

- code 是机器契约，prose 可本地化。
- 负断言必须改锚到新 live text/code，不能留在已死亡字符串上形成 vacuous green。
- 本卡只换观测面，不改变控制流；diff 中若出现新增/删除 gate 分支即越界。
