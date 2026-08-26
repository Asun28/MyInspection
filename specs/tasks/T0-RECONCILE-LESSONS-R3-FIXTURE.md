---
id: T0-RECONCILE-LESSONS-R3-FIXTURE
title: 移除 lessons 夹具中的未合并身份守卫声明
depends_on: [T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE]
status: todo
branch: T0-RECONCILE-LESSONS-R3-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-R3-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或其他调和卡
  - 把未合并任务规格写成当前机械守卫或测试证据
non_goals:
  - 实现身份守卫
  - 执行 T0-RECONCILE-LESSONS
  - 合并源夹具分支
acceptance:
  - "A1 LESSONS 的 ledger_source_ref 与构造命令唯一指向 ff7e5e4fdf1553b1c4d0fe6301b609bef82102c6"
  - "A2 L238 保留身份钉住原则，但 enforced_by 明确为当前无机械守卫"
  - "A3 目标卡不再要求 ExpectHead、Assert-MeasuredTip、R3-HEAD-MISMATCH 或 selftest 15b4"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$c=Get-Content 'specs/tasks/T0-RECONCILE-LESSONS.md' -Raw;$oid='ff7e5e4fdf1553b1c4d0fe6301b609bef82102c6';$needle=$oid+':docs/lessons/LEDGER.md';if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2){throw 'binding'};if($c.Contains('10515e8190038387168b6017a01369fe3fe33242:docs/lessons/LEDGER.md')){throw 'stale'};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};$b=[regex]::Match($s,'(?ms)^## L238\r?\n(?:(?!^## L[0-9]+).)*').Value;if(-not$b){throw 'L238'};if([regex]::Matches($b,'(?m)^- enforced_by: none（设计经验；当前尚无机械守卫）\r?$').Count-ne1){throw 'unenforced'};if([regex]::Matches($b,'(?m)^- refs: docs/lessons/LEDGER\.md; git refs/HEAD identity analysis\r?$').Count-ne1){throw 'refs'};if($b-match'(?i)(ExpectHead|Assert-MeasuredTip|R3-HEAD-MISMATCH|selftest 闸 15b4)'){throw 'pending evidence'};$pat="Block 'L243' @('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','当前尚无机械守卫','git refs/HEAD identity analysis')";if(-not$c.Contains($pat)){throw 'target pattern'};if($c-match'(?i)(ExpectHead|Assert-MeasuredTip|R3-HEAD-MISMATCH|selftest 闸 15b4)'){throw 'target pending evidence'}
dod_exit: 0
dod_assert: A1–A3 双 OID 绑定；L238 明示 unenforced；源与目标均无未合并证据
review_gate: codex {verdict:pass}
hygiene: 只更新来源 OID 与 L243 验收模式
doc_sync: 无
---

# T0-RECONCILE-LESSONS-R3-FIXTURE

PR #148 的 R3 证明原 L238 把尚未合并的任务规格误写成当前守卫。新夹具保留身份一致性原则，但把执行状态改为当前无机械守卫，并移除全部未落地的脚本与闸号声明。
