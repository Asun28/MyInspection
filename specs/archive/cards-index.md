# 已归档任务卡索引（merged cards · cold storage）

> 一行一条已 `merged` 的卡，共 77 张；完整卡在 `specs/archive/tasks/<id>.md`。
> 由 `scripts/archive.ps1` 从 `specs/archive/tasks/` 投影生成，勿手工编辑。

| id | 状态 | 标题 |
|---|---|---|
| T0-CARD-ACCEPTANCE-SETS | merged | 给两张 round-cap 卡补封闭 acceptance 清单，并记录「轮次通胀 ≠ 颗粒度」的判据 |
| T0-CI-DOCS-FAST-PATH | merged | 让纯文档 PR 保留轻量 verify 状态而跳过 Android 工具链 |
| T0-CI-LICENSE-GATE-HASH-SYNC | merged | 同步 docs-only License gate 的 8.2b2 规范块哈希 |
| T0-CI-UNICODE-DEP-FIXTURE | merged | 补齐 license scanner 自检夹具的 Unicode helper 依赖并防假绿 |
| T0-DEBT-ARCHIVE-CARDS-INDEX-GATE | merged | 让归档任务卡索引保持为可验证的真实投影（偿还 TD146） |
| T0-DEBT-ARCHIVED-CARD-PATHS | merged | Repair inbound references to archived task cards (repay TD22) |
| T0-DEBT-CASE-PROBE-CLOSURE-SCOPE | merged | Make 17cc case mutation probes host-independent (repay TD25) |
| T0-DEBT-GATE-ENTRY-TRUST-BINDINGS | merged | 把 fail-closed 新入口信任绑定纪律晋升为必须层（偿还 TD154 / L164） |
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
| T0-GATE-FIXFORWARD | merged | 许可闸路径比较改 OS 感知 + 发布清单收敛为单一解锁路径（T0-GATE-HARDENING 事后 R3 两条 block 的 fix-forward） |
| T0-GATE-HARDENING | merged | 许可闸看得见 Gradle + verify 确定性 + 两枚闸门自测（从 T0-TOOLCHAIN 拆出） |
| T0-GATE-ID-UNIQUENESS | merged | 闸号唯一性做成机检，并让按锚点拼接的编辑不再静默错位 |
| T0-GRADLE-RUNTIME-FILE-INPUTS | merged | 让 Gradle 看见测试真正的输入，消除两类「改了东西仍报绿、测试其实没跑」 |
| T0-HANDOFF-REVALIDATE | merged | 续接旧 HANDOFF 前重验下一动作仍成立 |
| T0-HARNESS-PERF | merged | 横切优化 selftest 与 CI 墙钟时间（约 300 行 harness/测试改动） |
| T0-HARNESS-SUBTRACTION-PROTOCOL | merged | 为常驻 harness 文本增加量化、可回滚的减负协议 |
| T0-LESSONS-BUMP-PLANE | merged | bump 写主检出账本，复发计数不再随卡片 diff 丢失 |
| T0-LICENSE-DIAGNOSTICS | merged | Gradle 许可扫描统一诊断边界与脱敏套件（TD2 收口卡 3/5） |
| T0-LICENSE-GAV-BOUNDS | merged | Gradle/Maven GAV 分段上界与有界诊断契约（TD135；TD2 4/5） |
| T0-LICENSE-POLICY | merged | Gradle POM 许可策略合同与 exact-GAV 专用套件（TD2 收口卡 2/4） |
| T0-LICENSE-SCANNER | merged | Gradle 已解析坐标图合同提取与离线图套件（TD2 收口卡 1/4） |
| T0-LICENSE-SELFTEST-DRIFT | merged | 恢复 Gradle diagnostics 的 selftest 回归覆盖并消除权威套件漂移 |
| T0-SCAFFOLD-LEAN-CI | merged | Stop launching scaffold-only CI shards for ordinary product pull requests |
| T0-TOOLCHAIN | merged | 本机 Android 工具链 + android/ Gradle 双模块骨架空编译绿 + verify/CI 收紧 |
| T1-CANON-HASH | merged | canonical JSON 序列化 + SHA-256 + 黄金向量（★冻结点） |
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
| T2-ROOM-REPEATABLE | merged | 房间 repeatable 契约与同窗口 schema 语义债收口（TD6/TD7/TD8） |
| T2-ROUTINE-CONTENT | merged | Routine 双语模板内容（80–120 项）+ schema 校验绿 |
| T3-E2E-CORE | merged | 将已验收 Golden Evidence JVM E2E fail-closed 接入 verify Gate 2 |
| T3-E2E-GATE-ISOLATION | merged | 将 Golden Evidence 拆入独立 e2eTest source set 并由 Gate 2 单独执行 |
| T3-E2E-GATE-PORTABILITY | merged | 修复 verify Gradle wrapper 的 Windows/Linux 跨平台执行 |
| T3-E2E-GOLDEN-FIXTURE | merged | 冻结 JVM Core E2E 的 canonical Golden Evidence Fixture |
| T3-E2E-HASH | merged | Golden Evidence JVM 闭环与 DB/报告/独立重算三源 hash |
| T3-E2E-TENANT-REDACTION | merged | Golden Evidence tenant report landlord/private sentinel 防泄露 |
| T3-FINALIZE | merged | finalize 事务：完备性校验 → canonical 哈希落库 → 只读强制 + Supplement 哈希链 |
| T5-BACKUP-FORMAT | merged | 加密备份归档格式：流式 ZIP+AES-GCM + manifest + 防篡改/错口令测试（★冻结点） |
| T5-RETENTION | merged | 租客数据保留期 + 一键清理（Privacy Act 2020） |
