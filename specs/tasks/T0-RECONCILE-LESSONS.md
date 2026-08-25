---
id: T0-RECONCILE-LESSONS
title: 将本地可复现经验按当前 lessons schema 归并到账本
depends_on: []
parallelizable_with: [T0-RECONCILE-DATA-AUTHORITY, T0-RECONCILE-DESIGN-METADATA]
status: todo
branch: T0-RECONCILE-LESSONS
worktree: C:\wt\T0-RECONCILE-LESSONS
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 整体覆盖上游账本、复用已有 id 或删除远端新增经验
  - 登记只在旧未合并提交成立的瞬时事实
non_goals:
  - 修改 lessons 工具、脚手架或历史归档
  - 把普通产品设计偏好晋升为仓库铁律
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L242$') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch '四参静态 [regex]::Replace') -and (Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L243$') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch 'Assert-MeasuredTip') -and (Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L244$') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch 'git apply --3way') -and (Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L245$') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch 'required 的匹配用 assert') -and (Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L246$') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch 'review.ps1 -WorktreePath') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch '-SizeOnly') -and (Select-String -Path 'docs/lessons/LEDGER.md' -Pattern '^## L247$') -and (Select-String -Path 'docs/lessons/LEDGER.md' -SimpleMatch 'archive.ps1 -CheckCardsIndex'))) { exit 1 }" && pwsh -NoProfile -File scripts/lessons.ps1 check
dod_exit: 0
dod_assert: 精确登记 L242–L247 六条长期有效经验及其具名证据锚点（regex 单点变异、HEAD/分支双钉、merge-base patch、脚本剥离断言、SizeOnly 权威量尺、archive 投影），再证明 id 唯一且 meta/enforced_by/refs 满足当前 schema；不迁移后台轮询习惯、已合并前参数说明或无证据的泛化条目
review_gate: codex {verdict:pass}
hygiene: 使用 lessons.ps1 add/check 规范化，不手工复制旧账本块；相同根因合并为复发计数或现有规则增量
doc_sync: CLAUDE.md 只接晋升后的最小规则增量（R5）
---

# T0-RECONCILE-LESSONS

## 产出

把本地账本中仍然可证、对当前上游仍有价值的经验重新编号并按现行 schema 登记；重复项并入既有 lesson，已被上游实现消解的瞬时说明不迁移。

## 验收

执行 front matter 的 `dod_command`，并逐条复核 evidence、enforced_by 与 refs 是否仍指向当前仓库事实。
