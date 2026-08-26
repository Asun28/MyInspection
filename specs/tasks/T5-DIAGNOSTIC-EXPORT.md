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
  - 禁止遥测/自动上传、远程 admin；诊断/健康不得写 finalized evidence；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - 数据库修复/编辑器、客服账号、云端日志平台、崩溃分析 SDK
  - 备份替代品；诊断包不能恢复数据
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.diagnostics.export.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 设置页明确展示包含/排除项；默认 7 天且只允许 7/30/90 天、最多 90 天；导出只含版本化 manifest、allowlisted events、quick_check 结果与计数摘要；无 serial/account/network id/精确路径；core+app unit tests 与 assemble 绿；SAF 保存和临时只读 content:// 分享均可离线完成；所有取消/失败路径不留可分享残件，单元测试和真机记录分别证明 chooser 取消、SAF 拒绝/写失败、自校验失败均删除 staging/未发布副本，分享完成或取消后显式 revoke 且旧 URI 再读失败；输出 manifest/hash 自校验，恶意夹具验证成品零命中地址、姓名、联系方式、备注/转写、URI、token 与 CRLF；admin/support 无写入口
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 将 TD136 置 paid；docs/DATABASE-DESIGN.md、SECURITY.md、TASK-BOARD 更新（R5）
---

# T5-DIAGNOSTIC-EXPORT

## 产出

设置页提供 `Export diagnostic report`。用户先确认包含/排除，再选择已声明的三档时间窗，生成版本化包：app/schema/OS API/device model（无唯一标识）、`quick_check` 结果码、表计数、验证结果计数、脱敏事件，以及 manifest、文件大小和 SHA-256。

导出在私有 staging 完成并自校验后，才通过 SAF 保存或受控 `content://` 分享。取消、写失败、自校验失败或分享取消均删除未发布副本；分享授权只读、单次、按生命周期撤回。支持人员只能读取用户主动提供的包，没有 SQL 控制台、repair button 或 finalized evidence 修改能力。

## 验收

真机飞行模式记录五条独立证据：SAF 成功保存、chooser 取消、SAF provider 拒绝/中途失败、分享成功后 revoke、分享取消后 revoke。每条记录步骤、构建、前后 staging 文件集合与旧 `content://` 再读结果；取消/失败后不得有可分享残件，两条 revoke 后旧 URI 必须不可读。成品禁地址、姓名、联系方式、备注/转写、媒体路径、SAF URI、token、provider body 与 CRLF；恶意夹具对最终包解包逐字节扫描，全部禁项零命中。manifest 缺项、额外文件、hash/size 不符、越界时间窗和未知 registry version 都 fail closed。
