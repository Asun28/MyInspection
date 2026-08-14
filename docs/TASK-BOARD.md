# TASK-BOARD — 任务/模型路由总表（v1 · 2026-08-14）

> **这张表管「谁做哪张卡、用什么档」**；每张卡的完整上下文包/验收在 `specs/tasks/<id>.md`；**状态以卡为准**（本表不追状态，防双源漂移）。
> 计划真相源 `_local/PLAN.md`；设计决策 `docs/adr/0001–0004`；需求 `docs/inspection-app-requirements.md`。
> 执行形态：每卡走 R1–R5（`scripts/task.ps1` start→ship），R3 评审恒 = **GPT-5.6 Sol**（`scripts/_config.ps1` 已钉；Sol 原则上不作同卡作者）。

## 模型席位（性价比路由原则）
| 席位 | 模型 | 用在 | 理由 |
|---|---|---|---|
| 工作马（默认） | **DeepSeek V4 Pro** | 规格清晰、契约已冻结的实现/测试/内容卡 | 最高性价比；卡内上下文包给足即可靠 |
| 设计/新颖单点 | **Opus 5** | 相机、PDF composer、加密格式、法律边界引擎 | 错误代价高或无先例可抄的卡 |
| 中档实现 | **Sonnet 5（max effort）** | 标准 Compose UI、Android 平台适配、本机装环境 | android 细节多但模式成熟 |
| 中档替补/只读消化 | **GPT-5.6 Terra** | WorkManager/通知类中档卡、大体量文档消化 | 分流 + 交叉视角 |
| 轻档内容/交叉复核 | **GPT-5.6 Luna Max** | 双语内容卡的第二双眼睛（抄录错编译不报错） | 便宜的独立复核 |
| 评审席（固定） | **GPT-5.6 Sol** | 全卡 R3 合并闸 + 安全面复核 | 已钉 `_config`；作者≠评审 |

效果档说明：effort 值为执行 harness 通用档（low/medium/high/max）；「难度」= 该卡对模型能力的真实要求（S 机械 / M 中档 / H 难 / H+ 最难）。

## 总表（25 卡 · 波次 = 依赖拓扑；同波卡可并行，allow_paths 互不重叠）
| 波 | 卡 id | 产出（一句话） | depends_on | 难度 | 首选模型 · effort | 备选 | 内容交叉复核 |
|---|---|---|---|---|---|---|---|
| W0 | T0-TOOLCHAIN | JDK17+SDK+`android/` Gradle 骨架空编译绿+verify/ci 收紧 | — | M | Sonnet 5 · max | Opus 5 | — |
| W1 | T1-SCHEMA-CORE ★ | SQLDelight 全 schema+UUIDv7+基线迁移+JVM 测试 | T0 | H | DeepSeek V4 Pro · high | Sonnet 5 max | 冻结前 Opus 5 抽审 |
| W1 | T1-SPIKE-PLATFORM | 真机可行性 ×4：overlay/离线听写/SAF/80 照 PDF 压力 | T0 | H | Opus 5 · max | Sonnet 5 max | —（人工真机验收） |
| W1 | T1-CANON-HASH ★ | canonical JSON+SHA-256+黄金向量 | T1-SCHEMA-CORE | H | DeepSeek V4 Pro · high | Opus 5 | Terra 对向量复算 |
| W1 | T1-TEMPLATE-ENGINE ★ | 模板 schema+加载器+stable-id/版本对齐+按类型枚举 | T1-SCHEMA-CORE | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W2 | T2-ROUTINE-CONTENT | Routine 双语模板 80–120 项+校验测试 | T1-TEMPLATE-ENGINE | S | DeepSeek V4 Pro · medium | Luna Max | **Luna Max 全文复核** |
| W2 | T2-PHOTO-PIPELINE | 照片存储/EXIF 转正(8 向)/哈希去重/导入 | T1-SCHEMA-CORE | M | Sonnet 5 · max | DeepSeek V4 Pro | — |
| W2 | T2-CAPTURE-CORE | 采集状态机+房间粒度草稿自动保存(:core) | T1-TEMPLATE-ENGINE | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W2 | T2-PHRASELIB | 双语短语库种子+数据接口 | T1-TEMPLATE-ENGINE | S | DeepSeek V4 Pro · low | Luna Max | Luna Max |
| W2 | T5-BACKUP-FORMAT ★ | 流式加密归档格式+manifest+防篡改/错口令测试 | T1-CANON-HASH | H+ | Opus 5 · max | Sonnet 5 max | Terra 对格式头复读 |
| W3 | T2-CAPTURE-UI | Compose 走查界面：大按钮/备注/短语/听写/两级拍照 | T2-CAPTURE-CORE,T2-PHOTO-PIPELINE,T1-SPIKE-PLATFORM | M | Sonnet 5 · max | Terra | — |
| W3 | T3-REPORT-COMPOSER ★ | 纯 Kotlin 布局引擎：分页/双语配对/哈希页脚+黄金布局树 | T1-CANON-HASH,T2-CAPTURE-CORE | H+ | Opus 5 · max | Sonnet 5 max | — |
| W3 | T3-FINALIZE | finalize 事务+只读强制+Supplement 哈希链 | T1-CANON-HASH | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W4 | T3-PDF-RENDERER | :app PdfDocument 渲染+CJK 字体+内存策略+双版本 | T3-REPORT-COMPOSER | H | Sonnet 5 · max | Opus 5 | — |
| W4 | T3-HISTORY-COMPARE | 历史条(上次状态/滑动)+ghost overlay 集成+双轨基线 | T2-CAPTURE-UI,T1-SPIKE-PLATFORM | H | Sonnet 5 · max | Opus 5 | — |
| W4 | T4-COMPLIANCE-ENGINE ★ | 配置驱动合规引擎+阻断 API+NZ DST 边界测试 | T1-SCHEMA-CORE | H | Opus 5 · high | DeepSeek V4 Pro | Terra 对规则夹具与需求逐条比对 |
| W4 | T5-BACKUP-IO | SAF 目的地+自动导出(WorkManager)+恢复先试跑后落刀 | T5-BACKUP-FORMAT | H | Sonnet 5 · max | Terra | — |
| W5 | T3-E2E-CORE | JVM e2e 闭环接 verify 闸门 2（$gate2Pending=false） | T3-FINALIZE,T3-REPORT-COMPOSER,T2-ROUTINE-CONTENT | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W5 | T4-NOTICES | 48h 通知双语文本+一键复制+送达存档(全文快照) | T4-COMPLIANCE-ENGINE | M | DeepSeek V4 Pro · high | Terra | Luna Max（通知文本） |
| W5 | T4-SCHEDULE | 13 周节奏提醒+本地通知 | T4-COMPLIANCE-ENGINE | S | GPT-5.6 Terra · medium | DeepSeek V4 Pro | — |
| W5 | T5-RETENTION | 租客数据保留期+一键清理 | T1-SCHEMA-CORE | S | DeepSeek V4 Pro · medium | Luna Max | — |
| W6 | T6-TEMPLATES-REST | Ingoing/Exit/Annual 内容+Exit wear/damage+配对约束 | T2-ROUTINE-CONTENT,T3-HISTORY-COMPARE | M | DeepSeek V4 Pro · medium | Luna Max | **Luna Max 全文复核** |
| W6 | T6-HHC | Healthy Homes 五项子模块+合规快照输出 | T3-PDF-RENDERER | M | DeepSeek V4 Pro · high | Terra | — |
| W7 | T7-REMEDIATION | LLM 建议：mock 优先+仅房东版+措辞边界+免责声明 | T3-PDF-RENDERER | M | Sonnet 5 · max | Opus 5 | Sol 安全面重点评审 |
| W7 | T7-SMOKE-POLISH | 真机全流程冒烟+微修捆绑（清单产出 docs/SMOKE-CHECKLIST.md） | 全部 MUST + T7-REMEDIATION（收官卡，不并行） | S | Sonnet 5 · medium | DeepSeek V4 Pro | — |

★ = 冻结点卡：合并后其产出登记 `scripts/_config.ps1` FrozenPaths，改动走版本评审。
并行窗口速查：W1 四卡并行；W2 五卡并行（BACKUP-FORMAT 提前入场）；W3–W5 各内部并行；关键路径 ≈ T0→SCHEMA→CANON→COMPOSER→PDF→E2E。

> **调研已回流**（docs/research/synthesis.md + 3 篇深挖）：官方 NZ 巡检表成为 Routine 模板骨架；二值主评级 UI（存储枚举不变）、照片隐私标记、物业级条目抑制、封面卷积/出处页脚等已并入相应卡上下文包；ghost overlay 确认为全品类空白（唯一差异化确认）。

## 待用户定（开工前 / 开工中可补）
1. **签认 ADR-0002**：备份设计推翻需求 §11 一处[定]（「数据目录指向云盘同步文件夹」在 Android 不成立 → SAF 加密导出）。3 方一致，但[定]的变更须你点头。
2. **房产现状**（需求问了两次没答案）：现有几套？自住还是出租？→ 出租中 ⇒ Ingoing 模板优先级提到 Routine 前（基线补不回来）。
3. **租客数据保留期**：建议「租约结束后 12 个月，可一键清理」（Privacy Principle 9 只说 not longer than necessary，无固定期限）。定个数即可。
4. **年检评级 5 态**：Codex 修正——3 档分不出「查过且健康」与「没查」，改为 NO_ISSUE/MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT/N_A。默认按 5 态做，可否决。
5. **s48(2)(c) 复检语义**（不阻塞开工）：向 Tenancy Services/持牌人士确认「查验已约定维修」是否占 4 周限额；确认后只改配置（ADR-0004）。
6. **Remediation 用哪家 LLM/key**（T7 前定即可；接口做成 provider 可换）。
7. （可选·调研启发）**Condition/Cleanliness 全量双刻度**（Inventory Hive 模式；法律依据 wear-tear 适用状况不适用清洁）——v1 按已定单刻度 + Exit/Ingoing 房间级清洁条目；要全量双刻度请在 T1-SCHEMA 冻结前说。
8. （可选·调研启发）缺陷**责任方/费用字段**（Landlord/Tenant/Investigate + 金额，全场竞品标配）——v1 只有 Exit 的 wear_or_damage 三态；要费用卷积请说（影响 schema+composer）。

## 已由 3 方讨论定稿（原[待] → 已定）
技术栈原生 Kotlin+Compose（ADR-0001）· 租赁评级 4 档 · Exit 独立 wear/damage 三态且仅差异项 · 两级拍照规则（N_A 不逼拍照）· UI 英文单语 + 报告平行双语 · finalize 锁定+哈希页脚 · SQLDelight/自研 UUIDv7/canonical 规范（ADR-0003）。
