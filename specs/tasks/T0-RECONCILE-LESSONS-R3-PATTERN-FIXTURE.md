---
id: T0-RECONCILE-LESSONS-R3-PATTERN-FIXTURE
title: 对齐 lessons 身份原则的 R3 验收词组
depends_on: [T0-RECONCILE-LESSONS-R3-FIXTURE]
status: todo
branch: T0-RECONCILE-LESSONS-R3-PATTERN-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-R3-PATTERN-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或来源 OID
  - 恢复任何未合并守卫、脚本或闸号声明
non_goals:
  - 实现身份守卫
  - 执行 T0-RECONCILE-LESSONS
acceptance:
  - "A1 L243 验收使用规则正文真实存在的两者都被消费就两者都钉"
  - "A2 删除只存在于旧 enforced_by 的分支引用与 HEAD 双钉词组"
  - "A3 ff7e5e4 来源双绑定与当前无机械守卫断言保持不变"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$c=Get-Content 'specs/tasks/T0-RECONCILE-LESSONS.md' -Raw;if([regex]::Matches($c,'两者都被消费就两者都钉').Count-ne1){throw 'new pattern'};if($c.Contains('分支引用与 HEAD 双钉')){throw 'stale pattern'};if([regex]::Matches($c,'ff7e5e4fdf1553b1c4d0fe6301b609bef82102c6:docs/lessons/LEDGER\.md').Count-ne2){throw 'binding'};foreach($q in @('显式参数传给被调方','当前尚无机械守卫','git refs/HEAD identity analysis')){if([regex]::Matches($c,[regex]::Escape($q)).Count-ne1){throw ('contract '+$q)}};if($c-match'(?i)(ExpectHead|Assert-MeasuredTip|R3-HEAD-MISMATCH|selftest 闸 15b4)'){throw 'pending evidence'}
dod_exit: 0
dod_assert: A1–A3 新词组唯一；旧词组为零；来源与 unenforced 契约不变
review_gate: codex {verdict:pass}
hygiene: 只替换 L243 的一个验收词组
doc_sync: 无
---

# T0-RECONCILE-LESSONS-R3-PATTERN-FIXTURE

R3 修复把旧 `enforced_by` 正确移除后，原验收词组也随之消失。规则正文仍明确表达相同原则，本卡只让验收匹配正文真实措辞。
