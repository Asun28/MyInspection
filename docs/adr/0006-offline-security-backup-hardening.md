# 0006 — 离线、安全与备份加固

日期：2026-08-20 · 状态：**accepted（用户已签认 2026-08-20）** · amends：ADR-0002 的密钥、失败隔离与恢复验证细节；保留其存储/provider 与整包/按物业范围

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
- `allowBackup=false`、旧版 `backup_rules.xml` 和 Android 12+ `data_extraction_rules.xml` 继续排除所有域及设备迁移。唯一支持的数据出口是用户显式生成的 PDF/通知文案或加密 `.mibk`。

### 3. 备份范围与密钥保管

- **format v1 UI 同时保留全量与按物业备份。** 这是产品范围合同，不是对当前冻结格式的实现声明：现有 v1 按物业包仍含整库 `db.sqlite`，不具备物业隔离，入口不得把它作为安全的物业级交付或恢复。过滤后的最小 SQLite 快照是未来版本评审目标；落地前必须继续保留两种范围需求，但按物业入口显示不可用及原因。恢复仍采用“全部验证后整包替换”，不做隐式合并。
- 用户口令是跨设备恢复的根；无服务端找回。每个 `.mibk` 继续按冻结格式使用 PBKDF2-HMAC-SHA256 + 分块 AES-256-GCM。
- 为支持 finalize 后和每周后台备份，设置时把口令 `CharArray` 用 Android Keystore 内不可导出的 AES-GCM key 加密，信封只保存在 internal `noBackupFilesDir`。信封、nonce 和版本可持久化；明文口令和派生密钥不写盘、不进日志/通知，使用后尽力清零。
- Keystore 信封只是本机便利，不写入 `.mibk`，也不替代用户记住口令。换机时用户直接用口令恢复。Keystore 被清除、失效、设备尚未解锁或信封损坏时，后台任务进入 `NEEDS_UNLOCK`/`NEEDS_PASSPHRASE`，保留旧回执并请求重新验证；不得生成未加密包或阻断巡检。

### 4. 可验证写入与恢复

- 每次备份创建新对象，不覆盖上一份已验证备份。写入 `.partial` → 关闭 → 重新打开 → 解密并逐项核对 manifest/path/hash/size → 形成最终对象 → 再次确认可打开，最后才写 `VerifiedBackupReceipt`。provider 不支持安全 rename 时采用复制到最终对象并复核；失败残留不得显示为成功。
- DB 快照优先使用 SQLite online backup/一致性快照；checkpoint + 文件复制仅允许在 DB 写屏障内。不得一边写 WAL 一边裸复制。
- 恢复先解密到 internal staging，并在落盘前完成：header/KDF 上限、format/schema 兼容、canonical manifest、路径白名单、重复项、文件数、每项与总字节溢出、可用空间、逐项 hash/size 和双向完备性检查。任何条目超过 manifest 声明大小立即停止。
- 空间预检必须保留 `max(512 MiB, 可用空间 10%)` 安全余量；空间不够不开始 commit。文件数和 manifest 大小设明确实现上限并由 hostile tests 固定。
- commit 进入 maintenance mode，写独立恢复 journal，按“旧数据改名保底 → 新数据就位 → 重开 DB/抽查资产 → 标记完成 → 清理旧数据”执行。崩溃后依据 journal 确定性回滚或完成，不允许新旧数据混合。
- 未来格式/不支持 schema 直接拒绝；旧备份允许恢复但必须显示备份日期与将回退的数据范围，并再次确认。恢复前现有数据不自动删除；建议先生成一份当前全量备份。

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
