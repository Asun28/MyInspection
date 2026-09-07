---
id: T2-BULK-PHOTO-ASSIGNMENT
title: 批量照片选择、逐张分配与安全提交
status: todo
depends_on: [T7-SMOKE-POLISH, T2-MEDIA-ACCESS-BOUNDARY]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/bulk/
  - android/core/src/test/kotlin/nz/myinspection/core/media/bulk/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/mediaimport/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/mediaimport/
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当用户批量选择照片时，系统应只读取本次授权的来源，并为每张照片展示待分配状态；系统不应按文件名或 EXIF 自动确定物业、房间或检查项。"
  - "R2 只有当照片已经由用户明确分配给当前物业的一个有效目标时，系统才应允许提交该照片，并在提交时重查草稿与目标归属。"
  - "R3 当照片提交时，系统应复用单张导入的 EXIF、质量、隐私确认、哈希去重与补偿规则，复制来源而不移动或删除原图。"
  - "R4 如果发生拒权、低空间、解码失败、取消或进程死亡，则系统应按已确认的批次提交策略保留成功证据并明确未完成项，不把部分成功显示为全批成功。"
acceptance:
  - "A1 [R1] 多来源、撤回授权、未知元数据不发生自动归属或越界读取。"
  - "A2 [R2] 未分配、跨物业、目标已完成与重复动作被拒绝或幂等处理，不能产生错配关联。"
  - "A3 [R3] 同源不同档位、旋转照片和重复导入遵守原单张契约，源文件字节不变。"
  - "A4 [R4] 策略确定后对每阶段注入失败/取消/进程死亡，断言每张照片的最终 DB/文件状态和 UI 结果。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.bulk.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 批次策略和资源限额有决策记录；未分配/跨物业/已完成目标拒绝；每阶段失败/取消/重启准确保留每张终态，复制不改来源；V1.1 真机选择→分配→提交接线可走通
review_gate: codex {verdict:pass}
---

# T2-BULK-PHOTO-ASSIGNMENT

版本：V1.1，V1 无批量入口且不依赖本卡。用途是集中补录旧照片；现场拍摄和单张补录已覆盖 V1。

待澄清（本卡实现前）：逐张提交或全批原子提交、取消后的待分配内容是否保留、进程重启审核状态、照片数量/总字节上限。原需求“选 20 张”是例子，不是默认值或限额。现有单图安全上限继续适用，不凭空增加批次默认。

生产接线：本卡在既有导航注册 mediaimport 页面；入口只在 V1.1 提供，取消返回原证据项并恢复焦点。
