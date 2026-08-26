---
id: T0-RECONCILE-LESSONS
title: 将本地可复现经验按当前 lessons schema 归并到账本
depends_on: []
parallelizable_with: [T0-RECONCILE-DESIGN-METADATA]
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
  - "A1 L242 regex/mutation target"
  - "A2 L243 branch-tip/HEAD/OID"
  - "A3 L244 replay；L245 removal accounting"
  - "A4 L246 authoritative SizeOnly"
  - "A5 L247 archive index projection"
  - "A6 L242–L247 schema exact；三类瞬时/泛化材料排除"
dod_command: $r=Get-Content 'docs/lessons/LEDGER.md' -Raw;function Block($id,$ps){$b=[regex]::Match($r,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*')).Value;if(-not$b){throw ('missing '+$id)};foreach($p in @('(?m)^- rule: .+','(?m)^- enforced_by: .+','(?m)^- refs: .+')+$ps){$m=[regex]::Match($b,$p);if(-not$m.Success-or[regex]::IsMatch($b.Remove($m.Index,$m.Length),$p)){throw ($id+' missing/non-unique '+$p)}}};$ids=@([regex]::Matches($r,'(?m)^## (L24[2-7])$')|%{$_.Groups[1].Value});if($ids.Count-ne6-or$ids.Count-ne@($ids|sort -Unique).Count-or(Compare-Object ($ids|sort) @('L242','L243','L244','L245','L246','L247'))){throw 'lesson ids'};Block 'L242' @('四参静态 \[regex\]::Replace','实例方法 \[regex\]::new','\[ \\t\]','目标行本身变了','scripts/selftest\.ps1 闸 2c','mutation.*guard');Block 'L243' @('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','Assert-MeasuredTip','R3-HEAD-MISMATCH','selftest 闸 15b4');Block 'L244' @('merge-base','git apply --3way','git diff --numstat origin/master','基线哨兵');Block 'L245' @('required 的匹配用 assert','删除行数 == 预期','关键词.*grep','命中数必须为 0');Block 'L246' @('review\.ps1 -WorktreePath','-SizeOnly','UTF-16 码元','T0-R3-DIFF-BUDGET','wc -c');Block 'L247' @('archive\.ps1 -CheckCardsIndex','cards-index.*投影不是文档','ARCHIVE-CARDS-INDEX-DRIFT','推 master 前.*verify');$new=[regex]::Match($r,'(?ms)^## L242\r?\n.*\z').Value;if($new-match'(?i)(Start-Job|Wait-Job|后台轮询|background poll|未合并.{0,8}PR|unmerged PR)'){throw 'excluded source'}; & scripts/lessons.ps1 check
dod_exit: 0
dod_assert: A1–A6 block/exclusion mutations
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
