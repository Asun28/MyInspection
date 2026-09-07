# Android 模块边界与安全接口规格

2026-09-06 审校补全；需求与版本安排见 `docs/inspection-app-requirements.md`、`docs/TASK-BOARD.md`。本规格是已授权后续任务的接口约束，**下面的类型签名是设计草案，尚未实现或编译**；具体 API 应优先复用现有类型，避免建立同义接口。冻结 schema、canonical 与备份格式仍服从各自版本评审。

## 模块与所有权

保留 `:core` 与 `:app`。用包可见性、构造入口和窄能力接口限制依赖；暂不新增 Gradle 模块。`:core` 中的持久化实现可以使用数据库，UI、Worker 和渲染器只接收各自需要的用例与结果，不接收整个数据库、任意路径、口令或可修改安全策略。

| 边界 | 唯一职责与可替换部分 | 实施卡 |
|---|---|---|
| 应用装配 / 巡检 | 装配 DB、Clock、ID、模板和平台 adapter；保存/finalize 复用原事务和规则 | T1-APP-BOUNDARY-ASSEMBLY；T2-CAPTURE-UI 接导航 |
| 媒体证据 | 验证归属、有界来源、文件发布、关联与补偿；相机/导入是不同来源 adapter | T2-MEDIA-ACCESS-BOUNDARY；批量 V1.1、录音 V2 分卡 |
| 报告 | 一次受众/隐私投影，再传同一 ReportContent 给 PDF/HTML；验证后签发格式回执 | T3-REPORT-EXPORT-CORE / UI |
| 备份恢复 | 一致性快照、既有 ArchiveStore、验证回执和 journal；SAF 是 adapter | T5-BACKUP-IO；format v2 → PROPERTY-SNAPSHOT-CLOSURE → PROPERTY-RESTORE-INTEGRATION |
| 合规规则 | 来源可信验证、配置领域校验、确认绑定及原子激活 | T4-COMPLIANCE-UPDATE-TRUST → T4-COMPLIANCE-OVERRIDE-IMPORT |
| 整改建议 | 本地 seed、已确认闭集 payload、provider adapter、响应措辞/结构验证 | T7-REMEDIATION-PROVIDER-DECISION → T7-REMEDIATION |
| 平台与诊断 | 权限/存储/LocalSecretBox/SafeLog；封闭诊断事件独立于证据库 | T1-LOCAL-DATA-SECURITY 及既有诊断卡 |

## 接口定义与约束

以下返回结果应区分拒绝、取消、可重试平台失败、完整性失败和成功；不向业务层传播原始 Throwable、路径或 provider body。`Validated*`、`Confirmed*` 的实例只能由拥有校验/确认职责的用例产生，不允许公开构造一个 String 包装来伪装可信凭证。创建时校验不代替使用时重查状态。

```kotlin
interface InspectionCommands {
    suspend fun save(command: ValidatedRoomSave): SaveOutcome
    suspend fun finalize(inspectionId: InspectionId): FinalizeOutcome
}

interface EvidenceCommands {
    suspend fun attach(request: ValidatedEvidenceAttachment): EvidenceOutcome
    suspend fun open(reference: EvidenceRef): EvidenceReadOutcome
}

interface ReportProjection {
    suspend fun project(request: ValidatedReportRequest): ReportContent
}

interface BackupCoordinator {
    suspend fun export(request: ValidatedBackupRequest): BackupOutcome
    suspend fun inspect(source: SelectedArchive): RestorePreflight
    suspend fun restore(confirmation: ConfirmedRestorePlan): RestoreOutcome
}

interface ComplianceUpdate {
    suspend fun inspect(source: SelectedRuleFile): RuleImportPreview
    suspend fun activate(confirmation: ConfirmedRuleImport): RuleImportOutcome
}

// 产品 V2；原件存储不属于识别引擎。
interface OnDeviceTranscriber {
    suspend fun capabilities(): DictationCapabilities
    suspend fun transcribe(audio: PersistedAudioRef): TranscriptionOutcome
}

interface RemediationProvider {
    suspend fun suggest(request: ConfirmedRemediationPayload): RemediationOutcome
}

// 优先复用已有诊断 registry，而不是再建一套事件类型。
interface SafeOperationEvents {
    fun record(event: AllowedOperationEvent)
}
```

- **巡检**：同一事务内重查活动物业、租约归属、草稿状态及完备性。默认同一 DB 连接；TD10 的多连接问题只在引入第二连接时重开。最终时间和 data_hash 由用例计算；不能让 UI 改写 finalize 策略。
- **媒体**：句柄必须具有具体目标归属和有界读权限，实际读流仍执行字节/尺寸上限；不给 UI 暴露 resolve/discardIn 或媒体根。保留 no-follow、no-overwrite、引用保护与回滚补偿。宽文件 API 是误用风险，本规格不把它宣称为已证实漏洞。
- **报告**：确认显式纳入私密照片时，确认绑定受众、巡检/内容版本及明确照片集合；切换任一维度使旧确认失效。保持既有“显式可纳入”合同，不能擅自禁止房客版显式选择。两种 renderer 只消费已过滤内容，不自行查 DB 或再次决定隐私。
- **备份**：复用现有 ArchiveStore 与 opaque destination/object/version，只有 adapter 解析 provider 对象。SQLite 在线快照须证明一致性；采用 checkpoint-copy 时必须有同一写入屏障保护整个快照过程，快照完成后即可释放，不把耗时云 provider 写入放在 DB 锁内。最终对象关闭重开并全验后才能签发回执。
- **恢复**：预检与确认绑定确切包身份、范围和替换影响；确认后换包须重新预检。恢复 journal 先于业务装配执行，成功后重建连接与用例。v1 property 仍拒绝；format v2 property 是替换全部本机数据为单物业快照，不是合并。
- **规则**：来源可信验证在现有 loader 前，不能用同一来源自带 hash 证明发布者身份。具体摘要/签名、信任根、撤销、日期和回退政策由决策卡确定；导入 UI 无关闭规则开关，法律 work-check 待办保持独立。
- **整改**：只发送版本号、锁定模板 stable id、合法状态与锁定 seed 建议码。精确 canonical JSON 预览确认后，在 HTTP adapter 最后复查字段全集、成员和长度；拒绝自由文本、媒体、地址、路径和额外字段。provider 身份/端点及请求字节变化令旧确认失效。LocalSecretBox 提供凭据；响应经有界解析和措辞/分级验证，失败不阻断 finalize 或报告。
- **诊断与权限**：只接受注册 operation/reason 和已批准 opaque id/count/duration；不接受任意 Map、Throwable 或业务原文。写诊断失败不能回滚成功业务。按具体动作申请能力；共享能力结果和错误展示，分别保留相机、通知、SAF、未来麦克风的不同恢复方式。

## 复用验收

1. 相机与单图导入继续复用 VerifiedAssetWorkflow；相机 content_hash 覆盖最终 JPEG，导入 content_hash 覆盖原始 source bytes，不能为消除重复而统一两者。
2. PDF/HTML 在一次投影后复用同一 ReportContent；序列化前排除的内容在重开后的两种产物中都不得出现。
3. MediaPaths/PdfArtifactPaths 可共享内部路径片段校验；各自命名空间和物业约束不被合并掉。普通有界流工具优先复用，冻结 hash/backup 不因代码相似就重构，测试 oracle 保持独立。
4. UI 优先复用现有 Field Ledger 状态、焦点恢复、权限说明组件；只有出现真实重复才提取。不得把所有权限和所有导入流程合成通用状态机。

## 仍需决定的参数

规则发布者/可信凭证、版本和撤销策略；provider/key 与出站限额；批量提交/取消/重启策略和批次限额；V2 录音格式、语言、资源上限和离线引擎。各项由对应卡标注的前置调查/决策关闭，本规格不填未经确认的默认值。

物业闭包内部的 SnapshotRows 只读已签发的一致性私有快照；编译期表/列 registry 与参数化物业值限定访问，调用方不能传 SQL/identifier 或 live DB。scope row 编码由 format v2 版本评审先定义，与 inspection canonical 分域，不为此修改冻结 SQLDelight 或现有 data_hash。
