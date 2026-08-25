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
dod_command: pwsh -NoProfile -Command "if (-not ((Test-Path 'docs/DATABASE-DESIGN.md') -and (Test-Path 'docs/adr/0006-offline-security-backup-hardening.md') -and (Select-String -Path 'docs/SECURITY.md' -SimpleMatch 'Offline') -and (Select-String -Path 'docs/inspection-app-requirements.md' -SimpleMatch 'backup'))) { exit 1 }"
dod_exit: 0
dod_assert: 数据库设计、ADR-0006、安全与需求四个权威面同时存在，且明确离线与备份边界；diff 只含文档，不宣称产品已实现
review_gate: codex {verdict:pass}
hygiene: 同一规则只保留一个权威定义，其余文档用引用或职责投影避免重复
doc_sync: 四个权威面内部链接与术语一致（R5）
---

# T0-RECONCILE-DATA-AUTHORITY

## 产出

把本地有效的数据库生命周期、独立诊断库、离线安全、敏感分享、本机备份与恢复失败语义整理为互不矛盾的权威文档。文档必须区分“设计目标”和“已经实现”，不得扩大 v1 到账号或云同步。

## 验收

执行 front matter 的 `dod_command`，并运行 `scripts/check-cards.ps1`、`scripts/verify.ps1` 与 diff 预算检查。
