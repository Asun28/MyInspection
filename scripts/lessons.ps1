#requires -Version 7
<#
.SYNOPSIS
  自净化经验系统的编排器（Tier1 必须 / Tier2 按需 / Tier3 总账）。
  让工作流"吸取经验、自我进步"：捕获→检索→晋升→提纯，遇到同样问题不再重导。

.DESCRIPTION
  三层与单向学习流（详见 docs/LESSONS.md）：
    Tier3 总账   docs/lessons/LEDGER.md            —— 唯一真相源，append-only
    Tier2 按需   docs/lessons/<topic>.md           —— lessons skill 按上下文触发
    Tier1 必须   CLAUDE.md「## 经验铁律（必须加载）」—— 每轮自动入上下文，**封顶**
  方向：会话级（progress.md / claude-mem）─捕获→ 总账 ─晋升→ 按需 / 必须。单向。

  子命令：
    add      追加一条经验到总账（低摩擦捕获）。
    list     列出总账条目（可按 tier/tag/kind 过滤）。
    search   按关键词检索总账 + 按需层（recall）。
    check    护栏：校验必须层**驻留经验 id 数**未超上限（非条目数）、id 无重复、字段完整、enforced_by 形态可认。
    promote  打印某条的晋升建议（是否够格进必须/按需层 + 操作提示）。
    bump     某条经验复发一次 → recurrence +1（复发计数入口，跨过 2 即提示 promote）。

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
  [Parameter(Position = 0)][ValidateSet('add', 'list', 'search', 'check', 'promote', 'bump')][string]$Command = 'list',
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
  [ValidateSet('pitfall', 'judgment')][string]$FilterKind
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot '_config.ps1')
. (Join-Path $PSScriptRoot '_lessons.ps1')   # 必须层驻留规则的共享判定核（上游 v0.43.0 #188/#189）
$Ledger = Join-Path $RepoRoot 'docs/lessons/LEDGER.md'
$OnDemandDir = Join-Path $RepoRoot 'docs/lessons'
$ClaudeMd = Join-Path $RepoRoot 'CLAUDE.md'
$MustCap = $ScaffoldConfig.LessonsMustCap   # 必须层（CLAUDE.md「经验铁律」）**驻留经验 id** 数上限（非条目数）——超限须淘汰最不活跃项回按需层

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# 解析总账为对象数组（按 "## L<n>" 分块）
function Get-Lessons {
  if (-not (Test-Path $Ledger)) { return @() }
  $raw = Get-Content $Ledger -Raw
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
    $enf = Get-ScaffoldLessonEnforcedBy $b   # 空字段不再吃掉下一行（旧式 \s* + (.+) 的 fail-open，见 _lessons.ps1 头注）
    $cost = ([regex]::Match($b, 'cost:\s*([^｜|]+)')).Groups[1].Value.Trim()   # 可选；旧条目无此字段 => 空（向后兼容）
    $out += [pscustomobject]@{ id = $id; tier = $tier; kind = $kind; severity = $sev; recurrence = [int]($rec | ForEach-Object { if ($_){$_}else{0} }); tags = $tags; rule = $rule; enforced_by = $enf; cost = $cost; body = $b }
  }
  return $out
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
    $ls = Get-Lessons
    $fail = $false
    # id 唯一
    $dup = $ls | Group-Object id | Where-Object Count -gt 1
    if ($dup) { Write-Warning "重复 id：$($dup.Name -join ', ')"; $fail = $true } else { Write-Host 'id 唯一 ✓' }
    # 必须层封顶（以 CLAUDE.md「经验铁律」实际驻留的**经验 id 数**为准，非 markdown 条目数）
    # TD39: 零 tier=must 时 Where-Object 发 AutomationNull，直接取 .Count 在 StrictMode 抛——@() 包裹保 Count 0（合法下游态：删净示例 must 经验）。
    $mustInLedger = @($ls | Where-Object tier -eq 'must').Count
    # 计量单位是**驻留的经验 id**，不是 markdown 条目：一条写着 [L17][L162][L172][L177] 的 bullet
    # 对计数器是 1 条、对模型是 4 条规则，封顶要管的正是后者（上游 issue #184 / 修复 #188）。
    # 本项目实测（origin/master 9c1f98d）：10 条 bullet 承载 19 个 id——按条目计恒绿，而每轮驻留
    # 上下文已是上限的近两倍，这正是「封顶通过了但成本还在涨」的静默失效面。
    # 「小节」只有一个定义 = 判定核返回的 Ids：封顶、id 存在性、层级漂移三处同读一份集合。此前封顶数
    # bullet、后两者正则扫整段文本，于是只写在小节引言 blockquote 里的 must 经验「登记了却不计费」。
    $mustSec = Get-ScaffoldMustLayerSection -Path $ClaudeMd
    if (-not $mustSec.Found -and $mustSec.Reason -eq 'HEADING-NOT-FOUND') {
      # fail-closed：静默返回 0 条会让封顶恒绿——「测不出」绝不能读成「没超」。
      Write-Warning "$($mustSec.Sentinel) CLAUDE.md 存在但找不到「经验铁律」小节（标题漂移？）——封顶无从计量，拒绝放行。"
      $fail = $true
    }
    $mustBullets = @($mustSec.Bullets)
    $mustIds = @($mustSec.Ids)
    $mustInClaude = $mustIds.Count
    $mergedBullets = @($mustBullets | Where-Object IdCount -gt 1)
    Write-Host "必须层：总账标 must=$mustInLedger ｜ CLAUDE.md 驻留 id=$mustInClaude（承载于 $($mustBullets.Count) 条目）｜ 上限=$MustCap"
    if ($mergedBullets.Count -gt 0) {
      Write-Host "  合并条目（一条承载多个 id，按 id 计）：$((($mergedBullets | ForEach-Object { $_.Ids -join '+' }) -join ' ｜ '))" -ForegroundColor DarkGray
    }
    if ($mustInClaude -gt $MustCap) { Write-Warning "CLAUDE.md 铁律超上限（驻留 id $mustInClaude>$MustCap）→ 淘汰最不活跃项回按需层。当前驻留：$(($mustIds -join ', '))"; $fail = $true }
    # id 存在性：每条 tier=must 的总账经验，其 [Lx] 必须**作为驻留条目**出现在铁律小节里（写进引言散文不算）
    foreach ($m in ($ls | Where-Object tier -eq 'must')) {
      if ($mustIds -notcontains $m.id) {
        Write-Warning "必须层经验 $($m.id) 未在 CLAUDE.md 铁律小节登记为驻留条目（id 缺失，或只写在小节散文里）。"; $fail = $true
      }
    }
    # 漂移：CLAUDE.md 铁律驻留的 [Lx]，其总账条目应仍标 tier=must（否则文本/层级漂移）
    foreach ($cid in $mustIds) {
      $src = $ls | Where-Object id -eq $cid | Select-Object -First 1
      if (-not $src) { Write-Warning "CLAUDE.md 铁律引用了不存在的 $cid（总账已删？漂移）。"; $fail = $true }
      elseif ($src.tier -ne 'must') { Write-Warning "$cid 在 CLAUDE.md 是铁律，但总账标 tier=$($src.tier)（层级漂移，需同步）。"; $fail = $true }
    }
    # 模板同步（元仓专用）：CLAUDE.template.md 的铁律节须与总账 tier=must 双向一致——
    #   堵「元仓晋升 must 只改 CLAUDE.md 忘改模板 → 下游 init 首跑 check 即挂」。下游无此文件 => 优雅跳过（空配置规则）。
    $TemplateMd = Join-Path $RepoRoot 'CLAUDE.template.md'
    if (Test-Path $TemplateMd) {
      # 同一枚判定核，同一份「小节」定义（本卡 forbid：不得有第二处「驻留 id 怎么数」的实现）。
      $tplSec = Get-ScaffoldMustLayerSection -Path $TemplateMd
      if (-not $tplSec.Found) {
        Write-Warning "$($tplSec.Sentinel) CLAUDE.template.md 存在但找不到「经验铁律」小节（模板标题漂移）——模板同步无从校验，拒绝放行。"; $fail = $true
      }
      $tplIds = @($tplSec.Ids)
      foreach ($m in ($ls | Where-Object tier -eq 'must')) {
        if ($tplIds -notcontains $m.id) {
          Write-Warning "必须层经验 $($m.id) 未在 CLAUDE.template.md 铁律小节登记（模板漂移，下游 init 首跑 check 会挂）。"; $fail = $true
        }
      }
      foreach ($tid in $tplIds) {
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
    # enforced_by **形态**可认（fail-closed）：非空、又既不是 'none（理由）' 也不是认得出的守卫引用的取值
    #   （TODO / N/A / 待补 / 见 PR 讨论）是**冒充了一次声明**——它让上面那道「blocking 必填」闸满意，
    #   于是这条经验此后既不会被追问、也不再被晋升探针提名。故在入口直接拒：写真守卫，或写 none（理由）。
    $badEnf = @($ls | Where-Object { -not (Test-ScaffoldLessonEnforcedByWellFormed $_.enforced_by) })
    if ($badEnf.Count) { Write-Warning "enforced_by 取值认不出是机械守卫（占位符一律拒绝；请写脚本路径 / 闸编号，或写 'none（理由）'）：$(($badEnf | ForEach-Object { "$($_.id)=$($_.enforced_by)" }) -join ' ｜ ')"; $fail = $true } else { Write-Host 'enforced_by 形态可认（非空取值皆为守卫引用或 none（理由））✓' }
    # enforced_by 判据自检（确定性 fixture，不依赖总账内容）：守卫判定必须**双向**成立且对未知取值 fail-closed——
    #   真脚本路径/闸编号判有守卫；none（理由）、空字段、以及 TODO/N/A/待补 这类占位符一律判**无**守卫。
    #   少了占位符那一段，「已有守卫」就能被一个待办事项冒充（上游 issue #183 的反面）。
    $guardProbeOk = (Test-ScaffoldLessonGuarded 'scripts/review.ps1') -and
                    (Test-ScaffoldLessonGuarded 'selftest 闸 10g（大小写负夹具）') -and
                    -not (Test-ScaffoldLessonGuarded 'none（本条只能靠人）') -and
                    -not (Test-ScaffoldLessonGuarded '') -and
                    -not (Test-ScaffoldLessonGuarded 'TODO') -and
                    -not (Test-ScaffoldLessonGuarded 'N/A') -and
                    -not (Test-ScaffoldLessonGuarded '待补') -and
                    -not (Test-ScaffoldLessonEnforcedByWellFormed 'TODO') -and
                    (Test-ScaffoldLessonEnforcedByWellFormed '') -and
                    (Test-ScaffoldLessonEnforcedByWellFormed 'none（理由）')
    if ($guardProbeOk) { Write-Host 'enforced_by 守卫判定自检（真引用/none/空/占位符 四类各判对）✓' } else { Write-Warning 'enforced_by 守卫判定回归（Test-ScaffoldLessonGuarded：真引用未判有守卫，或 none/空/占位符被误判为已有守卫——fail-open 方向）。'; $fail = $true }
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
    if (-not (Test-Path $Ledger)) { throw "总账不存在: $Ledger" }
    $raw = Get-Content $Ledger -Raw
    # 抠出该 id 的块（## L<n> 起，到下一个 ## L<n> 或文件尾止）
    $blockRe = "(?ms)^##\s+$([regex]::Escape($Query))\b.*?(?=^##\s+L\d|\z)"
    $m = [regex]::Match($raw, $blockRe)
    if (-not $m.Success) { throw "未找到 $Query。" }
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
    if (-not $l) { throw "未找到 $Query。" }
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
}
