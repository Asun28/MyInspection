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

## 总表（32 卡 · 波次 = 依赖拓扑；同波卡可并行，allow_paths 互不重叠）
| 波 | 卡 id | 产出（一句话） | depends_on | 难度 | 首选模型 · effort | 备选 | 内容交叉复核 |
|---|---|---|---|---|---|---|---|
| W0 | T0-TOOLCHAIN | JDK17+SDK+`android/` Gradle 骨架空编译绿+verify/ci 收紧 | — | M | Sonnet 5 · max | Opus 5 | — |
| W0 | T0-GATE-HARDENING | 许可闸递归发现+verify 确定性+两枚闸门自测（拆自 T0-TOOLCHAIN） | T0-TOOLCHAIN | M | Sonnet 5 · max | DeepSeek V4 Pro | —（事后 R3 block ×2 → 见 T0-GATE-FIXFORWARD） |
| W0 | T0-HARNESS-PERF | 横切优化 selftest 与 CI 墙钟时间（约 300 行 harness 改动） | T0-GATE-HARDENING | M | Sonnet 5 · max | DeepSeek V4 Pro | — |
| W0 | T0-GATE-FIXFORWARD | 许可闸路径比较改 OS 感知 + 发布清单收敛为单一解锁路径 | T0-GATE-HARDENING | M | Sonnet 5 · max | DeepSeek V4 Pro | —（**三张 T0 卡共用 selftest.ps1，须串行**：HARNESS-PERF → 本卡 → LICENSE-SCANNER） |
| W0 | T0-LICENSE-SCANNER | Gradle 逐坐标许可机检（偿还 TD2，落地即解锁发布阻断项） | T0-GATE-HARDENING | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W0 | T0-DEBT-TASK-INVENTORY | 移除 CLAUDE.md 易漂移的静态任务卡库存数（偿还 TD21） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `53eebf9`，PR #13；当前阶段改用活卡/归档真相源，无静态库存；句级分类器覆盖同栏历史计数、`cardboard` 边界与 `active cards`，Sol R3 pass；TD22 另卡） |
| W0 | T0-DEBT-SEEDED-CLOSURE-SCOPE | 让 17cc 变异闭包显式携带断言器（偿还 TD23；TD21 再审前置） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `39ea794`，PR #14；独立 worktree，真实 A/B mutation 在外层 helper 解绑后仍保持 exact marker/exit/control/SHA，Sol R3 pass；未混入 TD21） |
| W0 | T0-DEBT-CASE-PROBE-CLOSURE-SCOPE | 让 17cc case mutation 闭包显式携带探针函数（偿还 TD25） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `b7efd94`，PR #15；File/Command 两宿主 seeded 全绿，删除 capture 与恢复裸调用均命中专属 TD25 诊断，Sol R3 pass） |
| W0 | T0-DEBT-R3-CARD-BASELINE | 让 R3 与范围闸读取同一 pinned-base 任务卡（偿还 TD3） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `6eec97f`，PR #16；R3 卡片、diff、rubric 与 FrozenPaths 均钉到同一不可变 OID；base 卡非普通 blob、读取/探测失败均 fail-closed；Sol R3 pass） |
| W0 | T0-DEBT-ARCHIVED-CARD-PATHS | 修复三个已归档任务卡的失效入站路径（偿还 TD22） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `4ed2ec7`，PR #17；三处具名来源均改指 archive，17hh 覆盖 TA1 真实移动、非普通文件目标与三枚 old-path 变异；Sol R3 pass） |
| W0 | T0-DEBT-TEMPLATE-STORE-IMMUTABILITY | 补 TemplateStore 读回列表不可替换的变异自证（偿还 TD13） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `20d028f`，PR #18；三项 fixture 的 MutableList 索引替换命中 UOE，外层 wrapper 删除变异精确 RED，生产 SHA 恢复；Sol R3 pass） |
| W0 | T0-DEBT-REFERENCE-INTEGRITY | 修复权威文档／脚本中漂移或失效的 TD 交叉引用（偿还 TD16） | T0-GATE-FIXFORWARD | M | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `e8bf550`，PR #12；正式 RED 先证旧 TD14 指向必红，Sol R3 首轮拦下两处未覆盖回退与诊断字段缺口，修为六文件九个 source→target 映射、9 枚 code/path/reference 分类变异后次轮 pass） |
| W1 | T1-SKELETON-E2E | **一次性走通骨架**：建巡检→加一项→拍一张→导出 PDF（真机可见，用完即弃） | T0 | S–M | Opus 5 | Sonnet 5 max | —（人工真机验收） |
| W1 | T1-SCHEMA-CORE ★ | SQLDelight 全 schema+UUIDv7+基线迁移+JVM 测试 | T0 | H | DeepSeek V4 Pro · high | Sonnet 5 max | 冻结前 Opus 5 抽审 |
| W1 | T1-SPIKE-PLATFORM | 真机可行性 ×4：overlay/离线听写/SAF/80 照 PDF 压力 | T0 | H | Opus 5 · max | Sonnet 5 max | —（人工真机验收） |
| W1 | T1-CANON-HASH ★ | canonical JSON+SHA-256+黄金向量 | T1-SCHEMA-CORE | H | DeepSeek V4 Pro · high | Opus 5 | Terra 对向量复算 |
| W1 | T1-TEMPLATE-ENGINE ★ | 模板 schema+加载器+stable-id/版本对齐+按类型枚举 | T1-SCHEMA-CORE | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W2 | T2-ROUTINE-CONTENT | Routine 双语模板 80–120 项+校验测试 | T1-TEMPLATE-ENGINE | S | DeepSeek V4 Pro · medium | Luna Max | **merged**（master `00cb5f0`；deepseek-rescue 替代 Luna Max 复核，卡文已同步登记；9 轮 R3 后合并——烟雾报警器声明组连续 4 轮被拦：内容照抄→许可风险→中性标签改写丢事实→措辞被压缩丢法定 or 替代方案，详见 PR #5） |
| W2 | T2-PHOTO-PIPELINE | 照片存储/EXIF 转正(8 向)/哈希去重/导入 | T1-SCHEMA-CORE | M | Sonnet 5 · max | DeepSeek V4 Pro | **merged**（master `2a3fa6b`，PR #6；R3 第 9 轮触 `ReviewRoundCap` 转人裁，用户裁定选项①：合并+剩余两条登记 TD14（跨 FS+DB 共享临界区式真原子性）/TD15（编码字节上界形式证明），均写入卡 `non_goals`；裁后 3 轮各拦到真缺陷并逐一修复——去重复用漏判存在性校验/位图内存无界/EXIF 亚秒精度缺失、日志缺结构化上下文（round 10），以及本卡收尾时最深的一处：`PhotoAssociationRecorder` 补偿逻辑按内容哈希判活，而同一 rel_path 允许不同哈希并存，导致同 photoId 但哈希不同的重试会误删赢家仍引用的证据文件——改按 `selectById(photoId)` 判活 + 安全性不对称（判不清就不删）、`clock.nowMs()` 移入 try、补偿内部查询失败不再顶掉主异常、导入临时文件改逐次调用唯一命名、SAF 流从函数入口起就纳入所有权（round 11，含两次 L205 独立对抗复核）。78 个 JVM 测试、变异逐一击杀+SHA 复核；L236 登记（变异靶点粒度须与被证明的叙事性主张等宽，同 L165 族）） |
| W2 | T2-CAPTURE-CORE | 采集状态机+房间粒度草稿自动保存(:core) | T1-TEMPLATE-ENGINE | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `89d522e`；6 轮 R3 后合并——round 1-2 拦真缺陷（入参未校验致悬空引用/跨记录归属未核对/wear_or_damage 状态回退未清）；round 3 拦原子性（读-判-写跨事务边界）、登记 TD10（跨连接并发契约债，与 T3-FINALIZE 共享，仲裁后禁止评审再以此 block 单连接卡）；round 4-5 拦基线字段范围误读（人裁维持统一解析）、房间序未定、校验不对称、测试严谨度（DTO-only 断言/无时间戳断言/单物业覆盖）；round 6 拦 AdverseStatuses 可变集合强转泄露（同 T1-TEMPLATE-ENGINE 缺陷类）与草稿态基线语义。全程 76 测试、约 30 个单点变异逐一击杀+SHA 复核；L222 登记 SQLite 无 ORDER BY 返回序坑） |
| W2 | T2-PHRASELIB | 双语短语库种子+数据接口 | T1-TEMPLATE-ENGINE | S | DeepSeek V4 Pro · low | Luna Max | **merged**（master `b4655a1`；deepseek-rescue 替代 Luna Max 复核 66 条短语，卡文已同步登记，复核记录同时留在 PR body 与内容测试类头注——L227：R3 只读 diff 不读 PR body；4 轮 R3——round 1-2 拦真缺陷（sort 漏抄可被默认值 0 静默吞掉/suggestFor 对拼错评级值静默放行/5 处双语译文丢推测语气或语法不全/校验顺序注释与实现不符）后撞 ReviewRoundCap，stableId 是否需参与过滤的争议转人裁：卡文修订裁定 v1 契约=纯按状态过滤、stableId 为消费端预留接口缝，评审据此不得再以此 block；人裁后 round 1 拦下我自行加的分类过滤器（与刚裁定的"纯按状态"契约矛盾，已删）、round 2 pass。34 个 JVM 测试、24 枚单点变异逐一击杀+SHA 复核；新登记 L227，L223 追加一例） |
| W2 | T2-ROOM-REPEATABLE | 房间定义带 repeatable 标记：模板契约 + `.sqm` 迁移 + 入库读回往返 | T1-TEMPLATE-ENGINE | M | DeepSeek V4 Pro · high | Sonnet 5 max | —（**须先还清 TD4**；拆自 T1-TEMPLATE-ENGINE R3 仲裁） |
| W2 | T5-BACKUP-FORMAT ★ | 流式加密归档格式+manifest+防篡改/错口令测试 | T1-CANON-HASH | H+ | Opus 5 · max | Sonnet 5 max | **merged**（`efedcfb`，R3 第 4 轮 pass，两次人裁：分块 AEAD / CD 非规范性，见卡「格式评审记录」）；Terra 未接线 → DeepSeek V4 Pro 独立复读代替（L26），记录在 PR #9 |
| W3 | T2-CAPTURE-UI | Compose 走查界面：大按钮/备注/短语/听写/两级拍照 | T2-CAPTURE-CORE,T2-PHOTO-PIPELINE,T1-SPIKE-PLATFORM | M | Sonnet 5 · max | Terra | — |
| W3 | T3-REPORT-COMPOSER ★ | 纯 Kotlin 布局引擎：分页/双语配对/哈希页脚+黄金布局树 | T1-CANON-HASH,T2-CAPTURE-CORE | H+ | Opus 5 · max | Sonnet 5 max | — |
| W3 | T3-FINALIZE | finalize 事务+只读强制+Supplement 哈希链 | T1-CANON-HASH | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `a5a71ed`；PR #7，15 轮 R3 后合并，48 测试）——唯一悬点（DbCompletenessChecker 逐项 allowed_statuses 重验，评审三度提出，round 5/12/13 均按 mint-point/L220 驳回）触发两轮争议转人裁，用户裁**选项 A**（实现该检查，防御纵深）：新增 `itemsWithDisallowedStatus`；裁后评审又拦两条真发现——① 删掉自己此前引入的重复权威 `classifyAdverseness`/`Adverseness`（ADVERSE/NOT_ADVERSE 从未被消费），简化为 `isInDomain` 纯域成员判定；② 只读强制此前只证过冻结 SQL 谓词，补一条经真实 `InspectionRepository.setItemStatus`/`setWearOrDamage` 的集成测试。TD5 → paid（本 PR 为偿还指针） |
| W4 | T3-PDF-RENDERER | :app PdfDocument 渲染+CJK 字体+内存策略+双版本 | T3-REPORT-COMPOSER | H | Sonnet 5 · max | Opus 5 | — |
| W4 | T3-HISTORY-COMPARE | 历史条(上次状态/滑动)+ghost overlay 集成+双轨基线 | T2-CAPTURE-UI,T1-SPIKE-PLATFORM | H | Sonnet 5 · max | Opus 5 | — |
| W4 | T4-COMPLIANCE-ENGINE ★ | 配置驱动合规引擎+阻断 API+NZ DST 边界测试 | T1-SCHEMA-CORE | H | Opus 5 · high | DeepSeek V4 Pro | Terra 对规则夹具与需求逐条比对 |
| W4 | T5-BACKUP-IO | SAF 目的地+自动导出(WorkManager)+恢复先试跑后落刀 | T5-BACKUP-FORMAT | H | Sonnet 5 · max | Terra | — |
| W5 | T3-E2E-CORE | JVM e2e 闭环接 verify 闸门 2（$gate2Pending=false） | T3-FINALIZE,T3-REPORT-COMPOSER,T2-ROUTINE-CONTENT | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W5 | T4-NOTICES | 48h 通知双语文本+一键复制+送达存档(全文快照) | T4-COMPLIANCE-ENGINE | M | DeepSeek V4 Pro · high | Terra | Luna Max（通知文本） |
| W5 | T4-SCHEDULE | 13 周节奏提醒+本地通知 | T4-COMPLIANCE-ENGINE | S | GPT-5.6 Terra · medium | DeepSeek V4 Pro | — |
| W5 | T5-RETENTION | 租客数据保留期+一键清理 | T1-SCHEMA-CORE | S | DeepSeek V4 Pro · medium | Luna Max | **merged**（master `60cee85`；5 轮 R3（两次撞 ReviewRoundCap=2，均经人裁 reset）——round 1 拦法律措辞混淆（联系方式清理期 12 个月被误述为 RTA s123A 本身规定的数字）+ UI type-to-confirm 对空 tenant_name 永久锁死清理按钮；round 2（撞 cap）拦措辞残留（改写后仍暗示"无限期保留系 RTA 要求"）+ 哈希不变量测试造假（DRAFT 巡检+未持久化照片，未验证真实 finalize 记录）+ purge() 自身到期边界无测试覆盖，人裁：findings 属实且卡内可修 → reset；round 3（reset 后首轮）拦 civil-calendar 时区错用（`ZoneOffset.UTC` 误引"存储用 UTC 入库"规则算日历月，应循 ADR-0004 先例改用 Pacific/Auckland + DST 边界测试），人裁 reset；round 4（再撞 cap）拦 5 处测试盲区（sortedBy 排序/isPurgeable/`Collections.unmodifiableList`/`months` 覆盖参数均无证伪测试、UI "12 个月"字符串未溯源常量），人裁：全部属实 → 定裁修法（删 `months` 参数/补 4 处测试/UI 单源化）+ reset；round 5 pass。20 个 JVM 测试、8 处单点变异逐一击杀+SHA 复核（其一因误用 `.clear()` 而非 `.set()` 产出假证明，识破后重做）；新登记 L231（civil-calendar 计算时区与存储格式规则混淆）、L232（产品策略数值与法条数字巧合相同时的措辞混淆）；TD13（`TemplateStore.read()` 同款 `Collections.unmodifiableList` 缺自证测试） |
| W6 | T6-TEMPLATES-REST | Ingoing/Exit/Annual 内容+Exit wear/damage+配对约束 | T2-ROUTINE-CONTENT,T3-HISTORY-COMPARE | M | DeepSeek V4 Pro · medium | Luna Max | **Luna Max 全文复核** |
| W6 | T6-HHC | Healthy Homes 五项子模块+合规快照输出 | T3-PDF-RENDERER | M | DeepSeek V4 Pro · high | Terra | — |
| W7 | T7-REMEDIATION | LLM 建议：mock 优先+仅房东版+措辞边界+免责声明 | T3-PDF-RENDERER | M | Sonnet 5 · max | Opus 5 | Sol 安全面重点评审 |
| W7 | T7-SMOKE-POLISH | 真机全流程冒烟+微修捆绑（清单产出 docs/SMOKE-CHECKLIST.md） | 全部 MUST + T7-REMEDIATION（收官卡，不并行） | S | Sonnet 5 · medium | DeepSeek V4 Pro | — |

★ = 冻结点卡：合并后其产出登记 `scripts/_config.ps1` FrozenPaths，改动走版本评审。
并行窗口速查：W1 四卡并行；W2 五卡并行（BACKUP-FORMAT 提前入场）；W3–W5 各内部并行；关键路径 ≈ T0→SCHEMA→CANON→COMPOSER→PDF→E2E。

> **调研已回流**（docs/research/synthesis.md + 3 篇深挖）：官方 NZ 巡检表成为 Routine 模板骨架；二值主评级 UI（存储枚举不变）、照片隐私标记、物业级条目抑制、封面卷积/出处页脚等已并入相应卡上下文包；ghost overlay 确认为全品类空白（唯一差异化确认）。

## 用户已定（2026-08-15 签认，下列为**执行契约**，执行模型按此做，勿再问）
1. ✅ **ADR-0002 已签认**：备份 = app 私有存储 + SAF 加密归档导出；需求 §11 那处[定]以 ADR-0002 为准。T5 线解锁。
2. ✅ **房产现状 = 2 套以上，部分在租**。两条硬后果：
   - **既有租约补不回 Ingoing** ⇒ schema 必须支持「把某次 Routine 指定为该 tenancy 的基线」（详见 T1-SCHEMA-CORE 上下文包新增段），Exit 对照 `tenancy.baseline_inspection_id` 而非「必有 Ingoing」的假设；
   - 多物业是**常态不是边缘**：物业切换/按物业筛选是 v1 面（T2-CAPTURE-UI 与 T5-BACKUP-IO 的「按物业导出」照 ADR-0002 已含）。
3. ✅ **租客数据保留期 = 租约结束后 12 个月**（对**联系方式**）：`tenant_name`/`contact` 到期一键清空（置 NULL，**不删行**——证据链要留）；**照片/报告/哈希无限期保留**（Rentals Act s123A 的 12 个月是**法定下限**、非上限，押金争议实务按 Limitation Act 更久）。落地卡 = T5-RETENTION。
4. ✅ **年检评级 5 态**（NO_ISSUE/MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT/NOT_APPLICABLE）——用户未否决，按 5 态做。
7. ✅ **不做** Condition/Cleanliness 全量双刻度：v1 = 单刻度 + Exit/Ingoing 房间级清洁条目（已在卡内）。
8. ✅ **不做** 缺陷责任方/费用字段：v1 只保留 Exit 的 `wear_or_damage` 三态。

> 3/7/8 是 **T1-SCHEMA-CORE 冻结前**必须落定的项，现已落定 ⇒ 该卡可开工，无待定阻塞。

## 仍待定（不阻塞当前波次）
5. **s48(2)(c) 复检语义**：向 Tenancy Services/持牌人士确认「查验已约定维修」是否占 4 周限额；确认后只改配置（ADR-0004），不改码。
6. **Remediation 用哪家 LLM/key**（T7 前定即可；接口做成 provider 可换）。

## 已由 3 方讨论定稿（原[待] → 已定）
技术栈原生 Kotlin+Compose（ADR-0001）· 租赁评级 4 档 · Exit 独立 wear/damage 三态且仅差异项 · 两级拍照规则（N_A 不逼拍照）· UI 英文单语 + 报告平行双语 · finalize 锁定+哈希页脚 · SQLDelight/自研 UUIDv7/canonical 规范（ADR-0003）。
