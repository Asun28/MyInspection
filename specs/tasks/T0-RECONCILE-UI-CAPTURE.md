---
id: T0-RECONCILE-UI-CAPTURE
title: 对齐采集、历史与 PDF 实现卡的设计系统指针
depends_on: [T0-RECONCILE-UI-COVERAGE]
status: todo
branch: T0-RECONCILE-UI-CAPTURE
worktree: C:\wt\T0-RECONCILE-UI-CAPTURE
allow_paths:
  - specs/tasks/T2-CAPTURE-UI.md
  - specs/tasks/T3-FIELD-UX-ACCEPTANCE.md
  - specs/tasks/T3-HISTORY-COMPARE.md
  - specs/tasks/T3-PDF-RENDERER.md
forbid:
  - 复制 DESIGN.md 的完整规格或修改产品代码
  - 编辑已归档的 theme/photo-property-dedupe 卡
non_goals:
  - 通知、日程、备份、清除、remediation 与 smoke 卡同步
  - 实现 Compose/PDF/相机功能
acceptance:
  - "A1 T2-CAPTURE-UI：plan_ref 主旅程且恰有 A1–A5，覆盖 21-page ownership 中的 setup/capture/review/camera、resume/save/focus、evidence states、permission/offline fallback、48dp/200%/TalkBack/performance"
  - "A2 T3-FIELD-UX-ACCEPTANCE：plan_ref accessibility 且恰有 A1–A5，具名设备/构建、日光单手、TalkBack/200%、Reduce Motion、process death、offline/provider、P0/P1 debt closure"
  - "A3 T3-HISTORY-COMPARE：plan_ref history matrix 且恰有 A1–A4，覆盖 previous/baseline/empty/archived、visible controls、overlay preview-only、focus return 与离线读"
  - "A4 T3-PDF-RENDERER：plan_ref backup/report matrix 且恰有 A1–A4，覆盖质量/进度/验证回执、Open/Share/Export actions、temporary content URI、CJK/内存/离线与失败恢复"
  - "A5 去重与范围：四卡不出现 DESIGN 的 component-matrix 章节或第二套 token；只增 plan_ref/acceptance/最小 dependency，不修改已归档 theme/photo dedupe 卡或产品代码"
dod_command: function ExactIds($r,$e){$q=[regex]::Match($r,'(?ms)^acceptance:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value;$a=@([regex]::Matches($q,'(?m)^  - \"?([^ \"\r\n]+) ')|%{$_.Groups[1].Value});if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'acceptance ids'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$c=Get-Content 'specs/tasks/T2-CAPTURE-UI.md' -Raw;$f=Get-Content 'specs/tasks/T3-FIELD-UX-ACCEPTANCE.md' -Raw;$h=Get-Content 'specs/tasks/T3-HISTORY-COMPARE.md' -Raw;$p=Get-Content 'specs/tasks/T3-PDF-RENDERER.md' -Raw;ExactIds $c @('A1','A2','A3','A4','A5');ExactIds $f @('A1','A2','A3','A4','A5');ExactIds $h @('A1','A2','A3','A4');ExactIds $p @('A1','A2','A3','A4');Must $c @('(?m)^plan_ref: context/DESIGN\.md#primary-inspection-journey$','(?m)^  - \"A1 (?=[^\"\r\n]*setup)(?=[^\"\r\n]*capture)(?=[^\"\r\n]*review)(?=[^\"\r\n]*camera)[^\"\r\n]+\"$','(?m)^  - \"A2 (?=[^\"\r\n]*resume)(?=[^\"\r\n]*save)(?=[^\"\r\n]*focus)(?=[^\"\r\n]*evidence)[^\"\r\n]+\"$','(?m)^  - \"A3 (?=[^\"\r\n]*permission)(?=[^\"\r\n]*offline)(?=[^\"\r\n]*fallback)[^\"\r\n]+\"$','(?m)^  - \"A4 (?=[^\"\r\n]*48dp)(?=[^\"\r\n]*200%)(?=[^\"\r\n]*TalkBack)[^\"\r\n]+\"$','(?m)^  - \"A5 (?=[^\"\r\n]*main-thread)(?=[^\"\r\n]*LRU)(?=[^\"\r\n]*performance)[^\"\r\n]+\"$');Must $f @('(?m)^plan_ref: context/DESIGN\.md#accessibility-contract$','(?m)^  - \"A1 (?=[^\"\r\n]*device)(?=[^\"\r\n]*build)[^\"\r\n]+\"$','(?m)^  - \"A2 (?=[^\"\r\n]*daylight)(?=[^\"\r\n]*one-hand)[^\"\r\n]+\"$','(?m)^  - \"A3 (?=[^\"\r\n]*TalkBack)(?=[^\"\r\n]*200%)(?=[^\"\r\n]*Reduce Motion)[^\"\r\n]+\"$','(?m)^  - \"A4 (?=[^\"\r\n]*process death)(?=[^\"\r\n]*offline)(?=[^\"\r\n]*provider)[^\"\r\n]+\"$','(?m)^  - \"A5 (?=[^\"\r\n]*P0/P1)(?=[^\"\r\n]*closure)[^\"\r\n]+\"$');Must $h @('(?m)^plan_ref: context/DESIGN\.md#history-evidence-and-media-component-matrix$','(?m)^  - \"A1 (?=[^\"\r\n]*previous)(?=[^\"\r\n]*baseline)(?=[^\"\r\n]*empty)(?=[^\"\r\n]*archived)[^\"\r\n]+\"$','(?m)^  - \"A2 [^\"\r\n]*visible controls[^\"\r\n]+\"$','(?m)^  - \"A3 (?=[^\"\r\n]*preview-only)(?=[^\"\r\n]*focus return)[^\"\r\n]+\"$','(?m)^  - \"A4 [^\"\r\n]*offline read[^\"\r\n]+\"$');Must $p @('(?m)^plan_ref: context/DESIGN\.md#backup-report-health-and-compliance-component-matrix$','(?m)^  - \"A1 (?=[^\"\r\n]*quality)(?=[^\"\r\n]*progress)(?=[^\"\r\n]*verified receipt)[^\"\r\n]+\"$','(?m)^  - \"A2 (?=[^\"\r\n]*Open PDF)(?=[^\"\r\n]*Share)(?=[^\"\r\n]*Export another quality)[^\"\r\n]+\"$','(?m)^  - \"A3 [^\"\r\n]*temporary content URI[^\"\r\n]+\"$','(?m)^  - \"A4 (?=[^\"\r\n]*CJK)(?=[^\"\r\n]*memory)(?=[^\"\r\n]*offline)(?=[^\"\r\n]*failure recovery)[^\"\r\n]+\"$');if(($c+$f+$h+$p)-match'(?m)^### .*component matrix$'){throw 'copied matrix'}
dod_exit: 0
dod_assert: A1–A5 的 exact sets、逐块语义和删除变异全绿；删改任一要求即 RED
review_gate: codex {verdict:pass}
hygiene: 每条新增验收都能指向 DESIGN.md 或 UI-UX-ELEMENTS 的唯一条目
doc_sync: 四张卡与 UI 覆盖索引一致（R5）
---

# T0-RECONCILE-UI-CAPTURE

## 产出

把采集主流程、现场验收、历史比较和 PDF 出口四张实现卡对齐到 Field Ledger 页面与组件合同，删除本地重复的长篇设计说明。

## 验收

执行 front matter 的 `dod_command`，并确认净 diff 保持评审预算内。
