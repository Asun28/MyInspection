---
id: T2-MEDIA-ACCESS-BOUNDARY
title: 媒体能力收窄与路径校验复用
status: todo
depends_on: [T1-LOCAL-DATA-SECURITY, T2-PHOTO-PIPELINE, T2-PHOTO-PROPERTY-DEDUPE]
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/test/kotlin/nz/myinspection/app/media/
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/core/src/main/kotlin/nz/myinspection/core/report/pdf/PdfArtifactPaths.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当业务调用媒体读写时，系统应只接受由领域校验生成的归属引用和有界来源句柄，UI 不得传入任意相对路径或删除目录。"
  - "R2 当照片发布或清理时，系统应在实际使用边界复核归属与引用保护，并保留 no-follow、no-overwrite、lease 和失败补偿。"
  - "R3 当相机与导入使用公共工作流时，系统应保留相机最终 JPEG 哈希与导入原始 source 哈希的区别；公共路径片段校验应保留各资源命名空间约束。"
acceptance:
  - "A1 [R1] 未验证引用、路径穿越、跨物业 ID 与过期目标不能读写另一物业；公开业务接口不能任意调用 discardIn。"
  - "A2 [R2] 真实临时目录验证重复发布、回滚、被引用文件与符号链接保护；宿主不支持的检查必须记录未验证，并在支持目标补证据。"
  - "A3 [R3] 相机/导入均复用 VerifiedAssetWorkflow；同源不同质量仍保持导入去重身份，独立 hash oracle 不共用被测实现；路径校验抽取前后用同一合法/非法矩阵验证。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 媒体真实目录测试证明归属/穿越/跨物业拒绝、no-follow/no-overwrite/lease/引用保护；相机与导入 hash 语义不变；MediaPaths/PdfArtifactPaths 共享校验矩阵通过，符号链接跳过明确留待支持目标补证据
review_gate: codex {verdict:pass}
---

# T2-MEDIA-ACCESS-BOUNDARY

版本：V1。宽文件 API 是未来误用风险，本卡不宣称已经存在越权漏洞。优先用 internal/private 收窄 adapter，再提供 `EvidenceCommands` 所需能力。

`MediaPaths` 与 `PdfArtifactPaths` 的公共片段校验可提取为内部工具；物业归属、照片/报告命名空间仍各自拥有。六条路径是单一媒体边界及其复用调用方，不扩大到整个报告重构。冻结 canonical/backup 代码不随相似 hash 代码改写；SafeLog 迁移由前置安全卡拥有。
