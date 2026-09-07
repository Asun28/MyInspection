---
id: T2-AUDIO-EVIDENCE
title: V2 原始录音证据、回放与归属
status: todo
depends_on: [T7-SMOKE-POLISH, T2-MEDIA-ACCESS-BOUNDARY]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/audio/
  - android/core/src/test/kotlin/nz/myinspection/core/media/audio/
  - android/app/src/main/kotlin/nz/myinspection/app/media/audio/
  - android/app/src/test/kotlin/nz/myinspection/app/media/audio/
  - docs/spike/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/capture/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/capture/
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当用户在可编辑检查项显式开始录音时，系统应先检查设备能力和按需权限，并将录音归属绑定该项。"
  - "R2 当一段录音成功保存时，系统应保留原始音频、完整性信息和归属，并允许回放；后续识别结果不得替代或删除原始证据。"
  - "R3 如果权限、存储、录制或生命周期失败，则系统应显示明确状态并保持预设选项与键盘可用，不能把不完整文件标为成功。"
  - "R4 在检查已完成时，系统应禁止改写已有音频证据，且报告不得嵌入音频。"
acceptance:
  - "A1 [R1] 真机报告记录系统/机型/录音能力/权限拒绝及恢复，UI 不自动请求麦克风。"
  - "A2 [R2] 保存后关闭重开、哈希核验和回放成功，重复录制追加而不覆盖，识别失败不丢原件。"
  - "A3 [R3] 每个录音阶段注入取消、进程死亡、低空间和卷失联；按批准策略处置未完成片段，无伪成功或孤儿关联。"
  - "A4 [R4] 已完成证据不可改，既有备份音频往返不回归，两种报告不泄露音频引用。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.audio.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 真实保存后重开 hash 相等且回放成功、录音追加不覆盖、finalized 改写拒绝；真机系统/机型/权限及各失败路径有证据；未完成片段按批准策略处理，原件不因转写失败删除
review_gate: codex {verdict:pass}
---

# T2-AUDIO-EVIDENCE

版本：产品 V2；V1 不实现 app-owned 麦克风/听写，不修改已冻结音频字段，不清除已有音频。

先验证 app-owned 录音能保存原件，再接 `T2-ONDEVICE-DICTATION`。待澄清：目标手机与语言、容器/编码、时长/字节上限、录制取消与未完成片段保留策略。任何需 schema 变动的发现先独立版本评审；本卡不得越过冻结边界。

生产接线：在 V2 capture 证据项接入录制/停止/回放，沿用既有页面生命周期。路径覆盖录音领域、Android adapter、入口与各自测试，是同一录音交付；不顺手重构其他 capture 页面。
