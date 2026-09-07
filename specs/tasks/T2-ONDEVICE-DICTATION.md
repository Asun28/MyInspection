---
id: T2-ONDEVICE-DICTATION
title: V2 可替换的离线听写适配
status: todo
depends_on: [T2-AUDIO-EVIDENCE]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/speech/
  - android/core/src/test/kotlin/nz/myinspection/core/speech/
  - android/app/src/main/kotlin/nz/myinspection/app/speech/
  - android/app/src/test/kotlin/nz/myinspection/app/speech/
  - docs/spike/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/capture/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/capture/
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当用户对已保存音频请求转写时，系统应通过可替换的设备端接口检查能力，结果应绑定原录音、目标检查项和本次操作。"
  - "R2 如果设备端识别或语言不可用，则系统应保留录音并提供预设选项与键盘输入，不得静默切换云识别。"
  - "R3 当转写成功时，系统应先展示供用户确认的文字；如果用户取消、目标切换或草稿失效，则迟到结果不应覆盖备注。"
  - "R4 当识别操作结束或取消时，系统应释放平台资源，并保留可重试的结果状态。"
acceptance:
  - "A1 [R1] mock 引擎替换不改录音存储/备注领域；目标设备的 app-owned 原件转写链有真机证据。"
  - "A2 [R2] 缺服务/缺离线语言/失败时无 HTTP 降级，音频哈希与可回放性不变。"
  - "A3 [R3] 并发回调、切项、取消、进程恢复和 finalize 后的迟到结果不能串项或覆盖已确认文字。"
  - "A4 [R4] 重复开始/停止和错误路径释放识别资源，测试与真机报告记录支持与不支持边界。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.speech.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 目标设备验证从已保存原件离线转写；缺模型/语言无联网降级；取消、切项、finalize 与迟到结果不覆盖备注；资源释放与原件 hash/回放保持测试通过
review_gate: codex {verdict:pass}
---

# T2-ONDEVICE-DICTATION

版本：产品 V2。`OnDeviceTranscriber` 接收已保存音频引用；不能以系统麦克风即时听写替代原件保存合同。

待澄清：实际系统 API 能否消费该原件，目标语言离线包、用户确认方式和许可/包大小预算。系统服务或本地模型均未被选定；不默认 whisper.cpp，不引入 SDK/模型直至可行性与许可确认。

生产接线：在前置录音控件接入明确的转写/确认动作；录音原件归属仍由前卡拥有，迟到回调不得越过目标/版本验证。
