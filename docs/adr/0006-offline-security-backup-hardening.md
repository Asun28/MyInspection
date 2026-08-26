# 0006 — 离线、安全与备份加固

日期：2026-08-20 · 状态：**accepted（依据需求 §11 的 `[定]` 范围合同）** · amends：ADR-0002 的密钥、失败隔离与恢复验证细节；保留其存储/provider 与整包/按物业范围

这里“保留存储/provider”只保留 **app-private** 安全边界、用户自选 SAF provider 与两种导出范围；本 ADR 明确 supersede ADR-0002 决策 1 的“全部活数据放 app-specific external storage”：SQLite、设置、回执、密钥信封与恢复控制状态改放 credential-encrypted internal storage，只有大体积媒体可放 app-specific external storage。

## 背景

MyInspection 在住宅现场使用，网络可能完全不可用；本机又保存地址、联系方式、租客物品照片和可用于争议的巡检证据。现有设计已有 local-first、SAF、分块 AEAD 和验证后恢复的正确基础，但仍有四个架构缺口：

1. SQLite 和所有控制状态都放外部 app-specific storage；该位置空间大，但不如内部存储可靠，且旧 Android 上隐私边界较弱。
2. 当前冻结的 format v1 按物业包仍含整库 `db.sqlite`，范围只由 manifest 标记，不能视为物业隔离；过滤后的最小快照需要独立版本评审后才能成为真实行为。
3. 自动备份只保存“口令校验哈希”；校验值无法重新派生加密密钥，因此无人值守加密备份无法按现卡实现。
4. 离线、云 DocumentsProvider 失联、授权收回、低空间、密钥失效和敌意备份包的降级行为尚未形成统一契约。

Android 官方建议离线优先应用以本地数据源为唯一真相源，并让 UI/领域层不直接接触网络；敏感且必须可用的数据优先放内部存储。Android Keystore 能提供不可导出的本机密钥，但它不是跨设备恢复机制。系统 Auto Backup 默认会覆盖多数 app 数据，所以敏感应用需要同时处理旧版备份规则和 Android 12+ 数据提取/设备迁移规则。

参考：

- [Android offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Android app-specific storage](https://developer.android.com/training/data-storage/app-specific)
- [Secure file sharing](https://developer.android.com/training/secure-file-sharing)
- [Network security configuration](https://developer.android.com/privacy-and-security/security-config)

## 决策

### 1. 本地优先与网络隔离

- SQLite、配置、备份回执、恢复日志和本地队列是唯一真相源；所有核心读取和写入先且只依赖本地数据。
- 建物业、巡检、拍照/导入、自动保存、finalize、历史比较、规则校验、本地 PDF、日程和本地/USB 备份在飞行模式下可用。
- 远程 remediation 是唯一可选联网能力，必须由用户显式触发并经独立 adapter；离线种子建议仍可用。远程失败不改变本地数据、不阻断 finalize 或 PDF。
- 云 SAF provider 是用户选择的外部目的地，不是核心依赖。云 provider 离线只让该次备份/恢复失败；本机巡检继续工作。
- v1 没有账号、服务器同步、遥测、广告或 app-owned 后台 HTTP/遥测上传。自动备份仍可通过 Android SAF/DocumentsProvider 写入用户选择的目录；云 provider 后续如何传输密文由 provider 管理，不属于 app 网络 adapter。除用户显式触发的 remediation 外，引入任何 app-owned 出站目的地必须另走 ADR/任务卡和 payload 评审。

### 2. 活数据分层

- SQLite、设置、回执、Keystore 密文信封、恢复 journal 和 staging 元数据放 credential-encrypted **internal storage**；device-protected storage 不放租客数据。
- 体积较大的照片/音频可放 app-specific external storage，但不可成为 DB、恢复 journal 或密钥的唯一落点。启动和每次媒体操作都处理卷不可用/空间不足。
- 临时明文只放 internal cache/staging，使用不可预测名称；成功、失败、崩溃恢复后都清理。文件名、日志和通知不含地址、姓名、备注或租客信息。
- 数据不进 MediaStore/相册。对外打开或分享 PDF 时只用 `FileProvider` `content://` URI 和临时只读授权；不暴露原始路径，不永久导出 provider。
- `allowBackup=false`、旧版 `backup_rules.xml` 和 Android 12+ `data_extraction_rules.xml` 继续排除所有域及设备迁移。受支持的用户主动出口只有 PDF/通知文案、加密 `.mibk`，以及只含白名单聚合与脱敏事件的诊断包；其中只有 `.mibk` 是可恢复的完整证据数据出口。

### 3. 备份范围与密钥保管

- **format v1 UI 同时保留全量与按物业备份。** 现有 v1 按物业包仍含整库 `db.sqlite`，所以只能明确标为“包含全部物业数据库、仅媒体按物业筛选”的兼容导出；它不得获得物业隔离或可恢复回执，也不得用于物业级交付/恢复。
- **本 ADR 只批准安全按物业备份的产品范围与数据闭包，不是冻结格式的版本评审。** 下文用 `v2` 作为规划标签；精确 header/manifest/reader/writer 契约及 frozen-path 修改仍须由实现卡声明 `version_review: this card = the version review` 后批准。该包必须生成独立最小 SQLite 快照，不得含其他物业、全局秘密或无关设置。
- v2 按物业恢复仍不做隐式合并：用户确认影响范围后，以该隔离快照替换当前 app 数据，使恢复后的 app 只包含该物业；若当前 app 有其他数据，必须先提示生成全量备份。全量包继续替换全部数据。

#### v2 按物业数据闭包与完备性闸

- 逐表期望集合固定为：`property` = 目标 PK；`tenancy`/`inspection`/`property_item_override` = `property_id` 为目标的全部历史与活跃行；`room_instance`/`notice`/`supplement` = 父 `inspection` 在集合内；`inspection_item` = 父 `inspection` 与 `room_instance` 均在集合内；`photo` = 父 `room_instance` 在集合内且可选 `inspection_item_id` 为空或在集合内；`audio` = 父 `inspection_item` 在集合内；`template_version` = 所有活跃版本，加上被所选巡检引用的历史版本；`check_item_def` = 父 `template_version` 在集合内的全部历史与活跃定义。这样零巡检物业仍可创建每种新巡检，且 override 的 stable id 不因省略活跃模板而失效；每条 override 还必须匹配至少一个随包活跃定义，否则导出 fail closed。全局且不属于物业的 `phrase_entry` 不入包；提交恢复成功前，app 必须从版本锁定的内置短语资产确定性重建并核对其版本/content hash。`previous_inspection_id`、`baseline_inspection_id` 与 tenancy baseline 非空时必须指向所选巡检集合，否则 fail closed。
- 导出必须在同一源快照中为上述每张表生成按 PK 排序的 `(PK, canonical-row-hash)` 集合；staging 重开后逐表重算并要求与源集合完全相等，同时要求 staged schema 中每张表都有显式 include/exclude 规则。新增表没有规则、任一源行遗漏、多出一行、字段被改、逻辑引用不闭合，均不得写回执。
- 媒体集合由所选 `photo`/`audio` 行的相对路径、内容 hash 与 size 唯一推导，并与 manifest/归档条目双向相等。hostile fixtures 必须对每张非空表分别删除一行、加入跨物业行、修改一行，并对每条逻辑关系造孤儿；另须覆盖零巡检物业仍带全套活跃模板、override 仅由原本未被巡检引用的活跃模板定义，以及短语重建版本/hash 不符。每个夹具都必须在替换当前数据前失败。
- 兼容性固定为：旧 v1 reader 对任何 v2 header fail closed；完成冻结契约评审后的新 writer 对 full/property 都写 v2；新 reader 只接受 v1 `full`、v2 `full`、v2 `property`，明确拒绝 v1 `property` 与未知/未来版本。该矩阵不预先批准 v2 的具体字段布局。
- 用户口令是跨设备恢复的根；无服务端找回。每个 `.mibk` 继续按冻结格式使用 PBKDF2-HMAC-SHA256 + 分块 AES-256-GCM。
- 为支持 finalize 后和每周后台备份，设置时把口令 `CharArray` 用 Android Keystore 内不可导出的 AES-GCM key 加密，信封只保存在 internal `noBackupFilesDir`。信封、nonce 和版本可持久化；明文口令和派生密钥不写盘、不进日志/通知，使用后尽力清零。
- Keystore 信封只是本机便利，不写入 `.mibk`，也不替代用户记住口令。换机时用户直接用口令恢复。Keystore 被清除、失效、设备尚未解锁或信封损坏时，后台任务进入 `NEEDS_UNLOCK`/`NEEDS_PASSPHRASE`，保留旧回执并请求重新验证；不得生成未加密包或阻断巡检。

### 4. 可验证写入与恢复

- 自动备份使用用户授予的 SAF 目录树：每次创建新的 `.partial` 文档，关闭并重开完成解密与 manifest/path/hash/size 校验后，优先安全 rename；provider 不支持安全 rename 时采用复制到最终对象并复核。只有最终文档复核成功才写 `VerifiedBackupReceipt`；随后尽力删除 `.partial`，删除失败则记脱敏 reason code 并在下次维护时按 app 自建标记清理，绝不把残留显示为成功。
- 手动 `ACTION_CREATE_DOCUMENT` 是单文档协议，不假设目录、同级新建、rename 或 copy 权限：写入用户授予的新文档 URI，关闭后通过同一 URI 重开并完整解密复核，成功后才写回执。写入或重开失败时不写回执，尽力删除该 URI；若 provider 不支持删除，明确提示“未完成文件可能残留，请删除后重试”，且最近一次已验证备份状态保持不变。
- DB 快照优先使用 SQLite online backup/一致性快照；checkpoint + 文件复制仅允许在 DB 写屏障内。不得一边写 WAL 一边裸复制。
- 恢复先解密到 internal staging，并在落盘前完成：header/KDF 上限、format/schema 兼容、canonical manifest、路径白名单、重复项、文件数、每项与总字节溢出、可用空间、逐项 hash/size 和双向完备性检查。任何条目超过 manifest 声明大小立即停止。
- 空间预检必须保留 `max(512 MiB, 可用空间 10%)` 安全余量；空间不够不开始 commit。文件数和 manifest 大小设明确实现上限并由 hostile tests 固定。
- commit 进入 maintenance mode，写独立恢复 journal，按“旧数据改名保底 → 新数据就位 → 重开 DB/抽查资产 → 标记完成 → 清理旧数据”执行。崩溃后依据 journal 确定性回滚或完成，不允许新旧数据混合。
- 恢复矩阵必须按解密后 manifest fail closed：v1 `full` 包在全部验证通过后可恢复并整包替换；v1 `property` 包即使可解密也必须拒绝，因为它含整库 DB 但媒体不完整；经独立冻结契约评审实现的 v2 `full` 包整包替换，v2 `property` 包在上述逐表源/staging 等值与媒体完备性验证后，以该物业快照替换当前 app 数据。未知 scope、未来格式或不支持 schema 一律拒绝。任何允许的旧包恢复都显示备份日期与将回退的数据范围并再次确认；恢复前现有数据不自动删除，建议先生成当前全量备份。

### 5. 产品状态与隐私反馈

- 离线不是全局错误。只有用户启动云备份、云恢复或远程建议时，才在操作旁说明依赖和恢复动作。
- 备份页始终把“最近一次尝试”和“最近一次已验证备份”分开。失败、授权收回、provider 离线、低空间或需要口令时，旧的已验证时间仍保留。
- 支持状态：`NOT_CONFIGURED / READY / RUNNING / VERIFIED / FAILED / AUTHORIZATION_REVOKED / PROVIDER_UNAVAILABLE / NEEDS_UNLOCK / NEEDS_PASSPHRASE / LOW_STORAGE`。每个非运行态最多一个主要恢复动作。
- 口令、恢复预检、租客联系方式和全屏敏感照片使用 secure-window/recents 保护；普通巡检列表和明确的 PDF 分享流程不做全局截图封锁。导出的 PDF/通知文案离开 app 后不再受 app 控制，分享前明示。

## 离线能力矩阵

| 能力 | 完全离线 | 离线时的产品行为 |
| --- | --- | --- |
| 物业、租约、巡检、历史、规则 | 是 | 直接读写本地真相源 |
| 相机与本机照片导入 | 是 | 权限拒绝时相机↔导入互为降级；均不可用时可继续非照片字段 |
| 语音 | 视本机离线模型而定 | 无离线模型时隐藏/禁用麦克风并保留键盘，不启动网络识别 |
| finalize、hash、本地 PDF | 是 | 不等待网络或备份 |
| 日程与应用内提醒 | 是 | 通知权限关闭时仍保留应用内日程 |
| 本地目录/USB `.mibk` | 是 | provider/卷可用即可；拔出或授权失效给出重选动作 |
| Drive/OneDrive 等 SAF | 不保证 | provider 不可达只失败本次操作；不伪报 Verified |
| 恢复本地 `.mibk` | 是 | 全部验证后才替换；失败保持当前数据 |
| 离线 remediation 种子建议 | 是 | 命中项直接生成 |
| 远程 remediation | 否 | 显示 `Unavailable offline`，保留离线建议与报告流程 |

## 威胁边界

本设计保护：遗失但锁定的设备、被复制的备份、传输/存储篡改、恶意/损坏归档、路径穿越/压缩炸弹、授权收回、低空间、进程中断、错误口令和意外版本回退。

本设计不保护：已经解锁或 root/恶意系统控制的设备、用户主动分享出去的 PDF/明文、恶意键盘/无障碍服务、忘记的备份口令、SAF provider 在接收密文后的删除或拒绝服务。硬件级 Keystore 是优先能力，不作为所有设备上的保证。

## 备选方案

- **SQLCipher 全库加密**：能缩小解锁设备上的部分落盘风险，但引入依赖、迁移和恢复复杂度；v1 仍采用系统文件加密/沙箱 + 锁屏。若威胁模型升级再另立 ADR。
- **只做手动备份**：最少密钥保管面，但无法满足 finalize 后和周期性保护；弃，改用 Keystore 信封且失败安全降级。
- **把用户口令明文或可逆固定 key 写入 preferences**：后台方便但扩大泄露面；弃。
- **把当前 v1 按物业包当成物业隔离包交付**：其整库 `db.sqlite` 与标签不一致，存在跨物业泄露；禁止。过滤快照须经格式版本评审后再启用。
- **强制所有页面 `FLAG_SECURE`**：更简单但破坏用户合理截图、演示和报告工作流；弃，采用敏感页面分级。

## 后果

- 核心 app 可长期断网运行，网络恢复不会触发隐式同步或改变已保存证据。
- 自动备份在同一设备上可运行；换机恢复仍只依赖用户口令和备份文件。
- 产品范围继续保留全量与按物业两种选择；当前冻结 v1 只具备安全的全量恢复语义，按物业隔离在完成格式版本评审前不得冒充已实现。
- live DB 仍未应用级加密，必须诚实依赖设备锁、Android 文件加密和 app sandbox；敏感设备应设置强锁屏。
- 本 ADR 是设计/任务契约。实现仍需独立任务卡、测试、真机离线/断电/低空间/授权收回演练和 frozen-path 版本评审。
