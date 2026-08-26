---
id: T0-RECONCILE-LESSONS
title: 按当前 schema 归并本地经验
depends_on: []
parallelizable_with: [T0-RECONCILE-DESIGN-METADATA]
status: todo
branch: T0-RECONCILE-LESSONS
worktree: C:\wt\T0-RECONCILE-LESSONS
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
dod_command: $c=Get-Content 'CLAUDE.md' -Raw;$b=&git show '13f6e809b345c01aa69d4d7090a52f404b96e1df:CLAUDE.md'|Out-String;if($LASTEXITCODE-ne0){throw 'base'};$s=&git show '44d3e13a9742fe17fb8df6170d7499fff8835dc0:CLAUDE.md'|Out-String;if($LASTEXITCODE-ne0){throw 'source'};function L17($x){$m=[regex]::Matches($x,'(?m)^- \*\*\[L17\].*\r?$');if($m.Count-ne1){throw 'L17'};$m[0].Value.TrimEnd("`r")};$e=$b-replace[regex]::Escape((L17 $b)),(L17 $s);function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N $c)-cne(N $e)){throw 'CLAUDE'};$r=Get-Content 'docs/lessons/LEDGER.md' -Raw;function Block($id,$ps){$b=[regex]::Match($r,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*')).Value;if(-not$b){throw ('missing '+$id)};foreach($p in @('(?m)^- rule: .+','(?m)^- enforced_by: .+','(?m)^- refs: .+')+$ps){$m=[regex]::Match($b,$p);if(-not$m.Success-or[regex]::IsMatch($b.Remove($m.Index,$m.Length),$p)){throw ($id+' missing/non-unique '+$p)}}};$ids=@([regex]::Matches($r,'(?m)^## (L24[2-7])$')|%{$_.Groups[1].Value});if($ids.Count-ne6-or$ids.Count-ne@($ids|sort -Unique).Count-or(Compare-Object ($ids|sort) @('L242','L243','L244','L245','L246','L247'))){throw 'lesson ids'};Block 'L242' @('四参静态 \[regex\]::Replace','实例方法 \[regex\]::new','\[ \\t\]','目标行本身变了','scripts/selftest\.ps1 闸 2c','mutation.*guard');Block 'L243' @('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','Assert-MeasuredTip','R3-HEAD-MISMATCH','selftest 闸 15b4');Block 'L244' @('merge-base','git apply --3way','git diff --numstat origin/master','基线哨兵');Block 'L245' @('required 的匹配用 assert','删除行数 == 预期','关键词.*grep','命中数必须为 0');Block 'L246' @('review\.ps1 -WorktreePath','-SizeOnly','UTF-16 码元','T0-R3-DIFF-BUDGET','wc -c');Block 'L247' @('archive\.ps1 -CheckCardsIndex','cards-index.*投影不是文档','ARCHIVE-CARDS-INDEX-DRIFT','推 master 前.*verify');$new=[regex]::Match($r,'(?ms)^## L242\r?\n.*\z').Value;if($new-match'(?i)(Start-Job|Wait-Job|后台轮询|background poll|未合并.{0,8}PR|unmerged PR)'){throw 'excluded source'}; & scripts/lessons.ps1 check
dod_exit: 0
dod_assert: L17 only；A1–A6 mutations
review_gate: codex {verdict:pass}
hygiene: 权威唯一
doc_sync: R5
---
