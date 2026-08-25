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
  - "A1 DATABASE-LIFECYCLE 卡：depends on migration snapshot allowlist；恰有 6 个 sqldelight/capture/retention main+test paths；显式声明 this card = the version review，并要求 schema version、.sqm、snapshot、verifyMigrations 证据；active/any reads、purged terminal、baseline ownership 均在 DoD"
  - "A2 LOCAL-DATA-SECURITY 卡：depends on platform spike；恰有 app build/platform/media/test 4 paths；AppStoragePolicy、Keystore LocalSecretBox、SafeLog、卷/低空间/NEEDS_UNLOCK 均在 DoD，禁止明文/外部 fallback/运行期出站"
  - "A3 SHARE-SCREEN-PRIVACY 卡：depends on local security；恰有 manifest/res xml/privacy main+test 4 paths；allowBackup/cleartext/Photo Picker/clipboard/FileProvider/scoped URI/selective secure surface 均在 DoD"
  - "A4 所有权不重叠：三张卡 allow_paths 两两无重叠，数据库/core、存储平台、Android privacy 出口各唯一 owner；check-cards 解析全部依赖和 id"
  - "A5 离线/冻结边界：三卡共同禁止非授权网络、账号/RBAC/遥测、冻结 schema/backup format 绕过；每卡 non_goals 指回正确下游 owner"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 && function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set mismatch'}};function Paths($p){$r=Get-Content $p -Raw;@([regex]::Matches([regex]::Match($r,'(?ms)^allow_paths:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value,'(?m)^  - ([^\r\n]+)$')|%{$_.Groups[1].Value})};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$dbp='specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY.md';$lsp='specs/tasks/T1-LOCAL-DATA-SECURITY.md';$ssp='specs/tasks/T1-SHARE-SCREEN-PRIVACY.md';$db=Get-Content $dbp -Raw;$ls=Get-Content $lsp -Raw;$ss=Get-Content $ssp -Raw;Exact (Paths $dbp) @('android/core/src/main/sqldelight/nz/myinspection/core/db/','android/core/src/main/kotlin/nz/myinspection/core/capture/','android/core/src/main/kotlin/nz/myinspection/core/retention/','android/core/src/test/kotlin/nz/myinspection/core/capture/','android/core/src/test/kotlin/nz/myinspection/core/retention/','android/core/src/test/kotlin/nz/myinspection/core/db/');Exact (Paths $lsp) @('android/app/build.gradle.kts','android/app/src/main/kotlin/nz/myinspection/app/platform/','android/app/src/main/kotlin/nz/myinspection/app/media/','android/app/src/test/kotlin/nz/myinspection/app/platform/');Exact (Paths $ssp) @('android/app/src/main/AndroidManifest.xml','android/app/src/main/res/xml/','android/app/src/main/kotlin/nz/myinspection/app/privacy/','android/app/src/test/kotlin/nz/myinspection/app/privacy/');$all=@((Paths $dbp)+(Paths $lsp)+(Paths $ssp));if($all.Count-ne@($all|sort -Unique).Count){throw 'overlap'};Must $db @('(?m)^depends_on: \[T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST\]$','(?m)^version_review: this card = the version review$','schema version','\.sqm','schema snapshot','verifyMigrations','(?m)^dod_assert: .*活跃 property/tenancy/template.*historical.*purged_at.*baseline','新增账号、角色、ACL、admin 绕过或物理级联删除','未经版本评审改哈希域');Must $ls @('(?m)^depends_on: \[T1-SPIKE-PLATFORM\]$','(?m)^dod_assert: .*AppStoragePolicy.*LocalSecretBox.*NEEDS_UNLOCK.*SafeLog','运行期出站网络','明文 secret/tenant data','外部 app-specific storage');Must $ss @('(?m)^depends_on: \[T1-LOCAL-DATA-SECURITY\]$','(?m)^dod_assert: .*allowBackup=false.*cleartext=false.*Photo Picker.*ClipboardPolicy.*FileProvider.*SensitiveSurfacePolicy','运行期出站网络','file:// URI','全局 FLAG_SECURE');Must $ls @('Keystore-backed LocalSecretBox','卷不可用/低空间','key 不可导出');Must $ss @('content:// \+ temporary read grant','paths XML 只暴露 internal reports/export 子树','scoped URI')
dod_exit: 0
dod_assert: A1–A5 完整矩阵通过：依赖、6/4/4 精确且两两不重叠的 paths、schema version-review/.sqm/snapshot/verifyMigrations 证据、逐卡 DoD/禁止边界与 check-cards 均成立；唯一语义逐条删除即 RED
review_gate: codex {verdict:pass}
hygiene: 每张卡只有一个安全边界产出，不把后续诊断功能塞入 T1
doc_sync: 卡片引用已合并的数据库/安全设计权威（R5）
---

# T0-RECONCILE-T1-SECURITY-CARDS

## 产出

登记三张独立的 T1 实现卡，形成数据库写权限、设备本地数据保护、Android 分享与屏幕隐私的基础依赖链。

## 验收

执行 front matter 的 `dod_command` 和 `scripts/check-cards.ps1`。
