#requires -Version 7
# 共享：范围闸（allow_paths 越界拦截）的**判定核**——卡 allow_paths 取值 / 改动清单求值 / 段级匹配器。
#
# 为什么是共享件（TD93 item①）：判定逻辑此前只活在 task.ps1 的内联 ship 块里。但「任何已 push 状态的手工恢复」
# （docs/DEVOPS-WORKFLOW.md）是**绕过 ship 主路**的最后手段平面，而 **CI 没有范围闸**（TD89 的根因）——那条恢复
# 序列里的范围核对，遂是该平面上范围闸的唯一承载，且原本只是散文（人眼比对 `git diff --name-only` 输出，
# **没有退出码**，漏看一行不会有任何信号）。把核抽到本文件后，独立入口 scripts/check-scope.ps1 与 ship 打的是
# **同一枚核**：恢复序列因此有了可跑命令，也不必写「等价的第二实现」——后者会重演 TD68（review.ps1 修了、
# task.ps1 没修，被 R3 抓出）。同 _gitbase.ps1 头注所述之理，在范围闸面的同款应用。
#
# 调用方职责（本库只判定、不处置）：allow 列表为空 / diff 求值失败 / 基线不可解析 —— **调用方一律 fail-closed**
# （不确定 ≠ 放行）。输入有效后的**匹配方向**才宽松（前缀 / glob、正斜杠归一，宁放不误拦），绝不误拦合法改动。
. (Join-Path $PSScriptRoot '_cards.ps1')   # Get-FrontMatter / Get-YamlBlockListItems（卡 front-matter 解析的单一实现）

# 卡**原文**的 allow_paths（反斜杠归一为正斜杠）。取不到即返回空数组，由调用方 fail-closed。
# 收**文本**而非路径：判定标准应来自受信基线，调用方常用 `git show <baseRef>:specs/tasks/<id>.md` 取原文
# （见 check-scope.ps1 的 [SCOPE-NOCARD] 一段——读被审检出里的卡，会让分支自行扩 allow_paths 绕过本闸）。
function Get-ScaffoldCardAllowPathFromText {
  param([string]$CardText)
  # 先用 check-cards 同款正则取 front-matter（防行走漏进正文、把正文列表项当路径），再行走收集 allow_paths。
  # 块式**专用**取值器（非 Get-YamlListItems）：行内 flow 写法解析为 0 项 → 调用方空列表分支 fail-closed 拦停。
  # check-cards 已在上游拒绝行内写法（闸 10d），这里是它失效时的后置防线；两个取值器的差异是有意的，见 _cards.ps1 注释。
  $fm = Get-FrontMatter $CardText
  if (-not $fm) { return @() }
  return @(Get-YamlBlockListItems $fm 'allow_paths' | ForEach-Object { $_ -replace '\\', '/' })
}

# Same, but reading the copy ON DISK. NEITHER scope gate uses it: ship and check-scope.ps1 both read the
# base copy through `git show` (T241/TD247 - "main checkout == baseline" is a guarantee L86 never gave,
# and an uncommitted widening was honoured because of it). It is left for the two NON-JUDGING callers in task.ps1: the concurrent-session notice and the PR title, which do ask what this checkout looks like right now.
function Get-ScaffoldCardAllowPath {
  param([Parameter(Mandatory)][string]$CardPath)
  return @(Get-ScaffoldCardAllowPathFromText -CardText (Get-Content -Raw $CardPath))
}

# 相对基线引用的改动清单（`<BaseRef>...<TipRef>`）。git 非零退出即 throw——调用方据此 fail-closed。
# TipRef 缺省 'HEAD'（ship 侧语义：$Wt 的 HEAD 就是本卡分支尖端，行为与抽核前逐字一致）；
# 独立检查器显式传 refs/heads/<TaskId>，好让判定对象由**卡 id** 锚定、不随 -Path 指到哪个检出而漂移。
function Get-ScaffoldChangedPath {
  param(
    [Parameter(Mandatory)][string]$GitDir,    # 在哪个 git 目录/工作树里求 diff
    [Parameter(Mandatory)][string]$BaseRef,   # 已解析的基线**引用**（全限定，见 _gitbase.ps1）
    [string]$TipRef = 'HEAD'                  # 被判定的尖端引用
  )
  # -c core.quotepath=false（TD54/TD-117）：否则 git 把非 ASCII 路径 C-quote 成 "docs/\346..." → 与 allow_paths
  # 的原样 UTF-8 条目匹配落空 → 合法的 CJK-名文件被判假越界 BLOCK（fail-closed 但真误拦）。
  # -c diff.renames=false（TD-202）：git 默认开启改名探测，把「删 A + 增 B（高相似）」折叠成单条 rename 记录、
  # 只印目标 B——被删的卡外 origin A 从此清单消失，范围闸看不到它离场（把仓内任意文件 relocate 进卡内目标目录即
  # 绕过越界拦截）。禁用改名探测令 origin 与 destination 各自作为独立路径重现、各受既有 allow_paths 检查；
  # 亦覆盖相似度改名（移动+编辑）。绝不去解析 --name-status 的 R100 记录（多一条代码路径与失败面）。
  $changed = @(& git -C $GitDir -c core.quotepath=false -c diff.renames=false diff --name-only "$BaseRef...$TipRef" 2>$null | Where-Object { $_ })
  if ($LASTEXITCODE -ne 0) { throw "git diff --name-only $BaseRef...$TipRef 非零退出（基线 '$BaseRef' 或尖端 '$TipRef' 在该仓不可解析？）" }
  return $changed
}

# 纯匹配器：返回 $ChangedPath 中不被任何 $AllowPath 条目覆盖的路径。无 git、无 IO、可单测。
# 注意：$AllowPath 为空时**全部**改动都判越界——「空 allow 即 fail-closed」的语义由调用方在调用前显式判定并给
# 专门的修法文案（本函数不区分「卡没写 allow_paths」与「真的全部越界」）。
function Get-ScaffoldOutOfScopePath {
  param(
    [string[]]$ChangedPath = @(),
    [string[]]$AllowPath = @()
  )
  # TD60/TD-123：段级前缀匹配，非字符前缀——旧写法 `$f -like ($_.TrimEnd('/')+'*')` 是纯字符串前缀，
  # allow `docs/`（trim 后 "docs*"）会让 "docs2/oob.md" 误判在范围内（字符前缀假阳性放行，反不该放行却放）；
  # allow `README.md`（"README.md*"）同理误放行 "README.md.bak"。改为「整段相等」或「以 `路径/` 开头」
  # 两种判据之一，杜绝同前缀不同路径段的误配。另外 allow 条目本身若含 `-like` 通配符特殊字符（如 `[`），
  # 直接把它拼进 `-like` 模式会被当字符类而非字面量、令这条**合法**的字面路径匹配失败（反向：本该放行却拦）——
  # 用 WildcardPattern.Escape 转义后再接 "/*"，使其在前缀分支里保持字面语义；末尾仍保留 `$f -like $_`
  # （原始、未转义）分支，让 allow 条目里刻意写的 glob（如 `frontend/**`）继续按通配符生效（不收窄既有能力）。
  return @($ChangedPath | Where-Object {
      $f = $_ -replace '\\', '/'
      -not ($AllowPath | Where-Object {
          $norm = $_.TrimEnd('/')
          $escNorm = [System.Management.Automation.WildcardPattern]::Escape($norm)
          $f -eq $norm -or $f -like "$escNorm/*" -or $f -like $_
        })
    })
}

# -- T120-SCOPE-UNPUSHED-BASE (L114 x6): why an out-of-scope REPORT may not mean an out-of-scope EDIT --
# The ship scope gate diffs the branch against the REMOTE base (fail-closed since TD68/TD84). When the operator
# commits to the LOCAL base and never pushes it - a card registration, a lessons entry - the remote base lacks
# those commits, so files they added to the base THEMSELVES surface as out-of-scope. L114 has recurred six times
# and its own text says the report alone does not make that state recognisable.
# This is the pure half of that diagnosis: given the out-of-scope paths and the paths the local base carries but
# the remote base does not, return the ones the base does NOT explain. An empty result means the unpushed base
# accounts for the entire block and the repair is a single push. A non-empty result means at least one path is a
# genuine out-of-scope edit, so the caller must stay silent rather than send the operator down the wrong repair.
# Pure by design (no git, no IO): the caller supplies both lists, so this is testable from literals and cannot
# drift between ship and any later caller - the same reason the matcher above lives here rather than inline (TD68).
# DIAGNOSIS ONLY. Nothing here decides what is blocked; Get-ScaffoldOutOfScopePath already did that, and this
# function is never allowed to widen or narrow it.
function Get-ScaffoldUnexplainedOutOfScopePath {
  param(
    [string[]]$OutOfScopePath = @(),
    [string[]]$BaseOnlyPath = @()
  )
  # Normalise BOTH sides exactly as the matcher does: a backslash on one side and a forward slash on the other
  # would otherwise read as unexplained and silence a hint that should have fired.
  $baseSet = @($BaseOnlyPath | ForEach-Object { $_ -replace '\\', '/' })
  return @($OutOfScopePath | Where-Object { ($_ -replace '\\', '/') -notin $baseSet })
}

# -- T147-CONCURRENT-SESSION-NOTICE (L114 x6, divergence half): is a SECOND session writing here? --
# T120 above named the SINGLE-session shape of a confusing scope block (the unpushed local base). This is the
# multi-session shape, and it was field-proven during the session that opened the card: a second session
# created two task cards and appended four tech-debt rows to the shared checkout mid-write, claiming three
# card numbers the first session had already allocated. Nothing announced it - the collision surfaced only
# because check-cards happened to run and emit an advisory [CARD-ID-REUSE].
#
# ADVISORY BY CONSTRUCTION. L114's own enforced_by says no gate can hold this, and a false positive refusing
# a legitimate solo ship would be worse than the silence it replaces. So this returns SIGNALS, never a
# verdict, and the caller prints them without touching its exit code. No lock, no lockfile, no mutex: the
# repo has none by design and a crashed session holding one would wedge the next.
#
# Pure (no git, no IO): the caller supplies the three observations. Each arm SUBTRACTS what this session is
# responsible for, which is what separates a signal from noise - a worktree this card opened is not a second
# writer, and a dirty registry file this card is allowed to edit is this session's own work. The registry arm
# reuses Get-ScaffoldOutOfScopePath rather than restating the matcher, so "covered by allow_paths" cannot
# come to mean two different things in two places (the TD68 reason the matcher lives here at all).
function Get-ScaffoldConcurrentSessionSignal {
  param(
    [string[]]$WorktreeName = @(),
    [AllowEmptyString()][string]$SelfCardId = '',
    [string[]]$BaseOnlyCommit = @(),
    [string[]]$DirtyRegistryPath = @(),
    [string[]]$AllowPath = @()
  )
  $signals = [System.Collections.Generic.List[object]]::new()

  $foreignWt = @($WorktreeName | Where-Object { $_ -and ($_ -cne $SelfCardId) })
  if ($foreignWt.Count -gt 0) {
    $signals.Add([pscustomobject]@{ Signal = 'foreign-worktree'; Detail = @($foreignWt) })
  }
  if (@($BaseOnlyCommit | Where-Object { $_ }).Count -gt 0) {
    $signals.Add([pscustomobject]@{ Signal = 'base-moved'; Detail = @($BaseOnlyCommit | Where-Object { $_ }) })
  }
  # A dirty shared registry is only a foreign-writer signal when THIS card is not allowed to touch it.
  $foreignReg = @(Get-ScaffoldOutOfScopePath -ChangedPath @($DirtyRegistryPath | Where-Object { $_ }) -AllowPath $AllowPath)
  if ($foreignReg.Count -gt 0) {
    $signals.Add([pscustomobject]@{ Signal = 'shared-registry'; Detail = @($foreignReg) })
  }
  return @($signals)
}

# -- T112-CORE-SELFCHECK-SCOPE (TD140 / ADR 0011): the declared self-check for the scope decision --
# This core is the shared judgement behind BOTH the ship scope gate and check-scope.ps1, so a wrong answer
# here is the difference between a merge that respects allow_paths and one that does not. Until now it was
# covered only indirectly, through selftest fixtures that drive whole scripts.
# The rejected shape is the real bug this matcher was written to fix (TD60/TD-123):
#   prefix-substring - match a changed path against an allow entry by bare CHARACTER prefix instead of by
#                      path SEGMENT. That is the pre-TD60 implementation, and it is a fail-OPEN error:
#                      allow `docs/` would admit `docs2/oob.md`, allow `README.md` would admit
#                      `README.md.bak`. Both are cases below, and both flip under the variant.
function Test-ScaffoldScopeOutOfScopeVia($ChangedPath, $AllowPath, $Variant) {
  if ($Variant -eq 'prefix-substring') {
    return @($ChangedPath | Where-Object {
        $f = $_ -replace '\\', '/'
        -not ($AllowPath | Where-Object { $f -like (($_.TrimEnd('/')) + '*') })
      })
  }
  return @(Get-ScaffoldOutOfScopePath -ChangedPath $ChangedPath -AllowPath $AllowPath)
}

# Declared examples for the scope decision. Returns findings as strings and never throws. Two tables: the
# matcher (which the -Variant exercises) and the allow_paths reader, whose fail-closed behaviour on an
# inline flow list is a property check-cards enforces upstream and this core backstops. Fully hermetic -
# no git, no IO, every input a literal.
function Test-ScaffoldScopeExamples {
  [CmdletBinding()]
  param([ValidateSet('prefix-substring')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()

  # --- the matcher: which changed paths fall outside allow_paths ---
  $matchCases = @(
    @{ what = 'an exactly named file is in scope'; changed = @('README.md'); allow = @('README.md'); expect = 0 }
    @{ what = 'a directory entry covers what is under it'; changed = @('docs/a.md'); allow = @('docs/'); expect = 1 - 1 }
    @{ what = 'a SEGMENT boundary is respected - docs/ must not admit docs2/'; changed = @('docs2/oob.md'); allow = @('docs/'); expect = 1 }
    @{ what = 'a file-name prefix is not a match - README.md must not admit README.md.bak'; changed = @('README.md.bak'); allow = @('README.md'); expect = 1 }
    @{ what = 'backslashes in a changed path are normalised before matching'; changed = @('docs\a.md'); allow = @('docs/'); expect = 0 }
    @{ what = 'a deliberate glob entry still works as a glob'; changed = @('frontend/x/y.ts'); allow = @('frontend/**'); expect = 0 }
    @{ what = 'an empty allow list puts every change out of scope (caller fail-closes on this)'; changed = @('a.md', 'b.md'); allow = @(); expect = 2 }
    @{ what = 'wildcard metacharacters in an allow entry stay literal in the prefix branch'; changed = @('docs/[wip]/a.md'); allow = @('docs/[wip]/'); expect = 0 }
    @{ what = 'one out-of-scope path among several in-scope ones is still reported'; changed = @('docs/a.md', 'scripts/x.ps1', 'docs/b.md'); allow = @('docs/'); expect = 1 }
  )
  foreach ($c in $matchCases) {
    $got = @(Test-ScaffoldScopeOutOfScopeVia $c.changed $c.allow $v)
    if ($got.Count -ne $c.expect) { $findings += "[SCOPE-EXAMPLE] case '$($c.what)' reported $($got.Count) out-of-scope path(s), expected $($c.expect). This matcher decides what the ship scope gate blocks, so a wrong answer here is a merge that ignores allow_paths (TD60/TD-123). [FIX] fix the matcher, never the example." }
  }

  # --- the allow_paths reader: card TEXT in, normalised entries out ---
  $blockCard = (@('---', 'id: TZ-EXAMPLE', 'allow_paths:', '  - scripts/x.ps1', '  - docs\y.md', '---', 'body') -join "`n")
  $flowCard = (@('---', 'id: TZ-EXAMPLE', 'allow_paths: [scripts/x.ps1, docs/y.md]', '---', 'body') -join "`n")
  $readCases = @(
    @{ what = 'a block list yields one entry per item, backslashes normalised'; text = $blockCard; expect = 2; contains = 'docs/y.md' }
    @{ what = 'an inline flow list yields ZERO entries, so the caller fail-closes (check-cards rejects it upstream; this is the backstop)'; text = $flowCard; expect = 0; contains = $null }
    @{ what = 'text with no front matter yields nothing'; text = 'no front matter here'; expect = 0; contains = $null }
  )
  foreach ($c in $readCases) {
    $got = @(Get-ScaffoldCardAllowPathFromText -CardText $c.text)
    if ($got.Count -ne $c.expect) { $findings += "[SCOPE-EXAMPLE] case '$($c.what)' read $($got.Count) allow_paths entry/entries, expected $($c.expect). [FIX] fix the reader, never the example." }
    elseif ($c.contains -and ($got -notcontains $c.contains)) { $findings += "[SCOPE-EXAMPLE] case '$($c.what)' read $($got.Count) entries but not the expected '$($c.contains)' - the count is right and the content is not, which a count-only assertion would miss. [FIX] fix the reader, never the example." }
  }
  # --- the unpushed-base diagnosis: which out-of-scope paths the local-only base does NOT account for ---
  # Direction matters more than the count here. Case 2 is the one that protects the operator: if ANY path is a
  # real out-of-scope edit, the hint must stay silent, because sending someone to `git push origin <base>` when
  # they actually have a rogue edit wastes the push and leaves the block standing with a wrong explanation.
  $unexplainedCases = @(
    @{ what = 'every out-of-scope path is carried by the unpushed base, so the block is fully explained'; oos = @('specs/tasks/T1-X.md'); baseOnly = @('specs/tasks/T1-X.md'); expect = 0 }
    @{ what = 'one out-of-scope path the base does not explain must keep the hint silent'; oos = @('specs/tasks/T1-X.md', 'scripts/rogue.ps1'); baseOnly = @('specs/tasks/T1-X.md'); expect = 1 }
    @{ what = 'an empty base-only list explains nothing, so every out-of-scope path stays unexplained'; oos = @('a.md', 'b.md'); baseOnly = @(); expect = 2 }
    @{ what = 'backslashes are normalised on BOTH sides before comparing'; oos = @('specs\tasks\T1-X.md'); baseOnly = @('specs/tasks/T1-X.md'); expect = 0 }
    @{ what = 'a base carrying extra unrelated paths still explains the whole block'; oos = @('a.md'); baseOnly = @('a.md', 'b.md', 'c.md'); expect = 0 }
  )
  foreach ($c in $unexplainedCases) {
    $got = @(Get-ScaffoldUnexplainedOutOfScopePath -OutOfScopePath $c.oos -BaseOnlyPath $c.baseOnly)
    if ($got.Count -ne $c.expect) { $findings += "[SCOPE-EXAMPLE] case '$($c.what)' reported $($got.Count) unexplained path(s), expected $($c.expect). This decides whether ship prints the [SCOPE-UNPUSHED-BASE] hint; a wrong answer either hides the real cause (L114 x6) or sends the operator to push a base that was never the problem. [FIX] fix the predicate, never the example." }
  }

  # --- the concurrent-session signals (T147): which observations indicate a SECOND writer in this checkout ---
  # The negative direction is the one that keeps this usable: a solo checkout must be silent, or the notice
  # becomes noise that operators learn to skip, which is worse than not printing it. Each positive case pairs
  # with the subtraction that makes it a signal rather than an artefact of this session's own work.
  $concurrentCases = @(
    @{ what = 'a solo checkout produces no signal at all'; wt = @('T1-MINE'); self = 'T1-MINE'; base = @(); reg = @(); allow = @('specs/'); expect = 0 }
    @{ what = 'a worktree this session did not open is a second writer'; wt = @('T1-MINE', 'T2-THEIRS'); self = 'T1-MINE'; base = @(); reg = @(); allow = @('specs/'); expect = 1 }
    @{ what = 'the local base carrying commits this session did not make is a second writer'; wt = @('T1-MINE'); self = 'T1-MINE'; base = @('abc123 specs: their card'); reg = @(); allow = @('specs/'); expect = 1 }
    @{ what = 'a dirty shared registry OUTSIDE this card allow_paths is a second writer'; wt = @('T1-MINE'); self = 'T1-MINE'; base = @(); reg = @('specs/tech-debt-tracker.md'); allow = @('scripts/'); expect = 1 }
    @{ what = 'a dirty registry this card IS allowed to edit is this session own work, not a signal'; wt = @('T1-MINE'); self = 'T1-MINE'; base = @(); reg = @('specs/tech-debt-tracker.md'); allow = @('specs/tech-debt-tracker.md'); expect = 0 }
    @{ what = 'all three observations firing at once report all three signals'; wt = @('T2-THEIRS'); self = 'T1-MINE'; base = @('abc123 x'); reg = @('specs/tech-debt-tracker.md'); allow = @('scripts/'); expect = 3 }
  )
  foreach ($c in $concurrentCases) {
    $got = @(Get-ScaffoldConcurrentSessionSignal -WorktreeName $c.wt -SelfCardId $c.self -BaseOnlyCommit $c.base -DirtyRegistryPath $c.reg -AllowPath $c.allow)
    if ($got.Count -ne $c.expect) { $findings += "[SCOPE-EXAMPLE] case '$($c.what)' reported $($got.Count) concurrent-session signal(s), expected $($c.expect). Too few and the operator meets a confusing scope block with no explanation (L114 x6); too many and the notice becomes noise that gets skipped. [FIX] fix the predicate, never the example." }
  }

  return $findings
}
