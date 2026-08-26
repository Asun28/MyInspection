---
id: T5-DIAGNOSTIC-EXPORT
title: 用户授权的离线诊断导出：只读健康摘要 + 脱敏事件包
depends_on: [T5-OPERATION-EVENT-STORE, T1-SHARE-SCREEN-PRIVACY]
plan_ref: docs/DATABASE-DESIGN.md#8-diagnostic-export-contract
status: todo
branch: T5-DIAGNOSTIC-EXPORT
worktree: C:\wt\T5-DIAGNOSTIC-EXPORT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/diagnostics/export/
  - android/core/src/test/kotlin/nz/myinspection/core/diagnostics/export/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/diagnostics/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/diagnostics/
  - android/app/src/androidTest/kotlin/nz/myinspection/app/feature/diagnostics/
forbid:
  - 直接导出主数据库、照片/音频、租客/地址/备注、凭据、原始路径/URI 或 provider 错误体
  - 后台自动上传、隐藏遥测、远程 admin 控制台、诊断模式修改 finalized 证据
  - 宽目录授权、file://、永久可读 provider 或未确认即分享
non_goals:
  - 数据库修复/编辑器、客服账号、云端日志平台、崩溃分析 SDK
  - 备份替代品；诊断包不能恢复数据
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.diagnostics.export.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 设置页明确展示包含/排除项；默认 7 天且最多 90 天；导出只含版本化 manifest、allowlisted events、quick_check 结果与计数摘要；无 serial/account/network id/精确路径；SAF 保存和临时只读 content:// 分享均可离线完成；取消/失败不留可分享残件；输出 manifest/hash 自校验；admin/support 无写入口
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 将 TD136 置 paid；docs/DATABASE-DESIGN.md、SECURITY.md、TASK-BOARD 更新（R5）
---

# T5-DIAGNOSTIC-EXPORT

## 产出

设置页提供 `Export diagnostic report`。用户先看包含/排除说明，再选择最近 7/30/90 天，生成版本化诊断包：

- app/schema/OS API/device model；不含设备唯一标识；
- `PRAGMA quick_check` 的结果码、各表计数与链/备份验证结果计数；不含业务行内容；
- T5-OPERATION-EVENT-STORE 的脱敏事件；
- manifest、文件大小和 SHA-256，供支持确认包完整。

支持人员只能读取用户主动提供的包。所有修复建议回到普通产品流程；本卡不提供 SQL 控制台、repair button 或 finalized evidence 修改能力。

## 验收

除自动测试外，真机飞行模式完成一次 SAF 保存和一次系统分享；检查系统 chooser 关闭后临时授权被收回。用带地址、租客、备注、媒体路径、SAF URI、token 和 CRLF 的恶意夹具验证成品零命中。

