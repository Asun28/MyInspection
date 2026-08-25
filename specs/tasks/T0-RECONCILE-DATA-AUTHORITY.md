---
id: T0-RECONCILE-DATA-AUTHORITY
title: 同步离线数据库、安全与备份设计权威
depends_on: []
parallelizable_with: [T0-RECONCILE-DESIGN-METADATA, T0-RECONCILE-LESSONS]
status: todo
branch: T0-RECONCILE-DATA-AUTHORITY
worktree: C:\wt\T0-RECONCILE-DATA-AUTHORITY
allow_paths:
  - docs/DATABASE-DESIGN.md
  - docs/adr/0006-offline-security-backup-hardening.md
  - docs/SECURITY.md
  - docs/inspection-app-requirements.md
forbid:
  - 修改产品代码、冻结 schema、备份格式或运行期网络边界
  - 引入账号、云同步、远程 admin、遥测或自动上传
non_goals:
  - 实现数据库迁移、安全 primitive、备份恢复或诊断功能
  - 修改任务总表、技术债或实现卡依赖
acceptance:
  - "A1 数据库权威：DATABASE-DESIGN 恰有 1–10 十个主节，明确本文件不授权未评审迁移，并完整覆盖三存储边界、表生命周期、字段写权限、active/history 读取、独立 diagnostics schema、导出与验收"
  - "A2 ADR 决策：ADR-0006 恰有五项决策，并具名离线能力矩阵、威胁边界、format v1 全量包、Keystore 信封、SAF provider 失败隔离和恢复先验后替换"
  - "A3 安全投影：SECURITY 2.1–2.5 五节同时覆盖出站、本机数据/密钥、备份恢复分享、日志/界面泄露和威胁验证；含 allowBackup=false、20,000 行上限、无远程 admin 写入口"
  - "A4 需求投影：需求 §11 与 §14 同步全量离线备份、provider 降级、物理清除、唯一联网 remediation、本机诊断/发布证据；未来云服务继续明确不在 v1"
  - "A5 设计/实现边界：四份文档只定义 reviewed target；不得声称 schema/安全 primitive/诊断/恢复已实现，不得授权绕过 FrozenPaths 或 TD4 migration verification"
dod_command: pwsh -NoProfile -Command "if (-not (((Select-String -Path 'docs/DATABASE-DESIGN.md' -Pattern '^## (1\. Verdict|2\. Database boundaries|3\. Table lifecycle and ownership|4\. Field write-authority matrix|5\. Active versus historical reads|6\. Confirmed hardening work|7\. Diagnostics database schema|8\. Diagnostic export contract|9\. Acceptance gates|10\. References)$').Count -eq 10) -and (Select-String -Path 'docs/DATABASE-DESIGN.md' -SimpleMatch 'it does not authorize an unreviewed migration') -and (Select-String -Path 'docs/DATABASE-DESIGN.md' -SimpleMatch 'OperationEventRecorder') -and ((Select-String -Path 'docs/adr/0006-offline-security-backup-hardening.md' -Pattern '^### [1-5]\. ').Count -eq 5) -and (Select-String -Path 'docs/adr/0006-offline-security-backup-hardening.md' -Pattern '^## 离线能力矩阵$') -and (Select-String -Path 'docs/adr/0006-offline-security-backup-hardening.md' -Pattern '^## 威胁边界$') -and ((Select-String -Path 'docs/SECURITY.md' -Pattern '^### 2\.[1-5] ').Count -eq 5) -and (Select-String -Path 'docs/SECURITY.md' -SimpleMatch 'allowBackup=false') -and (Select-String -Path 'docs/SECURITY.md' -SimpleMatch '20,000 行') -and (Select-String -Path 'docs/SECURITY.md' -SimpleMatch 'Admin/support') -and (Select-String -Path 'docs/inspection-app-requirements.md' -Pattern '^## 11\. 数据、备份、隐私$') -and (Select-String -Path 'docs/inspection-app-requirements.md' -Pattern '^## 14\. 生产可用性与非功能验收') -and (Select-String -Path 'docs/inspection-app-requirements.md' -Pattern '^### 安全与隐私$') -and (Select-String -Path 'docs/inspection-app-requirements.md' -Pattern '^### 本地监控与发布证据$') -and (Select-String -Path 'docs/inspection-app-requirements.md' -SimpleMatch '[未来设计,不在 v1]'))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 的四份权威面、章节数量、关键安全/离线边界和“设计未实现”声明全部命中；删除任一章节或关键约束即 RED
review_gate: codex {verdict:pass}
hygiene: 同一规则只保留一个权威定义，其余文档用引用或职责投影避免重复
doc_sync: 四个权威面内部链接与术语一致（R5）
---

# T0-RECONCILE-DATA-AUTHORITY

## 产出

把本地有效的数据库生命周期、独立诊断库、离线安全、敏感分享、本机备份与恢复失败语义整理为互不矛盾的权威文档。文档必须区分“设计目标”和“已经实现”，不得扩大 v1 到账号或云同步。

## 验收

执行 front matter 的 `dod_command`，并运行 `scripts/check-cards.ps1`、`scripts/verify.ps1` 与 diff 预算检查。
