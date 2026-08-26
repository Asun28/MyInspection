---
id: T0-RECONCILE-T1-SECURITY-CARDS
title: 登记数据库生命周期与 Android 本地安全三张实现卡
depends_on: [T0-RECONCILE-DATA-AUTHORITY]
status: todo
branch: T0-RECONCILE-T1-SECURITY-CARDS
worktree: C:\wt\T0-RECONCILE-T1-SECURITY-CARDS
allow_paths:
  - specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY.md
  - specs/tasks/T1-LOCAL-DATA-SECURITY.md
  - specs/tasks/T1-SHARE-SCREEN-PRIVACY.md
forbid:
  - 修改产品代码、冻结 schema 或备份格式
  - 在卡片中宣称功能已实现或放宽运行期离线边界
non_goals:
  - 实现三张卡或同步 Task Board/技术债
  - 诊断数据库、导出、删除与发布健康卡
acceptance:
  - "A1 DB lifecycle card schema/paths/evidence exact"
  - "A2 Local security card paths/contracts exact"
  - "A3 Share/privacy card paths/contracts exact"
  - "A4 三卡 paths 不重叠"
  - "A5 status todo、共同 forbids、无 implemented/merged claim"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};function Paths($p){$r=Get-Content $p -Raw;@([regex]::Matches([regex]::Match($r,'(?ms)^allow_paths:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value,'(?m)^  - ([^\r\n]+)$')|%{$_.Groups[1].Value})};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$dbp='specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY.md';$lsp='specs/tasks/T1-LOCAL-DATA-SECURITY.md';$ssp='specs/tasks/T1-SHARE-SCREEN-PRIVACY.md';$db=Get-Content $dbp -Raw;$ls=Get-Content $lsp -Raw;$ss=Get-Content $ssp -Raw;Exact (Paths $dbp) @('android/core/src/main/sqldelight/nz/myinspection/core/db/','android/core/src/main/kotlin/nz/myinspection/core/capture/','android/core/src/main/kotlin/nz/myinspection/core/retention/','android/core/src/test/kotlin/nz/myinspection/core/capture/','android/core/src/test/kotlin/nz/myinspection/core/retention/','android/core/src/test/kotlin/nz/myinspection/core/db/');Exact (Paths $lsp) @('android/app/build.gradle.kts','android/app/src/main/kotlin/nz/myinspection/app/platform/','android/app/src/main/kotlin/nz/myinspection/app/media/','android/app/src/test/kotlin/nz/myinspection/app/platform/');Exact (Paths $ssp) @('android/app/src/main/AndroidManifest.xml','android/app/src/main/res/xml/','android/app/src/main/kotlin/nz/myinspection/app/privacy/','android/app/src/test/kotlin/nz/myinspection/app/privacy/');$all=@((Paths $dbp)+(Paths $lsp)+(Paths $ssp));if($all.Count-ne@($all|sort -Unique).Count){throw 'overlap'};Must $db @('(?m)^depends_on: \[T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST\]$','(?m)^version_review: this card = the version review$','schema version','\.sqm','schema snapshot','verifyMigrations','(?m)^dod_assert: .*活跃 property/tenancy/template.*historical.*purged_at.*baseline');Must $ls @('(?m)^depends_on: \[T1-SPIKE-PLATFORM\]$','(?m)^dod_assert: .*AppStoragePolicy.*LocalSecretBox.*NEEDS_UNLOCK.*SafeLog','保留 ADR-0002 的 app-specific external/SAF 存储与导出范围','Keystore-backed LocalSecretBox','卷不可用/低空间','key 不可导出');Must $ss @('(?m)^depends_on: \[T1-LOCAL-DATA-SECURITY\]$','(?m)^dod_assert: .*allowBackup=false.*cleartext=false.*Photo Picker.*ClipboardPolicy.*FileProvider.*SensitiveSurfacePolicy','file:// URI','全局 FLAG_SECURE','content:// \+ temporary read grant','paths XML 只暴露 internal reports/export 子树','scoped URI');foreach($c in @($db,$ls,$ss)){Must $c @('(?m)^status: todo$','未经授权的运行期出站网络','账号/RBAC','遥测','未经本卡 version review 改冻结 schema/backup format');if($c-match'(?i)(status: merged|already implemented|功能已实现|已经实现)'){throw 'implementation claim'}}
dod_exit: 0
dod_assert: A1–A5 card/status mutations
review_gate: codex {verdict:pass}
hygiene: 每卡一个安全边界产出
doc_sync: 引用已合并数据库/安全权威（R5）
---
