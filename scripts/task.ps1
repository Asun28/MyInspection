#requires -Version 7
<#
.SYNOPSIS
  单任务闭环编排器（R1 worktree + R2 TDD + R3 Codex-PR闸门 + R4 测试卫生 + R5 文档同步）。

.DESCRIPTION
  把一张任务卡，跑成可机检的 worktree → 红 → 绿 → 重构/剪枝 → Codex 评审 →
  PR + 合并 → 收尾 → 文档同步 闭环。分阶段执行（编码本身由 Claude/人在 worktree 内做）：

    start   : 建 worktree(<WorktreeRoot>\<TaskId>) + 引导环境(uv sync / npm i)，打印 TDD 提醒。
    ship    : DoD(必绿) → verify 总闸 → 提交 → 范围闸(allow_paths) → 许可闸 → 防泄露闸 → 真实 diff 预算
              → push → 开 PR → Codex 评审(必 pass) → 候选树 ci.yml 的 pinned-head jobs 全绿 → base/head 复核
              → 绑定已证明 head 直接 squash 合并。free+private 无服务端规则集/auto-merge，故客户端把本地确定性闸、
              R3 与候选 CI 都作为 mandatory gate；任一不可判/失败均拒绝自动合并与 -NoAutoMerge 人工就绪。
    cleanup : 合并后 Windows 安全拆除 worktree + 剪枝 + 删分支。脏树守卫：worktree 有未提交改动时默认拒绝拆除（防不可逆丢失），加 -Force 显式覆盖。

  设计取舍见 docs\DEVOPS-WORKFLOW.md。本地闸、R3 与候选 CI 全通过后才可自动合并（用户选择 hands-off）。
  项目级常量（账号 / worktree 根 / Python 版本）来自 scripts\_config.ps1。

.PARAMETER TaskId  形如 T1-FOO，须存在 specs\tasks\<TaskId>.md
.PARAMETER Phase   start | ship | cleanup（默认 start）
.PARAMETER Base    基线分支（默认=仓库当前分支,自动探测 main/master,可显式覆盖）
.PARAMETER NoAutoMerge  ship 时不开启自动合并（改为人工点合并）
.PARAMETER Force  cleanup 阶段：worktree 有未提交改动时仍强制拆除（确认丢弃；缺省有脏改动即拒，防不可逆数据丢失）
.EXAMPLE
  # 所有相位命令都从**主检出**根目录跑（L86）。cd 进 worktree 只为编辑文件，别在里面跑这份脚本——
  # worktree 自带的副本会把 $RepoRoot 派生成 worktree 本身，被 fail-closed 守卫拒（哨兵 L86-WT）。
  pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase start
  # ... 在 <WorktreeRoot>\T0-SCAFFOLD 里写失败测试→实现→绿（编辑文件；相位命令仍回主检出跑）...
  pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase ship
  pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase cleanup
#>
[CmdletBinding()]
param(
  # TaskId 绑定期即校验字符集（同 check-cards 卡 id 契约）——start 外的相（red/ship/cleanup）此前拿未净化
  # TaskId 拼 $Wt/$Card 路径（cleanup 更 Remove-Item -Recurse -Force $Wt），路径穿越面。绑定期 ValidatePattern
  # 令 4 相统一在最外层即拒畸形/穿越 id（单一真相源=同一正则），不依赖 :109 卡存在守卫的偶发耦合（TD50/TD-113）。
  # ErrorMessage 内嵌 ASCII 哨兵 [TD50-BADID]，供 selftest 15l 跨子进程/locale 稳定判定绑定期拒（非 :109 throw）。
  [Parameter(Mandatory)]
  [ValidatePattern('^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$', ErrorMessage = 'TaskId 非法格式 [TD50-BADID]：值 "{0}" 须匹配卡 id 契约 ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$（同 check-cards）；red/ship/cleanup 各相均在绑定期校验，防未净化 TaskId 拼进 worktree/卡片路径致路径穿越。')]
  [string]$TaskId,
  [ValidateSet('start', 'red', 'ship', 'cleanup')][string]$Phase = 'start',
  [string]$Base = '',
  [switch]$NoAutoMerge,
  [switch]$Local,      # 本地完成：DoD(+可选评审) 后本地合并，不 push/PR/gh（治「T0 无远端/无 Codex 也能闭环」）
  [switch]$SkipRed,    # 显式跳过 RED-first 闸（纯文档/非 TDD 卡用；跳过会被记录）
  [switch]$Force       # cleanup：确认丢弃 worktree 内未提交改动后再拆除（TD47 脏树守卫的显式覆盖；缺省=有脏改动即拒）
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# UTF-8 控制台输出 + 顶层原生命令按退出码判（git diff --cached --quiet / gh pr view 等正常返回非零，不当终止错抛）：
# 单源自 _encoding.ps1（TD54/TD-117；DoD 包装器内仍按需局部覆盖 $true，见下方 ship 阶段）（30-lens C13）。
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # 前奏缺失（如 hermetic 单文件测试未随拷）即 fail-open 退回原行为
# 忽略会话里无效的 token（空串仍被 gh 视为“存在”→会遮蔽 keyring），用 Remove-Item 彻底清除
Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -ErrorAction SilentlyContinue

. (Join-Path $PSScriptRoot '_config.ps1')
. (Join-Path $PSScriptRoot '_cards.ps1')
. (Join-Path $PSScriptRoot '_gitbase.ps1')   # 共享基线名→引用解析（TD68 单一实现，与 review.ps1 共用防漂移）
. (Join-Path $PSScriptRoot '_scope.ps1')     # 共享范围闸判定核（TD93 单一实现，与 check-scope.ps1 共用防漂移）
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $Base) {
  # default base = repo's current branch (handles main vs master vs any); detached HEAD falls through to
  # probing the repo's actual default branch instead of hardcoding 'main' (TD63 item4：master-default 仓在
  # detached HEAD 下会得到错误的 $Base——镜像 review.ps1 的同款探测：origin/HEAD → main/master 存在性 → 告警兜底)。
  $Base = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
  if ($Base) { $Base = $Base.Trim() }
  if (-not $Base) {
    $originHead = (& git -C $RepoRoot symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>$null)
    if ($originHead) { $Base = ($originHead.Trim() -replace '^origin/', '') }
  }
  if (-not $Base) {
    foreach ($b in @('main', 'master')) {
      & git -C $RepoRoot rev-parse --verify --quiet $b 1>$null 2>$null
      if ($LASTEXITCODE -eq 0) { $Base = $b; break }
    }
  }
  if (-not $Base) {
    Write-Warning "无法探测默认分支（detached HEAD 且无 origin/HEAD、本地也无 main/master）——回退硬编码 'main'；如非预期请显式传 -Base <分支>。"
    $Base = 'main'
  }
}
$WtRoot = Get-ScaffoldWorktreeRoot   # 留空配置 => 按 OS 取默认（Windows <系统盘>\wt 如 C:\wt / *nix ~/.wt），可移植
$Wt = Join-Path $WtRoot $TaskId
$Card = Join-Path $RepoRoot "specs/tasks/$TaskId.md"

# ── 两道 fail-closed 守卫（TD-203 / 铁律 L86）。置于任何相位逻辑之前，覆盖 red/ship/cleanup 与远端路径。
#    各带**互不相同**的 ASCII 哨兵，令闸 15m 能分别证明是哪一道拦下的（共用哨兵则删掉一道也照样绿）。──
# (1) $RepoRoot 就是本卡自己的 worktree：即「用 worktree 内那份 task.ps1 跑相位命令」。此时 -Local 的
#     `git -C $RepoRoot merge $TaskId` 是把分支并进它自己（"Already up to date." + exit 0 的假成功），
#     **传 -Base 也救不了**（实测：-Base master 绕过下面的 (2)，仍假成功且 base 从未前进）。故与 $Base 无关地拒。
$wtResolved = if (Test-Path $Wt) { (Resolve-Path $Wt).Path } else { $null }
if ($wtResolved -and ($RepoRoot -ieq $wtResolved)) {
  throw "L86-WT: 相位命令跑在本卡自己的 worktree 里（`$RepoRoot == $Wt）——本地合并会把分支并进它自己而假报成功、base 从未前进，随后 cleanup 会强删这条从未合并的分支。回**主检出**跑相位命令（cd 进 worktree 只为编辑文件）。传 -Base 并不能修复此情形。"
}
# (2) 基线解析成本卡分支自己（主检出里显式误传 -Base <卡 id>）：范围闸 `$Base...HEAD` 得空 diff → 越界改动会被空过。
if ($Base -eq $TaskId) {
  throw "L86-BASE: base==TaskId（'$TaskId'）——基线退化成分支自己，范围闸会对空 diff 空过。传 -Base 指定真实基线分支（如 master）。"
}
$Py = $ScaffoldConfig.PythonVersion

function Step($m) { Write-Host "`n=== [$TaskId] $m ===" -ForegroundColor Cyan }

function Invoke-GhBeforeDeadline {
  param(
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  $remainingMs = [int][Math]::Floor(($Deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds)
  if ($remainingMs -le 0) { return [pscustomobject]@{ TimedOut = $true; ExitCode = 124; Stdout = ''; Stderr = '' } }
  $psi = [Diagnostics.ProcessStartInfo]::new()
  $psi.FileName = (Get-Command pwsh -ErrorAction Stop).Source; $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
  $psi.WorkingDirectory = $WorkingDirectory
  $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
  [void]$psi.ArgumentList.Add('-NoProfile'); [void]$psi.ArgumentList.Add('-Command')
  [void]$psi.ArgumentList.Add('$ghArgs = @($env:SCAFFOLD_GH_ARGS_JSON | ConvertFrom-Json); & gh @ghArgs; exit $LASTEXITCODE')
  $psi.Environment['SCAFFOLD_GH_ARGS_JSON'] = ($Arguments | ConvertTo-Json -Compress)
  $proc = [Diagnostics.Process]::new(); $proc.StartInfo = $psi
  try {
    if (-not $proc.Start()) { throw "无法启动 gh 子进程：$($Arguments -join ' ')" }
    $stdoutTask = $proc.StandardOutput.ReadToEndAsync(); $stderrTask = $proc.StandardError.ReadToEndAsync()
    if (-not $proc.WaitForExit($remainingMs)) {
      try { $proc.Kill($true) } catch { }
      try { [void]$proc.WaitForExit(1000) } catch { }
      $timedOutStdout = if ($stdoutTask.IsCompleted) { $stdoutTask.GetAwaiter().GetResult() } else { '' }
      $timedOutStderr = if ($stderrTask.IsCompleted) { $stderrTask.GetAwaiter().GetResult() } else { '' }
      return [pscustomobject]@{ TimedOut = $true; ExitCode = 124; Stdout = $timedOutStdout; Stderr = $timedOutStderr }
    }
    [void]$proc.WaitForExit()
    $streamMs = [int][Math]::Floor(($Deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds)
    $streamsOk = $false; if ($streamMs -gt 0) { try { $streamsOk = [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdoutTask, $stderrTask), $streamMs) } catch { $streamsOk = $false } }
    if (-not $streamsOk) {
      try { if (-not $proc.HasExited) { $proc.Kill($true) } } catch { }
      $lateOut = if ($stdoutTask.IsCompleted) { $stdoutTask.GetAwaiter().GetResult() } else { '' }; $lateErr = if ($stderrTask.IsCompleted) { $stderrTask.GetAwaiter().GetResult() } else { '' }
      return [pscustomobject]@{ TimedOut = $true; ExitCode = 124; Stdout = $lateOut; Stderr = $lateErr }
    }
    return [pscustomobject]@{ TimedOut = $false; ExitCode = $proc.ExitCode; Stdout = $stdoutTask.GetAwaiter().GetResult(); Stderr = $stderrTask.GetAwaiter().GetResult() }
  } finally { $proc.Dispose() }
}
function Wait-CiRetryBeforeDeadline([DateTimeOffset]$Deadline) {
  $sleepMs = [int][Math]::Min(1000, [Math]::Max(0, [Math]::Floor(($Deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds)))
  if ($sleepMs -gt 0) { Start-Sleep -Milliseconds $sleepMs }
}
# 「这是不是一个 JSON 整数」只能按 **CLR 类型** 判，不能按「能不能转成 long」判：
# ConvertFrom-Json 把 JSON 整数字面量映射成 Int64、小数映射成 Double、超出 Int64 的整数映射成 BigInteger、
# 带引号的映射成 String（本仓 PS 7.6 实测）。而 [long]'11' 与 [long]11.0 都会成功——那两种形态正是要拒的，
# 用 TryParse/强转做判据等于放行它们。列表只收能**无损**落进 Int64 的整数类型；UInt64 刻意不在列
# （它超出 Int64 时无法无损承载，而 total_count 与 id 去重集都以 Int64 承载，放它进来会把一次拒绝
# 变成一次强转溢出异常）。
function Test-JsonInteger($Value) {
  return ($Value -is [byte]) -or ($Value -is [sbyte]) -or ($Value -is [int16]) -or ($Value -is [uint16]) -or
    ($Value -is [int32]) -or ($Value -is [uint32]) -or ($Value -is [int64])
}
# 分页读取的身份契约（T0-CI-PAGED-CONTRACT）：check-runs / workflow-runs / jobs 三个 endpoint 共用本函数，
# 故契约在此一次收口、三处同时生效。
# 为什么 id 是必需的而非可选的：条目没有稳定身份时，一页被重放（或与下一页重叠）会把同一个绿 run 计成两条、
# 凑满 total_count 提前满足终止条件，从而**掩盖一个从未被读到的红 run 并走到 merge**。
# `$items.Count -eq $total` 只证明数量对得上，不证明读到的是 N 个**不同**的 run。真实 GitHub API 的这三个
# endpoint 都返回正整数 id，故「要求 id」是向真实形态收紧、不是新增假设。
function Get-GhPagedCollectionBeforeDeadline {
  param(
    [Parameter(Mandatory)][string]$EndpointTemplate,
    [Parameter(Mandatory)][string]$CollectionProperty,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  # $seen 跨页存活（声明在页循环之外）——跨页重放正是它要拦的形态，每页新建一个集合等于没有去重。
  $items = @(); $total = -1L; $seen = [Collections.Generic.HashSet[long]]::new()
  for ($page = 1; $page -le 100; $page++) {
    $endpoint = $EndpointTemplate.Replace('{page}', "$page")
    $api = Invoke-GhBeforeDeadline -Arguments @('api', $endpoint) -Deadline $Deadline -WorkingDirectory $WorkingDirectory
    if ($api.TimedOut) { return [pscustomobject]@{ Readable = $false; TimedOut = $true; Items = @(); Reason = "$CollectionProperty timeout/$page" } }
    try {
      if (($api.ExitCode -ne 0) -or [string]::IsNullOrWhiteSpace($api.Stdout)) { throw "gh api exit $($api.ExitCode) 或空输出" }
      $response = $api.Stdout | ConvertFrom-Json -ErrorAction Stop; $propertyNames = @($response.PSObject.Properties.Name)
      if (($propertyNames -cnotcontains 'total_count') -or ($null -eq $response.total_count)) { throw 'total_count 缺失/null' }
      if (($propertyNames -cnotcontains $CollectionProperty) -or ($null -eq $response.$CollectionProperty)) { throw "$CollectionProperty 缺失/null" }
      $totalRaw = $response.total_count
      if (-not (Test-JsonInteger $totalRaw)) { throw 'total_count 非 JSON integer' }
      $pageTotal = [long]$totalRaw
      if ($pageTotal -lt 0) { throw 'total_count 非负整数契约失败' }
      $collectionRaw = $response.$CollectionProperty
      if ($collectionRaw -isnot [System.Array]) { throw "$CollectionProperty 必须是 JSON array，不能是 scalar/object" }
      $pageItems = @($collectionRaw)
    } catch { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page/$($api.ExitCode):$($_.Exception.Message)" } }
    if ($total -lt 0) { $total = $pageTotal }
    elseif ($total -ne $pageTotal) { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty total $total->$pageTotal" } }
    # 身份校验必须在 `$items +=` **之前**：不合格/重复的条目一律不得进入累积，否则它已经把 total 凑近一格，
    # 后面无论怎么判都晚了。三个出口的 Reason 各自可辨（非对象 / id 非正整数 / id 重复），夹具才能证明
    # 命中的是去重出口，而不是更早的 total 漂移或 count>total（hygiene 要求）。
    foreach ($item in $pageItems) {
      if ($item -isnot [pscustomobject]) {
        return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page item-not-object" }
      }
      if ((@($item.PSObject.Properties.Name) -cnotcontains 'id') -or (-not (Test-JsonInteger $item.id)) -or ([long]$item.id -le 0)) {
        return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page id-not-positive-integer" }
      }
      if (-not $seen.Add([long]$item.id)) {
        return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page id-duplicate:$($item.id)" }
      }
    }
    $items += $pageItems
    if ($items.Count -gt $total) { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty count $($items.Count)>$total" } }
    if ($items.Count -eq $total) { return [pscustomobject]@{ Readable = $true; TimedOut = $false; Items = @($items); Reason = '' } }
    if ($pageItems.Count -eq 0) { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty empty:$($items.Count)/$total" } }
  }
  return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty >100" }
}
function Get-ExactHeadChecksBeforeDeadline {
  param(
    [Parameter(Mandatory)][string]$Head,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  $pages = Get-GhPagedCollectionBeforeDeadline `
    -EndpointTemplate "repos/{owner}/{repo}/commits/$Head/check-runs?per_page=100&page={page}" `
    -CollectionProperty 'check_runs' -Deadline $Deadline -WorkingDirectory $WorkingDirectory
  if (-not $pages.Readable) { return [pscustomobject]@{ Readable = $false; TimedOut = $pages.TimedOut; Runs = @(); Blocking = @(); Reason = $pages.Reason } }
  $runs = @($pages.Items)
  $blocking = @($runs | Where-Object {
    ("$($_.status)" -ieq 'completed') -and
    ("$($_.conclusion)" -in @('failure', 'cancelled', 'timed_out', 'action_required', 'startup_failure', 'stale'))
  })
  return [pscustomobject]@{ Readable = $true; TimedOut = $false; Runs = $runs; Blocking = $blocking; Reason = '' }
}

# TD45：卡片解析共享自 _cards.ps1（front-matter-only 提取 + 大小写敏感取值 + 注释剥离）。
# 旧 Get-CardField 曾整文件 `Select-String`（大小写不敏感、正文/前置元数据不分），与 check-cards 的契约脱节：
# (a) 卡片正文里一行形似 `dod_command: ...` 的文档示例会被当真；(b) `DOD_COMMAND:`（大小写错）仍被找到；
# (c) 不剥注释，`title: 真标题 # 备注` 会把注释泄进 PR 标题。三者 check-cards 从 start 起就已正确拒绝/剥离，
# ship 阶段的取值必须与之同判——否则 ship 执行的是 check-cards 从未核准过的内容。
function Get-CardField($name) {
  if (-not (Test-Path $Card)) { return $null }
  $fm = Get-FrontMatter (Get-Content $Card -Raw)
  if (-not $fm) { return $null }
  $v = Get-Scalar $fm $name
  if ($null -eq $v) { return $null }
  # dod_command 故意保留注释（同 check-cards.ps1:127 的取值——注释可能是命令片段的一部分，如 URL 里的 #），
  # 其余字段（如 title）剥尾随 ` # 注释`，防注释泄入下游用途（PR 标题等）。
  if ($name -eq 'dod_command') { $v = $v.Trim() } else { $v = Get-UncommentedValue $v }
  if ([string]::IsNullOrWhiteSpace($v)) { return $null }
  return $v
}

# 效果账本（TD2）：ship 闸门**真拦截**时追加一行 JSONL 到 _local/effectiveness-ledger.jsonl（gitignored）。
# best-effort：写失败一律吞掉——仪表盘绝不能影响闸门本身。triage 探针 8 读它做各闸拦截计数复审。
function Add-CatchRecord($gate, $detail) {
  try {
    $dir = Join-Path $RepoRoot '_local'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir -ErrorAction SilentlyContinue | Out-Null }
    (@{ ts = (Get-Date).ToString('o'); gate = $gate; task = $TaskId; detail = "$detail" } | ConvertTo-Json -Compress) |
      Add-Content -Path (Join-Path $dir 'effectiveness-ledger.jsonl') -Encoding utf8
  } catch { }
}

# PR 建好后与自动合并前各调用一次：查询失败、空输出、或与已评审基线不一致均 fail-closed。
function Assert-RemotePrBase {
  param([Parameter(Mandatory)][int]$Pr, [Parameter(Mandatory)][string]$ExpectedBase)
  $actual = (& gh pr view $Pr --json baseRefName -q .baseRefName 2>$null)
  $queryExit = $LASTEXITCODE
  if ($actual) { $actual = "$actual".Trim() }
  if ($queryExit -ne 0 -or [string]::IsNullOrWhiteSpace("$actual")) {
    Add-CatchRecord 'scope' "PR #$Pr baseRefName 查询失败或为空（exit $queryExit）"
    throw "范围闸 fail-closed：未能确认 PR #$Pr 的实际 base 分支（gh pr view exit $queryExit，输出为空）。检查 gh 登录/API 后重 ship；未确认合并目标前禁止评审或合并（Codex#1/TD84）。"
  }
  if ($actual -cne $ExpectedBase) {
    Add-CatchRecord 'scope' "PR #$Pr baseRefName '$actual' != 评审基线 '$ExpectedBase'"
    throw "范围闸 fail-closed（PR 合并目标 ≠ 评审基线）：PR #$Pr 实际 base 分支是 '$actual'，但本次 review/范围对照 '$ExpectedBase'——`gh pr merge` 会并入 '$actual'、评审却没审对那个基线的 diff（Codex#1/TD84）。`gh pr edit $Pr --base $ExpectedBase` 改回，或用正确 -Base 重 ship。"
  }
}

# ── -Local 合并目标不变量（单一实现，ship 入口 + 合并前两处调用；R3 PR#102 九轮 + 双审计 F1/F3）──
# -Local 的合并目标 = 主检出**当前分支** $Cur（`git -C $RepoRoot merge $TaskId` 并入它，本地 ref）。scope/R3 都对照 $Base 算 diff。
# 以下任一即 fail-closed（否则「对照 A 评审、却并入 B」或自并入空操作，随后 cleanup branch -D 丢弃未合并的 work）：
function Assert-LocalMergeTarget {
  param([string]$Cur, [string]$Base, [string]$TaskId)
  if (-not $Cur) {
    throw "范围闸 fail-closed（-Local 无法确定合并目标）：主检出处于 detached HEAD——`git merge $TaskId` 会并入游离 HEAD、不落任何分支，而 review/范围对照 '$Base'。请在主检出 git switch <目标分支> 后重 ship。"
  }
  # 注：合并目标 == 任务分支（worktree 内自调用 / -Base <卡 id> 退化）由 ship 顶部已合入的 L86-WT/L86-BASE 守卫更早拦下
  # （$Cur==$TaskId 时 $Base 缺省即 = $TaskId → L86-BASE 命中）；此处不再重复该 clause，避免与 L86 双守卫冗余。
  if ($Base -match '^origin/') {
    throw "范围闸 fail-closed（-Local 不接受远端限定基线）：-Local 把 $TaskId 并入主检出当前分支 '$Cur'（本地 ref），而 -Base '$Base' 是远端跟踪引用——本地领先 origin 时会对照陈旧 origin、却并入本地（TD68 同类错基线）。去掉 origin/ 前缀（传 -Base '$Cur'）或改走远端 ship。"
  }
  if ($Base -and ($Base -cne $Cur)) {   # F4：大小写敏感（refs 大小写敏感；-Base MAIN vs 分支 main 须判为不一致）
    throw "范围闸 fail-closed（-Local 基线错配）：-Local 会把 $TaskId 并入主检出当前分支 '$Cur'，但 -Base 是 '$Base'——会对照 '$Base' 评审/范围、却并入 '$Cur'（TD68 同类错基线）。git switch '$Base' 或改传 -Base '$Cur' 后重 ship。"
  }
}

if (-not (Test-Path $Card)) { throw "任务卡不存在: $Card（先在 specs\tasks\ 建卡）。" }

switch ($Phase) {

  'start' {
    Step '校验任务卡（check-cards：id=文件名 / status 枚举 / branch·worktree 不漂移 / dod_command·allow_paths 完整 / 拒卡文占位符 token 字面量）'
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-cards.ps1') -TaskId $TaskId
    if ($LASTEXITCODE -ne 0) { throw "任务卡校验未过：先修正 specs\tasks\$TaskId.md 再 start。" }

    Step "R1 建 worktree $Wt（分支 $TaskId ← $Base）"
    # try/catch 给出可操作错误：默认 worktree 根落在 <系统盘>\wt；若该盘/路径不可写，
    # 指向 _config.ps1 WorktreeRoot 而非裸抛 DriveNotFoundException（30-lens C02）。
    try { New-Item -ItemType Directory -Force $WtRoot -ErrorAction Stop | Out-Null }
    catch { throw "无法创建 worktree 根目录 '$WtRoot'：$($_.Exception.Message)。请在 scripts\_config.ps1 设 WorktreeRoot 为一个可写的浅路径（如 C:\wt 或 ~/.wt）后重试。" }
    if (Test-Path $Wt) { throw "worktree 已存在: $Wt。恢复指引——若上次 ship 中断（已 commit、未合并/未推）→ 直接重跑 `-Phase ship` 续（ship 各闸幂等、可安全重入）；若要从头重来 → 先 `-Phase cleanup` 拆除再 start。" }
    & git -C $RepoRoot worktree add -b $TaskId $Wt $Base
    if ($LASTEXITCODE -ne 0) { throw 'git worktree add 失败' }

    Step '引导隔离环境（.venv / node_modules 每 worktree 独立，gitignored）'
    Push-Location $Wt
    try {
      if ((Test-Path (Join-Path $Wt 'pyproject.toml')) -and (Get-Command uv -ErrorAction SilentlyContinue)) {
        & uv venv --python $Py .venv 2>&1 | Write-Host
        & uv sync 2>&1 | Write-Host
      } elseif (Test-Path (Join-Path $Wt 'pyproject.toml')) { Write-Warning 'uv 不在 PATH，跳过后端环境引导。' }
      if (Test-Path (Join-Path $Wt 'frontend/package.json')) {
        Push-Location (Join-Path $Wt 'frontend'); & npm install 2>&1 | Write-Host; Pop-Location
      }
      if (Test-Path (Join-Path $RepoRoot '.env')) { Copy-Item (Join-Path $RepoRoot '.env') $Wt -Force }
    } finally { Pop-Location }

    Write-Host "`nTDD 提醒（R2）：" -ForegroundColor Yellow
    Write-Host "  1) 先写失败测试，确认 RED（跑卡片 dod_command，期望非 0）。" -ForegroundColor Yellow
    Write-Host "  2) 实现到 GREEN，不改冻结契约/manifest（见 _config.ps1 FrozenPaths）。" -ForegroundColor Yellow
    Write-Host "  3) 重构 + R4 测试剪枝（mutation-survivor 法，见 DEVOPS-WORKFLOW.md）。" -ForegroundColor Yellow
    Write-Host "  完成后：pwsh -File scripts\task.ps1 -TaskId $TaskId -Phase ship" -ForegroundColor Yellow
    $dod = Get-CardField 'dod_command'
    if ($dod) { Write-Host "  本卡 DoD: $dod" -ForegroundColor DarkGray }
  }

  'red' {
    # R2 RED-first 检查点（治「RED 仅是 start 阶段的 Write-Host 提醒、无任何强制」）：
    # 跑卡片 DoD，断言**非零**（测试确实先失败），把证据落 .review\<id>.red 供 ship 校验。
    if (-not (Test-Path $Wt)) { throw "worktree 不存在: $Wt（先 -Phase start）" }
    Push-Location $Wt
    try {
      Step 'R2 RED 检查点（TDD：先写会失败的测试 → 跑 DoD，期望 NON-zero）'
      # TD69/L95：红相前置一次 check-cards（同 start:154 / ship:218 的确定性契约），令「嵌套 pwsh -Command + 内插
      #   $ → 双层包裹铸成 vacuous RED」这类坏 dod_command 在**假 RED 被铸出之前**即被拒（红相此前从不校验卡片、
      #   会把 dod_command 的 ParserError 当合法 RED 收下）。单一真相源 = check-cards.ps1，不在此复制其判定逻辑。
      & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-cards.ps1') -TaskId $TaskId
      if ($LASTEXITCODE -ne 0) { throw "任务卡校验未过（见上）：先修正 specs\tasks\$TaskId.md 再 -Phase red（TD69：dod_command 勿用嵌套 pwsh -Command + 内插变量写法，改无变量内联）。" }
      $dod = Get-CardField 'dod_command'
      if (-not $dod) { throw "卡片缺少 dod_command 字段: $Card" }
      Write-Host "运行（期望 RED / 非 0）: $dod" -ForegroundColor DarkGray
      $PSNativeCommandUseErrorActionPreference = $false   # 非零不抛：要捕获退出码判 RED
      & pwsh -NoProfile -Command $dod 2>&1 | Write-Host
      $dodExit = $LASTEXITCODE
      if ($dodExit -eq 0) {
        throw 'RED 检查失败：dod_command 退出 0（已是 GREEN）。TDD 要求先有失败测试——先写断言尚未实现行为的测试（应失败）再跑 -Phase red。'
      }
      $proofDir = Join-Path $Wt '.review'
      New-Item -ItemType Directory -Force $proofDir | Out-Null
      $redSha = (& git -C $Wt rev-parse HEAD 2>$null)
      $redSha = if ($redSha) { $redSha.Trim() } else { '(no-commit-yet)' }
      @{ taskId = $TaskId; sha = $redSha; dodExit = $dodExit; phase = 'red' } |
        ConvertTo-Json | Set-Content (Join-Path $proofDir "$TaskId.red") -Encoding utf8
      Write-Host "RED 已确认（dod 退出 $dodExit）。证据落 .review\$TaskId.red。实现到 GREEN 后 -Phase ship（ship 会校验本证据）。" -ForegroundColor Green
    } finally { Pop-Location }
  }

  'ship' {
    # ── -Local 合并目标守卫（fail-closed，置于 ship 最前，任何工作之前；合并前会**再断言一次**见 F3）──
    # 单一实现在 Assert-LocalMergeTarget：detached / 目标==任务分支(F1) / 远端限定 / 与当前分支不一致，任一即拒。
    if ($Local) {
      $shipCur = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
      if ($shipCur) { $shipCur = $shipCur.Trim() }
      Assert-LocalMergeTarget -Cur $shipCur -Base $Base -TaskId $TaskId
    }
    # GitHub 的 baseRefName 不含 origin/ 前缀。显式 -Base origin/master 与 master 同义；从这里起远端
    # fetch / PR create / base 校验 / review 统一使用此单源，避免部分归一化造成假错配。
    $shipBase = if ($Local) { $Base } else { $Base -replace '^origin/', '' }
    if (-not $Local -and [string]::IsNullOrWhiteSpace($shipBase)) {
      throw "远端 ship 的 -Base '$Base' 归一化后为空；请传分支名（如 master 或 origin/master）。"
    }

    if (-not (Test-Path $Wt)) { throw "worktree 不存在: $Wt（先 -Phase start）" }

    # T26-SHIPSAGA 腿完成跟踪：ship 是多腿可重试 saga，任一腿失败须在失败时刻自述进度（TD85 事件实证：四个并发
    # 会话各自从散文重推断状态、其一误判）。有序腿名与下方既有 Step 标签一一对应；只记相内内存、不写盘
    # （ship 幂等可重入，跨进程状态无必要）。catch 只报告后原样 rethrow——异常语义/退出码/失败面均不变。
    $sagaLegs = @('卡校验', 'DoD', 'verify', '提交', '范围闸', '许可闸', '防泄露闸', '真实 diff 预算') + $(if ($Local) { @('R3 评审', '本地合并') } else { @('push+PR', 'R3 评审', 'CI gate', '合并') })
    $sagaDone = @()
    # 腿间非腿操作的显式追踪（R3 r3 #9）：卡校验→DoD 之间还有预检（评审后端可用性/账号守卫/环境引导）与 RED 证据闸
    # 两段非 Step 操作——只按「首个未完成腿」推断会把这些失败误报成 DoD（TD85 事件正是这样被误判的）；$sagaAt 非空
    # 即失败点真相源，进入某腿的 Step 前清空。
    $sagaAt = ''
    $sagaHeadMoved = $false    # 本次 ship 是否真产生了新 commit——「提交」腿完成≠HEAD 前移（no-op 提交不动 HEAD），
                               # TD85 死锁只在 HEAD 真前移且未 -SkipRed 时存在（R3 r5 #9）
    $sagaLocalMerged = $false  # -Local 合并是否已成功——合并腿失败按阶段状态分流（post-merge 凭据 / 合并中 / 守卫拦下）
    try {   # saga 报告层（体内缩进保持原样：外科式最小 diff，不重排任何既有闸门）

    Step '卡片校验（check-cards：ship 与 start 用同一份确定性契约重跑，TD45——防 start 后卡片漂移 / -Phase ship 未 fresh start 时跳过校验）'
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-cards.ps1') -TaskId $TaskId
    if ($LASTEXITCODE -ne 0) { throw "任务卡校验未过（见上）：ship 与 start 共用同一份 check-cards 契约——先修正 specs\tasks\$TaskId.md 再 ship（卡片可能在 start 后被编辑，或本次 ship 未经 fresh start）。" }
    $sagaDone += '卡校验'
    $sagaAt = '预检（评审后端可用性/账号守卫/环境引导——位于 卡校验 与 RED 证据闸 之间）'

    # R3 评审后端可用性判定（与下方 -Local 分支同一份，提前求值供两路复用；TD22-C23）：
    # 远端 ship 必经第二模型评审 → 无后端（无 codex 且 ReviewCommand 空）时在闸序最前 fail-fast，
    # 不再等 push + 开 PR 之后才卡死在评审闸、白留远端半合并态分支。-Local 路径评审仍为可选（行为不变）。
    $reviewAvail = (Get-Command codex -ErrorAction SilentlyContinue) -or ($ScaffoldConfig.ContainsKey('ReviewCommand') -and $ScaffoldConfig.ReviewCommand)
    if (-not $Local -and -not $reviewAvail) {
      Add-CatchRecord 'review' '无评审后端（远端 ship push 前 fail-fast）'
      throw "无评审后端：远端 ship 必经第二模型评审（R3），但未检测到 codex 且 _config.ps1 ReviewCommand 为空——已在 push/开 PR 之前停止。补救任选其一：1) 安装 codex CLI；2) 在 scripts\_config.ps1 配置 ReviewCommand 接入其他评审后端；3) 改用 -Phase ship -Local 走本地闭环（无 push/PR）。"
    }
    # 个人账号守卫：push/PR/合并前确认仅配置的个人账号（禁组织）。-Local 无远端 → 跳过。
    if (-not $Local) {
      . (Join-Path $PSScriptRoot '_guard.ps1')
      Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemote
    }
    Push-Location $Wt
    try {
      $sagaAt = 'RED 证据闸（TDD 失败证据在位且新鲜——位于 预检 与 DoD 之间）'
      # R2 RED-first 闸（治「RED 无强制、可静默跳过」+ TD36「证据仅 Test-Path 可伪造 / -SkipRed 旁路未记账」）：
      # ship 前须有 -Phase red 留下的**内容有效**失败证据——不止文件在，还要 taskId 对得上、dodExit 真非零
      # （空文件 / 别卡证据 / dodExit=0 均拒），拦截与旁路都落效果账本供审计（兑现 param/throw 文案的「会记录」）。
      $redProof = Join-Path $Wt ".review/$TaskId.red"
      $redShaForMint = ''   # T35-RECEIPT：RED 闸校验通过时在手的证据 sha——为 40-hex 时提交后用作水位线收据 redSha；-SkipRed/占位值则留空=不铸
      if (-not $SkipRed) {
        if (-not (Test-Path $redProof)) {
          Add-CatchRecord 'red' 'RED 证据缺失'
          throw "缺少 RED 证据（.review\$TaskId.red）：TDD 要求先跑 -Phase red 确认测试会失败，再 ship。非 TDD 卡（如纯文档）用 -SkipRed 显式跳过（会记录）。"
        }
        # 内容校验：Test-Path 只证「文件在」，不证「是本卡真跑出的 RED」——空文件 / 别卡证据 / dodExit=0 都能骗过存在性检查。
        $redOk = $false; $redWhy = ''; $redShaMoved = $false
        try {
          $rp = Get-Content $redProof -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
          $rf = @{}; $rp.PSObject.Properties | ForEach-Object { $rf[$_.Name] = $_.Value }   # StrictMode 安全：缺字段取 $null 而非抛
          # TD63 item3：sha 字段写了却从不校验——陈旧证据（worktree 在 RED 之后已有新提交、或证据抄自别的分支/
          # 别次 RED）也能过闸。RED 与 ship 之间通常无中间提交（实现全在工作树里、直到 ship 才一次性提交），
          # 故合法证据的 sha 应等于此刻 HEAD（同 review.ps1 裁决 sha 新鲜度守卫之理）；(no-commit-yet) 是 -Phase red
          # 在从未提交过的全新分支上的合法占位值，不当陈旧证据处理。
          # codex R3 评审两轮纠偏：
          #  ① sha **缺失/空**须与「sha 存在但不符」同等严处——旧写法用 `"$sha" -and ...` 短路，令 taskId/dodExit
          #     对齐但 sha 字段缺失/空串的伪造/残缺证据静默放行（绕过本条校验本身）。
          #  ② `(no-commit-yet)` 白名单不得无条件放行——旧写法只要 sha 字面量等于该占位值就直接判合法，即便
          #     worktree **此刻确有真实 HEAD**；伪造证据可直接抄这个占位字符串完全绕过 sha 校验。占位值只应在
          #     worktree 真无法解析 HEAD（`git rev-parse HEAD` 失败，如全新空仓）时才合法。
          # 改法：先分两支——worktree 有真实 HEAD 时，sha 必须**逐字节等于**它（不再有任何占位值例外）；
          # worktree 确实无 HEAD 时，才唯一接受 `(no-commit-yet)` 占位值。
          $curHeadForRed = (& git -C $Wt rev-parse HEAD 2>$null); if ($curHeadForRed) { $curHeadForRed = $curHeadForRed.Trim() }
          $redSha = "$($rf['sha'])"
          if ("$($rf['taskId'])" -ne $TaskId) { $redWhy = "证据 taskId=$($rf['taskId']) ≠ 本卡 $TaskId（张冠李戴）" }
          elseif ($null -eq $rf['dodExit'] -or [int]$rf['dodExit'] -eq 0) { $redWhy = "证据 dodExit=$($rf['dodExit'])（非非零：测试当时并未失败，RED 无效）" }
          elseif ([string]::IsNullOrWhiteSpace($redSha)) { $redWhy = '证据缺少 sha 字段（或为空）——无法证明证据对应本次 worktree 状态（残缺/伪造证据）' }
          elseif ($curHeadForRed) {
            if ($redSha -ne $curHeadForRed) {
              $redShaMoved = $true   # sha 前移：最常见良性成因是 ship 中断后 resume（commit 已把 HEAD 前移）——T35-RECEIPT 收据自洽校验隔离到 try 之外（下方），闸不放宽
              $redWhy = "证据 sha=$redSha 与当前 HEAD=$curHeadForRed 不符（陈旧/伪造证据：worktree 在 RED 之后已有新提交、证据抄自别的分支/别次 RED，或滥用 '(no-commit-yet)' 占位值绕过校验——worktree 现有真实 HEAD 时该占位值不合法）"
            } else { $redOk = $true; $redShaForMint = $redSha }
          }
          elseif ($redSha -eq '(no-commit-yet)') { $redOk = $true }
          else { $redWhy = "证据 sha=$redSha，但当前 worktree 无法解析 HEAD（真无提交）——此态只接受占位值 '(no-commit-yet)'" }
        } catch { $redWhy = "证据非合法 JSON 或不可读（疑伪造）：$($_.Exception.Message)" }
        # T35-RECEIPT（水位线收据 · TD89 根治）：evidence sha 前移（结构合法但≠HEAD，即 $redShaMoved）时，检查 ship 自身提交腿铸的收据是否**四谓词自洽**——
        # 自洽=合法 resume、放行进 DoD（既有闸不放宽、仅此一条分支）；不自洽/缺失/损坏则维持 $redShaMoved 的 $redWhy 落既有 fail-closed throw。
        # **隔离于上方证据解析 try**（R3 r8 #9 纠偏）：git-common-dir 解析失败 fail-closed 抛的 [T35-RECEIPT] 哨兵错须原样直传 saga——置于证据解析 try
        # 内会被其 catch 误诊成「证据 JSON 非法」并吞掉哨兵。四谓词**唯一校验点**在此（saga 只测在位性、不复算）：①taskId==本卡 ②receipt.redSha==
        # evidence.sha 且**双侧 40-hex**（占位值双侧禁入）③redSha 为当前 HEAD 祖先 ④commitSha 为当前 HEAD 或其祖先。
        if ($redShaMoved -and (-not $redOk)) {
          $rcGcdR = "$(& git -C $Wt rev-parse --git-common-dir 2>$null)".Trim()
          if ($rcGcdR -and -not [System.IO.Path]::IsPathRooted($rcGcdR)) { $rcGcdR = Join-Path $Wt $rcGcdR }
          if (-not $rcGcdR -or -not (Test-Path $rcGcdR -PathType Container)) { throw "[T35-RECEIPT] 无法解析 git-common-dir（水位线收据平面不可达：'$rcGcdR'）——fail-closed，排查 git 环境后重 ship。" }
          $rcPathR = Join-Path (Join-Path $rcGcdR 'scaffold-shipped') $TaskId
          if (Test-Path $rcPathR) {
            try {
              $rcObj = Get-Content $rcPathR -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
              $rcf = @{}; $rcObj.PSObject.Properties | ForEach-Object { $rcf[$_.Name] = $_.Value }   # StrictMode 安全：缺字段取 $null
              $rcRed = "$($rcf['redSha'])"; $rcCommit = "$($rcf['commitSha'])"
              $p1 = ("$($rcf['taskId'])" -ceq $TaskId)
              $p2 = ($rcRed -cmatch '^[0-9a-f]{40}$') -and ($redSha -cmatch '^[0-9a-f]{40}$') -and ($rcRed -ceq $redSha)   # 占位/非 40-hex 任一侧即 false（双侧禁入）
              $p3 = $false; if ($p2) { & git -C $Wt merge-base --is-ancestor $rcRed HEAD 2>$null; $p3 = ($LASTEXITCODE -eq 0) }
              $p4 = $false; if ($rcCommit -cmatch '^[0-9a-f]{40}$') { & git -C $Wt merge-base --is-ancestor $rcCommit HEAD 2>$null; $p4 = ($LASTEXITCODE -eq 0) }
              if ($p1 -and $p2 -and $p3 -and $p4) {
                $redOk = $true; $redShaForMint = $redSha   # 视同「RED 闸校验通过」：resume 真提交时按同规则重铸（redSha 沿用原值不变、commitSha 取新 HEAD）
                Write-Host "水位线收据自洽（T35-RECEIPT resume 放行）：evidence.sha=$redSha 为当前 HEAD 祖先、收据 commitSha 为 HEAD 或其祖先——提交后重跑经收据放行 RED 闸，DoD/范围/许可/密钥/R3 全部重过 ✓" -ForegroundColor DarkGray
              }
            } catch { }   # 收据损坏/不可读 → 不放行、**不改 $redWhy**（保留 sha 前移诊断、不误诊），落既有 fail-closed throw（不放宽）
          }
        }
        if (-not $redOk) {
          Add-CatchRecord 'red' $redWhy
          # TD85-RESUME：sha 前移的最常见良性成因是 ship 被中断后的 resume——commit 已成功（HEAD 前移）、R3/merge 未完成
          # （典型 R3 元失败：配额耗尽 / 裁决文件解析失败，非对 diff 的实质 block）。此时 RED 证据 sha 是 commit **之前**的旧 HEAD，
          # 必与当前 HEAD 不符；且代码已 GREEN、无法再合法 `-Phase red`（会报「已是 GREEN」）——重跑整条 `-Phase ship` 在此死锁。
          # 本 RED 闸只把关「证据新鲜」、绝不放宽。**现行教义**（T36-DOCTRINE，见 DEVOPS-WORKFLOW「ship 非原子→重跑即 resume」）：
          # 收据在位 = 修复后重跑原封不动的同一条 ship（全闸重判，无豁免）；收据**缺失**且已 push 才走该节「手工补跑全部确定性闸
          # （CI 无范围闸，不可仅靠 CI）→ 最后手段」路径；收据**不自洽**（S8，含 watershed 后历史改写）须先按 S8 对齐远端并核验
          # PR head == 已过闸的本地 HEAD，再按上述兜底。**未手工补跑全部确定性闸即对已开 PR 评审+合并**属禁止——跳过范围闸（TD89 根因）。
          # T35-RECEIPT hint 兜底路由器（与 RED 闸放行分支同码点）：走到此=sha 前移**且收据未能放行**（缺失/损坏/不自洽），故不再教「重跑 ship」（确定性死循环）。
          # **推送态精确分类（R3 r14 #2 · 用户裁定 T35 纳远端底座）**：先 fetch 刷新 task ref，用**两个安全判据**覆盖 6 态（remote-absent/entirely-unpushed/
          # partially-pushed/equal/remote-ahead/diverged），杜绝「reset 改写已发布提交」与「合并未过闸的 remote-only 变更」：
          #   ① reset-safe = 远端分支不存在 **或** `merge-base --is-ancestor origin/<id> <redSha>` 退出 0（远端无 post-RED 提交、reset 只回退本地提交）；
          #   ② merge-safe = 远端分支存在 **且** 本地 HEAD == origin/<id>（PR head 恰是过闸产物、无 remote-only 变更）。
          # **命令一律 git -C "$Wt" 锚定 worktree**（相位从主检出跑、L86，裸 HEAD/fetch/reset/merge-base 会指向基线分支误判/误动，R3 r12/r13/r14 #9/#2）。
          # 卡 §1.2 的 `git log origin/<id>..HEAD` 判据保留为「本地领先远端提交清单」的可读佐证。pushed 各态权威长文归 DEVOPS-WORKFLOW（T36）。保留 TD85-RESUME 哨兵（15q 锁）。
          $resumeHint = if (-not $redShaMoved) { '' }
          elseif ($redSha -cnotmatch '^[0-9a-f]{40}$') {
            # R3 r16 #9：证据 sha 为占位值（(no-commit-yet)）→ 无 reset 靶、无法做基于 sha 的推送态判据；**短路到人工路由，绝不发不完整的 hash 依赖命令**（如空靶 merge-base）。
            "`n[TD85-RESUME] 水位线收据未能放行本次 resume，且 RED 证据 sha 为占位值（该分支从未有基线提交，无 reset 靶、无法据 sha 做推送态分类）——人工核对工作树后处置，见 docs/DEVOPS-WORKFLOW.md「ship 非原子→重跑即 resume」的 TD85-RESUME 段。"
          }
          else {
            # 40-hex 证据：完整推送态分类 hint（命令一律 git -C "$Wt" 锚定；$redSha 已保证 40-hex，直接用作靶与判据参数）。
            "`n[TD85-RESUME] 水位线收据未能放行本次 resume（缺失/损坏/不自洽）——视同收据缺失走兜底。**先精确判推送态**（命令一律 git -C `"$Wt`" 锚定 worktree）：先 ``git -C `"$Wt`" fetch --prune origin`` 刷新并**修剪陈旧跟踪引用**（--prune：远端分支已删则 origin/$TaskId 随之消失；裸 fetch origin $TaskId 遇远端已删会 exit 128 且**留陈旧 origin/$TaskId**致误判）——**fetch 失败即停、勿据陈旧 origin/$TaskId 分类**（fail-closed）；再 ``git -C `"$Wt`" rev-parse --verify --quiet origin/$TaskId``（无输出=远端分支不存在）；两安全判据——**reset-safe** = 远端不存在 或 ``git -C `"$Wt`" merge-base --is-ancestor origin/$TaskId $redSha`` 退出 0（远端无 post-RED 提交）；**merge-safe** = 远端存在且 ``git -C `"$Wt`" rev-parse HEAD`` == ``git -C `"$Wt`" rev-parse origin/$TaskId``。领先提交清单可读佐证：``git -C `"$Wt`" log origin/$TaskId..HEAD``。**三分流覆盖 6 态**：`n  · **reset-safe**（remote-absent / entirely-unpushed：远端无 post-RED 提交）：git -C `"$Wt`" reset --soft $redSha（把 HEAD 归位到 RED 证据 sha、改动保留在暂存区；**须 git -C `"$Wt`" 锚定**——相位从主检出跑，裸命令误动基线；非 HEAD~1——多提交分支上 HEAD~1 制造二次死锁），修复后重跑 -Phase ship（全部确定性闸+R3 重过、无旁路）。`n  · **仅 merge-safe**（equal：已推尽、head 相等）：勿 reset（会改写已发布提交）；**先手动补跑全部确定性闸（DoD、verify、范围闸、许可闸、防泄露闸、真实 diff 预算——CI 无范围闸兜底，不可仅靠 CI）**后 pwsh -NoProfile -File scripts\review.ps1 -WorktreePath `"$Wt`" -Base $shipBase -PostStatus -PrNumber <PR号>，再 gh pr merge <PR号> --squash、scripts\task.ps1 -TaskId $TaskId -Phase cleanup。`n  · **两者皆否**（partially-pushed / remote-ahead / diverged）：勿 reset（改写已发布）、勿直接合并（PR head 陈旧或含 remote-only 未过闸变更）——先 git -C `"$Wt`" push 推尽（partially-pushed）/ git -C `"$Wt`" pull --no-rebase 对齐（remote-ahead/diverged；**必 --no-rebase**——watershed 之后禁一切历史改写，裸 git pull 遇 pull.rebase=true 会 rebase 改写已提交历史），对齐后**同样先手动补跑全部确定性闸再** review.ps1 -PostStatus + 合并；详见 docs/DEVOPS-WORKFLOW.md「ship 非原子→重跑即 resume」的 TD85-RESUME 段（pushed 各态精确恢复权威教义）。"
          }
          throw "RED 证据无效（$redWhy）。重跑 -Phase red 生成真实失败证据再 ship；勿手工伪造 .review\$TaskId.red。$resumeHint"
        }
        Write-Host "RED 证据有效（$TaskId.red：taskId 对齐 · dodExit=$([int]$rf['dodExit']) 非零）✓" -ForegroundColor DarkGray
      } else {
        Add-CatchRecord 'skip-red' 'RED-first 闸经 -SkipRed 显式跳过（本卡未经红→绿验证）'
        Write-Warning '已跳过 RED-first 闸（-SkipRed）——本卡未经红→绿验证（已记效果账本）。'
      }

      $sagaAt = ''   # 自此失败点=首个未完成腿（各腿 Step 之间再无非腿操作）
      Step 'R2 DoD 闸门（必须全绿）'
      $dod = Get-CardField 'dod_command'
      if (-not $dod) { throw "卡片缺少 dod_command 字段: $Card" }
      Write-Host "运行: $dod" -ForegroundColor DarkGray
      # 让 dod 里**任一** native 命令非零即失败（否则只取最后一句退出码，
      # 多语句 dod（如 python -c ...; pytest ...）会被末句成功掩盖前句失败）
      $wrapped = "`$ErrorActionPreference='Stop'; `$PSNativeCommandUseErrorActionPreference=`$true; $dod"
      & pwsh -NoProfile -Command $wrapped
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'dod' "DoD exit $LASTEXITCODE"; throw "DoD 未通过（退出码 $LASTEXITCODE）。修绿再 ship。" }
      $sagaDone += 'DoD'

      Step 'R2 verify 总闸（项目级确定性回归；free+private 下本地即权威）'
      # 跑工作树自带的 verify.ps1（卡 DoD 只验本卡窄面，verify 兜项目级跨子系统回归）。空配置/脚手架期 verify 自身优雅降级。
      & pwsh -NoProfile -File (Join-Path $Wt 'scripts/verify.ps1')
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'verify' "verify exit $LASTEXITCODE"; throw 'verify.ps1 未过（项目级回归红）。修绿再 ship。' }
      $sagaDone += 'verify'

      Write-Host "`n安全闸提醒（建议性 · 非自动闸）：涉敏感面时，commit 前宜先跑 /security-review-local。" -ForegroundColor Yellow
      Write-Host "  注：security-review 是模型在环的【建议】层（非确定性 DoD 闸）；硬编码密钥/被追踪机密由下面 check-secrets 强制拦。" -ForegroundColor DarkGray

      Step '提交改动'
      # TD44：本脚本关了 $PSNativeCommandUseErrorActionPreference（:46）→ 原生命令非零不抛，须逐个显式校验退出码，
      # 否则失败静默续跑（同 gh-bootstrap:180-184 的已知坑）。git add 失败会漏 stage → 后续 push 陈旧内容。
      & git add -A
      if ($LASTEXITCODE -ne 0) { throw "git add -A 失败（exit $LASTEXITCODE）——已中止以防提交不完整。检查工作树后重 ship。" }
      & git diff --cached --quiet
      if ($LASTEXITCODE -ne 0) {
        & git commit -m "feat($TaskId): 实现至 DoD 全绿`n`n参见 specs/tasks/$TaskId.md"
        if ($LASTEXITCODE -ne 0) { throw "git commit 失败（exit $LASTEXITCODE）——已中止。检查 git 状态/身份配置后重 ship。" }
        $sagaHeadMoved = $true   # 真提交才算 HEAD 前移（恢复路由据此判断 RED 证据是否仍新鲜，R3 r5 #9）
        # T35-RECEIPT（水位线收据 · TD89 根治机制核心）：真提交成功后铸/重铸收据——仅当 $redShaForMint 为 40-hex（RED 闸校验放行时置为在手证据 sha；
        # -SkipRed/占位值则留空）时铸；no-op 提交不到此分支（不铸不改）。best-effort：失败仅 Write-Warning 不 throw（收据失败 throw 反扩大 watershed
        # 后残窗，落收据缺失兜底即可）。路径一律 git -C $Wt rev-parse --git-common-dir（linked worktree 返绝对路径；禁 $RepoRoot 形态走错平面）。
        if ($redShaForMint -cmatch '^[0-9a-f]{40}$') {
          try {
            $rcGcdM = "$(& git -C $Wt rev-parse --git-common-dir 2>$null)".Trim()
            if ($rcGcdM -and -not [System.IO.Path]::IsPathRooted($rcGcdM)) { $rcGcdM = Join-Path $Wt $rcGcdM }
            if (-not $rcGcdM -or -not (Test-Path $rcGcdM -PathType Container)) { throw "git-common-dir 不可解析/非目录：'$rcGcdM'" }
            $rcDirM = Join-Path $rcGcdM 'scaffold-shipped'
            New-Item -ItemType Directory -Force $rcDirM -ErrorAction Stop | Out-Null
            $rcHeadM = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
            if ($rcHeadM -cnotmatch '^[0-9a-f]{40}$') { throw "提交后 git rev-parse HEAD 未返回合法 40-hex commitSha（'$rcHeadM'）——不写无效收据（R3 r11 #9：原生命令非零不抛，须显式校验）" }
            @{ taskId = $TaskId; redSha = $redShaForMint; commitSha = $rcHeadM } | ConvertTo-Json -Compress | Set-Content (Join-Path $rcDirM $TaskId) -Encoding utf8 -ErrorAction Stop
            Write-Host "T35-RECEIPT：已铸水位线收据（redSha=$redShaForMint commitSha=$rcHeadM）——提交后重跑同一条 -Phase ship 即经收据 resume 放行 RED 闸、全部确定性闸重过。" -ForegroundColor DarkGray
          } catch { Write-Warning "T35-RECEIPT：水位线收据铸造失败（best-effort，落收据缺失兜底、不 throw）：$($_.Exception.Message)" }
        }
      } else { Write-Host '无新增改动可提交。' }
      $sagaDone += '提交'

      Step '范围闸（allow_paths 确定性越界拦截；评审只判质量，不兜底范围）'
      # 判定核（allow_paths 取值 / 改动清单求值 / 段级匹配器）见 scripts/_scope.ps1——独立检查器
      # scripts/check-scope.ps1 打的是同一枚核（TD93 item①，防第二实现漂移）。本处只留 ship 侧策略：
      # 远端定向 fetch + F5 fail-closed + 效果账本 + saga 文案。
      # 输入不可判（diff 求值失败 / allow_paths 提取为空）即 fail-closed 拦下——不确定 ≠ 放行。
      $fmAllow = @(Get-ScaffoldCardAllowPath -CardPath $Card)
      # TD68：范围闸对照的基线须是**本次 ship 的合并目标**——远端 ship=origin/<base>，-Local=本地 <base>
      # （line ~382 并入本地当前分支；前次 -Local 合并会让本地合法领先 origin，此时强用 origin 会把前次文件当越界误拦，
      # R3 PR#102 三轮指出）。-Local 走本地解析；远端则先刷新、再只接受远端跟踪引用。与 review.ps1 共用解析器防漂移。
      # F5（TD84）：远端 ship 必须在范围/评审闸前刷新并使用 GitHub 合并目标的远端跟踪引用。
      # 仅「分支名相同」不能证明提交相同；缺失或陈旧的 origin/<base> 若回退本地，会重入 TD68 的错基线 fail-open。
      if (-not $Local) {
        $remoteBaseName = $shipBase
        & git -C $Wt fetch --quiet --no-tags origin "+refs/heads/${remoteBaseName}:refs/remotes/origin/${remoteBaseName}" 2>$null
        if ($LASTEXITCODE -ne 0) {
          Add-CatchRecord 'scope' "无法刷新远端基线 refs/remotes/origin/$remoteBaseName"
          throw "范围闸 fail-closed：无法在闸门前刷新远端基线 refs/remotes/origin/$remoteBaseName。检查网络、origin 与分支名后重 ship；禁止回退到可能陈旧的本地基线（TD68/TD84）。"
        }
        $scopeBaseRef = Resolve-ScaffoldBaseRef -GitDir $Wt -BaseName "origin/$remoteBaseName"
        if ($scopeBaseRef -cne "refs/remotes/origin/$remoteBaseName") {
          Add-CatchRecord 'scope' "刷新后远端基线仍无法解析：refs/remotes/origin/$remoteBaseName"
          throw "范围闸 fail-closed：刷新后仍无法解析远端基线 refs/remotes/origin/$remoteBaseName；禁止回退本地引用。"
        }
        $scopeBaseOid = "$(& git -C $Wt rev-parse $scopeBaseRef 2>$null)".Trim()
        if ($scopeBaseOid -cnotmatch '^[0-9a-f]{40}$') {
          Add-CatchRecord 'scope' "刷新后远端基线 OID 不可判：$scopeBaseRef -> '$scopeBaseOid'"
          throw "范围闸 fail-closed：无法把已刷新远端基线 $scopeBaseRef 解析为 40-hex OID；拒绝在不可绑定的基线上继续。"
        }
      } else {
        $scopeBaseRef = Resolve-ScaffoldBaseRef -GitDir $Wt -BaseName $Base -PreferLocal:$Local
        if (-not $scopeBaseRef) {
          Add-CatchRecord 'scope' "本地基线引用无法解析（refs/heads/$Base 与 refs/remotes/origin/$Base 均不存在）"
          throw "范围闸无法解析本地基线引用：refs/heads/$Base 与 refs/remotes/origin/$Base 均不存在，无法求改动清单。显式传 -Base <分支> 或修复基线后重 ship。"
        }
      }
      try { $changed = @(Get-ScaffoldChangedPath -GitDir $Wt -BaseRef $scopeBaseRef) }
      catch {
        Add-CatchRecord 'scope' "git diff $scopeBaseRef...HEAD 求值失败"
        throw "范围闸无法求值改动清单（git diff --name-only $scopeBaseRef...HEAD 非零退出）。确认基线 '$scopeBaseRef' 在本仓可解析后重 ship。"
      }
      if ($fmAllow.Count -eq 0) {
        Add-CatchRecord 'scope' 'allow_paths 提取为空'
        throw "范围闸无法确定卡范围：$Card front-matter 未提取到 allow_paths 列表项。补齐卡片 allow_paths 后重 ship（check-cards 亦强制该字段）。"
      }
      $oos = @(Get-ScaffoldOutOfScopePath -ChangedPath $changed -AllowPath $fmAllow)
      if ($oos.Count -gt 0) {
        Add-CatchRecord 'scope' ($oos -join ', ')
        # T36-DOCTRINE: watershed 后禁历史改写——卡外改动走反向提交/base 前移吸收，不 rebase（权威长文见 docs/DEVOPS-WORKFLOW.md TD85-RESUME 段）
        throw "越界改动（不在卡 allow_paths 内）：$($oos -join ', ')`n处置（L18）：卡外必要改动应在本分支用反向提交撤出，或让 base 前移吸收后重跑 ship（watershed 后禁 rebase/改写历史）；确属本卡则先在 main 扩卡 allow_paths。"
      }
      Write-Host "范围闸 PASS（$($changed.Count) 个改动文件均在卡 allow_paths 内）" -ForegroundColor DarkGray
      $sagaDone += '范围闸'

      Step '商用许可闸门（check-licenses；命中禁列即 block）'
      # 跑**工作树自带**的 check-licenses（其 $RepoRoot=本工作树 → 扫到本卡 uv add/npm install 新增的依赖），
      # 而非主仓副本（TD80/TD-262：卡的依赖清单/venv 只存在于工作树，扫主仓会漏判违禁许可依赖）。该闸逻辑
      # 由本卡分支自带是 verify/check-secrets 已接受的既有属性，由 allow_paths 范围闸 + R3 评审 + 基线检出的
      # CI 副本共同兜底。
      & pwsh -NoProfile -File (Join-Path $Wt 'scripts/check-licenses.ps1')
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'license' 'check-licenses block'; throw '依赖许可不合规（见 docs/LICENSE-POLICY.md）。修复后重 ship。' }
      $sagaDone += '许可闸'

      Step '防泄露闸（check-secrets；提交后、推送/合并前拦截硬编码密钥与被追踪机密）'
      # 跑**工作树自带**的 check-secrets（其 $RepoRoot=本工作树 → 扫到本卡刚提交的改动），而非主仓副本。
      & pwsh -NoProfile -File (Join-Path $Wt 'scripts/check-secrets.ps1')
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'secrets' 'check-secrets fatal'; throw '检出疑似机密（见上 check-secrets）。立即轮换密钥、改用环境变量/密钥管理并移除后重 ship（见 docs/SECURITY.md）。' }
      $sagaDone += '防泄露闸'

      Step '真实 diff 预算闸（1000 changed lines 且 60000 chars 内；push/PR/R3 前硬阻断）'
      $sizeArgs = if ($Local) {
        @('-WorktreePath', $Wt, '-Base', $Base, '-SizeOnly', '-LocalBase')
      } else {
        @('-WorktreePath', $Wt, '-Base', $shipBase, '-SizeOnly')
      }
      $sizeOutput = (& pwsh -NoProfile -File (Join-Path $RepoRoot 'scripts/review.ps1') @sizeArgs 2>&1 | Out-String)
      $sizeExit = $LASTEXITCODE
      Write-Host $sizeOutput
      if ($sizeExit -ne 0) {
        Add-CatchRecord 'review-size' 'R3 diff budget block'
        throw '真实 diff 超过单卡/R3 完整读取预算，或预算无法可靠计算。拆卡或修复 git 基线后重 ship；本次调用未新增 push/PR、未消费 reviewer round，重试前请检查现有远端分支与 PR。'
      }
      $sagaDone += '真实 diff 预算'

      if ($Local) {
        # ── -Local：无 push/PR/gh 的本地完成路径（治「T0 throwaway 无远端/无 Codex 也能闭环」）──
        Step 'R3 第二模型评审（-Local：可选——有 codex/ReviewCommand 才跑，无则跳过、仅本地检视）'
        # $reviewAvail 已在 ship 入口求值（同一判定，远端路径拿它 fail-fast，此处复用；TD22-C23）
        if ($reviewAvail) {
          # -LocalBase：-Local 的合并目标是本地 <base>，评审基线也须对照本地（否则前次本地合并的文件被误判，TD68）。
          & pwsh -NoProfile -File (Join-Path $Wt 'scripts/review.ps1') -WorktreePath $Wt -Base $Base -LocalBase
          if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'review' 'R3 block (-Local)'; throw '第二模型评审 block（-Local），已停止。修复后重 ship -Local。' }
        } else {
          Write-Warning '无 codex / ReviewCommand：-Local 跳过第二模型评审（仅本地检视，未做对抗评审）。装 codex 或在 _config 配 ReviewCommand 可启用。'
        }
        $sagaDone += 'R3 评审'   # -Local 的 R3 是可选腿：pass 或显式跳过均算该腿完成
        Step '本地合并（-Local：并入当前基线分支，无 push/PR/gh）'
        # F3（R3 PR#102 九轮 + 审计）：入口守卫读的是 ship 开始时的 HEAD；DoD/verify/R3 可跑 10+ 分钟，其间主检出可能被
        # 切分支 / detach（L88 记有 mid-flight HEAD 移动）。合并前**重新断言**同一不变量（throw 不 warn）——否则会对照 $Base
        # 评审、却并入变化后的分支或游离 HEAD（同 F1 的数据丢失尾：cleanup 会 branch -D 丢弃未真正合并的 work）。
        $curBranch = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
        if ($curBranch) { $curBranch = $curBranch.Trim() }
        Assert-LocalMergeTarget -Cur $curBranch -Base $Base -TaskId $TaskId
        & git -C $RepoRoot merge --no-ff --no-edit $TaskId
        if ($LASTEXITCODE -ne 0) { throw "本地合并 $TaskId 失败（冲突？）。在主工作树解决冲突后重试。" }
        $sagaLocalMerged = $true   # 合并已成功——此后失败（凭据铸造）不得误报为合并前守卫态（R3 r5 #9）
        # T24-MERGETOKEN 铸造（-Local 合并成功事件）：cleanup 的 branch -D 只认这枚单次凭据（或 -Force / gh 在线补验）。
        # 铸造 fail-closed：失败即 throw（合并本身已成功，报错只指示凭据未铸——cleanup 会 fail-safe 保留分支）。
        $tokDir = "$(& git -C $RepoRoot rev-parse --git-common-dir 2>$null)".Trim()
        if (-not $tokDir) { throw "T24-MERGETOKEN：本地合并已成功但无法解析 git-common-dir，合并凭据未铸造——cleanup 将保留分支（或用 -Force）。排查 git 环境。" }
        if (-not [System.IO.Path]::IsPathRooted($tokDir)) { $tokDir = Join-Path $RepoRoot $tokDir }
        $tokDir = Join-Path $tokDir 'scaffold-merged'
        New-Item -ItemType Directory -Force $tokDir -ErrorAction Stop | Out-Null
        # R3 r3 #17：tip 取合并提交第二亲（HEAD^2 = 恰被并入的分支 tip）——原子锚定「已合并的那个状态」，免「合并后
        # 并发 ref 前移再 rev-parse 分支名」把未合并的新状态铸进凭据。--no-ff 保证合并提交恒有第二亲。
        $mintTip = "$(& git -C $RepoRoot rev-parse HEAD^2 2>$null)".Trim()
        if (-not $mintTip) { throw "T24-MERGETOKEN：本地合并已成功但无法解析合并提交第二亲（HEAD^2），合并凭据未铸造——cleanup 将保留分支（或用 -Force）。" }
        "tip=$mintTip`nmerged=$("$(& git -C $RepoRoot rev-parse HEAD 2>$null)".Trim())`nutc=$((Get-Date).ToUniversalTime().ToString('o'))" | Set-Content (Join-Path $tokDir $TaskId) -Encoding utf8
        $sagaDone += '本地合并'
        Write-Host "已本地合并 $TaskId（无远端 / 无 PR；已铸 T24-MERGETOKEN 合并凭据）。下一步：scripts\task.ps1 -TaskId $TaskId -Phase cleanup 拆 worktree。" -ForegroundColor Green
        return
      }

      Step 'push + 开 PR（Codex 评审在 PR 开好后单次运行，兼作回贴状态）'
      # push 之前是最后一个还能无代价停下的点：一旦推上去，远端就有了一个可能从未过预算闸的提交。
      # 按提交 OID 发布（而非分支名）属 T0-R3-MEASURED-OID-BINDING，本卡不做。
      & git push -u origin $TaskId
      # TD44（载重护栏）：push 静默失败（网络/凭证/非 fast-forward 拒绝）时若续跑，下游 `gh pr merge` 会合并 origin/$TaskId
      # 当前指向的【陈旧】head——与本地刚过闸的产物解耦、把未评审内容并入基线，且 R3 把绿状态回贴到 head 已陈旧的 PR（状态误导）。
      # 故 push 后立即校验退出码：非零即在开 PR / 合并之前 throw，恢复「过闸的产物 === 被合并的产物」不变量。
      if ($LASTEXITCODE -ne 0) { throw "git push 失败（exit $LASTEXITCODE）——远端未更新，已中止以防合并陈旧远端 head（TD44）。排查网络/凭证；若为非 fast-forward 拒绝，先 git fetch origin，再在 worktree 内 git merge origin/$TaskId（merge 从不 rebase；亦可 git pull --no-rebase）后重 ship；watershed 后严禁 rebase/改写历史。" }
      $cardTitle = Get-CardField 'title'; if (-not $cardTitle) { $cardTitle = $TaskId }
      $title = "${TaskId}: $cardTitle"
      $exists = (& gh pr view $TaskId --json number -q .number 2>$null)
      if (-not $exists) {
        & gh pr create --base $shipBase --head $TaskId --title $title `
            --body "闭环：worktree+TDD+Codex 评审。DoD 见 specs/tasks/$TaskId.md。Codex 裁决已回贴。" 2>&1 | Write-Host
      }
      $prRaw = (& gh pr view $TaskId --json number -q .number 2>$null)
      $pr = 0
      if (-not [int]::TryParse("$prRaw".Trim(), [ref]$pr) -or $pr -le 0) {
        throw "未能获取 PR 号（gh pr view 返回 '$prRaw'）。检查 push/gh 登录后重 ship。"
      }
      # Codex#1（TD84）：复用/新建的 PR 其**实际 base 分支**须 == 本次 $shipBase——否则 review/范围对照它、而 `gh pr merge`
      # 却并入 PR 真实 base（已存在或被 retarget 的 PR 即此情形），评审从未审对那个基线的 diff（错合并目标 fail-open）。
      Assert-RemotePrBase -Pr $pr -ExpectedBase $shipBase
      $sagaDone += 'push+PR'

      Step 'R3 Codex 评审闸门（单次运行：评审 + 回贴 codex-review 状态；block 即停、不合并）'
      $r3Head = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
      if ($r3Head -cnotmatch '^[0-9a-f]{40}$') {
        Add-CatchRecord 'review' "R3 前本地 HEAD 不可判：'$r3Head'"
        throw '[CI-GATE-LOCAL-HEAD] 候选 HEAD 无效。'
      }
      & pwsh -NoProfile -File (Join-Path $RepoRoot 'scripts/review.ps1') -WorktreePath $Wt -Base $shipBase -PostStatus -PrNumber $pr
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'review' 'R3 block'; throw 'Codex 裁决 block，已停止。修复后重 ship（PR 已开，重 ship 会更新）。' }
      $r3HeadAfter = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
      if (($r3HeadAfter -cnotmatch '^[0-9a-f]{40}$') -or ($r3HeadAfter -cne $r3Head)) {
        Add-CatchRecord 'review' "R3 期间本地 HEAD 变化（$r3Head -> '$r3HeadAfter'）"
        throw "[CI-GATE-LOCAL-HEAD-MOVED] $r3Head -> '$r3HeadAfter'"
      }
      $sagaDone += 'R3 评审'
      Step 'CI gate（候选树 ci.yml 的全部 job 须在同一 PR head 上 completed+success）'
      $wf = Join-Path $Wt '.github/workflows/ci.yml'
      if (-not (Test-Path -LiteralPath $wf -PathType Leaf)) { Add-CatchRecord 'ci' 'ci.yml missing'; throw '[CI-GATE-WF-MISSING] ci.yml。' }
      $decl = @(); $err = @(); $inJobs = $false
      foreach ($ln in @(Get-Content -LiteralPath $wf)) {
        if (-not $inJobs) {
          if ($ln -cmatch '^jobs:\s*(?:#.*)?$') { $inJobs = $true }
          continue
        }
        if ($ln -cmatch '^\S') { break }
        if ($ln -cmatch '^  \S') {
          if ($ln -cmatch '^  (?<job>[A-Za-z0-9_-]+):\s*(?:#.*)?$') { $decl += $Matches['job'] }
          elseif ($ln -cnotmatch '^  #') { $err += $ln.Trim() }
        }
        elseif ($ln -cmatch '^    name:\s*(?<job>[\w ./()_-]+?)\s*(?:#.*)?$') {
          if ($decl.Count -eq 0) { $err += $ln.Trim() } else { $decl[-1] = $Matches['job'] }
        }
        elseif ($ln -cmatch '^    (?:name|strategy|uses):|^      matrix:') { $err += $ln.Trim() }
      }
      $want = @($decl | Sort-Object -Unique)
      if ((-not $inJobs) -or ($decl.Count -eq 0) -or ($want.Count -ne $decl.Count) -or ($err.Count -gt 0)) { Add-CatchRecord 'ci' "$($decl.Count)/$($want.Count):$err"; throw '[CI-GATE-JOBS-DRIFT] jobs。' }
      $toSec = 1800
      if ($env:SCAFFOLD_CI_TIMEOUT_SEC) {
        $to = 0
        if ((-not [int]::TryParse($env:SCAFFOLD_CI_TIMEOUT_SEC, [ref]$to)) -or ($to -le 0)) { throw "[CI-GATE-TIMEOUT-CONFIG] '$($env:SCAFFOLD_CI_TIMEOUT_SEC)'。" }
        $toSec = $to
      }
      $ddl = [DateTimeOffset]::UtcNow.AddSeconds($toSec)
      $hr = Invoke-GhBeforeDeadline -Arguments @('pr', 'view', "$pr", '--json', 'headRefOid', '-q', '.headRefOid') -Deadline $ddl -WorkingDirectory $Wt
      $head = "$($hr.Stdout)".Trim()
      if ($hr.TimedOut) { throw "[CI-GATE-TIMEOUT] PR #$pr head。" }
      if (($hr.ExitCode -ne 0) -or ($head -cnotmatch '^[0-9a-f]{40}$')) { Add-CatchRecord 'ci' "head=$($hr.ExitCode):'$head'"; throw "[CI-GATE-NOHEAD] #$pr。" }
      if ($head -cne $r3Head) { Add-CatchRecord 'ci' "$r3Head!=$head"; throw "[CI-GATE-HEAD-MISMATCH] $head!=$r3Head" }
      $last = '尚未读取 check-runs'
      :ciStable while ($true) {
      while ($true) {
        if ([DateTimeOffset]::UtcNow -ge $ddl) { Add-CatchRecord 'ci' "timeout ${toSec}s: $last"; throw "[CI-GATE-TIMEOUT] $last" }
        $checks = Get-ExactHeadChecksBeforeDeadline -Head $head -Deadline $ddl -WorkingDirectory $Wt
        if ($checks.TimedOut) { Add-CatchRecord 'ci' "timeout ${toSec}s: $($checks.Reason)"; throw "[CI-GATE-TIMEOUT] $($checks.Reason)" }
        if (-not $checks.Readable) { Add-CatchRecord 'ci' "checks:$($checks.Reason)"; throw "[CI-GATE-API] checks:$($checks.Reason)" }
        if ($checks.Blocking.Count -gt 0) {
          $err = @($checks.Blocking | ForEach-Object { "$($_.name)=$($_.status)/$($_.conclusion)" }) -join ', '
          Add-CatchRecord 'ci' "red:$err"
          throw "[CI-GATE-RED] checks: $err"
        }
        $wfPg = Get-GhPagedCollectionBeforeDeadline `
          -EndpointTemplate "repos/{owner}/{repo}/actions/workflows/ci.yml/runs?event=pull_request&head_sha=$head&per_page=100&page={page}" `
          -CollectionProperty 'workflow_runs' -Deadline $ddl -WorkingDirectory $Wt
        if ($wfPg.TimedOut) {
          Add-CatchRecord 'ci' "timeout: $($wfPg.Reason)"
          throw "[CI-GATE-TIMEOUT] workflow: $($wfPg.Reason)"
        }
        if (-not $wfPg.Readable) {
          Add-CatchRecord 'ci' "workflow API: $($wfPg.Reason)"
          throw "[CI-GATE-API] workflow: $($wfPg.Reason)"
        }
        $wfRuns = @($wfPg.Items)
        if ($wfRuns.Count -eq 0) {
          $last = '候选 ci.yml 尚无该 head 的 pull_request workflow run'
          Wait-CiRetryBeforeDeadline $ddl
          continue
        }
        if ($wfRuns.Count -ne 1) {
          Add-CatchRecord 'ci' "runs=$($wfRuns.Count)/$head"
          throw "[CI-GATE-WORKFLOW-AMBIGUOUS] runs=$($wfRuns.Count)。"
        }
        $run = $wfRuns[0]
        $prop = @($run.PSObject.Properties.Name)
        $miss = @(@('id', 'head_sha', 'event', 'status', 'conclusion', 'run_attempt', 'path', 'pull_requests') | Where-Object { $prop -cnotcontains $_ })
        if ($miss.Count -gt 0) {
          Add-CatchRecord 'ci' "missing:$($miss -join ',')"
          throw "[CI-GATE-WORKFLOW-IDENTITY] 缺属性：$($miss -join ', ')"
        }
        $runId = 0L; $attempt = 0; $path = "$($run.path)"
        $prs = $run.pull_requests; $pm = @()
        if ($prs -is [System.Array]) {
          $pm = @($prs | Where-Object { "$($_.number)" -ceq "$pr" })
        }
        if ((-not [long]::TryParse("$($run.id)", [ref]$runId)) -or ($runId -le 0) -or
            (-not [int]::TryParse("$($run.run_attempt)", [ref]$attempt)) -or ($attempt -le 0) -or
            ("$($run.head_sha)" -cne $head) -or ("$($run.event)" -cne 'pull_request') -or
            ($path -cnotmatch '^\.github/workflows/ci\.yml(?:@.*)?$') -or ($pm.Count -ne 1)) {
          Add-CatchRecord 'ci' "wf=$($run.id)/$($run.run_attempt)/$($run.head_sha)/$($run.event)/$path/prs=$($pm.Count)"
          throw "[CI-GATE-WORKFLOW-IDENTITY] PR #$pr workflow 身份不唯一/不匹配。"
        }
        $ws = "$($run.status)"; $wc = "$($run.conclusion)"
        if (($ws -ieq 'completed') -and ($wc -ine 'success')) {
          Add-CatchRecord 'ci' "wf:$runId=$ws/$wc"
          throw "[CI-GATE-RED] workflow $runId=$ws/$wc。"
        }
        if (($ws -ine 'completed') -or ($wc -ine 'success')) {
          $last = "workflow $runId=$ws/$wc"
          Wait-CiRetryBeforeDeadline $ddl
          continue
        }
        $jobPg = Get-GhPagedCollectionBeforeDeadline `
          -EndpointTemplate "repos/{owner}/{repo}/actions/runs/$runId/attempts/$attempt/jobs?per_page=100&page={page}" `
          -CollectionProperty 'jobs' -Deadline $ddl -WorkingDirectory $Wt
        if ($jobPg.TimedOut) {
          Add-CatchRecord 'ci' "timeout: $($jobPg.Reason)"
          throw "[CI-GATE-TIMEOUT] jobs: $($jobPg.Reason)"
        }
        if (-not $jobPg.Readable) {
          Add-CatchRecord 'ci' "jobs API: $($jobPg.Reason)"
          throw "[CI-GATE-API] jobs: $($jobPg.Reason)"
        }
        $jobs = @($jobPg.Items)
        $wait = @()
        foreach ($j in $want) {
          $jm = @($jobs | Where-Object { "$($_.name)" -ceq $j })
          if ($jm.Count -ne 1) { $wait += "$j=match($($jm.Count))"; continue }
          $job = $jm[0]
          $st = "$($job.status)"; $co = "$($job.conclusion)"
          if (($st -ieq 'completed') -and ($co -ine 'success')) {
            Add-CatchRecord 'ci' "$j=$st/$co"
            throw "[CI-GATE-RED] job '$j'=$st/$co。"
          }
          if (($st -ine 'completed') -or ($co -ine 'success')) { $wait += "$j=$st/$co" }
        }
        if (($jobs.Count -ne $want.Count) -and ($wait.Count -eq 0)) { $wait += "jobs=$($jobs.Count)/$($want.Count)" }
        if ($wait.Count -eq 0) { break }
        $last = $wait -join ', '
        Wait-CiRetryBeforeDeadline $ddl
      }
      & git -C $Wt fetch --quiet --no-tags origin "+refs/heads/${remoteBaseName}:refs/remotes/origin/${remoteBaseName}" 2>$null
      if ($LASTEXITCODE -ne 0) {
        Add-CatchRecord 'ci' "base refresh:origin/$remoteBaseName"
        throw "[CI-GATE-BASE-REFRESH] origin/$remoteBaseName。"
      }
      $baseNow = "$(& git -C $Wt rev-parse "refs/remotes/origin/$remoteBaseName" 2>$null)".Trim()
      if (($baseNow -cnotmatch '^[0-9a-f]{40}$') -or ($baseNow -cne $scopeBaseOid)) {
        Add-CatchRecord 'ci' "base:$scopeBaseOid->$baseNow"
        throw "[CI-GATE-BASE-MOVED] $scopeBaseOid -> '$baseNow'。"
      }
      $final = Get-ExactHeadChecksBeforeDeadline -Head $head -Deadline $ddl -WorkingDirectory $Wt
      if ($final.TimedOut) {
        Add-CatchRecord 'ci' "final timeout:$($final.Reason)"
        throw "[CI-GATE-TIMEOUT] final checks: $($final.Reason)"
      }
      if (-not $final.Readable) {
        Add-CatchRecord 'ci' "final API:$($final.Reason)"
        throw "[CI-GATE-API] final checks: $($final.Reason)"
      }
      if ($final.Blocking.Count -gt 0) {
        $bad = @($final.Blocking | ForEach-Object { "$($_.name)=$($_.status)/$($_.conclusion)" }) -join ', '
        Add-CatchRecord 'ci' "final red:$bad"
        throw "[CI-GATE-RED] final checks: $bad"
      }
      $fwPg = Get-GhPagedCollectionBeforeDeadline `
        -EndpointTemplate "repos/{owner}/{repo}/actions/workflows/ci.yml/runs?event=pull_request&head_sha=$head&per_page=100&page={page}" `
        -CollectionProperty 'workflow_runs' -Deadline $ddl -WorkingDirectory $Wt
      if ($fwPg.TimedOut) { throw "[CI-GATE-TIMEOUT] final workflow: $($fwPg.Reason)" }
      if (-not $fwPg.Readable) { throw "[CI-GATE-API] final workflow: $($fwPg.Reason)" }
      $fwRuns = @($fwPg.Items)
      if ($fwRuns.Count -ne 1) { throw "[CI-GATE-WORKFLOW-AMBIGUOUS] final runs=$($fwRuns.Count)。" }
      $fwRun = $fwRuns[0]; $fwId = 0L; $fwTry = 0
      $fwProp = @($fwRun.PSObject.Properties.Name)
      $fwPrs = $fwRun.pull_requests; $fwPm = @()
      if ($fwPrs -is [System.Array]) { $fwPm = @($fwPrs | Where-Object { "$($_.number)" -ceq "$pr" }) }
      if (@(@('id', 'head_sha', 'event', 'status', 'conclusion', 'run_attempt', 'path', 'pull_requests') | Where-Object { $fwProp -cnotcontains $_ }).Count -gt 0 -or
          (-not [long]::TryParse("$($fwRun.id)", [ref]$fwId)) -or ($fwId -ne $runId) -or
          (-not [int]::TryParse("$($fwRun.run_attempt)", [ref]$fwTry)) -or ($fwTry -ne $attempt) -or
          ("$($fwRun.head_sha)" -cne $head) -or ("$($fwRun.event)" -cne 'pull_request') -or
          ("$($fwRun.path)" -cnotmatch '^\.github/workflows/ci\.yml(?:@.*)?$') -or ($fwPm.Count -ne 1)) {
        throw "[CI-GATE-WORKFLOW-IDENTITY] 决策前 workflow 身份漂移（id=$($fwRun.id), expected=$runId）。"
      }
      $fwSt = "$($fwRun.status)"; $fwCo = "$($fwRun.conclusion)"
      if (($fwSt -ieq 'completed') -and ($fwCo -ine 'success')) { throw "[CI-GATE-RED] wf:$runId=$fwSt/$fwCo" }
      if (($fwSt -ine 'completed') -or ($fwCo -ine 'success')) {
        $last = "final workflow $runId=$fwSt/$fwCo"; Wait-CiRetryBeforeDeadline $ddl; continue ciStable
      }
      $fjPg = Get-GhPagedCollectionBeforeDeadline `
        -EndpointTemplate "repos/{owner}/{repo}/actions/runs/$runId/attempts/$attempt/jobs?per_page=100&page={page}" `
        -CollectionProperty 'jobs' -Deadline $ddl -WorkingDirectory $Wt
      if ($fjPg.TimedOut) { throw "[CI-GATE-TIMEOUT] final jobs: $($fjPg.Reason)" }
      if (-not $fjPg.Readable) { throw "[CI-GATE-API] final jobs: $($fjPg.Reason)" }
      $fJobs = @($fjPg.Items); $fjWait = @()
      foreach ($j in $want) {
        $fm = @($fJobs | Where-Object { "$($_.name)" -ceq $j })
        if ($fm.Count -ne 1) { $fjWait += "$j=match($($fm.Count))"; continue }
        $fs = "$($fm[0].status)"; $fc = "$($fm[0].conclusion)"
        if (($fs -ieq 'completed') -and ($fc -ine 'success')) { throw "[CI-GATE-RED] $j=$fs/$fc" }
        if (($fs -ine 'completed') -or ($fc -ine 'success')) { $fjWait += "$j=$fs/$fc" }
      }
      if (($fJobs.Count -ne $want.Count) -and ($fjWait.Count -eq 0)) { $fjWait += "jobs=$($fJobs.Count)/$($want.Count)" }
      if ($fjWait.Count -gt 0) { $last = "final jobs: $($fjWait -join ', ')"; Wait-CiRetryBeforeDeadline $ddl; continue ciStable }
      $prRead = Invoke-GhBeforeDeadline -Arguments @('pr', 'view', "$pr", '--json', 'baseRefName,headRefOid') -Deadline $ddl -WorkingDirectory $Wt
      if ($prRead.TimedOut) { throw "[CI-GATE-TIMEOUT] PR #$pr 最终 base/head 快照超过 ${toSec}s。" }
      $prRaw2 = "$($prRead.Stdout)"; $prExit = $prRead.ExitCode
      $base2 = ''; $head2 = ''
      if (($prExit -eq 0) -and $prRaw2) {
        try {
          $pr2 = $prRaw2 | ConvertFrom-Json -ErrorAction Stop
          $base2 = "$($pr2.baseRefName)".Trim()
          $head2 = "$($pr2.headRefOid)".Trim()
        } catch { $base2 = ''; $head2 = '' }
      }
      if ($base2 -cne $shipBase) {
        Add-CatchRecord 'base' "$shipBase!=$base2/$prExit"
        throw "[CI-GATE-BASE-MISMATCH] '$base2' != '$shipBase' (exit $prExit)。"
      }
      if (($head2 -cnotmatch '^[0-9a-f]{40}$') -or ($head2 -cne $head)) {
        Add-CatchRecord 'ci' "head:$head->$head2/$prExit"
        throw "[CI-GATE-HEAD-MOVED] $head -> '$head2' (exit $prExit)。"
      }
      break ciStable
      }
      Write-Host "[CI-GATE-PASS] #$pr/$head [$($want -join ',')]" -ForegroundColor Green
      $sagaDone += 'CI gate'
      if (-not $NoAutoMerge) {
        # free + private：服务端无规则集/必需检查，auto-merge 亦未启用。
        Step '本地闸门+候选 CI 已过 → 绑定已证明 head 直接 squash 合并（free private：无服务端必需检查）'
        # 不加 --delete-branch：在 worktree 内它会尝试 checkout base(main) 以删本地分支，
        # 而 main 被主工作树占用 → fatal "'main' is already used by worktree"（合并其实已成功）。
        # 远端分支由仓库 delete_branch_on_merge=true 自动删；本地分支由 cleanup 阶段删。
        & gh pr merge $pr --squash --match-head-commit $head
        if ($LASTEXITCODE -ne 0) { throw "PR #$pr squash 合并失败（exit $LASTEXITCODE）。检查 gh 权限/合并冲突后重试。" }
        # T24-MERGETOKEN 铸造（PR squash 合并成功事件）：内容记 PR 号 + 分支 tip（仅溯源），在位即凭据。
        $tokDir = "$(& git -C $RepoRoot rev-parse --git-common-dir 2>$null)".Trim()
        if (-not $tokDir) { throw "T24-MERGETOKEN：PR #$pr 已合并但无法解析 git-common-dir，合并凭据未铸造——cleanup 将走 gh 在线补验（或 -Force）。排查 git 环境。" }
        if (-not [System.IO.Path]::IsPathRooted($tokDir)) { $tokDir = Join-Path $RepoRoot $tokDir }
        $tokDir = Join-Path $tokDir 'scaffold-merged'
        New-Item -ItemType Directory -Force $tokDir -ErrorAction Stop | Out-Null
        # R3 r3 #17：tip 取已合并 PR 的 headRefOid（权威=恰被 squash 进 base 的 head）——免并发本地 ref 前移铸错凭据。
        # R3 r5 #17：`gh pr merge` exit 0 ≠ 已合并（auto-merge 生效/merge queue 场景只是入队）——铸造前查 state，
        # 仅 MERGED 才铸；否则不留凭据、fail-closed（PR 真合并后 cleanup 走 gh 在线补验，或确认后 -Force）。
        # 注意 --json 字段表必须引号包裹：裸 state,headRefOid 会被 PowerShell 当数组拆成两个参数，真 gh 直接报错
        $mintJson = "$(& gh pr view $pr --json 'state,headRefOid' 2>$null)"
        $mintState = ''; $mintTip = ''
        if ($mintJson) { try { $mintO = $mintJson | ConvertFrom-Json; $mintState = "$($mintO.state)"; $mintTip = "$($mintO.headRefOid)".Trim() } catch { $mintState = ''; $mintTip = '' } }
        if (($mintState -ine 'MERGED') -or (-not $mintTip)) { throw "T24-MERGETOKEN：gh pr merge 已返回但 PR #$pr 状态非 MERGED（state='$mintState'，可能 auto-merge/merge queue 仅入队）或无 headRefOid——不铸造合并凭据（fail-closed）。PR 真正合并后 cleanup 可走 gh 在线补验；或人工确认后 -Phase cleanup -Force。" }
        "tip=$mintTip`nmerged_pr=#$pr`nutc=$((Get-Date).ToUniversalTime().ToString('o'))" | Set-Content (Join-Path $tokDir $TaskId) -Encoding utf8
        $sagaDone += '合并'
        Write-Host "PR #$pr 已 squash 合并（远端分支由仓库设置自动删；已铸 T24-MERGETOKEN 合并凭据）。合并后跑：scripts\task.ps1 -TaskId $TaskId -Phase cleanup，并执行 R5 文档同步。" -ForegroundColor Green
      } else {
        Write-Host "PR #$pr head $head 已通过 R3 + 候选 CI，就绪待人工合并。" -ForegroundColor Green
      }
    } finally { Pop-Location }
    } catch {
      # T26-SHIPSAGA saga 报告：任一腿失败时自述进度，随后**原样裸 throw**——退出码/失败面/上游捕获行为均不变；
      # 报告只陈述状态与命令，不做自动恢复/重试。原始异常文案紧随其后打印，两者互补（报告=形状，异常=根因）。
      $sagaTodo = @($sagaLegs | Where-Object { $sagaDone -notcontains $_ })
      $sagaFirst = if ($sagaTodo.Count) { $sagaTodo[0] } else { '（不可判：全部腿均已标记完成）' }
      # 失败点真相源（R3 r3 #9）：腿间非腿操作（预检/RED 证据闸）失败时 $sagaAt 非空——不得按「首个未完成腿」误报成 DoD。
      $sagaFailPoint = if ($sagaAt) { "$sagaAt；其后首个未完成腿=$sagaFirst" } else { "$sagaFirst（=首个未完成腿）" }
      $sagaMsg = "$($_.Exception.Message)"
      Write-Host "`n―― ship saga 报告 [T26-SHIPSAGA]（可重试 saga：能否安全重跑整条 ship 取决于「提交」腿是否已落，见恢复行）――" -ForegroundColor Yellow
      Write-Host ('  已完成腿：' + $(if ($sagaDone.Count) { $sagaDone -join ' → ' } else { '（无）' })) -ForegroundColor Yellow
      Write-Host "  失败点：$sagaFailPoint" -ForegroundColor Yellow
      # 待办腿（R3 r4 #9）：只有失败点本身是腿（$sagaAt 空）才从待办中摘掉首项；腿间预检/闸失败时首个未完成腿并未失败，
      # 不得被吞出待办清单。
      $sagaPending = @(if ($sagaAt) { $sagaTodo } else { $sagaTodo | Select-Object -Skip 1 })
      Write-Host ('  待办腿：' + $(if ($sagaPending.Count) { $sagaPending -join ' → ' } else { '（无）' })) -ForegroundColor Yellow
      # 恢复路由（R3 r1-r5 #9）：真死锁条件 = 本次 ship 真产生了新 commit（$sagaHeadMoved）**且**未用 -SkipRed——
      # RED 证据新鲜度闸只在此时对整条重跑 fail-closed（TD85）；「提交」腿完成≠HEAD 前移（no-op 提交不动 HEAD）。
      # -Local 合并腿失败按**阶段状态**分流（$sagaLocalMerged / MERGE_HEAD 在盘），不嗅探异常文案（每个合并失败
      # 消息都含「冲突？」字样，文案匹配必误报）。出路只引 TD85-RESUME 锚点原则、不复制其正文（真相源 =
      # docs/DEVOPS-WORKFLOW.md，免双源漂移）。
      # T35-RECEIPT saga 最小路由（双 Test-Path 机器态·**只测在位性、不复算四谓词**——四谓词唯一校验点在 RED 闸）：post-watershed 死锁态改按
      # 「水位线收据在位 ∧ RED 证据在位」分流——在位=重跑同一条 ship 即经收据令 RED 闸 resume 放行（并入下方安全重跑分支，**-Local 与远端同理**：
      # 铸造/RED 闸的收据 resume 均不区分 -Local，远端重跑同样经收据放行整条管线、全部确定性闸重过）；否则走兜底（未推送 reset --soft
      # <evidence.redSha>，**非 HEAD~1**——多提交分支上 HEAD~1 制造二次死锁 / 已推送 -PostStatus 最后手段）。读取一律 best-effort、**绝不在
      # catch 内 throw**（护 15r(e)「原始异常原样在场」）。（远端态 hermetic 夹具矩阵 = T37；本卡实现远端同款路由、-Local 夹具覆盖共享的安全重跑分支。）
      # R3 r13 #9：全局 EAP=Stop 下 Test-Path 遇 provider/权限/路径错会**抛**——在 saga catch 内抛会替换原始异常（毁 15r(e)「原异常在场」）。
      # 故每个探针独立 try/catch 兜住、绝不外抛（读不出即视作缺失/无 sha，落兜底）。
      $sagaRcptIn = $false; $sagaEvdSha = ''; $sagaEvdIn = $false
      try {
        $sagaGcd = "$(& git -C $Wt rev-parse --git-common-dir 2>$null)".Trim()
        if ($sagaGcd -and -not [System.IO.Path]::IsPathRooted($sagaGcd)) { $sagaGcd = Join-Path $Wt $sagaGcd }
        if ($sagaGcd) { $sagaRcptIn = Test-Path (Join-Path (Join-Path $sagaGcd 'scaffold-shipped') $TaskId) }
      } catch { $sagaRcptIn = $false }
      $sagaEvdP = Join-Path $Wt ".review/$TaskId.red"
      try { $sagaEvdIn = Test-Path $sagaEvdP } catch { $sagaEvdIn = $false }
      if ($sagaEvdIn) { try { $sagaEvdSha = "$((Get-Content $sagaEvdP -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop).sha)" } catch { $sagaEvdSha = '' } }
      $sagaRcptSafe = ($sagaRcptIn -and $sagaEvdIn)   # -Local 与远端同款：收据在位即安全重跑（RED 闸经收据 resume 放行整条管线，非 -Local 独有）
      if ($sagaMsg -match 'TD85-RESUME') {
        # 本次失败自身就是死锁重跑（RED 新鲜度闸 throw 在 sha 前移时自带 TD85-RESUME 哨兵）：本轮腿跟踪只走到 RED 闸
        # （首个未完成腿显示为 DoD，但真因不是 DoD——TD85 事件正是这样被误判的），真实进度（PR 开没开/合没合）不在
        # 本进程内存——勿按上方腿清单推断，以实查为准。
        Write-Host "  恢复：本次失败即 TD85 死锁重跑——勿再重跑 -Phase ship；按下方异常文案自带的 TD85-RESUME 指引处置，真实进度以实查为准（远端：gh pr view $TaskId；-Local：主检出 git log）。" -ForegroundColor Yellow
      } elseif ($Local -and (-not $sagaAt) -and $sagaTodo.Count -and ($sagaTodo[0] -eq '本地合并')) {
        # -Local 合并腿失败：按**阶段状态**分流（R3 r5 #9——不嗅探异常文案：每个合并失败消息都含「冲突？」，文案匹配
        # 必把非冲突失败误导去 merge --continue）。三态：凭据未铸（$sagaLocalMerged=合并其实已成功）/ 合并中
        # （MERGE_HEAD 在盘，续跑而非重发）/ 守卫拦下（合并未开始，回基线分支后重发）。置于重跑分支之前：停在合并中时重跑必失败。
        if ($sagaLocalMerged) {
          Write-Host '    本地合并已成功、仅 T24 合并凭据未铸（凭据写盘/解析失败）——cleanup 会 fail-safe 保留分支，人工确认已合并后 -Phase cleanup -Force。' -ForegroundColor Yellow
        } else {
          $sagaMergeHead = "$(& git -C $RepoRoot rev-parse --git-path MERGE_HEAD 2>$null)".Trim()
          if ($sagaMergeHead -and -not [System.IO.Path]::IsPathRooted($sagaMergeHead)) { $sagaMergeHead = Join-Path $RepoRoot $sagaMergeHead }
          if ($sagaMergeHead -and (Test-Path $sagaMergeHead)) {
            Write-Host "    本地合并冲突（MERGE_HEAD 在盘）——主路径三步（消解树重过全闸、非旁路，权威见 docs/DEVOPS-WORKFLOW.md TD85-RESUME S6 段）：① 主检出 git merge --abort ② 进入 worktree $Wt 内 git merge $Base 解决冲突并提交（禁 rebase；合并提交被水位线收据祖先语义接纳——T35 正例夹具已钉）③ 重跑同一条 ship（消解树重过范围闸+R3，管线内合并必 clean）。最后手段（树不过范围闸与 R3）：主检出 git merge --continue 完成合并后**立即重跑 pwsh -File scripts\selftest.ps1（或项目 verify）核验合并树**。" -ForegroundColor Yellow
          } else {
            Write-Host "    本地合并未开始即被守卫拦下（合并目标漂移/detached，L86/F3）：在主检出切回基线分支（git switch $Base）后重新执行 git -C `"$RepoRoot`" merge --no-ff --no-edit $TaskId（cleanup 无 T24 凭据会 fail-safe 保留分支，确认已合并后可 -Force）。" -ForegroundColor Yellow
          }
        }
      } elseif ((-not $sagaHeadMoved) -or $SkipRed -or $sagaRcptSafe) {
        # 整条重跑安全：本次未产生新 commit（RED 证据仍新鲜）/ -SkipRed（不经 RED 证据闸）/ **水位线收据在位**（收据令 RED 闸 resume
        # 放行，T35-RECEIPT——「提交」腿完成后重跑不再死锁）。完整重跑命令带齐所有影响行为的已绑定选项（R3 r2 #9）：丢 -Base 会错基线、
        # 丢 -SkipRed 会立刻卡在 RED 证据闸（非 TDD 卡本无证据）、丢 -NoAutoMerge 会违背调用方意图自动合并。-Base 只在显式传入时回填。
        $sagaCmd = "pwsh -File scripts\task.ps1 -TaskId $TaskId -Phase ship"
        if ($PSBoundParameters.ContainsKey('Base')) { $sagaCmd += " -Base $Base" }
        if ($Local) { $sagaCmd += ' -Local' }
        if ($SkipRed) { $sagaCmd += ' -SkipRed' }
        if ($NoAutoMerge) { $sagaCmd += ' -NoAutoMerge' }
        $sagaSafeWhy = if ($sagaHeadMoved -and (-not $SkipRed)) { '水位线收据在位——收据令 RED 闸 resume 放行、全部确定性闸+R3 重审' } else { '本次未产生新 commit 或 -SkipRed 不经 RED 闸——RED 证据语义无恙' }
        Write-Host "  恢复：$sagaCmd  （重跑即 resume：$sagaSafeWhy，已过闸的腿幂等重过、无死锁无旁路）" -ForegroundColor Yellow
      } else {
        # post-watershed（HEAD 前移、非 -SkipRed）且**水位线收据缺失/证据缺失**（S9/残窗/不自洽）——非安全重跑态。
        Write-Host '  恢复：水位线收据缺失/不自洽（收据或 RED 证据不在位）——「提交」腿已落、HEAD 已前移，勿直接重跑 -Phase ship（会落 RED 兜底路由器）。按当前状态走（原则同 docs/DEVOPS-WORKFLOW.md「ship 非原子→重跑即 resume」的 TD85-RESUME 段）：' -ForegroundColor Yellow
        if ($Local) {
          # -Local 的提交必然未推送（无远端腿）→ 闸门保真归位路径（R3 r6 #9）：reset 靶 = evidence.redSha 原值（best-effort 读，
          # 非固定 HEAD~1——resume 二次失败的多提交分支上 HEAD~1 会制造二次死锁）；证据缺失/占位 → 无靶，引锚点人工核对。
          if ($sagaEvdSha -cmatch '^[0-9a-f]{40}$') {
            Write-Host "    -Local（提交未推送）：在 worktree 内 git reset --soft $sagaEvdSha 撤销本次 ship 的提交（HEAD 归位 RED 证据 sha、改动保留在暂存区；非 HEAD~1），修复后重跑 -Phase ship -Local——全部确定性闸与 R3 评审重过，无死锁、无闸门旁路。" -ForegroundColor Yellow
          } else {
            Write-Host "    -Local（提交未推送）：RED 证据缺失/不可读（S9：worktree 重建或误跑 -Phase start 后），无自动 reset 靶——人工核对工作树后处置（勿重跑 -Phase start；原则见 docs/DEVOPS-WORKFLOW.md TD85-RESUME 段）。" -ForegroundColor Yellow
          }
        } else {
          # PR 真实状态不以腿成员判定推断（R3 r2 #9）：push+PR 是复合腿——pr create 已成功而后续 PR 号解析/base
          # 断言 throw 时，腿未标完成但 PR 已存在。以「PR 号是否已解析到手」为准；解析不到只指示实查，不断言「尚无 PR」。
          # 【已推送恢复闸门保真总则（R3 r14/r16 #17）】：CI 无范围闸兜底（TD89 根因）——下列**每个**已推送分支走 -PostStatus/合并前，
          # 必须先在 worktree **手动补跑全部确定性闸（DoD、verify、范围闸、许可闸、防泄露闸、真实 diff 预算）**，绝不以 CI 复跑替代（CI 漏卡外越界）。
          Write-Host "    【闸门保真总则】已推送恢复合并前**必先手动补跑全部确定性闸：DoD、verify、范围闸、许可闸、防泄露闸、真实 diff 预算**——CI 无范围闸兜底、不可仅靠 CI 复跑（TD89 根因/R3 r16 #17）。下列各分支均在此总则下。" -ForegroundColor Yellow
          $sagaPrNum = if ((Test-Path Variable:pr) -and $pr) { $pr } else { 0 }
          if ($sagaDone -contains 'R3 评审') {
            # R3 r3 #2 + r6 #9：修复若改动了 PR head（如解决冲突的新提交），已录的 R3 pass 即对旧 diff 而言；base 被
            # retarget（Assert-RemotePrBase 拦下的正是它）同样令已录 pass 失效——head 与 base **双新鲜度**都满足才可直合。
            Write-Host "R3 已 pass、合并腿未完成：MERGED→cleanup；否则 DoD→verify→范围闸→许可闸→防泄露闸→真实 diff 预算；pwsh -NoProfile -File scripts/review.ps1 -WorktreePath `"$Wt`" -Base `"$shipBase`" -PrNumber $sagaPrNum -PostStatus；同一 reviewed SHA：ci.yml jobs completed+success→base/head→gh pr merge $sagaPrNum --squash --match-head-commit <同一 reviewed SHA>。"
          } elseif ($sagaPrNum -gt 0) {
            Write-Host "PR #$sagaPrNum 已开：DoD→verify→范围闸→许可闸→防泄露闸→真实 diff 预算；pwsh -NoProfile -File scripts/review.ps1 -WorktreePath `"$Wt`" -Base `"$shipBase`" -PrNumber $sagaPrNum -PostStatus；同一 reviewed SHA：ci.yml jobs completed+success→base/head→gh pr merge $sagaPrNum --squash --match-head-commit <同一 reviewed SHA>。"
          } else {
            # R3 r7 #17：未推送兜底靶 = evidence.redSha 原值（非 HEAD~1——resume 二次失败的多提交分支上 HEAD~1 制造二次死锁）；证据缺失/占位 → 人工核对。
            $sagaRemoteReset = if ($sagaEvdSha -cmatch '^[0-9a-f]{40}$') { "git reset --soft $sagaEvdSha 撤销本次提交（HEAD 归位 RED 证据 sha，非 HEAD~1）" } else { "人工核对工作树后处置（RED 证据缺失/占位，无自动 reset 靶）" }
            Write-Host "commit 已落、PR 状态未知：gh pr view $TaskId；未推送：$sagaRemoteReset→ship；已推送：DoD→verify→范围闸→许可闸→防泄露闸→真实 diff 预算；pwsh -NoProfile -File scripts/review.ps1 -WorktreePath `"$Wt`" -Base `"$shipBase`" -PrNumber <PR号> -PostStatus；同一 reviewed SHA：ci.yml jobs completed+success→base/head→gh pr merge <PR号> --squash --match-head-commit <同一 reviewed SHA>。"
          }
        }
      }
      throw
    }
  }

  'cleanup' {
    Step 'R1 Windows 安全拆除 worktree'
    # T35-RECEIPT：**拆除 worktree 前**从 $Wt 解析收据平面并留存（契约硬约束：一律 git -C $Wt rev-parse --git-common-dir、**禁 $RepoRoot 形态**——
    # linked worktree 返回主仓 .git **绝对路径**，拆除 worktree 后该路径仍有效，故此处先解析、存 $rcGcdC 供下方拆除后清据消费）。
    # R3 r13 #9：全局 EAP=Stop 下 Test-Path 遇 provider/权限错会抛——best-effort 清据须绝不因探针抛而毁 cleanup，整块 try/catch 兜住（失败即无靶、下方走告警）。
    $rcGcdC = ''
    try {
      if (Test-Path $Wt) {
        $rcGcdC = "$(& git -C $Wt rev-parse --git-common-dir 2>$null)".Trim()
        if ($rcGcdC -and -not [System.IO.Path]::IsPathRooted($rcGcdC)) { $rcGcdC = Join-Path $Wt $rcGcdC }
        if ($rcGcdC -and -not (Test-Path $rcGcdC -PathType Container)) { $rcGcdC = '' }
      }
    } catch { $rcGcdC = '' }
    if (Test-Path $Wt) {
      # TD47 脏树守卫：cleanup 会 force-destroy worktree（worktree remove --force + Remove-Item -Recurse -Force），
      # 未提交/未跟踪改动一旦删除即不可逆（未入 git 对象库、无 reflog）。删除前先查脏树：有改动且无 -Force → 拒绝、
      # 打印将丢失什么、零删除；干净树（或 -Force 显式覆盖，或非 git 目录=残留态）照旧拆除。
      # 用 `status --porcelain`（非 `branch --merged`）：squash-merge 使卡分支非 base 祖先，--merged 会误拒正路径 cleanup、破坏收尾链。
      $dirty = & git -C $Wt status --porcelain 2>$null
      if (($LASTEXITCODE -eq 0) -and $dirty -and (-not $Force)) {
        Write-Host '以下未提交/未跟踪改动将随 worktree 一并永久丢失（未入 git 对象库、不可恢复）：' -ForegroundColor Red
        $dirty | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        throw "cleanup 已拒绝拆除 '$Wt'：worktree 有未提交改动（见上）。确认要丢弃 → 加 -Force 重跑；要保留 → 先在该 worktree 提交/推送后再 cleanup。"
      }
      & git -C $RepoRoot worktree remove --force $Wt 2>&1 | Write-Host
      if (Test-Path $Wt) {
        Write-Warning '常规移除失败（多半 .venv/node_modules 被占用）。请关掉占用该目录的进程后重试。'
        & git -C $RepoRoot worktree remove --force --force $Wt 2>&1 | Write-Host
        # 即便 git 取消了 worktree 跟踪，被锁文件仍可能残留 → 显式清目录，避免下次 start 撞 "已存在"
        if (Test-Path $Wt) { Remove-Item -Recurse -Force $Wt -ErrorAction SilentlyContinue }
      }
    }
    & git -C $RepoRoot worktree prune
    # T24-MERGETOKEN 凭据闸：branch -D 是本相位唯一无脏树守卫的破坏性删除点——未合并提交被删仅 reflog 期限内可救。
    # 删除本地分支只认三种机检/显式信号之一：① ship 合并成功时铸造的单次凭据（用后即废）；② -Force（语义同 TD47 脏树
    # 守卫：确认丢弃未合并工作；abandon/重来路径走此口）；③ gh 在线补验 PR 状态==MERGED（-NoAutoMerge 人工合并/他机
    # 合并路径）。三者皆无 → 保留分支 fail-safe。不用 ancestry（--merged/merge-base）判合并：squash-merge 使卡分支
    # 永非 base 祖先（见上方脏树守卫注）。
    $tokPath = "$(& git -C $RepoRoot rev-parse --git-common-dir 2>$null)".Trim()
    if ($tokPath) {
      if (-not [System.IO.Path]::IsPathRooted($tokPath)) { $tokPath = Join-Path $RepoRoot $tokPath }
      $tokPath = Join-Path (Join-Path $tokPath 'scaffold-merged') $TaskId
    }
    $branchTip = "$(& git -C $RepoRoot rev-parse --verify --quiet $TaskId 2>$null)".Trim()
    if (-not $branchTip) {
      # 本地分支已不存在：无事可删（保持既有幂等语义，静默略过）
    } elseif ($Force) {
      # R3 r3 #9：显式人工覆盖优先于一切凭据判定——若排在凭据分支之后，残留/tip 不匹配的凭据会遮蔽 -Force，
      # 警告里给出的「-Force 重跑」出路将永不可达。-Force 语义（确认丢弃）覆盖任意状态，故用 branch -D。
      & git -C $RepoRoot branch -D $TaskId 2>$null
      if ($LASTEXITCODE -eq 0) {
        if ($tokPath -and (Test-Path $tokPath)) { Remove-Item $tokPath -Force -ErrorAction SilentlyContinue }   # 连带注销残留凭据（防陈旧凭据日后误配同名新分支）
        if ($tokPath -and (Test-Path $tokPath)) {
          # R3 r4 #9：与凭据路径同款——注销失败不得虚报成功，残留凭据须显式报修
          Write-Warning "T24-MERGETOKEN：-Force 已删除本地分支 $TaskId，但残留凭据注销失败（$tokPath 仍在）——请手工删除该文件（防日后误配同名新分支）。"
        } else {
          Write-Host "T24-MERGETOKEN：-Force 在位（确认丢弃未合并工作）——已删除本地分支 $TaskId 并注销残留凭据。" -ForegroundColor Yellow
        }
      } else {
        Write-Warning "T24-MERGETOKEN：-Force 下 branch -D 仍失败（分支被别的 worktree 占用？）——处理后重跑 cleanup。"
      }
    } elseif ($tokPath -and (Test-Path $tokPath)) {
      # R3 #17：凭据内容升级为载荷（tip 绑定）——凭据证明的是「这个分支状态已合并」，不是「这个名字可删」。
      # 删除用 CAS（`update-ref -d <ref> <expected-sha>`，现值不符即拒）：比对与删除之间的并发 ref 前移也拦住（R3 r3 #17）。
      $tokTip = "$(@(Get-Content $tokPath -ErrorAction SilentlyContinue) -match '^tip=' -replace '^tip=', '' | Select-Object -First 1)".Trim()
      if ($tokTip -and ($tokTip -ieq $branchTip)) {
        & git -C $RepoRoot update-ref -d "refs/heads/$TaskId" $tokTip 2>$null
        if ($LASTEXITCODE -eq 0) {
          Remove-Item $tokPath -Force -ErrorAction SilentlyContinue   # 单次性：仅在删除成功后注销（失败保留凭据供重试）
          if (Test-Path $tokPath) {
            # R3 #9：注销失败不得虚报成功——残留凭据可再授权一次同名分支删除（单次性受损），显式报修
            Write-Warning "T24-MERGETOKEN：本地分支 $TaskId 已删除，但凭据注销失败（$tokPath 仍在）——单次性受损，请手工删除该文件。"
          } else {
            Write-Host "T24-MERGETOKEN：合并凭据在位且 tip 匹配（CAS 删除）——已删除本地分支 $TaskId 并注销凭据。" -ForegroundColor DarkGray
          }
        } else {
          Write-Warning "T24-MERGETOKEN：CAS 删除失败（比对后 ref 又前移 / 引用被占用）——凭据保留，处理后重跑 cleanup。"
        }
      } else {
        Write-Warning "T24-MERGETOKEN：凭据在位但 tip 不匹配（凭据 tip=$tokTip / 分支 tip=$branchTip）——铸造后分支另有新提交或同名重建，旧凭据不授权删除，保留分支 fail-safe。重新合并新状态 → 重跑 ship；确认丢弃 → -Phase cleanup -Force。"
      }
    } else {
      # 在线补验前先判 origin 在位：无远端仓（含 hermetic 夹具、纯本地 -Local 项目）直接跳过 gh，保持离线确定性（L78 同族约束）。
      # 另判 gh 在位（Get-Command）：命令缺席时 `& gh` 抛 CommandNotFoundException，`2>$null` 接不住、EAP=Stop 下直接
      # 崩相位——按契约「补验不可得」应走保留分支，而非中途非零退出（fresh-context 审计 F1）。
      $hasOrigin = "$(& git -C $RepoRoot remote get-url origin 2>$null)".Trim()
      $ghOk = if ($hasOrigin) { [bool](Get-Command gh -ErrorAction SilentlyContinue) } else { $false }
      # R3 #17：在线补验同样 tip 绑定——PR 须 MERGED **且** headRefOid == 本地分支 tip，否则（PR 合并后分支又添新提交）保留。
      # --json 字段表须引号包裹（裸逗号被 PowerShell 拆成数组 → 真 gh 收到两个参数即报错）
      $prJson = if ($ghOk) { "$(& gh pr view $TaskId --json 'state,headRefOid' 2>$null)" } else { '' }
      $prState = ''; $prHead = ''
      if ($prJson) { try { $prO = $prJson | ConvertFrom-Json; $prState = "$($prO.state)"; $prHead = "$($prO.headRefOid)" } catch { $prState = ''; $prHead = '' } }
      if (($prState -ieq 'MERGED') -and $prHead -and ($prHead -ieq $branchTip)) {
        & git -C $RepoRoot update-ref -d "refs/heads/$TaskId" $prHead 2>$null
        if ($LASTEXITCODE -eq 0) {
          Write-Host "T24-MERGETOKEN：无本地凭据，gh 在线补验 PR=MERGED 且 headRefOid==本地分支 tip（CAS 删除）——已删除本地分支 $TaskId。" -ForegroundColor DarkGray
        } else {
          Write-Warning "T24-MERGETOKEN：在线补验通过但 CAS 删除失败（比对后 ref 又前移 / 引用被占用）——保留分支，处理后重跑 cleanup。"
        }
      } else {
        Write-Warning "T24-MERGETOKEN：无合并凭据且在线补验不可得/非 MERGED——保留本地分支 $TaskId（fail-safe，防丢未合并提交）。确认丢弃 → -Phase cleanup -Force 重跑；确已合并 → 人工 git branch -D $TaskId。"
      }
    }
    # T35-RECEIPT：随 T24 凭据同点 best-effort 清理水位线收据（运行期产物、天然不入库；残留亦无害——新红相重写证据 sha 令旧据必不符）。
    # 收据平面 $rcGcdC 已在**拆除 worktree 前**由 $Wt 解析并留存（契约禁 $RepoRoot 形态，见相位开头）。失败不 throw 但**须 Write-Warning**
    # （R3 r8 #9：best-effort ≠ 静默吞——路径不可解析或删除失败须提示，残留收据可手工删）。
    if ($rcGcdC) {
      $rcPathC = Join-Path (Join-Path $rcGcdC 'scaffold-shipped') $TaskId
      # R3 r13 #9：Test-Path/Remove-Item 在 EAP=Stop 下遇 provider/权限错会抛——best-effort 清据整块 try/catch 兜住、绝不 throw，删除失败/探针失败均降级为告警。
      try {
        if (Test-Path $rcPathC) {
          Remove-Item $rcPathC -Force -ErrorAction SilentlyContinue   # 收据恒为普通文件；不 -Recurse，令占位目录等异常态走下方告警分支（best-effort 可见）
          if (Test-Path $rcPathC) { Write-Warning "T35-RECEIPT：水位线收据清理失败（best-effort，$rcPathC 仍在）——残留无害（新红相重写证据 sha 令旧据必不符），可手工删除。" }
        }
      } catch { Write-Warning "T35-RECEIPT：水位线收据清理探针/删除异常（best-effort 兜住不 throw）：$($_.Exception.Message)——残留无害，可手工删除。" }
    } else {
      Write-Warning 'T35-RECEIPT：cleanup 无法从 $Wt 解析 git-common-dir（worktree 已拆/不可解析或非目录），跳过水位线收据清理（best-effort，残留无害）。'
    }
    Write-Host "`nR5 文档同步提醒：合并后请更新" -ForegroundColor Yellow
    Write-Host "  - specs/tasks/$TaskId.md  status: -> merged" -ForegroundColor Yellow
    Write-Host "  - CLAUDE.md '当前阶段' / README（若面向用户）" -ForegroundColor Yellow
    Write-Host "`n复盘（自净化经验，见 docs/LESSONS.md）：本卡若踩过非平凡坑，入账" -ForegroundColor Yellow
    Write-Host "  pwsh -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'" -ForegroundColor DarkGray
    Write-Host "  blocking 的当场 promote <id>" -ForegroundColor DarkGray

    Step '经验系统自检（lessons check）'
    & pwsh -NoProfile -File (Join-Path $RepoRoot 'scripts/lessons.ps1') check
    if ($LASTEXITCODE -ne 0) { Write-Warning '经验系统 check 未过（必须层超限/id 重复/字段缺失）——见 docs/LESSONS.md 提纯。' }
  }
}
