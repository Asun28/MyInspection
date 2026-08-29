---
id: T2-REPEATABLE-ROOM-RUNTIME
title: 偿还 TD26：重复房间实例化、完备性与历史基线统一到实例维度
depends_on: [T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T2-ROOM-REPEATABLE]
status: merged
branch: T2-REPEATABLE-ROOM-RUNTIME
worktree: C:\wt\T2-REPEATABLE-ROOM-RUNTIME
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/capture/
  - android/core/src/main/kotlin/nz/myinspection/core/finalize/
  - android/core/src/test/kotlin/nz/myinspection/core/capture/
  - android/core/src/test/kotlin/nz/myinspection/core/finalize/
  - android/core/src/test/kotlin/nz/myinspection/core/template/TemplateRoomSchemaTest.kt
  - configs/secrets/tracked-sensitive-allowlist.json
forbid:
  - 未经版本评审修改冻结 schema 或把 room count 仅存在 UI/内存
  - 用 stable_id 单键或未排序查询匹配重复房间历史
  - 旁路 :core 完备性规则在 UI 自算实例缺失
non_goals:
  - Compose 房间导航与项目卡片（T2-CAPTURE-UI）
  - ghost overlay 与历史条 UI（T3-HISTORY-COMPARE）
  - 自动从地址、照片或旧报告猜测房间数量
dod_command: cmd /c "android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests nz.myinspection.core.capture.* --tests nz.myinspection.core.finalize.* && android\gradlew.bat -p android --offline --no-daemon -q :core:check"
dod_exit: 0
dod_assert: 属性的重复房间数量经版本评审后可持久化；建巡检按模板房间序再按 instance_no 稳定实例化；B1/B2 同 stable_id 不同状态时 Exit B2 必与 B2 对齐且交换插入顺序结果不变；声明两间却缺 B2 时 finalize 拒绝
review_gate: codex {verdict:pass}
version_review: approved 2026-08-29 — schema v3→v4 adds property_room_config(property_id, room_key, instance_count 1..99); missing config means 1; migration is additive 3.sqm with audited databases/3.db; no UI or inferred counts
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 将 TD26 置 paid；同步 T2-CAPTURE-UI 与 T3-HISTORY-COMPARE 的已满足前提（R5）
---

# T2-REPEATABLE-ROOM-RUNTIME

## 产出
把重复房间从 schema 能力接到真实运行时：物业房间配置、巡检实例化、完备性、稳定排序及 previous/baseline 历史匹配都使用 `(room_key, instance_no, stable_id)`。

## 实现前版本评审
推荐默认是持久化 `property_room_config(property_id, room_key, instance_count)`，因为当前仓库没有可靠的派生来源。开始实现前必须提交该方案及迁移影响面供版本评审；若评审选择别的权威来源，应先修订本卡，不能静默换设计。

## RED 夹具
先写同一物业两间 Bedroom、相同 `stable_id` 但不同状态的失败测试，并交换基线插入顺序。当前固定 `instance_no=1` 的实现必须失败；随后再补缺实例的 finalize 失败测试。

## 验收
见 front-matter。该卡合并前，T2/T3 不得把重复房间 UI 或历史匹配上线。

## R5

PR #193 / master `6a92aa58` 已合并；第二轮 R3 pass。TD26 已偿还，下游 `T2-CAPTURE-UI` 与 `T3-HISTORY-COMPARE` 已同步为消费实例维度核心合同。
