---
id: T0-RECONCILE-LESSONS
title: 按当前 schema 归并本地经验
depends_on: []
parallelizable_with: [T0-RECONCILE-DESIGN-METADATA]
status: merged
branch: T0-RECONCILE-LESSONS
worktree: C:\wt\T0-RECONCILE-LESSONS
ledger_source_ref: 19f51daa1851ae202be71d31f140ec0e20f6461c:docs/lessons/LEDGER.md
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
forbid:
  - 覆盖账本、复用 id 或删除上游经验
  - 登记旧未合并提交的瞬时事实
non_goals:
  - 修改 lessons 工具/脚手架/归档
  - 将产品偏好晋升为铁律
acceptance:
  - "A1 L17/L242 exact"
  - "A2 L243 tip/OID"
  - "A3 L244/245 replay"
  - "A4 L246 SizeOnly"
  - "A5 L247 archive index"
  - "A6 schema/exclusions"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/archive/tasks/T0-RECONCILE-LESSONS.md';$c=Get-Content $p -Raw;$oid='19f51daa1851ae202be71d31f140ec0e20f6461c';$needle='19f51daa1851ae202be71d31f140ec0e20f6461c:docs/lessons/LEDGER.md';$olds=@('ff7e5e4fdf1553b1c4d0fe6301b609bef82102c6','8d16b11668732faa4d1f78d43870426fb91b5ca5','d141f3d58ba386887aafb17a3893db50fcde814f');function Bound($x){if([regex]::Matches($x,('(?m)^ledger_source_ref: '+[regex]::Escape($needle)+'\r?$')).Count-ne1-or[regex]::Matches($x,[regex]::Escape($needle)).Count-ne2){return $false};foreach($old in $olds){if($x.Contains($old+':docs/lessons/LEDGER.md')){return $false}};$true};if(-not(Bound $c)){throw 'binding'};foreach($old in $olds){if(Bound ($c.Replace($oid,$old))){throw ('stale mutation '+$old)}};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};function B($id){$m=[regex]::Matches($s,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*'));if($m.Count-ne1){throw ('block '+$id)};$m[0].Value};$basePats=@('(?m)^- rule: .+\r?$','(?m)^- enforced_by: .+\r?$','(?m)^- refs: .+\r?$');function Valid($b,$ps){foreach($q in $basePats+$ps){if([regex]::Matches($b,$q).Count-ne1){return $false}};$true};function One($id,$ps){if(-not(Valid (B $id) $ps)){throw ('invalid '+$id)}};$defs=[ordered]@{L242=@('四参静态 \[regex\]::Replace','实例方法 \[regex\]::new','\[ \\t\]','目标行本身变了','scripts/selftest\.ps1 闸 2c','(?m)^- refs: .*mutation.*guard.*\r?$');L238=@('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','Assert-MeasuredTip','R3-HEAD-MISMATCH','selftest 闸 15b4');L243=@('(?m)^- rule: .*merge-base.*\r?$','(?m)^- rule: .*git apply --3way.*\r?$','(?m)^- rule: .*git diff --numstat origin/master.*\r?$','(?m)^- rule: .*抽查基线新内容仍在.*\r?$');L244=@('(?m)^- rule: .*required 的匹配用 assert.*\r?$','(?m)^- rule: .*删除行数 == 预期.*\r?$','(?m)^- rule: .*关键词.*grep.*\r?$','(?m)^- rule: .*命中数必须为 0.*\r?$');L245=@('(?m)^- rule: .*review\.ps1 -WorktreePath.*\r?$','(?m)^- rule: .*-SizeOnly.*\r?$','(?m)^- rule: .*阈值属于谁，就用谁的尺.*\r?$','(?m)^- rule: .*字节、码元、还是字素.*\r?$','(?m)^- rule: .*跨语言/跨工具重算.*\r?$');L248=@('(?m)^- rule: .*archive\.ps1 -CheckCardsIndex.*\r?$','(?m)^- rule: .*cards-index.*投影不是文档.*\r?$','(?m)^- rule: .*doc_sync 写「无」不豁免.*\r?$','(?m)^- rule: .*推 master 前.*verify.*\r?$')};foreach($id in $defs.Keys){One $id $defs[$id]};$orig=B L242;$mut=$orig.Replace('; mutation guard','');if($mut-ceq$orig-or(Valid $mut $defs.L242)){throw 'mutation guard'};$orig=B L248;$blank=[regex]::Replace($orig,$basePats[1],'- enforced_by:');if($blank-ceq$orig-or(Valid $blank $defs.L248)){throw 'empty enforced_by'};$cardPats=$basePats+@($defs.L242+$defs.L238+$defs.L243+$defs.L244+$defs.L245+$defs.L248);foreach($q in $cardPats){if([regex]::Matches($c,[regex]::Escape("'$q'")).Count-ne1){throw ('card pattern '+$q)}}
dod_exit: 0
dod_assert: L17 only；LEDGER 整文件精确目标；A1–A6 mutations
review_gate: codex {verdict:pass}
hygiene: 权威唯一
doc_sync: R5
---
