---
id: T0-R3-DIFF-BUDGET-R3-CLOSURE
title: R3 diff 预算收口：禁用外部 diff 助手并贯穿已测量提交
depends_on: [T0-R3-DIFF-BUDGET]
parallelizable_with: []
status: todo
branch: T0-R3-DIFF-BUDGET-R3-CLOSURE
worktree: C:\wt\T0-R3-DIFF-BUDGET-R3-CLOSURE
allow_paths:
  - scripts/review.ps1
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
  - docs/DEVOPS-WORKFLOW.md
forbid:
  - 扩大 1000 changed lines / 60000 chars 默认预算
  - 允许 diff.external、textconv 或属性配置改变预算输入
  - 在 SizeOnly 后继续 push、review 或 merge 一个不同的提交
  - 运行全量 selftest；只运行 workflow 与 seeded 目标分片
non_goals:
  - 改 ReviewRoundCap、ReviewTimeoutSec、R3 模型或 effort
  - 实现 T0-CI-MERGE-GATE
  - 重开 T0-R3-DIFF-BUDGET 已闭合的边界、状态码与文档措辞
diagnosis: PR #53 第 2 轮 R3 证明预算仍信任仓库可控的外部 diff/textconv，且 SizeOnly 捕获的 SHA 未传给后续 push/review/merge；因此测量对象与发布对象可能不同，pre-push 硬闸可被成功的 diff helper 或 ref 移动绕过。
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/review.ps1 -SimpleMatch '--no-ext-diff') -and (Select-String -Path scripts/review.ps1 -SimpleMatch '--no-textconv') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '[R3-DIFF-TIP-MOVED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'R3-DIFF-TIP-MOVED'))) { exit 1 }"
dod_exit: 0
dod_assert: 所有预算 diff 强制 --no-ext-diff 与 --no-textconv；成功伪装 helper 不能缩小字符数。SizeOnly 输出机器可读的 captured SHA，task.ps1 在 push、R3 与本地/远端 merge 前逐次核对同一 SHA，并只发布该提交；SizeOnly 后 ref 前移的真实 ship 夹具必须在 push/review/merge 前 fail-closed。
review_gate: codex {verdict:pass}
hygiene: 只承接 PR #53 第 2 轮两项 finding；各保留一枚语义明确的行为夹具，mutant 的非预期崩溃不得冒充命中
doc_sync: R5 同步原卡与 TD142 的人裁/PR 结果；T0-CI-MERGE-GATE 仅在本卡合并后解锁
---

# T0-R3-DIFF-BUDGET-R3-CLOSURE

## 起因

`T0-R3-DIFF-BUDGET` PR #53 已通过 DoD、`verify`、`workflow` 与 `seeded`，但在第 2 轮 R3 仍有两项确定性缺口。仓库 `ReviewRoundCap = 2`，因此停止原 PR 的第三轮修改；原 PR 交由人裁，本卡只承接余项。

## 唯一范围

1. **可信 diff 输入**：`--stat`、`--numstat`、unified diff 的每次权威调用均显式禁用 external diff 与 textconv。测试配置一个成功退出、会压缩/隐藏正文的 helper，证明 60001 字符仍被阻断；失败 helper 与移动 ref 使用独立 shim，不混淆责任。
2. **同一提交贯穿 ship**：`review.ps1 -SizeOnly` 返回本次实际测量的完整提交 OID；`task.ps1` 记录该 OID，并在 push、正常 R3、本地 merge、远端 merge 前核对候选分支仍指向它。任何 ref 前移、后退或替换都以稳定状态码阻断，且不 push、不调用 reviewer、不 merge。
3. 正常 R3 必须审同一 OID；push/PR 后还要核对远端 PR head OID 等于已测量 OID，不能把“分支名相同”当作提交身份。

## 验收证据

- 成功 external diff/textconv spoof 不能让大 diff 通过；删除任一禁用参数，专属夹具以预算绕过的精确反例变红。
- 真实 `ship` 在 SizeOnly 返回后移动任务分支，必须在下一项外部副作用前阻断；断言 push/reviewer/merge 哨兵均不存在。
- 正常未移动分支仍可完成现有 hermetic ship；本地与远端路径均绑定 exact OID。
- 只运行 `workflow`、`seeded` 与项目 `verify`；不运行全量 selftest。

## 被否决方案

- 不把 ref 移动留给正常 R3 再发现：远端路径在 R3 前已经 push/建 PR，违反 pre-push 硬闸。
- 不依赖仓库配置“通常没有 diff.external/textconv”：被审仓库与环境均不属于预算权威。
- 不继续修改 PR #53：已达到两轮上限，继续追评会重现不可收敛循环。
