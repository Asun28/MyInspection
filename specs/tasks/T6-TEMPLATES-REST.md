---
id: T6-TEMPLATES-REST
title: Ingoing / Exit / Annual 三模板内容 + Exit wear/damage 触发 + Ingoing↔Exit 配对约束
depends_on: [T2-ROUTINE-CONTENT, T3-HISTORY-COMPARE, T1-APP-BOUNDARY-ASSEMBLY]
parallelizable_with: [T6-HHC]
status: todo
branch: T6-TEMPLATES-REST
worktree: C:\wt\T6-TEMPLATES-REST
allow_paths:
  - data/templates/ingoing-v1.json
  - data/templates/exit-v1.json
  - data/templates/annual-v1.json
  - android/core/src/main/kotlin/nz/myinspection/core/capture/pairing/
  - android/core/src/test/kotlin/nz/myinspection/core/content/rest/
  - android/app/build.gradle.kts
  - android/app/src/test/kotlin/nz/myinspection/app/platform/composition/
forbid:
  - 改既有 routine-v1 的 stable_id（v1 定稿后 id 永不改）
non_goals:
  - 模板编辑器 UI（永不做）；whisper 语音（v2）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.content.rest.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug :app:verifyPackagedTemplateAssets
dod_exit: 0
dod_assert: 三模板过引擎校验+完备性测试（双语/唯一/photoRule/枚举按类型——ANNUAL 用 5 态）；INGOING 与 EXIT 条目 stable_id 对齐（Exit 差异计算的前提，测试断言两模板 stable_id 集合一致）；建 EXIT 时读取该 tenancy 的权威 baseline 指针；无有效基线时警告不阻断，已指定 Routine 时展示真实来源而不误报无基线
requirements:
  - "R1 当建 Exit 巡检时，系统应使用该租约显式选择且有效的 baseline；如果基线是 Routine，则系统应保留此来源并准确显示，不能当作缺失。"
acceptance:
  - "A1 [R1] 有效 Ingoing 与显式选定的已完成 Routine 均能作为该租约 baseline，Routine 来源被准确显示。"
  - "A2 [R1] 空指针与跨物业/租约的无效指针分别测试，不能被当作有效 baseline 静默选用。"
  - "A3 [R1] previous 与 baseline 不同的 fixture 证明 Exit 使用显式 baseline，不自动替换为最近 Routine。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T6-TEMPLATES-REST

## 产出
三份模板内容 + `core/capture/pairing`（Ingoing↔Exit 配对约束与无基线警告）。

## 上下文包（执行模型必读）
- **INGOING/EXIT 共享条目集**（stable_id 完全一致）：Exit 的 wear/damage 判定按 stable_id+room_instance 对齐该 tenancy 已选基线（T2-CAPTURE-CORE 已实现差异计算与仅差异项可写 wear_or_damage）；内容上 Ingoing 比 Routine 细（入住基线要密：含 keys/meters 类记录项——参考 docs/research/chapps.md Keys/Meters 模块，作为普通检查项建模，不建新表）。
- **ANNUAL**（自住年检，NZS 4306 思路 + Healthy Homes 日常复核点）：5 态枚举；条目偏维护视角（屋顶/排水/围护/水汽/结构走查），数量约 60–90 项。
- **配对约束**：读取 tenancy.baseline_inspection_id，校验同物业/同租约及 FINALIZED。Ingoing 是优先基线；既有在租可显式指定 Routine。真正缺失基线时警告不阻断；Routine 基线仅准确标注“Routine 基线／无入住记录”，不得标“无基线”或自动用最近 Routine。差异/缺项按现有对齐核心处理。
- 内容来源：routine-v1 为基（复用其房间骨架与措辞风格）+ 调研报告差异点；双模复核（Luna Max）强制，复核记录附 PR。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · medium；备选/复核 Luna Max。难度 M。

生产资产：沿用 T1-APP-BOUNDARY-ASSEMBLY 的构建期同步白名单加入三模板，并从 assembled APK/生产加载入口验证版本与 hash。修改范围只限同一模板交付，不能手工维护另一份 JSON。
