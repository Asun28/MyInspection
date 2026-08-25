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
    check    护栏：校验必须层**驻留经验 id 数**未超上限（非条目数）、id 无重复、字段完整、enforced_by 形态可认。
    promote  打印某条的晋升建议（是否够格进必须/按需层 + 操作提示）。
    bump     某条经验复发一次 → recurrence +1（复发计数入口，跨过 2 即提示 promote）。
    archive  选择一次性 ledger 经验，预览或交给 archive.ps1 冷存（选择规则的权威表述见 docs/LESSONS.md §3 PURIFY）。

  两类经验（-Kind，正交于 tier/severity）：
    pitfall  （默认）**工具链/方法的坑**（怎么干活踩雷）——可升级为机械守卫（enforced_by）。
    judgment **方向/决策的失手**（Anthropic《Recursive Self-Improvement》的 judgment scaffolding）：
             记「当时选了次优方向、更好的下一步是什么」。判断型启发式难机械执法，故喂 docs/HARNESS-REVIEW.md
             复审、随模型变强检验方向品味是否提升，而非 enforced_by。symptom=情境 / root_cause=为何选错 / rule=更好的启发式。

  安全：只记工程结论。add 的密钥过滤是**尽力而为的 early filter**（判定复用 check-secrets.ps1 的
  Find-LineSecret，单一真相源）；**权威闸是 check-secrets**（ship / pre-push / CI 强制），
  PII 无机检——由作者入账前自查。

.PARAMETER RepoRoot  仓库根（默认由脚本位置派生）；hermetic 夹具与跨仓调用用它指定别的仓。
.PARAMETER DryRun    仅 archive 可用：预览候选、写零文件。预览与实跑走同一搬运器（`archive.ps1`，只差 `-DryRun`），
                     故它的拒绝在预览里同样出现、同样非零退出。
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
. (Join-Path $PSScriptRoot '_lessons.ps1')   # 必须层驻留规则的共享判定核（上游 v0.43.0 #188/#189）
$Ledger = Join-Path $RepoRoot 'docs/lessons/LEDGER.md'
$LessonsArchive = Join-Path $RepoRoot 'specs/archive/lessons-archive.md'
$OnDemandDir = Join-Path $RepoRoot 'docs/lessons'
$ClaudeMd = Join-Path $RepoRoot 'CLAUDE.md'
$TemplateMd = Join-Path $RepoRoot 'CLAUDE.template.md'
$MustCap = $ScaffoldConfig.LessonsMustCap   # 必须层（CLAUDE.md「经验铁律」）**驻留经验 id** 数上限（非条目数）——超限须淘汰最不活跃项回按需层
if ($DryRun -and $Command -ne 'archive') { throw '-DryRun 只适用于 archive 子命令。' }

# 「常驻 CLAUDE 文件引用了某条经验」的**唯一真相源**：前不接 ASCII 字母/数字/冒号（排除 path:L88 行号），
# 后不接 -<digit>（排除 L52-71 行段）。别退回 '\[(L\d+)\]'——仓里的引用绝大多数**裸写**（`见 L26 之理`），
# 只认方括号会把常驻文件正在引用的条目搬进冷库，而事后无闸变红（闸 16 按热∪冷判，冷项照样算已定义）。
# **下面这一行是该判定式在全仓的唯一字面量**：selftest.ps1 闸 16 的 Get-LessonReferenceIdSet 不再另抄一份，
# 而是从本文件源码里把它抽出来复用（抽不到即 fail-closed 变红），故不存在「两份副本各自漂移」这回事；
# 闸 2g(a2) 另断言选择器排除面与闸 16 从同一份 CLAUDE.md 推出同一 id 集合（证明选择器真用了它）。
# 改本行时请一并保持 `$LessonRefRegex = '<模式>'` 这个单引号单行赋值形态——抽取按此形态锚定。
# 范围简写只保护两端点（`L229–L232` 不保护 L230/L231），见 docs/LESSONS.md §3。
$LessonRefRegex = '(?<![A-Za-z0-9:])L(\d+)\b(?!-\d)'
function Get-ResidentLessonRefs {
  # id → 引用它的常驻文件名（check 要文件名做诊断，archive 只用 ContainsKey）。
  $refs = @{}
  foreach ($residentPath in @($ClaudeMd, $TemplateMd)) {
    if (-not (Test-Path -LiteralPath $residentPath)) { continue }
    $leaf = Split-Path $residentPath -Leaf
    foreach ($m in [regex]::Matches((Get-Content -LiteralPath $residentPath -Raw), $LessonRefRegex)) {
      $rid = "L$($m.Groups[1].Value)"
      if (-not $refs.ContainsKey($rid)) { $refs[$rid] = [System.Collections.Generic.List[string]]::new() }
      if (-not $refs[$rid].Contains($leaf)) { $refs[$rid].Add($leaf) }
    }
  }
  return $refs
}

# id 的数值形态：下游（本文件的最高-id 比较、archive.ps1 的 Get-LedgerHeadings）都要 [int] 它，超 Int32 会抛
# 裸 .NET 异常、令 list/search/check 因一条坏条目全体不可用。不可解析返回 -1，调用方按 [LSN-META-INVALID] 处理。
function Get-LessonNumber([string]$Id) {
  # 位数上界曾写 \d{1,9}：那是拿位数当 Int32 范围的代理，两头都不对——1000000000..2147483647 是合法 Int32
  # 却被拒，而 bump 又能写出 10 位值让下一次读判非法。改用 TryParse，判据**就是** Int32 范围本身。
  if ($Id -notmatch '^L(\d+)$') { return -1 }
  $n = 0
  if (-not [int]::TryParse($Matches[1], [ref]$n)) { return -1 }
  return $n
}

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# bump 专用的账本平面（T0-LESSONS-BUMP-PLANE）：recurrence 是**仓库级元数据**，与任何卡片无关。
# 绑在 $PSScriptRoot/.. 时，在 linked worktree 里跑 bump 会把它写进**卡片分支**的 LEDGER.md，被范围闸与
# R3 #7（夹带无关改动）正确拦下 —— 计数遂无处可去。且丢失是结构性的：卡片工作全在 worktree 里做。
# 故 bump 一律解析到**主检出**。只有 bump 走这条平面：add 的新经验属有意随卡入库（L241 即先例），
# promote 只打印建议、不写盘。
function Resolve-BumpLedger {
  param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string]$Fallback)
  # 继承来的 GIT_DIR / GIT_COMMON_DIR / GIT_WORK_TREE 会盖过 -C，把解析劫持到**另一个仓库**——
  # git 执行 hook 时本就会设 GIT_DIR，届时计数会静默加到别的仓库的账本上。本函数语义是「$Root 所属仓库
  # 的主检出」，故先清掉这三个再问 git（清用 Remove-Item，赋空串仍算「已设置」，同 L3）。
  # 已知限制（R3 预审 #2）：若本仓被当作 submodule 使用，--git-common-dir 返回 <super>/.git/modules/<path>，
  # 其父级并非检出根，此处会 fail-closed 报错而非误写。本仓不以 submodule 形式使用，按「宁停勿猜」处理。
  $gitEnvNames = @('GIT_DIR', 'GIT_COMMON_DIR', 'GIT_WORK_TREE')
  $gitEnvSaved = @{}
  foreach ($n in $gitEnvNames) {
    $gitEnvSaved[$n] = [Environment]::GetEnvironmentVariable($n)
    if ($null -ne $gitEnvSaved[$n]) { Remove-Item "Env:$n" -ErrorAction SilentlyContinue }
  }
  $gcd = ''
  $rc = 1
  try {
    $gcd = "$(& git -C $Root rev-parse --git-common-dir 2>$null)".Trim()
    # 退出码必须**紧接着**取：_encoding.ps1 置 $PSNativeCommandUseErrorActionPreference = $false，原生非零
    # **不抛**，try/catch 只兜得住「git 根本不在 PATH」。只判 stdout 空会漏掉「git 失败却往 stdout 吐了个
    # 看似路径的东西」——那会把账本解析到一个不存在的检出上，正好是本函数最不该犯的错。
    $rc = $LASTEXITCODE
  }
  catch { $gcd = ''; $rc = 1 }
  finally {
    foreach ($n in $gitEnvNames) { if ($null -ne $gitEnvSaved[$n]) { Set-Item "Env:$n" $gitEnvSaved[$n] } }
  }
  # 非零退出**或**空输出 → 回落调用方检出（卡片契约原文）。这一步必需：selftest 闸 2b/2c 的 hermetic
  # 夹具是「只拷 scripts/ 的非 git 临时目录」，此处硬失败会把两枚既有闸打红。
  if ($rc -ne 0 -or -not $gcd) { return $Fallback }
  # 主检出里 git 返回**相对**的 `.git`（Split-Path 取父级得空串）；linked worktree 里才返回主仓 .git 的绝对
  # 路径。故先相对 $Root 解析成绝对路径，两种形态才走同一条路。
  $gitDir = if ([System.IO.Path]::IsPathRooted($gcd)) { $gcd } else { Join-Path $Root $gcd }
  $mainCheckout = Split-Path ([System.IO.Path]::GetFullPath($gitDir)) -Parent
  $candidate = Join-Path $mainCheckout 'docs/lessons/LEDGER.md'
  # fail-closed：解析到了主检出却没有账本，就停下报错。**绝不**回落到当前检出那份 —— 那正是本函数要根治的
  # 形态（计数悄悄写进卡片分支，再被范围闸吞掉）。
  if (-not (Test-Path $candidate)) {
    throw "[LSN-PLANE-UNRESOLVED] 主检出账本不存在：$candidate。recurrence 是仓库级元数据，bump 只写主检出；请确认该检出完好，或直接从主检出跑 bump。此处不回落到当前检出的账本。"
  }
  return $candidate
}

# 解析总账为对象数组（按 "## L<n>" 分块）
# 规范 meta 行 = 块内**唯一**一条以 `- date:` 开头的行，字段以全角｜或半角| 分隔、形如 `key: value`。
# 元数据只有这一个出生点：正文 prose 里写的 `tier: ledger` / `recurrence: 1` 一律不作数。
# 这不是洁癖——archive 是**搬运数据**的动作，靠不锚定的正则从整块里捞字段，等于让任意一句叙述文本
# 决定某条经验会不会被移出热账本。缺失/重复/非法一律 Ok=$false，由调用方 fail-closed（[LSN-META-INVALID]）。
function Get-LessonMeta([string]$Block) {
  $metaLines = @(($Block -split '\r?\n') | Where-Object { $_ -match '^-\s+date:' })
  if ($metaLines.Count -ne 1) {
    return [pscustomobject]@{ Ok = $false; Error = "规范 meta 行（以 '- date:' 开头）应恰好 1 条，实得 $($metaLines.Count)"; Fields = @{} }
  }
  $fields = @{}
  foreach ($seg in (($metaLines[0] -replace '^-\s+', '') -split '[｜|]')) {
    if ($seg -notmatch '^\s*(?<k>[a-z_]+)\s*:\s*(?<v>.*?)\s*$') { continue }
    $k = $Matches['k']
    if ($fields.ContainsKey($k)) {
      return [pscustomobject]@{ Ok = $false; Error = "规范 meta 行字段 '$k' 重复"; Fields = @{} }
    }
    $fields[$k] = $Matches['v']
  }
  # 不列 date：meta 行的定义就是「以 `- date:` 开头的那一行」，该字段结构上必然存在，列了也没有输入能触到。
  # 另三项即便漏列，下面的值校验也会因取到 $null（StrictMode 下缺失键不抛）而照判非法——所以本表若只报
  # 「不合法」，删掉任何一项都没人变红（实测如此）。故用 ASCII 哨兵 `missing-field=<名>` 把「缺失」与
  # 「值非法」报成两回事：既是可操作诊断（补字段 vs 改值），也让闸 2i(b2) 逐项断言本表真在起作用。
  foreach ($required in @('tags', 'tier', 'severity', 'recurrence')) {
    if (-not $fields.ContainsKey($required)) {
      return [pscustomobject]@{ Ok = $false; Error = "missing-field=$required（规范 meta 行缺少必填字段）"; Fields = @{} }
    }
  }
  if ($fields['date'] -notmatch '^\d{4}-\d{2}-\d{2}$') { return [pscustomobject]@{ Ok = $false; Error = "date 非法：'$($fields['date'])'"; Fields = @{} } }
  if ($fields['tier'] -notin @('must', 'ondemand', 'ledger')) { return [pscustomobject]@{ Ok = $false; Error = "tier 非法：'$($fields['tier'])'"; Fields = @{} } }
  if ($fields['severity'] -notin @('blocking', 'major', 'minor')) { return [pscustomobject]@{ Ok = $false; Error = "severity 非法：'$($fields['severity'])'"; Fields = @{} } }
  # 判据是 **Int32 范围本身**（TryParse），不是位数：下面会 [int] 它，超 Int32 在 $ErrorActionPreference='Stop' 下是终止性异常——一条坏条目就让
  # list/search/check/archive 全体抛裸 .NET 消息，连「先 search 查经验」这个入口都没了。越界归本条 fail-closed。
  # 同 Get-LessonNumber：位数不是 Int32 范围的等价判据。TryParse 既拦溢出，又不误杀合法的 10 位值。
  $recNum = 0
  if (($fields['recurrence'] -notmatch '^\d+$') -or (-not [int]::TryParse($fields['recurrence'], [ref]$recNum))) { return [pscustomobject]@{ Ok = $false; Error = "recurrence 非法：'$($fields['recurrence'])'"; Fields = @{} } }
  if ($fields.ContainsKey('kind') -and $fields['kind'] -notin @('pitfall', 'judgment')) { return [pscustomobject]@{ Ok = $false; Error = "kind 非法：'$($fields['kind'])'"; Fields = @{} } }
  return [pscustomobject]@{ Ok = $true; Error = ''; Fields = $fields }
}

function Get-LessonsFromPath([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) { return @() }
  $raw = Get-Content -LiteralPath $Path -Raw
  # 起点只认整行 `## L<n>`；终点与 archive.ps1 一致，是下一个任意 `## ` 标题。否则 Appendix 下的
  # meta/rule 会越界补全前一条 lesson，而搬运器实际只移动 Appendix 之前的半块。
  $blocks = [regex]::Matches($raw, '(?ms)^##[ \t]+(?<id>L\d+)[ \t]*\r?$.*?(?=^##[ \t]+|\z)')
  $out = @()
  foreach ($blockMatch in $blocks) {
    $b = $blockMatch.Value
    $id = $blockMatch.Groups['id'].Value
    $meta = Get-LessonMeta $b
    if ((Get-LessonNumber $id) -lt 0) {
      $meta = [pscustomobject]@{ Ok = $false; Error = "id '$id' 数值超出 Int32 可解析范围"; Fields = @{} }
    }
    $f = $meta.Fields
    # meta 行不合法时**不猜**：字段留空/0，metaOk=$false 让下游自己决定 fail-closed 的方式。
    $tier = if ($meta.Ok) { $f['tier'] } else { '' }
    $sev  = if ($meta.Ok) { $f['severity'] } else { '' }
    # kind 只在 meta 合法时才回落到 'pitfall'（旧条目兼容）；meta 读不出来时回落等于**猜**，而猜出的具体值
    # 会被 list/promote 当事实显示，可能正好与真相相反。
    $kind = if ($meta.Ok) { if ($f.ContainsKey('kind')) { $f['kind'] } else { 'pitfall' } } else { '' }
    $rec  = if ($meta.Ok) { [int]$f['recurrence'] } else { 0 }
    $tags = if ($meta.Ok) { $f['tags'] } else { '' }
    $cost = if ($meta.Ok -and $f.ContainsKey('cost')) { $f['cost'] } else { '' }          # 可选；旧条目无此字段 => 空（向后兼容）
    # 只允许水平空白，值也不得跨行；\s* 会吞换行，让空值借用下一条正文而假装完整。
    $rule = ([regex]::Match($b, '(?m)^- rule:[ \t]*([^\r\n]+)[ \t]*\r?$')).Groups[1].Value.Trim()
    $enf = Get-ScaffoldLessonEnforcedBy $b
    $out += [pscustomobject]@{ id = $id; tier = $tier; kind = $kind; severity = $sev; recurrence = $rec; tags = $tags; rule = $rule; enforced_by = $enf; cost = $cost; metaOk = $meta.Ok; metaError = $meta.Error; body = $b }
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

# `check` 与会移动数据的 `archive` 共用同一组条目不变量；选择器不得把 metaOk 误当成完整有效。
function Get-LessonInvariantErrors($Lesson) {
  $errors = [System.Collections.Generic.List[string]]::new()
  if (-not $Lesson.metaOk) { $errors.Add('meta-invalid') }
  if (-not $Lesson.rule) { $errors.Add('missing-rule') }
  if ($Lesson.metaOk -and $Lesson.severity -eq 'blocking' -and -not $Lesson.enforced_by) {
    $errors.Add('blocking-missing-enforced-by')
  }
  return $errors.ToArray()
}

function Throw-ArchivedLessonReadOnly([string]$Id, [string]$ArchivePath = $LessonsArchive) {
  if (@(Get-LessonsFromPath $ArchivePath | Where-Object id -eq $Id).Count) {
    $restoreRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $ArchivePath))
    throw "[LSN-ARCHIVED-READONLY] $Id 位于冷库；请先运行 pwsh -File scripts/archive.ps1 -RepoRoot `"$restoreRoot`" -LessonsOnly -RestoreLessonIds $Id，再执行 bump/promote。"
  }
}

function Next-Id {
  # 默认绑定取热∪冷：单调性此前靠「热永远保有全局最大 id」这条不变量，而它又由两处**各自算**的最高-id
  # 排除（本文件选择器 + archive.ps1 的 $maxId）共同维持，改动任一处即撞号。取并集最大，让单调性成为结构事实。
  param([array]$Lessons = (Get-AllLessons))
  # TD24/TD39: StrictMode 下空集合直接取 .id 会抛异常。裸调用（add 的生产路径）走【默认绑定】=(Get-AllLessons)，
  # 空账本时它返回 @()，经 [array] 参数强制转换 unroll 成 $null → @($null).Count==1、绕过 Count-eq-0
  # 守卫 → $ls.id 抛 PropertyNotFoundStrict（TD24 PR#47 只测了 -Lessons @() 显式绑定路径，漏了此默认绑定路径）。
  # 修法：先滤掉 null 元素再判 Count（@() 包裹单独不够——@($null) 仍 Count 1）。Get-Lessons 不动（改 ,$out 会破坏
  # search/list/check 的直管调用：逗号包裹令整个数组当单个管道项、Where-Object 取不到 .tier 属性）。
  $ls = @($Lessons | Where-Object { $null -ne $_ })
  if ($ls.Count -eq 0) { return 'L1' }
  $ids = @()
  foreach ($entry in $ls) {
    $n = Get-LessonNumber $entry.id
    # 铸新号前不许有读不出的 id：静默跳过会让新条目撞上那条坏 id 的号——单一真相源出现重号比抛错更糟。
    if ($n -lt 0) { throw "[LSN-META-INVALID] 无法从『$($entry.id)』解析条目编号，拒绝铸新 id（先修好该条目的「## L<n>」标题）。" }
    $ids += $n
  }
  if (-not $ids) { return 'L1' }
  $maxId = [int](($ids | Measure-Object -Maximum).Maximum)
  if ($maxId -ge [int]::MaxValue) {
    throw "[LSN-ID-EXHAUSTED] lesson id 已达 Int32 上限（L$maxId），无法铸造下一枚可被读取的 id；未写入任何字节。"
  }
  'L' + ($maxId + 1)
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
    # meta 读不出来的条目**不显示占位默认值**：空 tier/severity 与 rec=0 会被当成事实读。显式打不可用标记。
    $ls | ForEach-Object {
      if (-not $_.metaOk) { "{0,-4} [LSN-META-INVALID] {1}" -f $_.id, $_.metaError; return }
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
    # 同 list：metaOk=$false 时不渲染猜出来的 tier/cost，打不可用标记（召回本身仍照常给出 rule）。
    if ($hit) { $hit | ForEach-Object { if (-not $_.metaOk) { "{0} [LSN-META-INVALID] {1}" -f $_.id, $_.rule; return }; $costTag = if ($_.cost) { " 〔成本:$($_.cost)〕" } else { '' }; "{0} [{1}]{2} {3}" -f $_.id, $_.tier, $costTag, $_.rule } } else { '  （总账无匹配）' }
    Step "冷归档检索：$Query"
    $coldHit = Get-ArchivedLessons | Where-Object { Test-AllTermsMatch $_.body $terms }
    if ($coldHit) { $coldHit | ForEach-Object { $tierTag = if ($_.metaOk) { $_.tier } else { 'LSN-META-INVALID' }; "[archived] {0} [{1}] {2}" -f $_.id, $tierTag, $_.rule } } else { '  （冷归档无匹配）' }
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
    # 规范 meta 行完整性：先于其余校验，因为下面每一条判定（tier 漂移、must 封顶、晋升门槛）都读这些字段。
    # 元数据读不出来就不能假装读出了默认值——那正是「正文诱饵冒充元数据」的入口。
    $validation = @($ls | ForEach-Object { [pscustomobject]@{ Lesson = $_; Errors = @(Get-LessonInvariantErrors $_) } })
    $badMeta = @($validation | Where-Object { $_.Errors -contains 'meta-invalid' })
    foreach ($bm in $badMeta) { Write-Warning "[LSN-META-INVALID] $($bm.Lesson.id) 的规范 meta 行不合法：$($bm.Lesson.metaError)" }
    if ($badMeta.Count) { $fail = $true } else { Write-Host '规范 meta 行 ✓' }
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
    if (-not $mustSec.Found -and $mustSec.Reason -ne 'FILE-MISSING') {
      # fail-closed：标题漂移或重复驻留都会让 cap 计量失真；「测不准」绝不能读成「没超」。
      $detail = if ($mustSec.Reason -eq 'DUPLICATE-RESIDENT-ID') { "重复驻留 id：$(@($mustSec.DuplicateIds) -join ', ')" } else { '找不到「经验铁律」小节（标题漂移？）' }
      Write-Warning "$($mustSec.Sentinel) CLAUDE.md $detail——封顶无从可靠计量，拒绝放行。"
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
    # 两个常驻 CLAUDE 文件可引用已归档经验；定义域是热账本与冷库并集。层级漂移仍只在铁律小节校验。
    # 引用判定走顶部单一真相源 $LessonRefRegex（含裸引用），与 archive 选择器的排除面同源。
    $definedIds = @{}; foreach ($lesson in $ls) { $definedIds[$lesson.id] = $true }
    foreach ($entry in (Get-ResidentLessonRefs).GetEnumerator()) {
      if ($definedIds.ContainsKey($entry.Key)) { continue }
      Write-Warning "$($entry.Value -join ' / ') 引用了不存在的 $($entry.Key)（热账本/冷库并集均无定义）。"; $fail = $true
    }
    # 模板同步（元仓专用）：CLAUDE.template.md 的铁律节须与总账 tier=must 双向一致——
    #   堵「元仓晋升 must 只改 CLAUDE.md 忘改模板 → 下游 init 首跑 check 即挂」。下游无此文件 => 优雅跳过（空配置规则）。
    if (Test-Path $TemplateMd) {
      # 同一枚判定核，同一份「小节」定义（本卡 forbid：不得有第二处「驻留 id 怎么数」的实现）。
      $tplSec = Get-ScaffoldMustLayerSection -Path $TemplateMd
      if (-not $tplSec.Found) {
        $templateDetail = if ($tplSec.Reason -eq 'DUPLICATE-RESIDENT-ID') { "重复驻留 id：$(@($tplSec.DuplicateIds) -join ', ')" } else { '找不到「经验铁律」小节（模板标题漂移）' }
        Write-Warning "$($tplSec.Sentinel) CLAUDE.template.md $templateDetail——模板同步无从校验，拒绝放行。"; $fail = $true
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
    $bad = @($validation | Where-Object { $_.Errors -contains 'missing-rule' } | ForEach-Object Lesson)
    if ($bad.Count) { Write-Warning "[LSN-ENTRY-INVALID] 缺 rule 字段：$($bad.id -join ', ')"; $fail = $true } else { Write-Host '字段完整 ✓' }
    # enforced_by：blocking 经验必须声明机械守卫（脚本/闸门路径）或显式 'none（理由）'——
    #   OpenAI《Harness Engineering》「让同一错误不可复发」：把会卡死/返工的坑从「上下文提醒」升级为「确定性守卫」，或至少显式承认无守卫。
    $blkNoEnf = @($validation | Where-Object { $_.Errors -contains 'blocking-missing-enforced-by' } | ForEach-Object Lesson)
    if ($blkNoEnf.Count) { Write-Warning "[LSN-ENTRY-INVALID] blocking 经验缺 enforced_by（须填机械守卫路径，或 'none（理由）'）：$($blkNoEnf.id -join ', ')"; $fail = $true } else { Write-Host 'enforced_by 完整（blocking 均已声明守卫）✓' }
    # enforced_by **形态**可认（fail-closed）：非空、又既不是 'none（理由）' 也不是认得出的守卫引用的取值
    #   （TODO / N/A / 待补 / 见 PR 讨论，以及中文的 无闸门（只能靠人）/ 人工/评审 / 未定/待议）
    #   是**冒充了一次声明**——它让上面那道「blocking 必填」闸满意，
    #   于是这条经验此后既不会被追问、也不再被晋升探针提名。故在入口直接拒：写真守卫，或写 none（理由）。
    $badEnf = @($ls | Where-Object { -not (Test-ScaffoldLessonEnforcedByWellFormed $_.enforced_by) })
    if ($badEnf.Count) { Write-Warning "[LESSONS-ENFORCED-BY-INVALID] enforced_by 取值认不出是机械守卫（占位符一律拒绝；请写脚本路径 / 闸编号，或写 'none（理由）'）：$(($badEnf | ForEach-Object { "$($_.id)=$($_.enforced_by)" }) -join ' ｜ ')"; $fail = $true } else { Write-Host 'enforced_by 形态可认（非空取值皆为守卫引用或 none（理由））✓' }
    # enforced_by 判据自检（确定性 fixture，不依赖总账内容）：守卫判定必须**双向**成立且对未知取值 fail-closed——
    #   真脚本路径/闸编号判有守卫；none（理由）、空字段、以及 TODO/N/A/待补 这类占位符一律判**无**守卫。
    #   少了占位符那一段，「已有守卫」就能被一个待办事项冒充（上游 issue #183 的反面）。
    #   **中文取值同样要判对**：总账是中文散文，而 .NET 的 \w 认 CJK、`闸\s*\S` 认闸后任意字符，于是
    #   「人工/评审」曾被读成仓库路径、「无闸门（只能靠人）」（字面就是「没有闸门」）曾被读成**已有守卫**。
    #   反方向也要钉：本仓真在用的圈码闸编号（闸⑯ / gate ⑧）必须仍判有守卫，收紧不能连带拒真引用。
    $guardProbeOk = (Test-ScaffoldLessonGuarded 'scripts/review.ps1') -and
                    (Test-ScaffoldLessonGuarded 'selftest 闸 10g（大小写负夹具）') -and
                    (Test-ScaffoldLessonGuarded 'selftest 闸⑯') -and
                    -not (Test-ScaffoldLessonGuarded 'none（本条只能靠人）') -and
                    -not (Test-ScaffoldLessonGuarded '') -and
                    -not (Test-ScaffoldLessonGuarded 'TODO') -and
                    -not (Test-ScaffoldLessonGuarded 'N/A') -and
                    -not (Test-ScaffoldLessonGuarded '待补') -and
                    -not (Test-ScaffoldLessonGuarded '无闸门（只能靠人）') -and
                    -not (Test-ScaffoldLessonGuarded '人工/评审') -and
                     -not (Test-ScaffoldLessonGuarded '闸，靠人') -and
                     -not (Test-ScaffoldLessonGuarded 'gate 讨论') -and
                     -not (Test-ScaffoldLessonGuarded 'TODO: add scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonGuarded 'TBD scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonGuarded 'FIXME scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonGuarded '待补 scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonGuarded 'N/A (.json)') -and
                     -not (Test-ScaffoldLessonGuarded 'no gate 1') -and
                     -not (Test-ScaffoldLessonGuarded '无闸1') -and
                     -not (Test-ScaffoldLessonGuarded 'planned future.ps1') -and
                     -not (Test-ScaffoldLessonGuarded 'manual only; docs/manual') -and
                     -not (Test-ScaffoldLessonGuarded 'there is no gate 1') -and
                     -not (Test-ScaffoldLessonGuarded '没有闸1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'TODO') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed '无闸门（只能靠人）') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'TODO: add scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'TBD scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'FIXME scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed '待补 scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'N/A (.json)') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'no gate 1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed '无闸1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'planned future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'manual only; docs/manual') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'there is no gate 1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed '没有闸1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'none') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'none TODO') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'TODO（scripts/future.ps1）') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'N/A（scripts/future.ps1）') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed '待补（scripts/future.ps1）') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'TODO，scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'no gate（scripts/future.ps1）') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'none: scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'none：scripts/future.ps1') -and
                     -not (Test-ScaffoldLessonEnforcedByWellFormed 'none, scripts/future.ps1') -and
                     (Test-ScaffoldLessonEnforcedByWellFormed '') -and
                     (Test-ScaffoldLessonEnforcedByWellFormed 'none（理由）') -and
                     (Test-ScaffoldLessonEnforcedByWellFormed 'none(reason)')
    if ($guardProbeOk) { Write-Host 'enforced_by 守卫判定自检（真引用/圈码闸号/none/空/ASCII 占位符/中文伪守卫 各判对）✓' } else { Write-Warning 'enforced_by 守卫判定回归（Test-ScaffoldLessonGuarded：真引用或圈码闸号未判有守卫，或 none/空/占位符/中文伪守卫（无闸门…、人工/评审）被误判为已有守卫——fail-open 方向）。'; $fail = $true }
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
    # master(#129) 的写入平面 + 本卡(#51) 的冷存只读提示与严格标题口径，两者都要：
    # 前者决定写哪一份账本，后者决定块边界与「命中冷库条目时给出移回热区的修法」。
    $BumpLedger = Resolve-BumpLedger -Root $RepoRoot -Fallback $Ledger
    # A11 的闸必须在**任何写入之前**：旧写法只在「账本不存在」与「块没找到」两条失败分支上调它，于是
    # 「本检出已把 Lx 归冷、而主检出的 LEDGER 里 Lx 仍是热的」这条真实路径会命中块、照常改写、exit 0，
    # 冷存只读提示一次都不出现。写哪一份账本，就得连同那一份的冷库一起判——否则判据与写入面分家。
    # 两个平面任一为冷即拒（fail-closed）：id 在任何一侧已归冷，热/冷对就已经不一致，此时写入只会加深它。
    # 三级父目录才是检出根：<root>/docs/lessons/LEDGER.md → docs/lessons → docs → <root>。
    # 早先只上溯两级，算出 <root>/docs/specs/archive/... 这个永不存在的路径，于是 Test-Path 恒 false、
    # 主检出那侧的冷库**一次都没被看过**；而 2g(f) 仍然绿，因为它被前一道（本工作树冷库）那条守卫满足了
    # ——断言被另一条路径满足，正是 L165 要根除的形态。
    $BumpRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $BumpLedger))
    $BumpArchive = Join-Path $BumpRoot 'specs/archive/lessons-archive.md'
    Throw-ArchivedLessonReadOnly $Query
    if ((Test-Path $BumpArchive) -and ($BumpArchive -ne $LessonsArchive)) { Throw-ArchivedLessonReadOnly $Query $BumpArchive }
    if (-not (Test-Path $BumpLedger)) { throw "总账不存在: $BumpLedger" }
    $raw = Get-Content $BumpLedger -Raw
    # 起点严格匹配目标 id，终点则是任意二级标题；读、写、搬运三条路径共享同一块边界。
    $blockRe = "(?ms)^##[ \t]+$([regex]::Escape($Query))[ \t]*(?:\r?$).*?(?=^##[ \t]+|\z)"
    $m = [regex]::Match($raw, $blockRe)
    if (-not $m.Success) { Throw-ArchivedLessonReadOnly $Query; throw "未找到 $Query。" }
    $block = $m.Value
    # bump 是**写**路径，读计数器必须走与选择器同一个出生点 Get-LessonMeta。不锚定地在整块里捞
    # `recurrence:\s*(\d+)` 会捞到正文字面量：meta 行缺该字段时读出正文的 7、把锚定的 Replace 打空（块字节不变）、
    # 却打印绿字「7 → 8」并建议 promote 进必须层。元数据非法一律零写入非零退出，与 check/archive 同口径。
    $bumpMeta = Get-LessonMeta $block
    if (-not $bumpMeta.Ok) { throw "[LSN-META-INVALID] $Query 的规范 meta 行不合法（$($bumpMeta.Error)）——拒绝 bump，未写入任何字节。" }
    $old = [int]$bumpMeta.Fields['recurrence']
    # PowerShell 的 + 会在溢出时**静默升宽到 [long]**，于是这里能写出 2147483648，而下一次读取按 Int32
    # 判它非法——写路径造出读路径拒绝的值。到顶即 fail-closed，零写入。
    if ($old -ge [int]::MaxValue) { throw "[LSN-META-INVALID] $Query 的 recurrence 已达 Int32 上限（$old），再加一会写出读路径无法解析的值——拒绝 bump，未写入任何字节。" }
    $new = $old + 1
    # TD51/TD-114：原写法 [regex]::Replace($block,'recurrence:\s*\d+',"recurrence: $new",1) 并无
    # (string,string,string,int) 这个重载——第 4 个实参 1 被隐式转成 RegexOptions（1=IgnoreCase），
    # 等价于「大小写不敏感、替换块内所有匹配」，若 body 文本恰好引用了字面量 `recurrence: <digits>`
    # 会被静默篡改。改锚定到本块的 meta 行（`- date: ... recurrence: <n>`），只动该行的计数器，
    # 不触碰 body 里可能出现的同名字面量。`${1}` 用单引号字面量 + 字符串拼接 $new（避免双引号内
    # `${1}`/`$1` 被 PowerShell 当成变量插值、吞掉正则回引用）。
    # 三层各管一件事：Get-LessonMeta 拦「形状不对就别写」（闸 2i(e) 杀）；实例 Replace 第三实参 1 把「只写一处」
    # 变成结构事实（静态重载没有次数版，见上）；-ceq 拦「一个字节没改却报成功」——锚定形态漂移时只有它能变红
    # （闸 2c(b) 杀）。行首只收水平空白，并要求字段分隔符，避免跨行或正文里的 recurrence bait。
    $newBlock = [Regex]::new('(?m)^(-[ \t]+date:[^\r\n]*?[｜|][ \t]*recurrence[ \t]*:[ \t]*)\d+').Replace($block, ('${1}' + $new), 1)
    if ($newBlock -ceq $block) { throw "[LSN-META-INVALID] $Query 的 meta 行 recurrence 计数器未被更新（锚定未命中）——拒绝写回，未写入任何字节。" }
    $raw = $raw.Remove($m.Index, $m.Length).Insert($m.Index, $newBlock)
    Set-Content -Path $BumpLedger -Value $raw -Encoding utf8 -NoNewline
    Write-Host "$Query recurrence: $old → $new" -ForegroundColor Green
    if ($BumpLedger -ne $Ledger) {
      Write-Host "  账本 = $BumpLedger（主检出；recurrence 是仓库级元数据，不进卡片 diff）。本检出的 list/promote 读的仍是本地那份，计数会显示旧值。" -ForegroundColor DarkGray
    }
    if ($new -ge 2) {
      Write-Host "  recurrence≥2 → 满足晋升必须层门槛之一，考虑 promote $Query。" -ForegroundColor Yellow
    }
  }

  'promote' {
    if (-not $Query) { throw 'promote 需要条目 id（如 L1）。' }
    # 恢复/冲突态可能冷热两侧同时存在同一 id。先判冷库，不能因热副本存在就隐藏已归档事实并给出晋升结论。
    Throw-ArchivedLessonReadOnly $Query
    $l = Get-Lessons | Where-Object id -eq $Query | Select-Object -First 1
    if (-not $l) { throw "未找到 $Query。" }
    # promote 是**决策**面：读不出元数据却渲染空 tier / rec=0 并给出「暂不够必须层」，等于把默认值当结论卖出去。
    if (-not $l.metaOk) { throw "[LSN-META-INVALID] $Query 的规范 meta 行不合法（$($l.metaError)）——晋升门槛读不出来，拒绝给结论。" }
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
    # 选择规则的自然语言权威表述在 docs/LESSONS.md §3 PURIFY（此处只实现，不再复述一遍口径）。
    # 先验「引用排除面到底有没有输入」。Get-ResidentLessonRefs 对缺失文件是 `continue`，于是「定义受保护集合的
    # 那个文件不存在」与「确实没有条目被引用」在下游 `-not ContainsKey` 处**不可区分**：一棵有 LEDGER 却没有
    # CLAUDE.md 的树（或 -RepoRoot 指过去的别的仓）会让整批 tier=ledger / recurrence=1 / 非最高 id 的条目静默
    # 进候选、被真的搬冷，事后还没有闸会红（闸 16 按热∪冷判，冷项照样算已定义）——与「只认方括号引用」同一
    # 后果、不同入口。这是本命令唯一的 fail-open 面（非法 meta / 别名 id / 暂存写失败都早已 fail-closed），
    # 故读不到判据就不给「候选」这个结论：零搬运、非零退出，预览与实跑同口径。
    # 判据只认 CLAUDE.md 在不在：它是每个仓（元仓与下游）都有的常驻真相源，缺席即受保护集合的主输入没了；
    # CLAUDE.template.md 只在元仓存在，**单独**缺席属正常形态，不作判据——否则每个下游仓都归不了档。
    # check 不受此限——它只做诊断、不移动数据，缺文件时按空配置优雅降级（见上面 check 分支的 Test-Path）。
    if (-not (Test-Path -LiteralPath $Ledger -PathType Leaf)) {
      Write-Warning "[LSN-LEDGER-SOURCE-MISSING] lesson 热账本 $Ledger 不存在或不是可读文件——拒绝归档（零搬运）。"
      exit 1
    }
    try { $null = Get-Content -LiteralPath $Ledger -Raw -ErrorAction Stop } catch {
      Write-Warning "[LSN-LEDGER-SOURCE-MISSING] lesson 热账本 $Ledger 无法读取——拒绝归档（零搬运）：$($_.Exception.Message)"
      exit 1
    }
    if (-not (Test-Path -LiteralPath $ClaudeMd)) {
      Write-Warning "[LSN-RESIDENT-SOURCE-MISSING] 常驻 $ClaudeMd 不存在——引用排除面失去主输入，拒绝归档（零搬运）。"
      exit 1
    }
    $hot = @(Get-Lessons)
    # The mover accepts IDs, not parsed block identities. With duplicate hot IDs it can select a different
    # block than the eligibility filter inspected, so reject the ambiguous ledger before deriving candidates.
    $duplicateHotIds = @($hot | Group-Object id | Where-Object Count -gt 1)
    if ($duplicateHotIds.Count) {
      Write-Warning "[LSN-DUPLICATE-HOT-ID] 热账本 id 重复：$($duplicateHotIds.Name -join ',')——拒绝归档，双侧零写入。"
      exit 1
    }
    $maxNumber = -1
    foreach ($lesson in $hot) {
      $number = Get-LessonNumber $lesson.id
      if ($number -gt $maxNumber) { $maxNumber = $number }
    }
    $referenced = Get-ResidentLessonRefs
    # metaOk 是入选第一条件（理由见顶部 Get-LessonMeta 头注）：读不出来就留热账本，并且**整条命令非零退出**
    # ——同一份账本不能 check 报 1 而 archive 报 0；archive.ps1 的 DryRun 早就是这口径（闸 12e⑥）。
    # 但这个非零退出**不等于「什么都没发生」**：实跑仍会把合法候选照常搬冷（见文件末尾 exit 1 的位置），
    # 退出码报告的只是「账本里还有读不出的条目」。调用方别把 exit 1 读成回滚；修好坏条目后重跑幂等。
    $invariantById = @{}
    foreach ($lesson in $hot) { $invariantById[$lesson.id] = @(Get-LessonInvariantErrors $lesson) }
    $unparsable = @($hot | Where-Object { $invariantById[$_.id] -contains 'meta-invalid' })
    foreach ($bad in $unparsable) { Write-Warning "[LSN-META-INVALID] $($bad.id) 的规范 meta 行不合法（$($bad.metaError)）——保留在热账本，不进归档候选。" }
    $invalidEntries = @($hot | Where-Object {
      ($invariantById[$_.id] -contains 'missing-rule') -or ($invariantById[$_.id] -contains 'blocking-missing-enforced-by')
    })
    foreach ($bad in $invalidEntries) {
      Write-Warning "[LSN-ENTRY-INVALID] $($bad.id) 条目不完整（$($invariantById[$bad.id] -join ',')）——保留在热账本，不进归档候选。"
    }
    $candidates = @($hot | Where-Object {
      @($invariantById[$_.id]).Count -eq 0 -and
      $_.tier -eq 'ledger' -and $_.recurrence -eq 1 -and
      (Get-LessonNumber $_.id) -ne $maxNumber -and -not $referenced.ContainsKey($_.id)
    })
    $candidateText = if ($candidates.Count) { ($candidates.id -join ',') } else { 'none' }
    Write-Host "$(if ($DryRun) { '[LSN-ARCHIVE-DRYRUN]' } else { '[LSN-ARCHIVE]' }) candidates=$candidateText"
    $moverExit = 0
    if ($candidates.Count) {
      # 预览与实跑走**同一个**搬运器，只差 -DryRun。否则预览只演到「选出了谁」，而搬运器自己的拒绝全在这之后：
      # 预览报绿，实跑却搬走一部分后非零退出——落差压在数据移动上。经**本入口**实际可达的拒绝只有两类：
      # 非规范别名 id（如 `L02`，闸 2f(a) 有夹具）与「两侧并存但内容不一致」；另两类（拒最高 id / 未知 id）
      # 在这里结构上不可达——最高 id 已被上面的选择器先行排除，候选又恒取自热账本、搬运器必查得到。
      $archiveScript = Join-Path $PSScriptRoot 'archive.ps1'
      $moverArgs = @('-NoProfile', '-File', $archiveScript, '-RepoRoot', $RepoRoot, '-LessonsOnly', '-LessonIds', ($candidates.id -join ','))
      $moverArgs += if ($DryRun) { '-DryRun' } else { '-Quiet' }
      & pwsh @moverArgs
      $moverExit = $LASTEXITCODE
    }
    # 顺序即语义：搬运已在上面发生过了。此处的 exit 1 报告「账本里有读不出的条目」，**不表示零搬运**——
    # 合法候选此刻已经进了冷库（A15 只要求非零退出；预览路径因 -DryRun 天然零写入，两者退出码仍同口径）。
    if ($unparsable.Count -or $invalidEntries.Count) { exit 1 }
    if ($moverExit -ne 0) { exit $moverExit }
  }
}
