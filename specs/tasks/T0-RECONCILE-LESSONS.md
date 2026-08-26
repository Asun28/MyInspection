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
  - "A1 L242 block-local：静态 Regex.Replace 四参重载与跨行 whitespace 变异失靶，规则要求实例 Replace/count、[ tab] 行空白和变异后目标行确实改变；enforced_by 点名 selftest 2c/真实 mutation guard"
  - "A2 L243 block-local：consumer 读 HEAD 时必须同时钉 branch tip 与 HEAD，并显式传 OID；证据点名 Assert-MeasuredTip、R3-HEAD-MISMATCH 与 selftest 15b4"
  - "A3 L244/L245 block-local：旧分支成果按 merge-base patch + git apply --3way 重放；脚本剥离每个 required target 必须 assert 命中并做删除量/关键词残留对账"
  - "A4 L246 block-local：与 R3 阈值比较只使用 review.ps1 -SizeOnly 的 UTF-16 码元尺，证据指向 T0-R3-DIFF-BUDGET，不用 wc -c 做拆卡决策"
  - "A5 L247 block-local：归档搬运后必须跑 archive.ps1 -CheckCardsIndex，明确 cards-index 是机检投影非 doc_sync 文档，并保留 ARCHIVE-CARDS-INDEX-DRIFT 证据"
  - "A6 排除与 schema：不迁移后台轮询习惯、PR 未合并参数说明、无 refs 的泛化措辞；L242–L247 id 唯一且每块自身含 rule/enforced_by/refs，lessons check 全绿"
dod_command: $r=Get-Content 'docs/lessons/LEDGER.md' -Raw;function Block($id,$ps){$b=[regex]::Match($r,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*')).Value;if(-not$b){throw ('missing '+$id)};foreach($p in @('(?m)^- rule: .+','(?m)^- enforced_by: .+','(?m)^- refs: .+')+$ps){$m=[regex]::Match($b,$p);if(-not$m.Success-or[regex]::IsMatch($b.Remove($m.Index,$m.Length),$p)){throw ($id+' missing/non-unique '+$p)}}};$ids=@([regex]::Matches($r,'(?m)^## (L24[2-7])$')|%{$_.Groups[1].Value});if($ids.Count-ne6-or$ids.Count-ne@($ids|sort -Unique).Count-or(Compare-Object ($ids|sort) @('L242','L243','L244','L245','L246','L247'))){throw 'lesson ids'};Block 'L242' @('四参静态 \[regex\]::Replace','实例方法 \[regex\]::new','\[ \\t\]','目标行本身变了','scripts/selftest\.ps1 闸 2c','mutation.*guard');Block 'L243' @('消费者读的是 HEAD','分支引用与 HEAD 双钉','显式参数传给被调方','Assert-MeasuredTip','R3-HEAD-MISMATCH','selftest 闸 15b4');Block 'L244' @('merge-base','git apply --3way','git diff --numstat origin/master','基线哨兵');Block 'L245' @('required 的匹配用 assert','删除行数 == 预期','关键词.*grep','命中数必须为 0');Block 'L246' @('review\.ps1 -WorktreePath','-SizeOnly','UTF-16 码元','T0-R3-DIFF-BUDGET','wc -c');Block 'L247' @('archive\.ps1 -CheckCardsIndex','cards-index.*投影不是文档','ARCHIVE-CARDS-INDEX-DRIFT','推 master 前.*verify'); & scripts/lessons.ps1 check
dod_exit: 0
dod_assert: A1–A6 的 exact sets、逐块语义和删除变异全绿；删改任一要求即 RED
review_gate: codex {verdict:pass}
hygiene: 使用 lessons.ps1 add/check 规范化，不手工复制旧账本块；相同根因合并为复发计数或现有规则增量
doc_sync: CLAUDE.md 只接晋升后的最小规则增量（R5）
---
