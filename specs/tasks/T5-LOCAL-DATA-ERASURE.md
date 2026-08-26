---
id: T5-LOCAL-DATA-ERASURE
title: 无账号场景的全量本机数据物理清除：影响预览 + ERASE 强确认 + 清除验证
depends_on: [T5-BACKUP-IO, T1-LOCAL-DATA-SECURITY, T1-SHARE-SCREEN-PRIVACY]
status: todo
branch: T5-LOCAL-DATA-ERASURE
worktree: C:\wt\T5-LOCAL-DATA-ERASURE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/privacy/erasure/
  - android/core/src/test/kotlin/nz/myinspection/core/privacy/erasure/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/settings/erasure/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/settings/erasure/
forbid:
  - 删除或修改用户经 SAF 保存的外部 `.mibk`、云端/USB 文件；伪造“账号注销”或服务端删除语义
  - 未经影响预览与 `ERASE` 精确输入即开始；把部分清除、待重试或仅清缓存显示为成功
  - 修改冻结 schema/backup format；保留可重新打开的 DB、媒体、凭据、诊断或持久 URI 授权
non_goals:
  - 租约结束后联系方式清理（已由 merged T5-RETENTION 交付）；物业级照片归档（T5-LOCAL-MEDIA-RETENTION）
  - 账号、云端注销、外部备份删除、远程擦除、恢复忘记的备份口令
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.privacy.erasure.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 清除计划完整枚举主/诊断 DB、照片、音频、PDF、草稿/设置、Keystore aliases/secret envelopes、cache/temp、restore journal/staging 与 persisted URI grants；UI 先显示类别/不可逆性/外部 `.mibk` 保留边界并提供 Back up current data，只有输入精确 `ERASE` 才执行；执行中不可重复启动/返回，任一类别未确认清除不得显示成功；成功后进程重启进入 first-run 且任何旧 ID/route/secret/media/diagnostic 均不可重开；单元/变异测试与真机全量 fixture 清除记录全绿
review_gate: codex {verdict:pass}
hygiene: 每个清除类别一条权威计划断言，删除任一类别的单点变异必须命中具名失败（R4）
doc_sync: 需求 §11/§14、SECURITY、TASK-BOARD 与 T7-SMOKE-POLISH 记录证据（R5）
---

# T5-LOCAL-DATA-ERASURE

## 产出边界

本产品没有账号，因此本卡提供“物理注销”的真实等价物：设备所有者在 Settings 进入 `Delete all local data`，先看到完整影响范围、最近已验证备份与“外部备份不会被删除”，再输入 `ERASE`。普通联系信息清理由已交付 `T5-RETENTION` 继续拥有，本卡不改其历史契约。

## 状态与失败语义

状态固定为 `REVIEW → CONFIRMING → ERASING → VERIFYING → COMPLETE | FAILED`。进入 `ERASING` 后不提供取消或 Back；重复点击复用同一 operation id。清除完成必须逐类别验证不可重开，再进入首次使用空态。验证失败显示尚未确认清除的类别与安全重试，不展示旧业务内容，也不把“已请求系统清除”当作成功。

外部 SAF/USB/云 provider 中用户保存的加密 `.mibk` 不属于 app-owned 本机数据，绝不删除；本机保存的 URI 授权和目的地元数据必须清除。执行前的 `Back up current data` 是可选安全动作，不得造成网络或 provider 强依赖。

## 验收

除自动测试外，真机使用包含多物业、租客、finalized/draft 巡检、照片、音频、双版 PDF、备份回执、诊断事件、API key 与未完成恢复 journal 的完整 fixture 执行一次。重启后只出现 first-run，系统文件/数据库/Keystore/URI 授权检查均无旧数据；重新选择外部 `.mibk` 仍可进入正常恢复预检。
