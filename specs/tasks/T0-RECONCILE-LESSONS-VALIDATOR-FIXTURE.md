---
id: T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE
title: 收口 lessons 夹具的 enforced_by 校验兼容性
depends_on: [T0-RECONCILE-LESSONS-FINAL-FIXTURE]
status: todo
branch: T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或其他调和卡
  - 弱化目标卡的精确构造、字段 schema、语义模式或 lessons check
non_goals:
  - 执行 T0-RECONCILE-LESSONS
  - 合并源夹具分支
acceptance:
  - "A1 LESSONS 的 ledger_source_ref 与构造命令唯一指向 10515e8190038387168b6017a01369fe3fe33242"
  - "A2 L243/L244 源块的 enforced_by 均为校验器接受的完整 none（理由）形态"
  - "A3 e3db807 旧来源不再出现在目标卡中，原有六块与 lessons check 契约保持不变"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/tasks/T0-RECONCILE-LESSONS.md';$c=Get-Content $p -Raw;$oid='10515e8190038387168b6017a01369fe3fe33242';$needle=$oid+':docs/lessons/LEDGER.md';if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2){throw 'binding'};if($c.Contains('e3db807d9ee765c8bde8e0035b0e0993cbed9d03:docs/lessons/LEDGER.md')){throw 'stale'};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};function B($id){$m=[regex]::Matches($s,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*'));if($m.Count-ne1){throw ('block '+$id)};$m[0].Value};$a=[regex]::Matches((B L243),'(?m)^- enforced_by: none（[^\r\n]+）\r?$');$b=[regex]::Matches((B L244),'(?m)^- enforced_by: none（[^\r\n]+）\r?$');if($a.Count-ne1-or$b.Count-ne1){throw 'enforced_by shape'};if(-not$c.Contains('ledger_source_ref: '+$needle)){throw 'target ledger ref'};if(-not$c.Contains('&git show '''+$needle+'''')){throw 'target git show'};if(-not$c.Contains('& scripts/lessons.ps1 check')){throw 'target lessons check'};if(-not$c.Contains('Block ''L242''')-or-not$c.Contains('Block ''L247''')){throw 'target blocks'}
dod_exit: 0
dod_assert: A1–A3 双 OID 绑定；两条 enforced_by 形态；原有六块与 lessons check 保持
review_gate: codex {verdict:pass}
hygiene: 只改目标卡的夹具 OID
doc_sync: 无
---

# T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE

目标 LESSONS 的真实 DoD 已证明原夹具两条 `enforced_by` 在右括号后残留说明文字，违反当前校验器的 `none（理由）` 形态。新夹具仅把说明文字收进括号，不改变经验语义或六块映射。
