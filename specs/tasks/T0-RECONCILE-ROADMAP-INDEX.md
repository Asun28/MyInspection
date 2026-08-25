---
id: T0-RECONCILE-ROADMAP-INDEX
title: 将离线安全与诊断卡投影到任务表和技术债索引
depends_on: [T0-RECONCILE-T1-SECURITY-CARDS, T0-RECONCILE-T5-DIAGNOSTIC-CARDS]
status: todo
branch: T0-RECONCILE-ROADMAP-INDEX
worktree: C:\wt\T0-RECONCILE-ROADMAP-INDEX
allow_paths:
  - docs/TASK-BOARD.md
  - specs/tech-debt-tracker.md
forbid:
  - 修改产品代码或把 todo 设计描述为已实现
  - 复活已归档旧卡、TD24，或收窄 ADR-0002 的按物业备份范围
non_goals:
  - 创建或实现七张新卡
  - 修改 UI 设计真相源或 lessons 账本
acceptance:
  - "A1 Task Board 恰有 T1 local-security/share W1、database-lifecycle W2、T5 event/export/erasure W5、T7 local-health W7 七行，标题/depends_on 与卡一致"
  - "A2 TD160 同行含 active/history、write-authority、后果/修法/可测、TD4、major/carded 与 database-lifecycle 偿还指针"
  - "A3 TD161 同行含 typed diagnostics/export、PII/失败隔离、90天/20000行、飞行模式、T1 前置、major/carded 与 event→export closure"
  - "A4 TD12 保留按物业包携带整库/manifest scope 的既有风险与 format v2/回读 DB 修法；TD24 保持 archived paid，不重加 active tracker"
  - "A5 check-cards 解析七卡依赖；Task Board 只投影事实，不复制规格或把 todo 写成 merged"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$b=Get-Content 'docs/TASK-BOARD.md' -Raw;$t=Get-Content 'specs/tech-debt-tracker.md' -Raw;function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$waves=@{'T1-LOCAL-DATA-SECURITY'='W1';'T1-SHARE-SCREEN-PRIVACY'='W1';'T1-DATABASE-LIFECYCLE-AUTHORITY'='W2';'T5-OPERATION-EVENT-STORE'='W5';'T5-DIAGNOSTIC-EXPORT'='W5';'T5-LOCAL-DATA-ERASURE'='W5';'T7-LOCAL-HEALTH-RELEASE'='W7'};$ids=@([regex]::Matches($b,'(?m)^\| W[1257] \| (T1-LOCAL-DATA-SECURITY|T1-SHARE-SCREEN-PRIVACY|T1-DATABASE-LIFECYCLE-AUTHORITY|T5-OPERATION-EVENT-STORE|T5-DIAGNOSTIC-EXPORT|T5-LOCAL-DATA-ERASURE|T7-LOCAL-HEALTH-RELEASE) \|')|%{$_.Groups[1].Value});if($ids.Count-ne7-or$ids.Count-ne@($ids|sort -Unique).Count-or(Compare-Object ($ids|sort) ($waves.Keys|sort))){throw 'board ids'};foreach($id in $waves.Keys){$c=Get-Content ('specs/tasks/'+$id+'.md') -Raw;$title=[regex]::Match($c,'(?m)^title: (.+)$').Groups[1].Value;$deps=[regex]::Match($c,'(?m)^depends_on: \[(.*)\]$').Groups[1].Value;$row=[regex]::Match($b,('(?m)^\| '+$waves[$id]+' \| '+$id+' \|([^\r\n]+)$')).Value;$x=@($row.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne8-or$x[2]-cne$title-or$x[3]-cne$deps){throw ('board projection '+$id)}};Must $t @('(?m)^\| TD160 \|.*active 与 historical.*写权限.*后果.*修法.*可测.*TD4.*major \| carded \| `specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY\.md` \|$','(?m)^\| TD161 \|.*typed.*PII.*失败隔离.*90 天/20,000 行.*飞行模式.*T1-LOCAL-DATA-SECURITY.*major \| carded \| `T5-OPERATION-EVENT-STORE` → `T5-DIAGNOSTIC-EXPORT`.*\|$','(?m)^\| TD12 \|.*按物业导出仍带整库.*manifest.*format_version 2.*回读包内 DB.*major \| open \|');if($t-match'(?m)^\| TD24 \|'){throw 'revived TD24'}
dod_exit: 0
dod_assert: 七卡 projection、TD160/161、既有 TD12 与 TD24 archived 状态精确保持
review_gate: codex {verdict:pass}
hygiene: 任务表只投影卡片事实，不复制完整实现规格
doc_sync: Task Board、tech-debt tracker 与七张卡互相引用一致（R5）
---

# T0-RECONCILE-ROADMAP-INDEX

## 产出

把前两张卡已经登记的七个未来实现单元投影到当前任务图，并保留现有备份/媒体生命周期决策。完整规格继续以任务卡为准。

## 验收

执行 front matter 的 `dod_command`，随后运行 `scripts/check-cards.ps1` 与 `scripts/verify.ps1`。
