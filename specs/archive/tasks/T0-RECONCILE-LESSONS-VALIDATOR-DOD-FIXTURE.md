---
id: T0-RECONCILE-LESSONS-VALIDATOR-DOD-FIXTURE
title: 修正 lessons validator 修复卡的 PowerShell 检查列表
depends_on: [T0-RECONCILE-LESSONS-FINAL-FIXTURE]
status: merged
branch: T0-RECONCILE-LESSONS-VALIDATOR-DOD-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-VALIDATOR-DOD-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或目标 LESSONS 卡
  - 弱化 OID 双绑定、stale 排除或 enforced_by 形态检查
non_goals:
  - 执行 validator 修复卡或 T0-RECONCILE-LESSONS
  - 合并源夹具分支
acceptance:
  - "A1 删除会把五项拼成单字符串的 foreach 检查"
  - "A2 用四个独立、可定位失败原因的 Contains 断言覆盖来源、git show、lessons check 与首尾块"
  - "A3 其余 OID、源块与形态契约保持不变"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$c=Get-Content 'specs/tasks/T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE.md' -Raw;if($c.Contains('foreach($q in @(')){throw 'stale joined list'};foreach($label in @('target ledger ref','target git show','target lessons check','target blocks')){if([regex]::Matches($c,[regex]::Escape("throw '$label'")).Count-ne1){throw ('missing '+$label)}};foreach($q in @('10515e8190038387168b6017a01369fe3fe33242','e3db807d9ee765c8bde8e0035b0e0993cbed9d03','enforced_by shape')){if(-not$c.Contains($q)){throw ('contract '+$q)}}
dod_exit: 0
dod_assert: A1–A3 无 joined-list；四个独立断言；OID 与形态契约仍在
review_gate: codex {verdict:pass}
hygiene: 只修一条 dod_command 的 PowerShell 解析缺陷
doc_sync: 无
---

# T0-RECONCILE-LESSONS-VALIDATOR-DOD-FIXTURE

原命令在数组字面量里直接拼接字符串，PowerShell 将五项合成一个值，既无法逐项定位，也与 OID 计数约束互相矛盾。本卡仅改为四个独立断言。
