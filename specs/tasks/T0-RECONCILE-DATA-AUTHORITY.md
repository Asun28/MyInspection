---
id: T0-RECONCILE-DATA-AUTHORITY
title: 同步离线数据库、安全与备份设计权威
depends_on: [T0-RECONCILE-LESSONS]
parallelizable_with: [T0-RECONCILE-DESIGN-METADATA]
status: todo
branch: T0-RECONCILE-DATA-AUTHORITY
worktree: C:\wt\T0-RECONCILE-DATA-AUTHORITY
allow_paths:
  - CLAUDE.md
  - docs/DATABASE-DESIGN.md
  - docs/adr/0006-offline-security-backup-hardening.md
  - docs/SECURITY.md
  - docs/inspection-app-requirements.md
forbid:
  - 修改产品代码、冻结 schema、备份格式或运行期网络边界
  - 收窄 ADR-0002 的 app 私有存储 + SAF provider 或整包 + 按物业导出范围
  - 引入账号、云同步、远程 admin、遥测或自动上传
non_goals:
  - 实现数据库迁移、安全 primitive、备份恢复或诊断功能
  - 修改任务总表、技术债或实现卡依赖
acceptance:
  - "A1 DATABASE-DESIGN 恰有 1–10 十节，声明不授权未评审迁移，并覆盖存储边界、生命周期、写权限、active/history、diagnostics、导出与验收"
  - "A2 ADR-0006 恰有五项决策，仅补充 ADR-0002 的密钥/失败隔离/验证细节，明确保留 app 私有 + SAF 与整包 + 按物业范围；含离线矩阵、威胁边界、Keystore、provider 隔离、先验后替换"
  - "A3 SECURITY 2.1–2.5 覆盖出站、本机数据/密钥、备份恢复分享、日志/界面泄露和威胁验证；含 allowBackup=false、20,000 行上限、无远程 admin 写入口"
  - "A4 需求 §11/§14 保留 ADR-0002 和整包 + 按物业导出，并同步 provider 降级、物理清除、唯一联网 remediation、本机诊断/发布证据；未来云服务不在 v1"
  - "A5 CLAUDE 登记 DATABASE-DESIGN 与 ADR-0006；文档只定义 reviewed target，不得声称实现或绕过 FrozenPaths/TD4"
dod_command: function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};$db=Get-Content 'docs/DATABASE-DESIGN.md' -Raw;$adr=Get-Content 'docs/adr/0006-offline-security-backup-hardening.md' -Raw;$sec=Get-Content 'docs/SECURITY.md' -Raw;$req=Get-Content 'docs/inspection-app-requirements.md' -Raw;$idx=Get-Content 'CLAUDE.md' -Raw;Exact @([regex]::Matches($db,'(?m)^## ([0-9]+)\. ([^\r\n]+)$')|%{$_.Groups[1].Value+'. '+$_.Groups[2].Value}) @('1. Verdict','2. Database boundaries','3. Table lifecycle and ownership','4. Field write-authority matrix','5. Active versus historical reads','6. Confirmed hardening work','7. Diagnostics database schema','8. Diagnostic export contract','9. Acceptance gates','10. References');Must $db @('(?m)^\| Main evidence database \|','(?m)^\| Diagnostics database \|','(?m)^\| File storage \|','it does not authorize an unreviewed migration','OperationEventRecorder appends','selectActiveById','selectAnyById','Support/admin receives no database console');Exact @([regex]::Matches($adr,'(?m)^### ([0-9]+)\. ')|%{$_.Groups[1].Value}) @('1','2','3','4','5');Must $adr @('(?m)^日期：.*amends：ADR-0002 的密钥、失败隔离与恢复验证细节；保留其存储/provider 与整包/按物业范围','(?m)^- \*\*format v1 UI 同时保留全量与按物业备份。\*\*','Android Keystore 内不可导出的 AES-GCM key','provider 不支持安全 rename 时采用复制到最终对象并复核','全部验证后才替换；失败保持当前数据','(?m)^## 离线能力矩阵$','(?m)^## 威胁边界$','本 ADR 是设计/任务契约。实现仍需独立任务卡');Exact @([regex]::Matches($sec,'(?m)^### (2\.[0-9]+) ')|%{$_.Groups[1].Value}) @('2.1','2.2','2.3','2.4','2.5');Must $sec @('allowBackup=false','最多保留 90 天/20,000 行','“Admin/support” 无远程入口或写权限','唯一允许的运行期出站请求','用户主动导出');Must $req @('(?m)^## 11\. 数据、备份、隐私$','\*\*\[定,ADR-0002\]\*\* 工作数据留在 app 私有目录；用户通过 Android SAF','\*\*\[定\]\*\* 备份形态:整包导出 \+ 按物业导出','provider 不可达、授权收回、低空间或后台密钥不可用，只影响该次备份/恢复','本产品没有账号，故“账号物理注销”等价为 `Delete all local data`','Remediation 是唯一联网点','(?m)^## 14\. 生产可用性与非功能验收','(?m)^### 安全与隐私$','(?m)^### 本地监控与发布证据$','\*\*\[未来设计,不在 v1\]\*\*');Must $idx @('(?m)^\d+\. `docs/DATABASE-DESIGN\.md`','(?m)^\d+\. `docs/adr/0006-offline-security-backup-hardening\.md`');Must $sec @('(?m)^### 2\.4 日志、通知与界面泄露$','(?m)^- 生产日志只写操作名、非敏感 reason code','(?m)^- 用户可见通知只写 `Backup needs attention`','secure-window/recents 防护')
dod_exit: 0
dod_assert: A1–A5 exact sets 全绿；ADR-0002 的存储/provider 与 full/property 范围必须保持
review_gate: codex {verdict:pass}
hygiene: 同一规则只保留一个权威定义，其余文档用引用或职责投影避免重复
doc_sync: CLAUDE 索引与四个权威面内部链接、术语一致（R5）
---
