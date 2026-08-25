---
id: T0-RECONCILE-UI-OFFLINE-OPERATIONS
title: 对齐备份、媒体、remediation 与收官 smoke 的离线体验指针
depends_on: [T0-RECONCILE-UI-COVERAGE, T0-RECONCILE-ROADMAP-INDEX]
status: todo
branch: T0-RECONCILE-UI-OFFLINE-OPERATIONS
worktree: C:\wt\T0-RECONCILE-UI-OFFLINE-OPERATIONS
allow_paths:
  - specs/tasks/T5-BACKUP-IO.md
  - specs/tasks/T5-LOCAL-MEDIA-RETENTION.md
  - specs/tasks/T7-REMEDIATION.md
  - specs/tasks/T7-SMOKE-POLISH.md
forbid:
  - 修改产品代码、备份格式或运行期网络硬边界
  - 收窄 ADR-0002 的 full/property scope 或把 provider/LLM 不可用显示为核心 app 失败
non_goals:
  - 通知、日程、采集、历史与 PDF 卡同步
  - 实现备份、清除、诊断或 remediation
acceptance:
  - "A1 Backup IO：plan_ref + A1–A5；backup_scopes 精确为 full/property，backup_states 精确 10 项；覆盖 phases、receipt、staging/rollback、provider/authorization/storage/secret failure 与本地/USB 飞行模式"
  - "A2 Media retention：同 plan_ref + A1–A4，覆盖 1/3/5/10/Always、preflight/protected refs/30天、confirmation/progress/recovery、hash/size 与不删 DB/PDF/backup/cloud"
  - "A3 Remediation：同 plan_ref + A1–A4，覆盖 on-device first、remote explicit/cancellable、offline fallback、safe payload/source/disclaimer、永不阻断 finalize/report"
  - "A4 Smoke：primary journey plan_ref + A1–A5，覆盖 flight-mode、provider/permission/storage/process-death、backup/restore/erase/health/share、TalkBack/200%/theme/performance/exact evidence"
  - "A5 四卡无 component-matrix headings；runtime network 仅限用户显式 remediation；不实现功能或修改冻结备份格式"
dod_command: function ExactIds($r,$e){$q=[regex]::Match($r,'(?ms)^acceptance:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value;$a=@([regex]::Matches($q,'(?m)^  - "?([^ "\r\n]+) ')|%{$_.Groups[1].Value});if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'acceptance ids'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$b=Get-Content 'specs/tasks/T5-BACKUP-IO.md' -Raw;$m=Get-Content 'specs/tasks/T5-LOCAL-MEDIA-RETENTION.md' -Raw;$r=Get-Content 'specs/tasks/T7-REMEDIATION.md' -Raw;$s=Get-Content 'specs/tasks/T7-SMOKE-POLISH.md' -Raw;ExactIds $b @('A1','A2','A3','A4','A5');ExactIds $m @('A1','A2','A3','A4');ExactIds $r @('A1','A2','A3','A4');ExactIds $s @('A1','A2','A3','A4','A5');Must $b @('(?m)^plan_ref: context/DESIGN\.md#offline-and-data-protection-experience$','(?m)^backup_scopes: \[full, property\]$','(?m)^  - "A1 (?=[^"\r\n]*full)(?=[^"\r\n]*property)(?=[^"\r\n]*NOT_CONFIGURED)(?=[^"\r\n]*FAILED)(?=[^"\r\n]*verified receipt)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*staging)(?=[^"\r\n]*rollback)(?=[^"\r\n]*verify-before-replace)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*provider)(?=[^"\r\n]*authorization)(?=[^"\r\n]*storage)(?=[^"\r\n]*secret)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*local/USB)(?=[^"\r\n]*flight-mode)[^"\r\n]+"$','(?m)^  - "A5 (?=[^"\r\n]*runtime network)(?=[^"\r\n]*backup format)[^"\r\n]+"$');Must $m @('(?m)^plan_ref: context/DESIGN\.md#offline-and-data-protection-experience$','(?m)^  - "A1 (?=[^"\r\n]*1/3/5/10/Always)(?=[^"\r\n]*30-day)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*preflight)(?=[^"\r\n]*protected refs)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*confirmation)(?=[^"\r\n]*progress)(?=[^"\r\n]*recovery)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*hash/size)(?=[^"\r\n]*never delete DB/PDF/backup/cloud)[^"\r\n]+"$');Must $r @('(?m)^plan_ref: context/DESIGN\.md#offline-and-data-protection-experience$','(?m)^  - "A1 [^"\r\n]*on-device first[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*remote explicit)(?=[^"\r\n]*cancellable)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*offline fallback)(?=[^"\r\n]*safe payload)(?=[^"\r\n]*source)(?=[^"\r\n]*disclaimer)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*never block)(?=[^"\r\n]*finalize)(?=[^"\r\n]*report)[^"\r\n]+"$');Must $s @('(?m)^plan_ref: context/DESIGN\.md#primary-inspection-journey$','(?m)^  - "A1 [^"\r\n]*flight-mode end-to-end[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*provider)(?=[^"\r\n]*permission)(?=[^"\r\n]*storage)(?=[^"\r\n]*process-death)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*backup)(?=[^"\r\n]*restore)(?=[^"\r\n]*erase)(?=[^"\r\n]*health)(?=[^"\r\n]*share)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*TalkBack)(?=[^"\r\n]*200%)(?=[^"\r\n]*theme)(?=[^"\r\n]*performance)[^"\r\n]+"$','(?m)^  - "A5 [^"\r\n]*exact evidence[^"\r\n]+"$');if(($b+$m+$r+$s)-match'(?m)^### .*component matrix$'){throw 'copied matrix'};$sd=[regex]::Match($b,'(?m)^backup_states: \[(.*)\]$').Groups[1].Value;$st=@($sd.Split(',')|%{$_.Trim()});$ex='NOT_CONFIGURED,READY,RUNNING,VERIFIED,PROVIDER_UNAVAILABLE,AUTHORIZATION_REVOKED,NEEDS_UNLOCK,NEEDS_PASSPHRASE,LOW_STORAGE,FAILED'-split',';if($st.Count-ne$ex.Count-or$st.Count-ne@($st|sort -Unique).Count-or(Compare-Object ($st|sort) ($ex|sort))){throw 'backup states'};Must $b @('PREPARING.*ENCRYPTING.*WRITING.*VERIFYING')
dod_exit: 0
dod_assert: A1–A5 exact sets 全绿；full/property 与十状态均不可增删替换
review_gate: codex {verdict:pass}
hygiene: 卡内只保留可验收状态和链接，不重复页面或组件规格
doc_sync: 四张卡与 UI 覆盖索引、数据库/安全权威一致（R5）
---

# T0-RECONCILE-UI-OFFLINE-OPERATIONS

## 产出

把备份、恢复、媒体清理、可选 remediation 和最终 smoke 验收对齐到离线优先体验合同，明确核心 app 无网可用，只有显式远程 provider 在离线时降级。

## 验收

执行 front matter 的 `dod_command`，并确认没有扩大网络能力或收窄既有备份范围。
