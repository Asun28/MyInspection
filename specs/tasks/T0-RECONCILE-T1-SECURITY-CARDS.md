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
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 && pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY.md' -Pattern '^depends_on: \[T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST\]$') -and (Select-String -Path 'specs/tasks/T1-LOCAL-DATA-SECURITY.md' -Pattern '^depends_on: \[T1-SPIKE-PLATFORM\]$') -and (Select-String -Path 'specs/tasks/T1-LOCAL-DATA-SECURITY.md' -Pattern '^dod_assert: .*AppStoragePolicy.*LocalSecretBox.*SafeLog') -and (Select-String -Path 'specs/tasks/T1-SHARE-SCREEN-PRIVACY.md' -Pattern '^depends_on: \[T1-LOCAL-DATA-SECURITY\]$') -and (Select-String -Path 'specs/tasks/T1-SHARE-SCREEN-PRIVACY.md' -Pattern '^dod_assert: .*allowBackup=false.*cleartext=false.*FileProvider.*SensitiveSurfacePolicy'))) { exit 1 }"
dod_exit: 0
dod_assert: 三张卡分别拥有数据库生命周期、存储/Keystore/安全日志、分享/窗口/manifest 边界，依赖和 allow_paths 不重叠且可离线验收
review_gate: codex {verdict:pass}
hygiene: 每张卡只有一个安全边界产出，不把后续诊断功能塞入 T1
doc_sync: 卡片引用已合并的数据库/安全设计权威（R5）
---

# T0-RECONCILE-T1-SECURITY-CARDS

## 产出

登记三张独立的 T1 实现卡，形成数据库写权限、设备本地数据保护、Android 分享与屏幕隐私的基础依赖链。

## 验收

执行 front matter 的 `dod_command` 和 `scripts/check-cards.ps1`。
