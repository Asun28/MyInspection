---
id: T1-TEMPLATE-ENGINE
title: 模板 JSON schema + 加载器 + stable-id/版本对齐 + 按类型枚举校验（★冻结点）
depends_on: [T1-SCHEMA-CORE]
parallelizable_with: [T1-CANON-HASH]
status: todo
branch: T1-TEMPLATE-ENGINE
worktree: C:\wt\T1-TEMPLATE-ENGINE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/template/
  - android/core/src/test/kotlin/nz/myinspection/core/template/
  - android/core/build.gradle.kts
  - data/templates/README.md
forbid:
  - 碰 canon/ 包（并行卡领地）
  - 模板编辑器 UI（硬边界永不做）
non_goals:
  - 真实模板内容（T2-ROUTINE-CONTENT / T6-TEMPLATES-REST；本卡只带一个最小 fixture 模板）
  - 采集状态机（T2-CAPTURE-CORE）
  - 房间定义与 repeatable 标记（拆入 T2-ROOM-REPEATABLE：持久化它须改已冻结的 sqldelight/ ——新 .sqm + 版本评审，且须先还清 TD4；只加 JSON 字段而不入库会造出「入库静默丢字段」路径。见下「评审仲裁」段，已人裁定案）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.template.*"
dod_exit: 0
dod_assert: fixture 模板加载→校验→入库（template_version + check_item_def）往返绿；stable_id 重复/枚举越界/缺双语文案三类坏 fixture 被拒且错误信息点名条目；版本升级对齐规则（同 stable_id 改文案=沿用、新增项=新 id）有测试
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；合并后模板 JSON schema 视同契约（改=版本评审）（R5）
---

# T1-TEMPLATE-ENGINE

## 产出
模板 JSON schema（kotlinx.serialization 数据类即 schema）+ 加载/校验/入库器 + 版本对齐规则 + `data/templates/README.md`（内容作者指南，给 T2/T6 内容卡用）。

## 上下文包（执行模型必读）
- **模板 JSON 形态**（一类型一文件，如 `routine-v1.json`）：
  `{ type, version, items: [ { stableId, area, room, textEn, textZh, allowedStatuses[], photoRule } ] }`
  - stableId：模板内唯一、跨版本恒定（改措辞不改 id；新增项给新 id——需求 §4 硬性要求）；建议形如 `KIT-BENCH-01`（房间缩写-对象-序号），只作可读性，不承载语义。
  - allowedStatuses 按类型：ROUTINE/INGOING/EXIT → GOOD/FAIR/POOR/NOT_APPLICABLE；ANNUAL → 5 态（见 T1-SCHEMA-CORE 上下文包）。
  - photoRule：ROOM_PANORAMA（房间级 1–2 张全景强制）/ ADVERSE_ONLY（项目级：状态为不利发现时强制——**N_A 不逼拍照**，讨论修正）。
  - 房间定义带 `repeatable` 标记（如 BEDROOM）：建巡检时按物业实例化为 room_instance（Bedroom 1..N）——**房间是实例不是模板常量**（调研要点 6，schema 已有 room_instance 表）；非 repeatable 房间恒一实例。
- 加载路径：`:core` 纯 JVM 读 InputStream（测试喂 fixture；:app 侧后续用 assets 打开——本卡不做 android 侧）。`android/core/build.gradle.kts` 允许动：只为把 `data/templates/` 注册为测试 resources srcDir（构建期拷贝），别的不改（依赖 T0 已 pin）。
- 校验器错误要可指认（条目 stableId + 缺什么），内容卡的 DoD 就靠它当闸。
- 版本对齐：`alignHistory(old: TemplateVersion, new: TemplateVersion)` 返回 stable_id 交集/新增/移除清单——历史对比（T3-HISTORY-COMPARE）按 stable_id 对齐的基础设施。
- content_hash：模板文件字节 SHA-256，入 template_version 表（防「同版本号不同内容」静默漂移）。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max。难度 M。

## 评审仲裁：房间 `repeatable` 标记不在本卡（R3 第 2 轮）
上下文包里「房间定义带 `repeatable` 标记」这一句**不落在本卡**，独立成 `T2-ROOM-REPEATABLE`。R3 第 1 轮该评审者自己判它为 `[FOLLOW-UP]`（理由即「可能需要改动本卡 allow_paths 之外、且已冻结的持久层 schema」），第 2 轮升级为 block；仲裁维持第 1 轮判断，理由：

- **存不下**：`check_item_def` 只有 item 级 `room` 列，无房间定义表、无 repeatable 列；`sqldelight/` 自 `fcdc88d` 起冻结，加表/加列须走新 `.sqm` + 版本评审——在本卡 `allow_paths` 之外，且 TD4 未还清前无从校验迁移。
- **半落地更坏**：只往模板 JSON 加 `rooms[]` 而不持久化，等于在一张**冻结点卡**里新造一条「入库静默丢字段」的路径——正是 T1-SCHEMA-CORE 用 17 轮清掉的缺陷类。
- 本卡 front-matter 的 `dod_assert` 未包含 repeatable；按 rubric §0「按卡片自己声明的 DoD 判」，它不构成本卡的验收缺口。

本卡上下文包给出的模板 JSON 形态 `{ type, version, items: [...] }` 也没有 `rooms[]` 段——这与上述仲裁一致。

## 实现说明：`build.gradle.kts` 的允许项未动用
上下文包允许改 `android/core/build.gradle.kts`「只为把 `data/templates/` 注册为测试 resources srcDir」。本卡未动用该允许：本卡 `allow_paths` 在 `data/templates/` 下只放得了 `README.md`，注册后该 srcDir 里没有任何模板文件可供测试加载，等于落一段没有测试盯住的构建配置。fixture 因此内联在测试源码里（每个坏 fixture 只与好 fixture 差一处，断言面恰好等于被测规则）。
**后果需下游承接**：`T2-ROUTINE-CONTENT` 要写 `data/templates/routine-v1.json` 并在 `core/content/` 测试里加载它，而它的 `allow_paths` 不含构建文件——开卡时须补 `android/core/build.gradle.kts`（注册 srcDir），或在卡内明确用相对路径读取。
