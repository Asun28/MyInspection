---
id: T0-RECEIPT-LOSS-FAIL-CLOSED
title: 对已发布 receipt 丢失或不自洽状态执行单一路径 fail-closed 停止
depends_on: [T0-RECEIPT-AUTHORIZATION-BIT]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 真实生命周期先证明 receipt 已铸且存在，再于 watershed 后分别制造 missing/corrupt/inconsistent/store-unavailable；四态各自命中稳定 T35 state 并非零退出"
  - "A2 每个负例都证明 review/status/comment/candidate-CI/merge/cleanup/T24 未被消费，HEAD/ref/PR/evidence 保持"
  - "A3 有效四谓词 receipt 的随机身份 -NoAutoMerge 正例走完 normal ship 最终快照，证明 fixture 与哨兵非惰性"
  - "A4 receipt-loss 输出只允许只读诊断、人工升级、PR 保持未合并和恢复有效 receipt 后重跑 normal ship"
  - "A5 workflow 与 seeded-remote 均 PASS，card-inclusive diff 低于 1000 行/60000 字符"
  - "A6 唯一 publish-start marker 严格位于真实 git push 前；未发布正例证明 reset 前 HEAD 是有效 evidence.redSha 的非等后继，且 origin task ref 不存在或是 redSha 祖先，reset --soft 后精确归位 redSha；marker 已置位或 partially-pushed/remote-ahead/diverged 均零 reset/rebase/历史改写"
  - "A7 B 交付 15r(e) 与 T37 可执行行为 oracle，并只做 15q/15g/15s/17ai 维持中间 GREEN 所需的旧旁路退役；完整源码合同与 mutation 由 C 承接"
status: todo
branch: T0-RECEIPT-LOSS-FAIL-CLOSED
worktree: C:\wt\T0-RECEIPT-LOSS-FAIL-CLOSED
allow_paths:
  - specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - receipt-loss 分支调用 review.ps1 -PostStatus、候选 CI、gh pr merge 或 cleanup
  - 以 receipt 文件在场替代 A 的内存授权位
  - 自动重建、伪造 receipt，或允许诊断命令拥有合并授权
  - 使用 -SkipRed、reset、rebase 或历史改写绕过 T35 四谓词；唯一例外是 publish-start 前以有效 evidence.redSha 为唯一目标且远端不存在 post-RED 提交的既有 reset-safe 路由
non_goals:
  - 完整源码文本合同、enum/discovery 与删除或插入 mutation 网
  - 修改 candidate CI 正常 ship 控制流、check-scope 算法或质量预算
diagnosis:
  root_cause: post-watershed receipt 失效会进入一套手抄 review/status/CI/merge 恢复管线，使 T35 四谓词不再是唯一合并授权边界。
  same_class: 覆盖 missing、corrupt、inconsistent、store-unavailable 四态，以及 task、真实 T37、旧自检锚点和两份运维文档中的同类旁路。
dod_command: $w = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow *>&1 | Out-String); $we = $LASTEXITCODE; $w; if ($we -ne 0 -or $w -cnotmatch '15r\(e\).+OK') { exit 1 }; $r = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote *>&1 | Out-String); $re = $LASTEXITCODE; $r; if ($re -ne 0 -or $r -cnotmatch 'T37-REMOTEMX/4.+OK') { exit 1 }
dod_exit: 0
dod_assert: 真实已铸 receipt 在 watershed 后进入 missing/corrupt/inconsistent/store-unavailable 四态，均在 RED/receipt 边界以稳定 T35 状态非零停止，保留 worktree/branch/PR/evidence 并指向人工升级；review/status/comment/candidate-CI/merge/cleanup/T24/reset 哨兵全缺。有效四谓词 receipt 的 -NoAutoMerge 正例仍经 normal ship 到最终快照。未发布正例须证明 reset 前 HEAD 是有效 redSha 的非等后继、远端无 post-RED 提交，reset --soft 后 HEAD 精确归位 redSha；partially-pushed/remote-ahead/diverged 必须零 reset/rebase/历史改写。
review_gate: codex {verdict:pass}
hygiene: 用本地 bare origin 和真实 task.ps1 建随机 PR/head receipt-loss fixture；记录 publish-start marker 与 push/reset 哨兵，每个负例命中目标状态且证明零下游副作用，正例证明 mock 非惰性。旧手工恢复自检只做维持中间 GREEN 所需的最小退役。
doc_sync: DEVOPS-WORKFLOW 的 S2/S4/S5/S8/S9/TD85 与 DELIVERY-CHAINS 只保留只读诊断、人工升级、PR 未合并和恢复有效 T35 receipt 后重跑 normal ship。
---

# T0-RECEIPT-LOSS-FAIL-CLOSED

## 轻量计划

1. 在真实 git push 前设置唯一 publish-start marker；只保留 marker 未置位、当前 HEAD 是有效 evidence.redSha 后继、且 origin task ref 不存在或为 redSha 祖先时的既有 reset-safe 路由。
2. 分类已发布分支的四类 receipt 失效态，输出稳定 T35 状态并停止。
3. 删除第二套 review/status/CI/merge/cleanup 管线，保留现场与人工升级。
4. 以紧凑真实 T37 覆盖四个负例、remote-ahead/diverged 零 reset 和一个有效 receipt 正例；只做维持中间 GREEN 所需的旧断言退役。
5. 同步两份运维文档；完整源码合同与 mutation 网留给 C，并跑 workflow、seeded-remote、SizeOnly 与总验收。

## 可证伪验收

- missing/corrupt/inconsistent/store-unavailable 四态均 target-reaching、非零且零下游消费。
- publish-start marker 前的真未发布后继可以 evidence.redSha 字段值为唯一目标执行 `reset --soft` 并精确归位；marker 后或 partially-pushed/remote-ahead/diverged 均不 reset/rebase/改写历史。
- 有效四谓词 receipt 的随机身份正例跑到最终快照，避免 mock 惰性假绿。
- 文档只允许只读诊断、人工升级、PR 未合并和恢复有效 receipt 后重跑 normal ship。
- 51,000–55,000 字符只是拆分设计估算；交付以 card-inclusive 实际 `review.ps1 -SizeOnly` 的 1000/60000 硬闸为准，禁止整体移植超限 WIP。
