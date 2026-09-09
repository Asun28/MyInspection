---
id: T0-SHIP-SAGA-REPORTER
title: 将 T26 未授权恢复报告收敛到既有操作文档
depends_on: [T0-RECEIPT-NORMAL-SHIP-HARNESS]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: todo
branch: T0-SHIP-SAGA-REPORTER
worktree: C:\wt\T0-SHIP-SAGA-REPORTER
allow_paths:
  - specs/tasks/T0-SHIP-SAGA-REPORTER.md
  - scripts/task.ps1
  - scripts/selftest.ps1
acceptance:
  - "A1 T26 的 post-watershed 未授权 fallback（HEAD 已前移、未用 -SkipRed、未取得本轮 receipt 内存授权，且不在 Local 合并阶段专用恢复分支）只报告并保留现场，指向 DEVOPS 的 TD85-RESUME/S1–S9 人工核对，不再复制或调用 review/status/CI/merge/cleanup/reset 配方。"
  - "A2 已完成腿、失败点、待办腿及原样 bare throw 保持；首次 mint 不可写仍按既有 best-effort 继续，正常授权、选项透传、Local 阶段和早期 RED 提示均不改变。"
  - "A3 在真实隔离 Git fixture 中先建立 RED，再令首次 mint 写入失败，提交成功后命中真实范围闸；证明未授权 reporter 被调用、原范围异常保留且非零、现场保留和第二套恢复配方不输出。不得以已铸据后删除冒充首次 mint 失败。"
  - "A4 同步 15r(d)、15g 静态2 和 17ai 的 catch-only enum/discovery；保留有效授权锁、15r(e) 既有正反例、early hint、文档枚举及 15g⑦。这些后续退役责任仍由 FOUNDATION 承担。"
  - "A5 workflow 与 seeded-scanner 均退出 0，分别精确输出 DoD 的 reporter 行和 17ai 行一次；测试通过真实 task.ps1 入口验证，不能只检查源码文字。"
  - "A6 card-inclusive diff 不超过 1000 changed lines / 60000 chars；不新增依赖，不改生产 task 的成功路径。"
  - "A7 依据 DEVOPS-WORKFLOW §1 的 R5 默认约定，自身 status 可在本 PR 投影合并后的 merged 状态；该投影不代替真实 R3、CI、合并凭据和 cleanup 证据。"
forbid:
  - 用 if(false)、skip 或删除有效测试制造通过
  - 改写 early RED 异常、首次 mint best-effort、receipt 四谓词或同轮内存授权
  - 把本卡描述为完整 receipt-loss fail-closed 或整段 stdout 已无手工恢复提示
non_goals:
  - FOUNDATION 的 receipt-loss flag、稳定 T35 失效停止和四处操作合同同步
  - B 的四态分类、publish marker、严格 reset-safe 和远端拓扑矩阵
  - 修改操作文档、全量源码合同或重设任何 R3 计数
dod_command: $m='  15r(e) T26 reporter authority OK（首次 mint best-effort 失败→范围闸失败；未授权 catch 只保留现场并委托 DEVOPS；旧 review/PostStatus/CI/merge 配方零输出；原异常裸抛）';$w=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow *>&1|Out-String);$we=$LASTEXITCODE;$w;if($we-ne 0-or([regex]::Matches($w,'(?m)^'+[regex]::Escape($m)+'\r?$').Count-ne 1)){exit 1};$m='  17ai diff 预算 OK（999/1000 行过；1001 拦；60000 过、60001 拦；两组参数冲突已拒）';$s=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-scanner *>&1|Out-String);$se=$LASTEXITCODE;$s;if($se-ne 0-or([regex]::Matches($s,'(?m)^'+[regex]::Escape($m)+'\r?$').Count-ne 1)){exit 1}
dod_exit: 0
dod_assert: workflow 精确输出一次 `  15r(e) T26 reporter authority OK（首次 mint best-effort 失败→范围闸失败；未授权 catch 只保留现场并委托 DEVOPS；旧 review/PostStatus/CI/merge 配方零输出；原异常裸抛）`；seeded-scanner 精确输出一次 `  17ai diff 预算 OK（999/1000 行过；1001 拦；60000 过、60001 拦；两组参数冲突已拒）`；两片实际退出 0。
review_gate: codex {verdict:pass}
hygiene: 扩展现有 15r(e) 真实 Local fixture；记录首次 mint 写入失败、提交和范围异常的前置证据，以及 catch 前后 HEAD/evidence、退出后 worktree/branch。以定点变异证明新增 reporter 行为断言有效；不改写历史 RED 或正式评审结果。
doc_sync: 本 PR 默认交付自身合并后状态投影；依赖链由登记卡维护。本卡只指向现有 DEVOPS，不修改其人工恢复授权。
---

# T0-SHIP-SAGA-REPORTER

## 轻量计划

1. 在既有 15r(e) 隔离仓中建立“首次 mint 不可写，提交后范围失败”的真实 RED，保留原异常和状态观测。
2. 将 T26 未授权 fallback 的重复配方替换为现场报告和既有文档指针，同步直接冲突的静态观测。
3. 完成两个分片、定点变异、项目闸门和正式 R3；FOUNDATION 再处理成功铸据后的 receipt-loss。

本卡可独立验证：失败报告停止自行推导操作配方，操作权限继续由既有 DEVOPS 管理。早期 RED 异常仍可能输出旧手工指引；本卡不声称这些后继责任已交付，也不撤销现行文档中的人工最后手段。
