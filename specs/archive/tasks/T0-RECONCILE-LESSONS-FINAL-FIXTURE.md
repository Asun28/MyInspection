---
id: T0-RECONCILE-LESSONS-FINAL-FIXTURE
title: 最终收口 lessons 可执行源与唯一模式
depends_on: [T0-RECONCILE-LESSONS-FIXTURE]
status: merged
branch: T0-RECONCILE-LESSONS-FINAL-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-FINAL-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或其他调和卡
  - 移除六块 schema、精确构造、排除源或 mutation RED
non_goals:
  - 执行 T0-RECONCILE-LESSONS
  - 合并源夹具分支
acceptance:
  - "A1 LESSONS 的 ledger_source_ref 与构造命令唯一指向 e3db807d9ee765c8bde8e0035b0e0993cbed9d03"
  - "A2 六个映射源块的 rule/enforced_by/refs 与全部语义模式各唯一命中"
  - "A3 LESSONS 使用锚定字段模式；删除 mutation guard、清空 enforced_by 或改回任一旧 OID 均 RED"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/tasks/T0-RECONCILE-LESSONS.md';$c=Get-Content $p -Raw;$oid='e3db807d9ee765c8bde8e0035b0e0993cbed9d03';$needle=$oid+':docs/lessons/LEDGER.md';$stale=@('d141f3d58ba386887aafb17a3893db50fcde814f','8d16b11668732faa4d1f78d43870426fb91b5ca5','19f51daa1851ae202be71d31f140ec0e20f6461c');function Bound($x){$ok=[regex]::Matches($x,('(?m)^ledger_source_ref: '+[regex]::Escape($needle)+'\r?$')).Count-eq1-and[regex]::Matches($x,[regex]::Escape($needle)).Count-eq2;foreach($o in $stale){$ok=$ok-and-not$x.Contains($o+':docs/lessons/LEDGER.md')};$ok};if(-not(Bound $c)){throw 'binding'};if(Bound ($c.Replace($oid,$stale[2]))){throw 'stale mutation'};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};function B($id){$m=[regex]::Matches($s,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*'));if($m.Count-ne1){throw ('block '+$id)};$m[0].Value};function One($id,$ps){$b=B $id;foreach($q in @('(?m)^- rule: .+\r?$','(?m)^- enforced_by: .+\r?$','(?m)^- refs: .+\r?$')+$ps){if([regex]::Matches($b,$q).Count-ne1){throw ($id+' '+$q)}}};$defs=[ordered]@{L242=@('四参静态 \[regex\]::Replace','实例方法 \[regex\]::new','\[ \\t\]','目标行本身变了','scripts/selftest\.ps1 闸 2c','(?m)^- refs: .*mutation.*guard.*\r?$');L238=@('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','Assert-MeasuredTip','R3-HEAD-MISMATCH','selftest 闸 15b4');L243=@('(?m)^- rule: .*merge-base.*\r?$','(?m)^- rule: .*git apply --3way.*\r?$','(?m)^- rule: .*git diff --numstat origin/master.*\r?$','(?m)^- rule: .*基线哨兵.*\r?$');L244=@('(?m)^- rule: .*required 的匹配用 assert.*\r?$','(?m)^- rule: .*删除行数 == 预期.*\r?$','(?m)^- rule: .*关键词.*grep.*\r?$','(?m)^- rule: .*命中数必须为 0.*\r?$');L245=@('(?m)^- rule: .*review\.ps1 -WorktreePath.*\r?$','(?m)^- rule: .*-SizeOnly.*\r?$','(?m)^- rule: .*UTF-16 码元.*\r?$','(?m)^- rule: .*T0-R3-DIFF-BUDGET.*\r?$','(?m)^- rule: .*wc -c.*\r?$');L248=@('(?m)^- rule: .*archive\.ps1 -CheckCardsIndex.*\r?$','(?m)^- rule: .*cards-index.*投影不是文档.*\r?$','(?m)^- rule: .*ARCHIVE-CARDS-INDEX-DRIFT.*\r?$','(?m)^- rule: .*推 master 前.*verify.*\r?$')};foreach($id in $defs.Keys){One $id $defs[$id]};$mut=(B L242).Replace('; mutation guard','');if([regex]::IsMatch($mut,'mutation.*guard')){throw 'mutation survived'};$cardPats=@($defs.L243+$defs.L244+$defs.L245+$defs.L248);foreach($q in $cardPats){if(-not$c.Contains("'$q'")){throw ('card pattern '+$q)}}
dod_exit: 0
dod_assert: A1–A3 六块全模式执行；任一旧 OID、删除 mutation guard、空 enforced_by 与非锚定重复词均 RED
review_gate: codex {verdict:pass}
hygiene: 六块同类一次扫全
doc_sync: 无
---

# T0-RECONCILE-LESSONS-FINAL-FIXTURE

真实 DoD 与 R3 已完整列出原夹具的空字段、重复自然词与字段归属问题。子夹具 `e3db807` 只补齐这些可执行契约；本卡自身运行与目标 LESSONS 相同的六块模式，并验证 stale 与 mutation RED。
