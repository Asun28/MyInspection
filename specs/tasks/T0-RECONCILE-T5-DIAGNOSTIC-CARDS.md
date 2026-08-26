---
id: T0-RECONCILE-T5-DIAGNOSTIC-CARDS
title: 登记本机事件、诊断导出、数据清除与发布健康四张实现卡
depends_on: [T0-RECONCILE-T1-SECURITY-CARDS]
status: todo
branch: T0-RECONCILE-T5-DIAGNOSTIC-CARDS
worktree: C:\wt\T0-RECONCILE-T5-DIAGNOSTIC-CARDS
allow_paths:
  - specs/tasks/T5-OPERATION-EVENT-STORE.md
  - specs/tasks/T5-DIAGNOSTIC-EXPORT.md
  - specs/tasks/T5-LOCAL-DATA-ERASURE.md
  - specs/tasks/T7-LOCAL-HEALTH-RELEASE.md
forbid:
  - 修改产品代码或建立远程 admin、遥测、自动上传
  - 把诊断包当备份、把事件库放进证据哈希域或主备份
non_goals:
  - 实现四张卡或同步 Task Board/技术债
  - 修改 T1 安全卡
acceptance:
  - "A1 EVENT-STORE：migration+security 前置；4 paths；version review；独立 DB、typed allowlist、2KiB、90天/20000行、失败隔离、飞行模式"
  - "A2 DIAGNOSTIC-EXPORT：event+privacy 前置；5 paths；7/30/90天、manifest/hash、SAF/content URI、取消清理、禁记字段、admin 只读"
  - "A3 LOCAL-DATA-ERASURE：backup+安全前置；4 paths；完整类别、外部 mibk 保留、ERASE、失败不伪成功、first-run"
  - "A4 LOCAL-HEALTH：event/export/backup/security 前置；4 paths；六 health states、1秒、脱敏 crash、mapping/反混淆/NOT_MINIFIED"
  - "A5 四卡 paths 不重叠；每卡均禁止遥测/自动上传/远程 admin/诊断写 finalized evidence/未经 version review 改冻结合同"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};function Paths($p){$r=Get-Content $p -Raw;@([regex]::Matches([regex]::Match($r,'(?ms)^allow_paths:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value,'(?m)^  - ([^\r\n]+)$')|%{$_.Groups[1].Value})};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$ep='specs/tasks/T5-OPERATION-EVENT-STORE.md';$xp='specs/tasks/T5-DIAGNOSTIC-EXPORT.md';$rp='specs/tasks/T5-LOCAL-DATA-ERASURE.md';$hp='specs/tasks/T7-LOCAL-HEALTH-RELEASE.md';$e=Get-Content $ep -Raw;$x=Get-Content $xp -Raw;$r=Get-Content $rp -Raw;$h=Get-Content $hp -Raw;Exact (Paths $ep) @('android/core/build.gradle.kts','android/core/src/main/sqldelight/nz/myinspection/core/diagnostics/','android/core/src/main/kotlin/nz/myinspection/core/diagnostics/store/','android/core/src/test/kotlin/nz/myinspection/core/diagnostics/store/');Exact (Paths $xp) @('android/core/src/main/kotlin/nz/myinspection/core/diagnostics/export/','android/core/src/test/kotlin/nz/myinspection/core/diagnostics/export/','android/app/src/main/kotlin/nz/myinspection/app/feature/diagnostics/','android/app/src/test/kotlin/nz/myinspection/app/feature/diagnostics/','android/app/src/androidTest/kotlin/nz/myinspection/app/feature/diagnostics/');Exact (Paths $rp) @('android/core/src/main/kotlin/nz/myinspection/core/privacy/erasure/','android/core/src/test/kotlin/nz/myinspection/core/privacy/erasure/','android/app/src/main/kotlin/nz/myinspection/app/feature/settings/erasure/','android/app/src/test/kotlin/nz/myinspection/app/feature/settings/erasure/');Exact (Paths $hp) @('android/app/build.gradle.kts','android/app/src/main/kotlin/nz/myinspection/app/health/','android/app/src/test/kotlin/nz/myinspection/app/health/','docs/RELEASE-CHECKLIST.md');$all=@((Paths $ep)+(Paths $xp)+(Paths $rp)+(Paths $hp));for($i=0;$i-lt$all.Count;$i++){for($j=$i+1;$j-lt$all.Count;$j++){$a=$all[$i].TrimEnd('/');$b=$all[$j].TrimEnd('/');if($a-eq$b-or$a.StartsWith($b+'/')-or$b.StartsWith($a+'/')){throw 'overlap'}}};Must $e @('(?m)^depends_on: \[T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T1-LOCAL-DATA-SECURITY\]$','(?m)^version_review: this card = the version review$','diagnostics schema version','\.sqm','schema snapshot','verifyMigrations','(?m)^dod_assert: .*diagnostic_run/operation_event.*2KiB.*90 天.*20000.*业务调用结果不变.*飞行模式全绿','通用自由文本 logger');Must $x @('(?m)^depends_on: \[T5-OPERATION-EVENT-STORE, T1-SHARE-SCREEN-PRIVACY\]$','(?m)^dod_assert: .*7 天.*90 天.*manifest.*SAF.*content://.*取消/失败.*admin/support 无写入口','后台自动上传','诊断模式修改 finalized 证据','7/30/90 天','SHA-256','禁地址、姓名、联系方式、备注/转写','恶意夹具验证成品零命中');Must $r @('(?m)^depends_on: \[T5-BACKUP-IO, T1-LOCAL-DATA-SECURITY, T1-SHARE-SCREEN-PRIVACY\]$','(?m)^dod_assert: .*主/诊断 DB.*外部 `.mibk` 保留.*`ERASE`.*不得显示成功.*first-run','清除 app-owned 主/诊断 DB','任一类别未确认清除不得显示成功');Must $h @('(?m)^depends_on: \[T5-OPERATION-EVENT-STORE, T5-DIAGNOSTIC-EXPORT, T5-BACKUP-IO, T1-LOCAL-DATA-SECURITY\]$','(?m)^dod_assert: .*BACKUP_STALE_7D.*STARTUP_SLOW.*≤1s.*mapping SHA-256.*NOT_MINIFIED','诊断/健康写入失败改变','mapping/符号表入 APK','不存 message/业务内容','固定 obfuscated fixture 本地反混淆');$hd=[regex]::Match($h,'(?m)^health_states: \[(.*)\]$').Groups[1].Value;Exact @($hd.Split(',')|%{$_.Trim()}) @('BACKUP_STALE_7D','BACKUP_FAILED_3X','INTEGRITY_FAILED','RESTORE_ROLLED_BACK','PREVIOUS_CRASH','STARTUP_SLOW');foreach($c in @($e,$x,$r,$h)){Must $c @('遥测/自动上传','远程 admin','诊断/健康不得写 finalized evidence','未经本卡 version review 改冻结 schema/backup format')}
dod_exit: 0
dod_assert: exact paths/semantics/states/shared forbids 全绿
review_gate: codex {verdict:pass}
hygiene: 事件、导出、清除、健康各有单一 owner
doc_sync: 引用数据库/安全权威与备份卡（R5）
---
