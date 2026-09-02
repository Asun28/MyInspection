# 已归档任务卡索引（merged cards · cold storage）

> 一行一条已 `merged` 的卡，共 165 张；完整卡在 `specs/archive/tasks/<id>.md`。
> 由 `scripts/archive.ps1` 从 `specs/archive/tasks/` 投影生成，勿手工编辑。

| id | 状态 | 标题 |
|---|---|---|
| T0-CARD-ACCEPTANCE-FIELD | merged | 把 acceptance 作者声明的验收清单登记为正式卡片字段，并给它一道形态机检 |
| T0-CARD-ACCEPTANCE-SETS | merged | 给两张 round-cap 卡补封闭 acceptance 清单，并记录「轮次通胀 ≠ 颗粒度」的判据 |
| T0-CI-DOCS-FAST-PATH | merged | 让纯文档 PR 保留轻量 verify 状态而跳过 Android 工具链 |
| T0-CI-HARDENING-SPLIT-PLAN | merged | 将候选 CI 硬化卡拆为分页契约与身份/deadline 两张可读串行卡 |
| T0-CI-IDENTITY-DEADLINE | merged | 候选 CI 的 run 身份绑定与最终 exact-head/base 快照 |
| T0-CI-LICENSE-GATE-HASH-SYNC | merged | 同步 docs-only License gate 的 8.2b2 规范块哈希 |
| T0-CI-MERGE-GATE | merged | 在所有远端合并路径上等待候选分支 ci.yml 检查全绿 |
| T0-CI-PAGED-CONTRACT | merged | 候选 CI 分页读取的形态、总数、稳定身份与跨页重放契约 |
| T0-CI-UNICODE-DEP-FIXTURE | merged | 补齐 license scanner 自检夹具的 Unicode helper 依赖并防假绿 |
| T0-DEBT-ARCHIVE-CARDS-INDEX-GATE | merged | 让归档任务卡索引保持为可验证的真实投影（偿还 TD146） |
| T0-DEBT-ARCHIVED-CARD-PATHS | merged | Repair inbound references to archived task cards (repay TD22) |
| T0-DEBT-CASE-PROBE-CLOSURE-SCOPE | merged | Make 17cc case mutation probes host-independent (repay TD25) |
| T0-DEBT-GATE-ENTRY-TRUST-BINDINGS | merged | 把 fail-closed 新入口信任绑定纪律晋升为必须层（偿还 TD154 / L164） |
| T0-DEBT-LESSONS-BUMP-SUBMODULE-ROOT | merged | 让 lessons bump 在 submodule 中解析自己的主检出账本 |
| T0-DEBT-LICENSE-SCALAR-FORMAT | merged | 让许可元数据与诊断拒绝或清洗增补平面格式标量 |
| T0-DEBT-MIGRATION-FIXTURE-CLEANUP | merged | 收敛 TD4 migration fixture 的 Windows worktree 清理 |
| T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST | merged | 偿还 TD4：只豁免 SQLDelight schema 快照并恢复迁移验证 |
| T0-DEBT-MUTATION-BATCH-COUNT | merged | 把 mutation 批次条数自证纪律合并晋升为必须层（偿还 TD148 / L177） |
| T0-DEBT-MUTATION-EVIDENCE-CLASSIFIER | merged | 把 mutation 判据分类器纪律合并晋升为必须层（偿还 TD149 / L167） |
| T0-DEBT-MUTATION-RESTORE-SAFETY | merged | 把未提交 mutation 还原防丢纪律晋升为必须层（偿还 TD147 / L214） |
| T0-DEBT-POWERSHELL-DETACHED-ENCODING | merged | 把 detached PowerShell 编码纪律合并晋升为必须层（偿还 TD150 / L172） |
| T0-DEBT-R3-CARD-BASELINE | merged | Align R3 task-card authority with the pinned base (repay TD3) |
| T0-DEBT-R3-MOVING-REF-UNIX | merged | Repair the 17ac Unix moving-ref git shim with a quoted absolute target (repay TD27) |
| T0-DEBT-R3-QUOTA-ROUND-CLASSIFICATION | merged | 把 R3 配额/轮次证据分类纪律晋升为必须层（偿还 TD155 / L21） |
| T0-DEBT-REFERENCE-INTEGRITY | merged | Authority TD reference integrity (repay TD16) |
| T0-DEBT-RELEASE-CHECKLIST-HASH-GUARD | merged | Document and prove the release-checklist hash coupling (repay TD11) |
| T0-DEBT-SECRETS-SCALAR-FORMAT | merged | 让 tracked-sensitive allowlist 拒绝增补平面格式标量 |
| T0-DEBT-SEEDED-CLOSURE-SCOPE | merged | Make seeded mutation closures self-contained (repay TD23) |
| T0-DEBT-SELFTEST-CANARY-HARNESS | merged | 让 post-merge selftest canary 离线确定且失败可定位 |
| T0-DEBT-SELFTEST-CRITICAL-PATH | merged | 阶段性偿还 TD9：完整 scaffold selftest 退出 PR 合并关键路径 |
| T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS | merged | 让 selftest 分片与 all 汇总稳定点名失败闸 |
| T0-DEBT-SELFTEST-LOAD-STABILITY | merged | 消除 8.2e 高负载下固定五秒 rendezvous 假红 |
| T0-DEBT-SELFTEST-MUTATION-BUDGET | merged | 将 skip mutation 证明收敛到紧凑身份清单 |
| T0-DEBT-SELFTEST-NOGIT-ROUTING | merged | 用有界生产夹具证明 seeded no-git 路由 |
| T0-DEBT-SELFTEST-SKIP-VISIBILITY | merged | 让 selftest 有意跳过与前置失败裁剪均可见 |
| T0-DEBT-SELFTEST-SNAPSHOT-BASELINE | merged | 让 selftest all 快照钉住调用者 HEAD 与权威 master（偿还 TD156） |
| T0-DEBT-SELFTEST-SPLIT-PLAN | merged | 将 TD9 skip 可见性余项拆成有界串行卡 |
| T0-DEBT-TASK-INVENTORY | merged | Remove hand-maintained task-card inventory (repay TD21) |
| T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER | merged | 让 TD9 split checker 读取已归档规划卡 |
| T0-DEBT-TEMPLATE-STORE-IMMUTABILITY | merged | Prove TemplateStore read results reject element replacement (repay TD13) |
| T0-DEBT-UNICODE-SANITIZER-CATEGORIES | merged | 把 Unicode sanitizer 类别纪律合并晋升为必须层（偿还 TD151 / L181） |
| T0-DEBT-UNICODE-SCALAR-TEXT | merged | 建立 fail-closed 的 Unicode scalar 控制/格式文本单一真相源 |
| T0-DEBT-UNSAFE-PATH-DELEGATION | merged | 把不安全路径不得委托下游纪律晋升为必须层（偿还 TD153 / L171） |
| T0-DEBT-WINDOWS-PYTHON-UTF8 | merged | 把 Windows Python UTF-8 工具纪律合并晋升为必须层（偿还 TD152 / L162） |
| T0-DOCS-LIFECYCLE-RECONCILE-FOLLOWUP | merged | Reconcile remaining design and lessons card lifecycles |
| T0-DOCS-LIFECYCLE-RECONCILE | merged | Reconcile merged-card lifecycle and documentation workflow surfaces |
| T0-DOCS-T3-REPORT-CLOSURE | merged | Close absorbed T3 report R3 lifecycle and TD139 |
| T0-GATE-FIXFORWARD | merged | 许可闸路径比较改 OS 感知 + 发布清单收敛为单一解锁路径（T0-GATE-HARDENING 事后 R3 两条 block 的 fix-forward） |
| T0-GATE-HARDENING | merged | 许可闸看得见 Gradle + verify 确定性 + 两枚闸门自测（从 T0-TOOLCHAIN 拆出） |
| T0-GATE-ID-UNIQUENESS | merged | 闸号唯一性做成机检，并让按锚点拼接的编辑不再静默错位 |
| T0-GRADLE-RUNTIME-FILE-INPUTS | merged | 让 Gradle 看见测试真正的输入，消除两类「改了东西仍报绿、测试其实没跑」 |
| T0-HANDOFF-REVALIDATE | merged | 续接旧 HANDOFF 前重验下一动作仍成立 |
| T0-HARNESS-PERF | merged | 横切优化 selftest 与 CI 墙钟时间（约 300 行 harness/测试改动） |
| T0-HARNESS-SUBTRACTION-PROTOCOL | merged | 为常驻 harness 文本增加量化、可回滚的减负协议 |
| T0-LESSONS-BUMP-PLANE | merged | bump 写主检出账本，复发计数不再随卡片 diff 丢失 |
| T0-LESSONS-CAP-CORE-SPLIT | merged | 从超预算 PR |
| T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT | merged | 从超预算 PR |
| T0-LESSONS-CAP-TRIAGE-SPLIT | merged | 从超预算 PR |
| T0-LESSONS-CAP-UNIT | merged | 必须层封顶改按驻留经验 id 计量，并把 enforced_by 做成双向判据（横切卡，尺寸见 allow_paths） |
| T0-LESSONS-CMD-DOCSYNC | merged | 把 lessons.ps1 纳入 doc-drift 机检，并同步 archive 子命令到三处命令清单 |
| T0-LESSONS-COLD-RECALL | merged | 让一次性 lessons 可安全归冷且仍能统一检索 |
| T0-LESSONS-TIER1-CUT | merged | 必须层减法——驻留经验 id 从 19 降到 9，依据全取自 LEDGER 自己的字段 |
| T0-LICENSE-CI-INTEGRATION | merged | Gradle 许可扫描套件接线与 TD2 总验收（TD2 收口卡 5/5） |
| T0-LICENSE-DIAGNOSTICS | merged | Gradle 许可扫描统一诊断边界与脱敏套件（TD2 收口卡 3/5） |
| T0-LICENSE-GAV-BOUNDS | merged | Gradle/Maven GAV 分段上界与有界诊断契约（TD135；TD2 4/5） |
| T0-LICENSE-POLICY | merged | Gradle POM 许可策略合同与 exact-GAV 专用套件（TD2 收口卡 2/4） |
| T0-LICENSE-SCANNER | merged | Gradle 已解析坐标图合同提取与离线图套件（TD2 收口卡 1/4） |
| T0-LICENSE-SELFTEST-DRIFT | merged | 恢复 Gradle diagnostics 的 selftest 回归覆盖并消除权威套件漂移 |
| T0-LOCAL-RECONCILE-REGISTER | merged | 注册落后本地 master 的十二张可评审文档调和卡 |
| T0-R3-DIFF-BUDGET | merged | 在 push/R3 前按真实 diff 预算硬阻断超大任务卡 |
| T0-R3-FLOW-ENUM-SYNC | merged | 把真实 diff 预算闸补进每一处确定性闸枚举，并各配锚定断言（承接 T0-R3-DIFF-BUDGET 的 A13） |
| T0-RECONCILE-DATA-AUTHORITY | merged | 同步离线数据库、安全与备份设计权威 |
| T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE | merged | 登记 Components 原始音频保留修订源 |
| T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE | merged | 登记 Components 动效与 Compose 语义修订源 |
| T0-RECONCILE-DESIGN-COMPONENT-SPLIT | merged | 将超预算设计组件卡拆为基础与组件两个完整评审单元 |
| T0-RECONCILE-DESIGN-COMPONENTS | merged | 补齐 Field Ledger 组件合同、对比度、动效与无障碍规则 |
| T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE | merged | 同步旅程与组件卡到可移植的设计元数据源夹具 |
| T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE | merged | 登记 Foundations R3 修订源与两张下游卡的新钉点 |
| T0-RECONCILE-DESIGN-FOUNDATION-R3-PAIR-FIXTURE | merged | 登记 Foundations 精确渲染配对与层级修订源 |
| T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE | merged | 登记 Foundations 目标组件映射与非空洞 DoD |
| T0-RECONCILE-DESIGN-FOUNDATIONS | merged | 拆分 Field Ledger 设计基础合同以满足完整评审预算 |
| T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE | merged | 规范化设计旅程卡源文本换行后再执行区域验收 |
| T0-RECONCILE-DESIGN-JOURNEY-FIXTURE | merged | 修正设计旅程备份范围断言的可执行源夹具 |
| T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE | merged | 固定备份范围矩阵与完整旅程栈不变量 |
| T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE | merged | 固定支持路由列形与通知选择失败路径 |
| T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE | merged | 固定完整旅程表面注册与统一动作命名的设计源 |
| T0-RECONCILE-DESIGN-JOURNEYS | merged | 补齐 Field Ledger 信息架构、导航、恢复与离线隐私旅程 |
| T0-RECONCILE-DESIGN-METADATA-FIXTURE | merged | 修正设计元数据 YAML 状态的可执行源夹具 |
| T0-RECONCILE-DESIGN-METADATA | merged | 建立 Field Ledger 可机读设计令牌与组件注册表 |
| T0-RECONCILE-LESSONS-CONTRACT-FIXTURE | merged | 收口 lessons 六块精确模式与守卫夹具 |
| T0-RECONCILE-LESSONS-FINAL-FIXTURE | merged | 最终收口 lessons 可执行源与唯一模式 |
| T0-RECONCILE-LESSONS-FIXTURE | merged | 修正本地调和 lessons 的可执行源夹具 |
| T0-RECONCILE-LESSONS-R3-FIXTURE | merged | 移除 lessons 夹具中的未合并身份守卫声明 |
| T0-RECONCILE-LESSONS-R3-PATTERN-FIXTURE | merged | 对齐 lessons 身份原则的 R3 验收词组 |
| T0-RECONCILE-LESSONS-VALIDATOR-DOD-FIXTURE | merged | 修正 lessons validator 修复卡的 PowerShell 检查列表 |
| T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE | merged | 收口 lessons 夹具的 enforced_by 校验兼容性 |
| T0-RECONCILE-LESSONS | merged | 按当前 schema 归并本地经验 |
| T0-RECONCILE-ROADMAP-INDEX | merged | 将离线安全与诊断卡投影到任务表和技术债索引 |
| T0-RECONCILE-T1-SECURITY-CARDS | merged | 登记数据库与本地安全三卡 |
| T0-RECONCILE-T5-DIAGNOSTIC-CARDS | merged | 登记事件、诊断、清除与健康四卡 |
| T0-RECONCILE-UI-CAPTURE | merged | 对齐采集、历史与 PDF 实现卡的设计系统指针 |
| T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE | merged | 修正 UI Elements 覆盖卡的 Windows CRLF 表格解析 |
| T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE | merged | 登记 UI Elements 恢复确认组件修订源 |
| T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE | merged | 登记可完整验收的 UI Elements 覆盖源 |
| T0-RECONCILE-UI-COVERAGE | merged | 建立 UI Elements 覆盖索引 |
| T0-RECONCILE-UI-NOTICE-SCHEDULE | merged | 对齐通知与日程实现卡的设计系统指针 |
| T0-RECONCILE-UI-OFFLINE-OPERATIONS | merged | 对齐备份、媒体、remediation 与收官 smoke 的离线体验指针 |
| T0-SCAFFOLD-CI-HOTFIX | merged | 修复合并后 scaffold-selftest 的跨分支与跨 PowerShell 回归 |
| T0-SCAFFOLD-FLEET-LOOP | merged | fleet 双向回路——逐版决定、回填 v0.44 账域修复并留账 |
| T0-SCAFFOLD-LEAN-CI | merged | Stop launching scaffold-only CI shards for ordinary product pull requests |
| T0-SCAFFOLD-SYNC-045 | merged | 区分 scaffold origin/current，并推进到 v0.45.0 |
| T0-SELFTEST-ALLOWLIST-BASELINE-CLOSURE | merged | 让动态 E2E 基线追踪完整敏感清单 |
| T0-SELFTEST-MIGRATION-CHECK-CONTINUE | merged | 让 seeded migration 负例在 core:test 失败后继续跑真实 verifyMigrations task |
| T0-TOOLCHAIN | merged | 本机 Android 工具链 + android/ Gradle 双模块骨架空编译绿 + verify/CI 收紧 |
| T0-TRIAGE-EVIDENCE-CASE-REGISTER | merged | 登记 triage 裁决证据目录大小写语义修复卡 |
| T0-TRIAGE-EVIDENCE-SCOPE-REGISTER | merged | 把 PR |
| T1-CANON-HASH | merged | canonical JSON 序列化 + SHA-256 + 黄金向量（★冻结点） |
| T1-DATABASE-LIFECYCLE-AUTHORITY | merged | 数据库生命周期写权限：活跃/历史读取分流 + 基线与清理终态守卫 |
| T1-SCHEMA-CORE | merged | SQLDelight 全量 schema + UUIDv7 + 基线迁移 + JVM 测试（★冻结点） |
| T1-SKELETON-E2E | merged | 一次性走通骨架：建巡检 → 加一项 → 拍一张 → 导出一份 PDF（真机可见，用完即弃） |
| T1-TEMPLATE-ENGINE | merged | 模板 JSON schema + 加载器 + stable-id/版本对齐 + 按类型枚举校验（★冻结点） |
| T2-CAPTURE-CORE | merged | 采集领域核：巡检生命周期状态机 + 房间粒度草稿自动保存仓储（:core） |
| T2-FIELD-LEDGER-THEME-R3-CLOSURE | merged | Field Ledger Material 3 全角色显式映射（PR |
| T2-FIELD-LEDGER-THEME | merged | Field Ledger Material 3 主题契约：light/dark token 与语义状态角色 |
| T2-PHOTO-DIRECTORY-DURABILITY | merged | 照片 sidecar 的目录级 crash durability 收口 |
| T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER | merged | 照片孤儿清理：durable sidecar lease + WorkManager 生产调度（偿还 TD14） |
| T2-PHOTO-PIPELINE | merged | 照片管线：存储布局 + EXIF 转正（8 向）+ 内容哈希去重 + 导入 |
| T2-PHOTO-PROPERTY-DEDUPE | merged | 照片物理去重限定在同一物业（偿还 TD24，保证按物业备份闭包） |
| T2-PHOTO-QUALITY-PROFILES | merged | 新照片四档质量：Low / Medium / High / Extra High（默认 Medium） |
| T2-PHOTO-STREAMING-ENCODE | merged | 照片流式编码：去掉整份 JPEG ByteArray 内存峰值（偿还 TD15） |
| T2-PHRASELIB | merged | 双语短语库种子内容 + 查询接口 |
| T2-REPEATABLE-ROOM-RUNTIME | merged | 偿还 TD26：重复房间实例化、完备性与历史基线统一到实例维度 |
| T2-ROOM-REPEATABLE | merged | 房间 repeatable 契约与同窗口 schema 语义债收口（TD6/TD7/TD8） |
| T2-ROUTINE-CONTENT | merged | Routine 双语模板内容（80–120 项）+ schema 校验绿 |
| T3-E2E-CORE | merged | 将已验收 Golden Evidence JVM E2E fail-closed 接入 verify Gate 2 |
| T3-E2E-GATE-ISOLATION | merged | 将 Golden Evidence 拆入独立 e2eTest source set 并由 Gate 2 单独执行 |
| T3-E2E-GATE-PORTABILITY | merged | 修复 verify Gradle wrapper 的 Windows/Linux 跨平台执行 |
| T3-E2E-GOLDEN-FIXTURE | merged | 冻结 JVM Core E2E 的 canonical Golden Evidence Fixture |
| T3-E2E-HASH | merged | Golden Evidence JVM 闭环与 DB/报告/独立重算三源 hash |
| T3-E2E-TENANT-REDACTION | merged | Golden Evidence tenant report landlord/private sentinel 防泄露 |
| T3-FINALIZE | merged | finalize 事务：完备性校验 → canonical 哈希落库 → 只读强制 + Supplement 哈希链 |
| T3-PDF-ARTIFACT-PATHS | merged | Report artifact path derivation and anchored shape predicate |
| T3-PDF-RENDERER | merged | Pure JVM PDF render program, four export qualities, geometry and per-page sampling bounds |
| T3-REPORT-COMPOSER-R3-CLOSURE | merged | 报告布局 R3 收口：40mm 内联缩略图、不可拆图槽、可读时间与引用完整性 |
| T3-REPORT-COMPOSER | merged | 纯 Kotlin 报告布局引擎：分页/缩略图排版/双语行配对/哈希页脚 + 黄金布局树（★冻结点级质量） |
| T3-REPORT-CONTENT-ADAPTER | merged | Adapt shared semantic report content into the existing A4 layout plan |
| T3-REPORT-CONTENT-CONTRACT | merged | Shared privacy-filtered report content for native PDF and HTML parity |
| T3-REPORT-HTML-CHARACTER-POLICY | merged | Contextual HTML escaping and the character policy the document can actually honour |
| T3-REPORT-INTERCHANGE-AUTHORITY | merged | Native Routine DOCX import and shared PDF/HTML product authority |
| T4-COMPLIANCE-ENGINE | merged | 配置驱动 NZ 合规引擎：阻断校验 API + Pacific/Auckland DST 边界测试（★规则 schema 冻结） |
| T4-NOTICES | merged | 48h 通知：双语文本生成 + 一键复制 + 送达存档（全文快照/提前量/校验快照） |
| T4-REMINDER-CORRESPONDS-TRIM | merged | 删掉 corresponds 中两个被 store 不变量蕴含的比较 |
| T4-SCHEDULE-CADENCE | merged | 巡检类型的本地民历提醒节奏 |
| T4-SCHEDULE-REMINDER-CONTRACTS | merged | 提醒身份、路由文案与精确诊断合同 |
| T4-SCHEDULE-REMINDER-DELIVERY | merged | 提醒 Worker、通知发布与不重投边界 |
| T4-SCHEDULE-REMINDER-FLIGHT | merged | 注册合流、异步 callback flight 与单调 watchdog |
| T4-SCHEDULE-REMINDER-RECEIPTS | merged | 提醒耐久回执、损坏隔离与 generation CAS |
| T4-SCHEDULE-REMINDER-SCHEDULER | merged | WorkRequest 构造、注册预留与保留工作恢复 |
| T4-SCHEDULE-REMINDER-SPLIT-PLAN | merged | 将超限提醒卡拆为 delivery 与 scheduler 两张可读串行卡 |
| T4-SCHEDULE-SPLIT-PLAN | merged | 将 T4-SCHEDULE 拆成可读且可独立评审的三张串行卡 |
| T5-BACKUP-FORMAT | merged | 加密备份归档格式：流式 ZIP+AES-GCM + manifest + 防篡改/错口令测试（★冻结点） |
| T5-MEDIA-ARCHIVE-CONTRACT | merged | 媒体归档收口：重新打开逐字节核验、原子回执与 finalized 不变性 |
| T5-MEDIA-ARCHIVE-ELIGIBILITY | merged | 媒体归档账本：本机状态、PDF 完成回执与 exact-content 资格判定 |
| T5-MEDIA-ARCHIVE-SCHEMA | merged | 媒体归档 schema v5：四表形态、约束、索引与查询面 |
| T5-RETENTION | merged | 租客数据保留期 + 一键清理（Privacy Act 2020） |
