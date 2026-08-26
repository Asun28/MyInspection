# 安全策略 (SECURITY)

> EN: Defines what must never enter the repo (secrets, credentials, databases, session state) and how that is enforced: the `check-secrets` leak gate wired into `task.ps1 ship` and the `pre-push` git hook, plus the `-Strict` full-history scan required before going public, the gh personal-account guard, and main-branch protection. Also states the honest threat boundary — these are self-discipline gates for a single personal account, not tamper-proof controls for multi-collaborator orgs.

> 本文件说明机密、边界与评审闸门的安全约定。建议仓库**私有**。
> 文档漂移纪律闸（非安全闸）的边界见 `docs/adr/0005-doc-drift-gate.md`。

## 1. 机密永不入库
- `.env`、`*storage_state*.json`、`auth/`、`.secrets/`、`.review/`、核心数据库（`*.db`/`*.sqlite`/`*.duckdb` 等）、密钥/证书（`*.key`/`*.pem`/`*.pfx`/`id_rsa*`）、凭据（`*credentials*.json`/`service-account*.json`/`*.secret`）均经 `.gitignore` 排除。
- `.env.example`、`data/README.md` 是可入库的模板/示例，**不含任何真实密钥**；确需入库小型样例库用 `git add -f`。
- 推送前由 `check-secrets.ps1` 防泄露闸扫描，覆盖**所有** push 路径——`task.ps1 ship` 内置调用 + 裸 `git push`（经 `gh-bootstrap.ps1` 装的 pre-push 钩子，串在账号守卫后；均非 `-Strict`，全历史扫描仍走 §1.1 手动/建仓预检）——发现机密被追踪即中止（敏感模式集单一真相源）。
- 导出/分发产物用**白名单**打包，杜绝凭证/日志混入。

## 1.1 Git / GitHub / 变 public 前防泄露
方法论：把机密从「被 `.gitignore` 排除」升级为「既排除、又**确认未被 git 追踪**、且**不在 git 历史里**」。
对**已被追踪**的文件，`.gitignore` 完全无效——它只对未追踪文件生效；已追踪文件须 `git rm --cached <f>` 取消追踪后再提交，否则随仓库一起公开。
**关键**：仓库变 public 暴露的是**整个提交历史**，不只当前快照。一个曾提交、后来 `git rm --cached`/删除的机密，当前快照扫不到、却能从历史 `git log -p` 恢复——`git rm --cached` **只动当前索引、不删历史 blob**。补救须 `git filter-repo`/BFG 清历史 + **轮换该密钥**。

- **Git + GitHub CLI（`gh`）是必需**：日常只读操作随用；所有**写操作**经 `_guard.ps1` 锁定到 `_config.ps1` 配置的个人账号（禁组织）。`gh-bootstrap` 还装 **`pre-push` git 钩子**，使**任何** `git push`（含 agent 直跑、绕过脚本者）都先过账号守卫——把账号锁从「脚本里调一下」升级为「git 层强制」（绕过须显式 `git push --no-verify`）；host 经负向后顾锚定，拒 `evilgithub.com` 之类子串伪装。`gh` 仅作举例工具，方法论是「写操作锁个人账号 + 推送前防泄露」。
- **GitHub Actions 是可选且有成本**：免费账户的**私有**仓不支持服务端规则集（403）。两条路：① **GitHub Pro + 私有仓**（拿到服务端分支保护）；或 ② **public 仓 + 本防泄露闸**（变 public 换来服务端规则集，但必须先确认零泄露）。
- **变 public 前必跑**：`pwsh scripts/check-secrets.ps1 -Strict`，**必须全绿**。它核验：① 核心数据库 / 私有文件既被 gitignore 排除、又**未被 git 追踪**（已追踪 → `git rm --cached`）；② 已追踪文件**内容**不含硬编码密钥（厂商前缀 + **JWT**（`eyJ….eyJ….`）+ 通用 `KEY=VALUE`）；③ **`-Strict` 还扫整个 git 历史**（文件名 + 新增行内容）——曾提交过的机密即使现已删除也会命中。高保险可再叠 `gitleaks`/`trufflehog`。
- **谁把守这道闸（TD62）**：`gh-bootstrap.ps1` **建 public 仓时**（`-Private:$false`）自动改跑 `-Strict` 全历史（fail-closed，非零即中止建仓）——补上「建 public 那一刻的历史盲点」。**但**日后手动把已有私有仓翻 public（`gh repo edit --visibility public` 或网页 Settings）**没有任何自动闸能拦**：那一步**必须由人先跑** `pwsh scripts/check-secrets.ps1 -Strict` 并确认全绿、再翻转——这是一道**人工清单闸**，不是机械强制（诚实的边界，见 §4）。
- **`.example` 模板不再被 blanket 放行（TD62）**：文件名闸只显式豁免 `.env.example` / `data/README.md`；`service-account.json.example` / `prod.pem.example` / `secret.example` 这类「按敏感模式命名、只加 `.example` 后缀」的文件不再整个逃过文件名闸。含真密钥的 `*.example` 无论如何都会被内容闸（JWT/厂商前缀/`KEY=VALUE`）抓到。
- **核心敏感类别**（`check-secrets.ps1` 的模式集，与本节工具命名同为举例）：**数据库**（`*.db`/`*.sqlite`/`*.duckdb`/`*.mdb`）· **密钥/证书**（`*.key`/`*.pem`/`*.pfx`/`*.p12`/`id_rsa*`）· **env**（`.env`/`*.env`）· **凭据**（`*credentials*.json`/`service-account*.json`/`*.secret`）· **登录态**（`*storage_state*`/`auth/`）。
- 退出码：有「致命」（已追踪敏感文件）即 1；`-Strict` 把「覆盖缺口 / 工作树未忽略」等警告也升级为致命。非 git 仓优雅跳过（exit 0）。

## 1.2 卡片即代码——任务卡的信任边界
**卡片即代码**：任务卡 `dod_command` 字段，经 `pwsh -Command` 按原文执行——
这**本质是代码**，不是声明式配置。`check-cards.ps1` 已有形态守卫（拒 no-op 空断言、YAML block-scalar 裸指示符、嵌套
`pwsh -Command "…$var…"` 双层包裹），但那只挡**语法层**坑，不挡「内容本身是恶意或未经审查的代码」。
**信任边界**：不得对**未经审查**的外部 PR 携带的卡片、第三方来源的卡片、或下载来的模板卡片，直接跑
`task.ps1 -Phase red` 或 `-Phase ship`（两者均会执行卡片的 `dod_command`）——
先人工读一遍该字段，确认没有意外的网络出站/文件删除/凭据读取，再执行任何会跑该命令的相位。
同账号/同人自建的卡片不在此边界内（本仓单人个人账号定位，见 §4）。

## 2. 运行时硬边界（MyInspection Android 产品）

### 2.1 离线与出站

- 本地 SQLite 是唯一真相源。物业、租约、巡检、照片、保存、finalize、历史、规则、本地 PDF、日程、本地/USB 备份与恢复不依赖网络。
- v1 无账号、同步、遥测、广告、崩溃上传或 app-owned 后台 HTTP/数据上传。远程 remediation 是唯一允许的运行期出站请求，且只在用户点 `Generate remote suggestions` 后经独立 adapter 调用；离线 seed-map 先行，网络失败不改本地证据、不阻断报告。自动加密备份仍可通过 SAF/DocumentsProvider 写入用户选择的目录；云 provider 对密文的后续传输由 provider 管理，不属于 app 网络 adapter。
- `core`、capture、report、compliance、backup format/restore 不得依赖 HTTP client。引入 `INTERNET` 时必须同时显式禁用 cleartext、限定远程 adapter、测试无隐式请求；新增目的地或 payload 另走 ADR/任务卡。
- remediation 出站 payload 是封闭投影，只含 `schema_version`、来自版本锁定模板的 `stable_id`、该模板允许的状态枚举和版本锁定 seed 资产中的 `seed_suggestion_code`；不得含备注或任何自由文本。发送前显示将发送的精确 canonical JSON 并再次确认；adapter 重新验证字段全集、模板成员关系和长度后才发送，未知/额外字段 fail closed。负向测试把地址、姓名、电话、邮箱、备注、路径、URI、照片/音频引用、完整报告、API key 和备份内容分别植入所有源记录，断言最终请求字节均不含这些值；响应只接受有上限的 schema JSON，自由文本/未知字段拒绝。

### 2.2 本机数据与密钥

- SQLite、设置、回执、恢复 journal、Keystore 密文信封和 staging 元数据放 credential-encrypted internal/no-backup storage；device-protected storage 不存租客数据。大照片/音频可放 app-specific external storage，但卷缺失不得破坏 DB 一致性。
- Android 系统备份/云恢复/设备迁移全部关闭：manifest `allowBackup=false`，Android 11 及以下和 Android 12+ 规则逐域排除。唯一支持的完整数据出口是用户选择目的地的加密 `.mibk`。
- 备份口令是用户掌握的跨设备恢复秘密。后台自动备份只读取 Android Keystore 加密的本机口令信封；信封不导出，Keystore key 不可导出。口令/派生 key 只以可清零缓冲短暂存在，不进数据库、日志、通知、崩溃信息或剪贴板历史。
- remediation API key 使用 Keystore 支持的本机加密存储，不入仓库、备份、日志或报告。产品不承诺在 root、恶意 OS、已解锁设备或恶意无障碍/键盘下保密。
- 产品无账号，故“注销”等价为设备所有者显式执行 `Delete all local data`：影响预览后输入 `ERASE`，清除 app-owned 主/诊断 DB、媒体、报告、设置、Keystore aliases/信封、缓存、journal/staging 与持久 URI 授权。用户经 SAF 保存到外部 provider 的加密 `.mibk` 不属于 app-owned 本机数据，绝不删除并在确认前明示。

### 2.3 备份、恢复与分享

- 产品范围同时保留整包与按物业导出。format v1 的按物业兼容导出仍含整库 `db.sqlite`，必须明示“数据库包含全部物业、仅媒体按物业筛选”，不得获得物业隔离/可恢复回执或用于物业级交付。ADR-0006 只批准下一版按物业范围与逐表数据闭包，不批准冻结格式：实现卡仍须完成 version review。物业包 staging 必须按 ADR-0006 列出的每张表比较源/目标 `(PK, canonical-row-hash)` 集合，并验证所有逻辑关系及媒体/manifest 双向完备；所有活跃模板/定义与已引用历史版本必须保留，内置短语须在成功前按锁定版本/hash 重建。任一表遗漏、多行、改值或无规则均不得写 Verified 回执。
- 恢复矩阵固定为：v1 `full` 全验后可整包替换；v1 `property` 因整库 DB + 不完整媒体必须拒绝；完成冻结契约评审后的 v2 `full` 整包替换，v2 `property` 全验后以隔离快照替换当前 app 数据，不做隐式合并。旧 reader 拒绝 v2，新 reader 仅接受 v1 full 与 v2 full/property；未知 scope、未来格式或不支持 schema 一律拒绝。
- 自动目录树备份采用新 `.partial` → 重开全验 → rename，或复制到新最终文档并再次重开复核；手动 `ACTION_CREATE_DOCUMENT` 只写并重开同一授权 URI，不假设 rename/copy。两条路径都只在最终可读对象全验成功后写回执；失败残留尽力删除，无法删除时提示用户处理且保留旧的已验证状态。
- 恢复是敌意输入边界：限制 KDF、manifest、文件数、路径、逐项/总字节和可用空间；先 staging 全验，再通过独立 journal 原子替换。错误口令、损坏、未来版本、空间不足、授权收回或进程中断都保持当前数据不动。
- PDF/备份分享只用 SAF 或 `FileProvider content://` + 临时只读 URI grant；禁 `file://`、宽目录授权、永久 exported provider 和原始路径。分享前提示 PDF 离开 app 后不再受本产品控制。
- 口令、恢复预检、租客联系方式和全屏敏感照片启用 secure-window/recents 防护；不全局禁截图，以保留普通巡检和明确分享流程的实用性。

### 2.4 日志、通知与界面泄露

- 生产日志只写操作名、非敏感 reason code、耗时/计数和随机 request/asset id。禁地址、姓名、联系方式、备注/转写、文件绝对路径、SAF URI、备份对象名、照片内容/hash、口令、key、Authorization header 和 provider 原始错误体。
- 持久诊断事件只进独立的 credential-encrypted/no-backup 诊断库，不进主证据库、canonical hash、PDF、通知、Android backup 或 `.mibk`；最多保留 90 天/20,000 行，先到即小批物理裁剪。日志写入失败不得改变巡检、finalize、备份或恢复结果。
- “Admin/support” 无远程入口或写权限。只有设备所有者可在设置页明确查看包含/排除项后，离线导出最近 7/30/90 天的脱敏诊断包；支持人员不能借诊断功能修改 finalized evidence。字段与验收合同见 `docs/DATABASE-DESIGN.md`。
- 用户可见通知只写 `Backup needs attention` 等通用文案；锁屏通知不显示物业地址、租客名、照片缩略图或恢复范围。
- 剪贴板仅用于用户显式复制通知文案；app 不自动复制秘密，敏感字段不提供复制动作。复制不等于发送，不生成 sent 状态。
- 本机健康状态只从 typed events、其不受事件保留期清除的本地 projection 与权威回执派生；因果先后只看数据库自增序列，时间戳只用于显示/年龄。operation/reason/context 封闭码、单位、outcome 约束及 `FINALIZE_FAILED / PDF_FAILED / BACKUP_LAST_FAILED / BACKUP_STALE_7D / BACKUP_FAILED_3X / INTEGRITY_FAILED / RESTORE_FAILED / RESTORE_ROLLED_BACK / PREVIOUS_CRASH / STARTUP_SLOW` 的精确派生/清除规则以 `docs/DATABASE-DESIGN.md`“Diagnostic registry v1”为唯一注册表。来源变化后 1 秒内显示一个可操作动作。它不是远程监控；v1 不自动遥测、Wi-Fi 上传或发送远程告警。
- 崩溃 marker 使用 registry 的 `PREVIOUS_CRASH/FAILURE`：Android 11+ 只把本 app `ApplicationExitInfo` 的 crash/native-crash/ANR/initialization-failure/excessive-resource 明确原因映射为封闭 reason；其他系统退出原因不告警。Android 10 及以下只接受 app 自写的未捕获 Java 异常 marker，不从“没有正常退出标记”猜崩溃。context 仅含来源枚举、格式受限 build id、封闭异常类型和最多 8 个安全帧标识；禁 system description/trace、message、原始类名、业务字段、行号、路径/URI、payload、token 或内存转储。opaque 消费账本与唯一 correlation 防重复，每个 release 的 mapping/符号证据仅保存在受控本地发布工件中，不进 APK、仓库、诊断包或自动上传链。

### 2.5 威胁边界与验证

- 保护目标：遗失但锁定设备、被复制/篡改备份、敌意归档、路径穿越/压缩炸弹、错误口令、低空间、授权收回、进程中断和意外回退。
- 不保护：root/已解锁或恶意系统控制的设备、用户主动导出的明文、忘记的口令、provider 删除密文或拒绝服务。硬件 Keystore 是优先能力，不假设所有设备都具备同等级硬件保护。
- 发布前必须真机演练：飞行模式完整巡检/PDF、本地备份恢复、云 provider 离线/授权收回、低空间、进程被杀、Keystore 失效、错误口令、损坏/敌意包和回滚恢复。

> 开发期工具（git/gh/codex/uv/Claude Code 插件）运行在开发者机器上，不在产品运行时边界内，也永不链接进 APK。完整决策见 ADR-0006。

## 3. 凭据与评审
- **GitHub**：仅用 `gh` keyring 凭据；本会话进程环境里若存在无效 `GITHUB_TOKEN`，脚本会在调用前清空，强制走 keyring。
  所有 gh 写操作仅限 `scripts\_config.ps1` 配置的个人账号（`_guard.ps1` 前置校验，禁组织账号）。
- **Codex 评审闸门**（R3）：Codex CLI 在**本地**只读运行，凭据**不进 CI**；
  其裁决以 commit status `codex-review` 回贴 GitHub，作为 main 分支规则集的必需检查（有 Pro 时）。
  这样「Codex 代替人工审批」不需要把任何 AI 凭据放进 GitHub Secrets。
- **CI**（`.github/workflows/ci.yml`）只跑纯确定性、无网络的 `verify`，不需要任何密钥。
- **外部信任面聚合视图**：运行期接触的外部信任边界（远程 MCP server `context7` + R3 评审后端 + vendored skill provenance）的来源/传输/出站数据集中登记在 `docs/TRUST-MANIFEST.md`；`.mcp.json` 每个 MCP server 须在其登记，`selftest.ps1` 闸 9g 强制此漂移不变量。

## 4. main 分支保护（规则集，需 GitHub Pro 或 public 仓）
- 必须经 PR 合并；必需状态检查：`verify`（CI）+ `codex-review`（本地 Codex 裁决）。
- 仅 squash 合并、合并后删分支、禁止强推与删除 main。
- free+private 不支持服务端规则集 → 由客户端 `review.ps1` 退出码 + task-loop skill 强制（见 docs/lessons L3）。
- **诚实边界（这是「自律工具」，非防篡改控制）**：`codex-review` 状态由**提交者自己的 gh token**回贴（legacy Statuses API，无 GitHub App / `integration_id`），故任何有写权限的协作者都能**伪造**该状态；且规则集 `required_approving_review_count=0` 表示**无人工审批兜底**。对**单人个人账号**（脚手架的设计定位）这没问题——它把第二模型评审当作纪律辅助；但在**多协作者/组织**场景，这不构成可审计的防篡改门禁。需要真门禁者应改用 GitHub App 出具状态 + 至少 1 个 CODEOWNERS 人工审批（属 org/team 模式的范围扩展，非本脚手架默认；治理边界以本节为准）。

## 5. 依赖许可（商用）
- 硬规则见 `docs/LICENSE-POLICY.md`：**禁** GPL/AGPL/SSPL 等 copyleft 与任何 non-commercial/research-only
  代码·模型权重·数据集·素材；**仅用** MIT/BSD/Apache 等宽松许可；LGPL 仅限进程外/动态调用。
- 每次加/升级依赖跑 `scripts\check-licenses.ps1`（命中禁列即失败）；Codex 评审闸门同步拦截。

## 6. 报告问题
仓库私有、单人/小团队：发现安全问题请在 issue 标记或直接联系维护者。涉法内容标 ⚖️ 须律师复核。

## 7. 企业代理 / TLS 环境（可选 · 按需读）
> 若开发机在企业 MITM 代理 / 自定义根 CA 环境下（常见于出站强制走公司网关），git/gh/npm/uv/codex 等工具的
> TLS 握手可能因看不到公司注入的根证书而失败（`SSL: CERTIFICATE_VERIFY_FAILED` 类错误）。本节只给
> **工具无关**的通用排查方向，不代某个具体工具做代理配置（各工具的代理/CA 设置项以其官方文档为准）。

- **根因常见于两类**：① 企业代理终止 TLS 并用自签/内部 CA 重签，需要把该 CA 加入各工具信任的证书存储；
  ② 出站强制走代理，需要工具知道代理地址。
- **通用排查方向**：确认是否需要把企业 CA bundle 追加进各工具的自定义 CA 配置项；确认代理地址是否需要显式
  声明给这些工具（多数遵循标准的 `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` 环境变量惯例，但并非所有工具都自动读取）。
- 出现 TLS 校验失败时，先确认是否为该类环境问题，**不要**图省事全局关闭证书校验（如 `--insecure`/
  `NODE_TLS_REJECT_UNAUTHORIZED=0`）——这会连真实中间人攻击也一并放行，只在临时诊断时用、绝不进日常配置或 CI。
- 本仓的 `codex`/`gh`/`uv`/`npm` 调用均遵循各自工具的标准代理/CA 环境变量约定，脚手架本身不做额外网络层封装。
