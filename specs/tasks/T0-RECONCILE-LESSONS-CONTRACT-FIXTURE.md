---
id: T0-RECONCILE-LESSONS-CONTRACT-FIXTURE
title: 收口 lessons 六块精确模式与守卫夹具
depends_on: [T0-RECONCILE-LESSONS-FIXTURE]
status: todo
branch: T0-RECONCILE-LESSONS-CONTRACT-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-CONTRACT-FIXTURE
allow_paths:
  - specs/archive/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或其他调和卡
  - 移除六块 schema、精确构造、排除源或 mutation RED
non_goals:
  - 执行 T0-RECONCILE-LESSONS
  - 把源夹具分支合入 master
acceptance:
  - "A1 LESSONS 的 ledger_source_ref 与构造命令唯一指向 19f51daa1851ae202be71d31f140ec0e20f6461c"
  - "A2 L242/L238/L243/L244/L245/L248 的 rule、enforced_by、refs 与全部语义模式各唯一命中；重复自然词不再误伤"
  - "A3 LESSONS 卡使用锚定 rule 行模式；删除 mutation guard、清空 enforced_by 或改回旧 OID 均 RED"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/archive/tasks/T0-RECONCILE-LESSONS.md';$c=Get-Content $p -Raw;$oid='19f51daa1851ae202be71d31f140ec0e20f6461c';$needle='19f51daa1851ae202be71d31f140ec0e20f6461c:docs/lessons/LEDGER.md';$olds=@('ff7e5e4fdf1553b1c4d0fe6301b609bef82102c6','8d16b11668732faa4d1f78d43870426fb91b5ca5','d141f3d58ba386887aafb17a3893db50fcde814f');function Bound($x){if([regex]::Matches($x,('(?m)^ledger_source_ref: '+[regex]::Escape($needle)+'\r?$')).Count-ne1-or[regex]::Matches($x,[regex]::Escape($needle)).Count-ne2){return $false};foreach($old in $olds){if($x.Contains($old+':docs/lessons/LEDGER.md')){return $false}};$true};if(-not(Bound $c)){throw 'binding'};foreach($old in $olds){if(Bound ($c.Replace($oid,$old))){throw ('stale mutation '+$old)}};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};function B($id){$m=[regex]::Matches($s,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*'));if($m.Count-ne1){throw ('block '+$id)};$m[0].Value};$basePats=@('(?m)^- rule: .+\r?$','(?m)^- enforced_by: .+\r?$','(?m)^- refs: .+\r?$');function Valid($b,$ps){foreach($q in $basePats+$ps){if([regex]::Matches($b,$q).Count-ne1){return $false}};$true};function One($id,$ps){if(-not(Valid (B $id) $ps)){throw ('invalid '+$id)}};$defs=[ordered]@{L242=@('四参静态 \[regex\]::Replace','实例方法 \[regex\]::new','\[ \\t\]','目标行本身变了','scripts/selftest\.ps1 闸 2c','(?m)^- refs: .*mutation.*guard.*\r?$');L238=@('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','Assert-MeasuredTip','R3-HEAD-MISMATCH','selftest 闸 15b4');L243=@('(?m)^- rule: .*merge-base.*\r?$','(?m)^- rule: .*git apply --3way.*\r?$','(?m)^- rule: .*git diff --numstat origin/master.*\r?$','(?m)^- rule: .*抽查基线新内容仍在.*\r?$');L244=@('(?m)^- rule: .*required 的匹配用 assert.*\r?$','(?m)^- rule: .*删除行数 == 预期.*\r?$','(?m)^- rule: .*关键词.*grep.*\r?$','(?m)^- rule: .*命中数必须为 0.*\r?$');L245=@('(?m)^- rule: .*review\.ps1 -WorktreePath.*\r?$','(?m)^- rule: .*-SizeOnly.*\r?$','(?m)^- rule: .*阈值属于谁，就用谁的尺.*\r?$','(?m)^- rule: .*字节、码元、还是字素.*\r?$','(?m)^- rule: .*跨语言/跨工具重算.*\r?$');L248=@('(?m)^- rule: .*archive\.ps1 -CheckCardsIndex.*\r?$','(?m)^- rule: .*cards-index.*投影不是文档.*\r?$','(?m)^- rule: .*doc_sync 写「无」不豁免.*\r?$','(?m)^- rule: .*推 master 前.*verify.*\r?$')};foreach($id in $defs.Keys){One $id $defs[$id]};$orig=B L242;$mut=$orig.Replace('; mutation guard','');if($mut-ceq$orig-or(Valid $mut $defs.L242)){throw 'mutation guard'};$orig=B L248;$blank=[regex]::Replace($orig,$basePats[1],'- enforced_by:');if($blank-ceq$orig-or(Valid $blank $defs.L248)){throw 'empty enforced_by'};$cardPats=$basePats+@($defs.L242+$defs.L238+$defs.L243+$defs.L244+$defs.L245+$defs.L248);foreach($q in $cardPats){if([regex]::Matches($c,[regex]::Escape("'$q'")).Count-ne1){throw ('card pattern '+$q)}};$wires=@("Get-Content 'CLAUDE.md' -Raw","&git show '13f6e809b345c01aa69d4d7090a52f404b96e1df:CLAUDE.md'","&git show '44d3e13a9742fe17fb8df6170d7499fff8835dc0:CLAUDE.md'","Get-Content 'docs/lessons/LEDGER.md' -Raw","&git show '13f6e809b345c01aa69d4d7090a52f404b96e1df:docs/lessons/LEDGER.md'","throw 'CLAUDE'","throw 'ledger scope'","throw 'excluded source'","& scripts/lessons.ps1 check");foreach($w in $wires){if([regex]::Matches($c,[regex]::Escape($w)).Count-ne2){throw ('card wiring '+$w)}};$mapWires=@("LB `$s L242 L242","LB `$s L238 L243","LB `$s L243 L244","LB `$s L244 L245","LB `$s L245 L246","LB `$s L248 L247");foreach($w in $mapWires){if([regex]::Matches($c,[regex]::Escape($w)).Count-ne1){throw ('map wiring '+$w)}}
dod_exit: 0
dod_assert: A1–A3 六块全模式执行；旧 OID、删除 mutation guard、空 enforced_by 与非锚定重复词均 RED
review_gate: codex {verdict:pass}
hygiene: 扫全同类模式；不再逐错补丁
doc_sync: 无
---

# T0-RECONCILE-LESSONS-CONTRACT-FIXTURE

前两次源修复证明仅检查 `refs` 或新增词组不足以证明原 LESSONS 卡可执行。本卡同时补齐 L247 的守卫来源，把会在自然语言中重复的关键词约束到唯一 `rule:` 行，并在自身 DoD 中执行六块全部模式与 stale/mutation RED。
