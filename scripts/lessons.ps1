#requires -Version 7
<#
.SYNOPSIS
  自净化经验系统的编排器（Tier1 必须 / Tier2 按需 / Tier3 总账）。
  让工作流"吸取经验、自我进步"：捕获→检索→晋升→提纯，遇到同样问题不再重导。

.DESCRIPTION
  三层与单向学习流（详见 docs/LESSONS.md）：
    Tier3 热/冷   docs/lessons/LEDGER.md + specs/archive/lessons-archive.md —— 共同构成项目总经验真相源
    Tier2 按需   docs/lessons/<topic>.md           —— lessons skill 按上下文触发
    Tier1 必须   CLAUDE.md「## 经验铁律（必须加载）」—— 每轮自动入上下文，**封顶**
  方向：会话级（progress.md / claude-mem）─捕获→ 总账 ─晋升→ 按需 / 必须。单向。

  子命令：
    add      追加一条经验到总账（低摩擦捕获）。
    list     列出总账条目（可按 tier/tag/kind 过滤）。
    search   按关键词检索总账 + 按需层（recall）。
    check    护栏：校验必须层条数未超上限、id 无重复、字段完整。
    promote  打印某条的晋升建议（是否够格进必须/按需层 + 操作提示）。
    bump     某条经验复发一次 → recurrence +1（复发计数入口，跨过 2 即提示 promote）。
    archive  选择一次性 ledger 经验，预览或交给 archive.ps1 冷存。

  两类经验（-Kind，正交于 tier/severity）：
    pitfall  （默认）**工具链/方法的坑**（怎么干活踩雷）——可升级为机械守卫（enforced_by）。
    judgment **方向/决策的失手**（Anthropic《Recursive Self-Improvement》的 judgment scaffolding）：
             记「当时选了次优方向、更好的下一步是什么」。判断型启发式难机械执法，故喂 docs/HARNESS-REVIEW.md
             复审、随模型变强检验方向品味是否提升，而非 enforced_by。symptom=情境 / root_cause=为何选错 / rule=更好的启发式。

  安全：只记工程结论。add 的密钥过滤是**尽力而为的 early filter**（判定复用 check-secrets.ps1 的
  Find-LineSecret，单一真相源）；**权威闸是 check-secrets**（ship / pre-push / CI 强制），
  PII 无机检——由作者入账前自查。

.EXAMPLE
  pwsh -File scripts\lessons.ps1 add -Tags 'powershell,git' -Severity blocking -Symptom '...' -RootCause '...' -Rule '...' -EnforcedBy 'scripts/review.ps1' -Cost '浪费40分钟'
.EXAMPLE
  pwsh -File scripts\lessons.ps1 add -Kind judgment -Tags 'planning,scope' -Severity major -Symptom '...' -RootCause '...' -Rule '...'
  pwsh -File scripts\lessons.ps1 list -FilterKind judgment
  pwsh -File scripts\lessons.ps1 search worktree
  pwsh -File scripts\lessons.ps1 check
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][ValidateSet('add', 'list', 'search', 'check', 'promote', 'bump', 'archive')][string]$Command = 'list',
  [Parameter(Position = 1)][string]$Query,            # search 的关键词 / promote|bump 的 id
  [string]$Tags,
  [ValidateSet('blocking', 'major', 'minor')][string]$Severity = 'major',
  [ValidateSet('pitfall', 'judgment')][string]$Kind = 'pitfall',  # 工具链坑(pitfall) vs 方向/决策失手(judgment)
  [ValidateSet('must', 'ondemand', 'ledger')][string]$Tier = 'ledger',
  [string]$Symptom,
  [string]$RootCause,
  [string]$Rule,
  [string]$Refs,
  [string]$EnforcedBy,   # 该经验的机械守卫（脚本/闸门路径），或 'none（理由）'；blocking 必填
  [string]$Cost,         # 可选：本坑的犯错成本（如 '浪费40分钟'/'半天返工'）——提 Gotcha 信噪比；仅给了才写进 meta 行
  [string]$FilterTier,
  [string]$FilterTag,
  [ValidateSet('pitfall', 'judgment')][string]$FilterKind,
  [string]$RepoRoot,
  [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open
if ($RepoRoot) { $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path }
else { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path }
. (Join-Path $PSScriptRoot '_config.ps1')
$Ledger = Join-Path $RepoRoot 'docs/lessons/LEDGER.md'
$LessonsArchive = Join-Path $RepoRoot 'specs/archive/lessons-archive.md'
$OnDemandDir = Join-Path $RepoRoot 'docs/lessons'
$ClaudeMd = Join-Path $RepoRoot 'CLAUDE.md'
$MustCap = $ScaffoldConfig.LessonsMustCap   # 必须层（CLAUDE.md「经验铁律」）条数上限——超限须淘汰最不活跃项回按需层
if ($DryRun -and $Command -ne 'archive') { throw '-DryRun 只适用于 archive 子命令。' }

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# 解析总账为对象数组（按 "## L<n>" 分块）
function Get-LessonsFromPath([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) { return @() }
  $raw = Get-Content -LiteralPath $Path -Raw
  $blocks = [regex]::Split($raw, '(?m)^##\s+(?=L\d)') | Where-Object { $_ -match '^L\d' }
  $out = @()
  foreach ($b in $blocks) {
    $id = ([regex]::Match($b, '^(L\d+)')).Groups[1].Value
    $tier = ([regex]::Match($b, 'tier:\s*(\w+)')).Groups[1].Value
    $sev = ([regex]::Match($b, 'severity:\s*(\w+)')).Groups[1].Value
    $kind = ([regex]::Match($b, 'kind:\s*(\w+)')).Groups[1].Value
    if (-not $kind) { $kind = 'pitfall' }   # 旧条目无 kind 字段 => 默认 pitfall（向后兼容）
    $rec = ([regex]::Match($b, 'recurrence:\s*(\d+)')).Groups[1].Value
    $tags = ([regex]::Match($b, 'tags:\s*([^｜|]+)')).Groups[1].Value.Trim()
    $rule = ([regex]::Match($b, '(?m)^- rule:\s*(.+)$')).Groups[1].Value.Trim()
    $enf = ([regex]::Match($b, '(?m)^- enforced_by:\s*(.+)$')).Groups[1].Value.Trim()
    $cost = ([regex]::Match($b, 'cost:\s*([^｜|]+)')).Groups[1].Value.Trim()   # 可选；旧条目无此字段 => 空（向后兼容）
    $out += [pscustomobject]@{ id = $id; tier = $tier; kind = $kind; severity = $sev; recurrence = [int]($rec | ForEach-Object { if ($_){$_}else{0} }); tags = $tags; rule = $rule; enforced_by = $enf; cost = $cost; body = $b }
  }
  return $out
}

function Get-Lessons { return @(Get-LessonsFromPath $Ledger) }
function Get-ArchivedLessons { return @(Get-LessonsFromPath $LessonsArchive) }
function Get-AllLessons {
  $hot = @(Get-Lessons)
  $cold = @(Get-ArchivedLessons)
  return @($hot + $cold)
}

function Throw-ArchivedLessonReadOnly([string]$Id) {
  if (@(Get-ArchivedLessons | Where-Object id -eq $Id).Count) {
    throw "[LSN-ARCHIVED-READONLY] $Id 位于冷库；如需 bump/promote，请先把该条目整块移回 docs/lessons/LEDGER.md，再执行写操作。"
  }
}

function Next-Id {
  param([array]$Lessons = (Get-Lessons))
  # TD24/TD39: StrictMode 下空集合直接取 .id 会抛异常。裸调用（add 的生产路径）走【默认绑定】=(Get-Lessons)，
  # 空 LEDGER 时 Get-Lessons 返回 @()，经 [array] 参数强制转换 unroll 成 $null → @($null).Count==1、绕过 Count-eq-0
  # 守卫 → $ls.id 抛 PropertyNotFoundStrict（TD24 PR#47 只测了 -Lessons @() 显式绑定路径，漏了此默认绑定路径）。
  # 修法：先滤掉 null 元素再判 Count（@() 包裹单独不够——@($null) 仍 Count 1）。Get-Lessons 不动（改 ,$out 会破坏
  # search/list/check 的直管调用：逗号包裹令整个数组当单个管道项、Where-Object 取不到 .tier 属性）。
  $ls = @($Lessons | Where-Object { $null -ne $_ })
  if ($ls.Count -eq 0) { return 'L1' }
  $ids = $ls.id | ForEach-Object { [int]($_ -replace '\D','') }
  if (-not $ids) { return 'L1' }
  'L' + (($ids | Measure-Object -Maximum).Maximum + 1)
}

# search 的共享谓词：多词 = AND，**所有**词都出现在文本里才命中（Tier2/Tier3 同用；check 对它做确定性自检）
function Test-AllTermsMatch([string]$Text, [string[]]$Terms) {
  return @($Terms | Where-Object { $Text -match [regex]::Escape($_) }).Count -eq $Terms.Count
}

# add 入账过滤边界（add 与 check 自检**共用同一函数**，令自检直测 add 实际走的边界）：
# 判定复用 check-secrets.ps1 的 Find-LineSecret（单一真相源，免双源漂移；TD18）——尽力 early filter，
# 权威闸仍是 check-secrets（ship / pre-push / CI 强制）。命中返回模式名（调用方应拒绝入账），否则 $null。
# -AsLibrary 只定义函数/模式集即返回，不执行其主扫描（坑：直接 dot-source 会跑整套闸且 exit 会杀掉本脚本）。
. (Join-Path $PSScriptRoot 'check-secrets.ps1') -AsLibrary
function Find-EntryBlobSecret([string]$Blob) {
  foreach ($line in ($Blob -split '\r?\n')) {
    $hit = Find-LineSecret $line
    if ($hit) { return $hit }
  }
  return $null
}

switch ($Command) {

  'add' {
    if (-not $Symptom -or -not $Rule) { throw '至少需要 -Symptom 与 -Rule。' }
    # blocking 经验必须声明机械守卫（OpenAI「让同一错误不可复发」——把软提醒升级为确定性守卫，或显式记 none+理由）。
    if ($Severity -eq 'blocking' -and -not $EnforcedBy) {
      throw "blocking 经验须 -EnforcedBy（机械守卫的脚本/闸门路径，如 'scripts/review.ps1'；确无守卫则写 'none（理由）'）。"
    }
    # 密钥过滤 = 尽力 early filter，走顶部定义的入账过滤边界 Find-EntryBlobSecret（check 自检直测同一函数）。
    $blob = "$Symptom $RootCause $Rule $Refs $Tags $EnforcedBy"
    $hit = Find-EntryBlobSecret $blob
    if ($hit) { throw "疑似含 token/密钥（$hit），已拒绝。经验只记工程结论，不记凭据；权威闸 = check-secrets。" }
    $id = Next-Id
    $today = (Get-Date -Format 'yyyy-MM-dd')   # 注：脚本运行时取，非 LLM 编造
    $costMeta = if ($Cost) { " ｜ cost: $Cost" } else { '' }   # 仅当给了 -Cost 才追加，旧条目格式不变（向后兼容）
    $entry = @"

## $id
- date: $today ｜ tags: $Tags ｜ tier: $Tier ｜ kind: $Kind ｜ severity: $Severity ｜ recurrence: 1$costMeta
- symptom: $Symptom
- root_cause: $RootCause
- rule: $Rule
- enforced_by: $EnforcedBy
- refs: $Refs
"@
    Add-Content -Path $Ledger -Value $entry -Encoding utf8
    Write-Host "已追加 $id 到总账（kind=$Kind）。" -ForegroundColor Green
    if ($Severity -eq 'blocking') { Write-Host "  severity=blocking → 满足晋升必须层门槛之一，考虑 promote $id。" -ForegroundColor Yellow }
    if ($Kind -eq 'judgment') { Write-Host "  kind=judgment → 方向/决策类经验：复审时喂 docs\HARNESS-REVIEW.md（检验方向品味），不强制 enforced_by。" -ForegroundColor Yellow }
  }

  'list' {
    $ls = Get-Lessons
    if ($FilterTier) { $ls = $ls | Where-Object tier -eq $FilterTier }
    if ($FilterTag) { $ls = $ls | Where-Object { $_.tags -match [regex]::Escape($FilterTag) } }
    if ($FilterKind) { $ls = $ls | Where-Object kind -eq $FilterKind }
    if (-not $ls) { Write-Host '（无匹配条目）'; break }
    $ls | ForEach-Object {
      $costTag = if ($_.cost) { " 〔成本:$($_.cost)〕" } else { '' }   # 有 cost 才显示（向后兼容）
      "{0,-4} [{1,-8}] {2,-8} sev={3,-8} rec={4}{5} | {6}" -f $_.id, $_.tier, $_.kind, $_.severity, $_.recurrence, $costTag, $_.rule
    }
  }

  'search' {
    if (-not $Query) { throw 'search 需要关键词。' }
    # 多词 = AND：按空白拆词，**所有**词都命中才算（治「codex stdin」两词探针漏 L4 的召回脆弱）；单词查询行为不变。
    $terms = @($Query -split '\s+' | Where-Object { $_ })
    if (-not $terms) { throw 'search 需要关键词。' }
    Step "总账检索：$Query"
    $hit = Get-Lessons | Where-Object { Test-AllTermsMatch $_.body $terms }
    if ($hit) { $hit | ForEach-Object { $costTag = if ($_.cost) { " 〔成本:$($_.cost)〕" } else { '' }; "{0} [{1}]{2} {3}" -f $_.id, $_.tier, $costTag, $_.rule } } else { '  （总账无匹配）' }
    Step "冷归档检索：$Query"
    $coldHit = Get-ArchivedLessons | Where-Object { Test-AllTermsMatch $_.body $terms }
    if ($coldHit) { $coldHit | ForEach-Object { "[archived] {0} [{1}] {2}" -f $_.id, $_.tier, $_.rule } } else { '  （冷归档无匹配）' }
    Step "按需层检索：$Query"
    $files = Get-ChildItem $OnDemandDir -Filter *.md -ErrorAction SilentlyContinue | Where-Object Name -ne 'LEDGER.md'
    $any = $false
    foreach ($f in $files) {
      if (-not (Test-AllTermsMatch (Get-Content $f.FullName -Raw) $terms)) { continue }
      $counts = foreach ($t in $terms) { @(Select-String -Path $f.FullName -Pattern $t -SimpleMatch -ErrorAction SilentlyContinue).Count }
      $any = $true; "  $($f.Name): $(($counts | Measure-Object -Sum).Sum) 处命中"
    }
    if (-not $any) { '  （按需层无匹配）' }
  }

  'check' {
    Step '护栏：必须层封顶 + id 唯一 + 字段完整'
    $ls = Get-AllLessons
    $fail = $false
    # id 唯一
    $dup = $ls | Group-Object id | Where-Object Count -gt 1
    if ($dup) { Write-Warning "重复 id：$($dup.Name -join ', ')"; $fail = $true } else { Write-Host 'id 唯一 ✓' }
    # 必须层封顶（以 CLAUDE.md「经验铁律」实际条数为准）
    # TD39: 零 tier=must 时 Where-Object 发 AutomationNull，直接取 .Count 在 StrictMode 抛——@() 包裹保 Count 0（合法下游态：删净示例 must 经验）。
    $mustInLedger = @($ls | Where-Object tier -eq 'must').Count
    $mustInClaude = 0
    if (Test-Path $ClaudeMd) {
      $cm = Get-Content $ClaudeMd -Raw
      $sec = [regex]::Match($cm, '(?s)## 经验铁律.*?(?=\n## |\z)').Value
      $mustInClaude = ([regex]::Matches($sec, '(?m)^\s*-\s+\*\*')).Count
    }
    Write-Host "必须层：总账标 must=$mustInLedger ｜ CLAUDE.md 铁律条目=$mustInClaude ｜ 上限=$MustCap"
    if ($mustInClaude -gt $MustCap) { Write-Warning "CLAUDE.md 铁律超上限（$mustInClaude>$MustCap）→ 淘汰最不活跃项回按需层。"; $fail = $true }
    # id 存在性：每条 tier=must 的总账经验，其 [Lx] 必须出现在 CLAUDE.md 铁律小节里（单一真相源可机检）
    $claudeSec = ''
    if (Test-Path $ClaudeMd) { $claudeSec = [regex]::Match((Get-Content $ClaudeMd -Raw), '(?s)## 经验铁律.*?(?=\n## |\z)').Value }
    foreach ($m in ($ls | Where-Object tier -eq 'must')) {
      if ($claudeSec -notmatch [regex]::Escape("[$($m.id)]")) {
        Write-Warning "必须层经验 $($m.id) 未在 CLAUDE.md 铁律小节登记（id 缺失）。"; $fail = $true
      }
    }
    # 漂移：CLAUDE.md 铁律里出现的 [Lx]，其总账条目应仍标 tier=must（否则文本/层级漂移）
    foreach ($cm in [regex]::Matches($claudeSec, '\[(L\d+)\]')) {
      $cid = $cm.Groups[1].Value
      $src = $ls | Where-Object id -eq $cid | Select-Object -First 1
      if (-not $src) { Write-Warning "CLAUDE.md 铁律引用了不存在的 $cid（总账已删？漂移）。"; $fail = $true }
      elseif ($src.tier -ne 'must') { Write-Warning "$cid 在 CLAUDE.md 是铁律，但总账标 tier=$($src.tier)（层级漂移，需同步）。"; $fail = $true }
    }
    $TemplateMd = Join-Path $RepoRoot 'CLAUDE.template.md'
    # 两个常驻 CLAUDE 文件可引用已归档经验；定义域是热账本与冷库并集。层级漂移仍只在铁律小节校验。
    $definedIds = @{}; foreach ($lesson in $ls) { $definedIds[$lesson.id] = $true }
    foreach ($residentPath in @($ClaudeMd, $TemplateMd)) {
      if (-not (Test-Path -LiteralPath $residentPath)) { continue }
      foreach ($rm in [regex]::Matches((Get-Content -LiteralPath $residentPath -Raw), '\[(L\d+)\]')) {
        $rid = $rm.Groups[1].Value
        if (-not $definedIds.ContainsKey($rid)) {
          Write-Warning "$(Split-Path $residentPath -Leaf) 引用了不存在的 $rid（热账本/冷库并集均无定义）。"; $fail = $true
        }
      }
    }
    # 模板同步（元仓专用）：CLAUDE.template.md 的铁律节须与总账 tier=must 双向一致——
    #   堵「元仓晋升 must 只改 CLAUDE.md 忘改模板 → 下游 init 首跑 check 即挂」。下游无此文件 => 优雅跳过（空配置规则）。
    if (Test-Path $TemplateMd) {
      $tplSec = [regex]::Match((Get-Content $TemplateMd -Raw), '(?s)## 经验铁律.*?(?=\n## |\z)').Value
      foreach ($m in ($ls | Where-Object tier -eq 'must')) {
        if ($tplSec -notmatch [regex]::Escape("[$($m.id)]")) {
          Write-Warning "必须层经验 $($m.id) 未在 CLAUDE.template.md 铁律小节登记（模板漂移，下游 init 首跑 check 会挂）。"; $fail = $true
        }
      }
      foreach ($tm in [regex]::Matches($tplSec, '\[(L\d+)\]')) {
        $tid = $tm.Groups[1].Value
        $src = $ls | Where-Object id -eq $tid | Select-Object -First 1
        if (-not $src) { Write-Warning "CLAUDE.template.md 铁律引用了不存在的 $tid（总账已删？漂移）。"; $fail = $true }
        elseif ($src.tier -ne 'must') { Write-Warning "$tid 在 CLAUDE.template.md 是铁律，但总账标 tier=$($src.tier)（层级漂移，需同步）。"; $fail = $true }
      }
    }
    # 字段完整
    $bad = $ls | Where-Object { -not $_.rule }
    if ($bad) { Write-Warning "缺 rule 字段：$($bad.id -join ', ')"; $fail = $true } else { Write-Host '字段完整 ✓' }
    # enforced_by：blocking 经验必须声明机械守卫（脚本/闸门路径）或显式 'none（理由）'——
    #   OpenAI《Harness Engineering》「让同一错误不可复发」：把会卡死/返工的坑从「上下文提醒」升级为「确定性守卫」，或至少显式承认无守卫。
    $blkNoEnf = $ls | Where-Object { $_.severity -eq 'blocking' -and -not $_.enforced_by }
    if ($blkNoEnf) { Write-Warning "blocking 经验缺 enforced_by（须填机械守卫路径，或 'none（理由）'）：$($blkNoEnf.id -join ', ')"; $fail = $true } else { Write-Host 'enforced_by 完整（blocking 均已声明守卫）✓' }
    # search 语义自检（确定性 fixture，不依赖总账内容）：多词=AND 全命中、缺一词即不命中、单词行为不变——
    #   守 search 的召回契约（selftest 闸② 借道本命令免费回归）。
    $probeOk = (Test-AllTermsMatch 'alpha beta gamma' @('alpha', 'gamma')) -and
               (-not (Test-AllTermsMatch 'alpha beta' @('alpha', 'zzz'))) -and
               (Test-AllTermsMatch 'alpha beta' @('alpha')) -and
               (-not (Test-AllTermsMatch 'alpha beta' @('zzz')))
    if ($probeOk) { Write-Host 'search 多词 AND 语义自检 ✓' } else { Write-Warning 'search 多词 AND 语义回归（Test-AllTermsMatch 谓词自检失败：AND 全命中/缺词不命中/单词不变 三契约之一被破坏）。'; $fail = $true }
    # add 密钥过滤自检（确定性 fixture，不依赖总账内容）：直测 add 实际调用的入账过滤边界
    #   Find-EntryBlobSecret（同一函数，非只测底层谓词）——假 token 必拦、正常工程文本必过
    #   （判定源 = check-secrets.ps1 Find-LineSecret，TD18；selftest 闸② 借道本命令免费回归）。
    #   fixture 运行时拼接，避免本文件留下可被内容扫描命中的完整 token 字面量。
    $fakeTok = 'ghp_' + ('Ab1Cd2Ef3G' * 4)   # 40 位假 GitHub token（拼接生成，非真凭据）
    $secretProbeOk = [bool](Find-EntryBlobSecret "symptom 正常前缀`nleaked $fakeTok in ship log") -and
                     -not (Find-EntryBlobSecret "worktree 内 gh pr merge 不加 --delete-branch（L13）`n经验只记工程结论")
    if ($secretProbeOk) { Write-Host 'add 密钥过滤自检（假 token 必拦 / 正常文本必过）✓' } else { Write-Warning 'add 密钥过滤回归（Find-EntryBlobSecret：假 token 未拦 或 正常文本被误拦）。'; $fail = $true }
    # Next-Id 空/非空总账自检（合成 fixture，不碰真实 LEDGER；TD24）：StrictMode 下空集合直接取 .id 会抛异常，
    #   空 LEDGER 首次 add 即崩——守空集合返回 'L1' 且不抛异常，非空集合仍正确递增。
    try {
      $nextIdEmptyOk = (Next-Id -Lessons @()) -eq 'L1'
      $nextIdIncrOk = (Next-Id -Lessons @([pscustomobject]@{ id = 'L3' }, [pscustomobject]@{ id = 'L1' })) -eq 'L4'
      $nextIdProbeOk = $nextIdEmptyOk -and $nextIdIncrOk
    } catch { $nextIdProbeOk = $false }
    if ($nextIdProbeOk) { Write-Host 'Next-Id 空/非空总账自检（StrictMode 下不崩、正确递增）✓' } else { Write-Warning 'Next-Id 回归（TD24：空 LEDGER 下 StrictMode 抛异常，或递增计算错误）。'; $fail = $true }
    if (-not $fail) { Write-Host 'id 存在性 + 漂移校验 ✓' }
    if ($fail) { exit 1 } else { Write-Host "`ncheck: PASS" -ForegroundColor Green }
  }

  'bump' {
    # 同一条经验复发一次 → recurrence +1（自净化闭环的「复发计数」入口，避免手改 LEDGER）。
    if (-not $Query) { throw 'bump 需要条目 id（如 L1）。' }
    if (-not (Test-Path $Ledger)) { Throw-ArchivedLessonReadOnly $Query; throw "总账不存在: $Ledger" }
    $raw = Get-Content $Ledger -Raw
    # 抠出该 id 的块（## L<n> 起，到下一个 ## L<n> 或文件尾止）
    $blockRe = "(?ms)^##\s+$([regex]::Escape($Query))\b.*?(?=^##\s+L\d|\z)"
    $m = [regex]::Match($raw, $blockRe)
    if (-not $m.Success) { Throw-ArchivedLessonReadOnly $Query; throw "未找到 $Query。" }
    $block = $m.Value
    $rm = [regex]::Match($block, 'recurrence:\s*(\d+)')
    if (-not $rm.Success) { throw "$Query 块内无 recurrence 字段，无法 bump（检查 LEDGER 格式）。" }
    $old = [int]$rm.Groups[1].Value
    $new = $old + 1
    # TD51/TD-114：原写法 [regex]::Replace($block,'recurrence:\s*\d+',"recurrence: $new",1) 并无
    # (string,string,string,int) 这个重载——第 4 个实参 1 被隐式转成 RegexOptions（1=IgnoreCase），
    # 等价于「大小写不敏感、替换块内所有匹配」，若 body 文本恰好引用了字面量 `recurrence: <digits>`
    # 会被静默篡改。改锚定到本块的 meta 行（`- date: ... recurrence: <n>`），只动该行的计数器，
    # 不触碰 body 里可能出现的同名字面量。`${1}` 用单引号字面量 + 字符串拼接 $new（避免双引号内
    # `${1}`/`$1` 被 PowerShell 当成变量插值、吞掉正则回引用）。
    $newBlock = [regex]::Replace($block, '(?m)^(- date:.*?recurrence:\s*)\d+', ('${1}' + $new))
    $raw = $raw.Remove($m.Index, $m.Length).Insert($m.Index, $newBlock)
    Set-Content -Path $Ledger -Value $raw -Encoding utf8 -NoNewline
    Write-Host "$Query recurrence: $old → $new" -ForegroundColor Green
    if ($new -ge 2) {
      Write-Host "  recurrence≥2 → 满足晋升必须层门槛之一，考虑 promote $Query。" -ForegroundColor Yellow
    }
  }

  'promote' {
    if (-not $Query) { throw 'promote 需要条目 id（如 L1）。' }
    $l = Get-Lessons | Where-Object id -eq $Query | Select-Object -First 1
    if (-not $l) { Throw-ArchivedLessonReadOnly $Query; throw "未找到 $Query。" }
    Step "晋升评估：$Query"
    "当前 tier=$($l.tier) kind=$($l.kind) severity=$($l.severity) recurrence=$($l.recurrence)"
    if ($l.kind -eq 'judgment') { Write-Host '  注：judgment（方向/决策）类——晋升的同时把它登记进 docs\HARNESS-REVIEW.md 的 judgment-feed，随模型变强复审方向品味。' -ForegroundColor DarkCyan }
    $qualMust = ($l.severity -eq 'blocking') -or ($l.recurrence -ge 2)
    if ($qualMust) {
      Write-Host '✓ 够格进**必须层**（blocking 或 复发≥2）。' -ForegroundColor Green
      Write-Host "  操作：把一行结论加到 CLAUDE.md「## 经验铁律（必须加载）」，并把本条 tier 改为 must；随后 lessons.ps1 check 校验未超上限（$MustCap）。"
    } else {
      Write-Host '→ 暂不够必须层；建议进**按需层**：写入对应 docs/lessons/<topic>.md，tier 改 ondemand。' -ForegroundColor Yellow
    }
  }

  'archive' {
    $hot = @(Get-Lessons)
    $maxNumber = -1
    foreach ($lesson in $hot) {
      $number = [int]($lesson.id.Substring(1))
      if ($number -gt $maxNumber) { $maxNumber = $number }
    }
    $referenced = @{}
    foreach ($residentPath in @($ClaudeMd, (Join-Path $RepoRoot 'CLAUDE.template.md'))) {
      if (-not (Test-Path -LiteralPath $residentPath)) { continue }
      foreach ($match in [regex]::Matches((Get-Content -LiteralPath $residentPath -Raw), '\[(L\d+)\]')) {
        $referenced[$match.Groups[1].Value] = $true
      }
    }
    $candidates = @($hot | Where-Object {
      $_.tier -eq 'ledger' -and $_.recurrence -eq 1 -and
      [int]($_.id.Substring(1)) -ne $maxNumber -and -not $referenced.ContainsKey($_.id)
    })
    $candidateText = if ($candidates.Count) { ($candidates.id -join ',') } else { 'none' }
    if ($DryRun) {
      Write-Host "[LSN-ARCHIVE-DRYRUN] candidates=$candidateText"
      break
    }
    Write-Host "[LSN-ARCHIVE] candidates=$candidateText"
    if (-not $candidates.Count) { break }
    $archiveScript = Join-Path $PSScriptRoot 'archive.ps1'
    & pwsh -NoProfile -File $archiveScript -RepoRoot $RepoRoot -LessonsOnly -LessonIds ($candidates.id -join ',') -Quiet
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
}
