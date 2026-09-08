---
id: T0-RECEIPT-LOSS-SOURCE-CONTRACT
title: 对 receipt-loss 单一路径补齐源码合同、enum 与 mutation 防回归
depends_on: [T0-RECEIPT-LOSS-FAIL-CLOSED]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 15q/15r/15g/15s/17ai 分别输出大小写敏感具名 PASS，并绑定真实 ship 分支、marker、catch、docs block 与 enum site"
  - "A2 每个删除、移动、插入或重复变异先证明恰好命中一次，再由唯一对应合同拒绝；no-op mutation 必须失败"
  - "A3 catch 文件 presence、旧 review/status/CI/merge/cleanup recipe、-SkipRed、publish 后 reset/rebase/历史改写或陈旧 enum discovery 任一恢复都会使测试失败"
  - "A4 不修改 runtime 或 docs，不复制 T37；行为真相继续由 B 的真实 pushed fixture 承担"
  - "A5 workflow 与 seeded-git 均 PASS，card-inclusive diff 不超过 300 行/35000 字符内部目标"
status: todo
branch: T0-RECEIPT-LOSS-SOURCE-CONTRACT
worktree: C:\wt\T0-RECEIPT-LOSS-SOURCE-CONTRACT
allow_paths:
  - specs/tasks/T0-RECEIPT-LOSS-SOURCE-CONTRACT.md
  - scripts/selftest.ps1
forbid:
  - 修改 scripts/task.ps1、两份运维文档、review.ps1 或 check-scope.ps1
  - 复制 B 的 T37 远端行为夹具
  - 以全文包含字符串作为唯一行为证据
non_goals:
  - 新增任何运行时、恢复命令或第二条 merge 链
  - 重测第三方或 PowerShell 框架自身行为
diagnosis:
  root_cause: B 的行为修复若只靠端到端正负例，源码锚点移动、旧旁路复活或 enum/discovery 陈旧仍可能在未触达分支中存活。
  same_class: 覆盖四谓词、两处授权来源、publish marker、catch、四类状态、文档块和全部 receipt-loss enum/discovery site。
dod_command: $w = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow *>&1 | Out-String); $we = $LASTEXITCODE; $w; if ($we -ne 0 -or $w -cnotmatch '15q.+OK' -or $w -cnotmatch '15r.+OK' -or $w -cnotmatch '15g.+OK' -or $w -cnotmatch '15s.+OK') { exit 1 }; $g = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-git *>&1 | Out-String); $ge = $LASTEXITCODE; $g; if ($ge -ne 0 -or $g -cnotmatch '17ai.+OK') { exit 1 }
dod_exit: 0
dod_assert: 15q/15r/15g/15s/17ai 分别绑定真实 ship 分支、publish marker、receipt-loss marker、catch 与文档块；删除 p1/p2/p3/p4 或授权赋值、移动或删除 publish marker、删除 redSha 后继与远端祖先 reset-safe 条件、删除状态/人工升级/未合并/normal-ship-only、插入 -SkipRed/review/CI/merge/cleanup/reset/rebase/历史改写、删除或重复 enum site 的变异均被对应具名闸拒绝，且每个变异先证明唯一实际命中。
review_gate: codex {verdict:pass}
hygiene: C 只维护静态合同与 enum/discovery mutation，行为 oracle 复用 B 已交付的真实 fixture；publish marker 顺序和 reset-safe 条件分别变异，每个 mutation 必须非空且只击穿目标 site。
doc_sync: 只验证 B 已同步的 task/docs 契约，不修改文档。
---

# T0-RECEIPT-LOSS-SOURCE-CONTRACT

## 轻量计划

1. 从真实 ship 分支提取四谓词、两处授权、publish marker、catch 和 receipt-loss 状态块。
2. 为 15q/15r/15g/15s/17ai 建唯一 site 与顺序合同，退役旧手工恢复 discovery。
3. 逐一删除、移动、插入或重复目标；每个 mutation 先证明唯一命中，再要求专属合同变红。
4. 只改本卡与 `scripts/selftest.ps1`；复用 B 的行为 oracle，不复制 T37。

## 内部尺寸闸

目标上限为 300 changed lines / 35,000 normalized diff chars（含本卡），比仓库正式硬闸更严；预计承接的 WIP 源码合同子集约 194 行 / 26,261 字符。
