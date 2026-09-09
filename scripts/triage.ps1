#requires -Version 7
<#
.SYNOPSIS
  脚手架的「心跳」(heartbeat)：按节律(cadence)对本仓做一次**只读、离线、确定性**的扫描，
  发现「待办信号」汇成 triage 收件箱——把 loop-engineering 的「自动发现 + 分诊」机械化，
  让人/agent 只需读收件箱、决定**做哪件**，而非手动巡检各子系统。

.DESCRIPTION
  动机（addy osmani《Loop Engineering》组件①「the heartbeat」+ Anthropic《Recursive Self-Improvement》
  「把 perspiration 自动化、人保留 direction-setting」）：脚手架原本全是**按需**触发（task start/ship/cleanup），
  缺一个定期**发现**待办的回路。本脚本扫描既有子系统的本地信号（**不打网络/不调 gh**；读取本地文件，
  delivery-blocked 另以离线 `git rev-parse HEAD` 把裁决绑定到当前检出）：
    - lessons-promote : LEDGER 里仍在 ledger 层、却已达晋升门槛（recurrence≥2 或 severity=blocking）的经验
    - tech-debt-open  : specs/tech-debt-tracker.md 里 status=open 的债项（持续小额还债的待还队列）
    - cards-active    : specs/tasks/*.md 里 status=in-progress|in-review 的在飞卡（可能待续/待评审）
    - handoff-open    : cwd 若有 progress.md，其 HANDOFF STATUS≠done/handoff-ready（交接未收口）；
                        另查 in-progress|in-review 卡的 worktree 内 progress.md（主检出续接不再对 worktree 交接失明）
    - lessons-cap     : 必须层（CLAUDE.md 经验铁律）**驻留经验 id 数**达封顶（minor）/ 超封顶（major）——
                        计量单位是 id 不是条目；小节标题找不到时按 fail-closed 报（该做减法了，见 HARNESS-REVIEW）
    - harness-refresh : judgment 经验累积达门槛——该双向自我改进（删旧闸 + 主动搜更优工具/方法纳新，见 HARNESS-REVIEW / L26）
    - effectiveness   : _local/effectiveness-ledger.jsonl 里各闸拦截计数——喂 HARNESS-REVIEW 据计数+ship 次数做减法（TD2；TD9 分母经 review 否决，见 ADR 0003）
    - worktree-orphan : WorktreeRoot 下卡已 merged 却没拆的残留 worktree（cleanup 漏跑 / 半合并遗留，TD3）
    - lessons-demote  : 必须层里已被确定性守卫覆盖的**驻留经验 id**——每轮上下文换来的是机器已在做的事（上游 issue #183 的逆向半）
    - delivery-blocked: 在飞卡坐在一份 R3 block 裁决上却没人接回注意力（**唯一读交付状态的探针**，上游 issue #185）
    - scaffold-stale  : 本地已取到的上游脚手架 tag 尚有未逐版裁决者（离线，只提示显式 check -Fetch）
  每信号产出一条 finding（severity + 一行 what + 建议的下一步命令），汇成 markdown 收件箱。
  **只发现、不行动**：绝不写仓内被跟踪文件、绝不 git/gh 写操作；act 走既有交付链
  （task-loop skill / lessons promote / 开卡偿还 / handoff check）。退出码恒 0（reporter，非闸门）。

  收件箱默认写到 _local/triage-inbox.md（gitignored，运行时态）。无 _config 依赖也能跑（优雅降级）。

.PARAMETER Verb     scan（扫描并写收件箱+打印摘要） | list（只打印上次收件箱，不重扫） |
                    selfcheck（探针 1/4/5/10/11 的 hermetic 自检：临时夹具、输出断言，见该段头注）。默认 scan。
.PARAMETER OutFile  收件箱路径（默认 _local/triage-inbox.md）。
.PARAMETER NoWrite  只报不写（selftest 干跑用：核验扫描在默认配置下不抛异常）。
.PARAMETER Quiet    静默：仅退出码与一行计数，不打印 finding 明细。
.PARAMETER CaseModeProfile portable 默认；require-dual-actual 要求双实际模式。
.EXAMPLE
  pwsh -File scripts\triage.ps1                 # 扫描，写 _local/triage-inbox.md
.EXAMPLE
  pwsh -File scripts\triage.ps1 scan -NoWrite   # 只报不写（selftest 用）
.EXAMPLE
  pwsh -File scripts\triage.ps1 selfcheck       # 探针 1/4/5/10/11 自检（末行 'triage selfcheck: PASS' 即绿）
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][ValidateSet('scan', 'list', 'selfcheck')][string]$Verb = 'scan',
  [string]$OutFile,
  [switch]$NoWrite,
  [switch]$Quiet,
  [ValidateSet('portable', 'require-dual-actual')][string]$CaseModeProfile = 'portable'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open
. (Join-Path $PSScriptRoot '_cards.ps1')
. (Join-Path $PSScriptRoot '_lessons.ps1')   # 必须层驻留规则 + enforced_by 的共享判定核（上游 v0.43.0）
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# _config 仅取 LessonsMustCap；缺失/留空亦能跑（fail-safe 默认）。
$MustCap = 10
try {
  . (Join-Path $PSScriptRoot '_config.ps1')
  if ($ScaffoldConfig -and $ScaffoldConfig.LessonsMustCap) { $MustCap = [int]$ScaffoldConfig.LessonsMustCap }
} catch { }

if (-not $OutFile) { $OutFile = Join-Path $RepoRoot '_local/triage-inbox.md' }

$Ledger    = Join-Path $RepoRoot 'docs/lessons/LEDGER.md'
$TechDebt  = Join-Path $RepoRoot 'specs/tech-debt-tracker.md'
$TasksDir  = Join-Path $RepoRoot 'specs/tasks'
$ClaudeMd  = Join-Path $RepoRoot 'CLAUDE.md'

# 晋升候选的批量阈值（上游 issue #185）：一条候选报一条 finding 在结构上无界——每次扫描都把
# 全部合格经验重报一遍，而处理一条是多 PR 的仪式，于是 N 条候选投影成约 3N 个 PR。超过阈值就
# 改口径：请求走一次批量复审，而不是派 N 份独立的活。
$PromoteBatchSize = 5
# finding 累加器：每条 = @{ probe; severity(blocking|major|minor); what; next }
$findings = [System.Collections.Generic.List[object]]::new()
function Add-Finding($probe, $severity, $what, $next) {
  $findings.Add([pscustomobject]@{ probe = $probe; severity = $severity; what = $what; next = $next })
}

# 只认**末尾**的 HANDOFF 块（同 handoff.ps1 Read-Block 契约）：progress.md 若被误 append 新块而非
# 原地编辑，[regex]::Match 的懒惰首匹配会读到过期首块——TD57/TD-120。返回 Match 对象或 $null。
function Get-LastHandoffBlock([string]$text) {
  $ms = [regex]::Matches($text, '(?s)<!--\s*HANDOFF:START\s*-->(.*?)<!--\s*HANDOFF:END\s*-->')
  if ($ms.Count -gt 0) { return $ms[$ms.Count - 1] } else { return $null }
}

# ── 探针 1：lessons-promote（LEDGER 里仍在 ledger 层却已达晋升门槛）──
function Invoke-ProbeLessons {
  if (-not (Test-Path $Ledger)) { return }
  $cands = [System.Collections.Generic.List[object]]::new()

  $raw = Get-Content $Ledger -Raw
  $blocks = [regex]::Split($raw, '(?m)^##\s+(?=L\d)') | Where-Object { $_ -match '^L\d' }
  foreach ($b in $blocks) {
    $id   = ([regex]::Match($b, '^(L\d+)')).Groups[1].Value
    $tier = ([regex]::Match($b, 'tier:\s*(\w+)')).Groups[1].Value
    $sev  = ([regex]::Match($b, 'severity:\s*(\w+)')).Groups[1].Value
    $recM = [regex]::Match($b, 'recurrence:\s*(\d+)')
    $rec  = if ($recM.Success) { [int]$recM.Groups[1].Value } else { 0 }
    $enf  = Get-ScaffoldLessonEnforcedBy $b
    # 只盯**未经分层（ledger）**且达客观门槛者；ondemand/must 已是有意安置，不打扰。
    # enforced_by 闸（上游 issue #183）：已有确定性守卫盯住的坑，不该再花每轮上下文去重复讲一遍——
    # docs/HARNESS-REVIEW.md 两处都是这么说的，而该字段本就被 lessons.ps1 解析并当真。判定核与
    # promote 动词共用（_lessons.ps1），两处不会漂移。
    if ($tier -eq 'ledger' -and ($rec -ge 2 -or $sev -eq 'blocking') -and -not (Test-ScaffoldLessonGuarded $enf)) {
      $why = if ($sev -eq 'blocking') { "severity=blocking" } else { "recurrence=$rec" }
      $cands.Add([pscustomobject]@{ id = $id; why = $why })
    }
  }
  if ($cands.Count -gt $PromoteBatchSize) {
    Add-Finding 'lessons-promote' 'major' `
      "$($cands.Count) 条无守卫经验已达晋升门槛（$((($cands | ForEach-Object id) -join ', '))）——按**一批**复审，别一条开一张卡。" `
      "下次 docs\HARNESS-REVIEW.md 复审时整批过：每条优先加确定性闸并记进 enforced_by，只有闸盖不住的才升必须层。"
  } else {
    foreach ($c in $cands) {
      Add-Finding 'lessons-promote' 'major' `
        "$($c.id) 仍在总账层、已达晋升门槛（$($c.why)）且无机械守卫——下次仍可能重导。" `
        "优先加确定性闸并记进 enforced_by；闸盖不住才升层：pwsh -File scripts\lessons.ps1 promote $($c.id)"
    }
  }
}

# ── 探针 2：tech-debt-open（status=open 的债项）──
# 分列助手 Split-TdRow 共享自 _cards.ps1。
function Invoke-ProbeTechDebt {
  if (-not (Test-Path $TechDebt)) { return }
  $lines = Get-Content $TechDebt
  # 状态列位置从表头行（首列字面 'id'）动态定位「状态」文本所在列，不再硬编码 5——
  # 防表结构调整时静默错位；表头缺失/改名时兜底回旧默认位置（fail-safe，非致命 reporter）。
  $statusIdx = 5
  foreach ($line in $lines) {
    if ($line -notmatch '^\s*\|') { continue }
    $cells = Split-TdRow $line
    if ($cells.Count -ge 1 -and $cells[0] -eq 'id') {
      $idx = [array]::IndexOf($cells, '状态')
      if ($idx -ge 0) { $statusIdx = $idx }
      break
    }
  }
  foreach ($line in $lines) {
    if ($line -notmatch '^\s*\|') { continue }
    $cells = Split-TdRow $line
    if ($cells.Count -lt 2) { continue }
    $id = $cells[0]
    # 跳过表头、分隔行、示例行
    if ($id -match '^-+$' -or $id -eq 'id' -or $id -match '示例|example') { continue }
    if ($cells.Count -le $statusIdx) {
      # 列数不足状态列位置：曾静默 continue（漏检不可见）——改 fail-loud 警告，指名该债项，
      # 但仍不阻断心跳（reporter 契约恒 exit 0）。
      Write-Warning "triage tech-debt 探针：债项 $id 行结构异常（列数 $($cells.Count) ≤ 状态列索引 $statusIdx）——已警告而非静默跳过，请检查该行是否有未转义的竖线字符或缺列。"
      continue
    }
    $status = $cells[$statusIdx]
    # 大小写不敏感前缀匹配（PowerShell -match 默认不区分大小写）：覆盖 Open / open (partial) 等
    # 变体，不再要求与 'open' 精确相等。
    if ($status -match '^open') {
      Add-Finding 'tech-debt-open' 'major' `
        "技术债 $id 未还（$($cells[2])）：$($cells[3])" `
        "转卡偿还（specs\tasks\<id>.md）或记 ADR（docs\adr\）接受，并在 tech-debt-tracker 改 status。"
    }
  }
}

# ── 探针 3：cards-active（在飞卡）──
function Invoke-ProbeCards {
  if (-not (Test-Path $TasksDir)) { return }
  $cards = Get-ChildItem $TasksDir -Filter *.md -ErrorAction SilentlyContinue | Where-Object Name -ne '_TEMPLATE.md'
  foreach ($c in $cards) {
    $fm = Get-FrontMatter (Get-Content $c.FullName -Raw)
    if (-not $fm) { continue }
    $status = ([regex]::Match($fm, '(?m)^status\s*:\s*(.*?)\s*$')).Groups[1].Value
    $id = [IO.Path]::GetFileNameWithoutExtension($c.FullName)
    if ($status -eq 'in-progress') {
      Add-Finding 'cards-active' 'minor' "卡 $id 在施工（in-progress）——确认未被中断遗留。" "pwsh -File scripts\task.ps1 -TaskId $id -Phase ship"
    } elseif ($status -eq 'in-review') {
      Add-Finding 'cards-active' 'minor' "卡 $id 待评审（in-review）——R3 闸门可能未收口。" "pwsh -File scripts\review.ps1（或重跑 task.ps1 ship）"
    }
  }
}

# ── 探针 4：handoff-open（cwd 交接未收口 + 在飞卡 worktree 内的交接未收口）──
# 交接曾严格 cwd 视角：主检出续接时看不见卡 worktree 里写的 HANDOFF。故对 in-progress|in-review 卡
# 额外查 <WorktreeRoot>\<id>\progress.md；worktree 即当前 cwd 时跳过（防重复发现）。配置留空/WorktreeRoot 异常
# 时 try/catch 优雅跳过（同探针 9）。仍是 reporter：只读、恒退出 0、零 gh 写。
function Invoke-ProbeHandoff {
  $cwd = (Get-Location).Path.TrimEnd('\', '/')
  $prog = Join-Path $cwd 'progress.md'
  if (Test-Path $prog) {
    $m = Get-LastHandoffBlock (Get-Content $prog -Raw)
    if ($m) {
      $status = ([regex]::Match($m.Groups[1].Value, '(?m)^\s*STATUS:\s*(.*?)\s*$')).Groups[1].Value
      if ($status -and $status -notin @('done', 'handoff-ready')) {
        Add-Finding 'handoff-open' 'major' "cwd 交接未收口（STATUS=$status）——下个 session 续接前须填好 HANDOFF 块。" "pwsh -File scripts\handoff.ps1 check"
      }
    }
  }
  try {
    $wtRoot = Get-ScaffoldWorktreeRoot
    if (-not $wtRoot -or -not (Test-Path $TasksDir)) { return }
    $cards = Get-ChildItem $TasksDir -Filter *.md -ErrorAction SilentlyContinue | Where-Object Name -ne '_TEMPLATE.md'
    foreach ($c in $cards) {
      $fm = Get-FrontMatter (Get-Content $c.FullName -Raw)
      if (-not $fm) { continue }
      $status = ([regex]::Match($fm, '(?m)^status\s*:\s*(.*?)\s*$')).Groups[1].Value
      if ($status -notin @('in-progress', 'in-review')) { continue }
      $id = [IO.Path]::GetFileNameWithoutExtension($c.FullName)
      $wtDir = (Join-Path $wtRoot $id).TrimEnd('\', '/')
      if ($wtDir -eq $cwd) { continue }   # worktree 即当前 cwd → 上方已查，跳过防重复发现
      $wtProg = Join-Path $wtDir 'progress.md'
      if (-not (Test-Path $wtProg)) { continue }
      $wm = Get-LastHandoffBlock (Get-Content $wtProg -Raw)
      if (-not $wm) { continue }
      $wtStatus = ([regex]::Match($wm.Groups[1].Value, '(?m)^\s*STATUS:\s*(.*?)\s*$')).Groups[1].Value
      if ($wtStatus -and $wtStatus -notin @('done', 'handoff-ready')) {
        Add-Finding 'handoff-open' 'major' `
          "卡 $id 的 worktree 交接未收口（STATUS=$wtStatus）——主检出续接前先读它的 HANDOFF 块。" `
          "pwsh -File scripts\handoff.ps1 show -Path $wtProg"
      }
    }
  } catch { return }
}

# ── 探针 5：lessons-cap（必须层驻留 id 逼近/超过封顶；单位是经验 id，非 markdown 条目）──
function Invoke-ProbeCap {
  if (-not (Test-Path $ClaudeMd)) { return }
  # 计量单位是**驻留的经验 id**，不是 markdown 条目（上游 issue #184）：把多个 id 并进一条 bullet
  # 曾经既满足封顶、又让驻留规则数继续涨。判定核与 lessons.ps1 check 共用（_lessons.ps1）。
  $sec = Get-ScaffoldMustLayerSection -Path $ClaudeMd
  if (-not $sec.Found) {
    # 标题漂移或重复驻留时继续计数都会假绿；「测不准」必须报出来（fail-closed）。
    $detail = if ($sec.Reason -eq 'DUPLICATE-RESIDENT-ID') { "重复驻留 id：$(@($sec.DuplicateIds) -join ', ')" } else { '找不到「经验铁律」小节（标题漂移？）' }
    Add-Finding 'lessons-cap' 'major' "$($sec.Sentinel) CLAUDE.md $detail——封顶已无从可靠计量。" "修正小节后 pwsh -File scripts\lessons.ps1 check 复核（该命令同样按此 fail-closed）。"
    return
  }
  $n = @($sec.Ids).Count
  if ($n -gt $MustCap) {
    Add-Finding 'lessons-cap' 'major' "必须层已驻留 $n/$MustCap 个经验 id（**超封顶**）——每轮上下文成本已越线，须先做减法。" "走 docs\HARNESS-REVIEW.md：淘汰最不活跃项回按需层，再 pwsh -File scripts\lessons.ps1 check 复核。"
  } elseif ($n -ge $MustCap) {
    Add-Finding 'lessons-cap' 'minor' "必须层已驻留 $n/$MustCap 个经验 id（达封顶）——再加铁律前须先做减法。" "走 docs\HARNESS-REVIEW.md：淘汰最不活跃项回按需层。"
  }
}

# ── 探针 6：harness-refresh（judgment 经验累积 → 该双向自我改进：删旧闸 + 主动搜更优工具/方法纳新）──
# self-improvement 的另一极：HARNESS-REVIEW 不只"做减法删闸门"，也"做加法/替换——搜更优工具/方法纳入"（L26）。
# judgment 经验是"方向/工具品味可能更优"的标记；累积到门槛即提醒走双向复审。reporter，非闸门。
function Invoke-ProbeRefresh {
  if (-not (Test-Path $Ledger)) { return }
  $raw = Get-Content $Ledger -Raw
  $blocks = [regex]::Split($raw, '(?m)^##\s+(?=L\d)') | Where-Object { $_ -match '^L\d' }
  $jud = @($blocks | Where-Object { $_ -match '(?m)kind:\s*judgment' })
  if ($jud.Count -ge 3) {
    Add-Finding 'harness-refresh' 'minor' `
      "judgment 经验已积累 $($jud.Count) 条——self-improvement 宜双向：删旧闸 + 主动搜更优工具/方法纳新。" `
      "走 docs\HARNESS-REVIEW.md：逐条复审 judgment（方向品味是否提升）+ 评估更优工具/方法替换（见 L26：方法论优先于工具）。"
  }
}

# ── 探针 8：effectiveness（效果账本：各 ship 闸的真实拦截计数 → 喂 HARNESS-REVIEW 据计数 + ship 次数做减法）──
# task.ps1 ship 真拦截时写 _local/effectiveness-ledger.jsonl（TD2）：拦截记一行 {gate}。本探针只**汇总暴露各闸拦截数**，
# 不算比率（denominator/ship 次数靠 git/PR history 人工判，非账本内——见 ADR 0003：denominator 探针化被 review 否决）、
# 不替你判减法（0 拦截≠该删：可能沉默防护，HARNESS-REVIEW 的活）。
# fail-safe：任何坏行（非对象 / 缺 gate / 非法 JSON）一律跳过——绝不让一行坏数据崩掉心跳（退出码须恒 0）。
function Invoke-ProbeEffectiveness {
  # 账本路径可被 $env:SCAFFOLD_EFFECTIVENESS_LEDGER 覆盖——仅供 selftest 12b 注入隔离临时账本（hermetic，不碰生产 _local 文件）。
  $ledger = if ($env:SCAFFOLD_EFFECTIVENESS_LEDGER) { $env:SCAFFOLD_EFFECTIVENESS_LEDGER } else { Join-Path $RepoRoot '_local/effectiveness-ledger.jsonl' }
  if (-not (Test-Path $ledger)) { return }
  $byGate = @{}; $total = 0
  foreach ($line in (Get-Content $ledger -ErrorAction SilentlyContinue)) {
    if (-not ($line -and $line.Trim())) { continue }
    $t = $line.Trim()
    if (-not $t.StartsWith('{')) { continue }   # 只认对象行 {…}：数组 [..]/标量/null/空串先按开头字符筛掉（治单元素数组 [{…}] 被管道解包误计）
    $g = $null
    try {
      $o = $t | ConvertFrom-Json
      # 整段 try/catch 是 **load-bearing 兜底**（勿收窄回只裹 ConvertFrom-Json，会重现 ADR 0003 崩溃）：
      # `{not json` 解析抛、空对象 `{}` 在 strict-mode 下 .PSObject.Properties.Name 访问也抛——都靠它 catch→跳过。
      if (($o -is [System.Management.Automation.PSCustomObject]) -and ($o.PSObject.Properties.Name -contains 'gate')) { $g = $o.gate }
    } catch { continue }
    if (-not $g) { continue }
    if ($byGate.ContainsKey($g)) { $byGate[$g]++ } else { $byGate[$g] = 1 }
    $total++
  }
  if ($total -lt 1) { return }
  $known = @('dod', 'license', 'secrets', 'review')
  $breakdown = ($known | ForEach-Object { $c = if ($byGate.ContainsKey($_)) { $byGate[$_] } else { 0 }; "${_}:$c" }) -join ' '
  $zero = @($known | Where-Object { -not $byGate.ContainsKey($_) })
  $zeroNote = if ($zero.Count) { "；0 拦截（须结合 ship 次数判沉默防护）：$($zero -join ', ')" } else { '' }
  Add-Finding 'effectiveness' 'minor' `
    "效果账本：$total 次闸拦截（$breakdown）$zeroNote——喂 HARNESS-REVIEW 据各闸拦截数 + ship 次数（看 history）做减法判断。" `
    "读 _local\effectiveness-ledger.jsonl；走 docs\HARNESS-REVIEW.md：多次 ship 仍 0 拦截的闸才是减法候选（denominator 看 git/PR history）。"
}

# ── 探针 9：worktree-orphan（卡已 merged 却 worktree 没拆 → cleanup 漏跑 / 半合并遗留）──
# 治盲点#3（ship 非原子；被中断/漏 cleanup 会留残留 worktree，triage 此前看不到）。纯文件：列 WorktreeRoot 下目录，
# 对每个 <TaskId> 看本仓 specs/tasks/<TaskId>.md——status=merged 还留着 = 该拆没拆。无对应卡的目录跳过
# （WorktreeRoot 可能跨项目共享，避免误报他仓 worktree）。WorktreeRoot 不存在即跳过。reporter 非闸门。
function Invoke-ProbeOrphanWorktree {
  $wtRoot = $null
  try { $wtRoot = Get-ScaffoldWorktreeRoot } catch { return }
  if (-not $wtRoot -or -not (Test-Path $wtRoot)) { return }
  foreach ($d in (Get-ChildItem -Path $wtRoot -Directory -ErrorAction SilentlyContinue)) {
    $card = Join-Path $TasksDir "$($d.Name).md"
    if (-not (Test-Path $card)) { $card = Join-Path $RepoRoot "specs/archive/tasks/$($d.Name).md" }   # 卡可能已归档（merged → specs/archive/tasks/，见 archive.ps1）——续查冷存，别对已归档的 merged 卡漏报孤儿 worktree
    if (-not (Test-Path $card)) { continue }   # 两处都无对应卡 → 可能是他仓 worktree，跳过避免误报
    $fm = Get-FrontMatter (Get-Content $card -Raw)
    if (-not $fm) { continue }
    $status = ([regex]::Match($fm, '(?m)^status\s*:\s*(.*?)\s*$')).Groups[1].Value
    if ($status -eq 'merged') {
      Add-Finding 'worktree-orphan' 'major' `
        "卡 $($d.Name) 已 merged 却仍留 worktree（$($d.FullName)）——cleanup 漏跑 / 半合并遗留。" `
        "确认无未推改动后：pwsh -File scripts\task.ps1 -TaskId $($d.Name) -Phase cleanup"
    }
  }
}

# ── 探针 10：lessons-demote（探针 1 的逆向；上游 issue #183 的另一半）──
# 一条已有确定性守卫盯住的规则，坐在必须层就是**永久**每轮成本，换来的是机器已经在做的事。
# 只报不动：降层是 HARNESS-REVIEW 的判断，不是心跳的。
function Invoke-ProbeLessonsDemote {
  if (-not (Test-Path $Ledger)) { return }
  $raw = Get-Content $Ledger -Raw
  $blocks = [regex]::Split($raw, '(?m)^##\s+(?=L\d)') | Where-Object { $_ -match '^L\d' }
  foreach ($b in $blocks) {
    $id   = ([regex]::Match($b, '^(L\d+)')).Groups[1].Value
    $tier = ([regex]::Match($b, 'tier:\s*(\w+)')).Groups[1].Value
    $enf  = Get-ScaffoldLessonEnforcedBy $b
    if ($tier -eq 'must' -and (Test-ScaffoldLessonGuarded $enf)) {
      Add-Finding 'lessons-demote' 'minor' `
        "$id 坐在必须层（每轮、永久），但机器已在守它：$enf" `
        "下次 docs\HARNESS-REVIEW.md 复审时判：从 CLAUDE.md 铁律小节摘掉该条、LEDGER 改 tier: ondemand，再 pwsh -File scripts\lessons.ps1 check"
    }
  }
}

# ── 探针 11：delivery-blocked（在飞卡坐在 R3 的 block 裁决上，却没人把这个结果接回注意力）──
# 补的洞（上游 issue #185）：其余探针读的全是脚手架自身状态，于是收件箱可以很热闹、而关键路径其实停着——
# 且箱里每一条可行动项都是脚手架自我维护。`cards-active` 读的是 `status:`（作者意图），坐在 block 上的卡
# 与正在推进的卡长得一模一样。
# 刻意**离线**、不调 gh：心跳不把外部信号当决策（docs/LOOP-ENGINEERING.md），而这个信号本就不需要网络——
# review.ps1 每次跑都把归一化裁决写进 <worktree>/.review/<分支>.json；探针另以本地 `git rev-parse HEAD`
# 拒绝不属于当前检出的旧裁决。severity 取 blocking（既有排序表的最高档），
# 于是「交付停摆」排在一切自我维护发现之上，无须新增排序码。
function Get-ScaffoldRepositoryHead {
  param([Parameter(Mandatory)][string]$Path)
  try {
    $head = (& git -C $Path rev-parse HEAD 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -eq 0 -and $head -cmatch '^[0-9a-f]{40}$') { return $head }
  } catch { }
  return $null
}

function Get-ScaffoldEvidencePathComparer {
  param([Parameter(Mandatory)][string]$Directory, [Parameter(Mandatory)][object[]]$Files)
  # Existing colliding leaves prove sensitivity. Otherwise, alter one cased character in an
  # existing leaf and resolve it read-only: success proves insensitive; a missing alternate
  # proves sensitive. Callers provide the full JSON directory listing, so an existing distinct
  # alternate was already caught above. No OS/volume name, localized tool output, or sentry
  # write is trusted.
  $folded = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
  foreach ($file in $Files) {
    if (-not $folded.Add([string]$file.Name)) { return [System.StringComparer]::Ordinal }
  }
  foreach ($file in $Files) {
    $name = [string]$file.Name
    $alternate = $null
    for ($i = 0; $i -lt $name.Length; $i++) {
      $ch = $name[$i]; $upper = [char]::ToUpperInvariant($ch); $lower = [char]::ToLowerInvariant($ch)
      if ([int]$upper -ne [int]$lower) {
        $replacement = if ($ch -ceq $upper) { $lower } else { $upper }
        $alternate = $name.Substring(0, $i) + [string]$replacement + $name.Substring($i + 1)
        break
      }
    }
    if (-not $alternate) { continue }
    try {
      $null = Get-Item -LiteralPath (Join-Path $Directory $alternate) -ErrorAction Stop
      return [System.StringComparer]::OrdinalIgnoreCase
    } catch [System.Management.Automation.ItemNotFoundException] {
      return [System.StringComparer]::Ordinal
    } catch {
      return $null
    }
  }
  return $null
}

function Invoke-ProbeDeliveryBlocked {
  if (-not (Test-Path $TasksDir)) { return }
  $wtRoot = $null
  try { $wtRoot = Get-ScaffoldWorktreeRoot } catch { $wtRoot = $null }
  $hits = @()
  foreach ($c in (Get-ChildItem $TasksDir -Filter *.md -ErrorAction SilentlyContinue | Where-Object Name -ne '_TEMPLATE.md')) {
    $fm = Get-FrontMatter (Get-Content $c.FullName -Raw)
    if (-not $fm) { continue }
    $status = ([regex]::Match($fm, '(?m)^status\s*:\s*(.*?)\s*$')).Groups[1].Value
    if ($status -notin @('in-progress', 'in-review')) { continue }
    $id = [IO.Path]::GetFileNameWithoutExtension($c.FullName)
    # 卡自己的 worktree 存着为它写过的每一份裁决；主检出里那份按分支名落盘（-Local ship），
    # 故在主检出只有 <id>.json 可能属于本卡。
    $evidenceFiles = @()
    if ($wtRoot) {
      $cardWorktree = Join-Path $wtRoot $id
      $wtReview = Join-Path $cardWorktree '.review'
      $wtReviewExists = $false
      try { $wtReviewExists = Test-Path $wtReview -ErrorAction Stop }
      catch {
        Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-ENUM] 卡 $id 的 worktree evidence 目录无法发现：$($_.Exception.Message)" "检查 $wtReview 的目录/权限后重跑 triage；裁决状态当前未知。"
      }
      if ($wtReviewExists) {
        try { $wtFiles = @(Get-ChildItem $wtReview -Filter *.json -ErrorAction Stop) }
        catch {
          Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-ENUM] 卡 $id 的 worktree evidence 目录无法枚举：$($_.Exception.Message)" "检查 $wtReview 的目录/权限后重跑 triage；裁决状态当前未知。"
          $wtFiles = @()
        }
        if ($wtFiles.Count) {
          $wtComparer = Get-ScaffoldEvidencePathComparer -Directory $wtReview -Files $wtFiles
          if ($null -eq $wtComparer) {
            Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-CASE] 卡 $id 的 worktree evidence 目录 caseMode 无法只读判定。" "检查 $wtReview 的实际目录 caseMode；triage 不会在真实 evidence 目录创建探针文件。"
          } else {
            $evidenceFiles += @($wtFiles | ForEach-Object { [pscustomobject]@{ File = $_; Root = $cardWorktree; SourceRank = 0; PathComparer = $wtComparer } })
          }
        }
      }
    }
    $localReview = Join-Path (Join-Path $RepoRoot '.review') "$id.json"
    $localReviewExists = $false
    try { $localReviewExists = Test-Path $localReview -ErrorAction Stop }
    catch {
      Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-READ] 卡 $id 的 local evidence 无法发现：$($_.Exception.Message)" "检查 $localReview 的文件/权限后重跑 triage；裁决状态当前未知。"
    }
    if ($localReviewExists) {
      $localReviewDirectory = Split-Path -Parent $localReview
      try {
        $localFile = Get-Item $localReview -ErrorAction Stop
        $localFiles = @(Get-ChildItem $localReviewDirectory -Filter *.json -ErrorAction Stop)
      }
      catch {
        Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-READ] 卡 $id 的 local evidence 无法读取：$($_.Exception.Message)" "检查 $localReview 的文件/权限后重跑 triage。"
        $localFile = $null; $localFiles = @()
      }
      if ($localFile) {
        $localComparer = Get-ScaffoldEvidencePathComparer -Directory $localReviewDirectory -Files $localFiles
        if ($null -eq $localComparer) {
          Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-CASE] 卡 $id 的 local evidence 目录 caseMode 无法只读判定。" "检查 $localReviewDirectory 的实际目录 caseMode；triage 不会在真实 evidence 目录创建探针文件。"
        } else {
          $evidenceFiles += [pscustomobject]@{ File = $localFile; Root = $RepoRoot; SourceRank = 1; PathComparer = $localComparer }
        }
      }
    }
    # 两条取证路径会互相干扰，各有一种坏法：
    #   ① **同一份裁决被数两次**——卡的 worktree 恰是主检出时，通配与按 id 拼出的路径指向同一个文件；
    #   ② **捞到别人的 block**——worktree 侧是 `*.json` 通配，别的分支在同一 .review 里留下的裁决会被当成本卡的。
    # 故先按全路径去重，再要求产物**自证属于本卡**。
    # 去重键 = `$vf.FullName`：FileInfo 的 FullName 本就是完全限定并已折叠 `.` / `..` 段的路径（实测
    # `Get-Item <dir>\a\..\a\.review\X.json` 交出的 FullName 已无 `..`），故再套一层 [IO.Path]::GetFullPath
    # 是恒等变换、摘掉它没有任何用例会红——那样的守卫只会让人误以为这里已经防住了什么。
    # **唯一真会变的是大小写**。去重与 branchless 旧产物的文件名归属都走各自 evidence 目录实测的路径语义；
    # branch/verdict 则是 JSON schema 字段，始终逐字精确，不能借文件系统语义放宽。
    $seenPath = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $headByRoot = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::Ordinal)
    $verdictCandidates = @()
    foreach ($evidence in $evidenceFiles) {
      $vf = $evidence.File
      # caseMode 属于 evidence 目录：只折叠它管理的 leaf，保留敏感祖先目录中的真实 root 身份。
      $identityPath = if ($evidence.PathComparer.Equals([System.StringComparer]::OrdinalIgnoreCase)) {
        Join-Path (Split-Path -Parent $vf.FullName) $vf.Name.ToUpperInvariant()
      } else { $vf.FullName }
      if (-not $seenPath.Add($identityPath)) { continue }   # ① 同一文件只算一次
      $verdict = ''; $reasons = 0; $owner = ''; $artifactSha = ''
      try { $rawEvidence = Get-Content $vf.FullName -Raw -ErrorAction Stop }
      catch {
        Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-READ] 卡 $id 的 evidence 无法读取：$($vf.FullName)（$($_.Exception.Message)）" "检查该 evidence 文件/权限后重跑 triage。"
        continue
      }
      try {
        $o = $rawEvidence | ConvertFrom-Json -ErrorAction Stop
        if ($o -and ($o.PSObject.Properties.Name -contains 'verdict')) { $verdict = [string]$o.verdict }
        if ($o -and ($o.PSObject.Properties.Name -contains 'reasons')) { $reasons = @($o.reasons).Count }
        if ($o -and ($o.PSObject.Properties.Name -contains 'branch'))  { $owner   = [string]$o.branch }
        if ($o -and ($o.PSObject.Properties.Name -contains 'sha'))     { $artifactSha = [string]$o.sha }
      } catch {
        Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-PARSE] 卡 $id 的 evidence JSON 无法解析：$($vf.FullName)（$($_.Exception.Message)）" "修复或移除损坏 evidence 后重跑 triage。"
        continue
      }
      if ($owner) {
        if (-not [string]::Equals($owner, $id, [System.StringComparison]::Ordinal)) { continue }
      } elseif (-not $evidence.PathComparer.Equals([IO.Path]::GetFileNameWithoutExtension($vf.Name), $id)) {
        continue
      }
      if (-not $headByRoot.ContainsKey($evidence.Root)) {
        $resolvedHead = Get-ScaffoldRepositoryHead -Path $evidence.Root
        if (-not $resolvedHead) {
          Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-HEAD] 卡 $id 的 evidence root HEAD 无法读取：$($evidence.Root)" "检查该 evidence root 的 Git 状态后重跑 triage。"
          $headByRoot[$evidence.Root] = ''
        } else { $headByRoot[$evidence.Root] = $resolvedHead }
      }
      if (-not $artifactSha) {
        Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-IDENTITY] 卡 $id 的 evidence 缺少 sha：$($vf.FullName)" "补齐可判定的 evidence identity 后重跑 triage。"
        continue
      }
      if (-not [string]::Equals($artifactSha, $headByRoot[$evidence.Root], [System.StringComparison]::Ordinal)) { continue }
      if (-not ([string]::Equals($verdict, 'pass', [System.StringComparison]::Ordinal) -or
                [string]::Equals($verdict, 'block', [System.StringComparison]::Ordinal))) {
        Add-Finding 'delivery-blocked' 'major' "[TRIAGE-EVIDENCE-VERDICT] 卡 $id 的 current evidence verdict 未知：$($vf.FullName)（$verdict）" "检查该 evidence 的 verdict enum 后重跑 triage。"
        continue
      }
      # mtime is display/sort metadata only after SHA + deterministic source selection; it never decides currency.
      $verdictCandidates += [pscustomobject]@{ id = $id; path = $vf.FullName; name = $vf.Name; verdict = $verdict; reasons = $reasons; when = $vf.LastWriteTimeUtc; sourceRank = $evidence.SourceRank }
    }
    $current = @(Select-ScaffoldCurrentVerdicts -Candidates $verdictCandidates -PathComparer ([System.StringComparer]::Ordinal))
    foreach ($candidate in $current) {
      if ($candidate.verdict -ceq 'block') { $hits += $candidate }
    }
  }
  foreach ($h in ($hits | Sort-Object when)) {     # 最旧优先：停得最久的卡先被读到
    $age = [int]((Get-Date).ToUniversalTime() - $h.when).TotalHours
    Add-Finding 'delivery-blocked' 'blocking' `
      "卡 $($h.id) 正坐在一份 block 裁决上（$($h.reasons) 条理由，约 $age 小时前）——评审干完了活，结果却没被接回注意力。" `
      "读 $($h.path)，按它点名的逐条修或拆卡后重 ship；若某条属于既有系统而非本次 diff，另开卡（L113），别让 block 悬着。"
  }
}

# ── 探针 12：scaffold-stale（落后上游脚手架几版；上游 v0.42.0 的 fleet 回路）──
# 只读**已经取到本地**的 ref 与决策账，**绝不 fetch**——完整保住「心跳只读、离线、确定性」这条刻意不变量；
# 刷新归显式的 `scaffold-sync.ps1 check -Fetch`。落后恒为**意见**、不进 ship：落后于脚手架不是停止交付本项目的理由。
function Invoke-ProbeScaffoldStale {
  $upstream = ''
  try { $upstream = Get-ScaffoldUpstreamRepo } catch { return }
  if (-not $upstream) { return }

  try { . (Join-Path $PSScriptRoot 'scaffold-sync.ps1') -AsLibrary } catch { return }

  # 元仓不把自己报成落后于自己。
  $originUrl = & git -C $RepoRoot remote get-url origin 2>$null
  if ($LASTEXITCODE -ne 0) { $originUrl = '' }
  if ((Get-ScaffoldStaleState $originUrl $upstream @() '').Status -eq 'self') { return }

  $tags = @()
  $raw = & git -C $RepoRoot for-each-ref "--format=%(refname:strip=2)" 'refs/scaffold-tags/' 2>$null
  if ($LASTEXITCODE -eq 0 -and $raw) { $tags = @($raw | Where-Object { $_ }) }

  $ledgerPath = Join-Path $RepoRoot 'docs/SCAFFOLD-SYNC.md'
  $ledgerText = if (Test-Path $ledgerPath) { Get-Content $ledgerPath -Raw } else { '' }
  $provenance = 'unknown'
  try { $provenance = Get-ScaffoldOriginVersion }
  catch { try { $provenance = Get-ScaffoldVersion } catch { } }
  try { $synced = Get-SyncedVersion $ledgerText $provenance $tags } catch {
    Add-Finding 'scaffold-stale' 'major' $_.Exception.Message '修 docs/SCAFFOLD-SYNC.md 决策账后重跑 pwsh -File scripts\scaffold-sync.ps1 check'
    return
  }

  $state = Get-ScaffoldStaleState $originUrl $upstream $tags $synced
  if ($state.Status -eq 'no-tags') {
    Add-Finding 'scaffold-stale' 'minor' `
      "本地没有任何上游脚手架 tag——本项目从未去 $upstream 看过有哪些修复可以回填。" `
      'pwsh -File scripts\scaffold-sync.ps1 check -Fetch'
    return
  }
  if ($state.Status -eq 'behind') {
    $syncedLabel = if (ConvertTo-ScaffoldVersion $synced) { "v$synced" } else { '一个未登记的基线' }
    Add-Finding 'scaffold-stale' 'major' `
      "$($state.Behind.Count) 个上游脚手架版本尚未议过（$syncedLabel -> v$($state.Latest)）。每一版都是「拿或写清为什么不拿」——跳过是正当决定，不登记不是。" `
      'pwsh -File scripts\scaffold-sync.ps1 check'
  }
}
function Select-ScaffoldCurrentVerdicts {
  param(
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Candidates,
    [Parameter(Mandatory)][System.StringComparer]$PathComparer
  )
  $groups = [System.Collections.Generic.Dictionary[string,System.Collections.Generic.List[object]]]::new($PathComparer)
  foreach ($candidate in $Candidates) {
    $key = [string]$candidate.name
    if (-not $groups.ContainsKey($key)) { $groups[$key] = [System.Collections.Generic.List[object]]::new() }
    $groups[$key].Add($candidate)
  }
  foreach ($group in $groups.Values) {
    $group | Sort-Object @{ Expression = 'sourceRank'; Descending = $false }, @{ Expression = { if ($_.verdict -ceq 'block') { 0 } else { 1 } }; Descending = $false }, @{ Expression = 'path'; Descending = $false } | Select-Object -First 1
  }
}

# ── selfcheck：探针 4（handoff-open）/ 5（lessons-cap）/ 10（lessons-demote）/ 11（delivery-blocked）与探针 1 的 hermetic 自检（R3 rubric #6：新逻辑须有自证测试）──
# 夹具全建在系统临时目录、finally 清理——绝不读写真仓/真 worktree/_local（对齐 selftest 12b 的 hermetic 模式）。
# 恪守 reporter 契约「退出码恒 0」（本卡 forbid）：核验以**输出断言**为准（同 selftest 12b 对探针 8 的
# 'dod:1' 输出断言模式）——全绿打印末行 'triage selfcheck: PASS'；任一断言失败则逐条打印 'FAIL <原因>'
# 且**无** PASS 行。常设接线（selftest 12c 断言输出含 'selfcheck: PASS'）属 selftest.ps1，另卡收编。
if ($Verb -eq 'selfcheck') {
  $fxRoot = Join-Path ([IO.Path]::GetTempPath()) "scaffold-triage-selfcheck-$PID"
  $fails = [System.Collections.Generic.List[string]]::new()
  try {
    $fixtureHeadRepo = Join-Path $fxRoot 'head-reader'
    $fixtureHooks = Join-Path $fxRoot 'empty-hooks'
    New-Item -ItemType Directory -Force $fixtureHeadRepo, $fixtureHooks | Out-Null
    & git -c 'init.templateDir=' -C $fixtureHeadRepo init -q
    & git -C $fixtureHeadRepo -c user.name=fixture -c user.email=fixture@example.invalid -c commit.gpgSign=false -c "core.hooksPath=$fixtureHooks" commit --allow-empty -m fixture -q
    $fixtureHead = (& git -C $fixtureHeadRepo rev-parse HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or (Get-ScaffoldRepositoryHead -Path $fixtureHeadRepo) -cne $fixtureHead) {
      $fails.Add('用例4a Get-ScaffoldRepositoryHead 未离线读出夹具仓当前 HEAD')
    }
    $realGetScaffoldRepositoryHead = ${function:Get-ScaffoldRepositoryHead}
    function Get-ScaffoldRepositoryHead { param([string]$Path) $null = $Path; return $fixtureHead }
    # 夹具：4 张卡覆盖四态——A in-progress+未收口(须报) / B in-review+blocked(须报) / C todo(须忽略) / D in-progress+已收口(须忽略)
    $fxCards = Join-Path $fxRoot 'cards'; $fxWt = Join-Path $fxRoot 'wt'; $fxCwd = Join-Path $fxRoot 'cwd'
    New-Item -ItemType Directory -Force $fxCards, $fxCwd | Out-Null
    foreach ($t in @(
        @{ id = 'T8-SC-A'; card = 'in-progress'; handoff = 'in-progress' },
        @{ id = 'T8-SC-B'; card = 'in-review';   handoff = 'blocked' },
        @{ id = 'T8-SC-C'; card = 'todo';        handoff = 'in-progress' },
        @{ id = 'T8-SC-D'; card = 'in-progress'; handoff = 'handoff-ready' })) {
      Set-Content -Path (Join-Path $fxCards "$($t.id).md") -Value "---`nid: $($t.id)`nstatus: $($t.card)`n---" -Encoding utf8
      $d = Join-Path $fxWt $t.id
      New-Item -ItemType Directory -Force $d | Out-Null
      Set-Content -Path (Join-Path $d 'progress.md') -Value "<!-- HANDOFF:START -->`nSTATUS: $($t.handoff)`n<!-- HANDOFF:END -->" -Encoding utf8
    }
    $TasksDir = $fxCards                                   # 注入：探针读脚本作用域 $TasksDir（仅本进程；selfcheck 打印后即退出）
    function Get-ScaffoldWorktreeRoot { $fxWt }            # 注入：影蔽 _config 的 worktree 根，指向夹具
    # 用例 1：主检出视角（cwd ≠ 任何 worktree）→ 恰报 A、B 两条，next 均为 handoff.ps1 show -Path <该 worktree progress.md>
    $findings.Clear(); Push-Location $fxCwd
    try { Invoke-ProbeHandoff } finally { Pop-Location }
    $hits = @($findings | Where-Object probe -eq 'handoff-open')
    if ($hits.Count -ne 2) { $fails.Add("用例1 期望 2 条 handoff-open（in-progress+in-review），实得 $($hits.Count)") }
    if (-not ($hits | Where-Object { $_.next -match 'handoff\.ps1 show -Path .*T8-SC-A' })) { $fails.Add('用例1 缺 in-progress 卡（A）的 show -Path 指针') }
    if (-not ($hits | Where-Object { $_.next -match 'handoff\.ps1 show -Path .*T8-SC-B' })) { $fails.Add('用例1 缺 in-review 卡（B）的 show -Path 指针') }
    # 用例 2：cwd = 卡 A 的 worktree → A 走 cwd 探针（next=check），跨 worktree 项对 A 必须去重
    $findings.Clear(); Push-Location (Join-Path $fxWt 'T8-SC-A')
    try { Invoke-ProbeHandoff } finally { Pop-Location }
    if (@($findings | Where-Object { $_.next -match 'show -Path .*T8-SC-A' }).Count -ne 0) { $fails.Add('用例2 worktree=cwd 未去重（A 被跨 worktree 重复上报）') }
    if (@($findings | Where-Object { $_.next -eq 'pwsh -File scripts\handoff.ps1 check' }).Count -ne 1) { $fails.Add('用例2 期望恰 1 条 cwd handoff-open（next=check）') }
    # ── 用例 4：delivery-blocked（探针 11）——四态：block 须报 / pass 不报 / todo 卡不报 / 坏 JSON 不崩 ──
    # 一个从不触发的探针比没有探针更糟，它读起来就像「一切正常」，故这里必须有能让它红的正例。
    foreach ($t in @(
        @{ id = 'T8-SC-A'; json = "{`"verdict`":`"block`",`"reasons`":[`"r1`",`"r2`"],`"sha`":`"$fixtureHead`"}" },   # in-progress + block → 须报
        @{ id = 'T8-SC-D'; json = "{`"verdict`":`"pass`",`"reasons`":[],`"sha`":`"$fixtureHead`"}" },             # in-progress + pass  → 不报
        @{ id = 'T8-SC-C'; json = "{`"verdict`":`"block`",`"reasons`":[`"r1`"],`"sha`":`"$fixtureHead`"}" },        # todo 卡 + block     → 不报（状态闸）
        @{ id = 'T8-SC-B'; json = '{ this is not json' })) {                       # in-review + 坏 JSON → 不崩、不报
      $rv = Join-Path (Join-Path $fxWt $t.id) '.review'
      New-Item -ItemType Directory -Force $rv | Out-Null
      Set-Content -Path (Join-Path $rv "$($t.id).json") -Value $t.json -Encoding utf8
    }
    # 本块头注承诺「绝不读写真仓」：探针的第二条取证路径是 <RepoRoot>\.review\<id>.json，$RepoRoot 若仍是
    # 真工作树，本用例就会去 Test-Path 真仓的 .review（用例 8/9/10 已各自注入，唯独这里漏了）。先注入再跑。
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'          # 注入：探针读脚本作用域 $RepoRoot
    $findings.Clear()
    try { Invoke-ProbeDeliveryBlocked } catch { $fails.Add("用例4 探针抛异常（心跳须 fail-safe）：$($_.Exception.Message)") }
    # Evidence diagnostics are additional major findings; this pre-existing assertion owns only A's normal block verdict.
    $db = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -notmatch '\[TRIAGE-EVIDENCE-' })
    if ($db.Count -ne 1) { $fails.Add("用例4 期望恰 1 条 delivery-blocked（仅 A），实得 $($db.Count)") }
    elseif ($db[0].what -notmatch 'T8-SC-A') { $fails.Add('用例4 报出的不是 block 那张卡（A）') }
    elseif ($db[0].severity -ne 'blocking') { $fails.Add("用例4 severity 应为 blocking（交付停摆须排在自我维护之上），实得 $($db[0].severity)") }
    elseif ($db[0].what -notmatch '2 条理由') { $fails.Add('用例4 未报出裁决的理由条数') }
    $parseEvidenceFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -match '\[TRIAGE-EVIDENCE-PARSE\]' -and $_.what -match 'T8-SC-B' })
    if ($parseEvidenceFinding.Count -ne 1 -or $parseEvidenceFinding[0].severity -ne 'major') {
      $fails.Add('用例4b：in-review 卡的坏 review JSON 必须以 major [TRIAGE-EVIDENCE-PARSE] finding 可见，不能静默丢弃。')
    }
    function Get-ScaffoldRepositoryHead { param([string]$Path) $null = $Path; return $null }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $headEvidenceFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -match '\[TRIAGE-EVIDENCE-HEAD\]' -and $_.what -match 'T8-SC-A' })
    if ($headEvidenceFinding.Count -ne 1 -or $headEvidenceFinding[0].severity -ne 'major') {
      $fails.Add('用例4c：current block 的 evidence root HEAD 无法读取时必须以 major [TRIAGE-EVIDENCE-HEAD] finding 可见，不能静默跳过。')
    }
    function Get-ScaffoldRepositoryHead { param([string]$Path) $null = $Path; return $fixtureHead }

    # 用例4d：两个**真实** evidence root 各有不同 HEAD；两种 source-priority 结果同时锁住，
    # 防止以一个 stub SHA/首个 root 覆盖另一个 root。这里刻意恢复真 reader，不能再由夹具函数代答。
    Set-Item function:Get-ScaffoldRepositoryHead -Value $realGetScaffoldRepositoryHead
    $fxDualWt = Join-Path $fxRoot 'dual-wt'; $fxDualRepo = Join-Path $fxRoot 'dual-repo'
    $fxDualWtCard = Join-Path $fxDualWt 'T8-SC-A'
    New-Item -ItemType Directory -Force $fxDualWtCard, $fxDualRepo | Out-Null
    foreach ($root in @($fxDualWtCard, $fxDualRepo)) {
      & git -c 'init.templateDir=' -C $root init -q
      & git -C $root -c user.name=fixture -c user.email=fixture@example.invalid -c commit.gpgSign=false -c "core.hooksPath=$fixtureHooks" commit --allow-empty -m ("fixture-" + [IO.Path]::GetFileName($root)) -q
      if ($LASTEXITCODE -ne 0) { throw "用例4d 无法创建真实 Git evidence root：$root" }
    }
    $fxDualWtReview = Join-Path $fxDualWtCard '.review'; $fxDualLocalReview = Join-Path $fxDualRepo '.review'
    New-Item -ItemType Directory -Force $fxDualWtReview, $fxDualLocalReview | Out-Null
    $dualWtHead = (& git -C $fxDualWtCard rev-parse HEAD | Out-String).Trim()
    $dualLocalHead = (& git -C $fxDualRepo rev-parse HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $dualWtHead -ceq $dualLocalHead -or
        (Get-ScaffoldRepositoryHead -Path $fxDualWtCard) -cne $dualWtHead -or
        (Get-ScaffoldRepositoryHead -Path $fxDualRepo) -cne $dualLocalHead) {
      $fails.Add('用例4d 两个真实 evidence root 的 HEAD 未被 Get-ScaffoldRepositoryHead 分别精确读出。')
    }
    $dualWtVerdict = Join-Path $fxDualWtReview 'T8-SC-A.json'; $dualLocalVerdict = Join-Path $fxDualLocalReview 'T8-SC-A.json'
    $RepoRoot = $fxDualRepo; function Get-ScaffoldWorktreeRoot { $fxDualWt }
    Set-Content $dualWtVerdict ('{"verdict":"block","reasons":["wt"],"sha":"' + $dualWtHead + '"}') -Encoding utf8
    Set-Content $dualLocalVerdict ('{"verdict":"pass","reasons":[],"sha":"' + $dualLocalHead + '"}') -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $dualWtWins = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($dualWtWins.Count -ne 1 -or $dualWtWins[0].next -notmatch [regex]::Escape($dualWtVerdict)) { $fails.Add('用例4d 双 root 不同 SHA 下 current worktree block 未按自己的 HEAD 生效。') }
    Set-Content $dualWtVerdict ('{"verdict":"pass","reasons":[],"sha":"' + $dualWtHead + '"}') -Encoding utf8
    Set-Content $dualLocalVerdict ('{"verdict":"block","reasons":["local"],"sha":"' + $dualLocalHead + '"}') -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    if (@($findings | Where-Object probe -eq 'delivery-blocked').Count -ne 0) { $fails.Add('用例4d 双 root 不同 SHA 下 current worktree pass 被 local block 越过来源优先级。') }

    # 用例4e：证据目录枚举失败不能伪装成「没有裁决」。调用仍是真 probe；夹具只让该目录枚举产生
    # 非终止错误，以钉住 production 对 ErrorVariable/try-catch 的可观测处理。
    $fxEnumWt = Join-Path $fxRoot 'enum-wt'; $fxEnumCard = Join-Path $fxEnumWt 'T8-SC-A'; $fxEnumReview = Join-Path $fxEnumCard '.review'
    New-Item -ItemType Directory -Force $fxEnumReview | Out-Null
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxEnumWt }
    function Get-ChildItem {
      [CmdletBinding()]
      param(
        [Parameter(Position = 0)][string]$Path,
        [string]$LiteralPath,
        [string]$Filter,
        [switch]$File,
        [switch]$Directory,
        [switch]$Force
      )
      $target = if ($PSBoundParameters.ContainsKey('LiteralPath')) { $LiteralPath } else { $Path }
      if ($target -ceq $fxEnumReview) {
        Write-Error 'fixture delivery evidence enumeration failed'
        return
      }
      $forward = @{}
      if ($PSBoundParameters.ContainsKey('LiteralPath')) { $forward.LiteralPath = $LiteralPath } else { $forward.Path = $Path }
      if ($PSBoundParameters.ContainsKey('Filter')) { $forward.Filter = $Filter }
      if ($File) { $forward.File = $true }
      if ($Directory) { $forward.Directory = $true }
      if ($Force) { $forward.Force = $true }
      & Microsoft.PowerShell.Management\Get-ChildItem @forward
    }
    try {
      $findings.Clear(); Invoke-ProbeDeliveryBlocked
    } finally {
      Remove-Item function:Get-ChildItem -ErrorAction SilentlyContinue
    }
    $enumEvidenceFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -match '\[TRIAGE-EVIDENCE-ENUM\]' -and $_.what -match 'T8-SC-A' })
    if ($enumEvidenceFinding.Count -ne 1 -or $enumEvidenceFinding[0].severity -ne 'major') {
      $fails.Add('用例4e：evidence 枚举失败必须以 major [TRIAGE-EVIDENCE-ENUM] finding 可见，不能静默伪装成无裁决。')
    }

    # 用例4f：.json 路径实际是目录，Get-Content 必失败；解析错与读取错要分开给操作者可行动信号。
    $fxReadWt = Join-Path $fxRoot 'read-wt'; $fxReadCard = Join-Path $fxReadWt 'T8-SC-A'; $fxReadReview = Join-Path $fxReadCard '.review'
    New-Item -ItemType Directory -Force (Join-Path $fxReadReview 'T8-SC-A.json') | Out-Null
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxReadWt }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $readEvidenceFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -match '\[TRIAGE-EVIDENCE-READ\]' -and $_.what -match 'T8-SC-A' })
    if ($readEvidenceFinding.Count -ne 1 -or $readEvidenceFinding[0].severity -ne 'major') {
      $fails.Add('用例4f：不可读取的 evidence JSON 必须以 major [TRIAGE-EVIDENCE-READ] finding 可见，不能和坏 JSON 一起静默吞掉。')
    }

    # 用例4g：JSON 可读、root HEAD 可读，但身份字段 sha 缺失时仍不具备可用证据身份，必须显式上报。
    $fxIdentityWt = Join-Path $fxRoot 'identity-wt'; $fxIdentityCard = Join-Path $fxIdentityWt 'T8-SC-A'; $fxIdentityReview = Join-Path $fxIdentityCard '.review'
    New-Item -ItemType Directory -Force $fxIdentityCard | Out-Null
    & git -c 'init.templateDir=' -C $fxIdentityCard init -q
    & git -C $fxIdentityCard -c user.name=fixture -c user.email=fixture@example.invalid -c commit.gpgSign=false -c "core.hooksPath=$fixtureHooks" commit --allow-empty -m fixture-identity -q
    if ($LASTEXITCODE -ne 0 -or -not (Get-ScaffoldRepositoryHead -Path $fxIdentityCard)) { throw '用例4g 无法创建可读 HEAD 的 identity fixture root' }
    New-Item -ItemType Directory -Force $fxIdentityReview | Out-Null
    Set-Content -Path (Join-Path $fxIdentityReview 'T8-SC-A.json') -Value '{"verdict":"block","reasons":["missing-sha"]}' -Encoding utf8
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxIdentityWt }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $identityEvidenceFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -match '\[TRIAGE-EVIDENCE-IDENTITY\]' -and $_.what -match 'T8-SC-A' })
    if ($identityEvidenceFinding.Count -ne 1 -or $identityEvidenceFinding[0].severity -ne 'major') {
      $fails.Add('用例4g：sha 缺失的未知/不可用 evidence identity 必须以 major [TRIAGE-EVIDENCE-IDENTITY] finding 可见，不能静默跳过。')
    }

    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxWt }
    function Get-ScaffoldRepositoryHead { param([string]$Path) $null = $Path; return $fixtureHead }

    # ── 用例 5：lessons-cap 按驻留 id 计数（上游 issue #184），封顶**两侧边界**各一枚 ──
    # 判据的要害在于：同一份夹具下**旧的按条目计数会绿、新的按 id 计数必红**——否则这条修复无从证伪。
    # 只测「超封顶」会让 minor 那一侧无人看守；阈值一律由 $MustCap 算出、不写 3/4 这类字面量，
    # 否则改常量时本用例照绿。
    $MustCap = 3
    foreach ($case in @(
        @{ n = $MustCap;     sev = 'minor'; word = '达封顶' },      # 恰好等于上限
        @{ n = $MustCap + 1; sev = 'major'; word = '超封顶' })) {   # 超出一个
      # 前 n-1 个 id 并进**一条** bullet（最后一个放续行）、末一个单独一条 ⇒ 条目数恒为 2，
      # 驻留 id 数 = n。续行钉住完整 list-item 解析，不能只扫物理首行。
      $merged = (1..($case.n - 2) | ForEach-Object { "[L90$_]" }) -join ''
      $wrappedId = "[L90$($case.n - 1)]"
      $fxClaude = Join-Path $fxRoot "CLAUDE-$($case.n).md"
      $continuation = if ($case.n -eq $MustCap) {
        @("lazy continuation $wrappedId（同一 markdown 条目的懒续行）")
      } else {
        @('', "  indented paragraph $wrappedId（空行后的缩进段落仍属同一条目）")
      }
      Set-Content -Path $fxClaude -Encoding utf8 -Value @(
        @('## 经验铁律（必须加载）', "- **$merged** 多个 id 并进一条 bullet") +
        $continuation +
        @("- **[L9$($case.n)9]** 单 id 一条", '', '## 下一节'))
      $ClaudeMd = $fxClaude       # 注入：探针读脚本作用域
      $bulletCount = ([regex]::Matches((Get-Content $fxClaude -Raw), '(?m)^\s*-\s+\*\*')).Count
      if ($bulletCount -gt $MustCap) { $fails.Add("用例5（$($case.n)/$MustCap）夹具无效：旧口径（条目数 $bulletCount）本身已超上限，证明不了新口径") }
      $findings.Clear()
      Invoke-ProbeCap
      $cap = @($findings | Where-Object probe -eq 'lessons-cap')
      if ($cap.Count -ne 1) { $fails.Add("用例5（$($case.n)/$MustCap）期望恰 1 条 lessons-cap，实得 $($cap.Count)") }
      elseif ($cap[0].what -notmatch "$($case.n)/$MustCap") { $fails.Add("用例5 未按驻留 id 计数（期望 $($case.n)/$MustCap，实得：$($cap[0].what)）") }
      elseif ($cap[0].severity -ne $case.sev) { $fails.Add("用例5（$($case.n)/$MustCap）severity 应为 $($case.sev)，实得 $($cap[0].severity)") }
      elseif ($cap[0].what -notmatch $case.word) { $fails.Add("用例5（$($case.n)/$MustCap）文案未点明「$($case.word)」：$($cap[0].what)") }
    }
    # ── 用例 5b：小节标题漂移必须 fail-closed ──
    # 找不到小节时若静默返回 0 条，就与「小节在、零驻留」不可分辨：封顶判定恒绿、探针一声不吭，
    # 而此刻真实驻留数其实远超上限。机检面认 ASCII 哨兵（L165），本地化文案只给人读。
    $fxDrift = Join-Path $fxRoot 'CLAUDE-drift.md'
    Set-Content -Path $fxDrift -Encoding utf8 -Value @(
      '## 必载经验（标题已漂移）',
      "- **[L901][L902][L903][L904][L905]** 驻留 5 个 id，远超上限 $MustCap",
      '',
      '## 下一节')
    $ClaudeMd = $fxDrift
    $findings.Clear(); Invoke-ProbeCap
    $drift = @($findings | Where-Object probe -eq 'lessons-cap')
    if ($drift.Count -ne 1) { $fails.Add("用例5b 标题漂移时期望恰 1 条 lessons-cap（fail-closed），实得 $($drift.Count)——静默返回 0 条即封顶恒绿") }
    elseif ($drift[0].what -notmatch [regex]::Escape($ScaffoldMustLayerNotFound)) { $fails.Add("用例5b 未打出 ASCII 哨兵 $ScaffoldMustLayerNotFound（实得：$($drift[0].what)）") }

    # ── 用例 5c：重复驻留 id 必须在两个消费者都 fail-closed ──
    # Break caught: Sort-Object -Unique inside one bullet or across bullets lets arbitrarily many repeated
    # residents collapse to one id before the cap is measured. Both shapes exercise the real shared parser,
    # then the triage consumer and a subprocess running the production lessons.ps1 check consumer.
    $duplicateSentinel = '[LESSONS-DUPLICATE-RESIDENT-ID]'
    $fxDuplicateRepo = Join-Path $fxRoot 'duplicate-consumer'
    New-Item -ItemType Directory -Force $fxDuplicateRepo, (Join-Path $fxDuplicateRepo 'docs/lessons') | Out-Null
    Copy-Item -LiteralPath $PSScriptRoot -Destination $fxDuplicateRepo -Recurse -Force
    Copy-Item -LiteralPath (Join-Path (Split-Path $PSScriptRoot) 'docs/lessons/LEDGER.md') -Destination (Join-Path $fxDuplicateRepo 'docs/lessons/LEDGER.md') -Force
    foreach ($duplicateCase in @(
        @{ id = 'within-bullet'; lines = @('## 经验铁律（必须加载）', '- **[L1]** first [L1] repeated in one resident bullet', '', '## 下一节') },
        @{ id = 'across-bullets'; lines = @('## 经验铁律（必须加载）', '- **[L1]** first resident bullet', '- **[L1]** second resident bullet', '', '## 下一节') })) {
      $fxDuplicateClaude = Join-Path $fxRoot "CLAUDE-duplicate-$($duplicateCase.id).md"
      Set-Content -LiteralPath $fxDuplicateClaude -Value $duplicateCase.lines -Encoding utf8
      $ClaudeMd = $fxDuplicateClaude
      $findings.Clear(); Invoke-ProbeCap
      $duplicateFinding = @($findings | Where-Object probe -eq 'lessons-cap')
      if ($duplicateFinding.Count -ne 1 -or $duplicateFinding[0].severity -ne 'major' -or $duplicateFinding[0].what -notmatch [regex]::Escape($duplicateSentinel)) {
        $fails.Add("用例5c/$($duplicateCase.id) triage 未以 major + $duplicateSentinel 拒绝重复驻留 id（实得 $($duplicateFinding.Count)：$(($duplicateFinding | ForEach-Object what) -join ' | ')）")
      }
      Set-Content -LiteralPath (Join-Path $fxDuplicateRepo 'CLAUDE.md') -Value $duplicateCase.lines -Encoding utf8
      $duplicateCheckOutput = (& pwsh -NoProfile -File (Join-Path $fxDuplicateRepo 'scripts/lessons.ps1') check 2>&1 | Out-String)
      $duplicateCheckExit = $LASTEXITCODE
      if ($duplicateCheckExit -eq 0 -or $duplicateCheckOutput -notmatch [regex]::Escape($duplicateSentinel)) {
        $fails.Add("用例5c/$($duplicateCase.id) lessons.ps1 check 未非零并给 $duplicateSentinel（exit=$duplicateCheckExit）")
      }
    }

    # ── 用例 5d：受限 CommonMark 边界——三种无序列表标记、空行后的块边界、围栏代码 ──
    # 这三枚夹具直接驱动共享解析器。只数 `-`、把空行后的仓外段落继续并进前一项、或把 fenced code
    # 里的原始 `##` 当小节边界，都会分别让一枚变红。
    $fxMarkers = Join-Path $fxRoot 'CLAUDE-markers.md'
    Set-Content -LiteralPath $fxMarkers -Encoding utf8 -Value @(
      '## 经验铁律（必须加载）',
      '* **[L930]** star item',
      '+ **[L931]** plus item',
      '- **[L932]** dash item',
      '', '## 下一节')
    $markerSection = Get-ScaffoldMustLayerSection -Path $fxMarkers
    if (-not $markerSection.Found -or ($markerSection.Ids -join ',') -ne 'L930,L931,L932' -or $markerSection.Bullets.Count -ne 3) {
      $fails.Add("用例5d/markers 未把 */+/- 解析为三条独立 resident item（Found=$($markerSection.Found)，Ids=$($markerSection.Ids -join ',')，Bullets=$($markerSection.Bullets.Count)）")
    }

    $fxBoundary = Join-Path $fxRoot 'CLAUDE-boundary.md'
    Set-Content -LiteralPath $fxBoundary -Encoding utf8 -Value @(
      '## 经验铁律（必须加载）',
      '- **[L940]** first item',
      'lazy continuation [L941]',
      '',
      'outside paragraph [L942]',
      '> outside blockquote [L943]',
      '- **[L944]** second item',
      '',
      '  indented continuation [L945]',
      '', '## 下一节')
    $boundarySection = Get-ScaffoldMustLayerSection -Path $fxBoundary
    if (-not $boundarySection.Found -or ($boundarySection.Ids -join ',') -ne 'L940,L941,L944,L945') {
      $fails.Add("用例5d/boundary 把空行后的外部段落/blockquote 算进 resident item，或丢了合法续行（Found=$($boundarySection.Found)，Ids=$($boundarySection.Ids -join ',')）")
    }

    $fxFence = Join-Path $fxRoot 'CLAUDE-fence.md'
    Set-Content -LiteralPath $fxFence -Encoding utf8 -Value @(
      '```markdown',
      '## 经验铁律（代码示例，不是小节）',
      '- **[L950]** fenced decoy',
      '```',
      '## 经验铁律（必须加载）',
      '- **[L951]** real item before fence',
      '```text',
      '## 下一节（围栏内，不得结束真实小节）',
      '- **[L952]** fenced decoy inside section',
      '```',
      '+ **[L953]** real item after fence',
      '', '## 下一节')
    $fenceSection = Get-ScaffoldMustLayerSection -Path $fxFence
    if (-not $fenceSection.Found -or ($fenceSection.Ids -join ',') -ne 'L951,L953' -or $fenceSection.Bullets.Count -ne 2) {
      $fails.Add("用例5d/fence 把 fenced code 的 heading/id 当成结构，或在围栏内标题处截断（Found=$($fenceSection.Found)，Ids=$($fenceSection.Ids -join ',')，Bullets=$($fenceSection.Bullets.Count)）")
    }

    # ── 用例 6：enforced_by 四向（上游 issue #183）——有守卫 / 显式 none / 空字段 / 认不出的占位符 ──
    # L904 的 enforced_by 是**空行**、其后紧跟 refs 行：旧式 '\s*(.+)' 会跨行捕到 refs 值、把它误判为已有守卫，
    # 于是最需要被提名的那条反而被静默滤掉（fail-open）。这里正是钉住该方向的用例。
    # L905/L906 钉的是另一种 fail-open：`TODO`/`N/A` 这类既非空、又非 none（理由）的占位符若被读成
    # 「已有守卫」，一条**无**守卫的铁律会被降层探针写成「机器已在守它：TODO」，而最该被提名加闸的
    # 总账条目则从心跳里消失。判定核对认不出的取值一律 fail-closed（判无守卫）。
    $fxLedger = Join-Path $fxRoot 'LEDGER.md'
    Set-Content -Path $fxLedger -Encoding utf8 -Value @(
      '## L901 有守卫的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: scripts/selftest.ps1 闸 99z',
      '',
      '## L902 显式无守卫的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: none（本条只能靠人）',
      '',
      '## L903 有守卫的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: scripts/selftest.ps1 闸 99y',
      '',
      '## L904 空 enforced_by 的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by:',
      '- refs: scripts/selftest.ps1 闸 99x',
      '',
      '## L905 占位符 enforced_by 的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: TODO',
      '',
      '## L906 占位符 enforced_by 的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: N/A',
      '',
      '## L912 复合占位符 enforced_by 的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: TODO: add scripts/future.ps1',
      '',
      '## L913 伪文件后缀占位符的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: N/A (.json)',
      '',
      '## L914 TBD 路径复合占位符的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: TBD scripts/future.ps1',
      '',
      '## L915 FIXME 路径复合占位符的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: FIXME scripts/future.ps1',
      '',
      '## L916 前导计划词 + 文件后缀的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: planned future.ps1',
      '',
      '## L917 前导人工说明 + 仓库路径的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: manual only; docs/manual',
      '',
      '## L918 前导英文否定 + gate 的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: there is no gate 1',
      '',
      '## L919 前导中文否定 + 闸号的必须层',
      '- tier: must',
      '- severity: blocking',
      '- enforced_by: 没有闸1',
      '',
      '## L920 前导计划词 + 文件后缀的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: planned future.ps1',
      '',
      '## L921 前导人工说明 + 仓库路径的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: manual only; docs/manual',
      '',
      '## L922 前导英文否定 + gate 的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: there is no gate 1',
      '',
      '## L923 前导中文否定 + 闸号的总账层',
      '- tier: ledger',
      '- severity: blocking',
      '- enforced_by: 没有闸1',
      '')
    $Ledger = $fxLedger          # 注入：两个探针都读脚本作用域
    $findings.Clear(); Invoke-ProbeLessonsDemote
    $dem = @($findings | Where-Object probe -eq 'lessons-demote')
    $demWhat = ($dem | ForEach-Object what) -join ' '
    if ($dem.Count -ne 1) { $fails.Add("用例6 期望恰 1 条 lessons-demote（仅有真守卫的 L901），实得 $($dem.Count)") }
    if ($demWhat -notmatch 'L901') { $fails.Add('用例6 有守卫的必须层条目 L901 未被提名降层（enforced_by 的降层方向失效）') }
    if ($demWhat -match 'L902') { $fails.Add('用例6 显式 none（理由）的 L902 被提名降层——none 必须判为**无**守卫') }
    if ($demWhat -match 'L905') { $fails.Add('用例6 占位符 enforced_by: TODO 的 L905 被提名降层——心跳在替一条无守卫的铁律说「机器已在守它」（fail-open）') }
    if ($demWhat -match 'L912') { $fails.Add('用例6 复合占位符 TODO: add scripts/future.ps1 的 L912 被提名降层——占位前缀不能被后续真路径洗白') }
    if ($demWhat -match 'L914') { $fails.Add('用例6 复合占位符 TBD scripts/future.ps1 的 L914 被提名降层——未知占位前缀不能被后续真路径洗白') }
    foreach ($id in 916..919) {
      if ($demWhat -match "L$id") { $fails.Add("用例6 前导否定/计划说明的伪守卫 L$id 被提名降层——字段中稍后出现的文件/路径/闸号不得洗白前导文本") }
    }
    $findings.Clear(); Invoke-ProbeLessons
    $pro = @($findings | Where-Object probe -eq 'lessons-promote')
    $proWhat = ($pro | ForEach-Object what) -join ' '
    if ($pro.Count -ne 1) { $fails.Add("用例6 期望 8 个候选超过批量窗口后合成恰 1 条 lessons-promote，实得 $($pro.Count)") }
    if ($proWhat -match 'L903') { $fails.Add('用例6 已有守卫的 L903 仍被提名晋升（enforced_by 闸未生效）') }
    if ($proWhat -notmatch 'L904') { $fails.Add('用例6 空 enforced_by 的 L904 未被提名——空字段被误读成「已有守卫」（跨行捕获 fail-open）') }
    if ($proWhat -notmatch 'L906') { $fails.Add('用例6 占位符 enforced_by: N/A 的 L906 未被提名——认不出的取值被误读成「已有守卫」（fail-open）') }
    if ($proWhat -notmatch 'L913') { $fails.Add('用例6 复合占位符 N/A (.json) 的 L913 未被提名——占位前缀不能被括号内文件后缀洗白') }
    if ($proWhat -notmatch 'L915') { $fails.Add('用例6 复合占位符 FIXME scripts/future.ps1 的 L915 未被提名——未知占位前缀不能被后续真路径洗白') }
    foreach ($id in 920..923) {
      if ($proWhat -notmatch "L$id") { $fails.Add("用例6 前导否定/计划说明的伪守卫 L$id 未被提名晋升——字段中稍后出现的文件/路径/闸号不得洗白前导文本") }
    }

    foreach ($invalidEnforcedBy in @(
      'TODO: add scripts/future.ps1', 'TBD scripts/future.ps1', 'FIXME scripts/future.ps1',
      '待补 scripts/future.ps1', 'N/A (.json)', 'no gate 1', '无闸1',
      'TODO（scripts/future.ps1）', 'N/A（scripts/future.ps1）', '待补（scripts/future.ps1）',
      'TODO，scripts/future.ps1', 'no gate（scripts/future.ps1）',
      'none', 'none TODO', 'none: scripts/future.ps1', 'none：scripts/future.ps1',
      'none, scripts/future.ps1',
      'planned future.ps1', 'manual only; docs/manual', 'there is no gate 1', '没有闸1')) {
      if (Test-ScaffoldLessonGuarded $invalidEnforcedBy) { $fails.Add("用例6c 伪守卫被判 guarded：$invalidEnforcedBy") }
      if (Test-ScaffoldLessonEnforcedByWellFormed $invalidEnforcedBy) { $fails.Add("用例6c 非规范声明被判 well-formed：$invalidEnforcedBy") }
    }
    foreach ($validNoGuard in @('none（理由）', 'none(reason)')) {
      if (-not (Test-ScaffoldLessonEnforcedByWellFormed $validNoGuard)) { $fails.Add("用例6c 带非空理由的 none 声明被拒：$validNoGuard") }
      if (Test-ScaffoldLessonGuarded $validNoGuard) { $fails.Add("用例6c none 声明被误判 guarded：$validNoGuard") }
    }

    # ── 用例 6b：**中文**取值的守卫判定（用例 6 的反方向；总账本就是中文散文）──
    # 用例 6 只覆盖了 ASCII 占位符，于是收紧成允许清单后仍有一个反向的 fail-open：判定核用 .NET 正则，
    # 而 .NET 的 \w 是 Unicode 感知的——`[\w.-]{2,}[\\/][\w.-]{2,}` 把任何**含斜杠的中文短语**读成
    # 「仓库路径」（人工/评审、手动/人工核验、见 PR #183 的讨论/结论）；`闸\s*\S` 又把「闸」后面的
    # **任意**字符当闸编号，于是「无闸门（只能靠人）」——字面意思就是没有闸门——被判成**已有守卫**，
    # 降层探针遂打出「机器已在守它：无闸门（只能靠人）」：一边引用「没有闸门」四个字、一边据此主张
    # 删掉一条本就无守卫的铁律。这正是 _lessons.ps1 自己的注释点名要防的那种灾难。
    # L165：每种形态各一枚夹具，且各配一枚**单句**变异（放宽对应字符类即可让本用例变红），故失败文案逐条分开写。
    # 反方向的 L911 同样重要：收紧不能连带拒掉本仓真在用的圈码闸编号（闸⑯ / gate ⑧）。
    $fx6b = Join-Path $fxRoot 'ledger-cjk.md'
    Set-Content -Path $fx6b -Encoding utf8 -Value @(
      '## L907 中文「无闸门」的必须层', '- tier: must', '- severity: blocking', '- enforced_by: 无闸门（只能靠人）', '',
      '## L908 中文斜杠短语的总账层', '- tier: ledger', '- severity: blocking', '- enforced_by: 人工/评审', '',
      '## L909 闸后跟标点的总账层', '- tier: ledger', '- severity: blocking', '- enforced_by: 闸，靠人', '',
      '## L910 gate 后跟中文的总账层', '- tier: ledger', '- severity: blocking', '- enforced_by: gate 讨论', '',
      '## L911 圈码闸编号的总账层', '- tier: ledger', '- severity: blocking', '- enforced_by: selftest 闸⑯', '')
    $Ledger = $fx6b
    $findings.Clear(); Invoke-ProbeLessonsDemote
    $demWhat6b = ((@($findings | Where-Object probe -eq 'lessons-demote')) | ForEach-Object what) -join ' '
    if ($demWhat6b -match 'L907') { $fails.Add('用例6b 中文「无闸门（只能靠人）」的 L907 被提名降层——心跳把「没有闸门」四个字当成了「机器已在守它」的证据（闸 分支认闸后任意字符，fail-open）') }
    $findings.Clear(); Invoke-ProbeLessons
    $pro6b = @($findings | Where-Object probe -eq 'lessons-promote')
    $proWhat6b = ($pro6b | ForEach-Object what) -join ' '
    if ($pro6b.Count -ne 3) { $fails.Add("用例6b 期望恰 3 条 lessons-promote（L908/L909/L910 三种中文伪守卫），实得 $($pro6b.Count)") }
    if ($proWhat6b -notmatch 'L908') { $fails.Add('用例6b 中文斜杠短语「人工/评审」的 L908 未被提名晋升——.NET 的 \w 认 CJK，含斜杠的中文短语被读成仓库路径（fail-open）') }
    if ($proWhat6b -notmatch 'L909') { $fails.Add('用例6b 「闸，靠人」的 L909 未被提名晋升——闸 后面跟的是标点不是闸编号，却被读成闸引用（fail-open）') }
    if ($proWhat6b -notmatch 'L910') { $fails.Add('用例6b 「gate 讨论」的 L910 未被提名晋升——gate 后面跟的是中文不是闸编号，却被读成闸引用（fail-open）') }
    if ($proWhat6b -match 'L911') { $fails.Add('用例6b 圈码闸编号「selftest 闸⑯」的 L911 被提名晋升——收紧连带拒掉了本仓真在用的闸引用形态（fail-closed 过头，会把真守卫报成没守卫）') }

    # ── 用例 7：批量窗口的边界（$PromoteBatchSize 恰好 vs 超一条）──
    # 阈值判据用的是 -gt，故「恰好等于」必须仍逐条报、「多一条」才切成一条批量 finding。
    # 只测其中一侧会让 off-by-one 静默存活（-ge 与 -gt 在 N 条时才分道）。
    foreach ($n in @($PromoteBatchSize, $PromoteBatchSize + 1)) {
      $fxN = Join-Path $fxRoot "ledger-$n.md"
      Set-Content -Path $fxN -Encoding utf8 -Value @(1..$n | ForEach-Object {
        "## L90$_ 无守卫且达门槛", '- tier: ledger', '- severity: blocking', '- enforced_by: none（夹具）', '' })
      $Ledger = $fxN
      $findings.Clear(); Invoke-ProbeLessons
      $hits = @($findings | Where-Object probe -eq 'lessons-promote')
      if ($n -le $PromoteBatchSize) {
        if ($hits.Count -ne $n) { $fails.Add("用例7 恰好 $n 条（== 阈值）应逐条报，实得 $($hits.Count) 条") }
      } else {
        if ($hits.Count -ne 1) { $fails.Add("用例7 超阈值（$n 条）应合成 1 条批量 finding，实得 $($hits.Count) 条") }
        elseif ($hits[0].what -notmatch "$n 条") { $fails.Add('用例7 批量 finding 未报出候选条数（读者无从判断规模）') }
      }
    }

    # ── 用例 8：主检出侧的 .review/<id>.json 也要被看见（-Local ship 的裁决落在那里）──
    # 探针有两条取证路径：卡自己的 worktree，以及主检出按**卡 id** 命名的那份。只测前者会让后者静默失效。
    $fxLocalReview = Join-Path $fxRoot 'localrepo/.review'
    New-Item -ItemType Directory -Force $fxLocalReview | Out-Null
    Set-Content -Path (Join-Path $fxLocalReview 'T8-SC-A.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"only-local`"],`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $RepoRoot = Join-Path $fxRoot 'localrepo'      # 注入：探针读脚本作用域 $RepoRoot
    function Get-ScaffoldWorktreeRoot { Join-Path $fxRoot 'no-such-wt' }   # worktree 侧刻意缺席，只剩本地那条路径
    $findings.Clear()
    try { Invoke-ProbeDeliveryBlocked } catch { $fails.Add("用例8 探针抛异常：$($_.Exception.Message)") }
    $lb = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($lb.Count -ne 1) { $fails.Add("用例8 期望恰 1 条来自主检出 .review 的 delivery-blocked，实得 $($lb.Count)") }
    elseif ($lb[0].what -notmatch 'T8-SC-A') { $fails.Add('用例8 报出的不是本地 .review 里那张卡') }
    # 用例 8b：本地路径先经 Test-Path，随后 Get-Item 仍可能因竞态/ACL 失败；两者不能把未知证据伪装成无发现。
    $fxLocalGetItem = Join-Path $fxRoot 'local-getitem'
    $fxLocalGetItemReview = Join-Path $fxLocalGetItem '.review'
    $forcedLocalGetItem = Join-Path $fxLocalGetItemReview 'T8-SC-A.json'
    New-Item -ItemType Directory -Force $fxLocalGetItemReview | Out-Null
    Set-Content -Path $forcedLocalGetItem -Value "{`"verdict`":`"block`",`"reasons`":[`"local-getitem`"],`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $RepoRoot = $fxLocalGetItem
    function Get-ScaffoldWorktreeRoot { Join-Path $fxRoot 'no-such-wt' }
    function Get-Item {
      [CmdletBinding()]
      param([string]$Path)
      if ($Path -ceq $forcedLocalGetItem) { throw 'fixture local Get-Item read failure' }
      Microsoft.PowerShell.Management\Get-Item -LiteralPath $Path
    }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $localGetItemFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-READ\]' })
    if ($localGetItemFinding.Count -ne 1 -or $localGetItemFinding[0].severity -cne 'major') { $fails.Add('用例8b：local Get-Item 读取失败必须以 major [TRIAGE-EVIDENCE-READ] finding 可见，不能只覆盖 Get-Content 失败。') }
    Remove-Item function:Get-Item
    # 用例 8c：两条 discovery Test-Path 都可能在 EAP=Stop 下因 ACL/provider/race 抛异常；未知裁决必须留具名诊断，不能把探针打断。
    $fxWorktreeDiscovery = Join-Path $fxRoot 'worktree-discovery'
    $forcedWorktreeReview = Join-Path $fxWorktreeDiscovery 'T8-SC-A/.review'
    New-Item -ItemType Directory -Force $forcedWorktreeReview | Out-Null
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxWorktreeDiscovery }
    function Test-Path {
      param([string]$Path)
      if ($Path -ceq $forcedWorktreeReview) { throw 'fixture worktree Test-Path discovery failure' }
      Microsoft.PowerShell.Management\Test-Path -LiteralPath $Path
    }
    $findings.Clear(); $worktreeDiscoveryThrew = $false
    try { Invoke-ProbeDeliveryBlocked } catch { $worktreeDiscoveryThrew = $true }
    $worktreeDiscoveryFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-ENUM\].*worktree evidence' })
    if ($worktreeDiscoveryThrew -or $worktreeDiscoveryFinding.Count -ne 1 -or $worktreeDiscoveryFinding[0].severity -cne 'major') { $fails.Add('用例8c-worktree：worktree discovery Test-Path 失败必须以 major [TRIAGE-EVIDENCE-ENUM] finding 可见，不得中断 reporter。') }
    Remove-Item function:Test-Path
    $fxLocalDiscovery = Join-Path $fxRoot 'local-discovery'
    $forcedLocalDiscovery = Join-Path $fxLocalDiscovery '.review/T8-SC-A.json'
    New-Item -ItemType Directory -Force (Split-Path -Parent $forcedLocalDiscovery) | Out-Null
    Set-Content -Path $forcedLocalDiscovery -Value ('{"verdict":"block","reasons":["local-discovery"],"sha":"' + $fixtureHead + '"}') -Encoding utf8
    $RepoRoot = $fxLocalDiscovery; function Get-ScaffoldWorktreeRoot { Join-Path $fxRoot 'no-such-wt' }
    function Test-Path {
      param([string]$Path)
      if ($Path -ceq $forcedLocalDiscovery) { throw 'fixture local Test-Path discovery failure' }
      Microsoft.PowerShell.Management\Test-Path -LiteralPath $Path
    }
    $findings.Clear(); $localDiscoveryThrew = $false
    try { Invoke-ProbeDeliveryBlocked } catch { $localDiscoveryThrew = $true }
    $localDiscoveryFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-READ\].*local evidence' })
    if ($localDiscoveryThrew -or $localDiscoveryFinding.Count -ne 1 -or $localDiscoveryFinding[0].severity -cne 'major') { $fails.Add('用例8c-local：local discovery Test-Path 失败必须以 major [TRIAGE-EVIDENCE-READ] finding 可见，不得中断 reporter。') }
    Remove-Item function:Test-Path
    # ── 用例 9：两条取证路径**真重合**时，同一份裁决只报一条 ──
    # 重合条件是 <RepoRoot> == <wtRoot>/<id>：此时通配取到的 .review/<id>.json 与按 id 拼出的
    # <RepoRoot>/.review/<id>.json 是**同一个文件**。用例 4 与 8 各自只喂一条路径，都盖不住这里。
    $fxOv = Join-Path $fxRoot 'overlap'
    $fxOvCard = Join-Path $fxOv 'T8-SC-A'
    New-Item -ItemType Directory -Force (Join-Path $fxOvCard '.review') | Out-Null
    Set-Content -Path (Join-Path $fxOvCard '.review/T8-SC-A.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"dup`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $RepoRoot = $fxOvCard                                 # 主检出恰是卡自己的 worktree
    function Get-ScaffoldWorktreeRoot { $fxOv }           # 于是两条路径解析到同一个文件
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $ov = @($findings | Where-Object probe -eq 'delivery-blocked')
    # 只断言条数会把「两条都被归属挡掉」的 0 条与真去重混为一谈，故连报的是谁、指向哪个文件一并钉住。
    if ($ov.Count -ne 1) { $fails.Add("用例9 重合路径下期望恰 1 条 delivery-blocked，实得 $($ov.Count)（未按全路径去重，一份裁决被数两次）") }
    elseif ($ov[0].what -notmatch 'T8-SC-A') { $fails.Add("用例9 报的不是重合路径上那张卡（A）：$($ov[0].what)") }
    elseif ($ov[0].next -notmatch [regex]::Escape([IO.Path]::Combine('overlap', 'T8-SC-A', '.review', 'T8-SC-A.json'))) { $fails.Add("用例9 finding 指向的不是重合路径上那唯一一份裁决文件：$($ov[0].next)") }
    # ── 用例 9b：目录级路径大小写语义；只信真实 CreateNew/读回行为，不从 OS 名称或 fsutil 文案猜测 ──
    function New-ScaffoldActualCaseEvidenceFixture {
      param([Parameter(Mandatory)][string]$CardRoot, [Parameter(Mandatory)][string]$Sha)
      $reviewRoot = Join-Path $CardRoot '.review'
      New-Item -ItemType Directory -Force $reviewRoot | Out-Null
      $upperPath = Join-Path $reviewRoot 'T8-SC-A.json'; $lowerPath = Join-Path $reviewRoot 't8-sc-a.json'
      $upperJson = "{`"verdict`":`"block`",`"reasons`":[`"upper`"],`"branch`":`"T8-SC-A`",`"sha`":`"$Sha`"}"
      $lowerJson = "{`"verdict`":`"block`",`"reasons`":[`"lower`"],`"branch`":`"T8-SC-A`",`"sha`":`"$Sha`"}"
      $utf8 = [System.Text.UTF8Encoding]::new($false)
      $upperStream = [IO.File]::Open($upperPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
      try { foreach ($byte in $utf8.GetBytes($upperJson)) { $upperStream.WriteByte($byte) } } finally { $upperStream.Dispose() }
      $mode = ''
      try {
        $lowerStream = [IO.File]::Open($lowerPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { foreach ($byte in $utf8.GetBytes($lowerJson)) { $lowerStream.WriteByte($byte) } } finally { $lowerStream.Dispose() }
        $mode = 'sensitive'
      } catch [IO.IOException] {
        [IO.File]::WriteAllText($lowerPath, $lowerJson, $utf8)
        $mode = 'insensitive'
      }
      $files = @(Get-ChildItem -LiteralPath $reviewRoot -File)
      $upperContent = [IO.File]::ReadAllText($upperPath, $utf8); $lowerContent = [IO.File]::ReadAllText($lowerPath, $utf8)
      $valid = if ($mode -ceq 'sensitive') {
        $files.Count -eq 2 -and $upperContent -cmatch 'upper' -and $lowerContent -cmatch 'lower'
      } else {
        $files.Count -eq 1 -and $upperContent -cmatch 'lower' -and $lowerContent -cmatch 'lower'
      }
      [pscustomobject]@{ CardRoot = $CardRoot; ReviewRoot = $reviewRoot; Mode = $mode; Valid = $valid; FileCount = $files.Count }
    }

    $caseCapabilityNotes = [System.Collections.Generic.List[string]]::new()
    $caseModeProof = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $secondModeCapabilityIssue = ''
    function Get-ScaffoldCaseModeProfileFailures {
      param([string]$Profile, [string[]]$ModeProof, [string]$Issue)
      if ($Profile -ceq 'require-dual-actual') { foreach ($mode in 'sensitive','insensitive') {
        if ($ModeProof -cnotcontains $mode) { "用例9b-required-mode：profile=require-dual-actual，实际 $mode 缺失（$(if($Issue){$Issue}else{'未获得第二实际模式证明'})）；DoD 不得 SKIP。" }
      } }
    }
    $profileRequiredMissing = @(Get-ScaffoldCaseModeProfileFailures require-dual-actual @('insensitive') 'fsutil 不可用')
    if (@(Get-ScaffoldCaseModeProfileFailures portable @('insensitive') 'fsutil 不可用').Count -or $profileRequiredMissing.Count -ne 1 -or $profileRequiredMissing[0] -cnotmatch 'profile=require-dual-actual，实际 sensitive' -or @(Get-ScaffoldCaseModeProfileFailures require-dual-actual @('sensitive', 'insensitive') '').Count) {
      $fails.Add('用例9b-profile：portable SKIP、require-dual-actual 缺失拒绝、双模式通过。')
    }
    $fxDefaultCase = Join-Path $fxRoot 'case-default'; $fxDefaultCaseCard = Join-Path $fxDefaultCase 'T8-SC-A'
    New-Item -ItemType Directory -Force $fxDefaultCaseCard | Out-Null
    $defaultCase = New-ScaffoldActualCaseEvidenceFixture -CardRoot $fxDefaultCaseCard -Sha $fixtureHead
    if (-not $defaultCase.Valid) { $fails.Add("用例9b 默认实际根夹具失效：mode=$($defaultCase.Mode)，files=$($defaultCase.FileCount)。") }
    $RepoRoot = if ($defaultCase.Mode -ceq 'insensitive') { $fxDefaultCaseCard.ToUpperInvariant() } else { $fxDefaultCaseCard }
    function Get-ScaffoldWorktreeRoot { $fxDefaultCase }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $defaultCaseFindings = @($findings | Where-Object probe -eq 'delivery-blocked')
    $expectedDefaultCaseFindings = if ($defaultCase.Mode -ceq 'sensitive') { 2 } else { 1 }
    if ($defaultCaseFindings.Count -ne $expectedDefaultCaseFindings -or ($defaultCaseFindings | Where-Object { $_.what -notmatch 'T8-SC-A' })) {
      $fails.Add("用例9b 默认实际根 mode=$($defaultCase.Mode) 期望 $expectedDefaultCaseFindings 条 delivery-blocked，实得 $($defaultCaseFindings.Count)。")
    } else {
      [void]$caseModeProof.Add($defaultCase.Mode)
      Write-Host "  INFO 用例9b required actual mode verified: $($defaultCase.Mode)" -ForegroundColor DarkGray
    }

    # 用例9b-portable：普通单文件 evidence 不能依赖 Windows 专属目录 flag；模拟该 probe 不可用时仍须用真实目录的只读 lookup 保住 block。
    $fxPortableCase = Join-Path $fxRoot 'case-portable'; $fxPortableCard = Join-Path $fxPortableCase 'T8-SC-A'
    $fxPortableReview = Join-Path $fxPortableCard '.review'
    New-Item -ItemType Directory -Force $fxPortableReview | Out-Null
    $portableVerdict = Join-Path $fxPortableReview 'T8-SC-A.json'
    Set-Content -Path $portableVerdict -Value ('{"verdict":"block","reasons":["portable-single"],"branch":"T8-SC-A","sha":"' + $fixtureHead + '"}') -Encoding utf8
    function Get-ScaffoldWindowsDirectoryCaseSensitive { throw 'portable comparer must not ask an OS-specific helper' }
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxPortableCase }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $portableCaseFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-CASE\]' })
    $portableBlockFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.severity -ceq 'blocking' -and $_.next -cmatch [regex]::Escape($portableVerdict) })
    if ($portableCaseFinding.Count -ne 0 -or $portableBlockFinding.Count -ne 1) { $fails.Add('用例9b-portable：普通单文件 evidence 在无 Windows flag probe 时必须仍按实际目录语义报 exact blocking verdict，且不得报 [TRIAGE-EVIDENCE-CASE]。') }
    Remove-Item function:Get-ScaffoldWindowsDirectoryCaseSensitive

    # 此表只锁 Select-ScaffoldCurrentVerdicts 的纯选择语义；上面的实际根调用才是目录行为证据。
    $logicalCaseCandidates = @(
      [pscustomobject]@{ path = (Join-Path $fxDefaultCase 'T8-SC-A.json'); name = 'T8-SC-A.json'; verdict = 'block'; sourceRank = 0 },
      [pscustomobject]@{ path = (Join-Path $fxDefaultCase 't8-sc-a.json'); name = 't8-sc-a.json'; verdict = 'block'; sourceRank = 0 }
    )
    $sensitiveCurrent = @(Select-ScaffoldCurrentVerdicts -Candidates $logicalCaseCandidates -PathComparer ([System.StringComparer]::Ordinal))
    $insensitiveCurrent = @(Select-ScaffoldCurrentVerdicts -Candidates $logicalCaseCandidates -PathComparer ([System.StringComparer]::OrdinalIgnoreCase))
    if ($sensitiveCurrent.Count -ne 2 -or $insensitiveCurrent.Count -ne 1) {
      $fails.Add("用例9b selector 两侧分组未同时成立（sensitive=$($sensitiveCurrent.Count), insensitive=$($insensitiveCurrent.Count)）。")
    }
    # 用例9b-selector：同一来源、同一逻辑 evidence 的 pass/block 冲突不能依赖输入顺序或路径字典序；block 必须胜出。
    $sameSourcePass = [pscustomobject]@{ path = (Join-Path $fxDefaultCase 'T8-SC-A.json'); name = 'T8-SC-A.json'; verdict = 'pass'; sourceRank = 0 }
    $sameSourceBlock = [pscustomobject]@{ path = (Join-Path $fxDefaultCase 't8-sc-a.json'); name = 't8-sc-a.json'; verdict = 'block'; sourceRank = 0 }
    foreach ($ordered in @(@($sameSourcePass, $sameSourceBlock), @($sameSourceBlock, $sameSourcePass))) {
      $selected = @(Select-ScaffoldCurrentVerdicts -Candidates $ordered -PathComparer ([System.StringComparer]::OrdinalIgnoreCase))
      if ($selected.Count -ne 1 -or $selected[0].verdict -cne 'block') {
        $fails.Add("用例9b-selector：同 source pass/block 冲突未稳定选择 block（input=$($ordered.verdict -join ','), actual=$($selected.verdict -join ',')）。")
      }
    }

    $fsutil = Get-Command fsutil -ErrorAction SilentlyContinue
    if (-not $fsutil) {
      $secondModeCapabilityIssue = 'fsutil 不可用'
      Write-Host '  SKIP 用例9b 第二目录模式：fsutil 不可用；已验证当前临时目录的实际模式。' -ForegroundColor DarkGray
    } else {
      $fxAlternateCase = Join-Path $fxRoot 'case-alternate'; $fxAlternateProbe = Join-Path $fxAlternateCase 'original-mode-probe'
      New-Item -ItemType Directory -Force $fxAlternateProbe | Out-Null
      $alternateOriginal = New-ScaffoldActualCaseEvidenceFixture -CardRoot $fxAlternateProbe -Sha $fixtureHead
      if (-not $alternateOriginal.Valid) { throw "用例9b 第二模式原始夹具无效：mode=$($alternateOriginal.Mode)，files=$($alternateOriginal.FileCount)。" }
      $resolvedFxRoot = (Resolve-Path -LiteralPath $fxRoot -ErrorAction Stop).Path
      $resolvedAlternateProbe = (Resolve-Path -LiteralPath $fxAlternateProbe -ErrorAction Stop).Path
      $expectedAlternateProbe = [IO.Path]::GetFullPath((Join-Path (Join-Path $fxRoot 'case-alternate') 'original-mode-probe'))
      $fxRootPrefix = $resolvedFxRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
      if ($resolvedAlternateProbe -cne $expectedAlternateProbe -or -not $resolvedAlternateProbe.StartsWith($fxRootPrefix, [System.StringComparison]::Ordinal)) {
        throw "用例9b 拒绝删除非本次 fxRoot 专属 probe：$resolvedAlternateProbe"
      }
      Remove-Item -LiteralPath $fxAlternateProbe -Recurse -Force
      $fxAlternateCard = Join-Path $fxAlternateCase 'T8-SC-A'
      New-Item -ItemType Directory -Force $fxAlternateCard | Out-Null
      $toggleAction = if ($alternateOriginal.Mode -ceq 'sensitive') { 'disable' } else { 'enable' }
      $restoreAction = if ($alternateOriginal.Mode -ceq 'sensitive') { 'enable' } else { 'disable' }
      $queryBefore = (& $fsutil.Source file queryCaseSensitiveInfo $fxAlternateCard 2>&1 | Out-String).Trim()
      $queryExit = $LASTEXITCODE
      $caseCapabilityNotes.Add("query-before exit=$queryExit output=$queryBefore")
      $toggleApplied = $false
      try {
        $toggleOutput = (& $fsutil.Source file setCaseSensitiveInfo $fxAlternateCard $toggleAction 2>&1 | Out-String).Trim()
        $toggleExit = $LASTEXITCODE
        $caseCapabilityNotes.Add("$toggleAction exit=$toggleExit output=$toggleOutput")
        if ($toggleExit -ne 0) {
          $secondModeCapabilityIssue = "fsutil $toggleAction 未成功（exit=$toggleExit）"
          Write-Host "  SKIP 用例9b 第二目录模式：fsutil $toggleAction 未成功；已验证当前临时目录的实际模式。" -ForegroundColor DarkGray
        } else {
          $toggleApplied = $true
          $alternateCase = New-ScaffoldActualCaseEvidenceFixture -CardRoot $fxAlternateCard -Sha $fixtureHead
          if (-not $alternateCase.Valid) {
            $secondModeCapabilityIssue = "第二模式夹具无效（mode=$($alternateCase.Mode)，files=$($alternateCase.FileCount)）"
            Write-Host "  SKIP 用例9b 第二目录模式：$secondModeCapabilityIssue；已验证当前临时目录的实际模式。" -ForegroundColor DarkGray
          } elseif ($alternateCase.Mode -ceq $alternateOriginal.Mode) {
            $secondModeCapabilityIssue = 'fsutil 成功但实际 CreateNew 行为未切换'
            Write-Host "  SKIP 用例9b 第二目录模式：fsutil 成功但实际 CreateNew 行为未切换；已验证当前临时目录的实际模式。" -ForegroundColor DarkGray
          } else {
            $RepoRoot = if ($alternateCase.Mode -ceq 'insensitive') { $fxAlternateCard.ToUpperInvariant() } else { $fxAlternateCard }
            function Get-ScaffoldWorktreeRoot { $fxAlternateCase }
            $findings.Clear(); Invoke-ProbeDeliveryBlocked
            $alternateFindings = @($findings | Where-Object probe -eq 'delivery-blocked')
            $expectedAlternateFindings = if ($alternateCase.Mode -ceq 'sensitive') { 2 } else { 1 }
            if ($alternateFindings.Count -ne $expectedAlternateFindings -or ($alternateFindings | Where-Object { $_.what -notmatch 'T8-SC-A' })) {
              $fails.Add("用例9b 第二实际根 mode=$($alternateCase.Mode) 期望 $expectedAlternateFindings 条 delivery-blocked，实得 $($alternateFindings.Count)。")
            } else {
              [void]$caseModeProof.Add($alternateCase.Mode)
              Write-Host "  INFO 用例9b required actual mode verified: $($alternateCase.Mode)" -ForegroundColor DarkGray
            }
          }
        }
      } finally {
        if ($toggleApplied) {
          $restoreOutput = (& $fsutil.Source file setCaseSensitiveInfo $fxAlternateCard $restoreAction 2>&1 | Out-String).Trim()
          $restoreExit = $LASTEXITCODE
          $caseCapabilityNotes.Add("restore-$restoreAction exit=$restoreExit output=$restoreOutput")
          if ($restoreExit -ne 0) { $fails.Add("用例9b 无法恢复 owned temp child 的原实际属性（exit=$restoreExit）。") }
        }
      }
    }
    foreach ($caseCapabilityNote in $caseCapabilityNotes) { Write-Host "  INFO 用例9b fsutil $caseCapabilityNote" -ForegroundColor DarkGray }

    # 用例 9d：只有实际 comparer 不可得才报告 caseMode unknown；它不得静默清空一个仍在场的裁决。
    $fxUnknownCase = Join-Path $fxRoot 'case-unknown'
    New-Item -ItemType Directory -Force (Join-Path $fxUnknownCase 'T8-SC-A/.review') | Out-Null
    Set-Content -Path (Join-Path $fxUnknownCase 'T8-SC-A/.review/T8-SC-A.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"case-unknown`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $savedEvidencePathComparer = ${function:Get-ScaffoldEvidencePathComparer}
    function Get-ScaffoldEvidencePathComparer { param([string]$Directory, [object[]]$Files) $null = $Directory; $null = $Files; return $null }
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $fxUnknownCase }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $unknownCaseFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-CASE\]' })
    if ($unknownCaseFinding.Count -ne 1 -or $unknownCaseFinding[0].severity -cne 'major') { $fails.Add('用例9d：actual comparer 不可得时必须以 major [TRIAGE-EVIDENCE-CASE] finding 可见，不能静默遗漏。') }

    # 用例9d-local：local source 的 comparer 也必须独立可证伪；不得只因 worktree fixture 已覆盖而让 local CASE finding 变成死代码。
    $fxLocalUnknownCase = Join-Path $fxRoot 'case-unknown-local'
    $fxLocalUnknownReview = Join-Path $fxLocalUnknownCase '.review'
    New-Item -ItemType Directory -Force $fxLocalUnknownReview | Out-Null
    Set-Content -Path (Join-Path $fxLocalUnknownReview 'T8-SC-A.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"case-unknown-local`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $RepoRoot = $fxLocalUnknownCase; function Get-ScaffoldWorktreeRoot { Join-Path $fxRoot 'no-such-worktree' }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $localUnknownCaseFinding = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-CASE\].*local evidence' })
    if ($localUnknownCaseFinding.Count -ne 1 -or $localUnknownCaseFinding[0].severity -cne 'major') { $fails.Add('用例9d-local：local evidence 的 comparer 不可得时必须恰报一条 major [TRIAGE-EVIDENCE-CASE] finding，不能静默遗漏。') }
    Set-Item function:Get-ScaffoldEvidencePathComparer -Value $savedEvidencePathComparer

    # 用例 9e：父目录可敏感、两个 Git root 仅大小写不同，而各自 .review 不敏感时，去重只能折叠 leaf，不能折叠完整路径。
    if (-not $fsutil) {
      Write-Host '  SKIP 用例9e：fsutil 不可用；当前平台无法构造敏感父目录与不敏感 .review 的实际双 Git root。' -ForegroundColor DarkGray
    } else {
      $fxAncestor = Join-Path $fxRoot 'case-ancestor'
      $fxSensitiveParent = Join-Path $fxAncestor 'roots'
      New-Item -ItemType Directory -Force $fxSensitiveParent | Out-Null
      $enableAncestor = (& $fsutil.Source file setCaseSensitiveInfo $fxSensitiveParent enable 2>&1 | Out-String).Trim()
      if ($LASTEXITCODE -ne 0) {
        Write-Host "  SKIP 用例9e：fsutil 无法启用 owned temp 父目录 caseMode：$enableAncestor" -ForegroundColor DarkGray
      } else {
        $fixtureGetScaffoldRepositoryHead = ${function:Get-ScaffoldRepositoryHead}
        $fxCaseWt = Join-Path $fxSensitiveParent 'T8-SC-A'; $fxCaseLocal = Join-Path $fxSensitiveParent 't8-sc-a'
        New-Item -ItemType Directory -Force $fxCaseWt, $fxCaseLocal | Out-Null
        $rootPair = @(Get-ChildItem -LiteralPath $fxSensitiveParent -Directory | Where-Object { $_.Name -cin @('T8-SC-A', 't8-sc-a') })
        if ($rootPair.Count -ne 2) {
          $fails.Add("用例9e 实际敏感父目录未保留两个仅大小写不同的 Git root（实得 $($rootPair.Count)）。")
        } else {
          foreach ($root in @($fxCaseWt, $fxCaseLocal)) {
            & git -c 'init.templateDir=' -C $root init -q
            & git -C $root -c user.name=fixture -c user.email=fixture@example.invalid -c commit.gpgSign=false -c "core.hooksPath=$fixtureHooks" commit --allow-empty -m ([IO.Path]::GetFileName($root)) -q
            New-Item -ItemType Directory -Force (Join-Path $root '.review') | Out-Null
            $disableReview = (& $fsutil.Source file setCaseSensitiveInfo (Join-Path $root '.review') disable 2>&1 | Out-String).Trim()
            if ($LASTEXITCODE -ne 0) { throw "用例9e 无法将 owned temp .review 设为 insensitive：$disableReview" }
          }
          $wtReviewCase = New-ScaffoldActualCaseEvidenceFixture -CardRoot $fxCaseWt -Sha '0000000000000000000000000000000000000000'
          $localHeadCase = (& git -C $fxCaseLocal rev-parse HEAD | Out-String).Trim()
          $localReviewCase = New-ScaffoldActualCaseEvidenceFixture -CardRoot $fxCaseLocal -Sha $localHeadCase
          if ($wtReviewCase.Mode -cne 'insensitive' -or $localReviewCase.Mode -cne 'insensitive') {
            $fails.Add("用例9e 实际 .review 目录未同时为 insensitive（worktree=$($wtReviewCase.Mode)，local=$($localReviewCase.Mode)）。")
          } else {
            $caseWtVerdict = Join-Path $wtReviewCase.ReviewRoot 'T8-SC-A.json'
            $caseLocalVerdict = Join-Path $localReviewCase.ReviewRoot 'T8-SC-A.json'
            Set-Content -Path $caseWtVerdict -Value '{"verdict":"pass","reasons":[],"branch":"T8-SC-A","sha":"0000000000000000000000000000000000000000"}' -Encoding utf8
            Set-Content -Path $caseLocalVerdict -Value "{`"verdict`":`"block`",`"reasons`":[`"current-local`"],`"branch`":`"T8-SC-A`",`"sha`":`"$localHeadCase`"}" -Encoding utf8
            Set-Item function:Get-ScaffoldRepositoryHead -Value $realGetScaffoldRepositoryHead
            $RepoRoot = $fxCaseLocal; function Get-ScaffoldWorktreeRoot { $fxSensitiveParent }
            $findings.Clear(); Invoke-ProbeDeliveryBlocked
            $ancestorCaseFindings = @($findings | Where-Object probe -eq 'delivery-blocked')
            if ($ancestorCaseFindings.Count -ne 1 -or $ancestorCaseFindings[0].next -notmatch [regex]::Escape($caseLocalVerdict)) { $fails.Add('用例9e 敏感父目录下的 stale worktree pass 折叠了 current local block；路径去重错误地折叠了完整路径。') }
            Set-Item function:Get-ScaffoldRepositoryHead -Value $fixtureGetScaffoldRepositoryHead
          }
        }
      }
    }

    # 用例 9c：裁决必须匹配所属检出的当前 HEAD；两份都当前时固定优先 worktree，不读取可伪造的 mtime。
    $fxCurrentWt = Join-Path $fxRoot 'current-wt'; $fxCurrentRepo = Join-Path $fxRoot 'current-repo'
    $wtCurrentReview = Join-Path $fxCurrentWt 'T8-SC-A/.review'; $localCurrentReview = Join-Path $fxCurrentRepo '.review'
    New-Item -ItemType Directory -Force $wtCurrentReview, $localCurrentReview | Out-Null
    $worktreeVerdict = Join-Path $wtCurrentReview 'T8-SC-A.json'; $localVerdict = Join-Path $localCurrentReview 'T8-SC-A.json'
    $staleSha = '0000000000000000000000000000000000000000'
    Set-Content $worktreeVerdict "{`"verdict`":`"block`",`"reasons`":[`"stale`"],`"branch`":`"T8-SC-A`",`"sha`":`"$staleSha`"}" -Encoding utf8
    Set-Content $localVerdict "{`"verdict`":`"pass`",`"reasons`":[],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $RepoRoot = $fxCurrentRepo; function Get-ScaffoldWorktreeRoot { $fxCurrentWt }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    if (@($findings | Where-Object probe -eq 'delivery-blocked').Count -ne 0) { $fails.Add('用例9c SHA 过期的 worktree block 遮住当前 local pass') }
    Set-Content $worktreeVerdict "{`"verdict`":`"pass`",`"reasons`":[],`"branch`":`"T8-SC-A`",`"sha`":`"$staleSha`"}" -Encoding utf8
    Set-Content $localVerdict "{`"verdict`":`"block`",`"reasons`":[`"current`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $currentBlocked = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($currentBlocked.Count -ne 1 -or $currentBlocked[0].next -notmatch [regex]::Escape($localVerdict)) { $fails.Add('用例9c 当前 local block 未胜过 SHA 过期的 worktree pass') }
    Set-Content $worktreeVerdict "{`"verdict`":`"block`",`"reasons`":[`"worktree-current`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    Set-Content $localVerdict "{`"verdict`":`"pass`",`"reasons`":[],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    [IO.File]::SetLastWriteTimeUtc($worktreeVerdict, [datetime]::UtcNow.AddHours(-2)); [IO.File]::SetLastWriteTimeUtc($localVerdict, [datetime]::UtcNow.AddHours(-1))
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $worktreeCurrent = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($worktreeCurrent.Count -ne 1 -or $worktreeCurrent[0].next -notmatch [regex]::Escape($worktreeVerdict)) { $fails.Add('用例9c 两份 SHA 都当前时未按固定来源优先级选择 worktree block（疑似仍依赖 mtime）') }
    Set-Content $worktreeVerdict "{`"verdict`":`"pass`",`"reasons`":[],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    Set-Content $localVerdict "{`"verdict`":`"block`",`"reasons`":[`"local-loses`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    if (@($findings | Where-Object probe -eq 'delivery-blocked').Count -ne 0) { $fails.Add('用例9c 两份 SHA 都当前时 local block 越过固定来源优先级遮住 worktree pass') }

    # ── 用例 10：归属校验的**两道**各测一条 ──
    # (a) 文件名就不是本卡的（隔壁分支按自己分支名落盘）——由文件名兜底挡下；
    # (b) 文件名恰好是 <id>.json、但产物自述 branch 属于别人（分支改名/复制夹具后会出现）——只有读 branch 才挡得下。
    # 少测 (b)，branch 归属那半就是死代码：删掉它测试照绿。
    $fxFor = Join-Path $fxRoot 'foreign'
    New-Item -ItemType Directory -Force (Join-Path $fxFor 'T8-SC-A/.review') | Out-Null
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/codex-other-branch.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"not-ours`"],`"branch`":`"T9-SOMEONE-ELSE`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'
    function Get-ScaffoldWorktreeRoot { $fxFor }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $fo = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($fo.Count -ne 0) { $fails.Add("用例10(a) 文件名非本卡的裁决被算到本卡头上（实得 $($fo.Count) 条）") }
    # (b)：同一目录再放一份**文件名对得上、branch 对不上**的，仍不得上报
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/T8-SC-A.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"renamed-branch`"],`"branch`":`"T9-SOMEONE-ELSE`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $fo2 = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($fo2.Count -ne 0) { $fails.Add("用例10(b) 文件名对得上但 branch 自述属于别人的裁决仍被上报（实得 $($fo2.Count) 条）——branch 归属校验是死代码") }
    # (c)：**无 branch 字段**的旧产物（该字段是后加的）——此时唯一能判归属的就是文件名那道兜底。
    # 少了这条，文件名兜底同样是死代码：(a)/(b) 里 branch 都在场，第一道就把它们挡了，兜底永远走不到。
    $fxLegacy = Join-Path $fxRoot 'legacy'
    New-Item -ItemType Directory -Force (Join-Path $fxLegacy 'T8-SC-A/.review') | Out-Null
    Set-Content -Path (Join-Path $fxLegacy 'T8-SC-A/.review/codex-legacy-no-branch.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"legacy-artifact`"],`"sha`":`"$fixtureHead`"}" -Encoding utf8
    function Get-ScaffoldWorktreeRoot { $fxLegacy }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $fo3 = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($fo3.Count -ne 0) { $fails.Add("用例10(c) 无 branch 字段、文件名也非本卡的旧产物被上报（实得 $($fo3.Count) 条）——文件名兜底是死代码") }
    # (d/e) branch 与 verdict 是 JSON schema 字段，大小写必须逐字精确；PowerShell 的 -eq/-in 默认不敏感。
    function Get-ScaffoldWorktreeRoot { $fxFor }
    Get-ChildItem -LiteralPath (Join-Path $fxFor 'T8-SC-A/.review') -Filter '*.json' | Remove-Item -Force
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/T8-SC-A.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"case-owner`"],`"branch`":`"t8-sc-a`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    if (@($findings | Where-Object probe -eq 'delivery-blocked').Count -ne 0) { $fails.Add('用例10(d) branch 只有大小写不同仍被当成本卡所有——schema 归属必须 Ordinal') }
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/T8-SC-A.json') -Value "{`"verdict`":`"BLOCK`",`"reasons`":[`"case-verdict`"],`"branch`":`"T8-SC-A`",`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $unknownVerdict = @($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-VERDICT\]' })
    if ($unknownVerdict.Count -ne 1 -or $unknownVerdict[0].severity -cne 'major') { $fails.Add('用例10(e) 精确匹配本卡 branch/SHA 的大写 BLOCK 必须以 major [TRIAGE-EVIDENCE-VERDICT] finding 可见；未知 enum 不能静默跳过。') }
    # (f) 仅旧产物的文件名归属跟随实际 evidence 目录测得的 comparer，而非运行 OS 名称。
    Get-ChildItem -LiteralPath (Join-Path $fxFor 'T8-SC-A/.review') -Filter '*.json' | Remove-Item -Force
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/t8-sc-a.json') -Value "{`"verdict`":`"block`",`"reasons`":[`"legacy-case`"],`"sha`":`"$fixtureHead`"}" -Encoding utf8
    $legacyFiles = @(Get-ChildItem -LiteralPath (Join-Path $fxFor 'T8-SC-A/.review') -Filter '*.json')
    $legacyComparer = Get-ScaffoldEvidencePathComparer -Directory (Join-Path $fxFor 'T8-SC-A/.review') -Files $legacyFiles
    if ($null -eq $legacyComparer) { throw '用例10(f) 实际 evidence 目录 comparer 不可得，不能用 OS 名称猜测。' }
    $legacyIgnoreCase = $legacyComparer.Equals([System.StringComparer]::OrdinalIgnoreCase)
    $RepoRoot = $fxFor
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $legacyCaseCount = @($findings | Where-Object probe -eq 'delivery-blocked').Count
    $expectedLegacyCaseCount = if ($legacyIgnoreCase) { 1 } else { 0 }
    if ($legacyCaseCount -ne $expectedLegacyCaseCount) { $fails.Add("用例10(f) branchless 大小写文件名未跟随实际 evidence comparer（caseInsensitive=$legacyIgnoreCase，期望 $expectedLegacyCaseCount，实得 $legacyCaseCount）") }
    foreach ($profileFailure in @(Get-ScaffoldCaseModeProfileFailures $CaseModeProfile @($caseModeProof) $secondModeCapabilityIssue)) {
      $fails.Add($profileFailure)
    }

    $foreignCases = @(
      @('foreign-missing-sha','{"verdict":"block","reasons":["foreign-missing-sha"],"branch":"T9-SOMEONE-ELSE"}',$false,'用例10(g)：foreign 缺 sha 未先过滤归属。'),
      @('foreign-unreadable-root',('{"verdict":"block","reasons":["foreign-unreadable-root"],"branch":"T9-SOMEONE-ELSE","sha":"' + $fixtureHead + '"}'),$true,'用例10(h)：foreign unreadable root 未先过滤归属。')
    )
    $savedForeignHead = ${function:Get-ScaffoldRepositoryHead}; try { foreach ($foreignCase in $foreignCases) {
      $foreignRoot = Join-Path $fxRoot $foreignCase[0]; $foreignCard = Join-Path $foreignRoot 'T8-SC-A'
      New-Item -ItemType Directory -Force (Join-Path $foreignCard '.review') | Out-Null
      Set-Content -LiteralPath (Join-Path $foreignCard '.review/T8-SC-A.json') -Value $foreignCase[1] -Encoding utf8
      function Get-ScaffoldRepositoryHead { param([string]$Path) if ($foreignCase[2] -and $Path -ceq $foreignCard) { return $null }; return $fixtureHead }
      $RepoRoot = Join-Path $fxRoot 'no-such-repo'; function Get-ScaffoldWorktreeRoot { $foreignRoot }
      $findings.Clear(); Invoke-ProbeDeliveryBlocked
      if (@($findings | Where-Object { $_.probe -eq 'delivery-blocked' -and $_.what -cmatch '^\[TRIAGE-EVIDENCE-(?:HEAD|IDENTITY)\]' }).Count) { $fails.Add($foreignCase[3]) }
    }} finally { Set-Item function:Get-ScaffoldRepositoryHead -Value $savedForeignHead }

    # 用例 3：WorktreeRoot 取值函数缺失（等价 _config 缺失/加载失败）→ 优雅跳过：不抛异常、无任何发现
    Remove-Item function:Get-ScaffoldWorktreeRoot
    $findings.Clear(); Push-Location $fxCwd
    try { Invoke-ProbeHandoff } catch { $fails.Add("用例3 配置缺失时抛异常：$($_.Exception.Message)") } finally { Pop-Location }
    if (@($findings).Count -ne 0) { $fails.Add('用例3 配置缺失仍产出发现（应整段优雅跳过）') }
  } catch {
    $fails.Add("selfcheck 夹具/执行异常：$($_.Exception.Message)")
  } finally {
    Remove-Item $fxRoot -Recurse -Force -ErrorAction SilentlyContinue
  }
  if ($fails.Count) {
    foreach ($f in $fails) { Write-Host "  FAIL $f" -ForegroundColor Red }
    Write-Host 'triage selfcheck: FAIL'
  } else {
    Write-Host 'triage selfcheck: PASS（探针 4 跨 worktree · 探针 11 block 四态+本地 .review+路径重合去重+真实双 Git root/逐 root HEAD+枚举/读取/解析/身份失败可见+当前 SHA/固定来源优先+实际目录路径语义（可用时双模式）去重+旧文件归属+JSON 字段大小写+隔壁分支归属 · 探针 5 按驻留 id 计数的封顶两侧边界+标题漂移/重复 id fail-closed+resident Markdown markers/boundary/fence · 探针 1/10 的 enforced_by 四向、空字段/ASCII 占位符/中文伪守卫（无闸门…、含斜杠短语、闸后非编号）+圈码闸编号仍判有守卫，与批量窗口）' -ForegroundColor Green
  }
  exit 0
}

if ($Verb -eq 'list') {
  if (Test-Path $OutFile) { Get-Content $OutFile -Raw | Write-Host }
  else { Write-Host "（尚无收件箱：先 pwsh -File scripts\triage.ps1 scan）" -ForegroundColor DarkGray }
  exit 0
}

# --- scan ---
Invoke-ProbeLessons
Invoke-ProbeTechDebt
Invoke-ProbeCards
Invoke-ProbeHandoff
Invoke-ProbeCap
Invoke-ProbeRefresh
Invoke-ProbeEffectiveness
Invoke-ProbeOrphanWorktree
Invoke-ProbeLessonsDemote
Invoke-ProbeDeliveryBlocked
Invoke-ProbeScaffoldStale

$order = @{ blocking = 0; major = 1; minor = 2 }
$sorted = $findings | Sort-Object @{ Expression = { $order[$_.severity] } }, probe
$ts = (Get-Date -Format 'yyyy-MM-dd HH:mm')   # 注：脚本运行时取，非 LLM 编造

# 组装 markdown 收件箱
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('# Triage 收件箱（脚手架心跳）')
[void]$sb.AppendLine('')
[void]$sb.AppendLine("> 生成: $ts ｜ 信号 $($findings.Count) 条。**只发现不行动**——act 走既有交付链（task-loop / lessons promote / 开卡 / handoff）。")
[void]$sb.AppendLine('> 标准/动机见 docs/LOOP-ENGINEERING.md。本文件 gitignored、每次 scan 覆盖。')
[void]$sb.AppendLine('')
if ($findings.Count -eq 0) {
  [void]$sb.AppendLine('无待办信号 ✓（各子系统已收口）。')
} else {
  foreach ($f in $sorted) {
    [void]$sb.AppendLine("- **[$($f.severity)] $($f.probe)** — $($f.what)")
    [void]$sb.AppendLine("  - → $($f.next)")
  }
}
$inbox = $sb.ToString()

if (-not $NoWrite) {
  $dir = Split-Path -Parent $OutFile
  if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
  Set-Content -Path $OutFile -Value $inbox -Encoding utf8
}

if (-not $Quiet) {
  $rel = $OutFile.Replace($RepoRoot + '\', '')
  if ($findings.Count -eq 0) {
    Write-Host "triage: 无待办信号 ✓" -ForegroundColor Green
  } else {
    Write-Host "triage: $($findings.Count) 条待办信号" -ForegroundColor Yellow
    foreach ($f in $sorted) { Write-Host ("  [{0,-8}] {1,-15} {2}" -f $f.severity, $f.probe, $f.what) }
  }
  if (-not $NoWrite) { Write-Host "  收件箱 → $rel" -ForegroundColor DarkGray }
} else {
  Write-Host "triage: $($findings.Count) 条信号"
}
exit 0
