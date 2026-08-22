#requires -Version 7
<#
.SYNOPSIS
  脚手架的**唯一项目配置点**。新项目只改这一个文件（或跑 init-scaffold.ps1 自动填）。
  所有脚本/钩子 dot-source 本文件取项目级常量，避免把项目名/账号/冻结路径散落硬编码到各处。

.DESCRIPTION
  - 被 _guard.ps1 / task.ps1 / review.ps1 / gh-bootstrap.ps1 / check-licenses.ps1 与
    .claude/hooks/guard-frozen.ps1 dot-source。
  - 返回一个 $ScaffoldConfig 哈希表；调用方按需取字段。
  - 故意 fail-closed：GhAccount 未配置（留空）时，账号守卫会拒绝一切 gh 写操作，
    强制你先配置，杜绝误推到错误账号。
#>

$script:ScaffoldConfig = @{

  # 本项目唯一允许的 GitHub 个人账号。账号守卫(_guard.ps1)会拒绝任何其它账号的 gh 写操作。
  # 留空 '' => 守卫直接报错并提示先配置（fail-closed）。
  GhAccount = 'Asun28'

  # 仓库 / 项目名。留空 '' => 各脚本自动用仓库根目录名（Split-Path -Leaf）。
  # 一般留空即可；仅当目录名与期望仓库名不一致时才显式填。
  ProjectName = 'MyInspection'

  # 后端 Python 版本（setup/task 建 venv 用）。无 Python 后端则忽略。
  PythonVersion = '3.13'

  # 开发 worktree 的父目录（R1：每张卡一个 <root>\<TaskId>）。浅路径规避 Windows MAX_PATH。
  # 留空 '' => 按 OS 自动取默认（Windows: <系统盘>\wt，如 C:\wt；macOS/Linux: ~/.wt）——见 Get-ScaffoldWorktreeRoot。
  # 默认走 $env:SystemDrive（不硬编码 D:），修「单盘机器无 D: → 首次 start 崩」(C02) 与「mac/Linux 吃 D: 盘符」两个可移植性坑。
  # 显式填路径即覆盖自动值。selftest 闸⑮ 按该字段的单引号字面量注入临时根，留空仍可被注入。
  WorktreeRoot = ''

  # ── 冻结物（一等资产）：契约 / schema 一旦冻结，演进须走版本评审 ──
  # guard-frozen 钩子(PreToolUse)与 review.ps1 据此拒绝就地编辑这些文件。
  # 用「仓库相对、正斜杠」的正则片段；空数组 @() => 不启用冻结守卫（项目还没冻结点时留空）。
  # 示例（取消注释并改成你项目的冻结文件）：
  #   'backend/app/providers/contract\.py',
  #   'backend/app/schemas/manifest',
  # T1-SCHEMA-CORE 合并后冻结（该卡 doc_sync 明列本步；卡内多处注释亦以「本目录合并后即进 FrozenPaths」
  # 为前提解释了为什么下游卡要的查询必须由该卡预先提供）。此后加表/改列/加查询 = 新 .sqm + 版本评审，
  # 并通过 TD4 已恢复的 verifyMigrations 与逐路径 schema 快照防泄露闸。
  # **只冻 sqldelight/**：`core/model/` 的快照类型刻意不冻——T1-CANON-HASH 落地时若发现哈希域形状需微调，
  # 那是正常演进（该形状已由 InspectionSnapshotTest 的逐字段断言钉住，改动不会静默发生）。
  # T1-CANON-HASH 合并后冻结（该卡 doc_sync 明列本步）：canonical JSON 序列化器 + 哈希 + 快照投影是
  # finalize 哈希与备份 manifest 的共用地基，任何字节级行为变化都会改写历史 data_hash 的可复验性。
  # 演进走版本评审；黄金向量（含测试）一并冻结——测试就是契约本体。
  # T1-TEMPLATE-ENGINE 合并后冻结（该卡 doc_sync 明列本步）：**只冻 Template.kt**——模板 JSON 的形态
  # 就是内容卡（T2-ROUTINE-CONTENT / T6-TEMPLATES-REST）与采集层共同依赖的契约，改字段=改所有已发布模板的
  # 可读性。加载器/入库器/对齐器**刻意不冻**：那是实现，可以演进，形态已由 26 个测试逐条钉住。
  # 注：`T2-ROOM-REPEATABLE` 要往这份 schema 加房间定义，其第一步就是本条冻结的版本评审（该卡已写明）。
  # T5-BACKUP-FORMAT 合并后冻结（该卡 doc_sync 明列本步）：**格式是跨年契约**——今年导出的备份包必须能被
  # 未来任意版本读回，所以 47 字节明文头的逐字节布局、manifest 的 canonical JSON 形态、分块 AEAD 的
  # nonce 构造（前缀‖块序号‖final 标志）与 AAD 绑定，改一个字节就是改契约。
  # **主/测试目录一并冻结**：黄金锚点（头的十六进制布局、manifest 黄金串、公开 RFC 7914 的 PBKDF2 向量、
  # zip 尾部契约）即契约本体，测试就是规范的可执行副本。演进 = 新 format_version + 版本评审。
  # 注：`core/backup/` 下将来的**非格式层**（如 T5-BACKUP-IO 的 SAF 适配）不在此列，只冻 `format/`。
  FrozenPaths = @(
    'android/core/src/main/kotlin/nz/myinspection/core/backup/format/',
    'android/core/src/test/kotlin/nz/myinspection/core/backup/format/',
    'android/core/src/main/sqldelight/',
    'android/core/src/main/kotlin/nz/myinspection/core/canon/',
    'android/core/src/test/kotlin/nz/myinspection/core/canon/',
    'android/core/src/main/kotlin/nz/myinspection/core/template/Template\.kt'
  )

  # T38-DOCDRIFT：源脚本变更时须同步触及的权威文档（仓库相对、正斜杠正则；空表 => 不启用）。
  DocSyncMap = @{
    'scripts/task\.ps1'           = @('docs/DEVOPS-WORKFLOW.md')
    'scripts/review\.ps1'         = @('docs/QUALITY-RUBRIC.md')
    'scripts/check-licenses\.ps1' = @('docs/LICENSE-POLICY.md')
    # check-scope.ps1 是 DEVOPS-WORKFLOW「已推送恢复」序列第 3 步的可执行投影，二者今后必须同步改（TD93 item①）。
    'scripts/check-scope\.ps1'    = @('docs/DEVOPS-WORKFLOW.md')
  }

  # ── 本项目是否**分发**软件（许可闸 GPL 触发点判定，⚖️ 非法律意见）──
  # 默认 $true（保守/fail-closed，行为与今日一致）。GPL 系 copyleft 的义务触发点是**分发**——
  # 若你的项目**从不分发软件**（纯内部工具 / 纯 SaaS 后端且不随产品交付二进制），设 $false 会让
  # check-licenses.ps1 把**纯 GPL**依赖从致命降为黄牌（人工确认）。**注意仅纯 GPL**：
  # AGPL/Affero(网络触发)、SSPL(SaaS 触发)、EUPL(分发+通信触发)、non-commercial/研究限(用途触发)
  # 触发点与分发无关，**一律仍致命、本旗不降级**。变 public（开源=分发源码）请用 -Strict 复核。
  Distributes = $true

  # ── R3 第二评审后端（L26 模型无关）──：留空 '' => 内置默认 = codex CLI。
  # 设为任意命令模板即换后端（如自托管模型 / 另一家 CLI）：该命令须**从 stdin 读 prompt**、
  # 把裁决 JSON（{"verdict":"pass|block","reasons":[]}）**写到 $env:REVIEW_OUT 指向的路径**
  # （$env:REVIEW_WT = 被审工作树）。review.ps1 据此解析裁决，与 codex 默认路径同构。
  # 核心闸门「第二独立模型对抗评审」是方法论；codex 只是当前默认实现，可随时替换。
  # 安全告知（TD20）：自定义评审后端**无沙箱**（对比默认 codex 的只读沙箱 `-s read-only`），
  # 且运行于含攻击者可控 diff 的 prompt 上，对 $env:REVIEW_WT 指向的工作树有全读写与网络能力——
  # 接入方宜自加进程隔离/只读挂载等约束。
  ReviewCommand = ''

  # ── R3 评审状态检查名（L26 工具无关）──：commit-status 的 context 名，由 review.ps1（回贴状态）与
  # gh-bootstrap.ps1（分支规则集必需检查名）**从这一处**读取，治「工具名 'codex-review' 硬编码进永久契约 +
  # 在两处重复的魔法字面量」。留空 '' => 回退向后兼容默认 'codex-review'；换非 codex 后端可改名（如 'r3-review'）。
  ReviewStatusContext = ''

  # ── R3 轮次上限（治「评审无上限循环」）──
  # CLAUDE.md 早有明文：「maker 与 checker 同一争点两轮互不认可即停、排队人裁」。此前只是文档里的话、无机检，
  # 实测跑成 T0-TOOLCHAIN 9 轮 / T0-GATE-HARDENING 12 轮 / T1-SCHEMA-CORE 9 轮（_local/effectiveness-ledger.jsonl）,
  # 19 小时 3 张卡 30 次 block、零产品代码。本项把那条规则变成闸：同一分支累计 block 达此数后 review.ps1
  # **不再唤起评审者**，直接转人裁。
  # **它不是放行阀**：到顶后仍写 block 裁决、仍 exit 1、绝不产出 pass、绝不合并——只是停止烧评审时间
  # （配合 ReviewTimeoutSec=3600，每轮最坏 1 小时，无上限循环的代价是以小时计的）。
  # 人裁完（改卡 non_goals / 越界发现开新卡 / 认下并修）后 review.ps1 -ResetRounds 清零再跑。
  # 0 = 不封顶（回到旧行为）。
  ReviewRoundCap = 2

  # ── R3 评审模型 / 推理档位（当前默认后端 codex 的启动参数）──
  # ReviewModel 非空 = 项目**接管**评审者启动：review.ps1 额外传 --ignore-user-config，
  # 整份用户级 ~/.codex/config.toml（model / service_tier / mcp_servers / notify / 沙箱 / 插件）都不参与，
  # 评审者 hermetic（顺带收窄注入面）。留空则不传该 flag，沿用后端默认（含用户级配置），此时不声称免疫。
  # 留空 '' => 沿用评审后端自身的默认（codex 读**用户级** ~/.codex/config.toml）。**建议显式钉住**：
  # 那个文件是用户级、且会被 Codex 桌面应用改写——2026-07-10 实测桌面端把 model 改成当时 CLI 不支持的值，
  # 评审者启动即 400、review.ps1 fail-closed block，**合并闸对所有 PR 静默失效**。钉在项目配置里即免疫此类外部漂移。
  # 优先级：review.ps1 的 -Model/-Effort 参数 > 本两项 > 后端自身默认（留空即后者，保「空配置仍可跑」）。
  # L26 工具无关：换 ReviewCommand 后端后，本两项只经 $env:REVIEW_MODEL / $env:REVIEW_EFFORT 透传，
  # 由该后端自行解释（不做 codex 的枚举校验——别人的档位命名不归 codex 管）。
  ReviewModel = 'gpt-5.6-sol'

  # 推理档位。留空 '' => 后端默认。**合法值随模型而异**，本仓刻意不硬编码枚举：
  #   实测（2026-07-10，codex-cli 0.144.1）gpt-5.6-sol / -luna 接受 max、却**拒** minimal，
  #   而 API 的通用参数报错又把 minimal 列为合法——两者不同源，任何静态列表都会误拒/误放。
  # 填错即评审者启动失败 → 写不出裁决 → review.ps1 既有 fail-closed 路径 block（控制台可见后端原文报错）。
  # 本仓 PR 常触及闸/评审者本体（高风险面），故取 high。
  ReviewEffort = 'high'

  # R3 评审者超时秒数。留空/0 => review.ps1 内建 600s。
  # 优先级：review.ps1 的 -TimeoutSec 参数 > 本项 > 内建 600s。
  # **本仓取 3600**（TD109 实测依据 + 2026-08-05 T56 r16 复卡后二次上调）：评审者会在沙箱里自行跑全套
  # selftest 验证 diff（正当行为，见 QUALITY-RUBRIC「评审者时间预算」），实测曾需约 630s 而内建 600s
  # 恰好卡死——T41/T42/T43 三卡共 5 次「评审者已吐真 pass 却被墙杀」，每次都要人裁兜底，遂调 1800。
  # r14 的 t36set 全码位双向扫描后，selftest 在评审沙箱约 13 分钟/次，且评审者可因子进程退出码丢失按
  # 审计要求重跑一次——两跑即超 1800（T56 r16 实测：正当验证进行中被墙杀，非挂起）。3600 容两跑 + 读
  # diff 余量，同时仍封死无限挂起（TD11/L21 的原意）。**这是调预算、不是弱化闸**：跑不完就调大，
  # 绝不改成让评审者少验。
  ReviewTimeoutSec = 3600

  # 经验系统「必须层」（CLAUDE.md 经验铁律）封顶的**驻留经验 id 数**——不是该小节的 markdown 条目数：
  # 一条写着 [L190][L193] 的 bullet 对计数器是 1 条、对模型是 2 条规则，封顶要管的是后者，否则
  # 把几条并进一条 bullet 就能一边合规一边让每轮上下文成本继续涨。判定核 scripts/_lessons.ps1。
  # 超限须淘汰最不活跃项回按需层。
  LessonsMustCap = 10

  # ── 项目规模档位（软提示）──：建议「跳过哪些交付链」，治「小项目被全套流程拖慢」。
  # T0 极简（脚本/玩具/一次性）· T1 标准（多数项目）· T2 完整（大/长周期/团队/合规）。
  # 注：T2 的「团队/合规」指**项目复杂度**（更重流程/审计），非「脚手架提供多人组织治理」——
  #   git 层控制仍锁单个人账号（见 _guard.ps1 + docs/SECURITY.md §4）。
  # 纯软提示：AI/人据此裁剪流程；**不做强制机制、不做 init 物理裁剪**。按规模档位表见 docs/IDEA-TO-PLAN.md。
  ProjectTier = 'T1'

  # ── 脚手架版本（溯源 + fleet 回填锚点）──：本模板自身的版本，**非**项目级可填项。
  # init-scaffold 把它戳进下游 CLAUDE.md footer，便于日后对照上游、回填脚手架改进。
  # 发布脚手架改进的仪式（TD12）：① 在此 bump（semver x.y.z）；② 在 CHANGELOG.md 顶部加一条
  #   `## [x.y.z] - YYYY-MM-DD`（selftest 闸 ⑧ 8.0c 强制：顶条目须 == 本字段）；③ 合并后打 git tag `vx.y.z` 并推送
  #   （下游据 tag + CHANGELOG 回填，见 TEMPLATE-README「升级已 init 的下游」）。selftest 闸 ⑧ 校验格式并验证戳入下游。
  ScaffoldVersion = '0.29.0'

  # ── 上游脚手架仓库（fleet 双向回路）──：scaffold-sync.ps1 据此取 release tag 与 CHANGELOG、
  # 并在 report 时把元层缺陷反哺回该仓。留空 '' => 各调用方回退到自带默认地址。
  # fork 了脚手架就改指自己那份（真正维护本项目的那个）。
  UpstreamRepo = 'Asun28/claude-devops-scaffold'
}

# 便捷解析：取 ProjectName（留空则回退仓库目录名）。$RepoRoot 由调用方传入。
function Get-ScaffoldProjectName {
  param([Parameter(Mandatory)][string]$RepoRoot)
  if ($script:ScaffoldConfig.ProjectName) { return $script:ScaffoldConfig.ProjectName }
  return (Split-Path $RepoRoot -Leaf)
}

# 便捷解析：取经配置的 GhAccount，未配置即 fail-closed 报错（守卫调用）。
function Get-ScaffoldGhAccount {
  $a = $script:ScaffoldConfig.GhAccount
  if (-not $a) {
    throw "脚手架未配置 GitHub 账号：请编辑 scripts\_config.ps1 的 GhAccount（或跑 init-scaffold.ps1）。这是 fail-closed 设计，避免误推到错误账号。"
  }
  return $a
}

# 便捷解析：取脚手架版本（溯源戳；未设回退 'unknown'）。
function Get-ScaffoldVersion {
  $v = $script:ScaffoldConfig.ScaffoldVersion
  if (-not $v) { return 'unknown' }
  return $v
}

# 便捷解析：取上游脚手架仓库（fleet 回路）。ContainsKey 守卫：旧 _config（未含该键）在
# StrictMode 下直接取键会抛——缺失或留空均返回 ''，调用方自行回退。
function Get-ScaffoldUpstreamRepo {
  if (-not $script:ScaffoldConfig.ContainsKey('UpstreamRepo')) { return '' }
  $r = $script:ScaffoldConfig.UpstreamRepo
  if (-not $r) { return '' }
  return $r
}

# 便捷解析：取开发 worktree 根。配置非空即用之；留空则按 OS 取默认（可移植：mac/Linux 不再吃 D:\ 盘符）。
function Get-ScaffoldWorktreeRoot {
  $w = $script:ScaffoldConfig.WorktreeRoot
  if ($w) { return $w }
  if ($IsWindows) {
    # 系统盘根的浅目录（规避 Windows MAX_PATH），用 $env:SystemDrive 而非硬编码 D:——
    # 治「单盘机器（只有 C:）首次 task.ps1 -Phase start 的 New-Item 抛 DriveNotFoundException、
    # 错误既不提 WorktreeRoot 也不提 _config」（30-lens C02）。要用别的盘显式填 WorktreeRoot。
    $sysDrive = if ($env:SystemDrive) { $env:SystemDrive } else { 'C:' }
    return (Join-Path $sysDrive 'wt')
  }
  return (Join-Path $HOME '.wt')
}

# 便捷解析：取「是否分发软件」旗（许可闸 GPL 触发点判定）。
# ContainsKey 守卫：旧 _config（未含该键）在 StrictMode 下直接取键会抛——保守回退 $true（视为分发 → GPL 仍致命）。
function Get-ScaffoldDistributes {
  if ($script:ScaffoldConfig.ContainsKey('Distributes')) {
    return [bool]$script:ScaffoldConfig.Distributes
  }
  return $true
}

# 便捷解析：取项目规模档位（软提示；未设回退 'T1' 标准档）。
function Get-ScaffoldProjectTier {
  $t = $script:ScaffoldConfig.ProjectTier
  if (-not $t) { return 'T1' }
  return $t
}

# 便捷解析：取 R3 评审状态检查名（单一来源；留空回退向后兼容默认 'codex-review'）。
# ContainsKey 守卫：旧 _config（未含该键）在 StrictMode 下直接取键会抛——优雅退回默认。
function Get-ScaffoldReviewStatusContext {
  if ($script:ScaffoldConfig.ContainsKey('ReviewStatusContext') -and $script:ScaffoldConfig.ReviewStatusContext) {
    return $script:ScaffoldConfig.ReviewStatusContext
  }
  return 'codex-review'
}

# 便捷解析：取 R3 评审模型（留空 '' => 由评审后端自身默认决定，不传 -m）。
# ContainsKey 守卫：旧 _config（未含该键）在 StrictMode 下直接取键会抛——优雅退回 ''（= 后端默认，行为不变）。
function Get-ScaffoldReviewModel {
  if ($script:ScaffoldConfig.ContainsKey('ReviewModel')) { return [string]$script:ScaffoldConfig.ReviewModel }
  return ''
}

# 便捷解析：取源脚本 ↔ 权威文档耦合表；旧配置无此键时优雅降级为空表。
function Get-ScaffoldDocSyncMap {
  if ($script:ScaffoldConfig.ContainsKey('DocSyncMap') -and $script:ScaffoldConfig.DocSyncMap) { return $script:ScaffoldConfig.DocSyncMap }
  return @{}
}

# 便捷解析：取 R3 推理档位（留空 '' => 后端默认）。合法值校验在 review.ps1（只对默认 codex 路径生效，L26）。
function Get-ScaffoldReviewEffort {
  if ($script:ScaffoldConfig.ContainsKey('ReviewEffort')) { return [string]$script:ScaffoldConfig.ReviewEffort }
  return ''
}
