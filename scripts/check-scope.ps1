#requires -Version 7
<#
.SYNOPSIS
  独立范围检查器：把「某分支相对基线的改动是否全在任务卡 allow_paths 内」变成一条有退出码的命令。

.DESCRIPTION
  ship 的范围闸（task.ps1）与本脚本共用同一枚判定核 scripts\_scope.ps1——本脚本不是「第二实现」，
  它只是那枚核的第二个入口（TD93 item①；双实现漂移的教训见 _scope.ps1 / _gitbase.ps1 头注）。

  存在的理由：`docs\DEVOPS-WORKFLOW.md`「任何已 push 状态的手工恢复」是**绕过 ship 主路**的最后手段平面，
  而 CI 没有范围闸（TD89 根因）——那条序列里的范围核对此前是散文（人眼比对 git diff 输出，没有退出码）。

  退出码（fail-closed）：
    0 = 全部改动 ∈ 卡 allow_paths。
    1 = 越界（点名越界路径 + 给 L18 处置修法）**或不可判**——卡不存在 / allow_paths 取不到（含行内 flow 写法）/
        基线引用不可解析 / git diff 求值失败。不确定 ≠ 放行。

  与 ship 范围闸的**有意差异**：本脚本**不做定向 fetch**（只读诊断口，不该在诊断路径上动网络）。
  ship 是合并闸、须最强，故有 F5「必须刷新远端基线且不许回退本地」；本脚本改为打印所用基线 ref 与其 sha，
  基线陈旧与否由调用者自行判断（要新就先 `git fetch origin <base>` 再跑）。判定语义本身两者完全一致。

  **必须跑受信检出里的这一份**（同 task.ps1 的 L86 之理）：本脚本与 _scope.ps1/_cards.ps1 由**相对自身位置**加载，
  所以从被审工作树里跑，等于让被审分支自带的那份检查器来判自己——它把匹配器改成恒 PASS 即可绕过这条恢复路径上
  唯一的范围闸。正确形态：跑**主检出（基线）**那份 `scripts\check-scope.ps1`，用 `-Path <被审仓库/工作树>` 指向要判的树。

  **判定对象由卡 id 锚定，不看 -Path 那个检出的 HEAD 是谁**：尖端取 refs/remotes/origin/<TaskId>（缺省/远端模式）
  或 refs/heads/<TaskId>（-Local），基线取同侧的 <base>；allow_paths 取**基线那份卡**（git show <baseRef>:…）。
  这三条绑定是 ship 靠「相位命令只在主检出跑」（L86）隐式获得、而独立入口必须显式补上的（见各 [SCOPE-*] 哨兵）。

.PARAMETER TaskId  形如 T1-FOO，须在**基线**上存在 specs/tasks/<TaskId>.md（allow_paths 只认基线那份卡）。
.PARAMETER Base    基线分支**名**（如 master 或 origin/master——前缀会被归一掉，**取哪一侧由 -Local 决定、不由前缀决定**）。
                   只接受纯名，拒 git revision 语法（`^{}` `~` `@{}` `..`）。缺省 = 主检出当前分支，detached 时探测 origin/HEAD → main/master。
.PARAMETER Path    在哪个仓库/工作树里解析引用。缺省 = <WorktreeRoot>\<TaskId>；**该工作树不存在即 fail-closed**（不静默回退主检出）。工作树已拆时显式传主检出即可——判定对象由卡 id 锚定，与传哪个检出无关。
.PARAMETER Local   本地模式：基线与尖端都取本地引用（对应 task.ps1 -Local 的合并目标，推送前诊断用）；缺省为远端模式，两侧都取 origin/*（即「已推送状态」这条闸的正题）。
.PARAMETER ExpectTip  期望的尖端 sha（7–40 位十六进制，通常来自 `gh pr view <PR号> --json headRefOid`）。给了就机检
                   「本闸判过的树 == 你要合并的树」，不符即 fail-closed。**恢复序列必须传**——本脚本离线、看不出
                   origin/* 是否已陈旧，只靠打印告示不构成 fail-closed 证据。
.PARAMETER ExpectBase 期望的基线 sha（7–40 位十六进制，同 -ExpectTip 的校验规则）。基线决定**采信哪份卡的 allow_paths**——
                   origin/<base> 陈旧就等于按旧标准判（master 上收窄过 allow_paths 时旧标准更宽），而 -ExpectTip
                   钉不到这一侧。恢复序列两个都要传。
.EXAMPLE
  pwsh -NoProfile -File scripts\check-scope.ps1 -TaskId T1-FOO -Base master
  pwsh -NoProfile -File scripts\check-scope.ps1 -TaskId T1-FOO -Base master -Local
  # 恢复序列用法：跑**主检出**那份、-Path 指被审树；fetch/gh 任一失败即中止，两侧 OID 都钉进闸
  git fetch origin master T1-FOO; if ($LASTEXITCODE -ne 0) { throw 'fetch 失败，中止恢复' }
  $head = gh pr view 42 --json headRefOid --jq .headRefOid
  if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { throw 'gh 未返回合法 head oid，中止恢复' }
  $baseOid = git -C <被审工作树> rev-parse refs/remotes/origin/master
  pwsh -NoProfile -File <主检出>\scripts\check-scope.ps1 -TaskId T1-FOO -Base master -Path <被审工作树> -ExpectTip $head -ExpectBase $baseOid
#>
[CmdletBinding()]
param(
  # 绑定期即校验字符集（同 check-cards / task.ps1 的卡 id 契约）：TaskId 会拼进卡片与 worktree 路径，路径穿越面。
  [Parameter(Mandatory)]
  [ValidatePattern('^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$', ErrorMessage = 'TaskId 非法格式：值 "{0}" 须匹配卡 id 契约 ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$（同 check-cards / task.ps1）。')]
  [string]$TaskId,
  [string]$Base = '',
  [string]$Path = '',
  [switch]$Local,
  # 把「我判的是哪个提交」从打印文案升级成**机检绑定**：给了就必须与解析出的尖端一致，否则 fail-closed。
  # 恢复序列据此把 `gh pr view --json headRefOid` 拿到的 PR head 钉进本闸——否则「检查过的树」与「合并的树」
  # 可以是两棵（离线检查器看不出 origin/* 陈旧，光印个告示不是 fail-closed 证据，codex R3 r4 #1）。
  [string]$ExpectTip = '',
  # 同款绑定，钉的是**基线**那一侧。基线 OID 决定的是「采信哪份卡的 allow_paths」——origin/<base> 若陈旧，
  # 判定用的就是旧标准（有人在 master 上收窄过 allow_paths 时，旧标准更宽 ⇒ 该拦的被放行），而 -ExpectTip
  # 只钉 head、钉不到这一侧（codex R3 r7 #2）。
  [string]$ExpectBase = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open
. (Join-Path $PSScriptRoot '_config.ps1')
. (Join-Path $PSScriptRoot '_gitbase.ps1')   # 基线名 → 引用（TD68/F2 单一实现，与 task.ps1 / review.ps1 共用）
. (Join-Path $PSScriptRoot '_scope.ps1')     # 判定核：allow_paths 取值 / 改动清单 / 段级匹配器（与 ship 同一枚）
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# 不可判一律走这里：打印修法后非零退出（fail-closed；调用方按退出码判，不必解析文案）。
# **每条结论行都带 ASCII 哨兵**（[SCOPE-UNDECIDABLE] / [SCOPE-FIX] / [SCOPE-BLOCK] / [SCOPE-PASS]）：
# 中文结论行在「父进程 stdout 被重定向、控制台编码未钉」的环境里会解码成乱码，令对着中文做的断言假红——
# codex R3 r6 实测：重定向跑官方 selftest 时 15s 有 6 个 case 因此失败。ASCII 哨兵跨 locale/编码稳定，
# 故机检一律认哨兵、中文只作人读文案（同 L17/L149 的教训面）。
function Stop-Undecidable([string]$Why, [string]$Fix) {
  Write-Host "[SCOPE-UNDECIDABLE] 范围检查 BLOCK（不可判）：$Why" -ForegroundColor Red
  Write-Host "[SCOPE-FIX] 修法：$Fix" -ForegroundColor Yellow
  exit 1
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Stop-Undecidable 'PATH 上没有 git，无法求改动清单。' '装 git 后重跑（本脚本是只读诊断口，不做任何写操作）。'
}

# 被检查的 git 目录：缺省取 <WorktreeRoot>\<TaskId>（task.ps1 -Phase start 的落点）。
# **缺省工作树不在即 fail-closed**（哨兵 [SCOPE-NOWT]，codex R3 r2 #1）：旧码在此**静默回退主检出**，
# 于是 base=master、HEAD=master → 0 changed files → 误印 PASS，而被审分支上的越界改动一个都没看。
# 工作树已拆（cleanup 后 / S9 重建）时，请显式 -Path <仓库或工作树>——显式优于静默猜。
if (-not $Path) {
  $wtDefault = Join-Path (Get-ScaffoldWorktreeRoot) $TaskId
  if (-not (Test-Path $wtDefault)) {
    Stop-Undecidable "缺省工作树不存在：$wtDefault [SCOPE-NOWT]——不静默回退主检出（那会拿 base 自己比自己、0 改动误印 PASS）。" '显式传 -Path <工作树或仓库路径>（工作树已拆时传主检出即可——判定对象由 -TaskId 的分支引用锚定，见下），或先跑 task.ps1 -Phase start。'
  }
  $Path = $wtDefault
}
if (-not (Test-Path $Path)) { Stop-Undecidable "指定的 -Path 不存在：$Path" '传一个存在的工作树/仓库路径。' }
$Path = (Resolve-Path $Path).Path

# 基线**名**：缺省取主检出当前分支（同 task.ps1 缺省），detached 时探测 origin/HEAD，再兜底 main/master。
if (-not $Base) {
  $Base = "$(& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)".Trim()
  if (-not $Base) {
    $originHead = "$(& git -C $RepoRoot symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>$null)".Trim()
    if ($originHead) { $Base = ($originHead -replace '^origin/', '') }
  }
  if (-not $Base) {
    foreach ($cand in @('main', 'master')) {
      & git -C $RepoRoot rev-parse --verify --quiet "refs/heads/$cand^{commit}" 1>$null 2>$null
      if ($LASTEXITCODE -eq 0) { $Base = $cand; break }
    }
  }
  if (-not $Base) { Stop-Undecidable '无法探测基线分支名（HEAD detached 且无 origin/HEAD / main / master）。' '显式传 -Base <分支名>。' }
}
# 自基线 fail-closed（task.ps1 的 L86-BASE 守卫在本入口的同款应用——判定核共享后，两个入口必须同守，
# 否则「多一个入口」反而多一个绕过面）：base 若解析成**本卡分支自己**，`<base>...HEAD` 得空 diff，
# 越界改动会被空过并印出 PASS——**假绿比没有检查器更坏**。两条进入路径都要堵：
#   ① 显式误传 -Base <卡 id>（或 origin/<卡 id>）；
#   ② 在**本卡 worktree 里**跑本脚本——$RepoRoot 随脚本自身位置派生成该 worktree，其当前分支就是卡分支，
#      于是上面的缺省探测拿到的正是卡分支自己（codex R3 r1 实测：0 changed files → PASS）。
# 与 -Local / 远端无关地拒；ASCII 哨兵 [SCOPE-SELFBASE] 供夹具跨 locale 稳定判定是**这道**守卫拦的。
# -Base 只接受**纯分支名**（可带 origin/ 前缀）。git revision 语法能把同一个提交拼成千百种写法——
# `<卡分支>^{commit}` / `<卡分支>~0` / `<卡分支>@{0}` 都解析到卡分支尖端（codex R3 r3 实测三种全绕过按字面量
# 比名字的旧守卫、拿到空 diff 印 PASS）。逐个列黑名单是打地鼠，故只放行纯名字符集（哨兵 [SCOPE-BADBASE]）。
# 这只是第一道；真正兜底的是下面**按提交身份**比对的那道——写法再刁钻也逃不掉。
if ($Base -notmatch '^(origin/)?[A-Za-z0-9._][A-Za-z0-9._/-]*$' -or $Base -match '\.\.') {
  Stop-Undecidable "基线名含 git revision 语法或非法字符：'$Base' [SCOPE-BADBASE]——`^{}` `~` `@{}` `..` 之类的写法能把卡分支自己伪装成基线。" '-Base 只接受纯分支名（可带 origin/ 前缀），如 -Base master。'
}
# 归一化**一次**：把可选的 origin/ 前缀剥掉，之后全程只用裸名。取哪一侧由 -Local 决定，不由前缀决定——
# 否则 `-Base origin/master` 在远端模式下会被再拼一次成 refs/remotes/origin/origin/master（连带把修法文案
# 印成 `git fetch origin origin/master`），在 -Local 下又会反过来强制远端基线、违背本地模式契约（codex R3 r4 #2）。
$Base = $Base -replace '^origin/', ''
if ($Base -eq $TaskId) {
  Stop-Undecidable "基线退化成分支自己（base = '$Base' == 卡分支 '$TaskId'）[SCOPE-SELFBASE]——`<base>...<tip>` 会是空 diff，越界改动将被空过并误印 PASS。" "显式传真实基线分支（如 -Base master）。注意：在本卡 worktree 里跑本脚本时，缺省基线就是当前分支＝卡分支，故要么显式传 -Base，要么改在主检出跑。"
}

# 基线**引用**：缺省对照远端跟踪 origin/<base>（GitHub 的合并目标）；-Local 优先本地 <base>（task.ps1 -Local 的合并目标）。
# 不做定向 fetch（见头注：诊断口不动网络）——远端引用缺失即不可判，由调用者决定先 fetch 还是改走 -Local。
if ($Local) { $baseRef = Resolve-ScaffoldBaseRef -GitDir $Path -BaseName $Base -PreferLocal }
else { $baseRef = Resolve-ScaffoldBaseRef -GitDir $Path -BaseName "origin/$Base" }
if (-not $baseRef) {
  $miss = if ($Local) { "refs/heads/$Base 与 refs/remotes/origin/$Base 均不存在" } else { "refs/remotes/origin/$Base 不存在（本脚本不自动 fetch）" }
  Stop-Undecidable "基线引用不可解析：$miss。" "先跑 git fetch origin $Base，或改传 -Base <分支名> / 加 -Local（对照本地基线，同 ship -Local 的合并目标）。"
}
# 被判定的**尖端**一律锚定到 TaskId 的分支引用，不用 $Path 那个检出的 HEAD（哨兵 [SCOPE-NOTIP]，codex R3 r2 #1）。
# 旧码直接 diff `<base>...HEAD`：HEAD 是谁完全取决于 -Path 指到哪个检出——把主检出（HEAD=master）传进来，
# 就变成 master 比 master、0 改动、误印 PASS，而被审分支上的越界改动一个没看（codex 实测：分支有 12 个改动文件）。
# 锚定到 refs/heads/<TaskId>（无则 refs/remotes/origin/<TaskId>）后，无论 -Path 指向哪个检出/工作树，
# 判定对象恒为**这张卡的分支**；指到无关仓库则该引用解析不到 → fail-closed，而非拿别人的树蒙混成 PASS。
# 尖端引用**随模式绑定**，与基线同侧（codex R3 r3 #2）：缺省＝远端模式（这条闸的正题就是「**已推送**状态的恢复」），
# 故绑 refs/remotes/origin/<TaskId>；-Local＝推送前的本地诊断口，绑 refs/heads/<TaskId>。
# 旧码恒优先本地：fetch 之后本地卡分支若陈旧，判的是旧提交、**新推上去的越界改动被整段忽略**（假 PASS，
# 恰恰发生在本闸唯一要守的那个平面上）。基线侧的 Resolve-ScaffoldBaseRef 本就按模式选边，两侧现在对称。
$tipCand = if ($Local) { "refs/heads/$TaskId" } else { "refs/remotes/origin/$TaskId" }
& git -C $Path rev-parse --verify --quiet "$tipCand^{commit}" 1>$null 2>$null
if ($LASTEXITCODE -ne 0) {
  $tipHint = if ($Local) { '传本卡分支所在的仓库/工作树' } else { "先跑 git fetch origin $TaskId，或加 -Local 判本地分支" }
  Stop-Undecidable "在 $Path 里找不到本卡的$(if ($Local) { '本地' } else { '远端跟踪' })分支引用（$tipCand）[SCOPE-NOTIP]。" "$tipHint。判定对象由卡 id 锚定，不看该检出当前 HEAD 是谁。"
}
$tipRef = $tipCand
# 远端模式下本地卡分支若与远端**分叉**，操作者多半以为在判自己手上那棵树、实则判的是远端（或反之）——
# 不猜，fail-closed 让人显式选边（哨兵 [SCOPE-TIPDIVERGE]）。-Local 模式不设此检：推送前本地领先是常态。
if (-not $Local) {
  $localTipSha = "$(& git -C $Path rev-parse --verify --quiet "refs/heads/$TaskId^{commit}" 2>$null)".Trim()
  $remoteTipSha = "$(& git -C $Path rev-parse --verify --quiet "$tipRef^{commit}" 2>$null)".Trim()
  if ($localTipSha -and $remoteTipSha -and $localTipSha -ne $remoteTipSha) {
    # 修法必须是**真能执行**的：`git fetch` 只动远端跟踪引用、**不动本地分支**，故光 fetch 永远消不掉本分叉，
    # 「本地陈旧 / 远端更新」这个最常见的恢复态会被本闸永久卡死（codex R3 r5 #3）。给出三条按方向可执行的路：
    # 本地落后 → 在卡分支上 merge origin/<id>（**禁 rebase**，T36-DOCTRINE watershed 后不改写历史）；
    # 本地领先 → push 上去；只想看本地那棵 → -Local。
    Stop-Undecidable "本地与远端的卡分支已分叉（refs/heads/$TaskId=$($localTipSha.Substring(0,7)) ≠ $tipRef=$($remoteTipSha.Substring(0,7))）[SCOPE-TIPDIVERGE]——判哪一棵会得出不同结论，不替你猜。" "按方向三选一（`git fetch` 只更新远端跟踪引用、**不会**让本地分支跟上，故单跑 fetch 消不掉本分叉）：① 本地落后 ⇒ ``git -C '$Path' checkout $TaskId && git -C '$Path' merge --no-edit origin/$TaskId`` 后重跑（禁 rebase，watershed 后不改写历史）；② 本地领先 ⇒ ``git -C '$Path' push origin $TaskId`` 后重跑；③ 只想判本地那棵 ⇒ 加 -Local。"
  }
}
# 自基线的**兜底**判据：不比名字比**提交身份**——基线与尖端解析到同一个提交时 `<base>...<tip>` 必是空 diff，
# 无论 -Base 被写成哪种花样（甚至另起一个指向同一提交的分支名）都逃不掉（codex R3 r3 #1）。
$baseShaFull = "$(& git -C $Path rev-parse --verify --quiet "$baseRef^{commit}" 2>$null)".Trim()
$tipShaFull = "$(& git -C $Path rev-parse --verify --quiet "$tipRef^{commit}" 2>$null)".Trim()
if ($baseShaFull -and $tipShaFull -and $baseShaFull -eq $tipShaFull) {
  Stop-Undecidable "基线与尖端解析到**同一个提交**（$baseShaFull）[SCOPE-SELFBASE]——空 diff 会让任何越界改动被空过并误印 PASS。" "传一个真正的合并目标作 -Base（如 master）；按名字比对拦不住 `<卡分支>~0`/`^{commit}`/`@{0}` 这类写法，故此处按提交身份兜底。"
}
# -ExpectTip：把「检查过的树」与「随后被合并的树」钉成同一个。本脚本离线、看不出 origin/* 是否陈旧
# （远端可能已经又推了新提交），故**光印一行告示不算 fail-closed 证据**——恢复序列须把 PR head 的 oid
# 传进来由本闸机检（codex R3 r4 #1）。不传则退化为纯诊断，判定照跑但不承担这层绑定。
# 绑定校验共用一段：**按「参数是否被显式传入」判，不按真假值判**（codex R3 r7 #1）。
# `if ($ExpectTip)` 会把「显式传了空串」当成「没传」——而恢复配方里这个值来自 `gh pr view` 的输出，
# gh 一旦失败就是空串，于是**本该强制的绑定被静默关掉**、闸照样印 [SCOPE-PASS]。显式传入即必须是合法 OID。
function Assert-ExpectPin([string]$Name, [string]$Raw, [string]$ActualSha, [string]$WhatFor) {
  if ([string]::IsNullOrWhiteSpace($Raw)) {
    Stop-Undecidable "-$Name 被显式传入但是空值 [SCOPE-TIPMISMATCH]——多半是 `gh pr view` / `git rev-parse` 失败后把空串喂了进来，绑定会被静默关掉。" "让配方在 gh/git 非零退出时立即中止；确认取到合法 40 位 oid 再传给 -$Name。"
  }
  # 最小位数 **7**（git 惯用缩写下限），与 .PARAMETER 帮助、错误文案三处一致——此前帮助写 7、校验写 4、
  # 注释又说 7，三处各说各的（codex R3 r8 #2 抓出）。取一个数并让三处同源，边界 7 / 6 各有夹具。
  if ($Raw -notmatch '^[0-9a-fA-F]{7,40}$') {
    Stop-Undecidable "-$Name 不是十六进制 sha（7–40 位）：'$Raw' [SCOPE-TIPMISMATCH]" "传完整 40 位 oid 给 -$Name（$WhatFor）。"
  }
  # **先解析成完整 OID 再按整串相等比**，不做前缀匹配（codex R3 r6 #2）：`StartsWith` 会让一个 7 位缩写
  # 「匹配」上任何同前缀的提交——那证明不了提交身份，只证明了前缀巧合；git 对**歧义**缩写则直接非零退出，
  # 于是歧义输入在这里就被挡住（而不是被悄悄当成命中）。
  $full = "$(& git -C $Path rev-parse --verify --quiet "$Raw^{commit}" 2>$null)".Trim()
  if ($full -cnotmatch '^[0-9a-f]{40}$') {
    Stop-Undecidable "-$Name '$Raw' 在该仓库解析不出唯一提交（不存在，或是**歧义缩写**）[SCOPE-TIPMISMATCH]" "传完整 40 位 oid；缩写一旦歧义就无法证明提交身份。"
  }
  if ($full -ne $ActualSha) {
    Stop-Undecidable "本闸实际用的$WhatFor 与 -$Name 不符（judged=$ActualSha expect=$full）[SCOPE-TIPMISMATCH]——本地引用与你打算合并的那个不是同一个提交（多半是 origin/* 陈旧）。" "先 git fetch origin $TaskId $Base（**fetch 失败即中止**）刷新两侧引用后重跑本闸；合并的必须是本闸判过的那个 sha。"
  }
}
if ($PSBoundParameters.ContainsKey('ExpectTip')) { Assert-ExpectPin 'ExpectTip' $ExpectTip $tipShaFull '尖端' }
if ($PSBoundParameters.ContainsKey('ExpectBase')) { Assert-ExpectPin 'ExpectBase' $ExpectBase $baseShaFull '基线（决定采信哪份卡的 allow_paths）' }
$baseSha = "$(& git -C $Path rev-parse --short $baseRef 2>$null)".Trim()
$tipSha = "$(& git -C $Path rev-parse --short $tipRef 2>$null)".Trim()
Write-Host "范围检查 [$TaskId]：仓库/工作树 $Path（$(if ($Local) { '本地模式 -Local' } else { '远端模式（已推送状态）' })）" -ForegroundColor Cyan
Write-Host "  基线 = $baseRef ($baseSha)$(if (-not $Local) { '（未自动 fetch）' })" -ForegroundColor DarkGray
Write-Host "  尖端 = $tipRef ($tipSha)（按卡 id 锚定，非该检出的 HEAD）" -ForegroundColor DarkGray
Write-Host "  判定的尖端 sha = $tipShaFull" -ForegroundColor DarkGray
Write-Host "  判定的基线 sha = $baseShaFull（allow_paths 取自这个提交上的卡）" -ForegroundColor DarkGray
if (-not $Local -and -not ($PSBoundParameters.ContainsKey('ExpectTip') -and $PSBoundParameters.ContainsKey('ExpectBase'))) {
  # 离线检查器无从判断 origin/* 是否已陈旧；这条提示不是闸，真正的绑定要靠 -ExpectTip/-ExpectBase（见上）。
  Write-Host '  提示：本脚本不自动 fetch。合并前请先 git fetch（失败即中止），并用 -ExpectTip <PR head oid> + -ExpectBase <origin/base oid> 让本闸机检「判过的 = 要合的」。' -ForegroundColor DarkYellow
}

# 自此往下**一律用已解析并校验过的不可变提交 sha**，不再用 $baseRef/$tipRef 这两个可变引用名（codex R3 r5 #2）：
# 引用是可动的——另一个进程在本闸「校验 sha」与「求 diff / 读卡」之间跑一次 fetch，就会让打印并经 -ExpectTip
# 校验过的那个 sha，与实际被判定的树、以及实际采信的 allow_paths 不是同一个（TOCTOU）。钉住 sha 后，
# 判定对象在本次运行内恒定，打印出来的 sha 即是真正判过的那棵树。
try { $changed = @(Get-ScaffoldChangedPath -GitDir $Path -BaseRef $baseShaFull -TipRef $tipShaFull) }
catch { Stop-Undecidable "改动清单求值失败：$($_.Exception.Message)" '确认基线与尖端引用在该仓库里均可解析后重跑。' }

Write-Host "  改动 $($changed.Count) 个文件：" -ForegroundColor DarkGray
foreach ($c in $changed) { Write-Host "    $c" -ForegroundColor DarkGray }

# allow_paths 一律从**基线**那份卡读，绝不读被审检出里的卡（哨兵 [SCOPE-NOCARD]，codex R3 r2 #2）。
# 旧码读 $RepoRoot/specs/tasks/<id>.md——按文档从卡的 worktree 里跑时，那就是**被审分支自己的**卡：
# 分支只要给自己的卡加几行 allow_paths，检查器就照单全收，恢复序列的范围闸遂被绕过。判定标准必须来自
# 受信基线，与闸 17ab（R3 的 FrozenPaths 从基线解析，被审分支清空自身副本也不能弱化标准）同一条道理。
# ship 侧无此洞：L86 强制相位命令在主检出跑，task.ps1 读的本就是基线检出那份卡。
# 同上：读的是**钉住的基线 sha**那份卡，不是 $baseRef 那个可变引用——否则并发 fetch 会让「判定用的 diff」
# 与「采信的 allow_paths」来自两个不同的基线提交（codex R3 r5 #2）。
$cardInBase = "specs/tasks/$TaskId.md"
$cardText = (& git -C $Path show "${baseShaFull}:${cardInBase}" 2>$null | Out-String)
if ($LASTEXITCODE -ne 0 -or -not $cardText.Trim()) {
  Stop-Undecidable "基线 $baseRef（$baseShaFull）上没有本卡：$cardInBase [SCOPE-NOCARD]——判定标准只认基线那份卡，不读被审检出里的副本（否则分支可自行扩 allow_paths 绕过本闸）。" '卡须先登记进基线分支（本仓约定：卡登记直推 master）再跑范围检查；确认 -TaskId 与 -Base 是否匹配。'
}
$allow = @(Get-ScaffoldCardAllowPathFromText -CardText $cardText)
if ($allow.Count -eq 0) {
  Stop-Undecidable "基线那份卡的 front-matter 未提取到 allow_paths 列表项（${baseRef}:${cardInBase}）" '补齐**基线上**卡片的 allow_paths（须是块式列表——每项一行 `  - path`；行内 flow 写法 `[a, b]` 一律解析为 0 项，check-cards 闸 10d 亦拒），然后重跑。'
}

$oos = @(Get-ScaffoldOutOfScopePath -ChangedPath $changed -AllowPath $allow)
if ($oos.Count -gt 0) {
  Write-Host "[SCOPE-BLOCK] 范围检查 BLOCK：越界改动（不在卡 allow_paths 内）：$($oos -join ', ')" -ForegroundColor Red
  # 与 ship 范围闸同源的处置文案；T36-DOCTRINE：watershed 后禁历史改写，故不给 rebase 建议。
  Write-Host '[SCOPE-FIX] 处置（L18）：卡外必要改动应在本分支用反向提交撤出，或让 base 前移吸收后重跑；确属本卡则先在 main 扩卡 allow_paths（watershed 后禁 rebase/改写历史）。' -ForegroundColor Yellow
  exit 1
}
Write-Host "[SCOPE-PASS] 范围检查 PASS（$($changed.Count) 个改动文件均在卡 allow_paths 内，$($allow.Count) 条 allow 条目）" -ForegroundColor Green
exit 0
