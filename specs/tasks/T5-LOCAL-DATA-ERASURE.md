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
  - 禁止遥测/自动上传、远程 admin；诊断/健康不得写 finalized evidence；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - 租约结束后联系方式清理（已由 merged T5-RETENTION 交付）；物业级照片归档（T5-LOCAL-MEDIA-RETENTION）
  - 账号、云端注销、外部备份删除、远程擦除、恢复忘记的备份口令
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.privacy.erasure.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 清除计划完整枚举主/诊断 DB、照片、音频、PDF、草稿/设置、Keystore aliases/secret envelopes、cache/temp、restore journal/staging 与 persisted URI grants；UI 先显示类别/不可逆性/外部 `.mibk` 保留边界并提供 Back up current data，只有输入精确 `ERASE` 才写入 opaque marker 并执行且此后不可取消；诊断 DB/WAL/SHM 最后关闭删除，开始后绝不重开；任一类别未确认清除不得显示成功，post-diagnostics 失败由 marker 使下次启动在 DB 初始化前续跑；成功不写 success event、不留 DB/marker，进程重启进入 first-run 且任何旧 ID/route/secret/media/diagnostic 均不可重开；单元/边界 kill 变异与真机全量 fixture 清除记录全绿
review_gate: codex {verdict:pass}
hygiene: 每个清除类别一条权威计划断言，删除任一类别的单点变异必须命中具名失败（R4）
doc_sync: 需求 §11/§14、SECURITY、TASK-BOARD 与 T7-SMOKE-POLISH 记录证据（R5）
---

# T5-LOCAL-DATA-ERASURE

## 产出边界

本产品没有账号，因此“物理注销”等价为设备所有者执行 `Delete all local data`。用户先看到完整影响范围、最近已验证备份与“外部备份不会被删除”，再输入 `ERASE`。清除 app-owned 主/诊断 DB、媒体、报告、设置、secret、缓存、journal 与授权；外部 SAF/USB/云 provider 中的 `.mibk` 不属于 app-owned 本机数据，绝不删除。

## 状态与失败语义

UI 状态为 `REVIEW → CONFIRMING → ERASING → VERIFYING → COMPLETE | FAILED`；`COMPLETE` 仅是当前内存中的首启跳转，不是持久事件。确认后先耐久写 opaque marker，随后不可取消。每个 app-owned 类别删除并验证，诊断 DB/WAL/SHM 最后关闭删除；一旦开始诊断删除，logger 永不重开它。之后失败直接返回，重启凭 marker 在任何 DB 初始化前续跑。

成功只定义为所有类别及 marker 均不存在、没有 success event/diagnostics DB，并进入 first-run。失败只显示未确认清除类别和安全重试，不展示旧业务内容。确认前取消/拒绝及诊断仍存在时的失败可记录；确认后不得持久化成功。

## 验收

完整 fixture 包含多物业、租客、finalized/draft、媒体、双版 PDF、回执、诊断事件、secret 与未完成 restore journal。测试在每个删除边界杀进程并重启，证明 marker 续跑、诊断库不重建；真机清除后所有 app-owned 类别为空，重新选择外部 `.mibk` 仍可进入正常恢复预检。
