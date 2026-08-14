---
id: T6-TEMPLATES-REST
title: Ingoing / Exit / Annual 三模板内容 + Exit wear/damage 触发 + Ingoing↔Exit 配对约束
depends_on: [T2-ROUTINE-CONTENT, T3-HISTORY-COMPARE]
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
forbid:
  - 改既有 routine-v1 的 stable_id（v1 定稿后 id 永不改）
non_goals:
  - 模板编辑器 UI（永不做）；whisper 语音（v2）
dod_command: cmd /c android\gradlew.bat --offline --no-daemon -q :core:test --tests "nz.myinspection.core.content.rest.*"
dod_exit: 0
dod_assert: 三模板过引擎校验+完备性测试（双语/唯一/photoRule/枚举按类型——ANNUAL 用 5 态）；INGOING 与 EXIT 条目 stable_id 对齐（Exit 差异计算的前提，测试断言两模板 stable_id 集合一致）；建 EXIT 无该 tenancy Ingoing 基线时产出强警告标记（不阻断——「在租补不回来，下租客补上」需求 §3）
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T6-TEMPLATES-REST

## 产出
三份模板内容 + `core/capture/pairing`（Ingoing↔Exit 配对约束与无基线警告）。

## 上下文包（执行模型必读）
- **INGOING/EXIT 共享条目集**（stable_id 完全一致）：Exit 的 wear/damage 判定按 stable_id+room_instance 对齐 Ingoing（T2-CAPTURE-CORE 已实现差异计算与仅差异项可写 wear_or_damage）；内容上 Ingoing 比 Routine 细（入住基线要密：含 keys/meters 类记录项——参考 docs/research/chapps.md Keys/Meters 模块，作为普通检查项建模，不建新表）。
- **ANNUAL**（自住年检，NZS 4306 思路 + Healthy Homes 日常复核点）：5 态枚举；条目偏维护视角（屋顶/排水/围护/水汽/结构走查），数量约 60–90 项。
- **配对约束**（需求 §3 关键约束）：Exit 报告在无 Ingoing 基线时证据价值骤降——建 EXIT 时 pairing 检查该 tenancy 是否有 FINALIZED 的 INGOING：无 → 返回 Warning（UI 醒目提示 + 报告封面标注「无入住基线」），**不阻断**。
- 内容来源：routine-v1 为基（复用其房间骨架与措辞风格）+ 调研报告差异点；双模复核（Luna Max）强制，复核记录附 PR。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · medium；备选/复核 Luna Max。难度 M。
