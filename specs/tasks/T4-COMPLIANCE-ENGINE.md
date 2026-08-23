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
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  # 每条都是**规范性**要求：无论当前实现是否已满足，都须有可证伪测试钉住。
  # 边界方向按需求 §10 的原文判定（「≥ 48 小时且 ≤ 14 天」「08:00–19:00」都是**闭区间**）：
  # 端点值本身合法，越界的是端点再往外一个最小刻度那一侧。写反了会让正确的引擎测不过，
  # 而让它变绿最便宜的改法就是把引擎改错——这类条目本身即缺陷源。
  - "A1 通知下限精确边界：权威 JSON 的 48h 下，恰好 48h00m00.000s 放行、48h 减 1 毫秒以 NOTICE_TOO_SHORT 阻断，两例均断言 reasons 的 key 序列精确等于单元素列表"
  - "A2 通知上限精确边界，钉住「≤ 14 天」的**包含**语义：恰好 Duration.ofDays(14) 的真实时长放行、14 天再**减** 1 毫秒同样放行、14 天再**加** 1 毫秒才以 NOTICE_TOO_EARLY 阻断。两个放行例断言结果恰为 ScheduleValidation.Pass，阻断例断言 reasons 的 key 序列精确等于单元素 [NOTICE_TOO_EARLY]。变异判据：把引擎的 `notice > Duration.ofDays(...)` 改成 `>=`，「恰好 14 天放行」那例必红——本条的**方向**是它自己的验收面，因为一份要求「14 天减 1 毫秒被拦」的清单会把合法的 14 天通知判成违规，而那正是需求 §10 明文允许的"
  - "A3 普通时段四点：Pacific/Auckland 本地 08:00:00 与 19:00:00 放行、07:59:59.999 与 19:00:00.001 以 OUTSIDE_VISIT_WINDOW 阻断，四点各一条断言，不用整体循环掩盖单点。两侧下界都取**相邻毫秒**：07:59:59 这种整秒下界会被带亚秒松弛的守卫（如 isBefore(start.minusMillis(500))）放过"
  - "A4 寄宿时段三点：isBoardingHouse=true 时 18:00:00 放行、18:00:00.001 阻断；同一个 18:00:00.001 时刻在 isBoardingHouse=false 下必须放行，以证明关闭时刻取自 boardingHouseEnd 分支而非同一个常量"
  - "A5 频率精确边界与同意不放行：同物业同用途的前一条 ROUTINE 相距 27 个民历日以 FREQUENCY_LIMIT 阻断（tenantConsented=true 时同样阻断且 reasons 恰为单元素 FREQUENCY_LIMIT）、相距 28 个民历日放行"
  - "A6 类型分流双向：INGOING/EXIT 作为待排请求时不被 27 日内的 ROUTINE 历史阻断；同两型作为历史记录时也不阻断 27 日内的 ROUTINE 请求；ROUTINE 两侧均计入。**ANNUAL 不在豁免集内**（见 A18），故它两侧都计入——本条与 A18 必须同时成立，任何把 ANNUAL 写回豁免侧的改动都会让 A18 红"
  - "A7 非默认通知上下限：noticeMinHours=72 且 noticeMaxDays=7 的配置下，48h 被 NOTICE_TOO_SHORT 阻断、72h 放行、10 天被 NOTICE_TOO_EARLY 阻断，三例都跨过 48/14 这两个默认常量。**注入机制**：配置一律用 `ComplianceConfig` 的 internal 构造器直接建，不改 configs/compliance/nz-rules-v1.json、也不靠 override 文件（A7–A10、A15 同此）"
  - "A8 非默认时窗：start=09:00/end=17:00/boardingHouseEnd=16:00 的配置下，08:30 与 18:00 被 OUTSIDE_VISIT_WINDOW 阻断、17:00 放行、同一 17:00 在寄宿模式被阻断，四例都跨过 08:00/19:00/18:00；配置由 internal 构造器直接建"
  - "A9 非默认频率天数：frequencyDays=42 的配置下，相距 30 个民历日的 ROUTINE 历史仍以 FREQUENCY_LIMIT 阻断（30 已越过默认 28）；配置由 internal 构造器直接建"
  - "A10 非默认豁免集合：exemptTypes=[\"ROUTINE\"] 的配置下，27 日内的 ROUTINE 历史不再阻断 ROUTINE 请求，而 27 日内的 INGOING 历史反过来阻断 INGOING 请求，两例合起来证明豁免集合读自配置而非 ComplianceEngine 里的字面量；配置由 internal 构造器直接建"
  - "A11 时区读自配置：用 ComplianceConfig 的 internal 构造器直接建一条 timezone=ZoneId.of(\"UTC\") 的配置，取一个两时区判定相反的 Instant（如 2026-08-19T20:30Z，UTC 下 20:30 越窗、Pacific/Auckland 下为次日 08:30 在窗内），断言 UTC 配置阻断而 Pacific/Auckland 配置放行；不得为此放宽加载器的 v1 时区常量校验"
  - "A12 DST 双向语义：入夏 2026-09-27 与出夏 2026-04-05 各锚一组 Instant，先用 assertEquals 钉死 Duration.between 分别是 47h 与 49h，再断言挂钟 48h 在入夏侧被 NOTICE_TOO_SHORT 阻断、出夏侧放行；频率侧用 2026-09-01T10:00 到 2026-09-29T10:00 钉死真实 671h 但仍算满 28 民历日而放行"
  - "A13 改期身份四例：同一份历史、同一目标时刻下——(1) 不指名 currentEntryId 时以 FREQUENCY_LIMIT 阻断；(2) 指名被改那一行时放行；(3) 同物业同用途的另一条真实记录仍以 FREQUENCY_LIMIT 阻断；(4) currentEntryId 指向**不存在**的 id 时以 **INVALID_HISTORY_ENTRY** 阻断、reasons 恰为该单元素——不是 FREQUENCY_LIMIT：一个匹配到零行的 id 不是改期，此时整份历史的身份已不可信，频率比较根本不该跑（它连『被改的是哪一行』都答不出）"
  - "A14 身份完整性 fail closed：existingEntries 含空串 entryId 或重复 entryId 时以 INVALID_HISTORY_ENTRY 阻断；currentEntryId 非空却匹配到多于一行时必须阻断而非静默排除多行（反例锚定：两条空 id 的 ROUTINE 相隔 2 天 + currentEntryId=\"\" 必须**不**放行）"
  - "A15 历史用途未知即 fail closed：history 行的 entryPurpose 不在 config.rules 键集内时以 INVALID_HISTORY_ENTRY 阻断；另有一例用已配置的第二个用途证明「同物业不同用途」仍正常跳过——两例必须分属不同测试，不得共用一个既未配置又用作『其它用途』的夹具。**注入机制**：权威 JSON 只有 `inspection` 一个用途键，第二个用途取不到，须用 `ComplianceConfig` 的 internal 构造器另配一条 rules 键"
  - "A16 拒绝分支逐条可证伪：built-in schemaVersion、effectiveDate、未知时区、非 v1 时区、sourceRefs 空、sourceRefs 重复、rules 空、purpose 非法、noticeMinHours/noticeMaxDays/frequencyLimit.days 非正、min 超 max、exemptTypes 重复/未知、时刻非 HH:mm、普通窗倒置、寄宿窗宽于普通、寄宿窗倒置，每例断言 ComplianceConfigException.errors **包含该分支自己的诊断串**（不得只断言 errors 非空），**且这些诊断串两两不同**——两条分支共用一串时，删掉其中一条会由兄弟分支在同一夹具上补位，没有任何测试变红。sourceRefs 的逐条校验有**五个子条件**（含 ISO 控制符 / URI 不可解析 / 非 https / 空 host / 带 userInfo），各配一枚**只触发该子条件**的负例，除诊断串外再断言 **errors 的元素个数**与该条目的下标前缀（形如 `sourceRefs[1]:`）——个数断言是防补位的第二道：诊断串一旦被合并成同一串，仅靠「包含」就区分不出五者，并各配一枚删除该子条件的变异；另加一例**同一 JSON 对象内的重复键**，断言 errors 含点名该键名的诊断（权威 override 的 SHA-256 认证的是整份字节，重复键让『字节说什么』落进信任边界内）。坏 UTF-8 以 CharacterCodingException 精确失败、未知 JSON 键以 SerializationException 精确失败"
  - "A17 override 三态与集合不可变：checksum 不符得 CHECKSUM_MISMATCH、schemaVersion 不符得 SCHEMA_VERSION_MISMATCH、其余非法得 INVALID_CONFIG，三例各断言具体枚举值且回退后的数值仍是 built-in 的；sourceRefs、rules、exemptTypes、ComplianceConfigException.errors、ScheduleValidation.Blocked.reasons 五处各做 MutableList/MutableMap cast 后的 add/remove/put/clear，断言 UnsupportedOperationException 且原值不变"
  - "A18 exemptTypes 与需求 §10 对齐：断言 configs/compliance/nz-rules-v1.json 解析后 rules[\"inspection\"].frequencyLimit.exemptTypes **精确等于** [\"INGOING\", \"EXIT\"]（需求 §10 只豁免这两型；ANNUAL 是本仓的模板类型、不是法定豁免，故从权威配置移除——用户已裁），且一条 27 民历日内的同物业同用途 ROUTINE 历史使 **ANNUAL 请求**以 FREQUENCY_LIMIT 阻断。configs/compliance/ 在本卡 allow_paths 内，此改动属本卡"
  - "A19 阻断不可绕过（`forbid` 那条点名『本身就是验收断言』的面）：用反射断言 `ComplianceEngine` 的公开成员恰好是「接一个 ComplianceConfig 的构造器 + `validateSchedule(request: ScheduleRequest): ScheduleValidation`」，再无第二个公开函数或可写属性（areTooClose / blocked 等一律 private）；同样用反射断言 `ScheduleRequest` 主构造器形参的**完整集合**（名字 + 类型）精确等于 {propertyId, entryPurpose, inspectionType, isBoardingHouse, scheduledAt, noticeGivenAt, tenantConsented, existingEntries, currentEntryId} 九个——是**完整集合而非名字黑名单**，任何新增的可空/布尔形参都会让它红（黑名单只要改个参数名就绕过，那正是第 2 轮打回粗清单的理由）。变异：给 validateSchedule 或 ScheduleRequest 加一个 `bypass: Boolean = false`，反射断言必红"
  - "A20 reason key 集合封闭：断言 `ComplianceReasonKey.entries` 精确等于有序列表 [UNKNOWN_ENTRY_PURPOSE, UNKNOWN_INSPECTION_TYPE, INVALID_PROPERTY_ID, INVALID_HISTORY_ENTRY, NOTICE_TOO_SHORT, NOTICE_TOO_EARLY, OUTSIDE_VISIT_WINDOW, FREQUENCY_LIMIT]；八个 key 每个都须有条目认领，其中前三个各配一枚负例——UNKNOWN_ENTRY_PURPOSE（entryPurpose 不在 config.rules 键集）、UNKNOWN_INSPECTION_TYPE（inspectionType 不在支持集 {ROUTINE, INGOING, EXIT, ANNUAL}）、INVALID_PROPERTY_ID（空白 propertyId），各断言 reasons 恰为该单元素；新增一个枚举值即让第一条红"
  - "A21 变异证明：A1–A20 每条守卫各配一枚**单句删除/反转**变异，跑一遍并把「变异 → 哪一条断言变红 → 失败文本」的对照表贴进 PR body；任一条守卫找不到能杀死它的单句变异，即视为该条未完成。判据按 L165 的分类器：**非零退出且命中指定断言文本**才算击杀，仅凭非零不算（非零也可能来自靶未命中/语法坏/更早的闸抢先中断）"
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
