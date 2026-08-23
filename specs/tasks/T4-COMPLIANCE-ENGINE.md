---
id: T4-COMPLIANCE-ENGINE
title: 配置驱动 NZ 合规引擎：阻断校验 API + Pacific/Auckland DST 边界测试（★规则 schema 冻结）
depends_on: [T1-SCHEMA-CORE]
parallelizable_with: [T3-PDF-RENDERER, T3-HISTORY-COMPARE, T5-BACKUP-IO]
status: todo
branch: T4-COMPLIANCE-ENGINE
worktree: C:\wt\T4-COMPLIANCE-ENGINE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/compliance/
  - android/core/src/test/kotlin/nz/myinspection/core/compliance/
  - configs/compliance/
forbid:
  - 规则硬编码进引擎（数值/窗口全来自配置，需求 §10）
  - 提供任何关闭/绕过开关（阻断不可关闭、不进设置——本身就是验收断言）
non_goals:
  - 通知文本生成与存档（T4-NOTICES）；work-check 用途的放行语义（ADR-0004：schema 留门、本版不启用）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.compliance.*"
dod_exit: 0
dod_assert: 需求 §10 全部规则各有正/反测试：4 周 Routine 限额（Ingoing/Exit 不计入·按类型分流）、通知提前量 ≥48h 且 ≤14d、时段 08:00–19:00（寄宿 18:00）、「双方同意也拦」场景；DST 转换日（NZ 9 月底进/4 月初出）边界用例绿；规则来自 configs/compliance/nz-rules-v1.json，改配置数值测试即变（引擎无字面量）
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；合并后 configs/compliance/ schema 视同契约登记 FrozenPaths（R5）
---

# T4-COMPLIANCE-ENGINE

## 产出
`core/compliance`：规则配置 schema + 加载器 + 校验引擎（`validateSchedule(...)` 返回 Pass 或 Blocked(reasons[]) 阻断结果）+ `configs/compliance/nz-rules-v1.json` 权威配置。

## 上下文包（执行模型必读）
- **规则清单（需求 §10 [定]，本版全部阻断级）**：
  1. 同物业 4 周内不得重复 ROUTINE（**法律上限非节奏建议**；INGOING/EXIT 不占限额——校验器按 type 分流）。
  2. 通知提前量 ≥ 48 小时且 ≤ 14 天（相对预定巡检时刻）。
  3. 巡检时刻落在 08:00–19:00；property.is_boarding_house = true 时 08:00–18:00。
  4. 最易踩场景写成显名测试：「上次发现问题、两周后回去复检」→ 拦下并给解释文案 key（即便双方同意）。
- **配置 schema（ADR-0004 冻结形态）**：{ schemaVersion, effectiveDate, sourceRefs[法条/tenancy.govt.nz 链接], timezone: "Pacific/Auckland", rules: { entryPurpose → { noticeMinHours, noticeMaxDays, visitWindow{...boardingHouse 变体}, frequencyLimit{days, exemptTypes[]} } } }。本版只有 entryPurpose="inspection" 条目；引擎按用途取规则。**留门已被证实必要**（调研 synthesis NZ 节）：s48(2)(cb) Healthy Homes 合规工作进入仅需 24h 通知（烟雾报警器工作同），s48(2)(c) 查验已约定维修另计——将来各自一条配置即可，代码零改。
- 时间纪律：DB 全 UTC 毫秒；**窗口/时段判定在配置时区**（java.time ZoneId "Pacific/Auckland"）；DST 测试锚定具体转换时刻（如 2026-09-27 02:00 进夏令时/2026-04-05 03:00 出——用 java.time 计算而非写死小时差）。提前量按真实时长（Duration），跨 DST 的 48h ≠ 挂钟 48h——以 Instant 差为准并写测试钉死该语义。
- 校验时机（消费者：建巡检/改期/T4-NOTICES 生成通知时）；引擎纯函数（Clock/规则注入），阻断结果带 reason key（UI/通知层译双语文案）。
- override 语义：数据目录 override 文件须 schemaVersion 匹配 + 校验和一致才生效，否则回退内置（ADR-0004 §3；加载器本卡实现，导入 UI 归后续微卡）。

## 验收 / 执行建议
dod 见 front-matter。首选 **Opus 5 · high**（法律边界卡，按难度上强档）；备选 DeepSeek V4 Pro；**Terra 交叉复核：规则夹具逐条对照需求 §10 原文**。难度 H。

## R3 round-cap 后续

PR #43 两轮 R3 后仍缺四类证据：非默认配置未跨过默认常量边界、改期没有当前记录身份、配置拒绝分支负例不完整、公开集合不可变包装缺少 mutation-sensitive 证明。按两轮上限停止扩张本卡；原 PR 保持开放并转人裁，后续由 `T4-COMPLIANCE-ENGINE-R3-CLOSURE` 精确收口，不重开已闭合的 DST、checksum、exact timezone 与阻断 reason 合同。
