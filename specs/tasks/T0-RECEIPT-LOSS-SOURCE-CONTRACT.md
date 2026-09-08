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
dod_command: $w = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow *>&1 | Out-String); $we = $LASTEXITCODE; $w; function EL([string]$s) { '(?m)^[ \t]*' + [regex]::Escape($s) + '\r?$' }; $wp = @{'15q'=(EL '15q receipt-loss 源码合同 OK（唯一授权/marker/catch/enum/docs 锚点；旧 review/status/CI/merge/cleanup 旁路与 -SkipRed/reset/rebase/history rewrite 变异均拒绝）'); '15r'=(EL '15r receipt-loss 源码合同 OK（15r 具名 ship 合同与 B 行为路径分离；删除/移动/插入/重复/no-op mutation 均由唯一合同拒绝）'); '15g'=(EL '15g(receipt) receipt-loss 源码合同 OK（四谓词、两授权来源、publish marker、catch、四状态、enum/discovery 唯一锚点；runtime/docs/T37 不复制）'); '15s'=(EL '15s receipt-loss 源码合同 OK（task/docs/enum discovery 静态锚点与唯一命中 mutation 证据完整；行为 oracle 复用 B）')}; if ($we -ne 0 -or @($wp.GetEnumerator() | Where-Object { ([regex]::Matches($w,$_.Value).Count -ne 1) }).Count) { exit 1 }; $g = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-git *>&1 | Out-String); $ge = $LASTEXITCODE; $g; $gp = EL '17ai receipt-loss card budget OK（working-tree projection 与 pinned SizeOnly 均不超过 300 行/35000 字符；binary/untracked/malformed/native failure 均 fail-closed）'; if ($ge -ne 0 -or ([regex]::Matches($g,$gp).Count -ne 1)) { exit 1 }; $untracked = (& git ls-files --others --exclude-standard 2>&1 | Out-String); $ue = $LASTEXITCODE; if ($ue -ne 0 -or $untracked.Trim()) { exit 1 }; $baseOid = (& git rev-parse --verify 'refs/remotes/origin/master^{commit}' 2>&1 | Out-String).Trim(); $be = $LASTEXITCODE; $headOid = (& git rev-parse --verify 'HEAD^{commit}' 2>&1 | Out-String).Trim(); $he = $LASTEXITCODE; $merge = (& git merge-base $baseOid $headOid 2>&1 | Out-String).Trim(); $me = $LASTEXITCODE; if ($be -ne 0 -or $he -ne 0 -or $me -ne 0 -or $baseOid -notmatch '^[0-9a-f]{40}$' -or $headOid -notmatch '^[0-9a-f]{40}$' -or $merge -notmatch '^[0-9a-f]{40}$') { exit 1 }; $ns = (& git -c core.quotepath=false diff --no-ext-diff --no-textconv --numstat $merge -- 2>$null | Out-String); $ne = $LASTEXITCODE; if ($ne -ne 0) { exit 1 }; $changed = [long]0; $nsLines = [regex]::Split($ns, '\r?\n'); if ($nsLines.Count -gt 0 -and $nsLines[$nsLines.Count - 1] -eq '') { $nsLines = @($nsLines | Select-Object -SkipLast 1) }; foreach ($line in $nsLines) { if ($line -notmatch '^(\d+|-)\t(\d+|-)\t[^\t\r\n]+$' -or $Matches[1] -eq '-' -or $Matches[2] -eq '-') { exit 1 }; $changed += [long]$Matches[1] + [long]$Matches[2] }; if ($changed -gt 300) { exit 1 }; $udRaw = (& git -c core.quotepath=false diff --no-ext-diff --no-textconv --unified=3 $merge -- 2>$null | Out-String); $de = $LASTEXITCODE; $ud = $udRaw -replace "`r`n", "`n"; if ($de -ne 0 -or $ud.Length -gt 35000) { exit 1 }; $budgetBase = (& git rev-parse --verify 'refs/remotes/origin/master^{commit}' 2>&1 | Out-String).Trim(); $bbe = $LASTEXITCODE; if ($bbe -ne 0 -or $budgetBase -cne $baseOid) { exit 1 }; $refName = 'codex/receipt-budget-' + [guid]::NewGuid().ToString('N'); $ref = 'refs/heads/' + $refName; $zero = '0' * 40; $made = $false; try { & git update-ref $ref $baseOid $zero; $ce = $LASTEXITCODE; if ($ce -ne 0) { exit 1 }; $made = $true; $size = (& pwsh -NoProfile -File scripts/review.ps1 -Base $refName -LocalBase -WorktreePath (Get-Location).Path -SizeOnly -MaxChangedLines 300 -MaxDiffChars 35000 *>&1 | Out-String); $sizeExit = $LASTEXITCODE; $size; if ($sizeExit -ne 0) { exit 1 }; $afterHead = (& git rev-parse --verify 'HEAD^{commit}' 2>&1 | Out-String).Trim(); $ae = $LASTEXITCODE; $afterBase = (& git rev-parse --verify 'refs/remotes/origin/master^{commit}' 2>&1 | Out-String).Trim(); $abe = $LASTEXITCODE; if ($ae -ne 0 -or $abe -ne 0 -or $afterHead -cne $headOid -or $afterBase -cne $baseOid) { exit 1 } } finally { if ($made) { & git update-ref -d $ref $baseOid; $cleanupExit = $LASTEXITCODE; if ($cleanupExit -ne 0) { exit 1 } } }
dod_exit: 0
dod_assert: 15q/15r/15g/15s/17ai 分别绑定真实 ship 分支、publish marker、receipt-loss marker、catch 与文档块；删除 p1/p2/p3/p4 或授权赋值、移动或删除 publish marker、删除 redSha 后继与远端祖先 reset-safe 条件、删除状态/人工升级/未合并/normal-ship-only、插入 -SkipRed/review/CI/merge/cleanup/reset/rebase/历史改写、删除或重复 enum site 的变异均被对应具名闸拒绝，且每个变异先证明唯一实际命中。DoD 专用成功行必须逐字为：15q receipt-loss 源码合同 OK（唯一授权/marker/catch/enum/docs 锚点；旧 review/status/CI/merge/cleanup 旁路与 -SkipRed/reset/rebase/history rewrite 变异均拒绝）；15r receipt-loss 源码合同 OK（15r 具名 ship 合同与 B 行为路径分离；删除/移动/插入/重复/no-op mutation 均由唯一合同拒绝）；15g(receipt) receipt-loss 源码合同 OK（四谓词、两授权来源、publish marker、catch、四状态、enum/discovery 唯一锚点；runtime/docs/T37 不复制）；15s receipt-loss 源码合同 OK（task/docs/enum discovery 静态锚点与唯一命中 mutation 证据完整；行为 oracle 复用 B）；17ai receipt-loss card budget OK（working-tree projection 与 pinned SizeOnly 均不超过 300 行/35000 字符；binary/untracked/malformed/native failure 均 fail-closed）。
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
