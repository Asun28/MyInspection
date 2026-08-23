#requires -Version 7
<#
.SYNOPSIS
  冷存压缩（cold-storage compaction）：把**已闭合**的技术债行（status=paid/accepted）与**已合并**的任务卡
  （status=merged）搬出「热路径」到 `specs/archive/`，只在活文件里留下未闭合项，并生成可 grep 的精简索引。

.DESCRIPTION
  动机：`specs/tech-debt-tracker.md` 与 `specs/tasks/*.md` 是 append-only 的真相源，随交付不断增长；
  但其中约九成是**已还债项 / 已合并卡**——每张新卡、每轮 triage 心跳、每次「这坑还没还过？」的检索
  都会把整段已闭合历史吞进上下文，令新任务的 token 成本远高于早期。本脚本把冷数据搬到旁路的
  `specs/archive/`（仍在仓内、版本化、可检索），让活文件回到「只装在飞项」的小体量，而**不删任何轨迹**
  （append-only 语义在归档侧延续）。

  搬运规则（保守：只搬**定局闭合**项，拿不准一律留在热路径）：
    - 技术债：整行 status 前缀 `paid` / `accepted` → 搬到 `specs/archive/tech-debt-archive.md`；
              `open` / `carded`（含示例行）留在活追踪器。
    - 任务卡：front-matter `status: merged` → 移到 `specs/archive/tasks/<id>.md`；
              `todo` / `in-progress` / `in-review` 留在 `specs/tasks/`。
              （check-cards.ps1 只非递归扫 specs/tasks/*.md，故归档卡天然不再被校验——它们已冻结。）

  产物（全部由本脚本生成/维护，勿手工编辑正文）：
    - specs/archive/tech-debt-archive.md  已闭合债项整行（append-only，保留完整还债指针=机构记忆）
    - specs/archive/tech-debt-index.md    一行一条的精简索引（丢弃冗长「偿还指针」列、截断「债」列；可 grep）
    - specs/archive/tasks/<id>.md         已合并卡原文
    - specs/archive/cards-index.md        已归档卡的精简索引（id · 状态 · 标题）

  幂等：跑完活文件已无可搬项，再跑搬 0 项；索引每次从归档侧**投影重算**，故重复运行结果稳定。
  离线、无 gh/网络、无 _config 依赖。用 Move-Item（非 git mv）——git 提交时自会识别重命名，避免依赖 git 存在。

.PARAMETER RepoRoot  仓库根（默认由脚本位置派生）。
.PARAMETER DryRun    只报「会搬什么」、写零文件（首用/核验安全网）。
.PARAMETER CheckCardsIndex  只读核验 cards-index.md 是否与归档卡投影逐字节一致；不搬运、不修复。
.PARAMETER Quiet     仅打印一行汇总。
.PARAMETER LessonsOnly  仅执行 -LessonIds 路径；不读写技术债、任务卡及其索引。
.EXAMPLE
  pwsh -File scripts\archive.ps1 -DryRun     # 预览：将搬多少债项/卡，不写任何文件
.EXAMPLE
  pwsh -File scripts\archive.ps1             # 执行压缩 + 重算索引
#>
[CmdletBinding()]
param(
  [string]$RepoRoot,
  [switch]$DryRun,
  [switch]$CheckCardsIndex,
  [switch]$Quiet,
  [switch]$LessonsOnly,
  [string[]]$LessonIds    # T40-LEDGERARCH：lessons 账本冷存目标（如 -LessonIds L32,L34）；未传时下方逻辑整段跳过，其余两路径行为逐字节不变
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出；缺失即 fail-open（同其它脚本）
. (Join-Path $PSScriptRoot '_cards.ps1')

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path }
if ($LessonsOnly -and -not $PSBoundParameters.ContainsKey('LessonIds')) {
  throw 'archive.ps1 -LessonsOnly 必须与 -LessonIds 同用（fail-closed，禁止伪装成成功的空范围归档）。'
}
if ($LessonsOnly -and $CheckCardsIndex) {
  # -CheckCardsIndex 的分支在下面所有 -LessonsOnly 守卫**之前**就 exit，同时传入会静默吞掉模式开关、
  # 让调用方以为跑的是 lessons 路径。互斥组合必是调用方口径出错，显式拒绝而非择一执行。
  throw 'archive.ps1：-LessonsOnly 与 -CheckCardsIndex 互斥（后者只读核验卡索引，与 lessons 路径无关）。'
}

$TrackerPath  = Join-Path $RepoRoot 'specs/tech-debt-tracker.md'
$TasksDir     = Join-Path $RepoRoot 'specs/tasks'
$ArchiveDir   = Join-Path $RepoRoot 'specs/archive'
$ArchTasksDir = Join-Path $ArchiveDir 'tasks'
$TdArchive    = Join-Path $ArchiveDir 'tech-debt-archive.md'
$TdIndex      = Join-Path $ArchiveDir 'tech-debt-index.md'
$CardsIndex   = Join-Path $ArchiveDir 'cards-index.md'

# ── 表格助手：Split-TdRow 共享自 _cards.ps1。──
function Test-SeparatorRow([string[]]$cells) {
  # 分隔行：每个非空单元格只由 - 与可选前后 : 组成（|---|:--:|…）
  $nonEmpty = @($cells | Where-Object { $_ -ne '' })
  return ($nonEmpty.Count -gt 0) -and (@($nonEmpty | Where-Object { $_ -notmatch '^:?-+:?$' }).Count -eq 0)
}
# 单元格搬进「另一张 markdown 表」（索引）时净化：字面竖线会破坏索引列 → 归一为 /，折叠空白，按需截断。
function Format-Cell([string]$s, [int]$max = 0) {
  if ($null -eq $s) { $s = '' }
  $s = ($s -replace '\\\|', '/') -replace '\|', '/'
  $s = ($s -replace '\s+', ' ').Trim()
  if ($max -gt 0 -and $s.Length -gt $max) { $s = $s.Substring(0, $max - 1).TrimEnd() + '…' }
  return $s
}
# T40-LEDGERARCH：lessons 账本冷存辅助——扫 `## L<n>` 标题，区间 = 该标题起、至下一个通用 `## ` 标题（任意内容）或 EOF 止
# （独占端点，与卡片定义一致）；供下面 -LessonIds 路径定位条目块、判队 LEDGER/归档两侧是否已含某 id。
function Get-LedgerHeadings([string[]]$lines) {
  $marks = [System.Collections.Generic.List[object]]::new()
  for ($i = 0; $i -lt $lines.Count; $i++) {
    # F11（R3）：同时保留**逐字 id 串**（Id）与数值（Number）——只存 [int] 会让 L02 别名撞上 L2 的块；
    # 定位/判存一律按 Id 精确串比，Number 只用于「最高 id」数值比较。
    # 位数上界 9：下一行就 [int] 它，超 Int32 会让整个脚本抛裸 .NET 转换异常。超界的标题一律不当条目标题
    # （仍作为区间终点参与 `^##\s` 切块），于是「解析不了的 id」永远不会被搬——与 lessons.ps1 的
    # Get-LessonNumber 同一个上界，两侧对「哪些 id 可处理」的认定不分家。
    if ($lines[$i] -match '^##\s+L(\d{1,9})\s*$') { $marks.Add([pscustomobject]@{ Id = "L$($Matches[1])"; Number = [int]$Matches[1]; Start = $i }) }
  }
  $result = [System.Collections.Generic.List[object]]::new()
  foreach ($m in $marks) {
    $end = $lines.Count
    for ($j = $m.Start + 1; $j -lt $lines.Count; $j++) { if ($lines[$j] -match '^##\s') { $end = $j; break } }
    $result.Add([pscustomobject]@{ Id = $m.Id; Number = $m.Number; Start = $m.Start; End = $end })
  }
  # 不用逗号运算符钉住返回值——本函数结果既会被直接赋值、也会被 `| Where-Object` 逐元素消费（下方判队 id 用），
  # 逗号会让 Where-Object 收到「整个 List 当一个对象」而非逐条元素。直接赋值处改由调用点外包 @() 兜零/单元素坍缩。
  return $result
}
# 半开区间取子数组，避开 PowerShell `n..(n-1)` 空区间反而倒序遍历的坑（0..-1 会产出 @(0,-1)，非空数组）。
function Get-LineSlice([string[]]$lines, [int]$from, [int]$toExclusive) {
  if ($toExclusive -le $from) { return ,@() }
  return ,@($lines[$from..($toExclusive - 1)])
}

$movedTd = @()      # 搬走的技术债整行（原文）
$stats = [ordered]@{ td_archived = 0; td_kept = 0; cards_archived = 0; cards_kept = 0 }

# ── 1. 技术债：拆热/冷 ──
$trackerHeader = $null; $trackerSep = $null; $statusIdx = 5
if (-not $LessonsOnly -and (Test-Path $TrackerPath)) {
  $lines = Get-Content $TrackerPath
  $keptLines = [System.Collections.Generic.List[string]]::new()
  foreach ($line in $lines) {
    if ($line -notmatch '^\s*\|') { $keptLines.Add($line); continue }   # 非表格行原样保留
    $cells = Split-TdRow $line
    if ($cells.Count -lt 1) { $keptLines.Add($line); continue }
    # 表头行：首列 == 'id' → 动态定位状态列，保留
    if ($cells[0] -eq 'id') {
      $trackerHeader = $line
      $idx = [array]::IndexOf($cells, '状态'); if ($idx -ge 0) { $statusIdx = $idx }
      $keptLines.Add($line); continue
    }
    if (Test-SeparatorRow $cells) { $trackerSep = $line; $keptLines.Add($line); continue }
    # 数据行：按状态列分冷/热
    $status = if ($cells.Count -gt $statusIdx) { $cells[$statusIdx] } else { '' }
    # 只搬**恰好** paid/accepted（trim 后精确匹配）——`\b` 后缀式会把 `paid; open`/`accepted pending review`/`paid-ish`
    # 这类含歧义或仍隐含「未闭合」的自由文本误判为冷（fresh-context 审计 #4）。拿不准一律留热（保守，防误归档在飞债）。
    if ($status.Trim() -match '^(paid|accepted)$') {
      $movedTd += $line; $stats.td_archived++       # 冷：搬走
    } else {
      $keptLines.Add($line); $stats.td_kept++        # 热：保留（open/carded/示例/歧义/未知）
    }
  }
}

# ── 2. 任务卡：找 merged ──
$mergedCards = @()   # @{ path; id; title; status }
function Get-CardField([string]$raw, [string]$key) {
  $fm = Get-FrontMatter $raw
  if (-not $fm) { return $null }
  $value = Get-Scalar $fm $key
  if ($null -eq $value) { return $null }
  return Get-UncommentedValue $value
}

function Get-CardsIndexText([string]$archiveTasksDir) {
  $cardRows = [System.Collections.Generic.List[string]]::new()
  if (Test-Path -LiteralPath $archiveTasksDir -PathType Container) {
    foreach ($cf in (Get-ChildItem -LiteralPath $archiveTasksDir -Filter *.md -ErrorAction SilentlyContinue | Sort-Object Name)) {
      $raw = Get-Content -LiteralPath $cf.FullName -Raw
      $id = Format-Cell ([IO.Path]::GetFileNameWithoutExtension($cf.Name))
      $title = Format-Cell (Get-CardField $raw 'title') 100
      $st = Format-Cell (Get-CardField $raw 'status')
      $cardRows.Add("| $id | $st | $title |")
    }
  }
  $cardHead = @(
    '# 已归档任务卡索引（merged cards · cold storage）',
    '',
    ('> 一行一条已 `merged` 的卡，共 {0} 张；完整卡在 `specs/archive/tasks/<id>.md`。' -f $cardRows.Count),
    '> 由 `scripts/archive.ps1` 从 `specs/archive/tasks/` 投影生成，勿手工编辑。',
    '',
    '| id | 状态 | 标题 |',
    '|---|---|---|'
  )
  # 生成件固定 UTF-8 no-BOM + LF，避免 Windows Set-Content 的平台行尾让同一投影字节漂移。
  return (($cardHead + $cardRows) -join "`n") + "`n"
}

function Test-ExactBytes([byte[]]$left, [byte[]]$right) {
  if ($null -eq $left -or $null -eq $right -or $left.Length -ne $right.Length) { return $false }
  for ($i = 0; $i -lt $left.Length; $i++) {
    if ($left[$i] -ne $right[$i]) { return $false }
  }
  return $true
}

if ($CheckCardsIndex) {
  $expectedCardsIndexBytes = [Text.UTF8Encoding]::new($false).GetBytes((Get-CardsIndexText $ArchTasksDir))
  [byte[]]$actualCardsIndexBytes = if (Test-Path -LiteralPath $CardsIndex -PathType Leaf) {
    [IO.File]::ReadAllBytes($CardsIndex)
  } else { $null }
  if (-not (Test-ExactBytes $actualCardsIndexBytes $expectedCardsIndexBytes)) {
    Write-Output '[ARCHIVE-CARDS-INDEX-DRIFT] specs/archive/cards-index.md 与 specs/archive/tasks/*.md 投影不一致；请用正常 archive 流程重建后提交。'
    exit 1
  }
  if (-not $Quiet) { Write-Host 'archive cards-index check: PASS' }
  exit 0
}

if (-not $LessonsOnly -and (Test-Path $TasksDir)) {
  foreach ($cf in (Get-ChildItem $TasksDir -Filter *.md -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne '_TEMPLATE.md' })) {
    $raw = Get-Content $cf.FullName -Raw
    $status = Get-CardField $raw 'status'
    if ($status -eq 'merged') {
      $mergedCards += @{ path = $cf.FullName; id = [IO.Path]::GetFileNameWithoutExtension($cf.Name); title = (Get-CardField $raw 'title'); status = $status }
      $stats.cards_archived++
    } else { $stats.cards_kept++ }
  }
}

# ── 3. lessons 账本冷存：-LessonIds 显式策展（T40-LEDGERARCH，正交独立于上面两路径；未传参本段不产生
#      任何 I/O，两既有路径行为逐字节不变）。源 docs/lessons/LEDGER.md，目标 specs/archive/lessons-archive.md。
$LedgerPath = Join-Path $RepoRoot 'docs/lessons/LEDGER.md'
$LessonsArchive = Join-Path $ArchiveDir 'lessons-archive.md'
# F10（R3）：用参数**在场性**判定而非值真伪——`-LessonIds ''` 绑定 @('')，单元素数组按值展开成空串为假，
# 值判定会静默跳过整段、exit 0，绕过空 token 的 fail-closed 承诺。在场即进入校验（空串在循环里被显式拒绝）。
$lessonsUsed = $PSBoundParameters.ContainsKey('LessonIds')
$lessonsReport = [System.Collections.Generic.List[string]]::new()
$lsMoved = 0; $lsSkipped = 0; $lsFailed = 0
$ledgerLines = @()
$archiveLines = @()
if ($lessonsUsed) {
  # F1（R3）：`pwsh -File` 外部调用不做逗号数组自动拆分——`-LessonIds L32,L34` 落地成一个字符串
  # "L32,L34"（而非两元素数组），偏偏 README 文档的正是这种写法，实测外部调用下会整体判非法、0 搬。
  # 校验前显式按逗号展开每个元素，兑现文档承诺的调用形态。F8（R3）：空 token **保留**进校验——
  # 悄悄滤掉会让 `-LessonIds ','` 这类误输入静默 exit 0，违反本路径宣示的 fail-closed。
  $LessonIds = @($LessonIds | ForEach-Object { "$_" -split ',' } | ForEach-Object { $_.Trim() })
  if ($LessonIds.Count -eq 0) {
    # F10 补全：参数在场却展开出 0 个 token（如显式空数组）——同属误输入，fail-closed。
    Write-Warning 'archive.ps1 -LessonIds：参数在场但没有任何 id token——fail-closed 拒绝。'
    $lessonsReport.Add('  [空参数]'); $lsFailed++
  }
  # 用「先置空数组、命中再重赋值」而非 `$x = if(...){}else{@()}` 表达式赋值——后者任一分支零输出时，
  # 管道会把结果拆散退化成 $null（PowerShell 空/单元素集合坍缩坑：函数直接赋值处另需调用点外包 @() 兜底）。
  $ledgerLines = @()
  if (Test-Path $LedgerPath) { $ledgerLines = @(Get-Content $LedgerPath) }
  $archiveRaw = @()
  if (Test-Path $LessonsArchive) { $archiveRaw = @(Get-Content $LessonsArchive) }
  $archiveHead0 = @(Get-LedgerHeadings $archiveRaw)
  # 归档件正文 = 自其第一个 `## L<n>` 标题起（跳过标题/头注 blockquote）；无条目时视作空正文。
  $archiveLines = @()
  if ($archiveHead0.Count) { $archiveLines = Get-LineSlice $archiveRaw $archiveHead0[0].Start $archiveRaw.Count }

  # Next-Id 再铸安全网：冻结「本轮起跑时」LEDGER 最大 id——搬走它会让下次 Next-Id 重铸撞号；
  # 本轮内固定值，不随本轮搬运递减（同批多 id 请求时用同一基准判定，见卡片 REFUSE 最高 id 条款）。
  $maxId = -1
  foreach ($h in (Get-LedgerHeadings $ledgerLines)) { if ($h.Number -gt $maxId) { $maxId = $h.Number } }

  foreach ($rawId in $LessonIds) {
    $id = "$rawId".Trim()
    if ($id -eq '') {
      # F8：多余逗号/空参产生的空 token——显式拒绝（fail-closed），绝不静默当作没发生。
      Write-Warning 'archive.ps1 -LessonIds：空 token（多余逗号或空参数）——fail-closed 拒绝。'
      $lessonsReport.Add('  [空token]'); $lsFailed++; continue
    }
    if ($id -notmatch '^L[1-9]\d*$') {
      # F11：只收**规范形式**（无前导零）——`L02` 这类别名经数值匹配会撞上 `L2` 的块，fail-closed 拒绝。
      Write-Warning "archive.ps1 -LessonIds：非法/非规范 id『$rawId』（须形如 L32，无前导零），已跳过。"
      $lessonsReport.Add("  [无效] $rawId"); $lsFailed++; continue
    }
    $n = [int]($id.Substring(1))
    if ($n -eq $maxId) {
      Write-Warning "archive.ps1 -LessonIds：拒绝搬 $id ——当前 LEDGER 最高 id，搬走会令下次 Next-Id 重铸撞号，已跳过（其余 id 照常处理）。"
      $lessonsReport.Add("  [拒绝-最高id] $id"); $lsFailed++; continue
    }
    $inLedger = @(Get-LedgerHeadings $ledgerLines | Where-Object { $_.Id -eq $id })
    $inArchive = @(Get-LedgerHeadings $archiveLines | Where-Object { $_.Id -eq $id })
    if (-not $inLedger.Count -and -not $inArchive.Count) {
      Write-Warning "archive.ps1 -LessonIds：未知 id『$id』——LEDGER 与归档均查无此条（防手滑打错 id），已跳过。"
      $lessonsReport.Add("  [未知id] $id"); $lsFailed++; continue
    }
    if ($inArchive.Count -and $inLedger.Count) {
      # F3（R3）自愈：归档与 LEDGER 两侧并存（LEDGER 替换中途失败 / 人工误把条目粘回 LEDGER 时撞见）。
      # F9（R3）：自愈**只在两侧内容逐字一致时**才补完移动——归档若陈旧/与在册分歧，盲删 LEDGER 侧会
      # 毁掉较新的在册内容（违反只搬不删）；不一致即拒绝自动处理、双侧不动、fail-closed 留人工核对。
      $h = $inLedger[0]
      $ha = $inArchive[0]
      $ledgerBlockNorm = ((Get-LineSlice $ledgerLines $h.Start $h.End) -join "`n").TrimEnd()
      $archiveBlockNorm = ((Get-LineSlice $archiveLines $ha.Start $ha.End) -join "`n").TrimEnd()
      if ($ledgerBlockNorm -ne $archiveBlockNorm) {
        Write-Warning "archive.ps1 -LessonIds：$id 两侧并存且内容不一致（疑似归档陈旧 / 在册已更新）——拒绝自动清除，双侧未动，请人工核对后再定。"
        $lessonsReport.Add("  [冲突-两侧不一致] $id"); $lsFailed++; continue
      }
      $ledgerLines = (Get-LineSlice $ledgerLines 0 $h.Start) + (Get-LineSlice $ledgerLines $h.End $ledgerLines.Count)
      $lessonsReport.Add("  [补齐-两侧并存] $id（两侧逐字一致，清除 LEDGER 残留副本补完移动）"); $lsMoved++; continue
    }
    if ($inArchive.Count) {
      # 幂等：归档已含该 id、LEDGER 已无此条（正常态：上轮已完整搬走）→ 0 动作，不重复追加。
      $lessonsReport.Add("  [已归档/跳过] $id（幂等，0 动作）"); $lsSkipped++; continue
    }
    $h = $inLedger[0]
    $block = Get-LineSlice $ledgerLines $h.Start $h.End
    $ledgerLines = (Get-LineSlice $ledgerLines 0 $h.Start) + (Get-LineSlice $ledgerLines $h.End $ledgerLines.Count)
    if ($archiveLines.Count -and "$($archiveLines[-1])".Trim() -ne '') { $archiveLines += '' }
    $archiveLines += $block
    $lessonsReport.Add("  [搬运] $id"); $lsMoved++
  }
}

# ── DryRun：只报不写 ──
if ($DryRun) {
  Write-Host "archive.ps1 -DryRun（不写任何文件）：" -ForegroundColor Cyan
  if (-not $LessonsOnly) {
    Write-Host "  技术债：将归档 $($stats.td_archived) 条（paid/accepted），保留 $($stats.td_kept) 条（open/carded/示例）"
    Write-Host "  任务卡：将归档 $($stats.cards_archived) 张（merged），保留 $($stats.cards_kept) 张"
    if (-not $Quiet -and $movedTd.Count) { Write-Host "  会搬的债项："; $movedTd | ForEach-Object { Write-Host "    $((Split-TdRow $_)[0])" -ForegroundColor DarkGray } }
    if (-not $Quiet -and $mergedCards.Count) { Write-Host "  会搬的卡："; $mergedCards | ForEach-Object { Write-Host "    $($_.id)" -ForegroundColor DarkGray } }
  }
  if ($lessonsUsed) {
    Write-Host "  lessons：将搬 $lsMoved 条，已归档/跳过 $lsSkipped 条，拒绝/无效 $lsFailed 条"
    if (-not $Quiet -and $lessonsReport.Count) { Write-Host "  lessons 明细："; $lessonsReport | ForEach-Object { Write-Host $_ -ForegroundColor DarkGray } }
  }
  # F2（R3）：DryRun 不再无条件 exit 0——`-DryRun -LessonIds L999` 之前告警照发却仍报成功，违反 fail-closed
  # 承诺（预览模式下也不该把「无效/拒绝」伪装成绿）。tracker/cards 的 DryRun 语义不受影响（那两路径不产生 $lsFailed）。
  if ($lessonsUsed -and $lsFailed -gt 0) { exit 1 } else { exit 0 }
}

# ── 落盘：确保归档目录存在 ──
# -LessonsOnly 且本轮 0 搬时不建目录：那一路承诺「幂等重跑不碰任何文件」，凭空造出 specs/archive/ 与之矛盾。
if (-not $LessonsOnly -or $lsMoved -gt 0) { New-Item -ItemType Directory -Force $ArchiveDir | Out-Null }
if (-not $LessonsOnly) { New-Item -ItemType Directory -Force $ArchTasksDir | Out-Null }

# ── 3. 写活追踪器（去掉冷行）+ 追加到归档 ──
if (-not $LessonsOnly -and $movedTd.Count) {
  Set-Content -Path $TrackerPath -Value ($keptLines -join "`n") -Encoding utf8

  if (-not $trackerHeader) { $trackerHeader = '| id | 发现日 | 位置 | 偏离了什么（债） | 严重度 | 状态 | 偿还指针 |' }
  if (-not $trackerSep) { $trackerSep = '|---|---|---|---|---|---|---|' }
  # 归档已存在则读旧行，否则起头。**去重按整行文本、绝不按 id**：id 并非唯一键（append-only 自由文本表，
  # 合并/手误可致同 id 两行，如本仓曾「TD69 两分支不同内容」）——按 id 去重会把「同 id 不同内容」的第二行
  # 从热文件删掉却不写进归档 = 静默数据丢失，违反卡片 `只搬不删`（fresh-context 审计 #1）。按整行去重只吞
  # 真正逐字重复的行（幂等所需），保全所有相异行。
  $existingRows = @{}
  $archiveBody = [System.Collections.Generic.List[string]]::new()
  if (Test-Path $TdArchive) {
    foreach ($l in (Get-Content $TdArchive)) {
      if ($l -match '^\s*\|') {
        $c = Split-TdRow $l
        if ($c.Count -ge 1 -and $c[0] -ne 'id' -and -not (Test-SeparatorRow $c)) { $existingRows[$l.Trim()] = $true; $archiveBody.Add($l) }
      }
    }
  }
  foreach ($row in $movedTd) {
    if (-not $existingRows.ContainsKey($row.Trim())) { $archiveBody.Add($row); $existingRows[$row.Trim()] = $true }
  }
  $archHead = @(
    '# 技术债归档（cold storage · paid/accepted）',
    '',
    '> `specs/tech-debt-tracker.md` 的**冷存**：status 已 `paid`/`accepted` 的债项整行搬到此处（append-only，保留完整还债轨迹=机构记忆），',
    '> 让活追踪器只留 `open`/`carded` 的热行、每轮扫描/每张新卡不再吞整段已还历史。',
    '> 精简索引见 `tech-debt-index.md`（一行一条、可 grep）；需某条完整还债指针时来此按 id 查。',
    '> 本文件与索引由 `scripts/archive.ps1` 生成/维护，勿手工编辑。',
    '',
    $trackerHeader,
    $trackerSep
  )
  Set-Content -Path $TdArchive -Value (($archHead + $archiveBody) -join "`n") -Encoding utf8
}

# ── 4. 重算技术债精简索引（从归档投影）──
if (-not $LessonsOnly -and (Test-Path $TdArchive)) {
  $idxRows = [System.Collections.Generic.List[string]]::new()
  $hdrIdx = 5
  foreach ($l in (Get-Content $TdArchive)) {
    if ($l -notmatch '^\s*\|') { continue }
    $c = Split-TdRow $l
    if ($c.Count -lt 1) { continue }
    if ($c[0] -eq 'id') { $ix = [array]::IndexOf($c, '状态'); if ($ix -ge 0) { $hdrIdx = $ix }; continue }
    if (Test-SeparatorRow $c) { continue }
    $id = Format-Cell $c[0]
    $loc = if ($c.Count -gt 2) { Format-Cell $c[2] 60 } else { '' }
    $debt = if ($c.Count -gt 3) { Format-Cell $c[3] 120 } else { '' }
    $sev = if ($c.Count -gt 4) { Format-Cell $c[4] } else { '' }
    $st = if ($c.Count -gt $hdrIdx) { Format-Cell $c[$hdrIdx] } else { '' }
    $idxRows.Add("| $id | $sev | $st | $loc | $debt |")
  }
  # 头注含反引号代码跨（`tech-debt-archive.md`）——须用**单引号**串（-f 注入计数），否则双引号里 `t 被当 TAB
  # 转义、文件名被吞成「ech-debt-archive.md」，指针失效（fresh-context 审计 #2）。
  $idxHead = @(
    '# 技术债精简索引（cold-storage index · 可 grep）',
    '',
    ('> 一行一条已归档（paid/accepted）债项，共 {0} 条；完整还债指针在 `tech-debt-archive.md` 按 id 查。' -f $idxRows.Count),
    '> 由 `scripts/archive.ps1` 从归档文件投影生成，勿手工编辑。新卡/续接查「这坑还没还过？」先 grep 本表。',
    '',
    '| id | 严重度 | 状态 | 位置 | 一句话（债，截断） |',
    '|---|---|---|---|---|'
  )
  Set-Content -Path $TdIndex -Value (($idxHead + $idxRows) -join "`n") -Encoding utf8
}

# ── 5. 移动 merged 卡 ──
if (-not $LessonsOnly) {
  foreach ($card in $mergedCards) {
    $dest = Join-Path $ArchTasksDir "$($card.id).md"
    # 目标已存在且**内容不同**（id 归档后又被重建的罕见碰撞）→ 跳过并告警，绝不 -Force 覆盖丢失（审计 #5）。
    # 内容相同则覆盖无害（幂等）。
    if ((Test-Path $dest) -and ((Get-Content $dest -Raw) -ne (Get-Content $card.path -Raw))) {
      Write-Warning "archive.ps1：归档目标已存在且内容不同，跳过以防覆盖丢失：specs/archive/tasks/$($card.id).md（活卡留原位，请人工核对）。"
      $stats.cards_archived--
      continue
    }
    Move-Item -Path $card.path -Destination $dest -Force
  }
}

# ── 6. 重算卡索引（从 specs/archive/tasks/ 投影）──
if (-not $LessonsOnly -and (Test-Path $ArchTasksDir)) {
  [IO.File]::WriteAllText($CardsIndex, (Get-CardsIndexText $ArchTasksDir), [Text.UTF8Encoding]::new($false))
}

# ── 7. 写 lessons 账本冷存（T40-LEDGERARCH；仅当本轮确有新搬运/补齐时落盘——幂等重跑 0 搬 = 不触碰任何文件）──
# F3（R3）：目的地（归档）先写、源（LEDGER，会被删走该块）后写——归档写失败时源保持原样不动，绝不出现
# 「两边都没有」的数据丢失窗口；只有归档确认落盘成功，才敢对 LEDGER 做破坏性移除。
# F5（R3）：且绝不对既有归档 Set-Content 直写——截断先于写入，中断/盘满的半写会把「LEDGER 里早已删源」的
# 旧条目一并毁掉。先写旁路临时件、写全后同卷原子替换（rename）——替换成功前旧归档始终完好。
# 并发边界（有意不做锁/CAS）：本脚本与全仓其余状态脚本同属**单写者**运行模型（一位策展人/一条 agent 主线、
# 波次串行），与既有 tracker/cards 两路径的读改写一致；跨进程锁与竞态测试会把 hermetic 确定性夹具变不确定，
# 收益对不上威胁模型——若运行模型改变（多写者），届时统一给全部路径上锁，不在本卡单点加。
if ($lessonsUsed -and $lsMoved -gt 0) {
  # 头注仿 tech-debt-archive.md：单引号串（含反引号代码跨），避开双引号里 `t 被当 TAB 转义吞字（同 #2 审计坑，行 196-197）。
  $lsHead = @(
    '# Lessons 账本归档（cold storage · 显式策展搬入）',
    '',
    '> `docs/lessons/LEDGER.md` 的**冷存**：被显式策展搬出（已归档 / 被后续经验合并吸收）的条目整块移到此处，',
    '> append-only、只搬不删；`lessons.ps1 search` 会统一召回并标 `[archived]`，也可裸 grep。',
    '> 唯一写入口是 `scripts/archive.ps1 -LessonIds L<n>[,L<n>...]`——id 恒为显式传入，本脚本自己不判定该搬谁；',
    '> `lessons.ps1 archive` 可先机械预筛出保守候选（规则见 `docs/LESSONS.md` §3），但仍只是转调本入口。勿手工编辑正文。',
    ''
  )
  $lsArchiveWriteOk = $true
  $lsTmpPath = "$LessonsArchive.tmp"
  try {
    Set-Content -Path $lsTmpPath -Value (($lsHead + $archiveLines) -join "`n") -Encoding utf8 -ErrorAction Stop
    Move-Item -Path $lsTmpPath -Destination $LessonsArchive -Force -ErrorAction Stop
  } catch {
    $lsArchiveWriteOk = $false
    $lsFailed++
    Remove-Item $lsTmpPath -Force -ErrorAction SilentlyContinue
    Write-Warning "archive.ps1 -LessonIds：归档暂存/替换失败，LEDGER 与既有归档保持原样未动（防数据丢失窗口）：$LessonsArchive —— $($_.Exception.Message)"
  }
  if ($lsArchiveWriteOk) {
    # F7（R3）：LEDGER 同样暂存+原子替换——直写截断被中断会毁掉从未归档的在册经验。归档已先行落盘，
    # 此步失败只留下「两侧并存」态（归档为权威副本），重跑经上方补齐分支自愈——双侧皆无丢失窗口。
    $lsLedgerTmp = "$LedgerPath.tmp"
    try {
      Set-Content -Path $lsLedgerTmp -Value ($ledgerLines -join "`n") -Encoding utf8 -ErrorAction Stop
      Move-Item -Path $lsLedgerTmp -Destination $LedgerPath -Force -ErrorAction Stop
    } catch {
      $lsFailed++
      Remove-Item $lsLedgerTmp -Force -ErrorAction SilentlyContinue
      Write-Warning "archive.ps1 -LessonIds：LEDGER 暂存/替换失败（归档已落盘=两侧并存态，重跑将自愈补齐）：$LedgerPath —— $($_.Exception.Message)"
    }
  }
}

$summary = if ($LessonsOnly) { 'archive.ps1：lessons-only（技术债/任务卡/索引均未读写）' }
else { "archive.ps1：技术债归档 $($stats.td_archived) 条（活追踪器留 $($stats.td_kept)）· 任务卡归档 $($stats.cards_archived) 张（活目录留 $($stats.cards_kept)）→ specs/archive/" }
if ($lessonsUsed) { $summary += " · lessons 归档 $lsMoved 条（跳过 $lsSkipped ｜ 拒绝/无效 $lsFailed）→ specs/archive/lessons-archive.md" }
if ($Quiet) { Write-Host $summary }
else {
  Write-Host $summary -ForegroundColor Green
  Write-Host "  索引：specs/archive/tech-debt-index.md · specs/archive/cards-index.md（可 grep 查已闭合项）"
}
if ($lessonsUsed -and $lsFailed -gt 0) { exit 1 } else { exit 0 }
