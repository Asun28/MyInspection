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
dod_assert: 设置页明确展示包含/排除项；默认 7 天且只允许 7/30/90 天、最多 90 天；导出只含版本化 manifest、allowlisted events、quick_check 结果与计数摘要；无 serial/account/network id/精确路径；core+app unit tests 与 assemble 绿；SAF 保存和临时只读 content:// 分享均可离线完成；所有取消/失败路径清除私有 staging 与未发布分享副本；SAF 写失败须捕获已创建目标 URI、尝试删除并验证不可读，provider 拒绝删除时结果保持失败且明确提示用户手动清理指定显示名，不得声称零残件；成功分享沿用临时读授权，接收端读取竞态测试证明 chooser 返回不会触发清理，启动或下次分享时才删除超过 24 小时的副本并验证旧 URI 不可读；输出 manifest/hash 自校验，恶意夹具验证成品零命中地址、姓名、联系方式、备注/转写、URI、token 与 CRLF；admin/support 无写入口
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 将 TD136 置 paid；docs/DATABASE-DESIGN.md、SECURITY.md、TASK-BOARD 更新（R5）
---

# T5-DIAGNOSTIC-EXPORT

## 产出

设置页提供 `Export diagnostic report`。用户先确认包含/排除，再选择已声明的三档时间窗，生成版本化包：app/schema/OS API/device model（无唯一标识）、`quick_check` 结果码、表计数、验证结果计数、脱敏事件，以及 manifest、文件大小和 SHA-256。

导出在私有 staging 完成并自校验后，才通过 SAF 保存或受控 `content://` 分享。取消、写失败、自校验失败或分享取消均删除私有 staging 与未发布分享副本。SAF 已创建目标后写入失败时捕获该 URI 并请求 provider 删除；删除失败不得改报成功，而要显示 provider 与安全显示名，指导用户在目标位置手动删除半成品，诊断事件不得保存 URI。成功分享严格沿用 T1-SHARE-SCREEN-PRIVACY 的临时只读授权和有界清理：不把 chooser 返回当作接收端读取完成，只在启动或下次分享时删除年龄超过 24 小时的受控副本。支持人员只能读取用户主动提供的包，没有 SQL 控制台、repair button 或 finalized evidence 修改能力。

## 验收

真机飞行模式记录六条独立证据：SAF 成功保存、chooser 取消、SAF 中途失败且半成品删除成功、provider 拒绝删除且显示人工清理提示、接收端持续读取跨越 chooser 返回且未被提前清理、启动或下次分享清理超过 24 小时副本。每条记录步骤、构建、前后私有 staging 文件集合；SAF 失败夹具还须捕获目标 URI 并证明删除后不可读，拒绝删除夹具须证明结果仍为失败、提示使用安全显示名且事件不落 URI；分享夹具须证明读取窗口内旧 `content://` 可读、超过年龄阈值的后续生命周期清理后不可读。成品禁地址、姓名、联系方式、备注/转写、媒体路径、SAF URI、token、provider body 与 CRLF；恶意夹具对最终包解包逐字节扫描，全部禁项零命中。manifest 缺项、额外文件、hash/size 不符、越界时间窗和未知 registry version 都 fail closed。
