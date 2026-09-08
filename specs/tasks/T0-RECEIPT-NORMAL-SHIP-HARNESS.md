---
id: T0-RECEIPT-NORMAL-SHIP-HARNESS
title: 将 T37 receipt 恢复夹具收敛到真实 normal ship 单一路径
depends_on: [T0-RECEIPT-AUTHORIZATION-BIT]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 T37-REMOTEMX/4 删除 selftest 内第二套手工 review/status/CI/merge recipe 实现，改由真实 task.ps1 normal ship 提供唯一被测控制流；不改生产或文档语义"
  - "A2 隔离 bare-origin fixture 先真 RED、再以随机 PR/run/attempt 完成 -NoAutoMerge normal ship，证明 valid receipt 已铸、任务 ref 已 push、PR open 且最终 candidate-CI 快照已到达"
  - "A3 删除已铸 receipt 后，同一 normal ship 以现有 RED/TD85 边界非零停止；review/status/comment/candidate-CI/merge/cleanup/T24 零消费，HEAD/ref/PR/evidence 与完整 worktree 快照保持"
  - "A4 逐字节恢复同一 valid receipt 后，-NoAutoMerge 正例再次走完随机 PR/head/run/attempt/CWD 最终快照，且不触达 merge/cleanup/T24，证明 fixture 非惰性"
  - "A5 保留两枚使用可解析错误 OID 的 scope tip/base 身份负例；成功行逐字为卡片 DoD 指定的 H1 marker"
  - "A6 workflow 与 seeded-remote 均 PASS；card-inclusive diff 小于 1000 行/60000 字符"
status: merged
branch: T0-RECEIPT-NORMAL-SHIP-HARNESS
worktree: C:\wt\T0-RECEIPT-NORMAL-SHIP-HARNESS
allow_paths:
  - specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md
  - scripts/selftest.ps1
forbid:
  - 修改 scripts/task.ps1、运维文档、receipt 状态分类或恢复授权
  - 用 if(false)、skip、仅静态文本或自写第二套恢复管线替代真实 task.ps1
  - 提前断言 FOUNDATION 的 T35 state、禁止建议、reset-safe、publish marker 或四态语义
non_goals:
  - 改变现有 receipt-loss 输出或运行时决策
  - 交付 FOUNDATION/B 的 fail-closed、四态、reset-safe 或源码 mutation 合同
dod_command: $m='  T37-REMOTEMX/4 receipt resume OK（真实已铸 receipt→missing 非零停止；零下游且 HEAD/ref/PR/evidence 保留；原始 receipt 恢复后 NoAutoMerge 经 normal ship 完成随机身份最终快照）';$p='(?m)^'+[regex]::Escape($m)+'\r?$';$w=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow *>&1|Out-String);$we=$LASTEXITCODE;$w;if($we-ne 0){exit 1};$r=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1|Out-String);$re=$LASTEXITCODE;$r;if($re-ne 0-or([regex]::Matches($r,$p).Count-ne 1)){exit 1}
dod_exit: 0
dod_assert: workflow 以既有选择集合 exit 0；seeded-remote 逐字输出一次 `  T37-REMOTEMX/4 receipt resume OK（真实已铸 receipt→missing 非零停止；零下游且 HEAD/ref/PR/evidence 保留；原始 receipt 恢复后 NoAutoMerge 经 normal ship 完成随机身份最终快照）`；missing 与 restored-valid 两支都真实执行并满足 A2–A5。
review_gate: codex {verdict:pass}
hygiene: 使用隔离 git/bare-origin、真实 task.ps1、随机 PR/run/attempt 与 endpoint/CWD/event 哨兵；先证明 receipt 真铸造再删除，恢复时复用原始字节；删除旧手工 recipe 而非禁用。
doc_sync: 本卡只做既有行为的 harness 重构，不改生产或文档；FOUNDATION 负责随后反转 receipt-loss 操作合同。
---

# T0-RECEIPT-NORMAL-SHIP-HARNESS

## 轻量计划

1. 用真实 normal ship 替换 T37/4 内自写的手工 review/merge recipe，保留隔离远端和随机身份。
2. 锁定“已铸据→missing 现有非零停止→原字节恢复→valid NoAutoMerge 最终快照”的正反例。
3. 保留 scope 身份负例，执行 workflow、seeded-remote、范围、预算与 R3；不提前实现后继语义。

## 被否决方案

- 不用 `if ($false)`、skip 或只删旧断言制造预算假绿。
- 不在测试里复制第三套 ship/review/merge 控制流。
- 不把 H1 描述为 TDD 功能改动；它是保持既有运行时语义的可执行 harness 重构。
