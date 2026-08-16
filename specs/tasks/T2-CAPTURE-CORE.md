---
id: T2-CAPTURE-CORE
title: 采集领域核：巡检生命周期状态机 + 房间粒度草稿自动保存仓储（:core）
depends_on: [T1-TEMPLATE-ENGINE]
parallelizable_with: [T2-ROUTINE-CONTENT, T2-PHOTO-PIPELINE, T2-PHRASELIB]
status: todo
branch: T2-CAPTURE-CORE
worktree: C:\wt\T2-CAPTURE-CORE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/capture/
  - android/core/src/test/kotlin/nz/myinspection/core/capture/
forbid:
  - 任何 Compose/Android import（:core 红线）
  - 绕过 finalized_at IS NULL 谓词的写路径
non_goals:
  - UI（T2-CAPTURE-UI）；finalize 事务（T3-FINALIZE）；历史对比查询（T3-HISTORY-COMPARE）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.capture.*"
dod_exit: 0
dod_assert: 建巡检（按类型解析 previous/baseline 双轨引用）→ 逐项置状态/备注 → 房间粒度保存 → 进程死亡模拟（新仓储实例）后恢复到同一房间进度，全链 JVM 测试绿；两级拍照规则完备性计算（哪些项还缺强制照片）测试绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T2-CAPTURE-CORE

## 产出
`core/capture`：InspectionRepository（建/读/写/房间粒度保存）+ 走查进度模型 + 拍照完备性规则 + 双轨基线解析。

## 上下文包（执行模型必读）
- **建巡检时解析双轨引用**（需求 §6）：previous_inspection = 同物业同类型时间上前一次；baseline_inspection = 该 tenancy 的 Ingoing（Exit 用；无 Ingoing 则空并标记「无基线」——Ingoing/Exit 配对约束的强提示归 T6，但字段语义本卡落定）。ANNUAL 的 previous = 上次年检。**澄清（2026-08-16 R3 仲裁）**：双轨引用对**所有**巡检类型统一解析入库——baseline 字段语义 = 创建时刻该 tenancy 基线的快照，「Exit 用」指 EXIT 是主要**消费者**、非唯一持有者；按类型条件置空不属本卡契约。
- **草稿自动保存粒度 = 房间**（需求 §5）：每完成一个房间的操作即持久化；模型上房间完成度 = 该房间全部项有状态 + ROOM_PANORAMA 照片已拍。进程死亡恢复 = 从 DB 重建进度（测试用新建仓储实例模拟）。
- **两级拍照规则**（讨论修正版）：房间级 ROOM_PANORAMA 1–2 张强制；项目级仅**不利发现**强制（租赁 FAIR/POOR；年检 MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT；**N_A 与 GOOD/NO_ISSUE 不逼拍**）。产出 `missingPhotos(inspection)` 完备性查询给 UI 与 finalize 校验共用。
- **不利发现强制备注**（TurboTenant 规则，[adopt·可否决]，调研要点 4）：status 为不利发现时 note 非空才算该项完成（短语库一点即满足，成本近零）；并入完备性查询 `missingNotes(inspection)`。
- **房间实例化**：建巡检时按模板 repeatable 房间 + 物业房型数实例化 room_instance（display_label 双语「Bedroom 2 / 次卧」）；走查/进度/历史全按 (room_instance, stable_id) 维度。
- 状态合法性：写 status 时按模板类型枚举校验（引擎 T1-TEMPLATE-ENGINE 提供 allowedStatuses）；wear_or_damage 仅 EXIT 且该项与 baseline 状态有差异时可写（差异计算本卡实现：按 stable_id 对齐比 status）。
- **物业级条目抑制**：建巡检实例化条目时按 property_item_override 排除 suppressed 项（「本物业不存在」永久生效，跨巡检——zInspector `Ø` 先例）；抑制/恢复用例本卡提供，UI 消费在 T2-CAPTURE-UI。完备性查询天然不含被抑制项。
- 时间注入 Clock 接口（合规引擎/测试同一纪律）；所有写经 SQLDelight 生成层（T1 的 finalized_at 谓词天然生效）。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max。难度 M。
