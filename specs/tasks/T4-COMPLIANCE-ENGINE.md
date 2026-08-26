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
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试；
  # 清单外的缺口记 [FOLLOW-UP] 开新卡，不在本卡 block（上游提案 Asun28/claude-devops-scaffold#203）。
  - "A1 4 周内不得重复 ROUTINE：正例（第 29 天放行）+ 反例（第 27 天拦下）各一"
  - "A2 通知提前量 ≥ 最小值：正例 + 反例（差 1 分钟）各一"
  - "A3 通知提前量 ≤ 最大值：正例 + 反例（超 1 分钟）各一"
  - "A4 巡检时刻落在普通时段；is_boarding_house=true 走寄宿时段：两组各正反例"
  - "A5 类型分流：INGOING/EXIT 不占 ROUTINE 频次额度（显名测试）"
  - "A6 显名场景「上次发现问题、两周后回去复检，双方已同意」→ 仍拦下并给 reason key"
  - "A7 DST 边界：进/出夏令时两个转换日各一用例，时刻由 java.time 计算而非写死小时差"
  - "A8 提前量按 Instant 真实时长判定：跨 DST 的 48h ≠ 挂钟 48h，用例钉死该语义"
  - "A9 数据驱动自证：所有规则用例的配置值须与内置默认不同且跨过默认边界（如最小提前量配 72h 而非 48h），使「引擎硬编码任一默认常量」至少让一条用例变红"
  - "A10 改期身份：ScheduleRequest 携带被编辑条目的 id，该条目不与自身冲突；另有一条真正竞争条目时必须冲突（两条用例）"
  - "A11 加载器拒绝集（各一条负例，且断言具体拒绝理由而非仅抛异常）：内置 schema 非法、effectiveDate 格式错、时区未知、sourceRefs 空、sourceRefs 重复、rules 空、entryPurpose 非法、最大值非正、频次天数非正、最小值 > 最大值、豁免类型重复或未知、时段起止倒置、UTF-8 非法、JSON 未知字段"
  - "A12 override 文件 schemaVersion 不匹配或校验和不一致 → 回退内置配置（两条负例）"
  - "A13 引擎与加载器交出的集合（sourceRefs / rules / exemptTypes / 校验错误 / blocked reasons）为不可变视图：各配一条「强转 MutableList/MutableMap 后改写」用例证明拒绝修改"
  - "A14 不存在任何关闭/绕过开关：断言公开 API 面无 disable/skip/force 形态参数"
  - "A15 阻断结果带稳定 reason key（供 UI/通知层译双语），key 集合由测试钉死"
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
