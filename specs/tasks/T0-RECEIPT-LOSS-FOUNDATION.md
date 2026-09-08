---
id: T0-RECEIPT-LOSS-FOUNDATION
title: 建立 receipt-loss 单一路径与旧恢复旁路退役基线
depends_on: [T0-RECEIPT-NORMAL-SHIP-HARNESS]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 首次 mint 失败保持既有 best-effort 语义；HEAD 已前移并偏离 RED 证据 sha、且重入无法取得有效 receipt 授权时进入稳定 T35 fail-closed，不撤销 A 的同轮内存授权；验收 fixture 必须先成功铸据再制造 missing"
  - "A2 receipt-loss catch 只保留只读诊断、人工升级、PR 未合并；不输出或调用 review/status/comment/candidate-CI/merge/cleanup、-Phase red 或 -SkipRed 恢复捷径"
  - "A3 H1 的真实 fixture 保持先成功铸造 receipt；本卡将 missing 负例强化为稳定 T35、零下游且保持 HEAD/ref/PR/evidence，原 receipt 有效的 -NoAutoMerge 正例仍走 normal ship 最终快照"
  - "A4 receipt-loss 一律无 reset/rebase/历史改写授权；严格 reset-safe、唯一 publish-start marker、四态细分与远端拓扑矩阵由串行后继 T0-RECEIPT-LOSS-FAIL-CLOSED 交付"
  - "A5 退役 15q/15g/15s/17ai 中要求手工 review/CI/merge 管线的旧断言，保留仍有效的 receipt 四谓词、cleanup、范围与预算检查；H1 已删除旧 T37/4 手工 recipe，本卡只强化其 receipt-loss 输出合同"
  - "A6 DEVOPS-WORKFLOW、DELIVERY-CHAINS、CLAUDE 命令入口与 LEDGER L116 统一说明 receipt-loss 只做只读诊断、保留未合并现场，恢复有效 receipt 后重跑 normal ship；S3 以 receipt 有效为前提，S9 仅描述已发布操作纪律"
  - "A7 workflow 与 seeded-remote 均 PASS；card-inclusive diff 小于 1000 行/60000 字符"
status: todo
branch: T0-RECEIPT-LOSS-FOUNDATION
worktree: C:\wt\T0-RECEIPT-LOSS-FOUNDATION
allow_paths:
  - specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 把首次 mint 失败冒充成功铸造后的 receipt-loss
  - 以 -SkipRed、-Phase red、reset/rebase 或第二套 review/CI/merge 管线绕过 T35
  - 仅删除旧测试而不同时交付真实 fail-closed 行为与本地行为 oracle
non_goals:
  - 严格 reset-safe、唯一 publish-start marker、完整四态远端 T37 与远端拓扑矩阵
  - 完整源码 enum/discovery/mutation 合同
dod_command: $gm='  15g(receipt) 水位线收据机制 OK（铸造/四谓词/失效 stable invalid/祖先两正例/no-op·-SkipRed 抑制/resume 重铸/cleanup 清据+失败告警/-Local 保真/不可写 best-effort）';$gp='(?m)^'+[regex]::Escape($gm)+'\r?$';$w=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow *>&1|Out-String);$we=$LASTEXITCODE;$w;if($we-ne 0-or([regex]::Matches($w,$gp).Count-ne 1)){exit 1};$r=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1|Out-String);$re=$LASTEXITCODE;$r;$rp='(?m)^'+[regex]::Escape('  T37-REMOTEMX/4 receipt resume OK（真实已铸 receipt→missing 非零停止；零下游且 HEAD/ref/PR/evidence 保留；原始 receipt 恢复后 NoAutoMerge 经 normal ship 完成随机身份最终快照）')+'\r?$';if($re-ne 0-or([regex]::Matches($r,$rp).Count-ne 1)){exit 1}
dod_exit: 0
dod_assert: workflow 逐字输出一次 `  15g(receipt) 水位线收据机制 OK（铸造/四谓词/失效 stable invalid/祖先两正例/no-op·-SkipRed 抑制/resume 重铸/cleanup 清据+失败告警/-Local 保真/不可写 best-effort）`；seeded-remote 真实执行先铸据再 missing 的零下游负例与有效 receipt -NoAutoMerge 正例，并逐字输出一次 `  T37-REMOTEMX/4 receipt resume OK（真实已铸 receipt→missing 非零停止；零下游且 HEAD/ref/PR/evidence 保留；原始 receipt 恢复后 NoAutoMerge 经 normal ship 完成随机身份最终快照）`；旧手工恢复管线已退役；四处权威说明无授权矛盾。
review_gate: codex {verdict:pass}
hygiene: 使用隔离 git/bare-origin 与真实 task.ps1；负例先证明 receipt 曾成功铸造，再删除；记录 HEAD/ref/PR/evidence/receipt 与下游哨兵，正例证明 fixture 非惰性。
doc_sync: 同步 DEVOPS-WORKFLOW、DELIVERY-CHAINS、CLAUDE 范围命令入口与 LEDGER L116 的现行 rule；保留 L116 历史 symptom/root_cause/refs，不改 receipt-loss 之外的项目事实。
---

# T0-RECEIPT-LOSS-FOUNDATION

## 轻量计划

1. 保留 A 的内存授权与首次 mint best-effort 边界，切断成功铸造后 receipt-loss 的第二套恢复管线。
2. 强化 H1 真实 seeded fixture 的已铸据→missing 停止输出和有效 receipt 正例，再最小退役相冲突的旧断言。
3. 同步四处权威说明并执行 workflow、seeded-remote、范围、预算与 R3；reset-safe、marker、完整四态交给串行后继 B。

## 被否决方案

- 不交付没有行为 oracle 的 production-only 改动；H1 的真实 fixture 是本卡强化并验收的底座。
- 不把所有首次 mint 失败改成硬停止；A1 只讨论已经成功铸造后的丢失。
- 不在本卡重写通用 -SkipRed 或新增 S9 状态机。
