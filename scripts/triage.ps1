#requires -Version 7
<#
.SYNOPSIS
  脚手架的「心跳」(heartbeat)：按节律(cadence)对本仓做一次**只读、离线、确定性**的扫描，
  发现「待办信号」汇成 triage 收件箱——把 loop-engineering 的「自动发现 + 分诊」机械化，
  让人/agent 只需读收件箱、决定**做哪件**，而非手动巡检各子系统。

.DESCRIPTION
  动机（addy osmani《Loop Engineering》组件①「the heartbeat」+ Anthropic《Recursive Self-Improvement》
  「把 perspiration 自动化、人保留 direction-setting」）：脚手架原本全是**按需**触发（task start/ship/cleanup），
  缺一个定期**发现**待办的回路。本脚本扫描既有子系统的本地信号（**不打网络/不调 gh**，纯文件解析）：
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
  每信号产出一条 finding（severity + 一行 what + 建议的下一步命令），汇成 markdown 收件箱。
  **只发现、不行动**：绝不写仓内被跟踪文件、绝不 git/gh 写操作；act 走既有交付链
  （task-loop skill / lessons promote / 开卡偿还 / handoff check）。退出码恒 0（reporter，非闸门）。

  收件箱默认写到 _local/triage-inbox.md（gitignored，运行时态）。无 _config 依赖也能跑（优雅降级）。

.PARAMETER Verb     scan（扫描并写收件箱+打印摘要） | list（只打印上次收件箱，不重扫） |
                    selfcheck（探针 1/4/5/10/11 的 hermetic 自检：临时夹具、输出断言，见该段头注）。默认 scan。
.PARAMETER OutFile  收件箱路径（默认 _local/triage-inbox.md）。
.PARAMETER NoWrite  只报不写（selftest 干跑用：核验扫描在默认配置下不抛异常）。
.PARAMETER Quiet    静默：仅退出码与一行计数，不打印 finding 明细。
.EXAMPLE
  pwsh -File scripts\triage.ps1                 # 扫描，写 _local/triage-inbox.md
.EXAMPLE
  pwsh -File scripts\triage.ps1 scan -NoWrite   # 只报不写（selftest 用）
.EXAMPLE
  pwsh -File scripts\triage.ps1 selfcheck       # 探针 4/5/10/11 自检（末行 'triage selfcheck: PASS' 即绿）
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][ValidateSet('scan', 'list', 'selfcheck')][string]$Verb = 'scan',
  [string]$OutFile,
  [switch]$NoWrite,
  [switch]$Quiet
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
    # 标题漂移时静默返回 0 条 = 封顶恒绿。「测不出」必须报出来，不能读成「没超」（fail-closed）。
    Add-Finding 'lessons-cap' 'major' "$($sec.Sentinel) CLAUDE.md 在，但找不到「经验铁律」小节——封顶已无从计量（标题漂移？）。" "对齐小节标题后 pwsh -File scripts\lessons.ps1 check 复核（该命令同样按此 fail-closed）。"
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
# review.ps1 每次跑都把归一化裁决写进 <worktree>/.review/<分支>.json。severity 取 blocking（既有排序表的最高档），
# 于是「交付停摆」排在一切自我维护发现之上，无须新增排序码。
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
    $files = @()
    if ($wtRoot) {
      $wtReview = Join-Path (Join-Path $wtRoot $id) '.review'
      if (Test-Path $wtReview) { $files += @(Get-ChildItem $wtReview -Filter *.json -ErrorAction SilentlyContinue) }
    }
    $localReview = Join-Path (Join-Path $RepoRoot '.review') "$id.json"
    if (Test-Path $localReview) { $files += @(Get-Item $localReview) }
    # 两条取证路径会互相干扰，各有一种坏法：
    #   ① **同一份裁决被数两次**——卡的 worktree 恰是主检出时，通配与按 id 拼出的路径指向同一个文件；
    #   ② **捞到别人的 block**——worktree 侧是 `*.json` 通配，别的分支在同一 .review 里留下的裁决会被当成本卡的。
    # 故先按全路径去重，再要求产物**自证属于本卡**。
    # 去重键 = `$vf.FullName`：FileInfo 的 FullName 本就是完全限定并已折叠 `.` / `..` 段的路径（实测
    # `Get-Item <dir>\a\..\a\.review\X.json` 交出的 FullName 已无 `..`），故再套一层 [IO.Path]::GetFullPath
    # 是恒等变换、摘掉它没有任何用例会红——那样的守卫只会让人误以为这里已经防住了什么。
    # **唯一真会变的是大小写**（GetFullPath 亦不规范化大小写，`Get-Item` 原样保留调用方给的壳），而它是否
    # 该被忽略**按运行 OS 定，不是 Windows 常量**：`.github/workflows/scaffold-selftest.yml` 把含本探针的
    # core 分片也跑在 ubuntu-latest 上，那里 `a.json` 与 `A.json` 是两份不同裁决，一律 OrdinalIgnoreCase
    # 会把其中一份静默吃掉。同 T0-GATE-FIXFORWARD 的病灶与修法（`-contains` 恒不敏感 / `StartsWith(string)`
    # 恒敏感，一行两套语义 ⇒ Linux 上被静默剪掉），沿用该卡定下的「按 OS 取比较器」家族，不另起一套。
    $pathComparer = if ($IsWindows -or $IsMacOS) { [System.StringComparer]::OrdinalIgnoreCase } else { [System.StringComparer]::Ordinal }
    $seenPath = [System.Collections.Generic.HashSet[string]]::new($pathComparer)
    foreach ($vf in $files) {
      if (-not $seenPath.Add($vf.FullName)) { continue }   # ① 同一文件只算一次
      $verdict = ''; $reasons = 0; $owner = ''
      try {
        $o = Get-Content $vf.FullName -Raw | ConvertFrom-Json
        if ($o -and ($o.PSObject.Properties.Name -contains 'verdict')) { $verdict = [string]$o.verdict }
        if ($o -and ($o.PSObject.Properties.Name -contains 'reasons')) { $reasons = @($o.reasons).Count }
        if ($o -and ($o.PSObject.Properties.Name -contains 'branch'))  { $owner   = [string]$o.branch }
      } catch { continue }   # 一份读不出的裁决绝不能把心跳带崩（同探针 8 的 fail-safe 契约）
      # ② 归属：review.ps1 会把被审分支写进 branch，按它判；旧产物无该字段时退回文件名（<id>.json 即本卡命名）。
      # 判不出归属就跳过——reporter 宁可漏报，也不该拿别人的 block 冤枉本卡（假阳性会把注意力引向错的地方）。
      if ($owner) { if ($owner -ne $id) { continue } }
      elseif ([IO.Path]::GetFileNameWithoutExtension($vf.Name) -ne $id) { continue }
      if ($verdict -ne 'block') { continue }
      $hits += [pscustomobject]@{ id = $id; path = $vf.FullName; reasons = $reasons; when = $vf.LastWriteTime }
    }
  }
  foreach ($h in ($hits | Sort-Object when)) {     # 最旧优先：停得最久的卡先被读到
    $age = [int]((Get-Date) - $h.when).TotalHours
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

  # 元仓不把自己报成落后于自己。
  $originUrl = & git -C $RepoRoot remote get-url origin 2>$null
  if ($LASTEXITCODE -eq 0 -and $originUrl -and ($originUrl -match [regex]::Escape($upstream))) { return }

  try { . (Join-Path $PSScriptRoot 'scaffold-sync.ps1') -AsLibrary } catch { return }

  $ledgerPath = Join-Path $RepoRoot 'docs/SCAFFOLD-SYNC.md'
  $ledgerText = if (Test-Path $ledgerPath) { Get-Content $ledgerPath -Raw } else { '' }
  $provenance = 'unknown'
  try { $provenance = Get-ScaffoldVersion } catch { }
  $synced = Get-SyncedVersion $ledgerText $provenance

  $tags = @()
  $raw = & git -C $RepoRoot for-each-ref "--format=%(refname:strip=2)" 'refs/scaffold-tags/' 2>$null
  if ($LASTEXITCODE -eq 0 -and $raw) { $tags = @($raw | Where-Object { $_ }) }
  if ($tags.Count -eq 0) {
    Add-Finding 'scaffold-stale' 'minor' `
      "本地没有任何上游脚手架 tag——本项目从未去 $upstream 看过有哪些修复可以回填。" `
      'pwsh -File scripts\scaffold-sync.ps1 check -Fetch'
    return
  }

  $behind = @(Get-NewerVersion $tags $synced)
  if ($behind.Count -gt 0) {
    $latest = $behind[$behind.Count - 1].Version.ToString()
    $syncedLabel = if (ConvertTo-ScaffoldVersion $synced) { "v$synced" } else { '一个未登记的基线' }
    Add-Finding 'scaffold-stale' 'major' `
      "$($behind.Count) 个上游脚手架版本尚未议过（$syncedLabel -> v$latest）。每一版都是「拿或写清为什么不拿」——跳过是正当决定，不登记不是。" `
      'pwsh -File scripts\scaffold-sync.ps1 check'
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
        @{ id = 'T8-SC-A'; json = '{"verdict":"block","reasons":["r1","r2"]}' },   # in-progress + block → 须报
        @{ id = 'T8-SC-D'; json = '{"verdict":"pass","reasons":[]}' },             # in-progress + pass  → 不报
        @{ id = 'T8-SC-C'; json = '{"verdict":"block","reasons":["r1"]}' },        # todo 卡 + block     → 不报（状态闸）
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
    $db = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($db.Count -ne 1) { $fails.Add("用例4 期望恰 1 条 delivery-blocked（仅 A），实得 $($db.Count)") }
    elseif ($db[0].what -notmatch 'T8-SC-A') { $fails.Add('用例4 报出的不是 block 那张卡（A）') }
    elseif ($db[0].severity -ne 'blocking') { $fails.Add("用例4 severity 应为 blocking（交付停摆须排在自我维护之上），实得 $($db[0].severity)") }
    elseif ($db[0].what -notmatch '2 条理由') { $fails.Add('用例4 未报出裁决的理由条数') }

    # ── 用例 5：lessons-cap 按驻留 id 计数（上游 issue #184），封顶**两侧边界**各一枚 ──
    # 判据的要害在于：同一份夹具下**旧的按条目计数会绿、新的按 id 计数必红**——否则这条修复无从证伪。
    # 只测「超封顶」会让 minor 那一侧无人看守；阈值一律由 $MustCap 算出、不写 3/4 这类字面量，
    # 否则改常量时本用例照绿。
    $MustCap = 3
    foreach ($case in @(
        @{ n = $MustCap;     sev = 'minor'; word = '达封顶' },      # 恰好等于上限
        @{ n = $MustCap + 1; sev = 'major'; word = '超封顶' })) {   # 超出一个
      # 前 n-1 个 id 并进**一条** bullet、末一个单独一条 ⇒ 条目数恒为 2（旧口径两侧皆绿），
      # 驻留 id 数 = n（新口径在超封顶侧必红）。
      $merged = (1..($case.n - 1) | ForEach-Object { "[L90$_]" }) -join ''
      $fxClaude = Join-Path $fxRoot "CLAUDE-$($case.n).md"
      Set-Content -Path $fxClaude -Encoding utf8 -Value @(
        '## 经验铁律（必须加载）',
        "- **$merged** 多个 id 并进一条 bullet",
        "- **[L9$($case.n)9]** 单 id 一条",
        '',
        '## 下一节')
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
      '')
    $Ledger = $fxLedger          # 注入：两个探针都读脚本作用域
    $findings.Clear(); Invoke-ProbeLessonsDemote
    $dem = @($findings | Where-Object probe -eq 'lessons-demote')
    $demWhat = ($dem | ForEach-Object what) -join ' '
    if ($dem.Count -ne 1) { $fails.Add("用例6 期望恰 1 条 lessons-demote（仅有真守卫的 L901），实得 $($dem.Count)") }
    if ($demWhat -notmatch 'L901') { $fails.Add('用例6 有守卫的必须层条目 L901 未被提名降层（enforced_by 的降层方向失效）') }
    if ($demWhat -match 'L902') { $fails.Add('用例6 显式 none（理由）的 L902 被提名降层——none 必须判为**无**守卫') }
    if ($demWhat -match 'L905') { $fails.Add('用例6 占位符 enforced_by: TODO 的 L905 被提名降层——心跳在替一条无守卫的铁律说「机器已在守它」（fail-open）') }
    $findings.Clear(); Invoke-ProbeLessons
    $pro = @($findings | Where-Object probe -eq 'lessons-promote')
    $proWhat = ($pro | ForEach-Object what) -join ' '
    if ($pro.Count -ne 2) { $fails.Add("用例6 期望恰 2 条 lessons-promote（空字段 L904 + 占位符 L906），实得 $($pro.Count)") }
    if ($proWhat -match 'L903') { $fails.Add('用例6 已有守卫的 L903 仍被提名晋升（enforced_by 闸未生效）') }
    if ($proWhat -notmatch 'L904') { $fails.Add('用例6 空 enforced_by 的 L904 未被提名——空字段被误读成「已有守卫」（跨行捕获 fail-open）') }
    if ($proWhat -notmatch 'L906') { $fails.Add('用例6 占位符 enforced_by: N/A 的 L906 未被提名——认不出的取值被误读成「已有守卫」（fail-open）') }

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
    Set-Content -Path (Join-Path $fxLocalReview 'T8-SC-A.json') -Value '{"verdict":"block","reasons":["only-local"]}' -Encoding utf8
    $RepoRoot = Join-Path $fxRoot 'localrepo'      # 注入：探针读脚本作用域 $RepoRoot
    function Get-ScaffoldWorktreeRoot { Join-Path $fxRoot 'no-such-wt' }   # worktree 侧刻意缺席，只剩本地那条路径
    $findings.Clear()
    try { Invoke-ProbeDeliveryBlocked } catch { $fails.Add("用例8 探针抛异常：$($_.Exception.Message)") }
    $lb = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($lb.Count -ne 1) { $fails.Add("用例8 期望恰 1 条来自主检出 .review 的 delivery-blocked，实得 $($lb.Count)") }
    elseif ($lb[0].what -notmatch 'T8-SC-A') { $fails.Add('用例8 报出的不是本地 .review 里那张卡') }
    # ── 用例 9：两条取证路径**真重合**时，同一份裁决只报一条 ──
    # 重合条件是 <RepoRoot> == <wtRoot>/<id>：此时通配取到的 .review/<id>.json 与按 id 拼出的
    # <RepoRoot>/.review/<id>.json 是**同一个文件**。用例 4 与 8 各自只喂一条路径，都盖不住这里。
    $fxOv = Join-Path $fxRoot 'overlap'
    $fxOvCard = Join-Path $fxOv 'T8-SC-A'
    New-Item -ItemType Directory -Force (Join-Path $fxOvCard '.review') | Out-Null
    Set-Content -Path (Join-Path $fxOvCard '.review/T8-SC-A.json') -Value '{"verdict":"block","reasons":["dup"],"branch":"T8-SC-A"}' -Encoding utf8
    $RepoRoot = $fxOvCard                                 # 主检出恰是卡自己的 worktree
    function Get-ScaffoldWorktreeRoot { $fxOv }           # 于是两条路径解析到同一个文件
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $ov = @($findings | Where-Object probe -eq 'delivery-blocked')
    # 只断言条数会把「两条都被归属挡掉」的 0 条与真去重混为一谈，故连报的是谁、指向哪个文件一并钉住。
    if ($ov.Count -ne 1) { $fails.Add("用例9 重合路径下期望恰 1 条 delivery-blocked，实得 $($ov.Count)（未按全路径去重，一份裁决被数两次）") }
    elseif ($ov[0].what -notmatch 'T8-SC-A') { $fails.Add("用例9 报的不是重合路径上那张卡（A）：$($ov[0].what)") }
    elseif ($ov[0].next -notmatch [regex]::Escape([IO.Path]::Combine('overlap', 'T8-SC-A', '.review', 'T8-SC-A.json'))) { $fails.Add("用例9 finding 指向的不是重合路径上那唯一一份裁决文件：$($ov[0].next)") }
    # ── 用例 9b：去重键的**大小写语义按运行 OS 定**（同 T0-GATE-FIXFORWARD 的病灶家族）──
    # 用例 9 的两条路径是同一个字符串拼出来的，两个 FullName 逐字节相同——于是 Ordinal 与 OrdinalIgnoreCase
    # 都能去重，比较器换掉照绿（实测：把 OrdinalIgnoreCase 改成 Ordinal，selfcheck 仍 PASS）。
    # 本用例让两条**字符串真不同**：主检出侧路径整体大写、worktree 侧原样，并在同一 .review 里再放一份
    # 只有大小写不同的文件名。
    #   Windows/macOS（不敏感）：t8-sc-a.json 覆盖同名文件 ⇒ 目录仍只有一份裁决；两条大小写不同的路径
    #                            指向它 ⇒ 必须去重成 **1** 条（比较器若改 Ordinal 就变 2 条，红）。
    #   Linux（敏感）：T8-SC-A.json 与 t8-sc-a.json 是**两份不同裁决**，大写的 RepoRoot 目录根本不存在
    #                  ⇒ 必须各报一条、共 **2** 条（比较器若写死 OrdinalIgnoreCase 就吃掉一份，红）。
    $fxCase = Join-Path $fxRoot 'oscase'
    $fxCaseCard = Join-Path $fxCase 'T8-SC-A'
    New-Item -ItemType Directory -Force (Join-Path $fxCaseCard '.review') | Out-Null
    Set-Content -Path (Join-Path $fxCaseCard '.review/T8-SC-A.json') -Value '{"verdict":"block","reasons":["upper"],"branch":"T8-SC-A"}' -Encoding utf8
    Set-Content -Path (Join-Path $fxCaseCard '.review/t8-sc-a.json') -Value '{"verdict":"block","reasons":["lower"],"branch":"T8-SC-A"}' -Encoding utf8
    $RepoRoot = $fxCaseCard.ToUpperInvariant()            # 主检出路径整体大写：与通配侧字符串不等，指向同一文件（仅在不敏感 FS 上）
    function Get-ScaffoldWorktreeRoot { $fxCase }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $osCase = @($findings | Where-Object probe -eq 'delivery-blocked')
    $caseInsensitiveFs = ($IsWindows -or $IsMacOS)        # 与探针取比较器同一判据：本闸测的正是「两侧各自该有的条数」
    $expectedOsCase = if ($caseInsensitiveFs) { 1 } else { 2 }
    if ($osCase.Count -ne $expectedOsCase) {
      $why = if ($caseInsensitiveFs) { '同一份裁决的两条大小写不同的路径未被去重（比较器退成 Ordinal？）' } else { '两份大小写不同的**不同**裁决被并成一条（比较器写死 OrdinalIgnoreCase？Linux 上会静默吃掉一份）' }
      $fails.Add("用例9b OS=$($PSVersionTable.Platform) 期望 $expectedOsCase 条 delivery-blocked，实得 $($osCase.Count)——$why")
    }
    elseif (($osCase | Where-Object { $_.what -notmatch 'T8-SC-A' })) { $fails.Add('用例9b 报出的裁决不属于本卡（A）') }

    # ── 用例 10：归属校验的**两道**各测一条 ──
    # (a) 文件名就不是本卡的（隔壁分支按自己分支名落盘）——由文件名兜底挡下；
    # (b) 文件名恰好是 <id>.json、但产物自述 branch 属于别人（分支改名/复制夹具后会出现）——只有读 branch 才挡得下。
    # 少测 (b)，branch 归属那半就是死代码：删掉它测试照绿。
    $fxFor = Join-Path $fxRoot 'foreign'
    New-Item -ItemType Directory -Force (Join-Path $fxFor 'T8-SC-A/.review') | Out-Null
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/codex-other-branch.json') -Value '{"verdict":"block","reasons":["not-ours"],"branch":"T9-SOMEONE-ELSE"}' -Encoding utf8
    $RepoRoot = Join-Path $fxRoot 'no-such-repo'
    function Get-ScaffoldWorktreeRoot { $fxFor }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $fo = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($fo.Count -ne 0) { $fails.Add("用例10(a) 文件名非本卡的裁决被算到本卡头上（实得 $($fo.Count) 条）") }
    # (b)：同一目录再放一份**文件名对得上、branch 对不上**的，仍不得上报
    Set-Content -Path (Join-Path $fxFor 'T8-SC-A/.review/T8-SC-A.json') -Value '{"verdict":"block","reasons":["renamed-branch"],"branch":"T9-SOMEONE-ELSE"}' -Encoding utf8
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $fo2 = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($fo2.Count -ne 0) { $fails.Add("用例10(b) 文件名对得上但 branch 自述属于别人的裁决仍被上报（实得 $($fo2.Count) 条）——branch 归属校验是死代码") }
    # (c)：**无 branch 字段**的旧产物（该字段是后加的）——此时唯一能判归属的就是文件名那道兜底。
    # 少了这条，文件名兜底同样是死代码：(a)/(b) 里 branch 都在场，第一道就把它们挡了，兜底永远走不到。
    $fxLegacy = Join-Path $fxRoot 'legacy'
    New-Item -ItemType Directory -Force (Join-Path $fxLegacy 'T8-SC-A/.review') | Out-Null
    Set-Content -Path (Join-Path $fxLegacy 'T8-SC-A/.review/codex-legacy-no-branch.json') -Value '{"verdict":"block","reasons":["legacy-artifact"]}' -Encoding utf8
    function Get-ScaffoldWorktreeRoot { $fxLegacy }
    $findings.Clear(); Invoke-ProbeDeliveryBlocked
    $fo3 = @($findings | Where-Object probe -eq 'delivery-blocked')
    if ($fo3.Count -ne 0) { $fails.Add("用例10(c) 无 branch 字段、文件名也非本卡的旧产物被上报（实得 $($fo3.Count) 条）——文件名兜底是死代码") }
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
    Write-Host 'triage selfcheck: PASS（探针 4 跨 worktree · 探针 11 block 四态+本地 .review+路径重合去重+去重键 OS 语义+隔壁分支归属 · 探针 5 按驻留 id 计数的封顶两侧边界+标题漂移 fail-closed · 探针 1/10 的 enforced_by 四向、空字段/ASCII 占位符/中文伪守卫（无闸门…、含斜杠短语、闸后非编号）+圈码闸编号仍判有守卫，与批量窗口）' -ForegroundColor Green
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
