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
acceptance:
  - "A1 L242 block-local：静态 Regex.Replace 四参重载与跨行 whitespace 变异失靶，规则要求实例 Replace/count、[ tab] 行空白和变异后目标行确实改变；enforced_by 点名 selftest 2c/真实 mutation guard"
  - "A2 L243 block-local：consumer 读 HEAD 时必须同时钉 branch tip 与 HEAD，并显式传 OID；证据点名 Assert-MeasuredTip、R3-HEAD-MISMATCH 与 selftest 15b4"
  - "A3 L244/L245 block-local：旧分支成果按 merge-base patch + git apply --3way 重放；脚本剥离每个 required target 必须 assert 命中并做删除量/关键词残留对账"
  - "A4 L246 block-local：与 R3 阈值比较只使用 review.ps1 -SizeOnly 的 UTF-16 码元尺，证据指向 T0-R3-DIFF-BUDGET，不用 wc -c 做拆卡决策"
  - "A5 L247 block-local：归档搬运后必须跑 archive.ps1 -CheckCardsIndex，明确 cards-index 是机检投影非 doc_sync 文档，并保留 ARCHIVE-CARDS-INDEX-DRIFT 证据"
  - "A6 排除与 schema：不迁移后台轮询习惯、PR 未合并参数说明、无 refs 的泛化措辞；L242–L247 id 唯一且每块自身含 rule/enforced_by/refs，lessons check 全绿"
dod_command: pwsh -NoProfile -Command "if (-not (((Get-Content 'docs/lessons/LEDGER.md' -Raw) -match '(?ms)^## L242\r?\n(?:(?!^## L[0-9]+).)*四参静态 \[regex\]::Replace(?:(?!^## L[0-9]+).)*scripts/selftest\.ps1 闸 2c') -and ((Get-Content 'docs/lessons/LEDGER.md' -Raw) -match '(?ms)^## L243\r?\n(?:(?!^## L[0-9]+).)*Assert-MeasuredTip(?:(?!^## L[0-9]+).)*selftest 闸 15b4') -and ((Get-Content 'docs/lessons/LEDGER.md' -Raw) -match '(?ms)^## L244\r?\n(?:(?!^## L[0-9]+).)*git apply --3way(?:(?!^## L[0-9]+).)*git diff --numstat origin/master') -and ((Get-Content 'docs/lessons/LEDGER.md' -Raw) -match '(?ms)^## L245\r?\n(?:(?!^## L[0-9]+).)*required 的匹配用 assert(?:(?!^## L[0-9]+).)*关键词全文 grep') -and ((Get-Content 'docs/lessons/LEDGER.md' -Raw) -match '(?ms)^## L246\r?\n(?:(?!^## L[0-9]+).)*review\.ps1 -WorktreePath(?:(?!^## L[0-9]+).)*T0-R3-DIFF-BUDGET') -and ((Get-Content 'docs/lessons/LEDGER.md' -Raw) -match '(?ms)^## L247\r?\n(?:(?!^## L[0-9]+).)*archive\.ps1 -CheckCardsIndex(?:(?!^## L[0-9]+).)*ARCHIVE-CARDS-INDEX-DRIFT'))) { exit 1 }" && pwsh -NoProfile -File scripts/lessons.ps1 check
dod_exit: 0
dod_assert: A1–A6 的六个 block-local regex 全部命中自身 rule/evidence/enforced_by/refs，随后 lessons check 证明 id/schema/必须层无漂移；关键词放到别的 lesson 不会误绿
review_gate: codex {verdict:pass}
hygiene: 使用 lessons.ps1 add/check 规范化，不手工复制旧账本块；相同根因合并为复发计数或现有规则增量
doc_sync: CLAUDE.md 只接晋升后的最小规则增量（R5）
---

# T0-RECONCILE-LESSONS

## 产出

把本地账本中仍然可证、对当前上游仍有价值的经验重新编号并按现行 schema 登记；重复项并入既有 lesson，已被上游实现消解的瞬时说明不迁移。

## 验收

执行 front matter 的 `dod_command`，并逐条复核 evidence、enforced_by 与 refs 是否仍指向当前仓库事实。
