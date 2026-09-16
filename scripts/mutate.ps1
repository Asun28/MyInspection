#requires -Version 7
<#
.SYNOPSIS
  变异证据一等 runner（T63/TD119）：把 L165「每道守卫配一枚单句删除变异」的证据税从「每枚付全量 selftest」
  降到「每枚只付目标闸」，并把 T53/T55/T56/T61 各卡即席手搓的批脚本（scratchpad + TSV + 轮询，会话间易丢、
  记账已实翻一次车）升级为登记表驱动、结果 tracked、增量重测的仓内工具。

.DESCRIPTION
  登记表（.psd1，Import-PowerShellDataFile 纯数据不执行；**启动即整表校验，任一项不合法即 [MUT-REG-INVALID]
  fail-closed 退出、零变异执行**——含 psd1 解析失败/条目非哈希表/id 缺失或非法 等形状错误，R3 r3 #4）：
    @{ mutations = @(
         @{ id      = 'N1'                       # 枚 id：^[A-Za-z0-9._-]+$（全表唯一、TSV 安全）
            target  = 'scripts/foo.ps1'          # Root-relative path: must resolve inside Root; absolute paths rejected
            anchor  = '  exact line to delete'   # 被删的那一行原文（**单物理行**：内嵌 CR/LF 拒，R3 r3 #5）
            # afterAnchor = '  previous line'    # 可选：锚不唯一时用「前行+本行」对消歧（同为单物理行）
            gate    = '17ac'                     # 目标闸 token：仅限 [A-Za-z0-9._,-]（防注入）
            mustFind      = @('[MUT-...')        # 正向证据（子串）；四通道合计至少一条正向
            # mustFindRegex    = @('^\[MUT-')    # 正向证据（正则，预检可编译——R3 r3 相邻轮）
            # mustNotFind      = @('...')        # 负向证据（子串）
            # mustNotFindRegex = @('...')        # 负向证据（正则）
            # guarded = @('scripts/bar.ps1')     # optional: extra guarded files (enter the cache key; same Root confinement)
         } ) }
  全部证据锚必须**纯可打印 ASCII**（L165⑤）。
  判据分类器（L167）：**未变异控制组先行**——每个解析后的探针命令在本批**变异前**先跑一次原样探针，非零即
  [MUT-PRISTINE-RED] fail-closed 零变异（本来就红的闸发什么证据都归因不到变异上，R3 r3 #1）；随后每枚：探针
  **非零退出 且 正向证据全命中 且 负向证据全不命中** 才记 [MUT-OK]；零退出 = [MUT-SURVIVED]；非零但证据不符 =
  [MUT-BAD-EVIDENCE]。任一非 OK/REUSE 枚 ⇒ runner 非零退出。

  增量重测（TD119 修向核心）：结果行记 cacheKey = SHA256( 枚定义规范化串（长度前缀 + 0x1E/0x1F 分隔防字段
  边界碰撞） + RunCommand 模板原文 + target/scripts/selftest.ps1/guarded… 各文件 SHA256 拼接 )——改判据、
  换模板、动任一被守字节即失配重测；未变且上次 OK 的枚打 [MUT-REUSE] 沿用（-Force 强制全重测）。
  结果 TSV 列 schema 版本化：头不符现行 schema 即 [MUT-RESULTS-RESET] 重建（旧行作废，不拿错列当键）。
  -Results must not be the SAME file as any target or the registry — path-string plus NTFS file ID
  (hardlinks share an ID; checked when fsutil is available).

  批安全（L196/L178 内建）：变异前落 <target>.mutbak；还原后 SHA256 字节比对（不等即 [MUT-RESTORE-FAIL] 且
  保留 .mutbak）；启动发现陈旧 .mutbak 时**先验身份再还原**（R3 r3 #3）——当前字节 == 备份（只清备份）或
  == 备份套用本表某枚变异的结果（死批遗留态，还原）才动作；其余一律 [MUT-BAK-DIVERGENT] fail-closed 双文件
  保留（陈旧备份不得覆掉你更新的编辑）；基线脏守卫（git 追踪的 target 有未提交改动 ⇒ [MUT-BASELINE-DIRTY]，
  -AllowDirty 显式放行）；变异体写盘保留源 BOM 态；**行感知删除**：锚可位于无尾随换行的文件末行，删除保持原
  EOF 形态（R3 r3 #5）；逐枚即时落结果行。**超 1 小时的批仍按 L196 派 OS 计划任务**。

  哨兵集（机检面恒 ASCII，L165⑤）：[MUT-OK] [MUT-SURVIVED] [MUT-BAD-EVIDENCE] [MUT-REUSE] [MUT-ANCHOR-MISS]
  [MUT-RESTORE-FAIL] [MUT-STALE-BAK-RESTORED] [MUT-BAK-DIVERGENT] [MUT-BASELINE-DIRTY] [MUT-PRISTINE-RED]
  [MUT-REG-INVALID] [MUT-RESULTS-RESET] [MUT-ORPHAN-DROPPED] [MUT-ROW-MALFORMED] [MUT-BAK-OWNED] [MUT-SUMMARY]。
  **runner 的绿不是验收**：[MUT-SUMMARY] 全 OK 只是证据批完成，卡验收恒为全 17 闸覆盖
  （本地无参全量 selftest，或 CI 分片并集全绿——T64/T65）。

  Narrowed contract (TD133, resolved 2026-08-16 by T71 per the tracker's sanctioned narrowing option):
  the positive mustFind channel is the load-bearing evidence channel - every entry must carry at least
  one positive pattern (enforced above), and per-channel deletion mutations are NOT owed for the regex/
  negative channels; per-guard exhaustiveness of a card's registry is that card's discipline, not a
  runner property; BOM preservation is proven by the SHA256 restore compare on every mutation (no
  separate byte-level probe is owed). Registry hygiene the runner DOES own: unknown keys are rejected
  at validation, orphan results rows are dropped at load, malformed rows are never reused.

.PARAMETER Registry   登记表 .psd1 路径（必填）。
.PARAMETER Only       只跑指定枚 id（逗号分隔）。每个 id 必须存在于登记表、选集非空，否则 fail-closed。
.PARAMETER Force      忽略增量沿用，全部实跑。
.PARAMETER RunCommand 探针命令模板，{gate} 被替换为已过字符集校验的 gate token。默认 = selftest 滤过跑。
.PARAMETER Results    结果 TSV 路径；缺省 = 登记表同目录同名 -results.tsv。
.PARAMETER Root       目标文件根（缺省 = 仓库根）。target/guarded 一律相对 Root 解析且不得越界。
.PARAMETER AllowDirty target 相对 git HEAD 有未提交改动时仍放行（对未提交实现跑批的显式口子）。
.PARAMETER AsLibrary  库模式：只定义 Test-MutateRootProbeMismatch 即返回——不校验 Registry / 不跑批 / 不 exit
                      （供卡 DoD 与 selftest 17ac 复用；镜像 check-licenses.ps1 / check-secrets.ps1 -AsLibrary）。
.EXAMPLE
  pwsh -File scripts\mutate.ps1 -Registry specs\mutations\T63-TD119-MUTATION-RUNNER.psd1
#>
[CmdletBinding()]
param(
  # Optional ONLY because -AsLibrary returns before any registry is read (T209/TD205). Mandatory here made
  # the documented library mode uncallable: a dot-source for the pure functions could not satisfy it, which
  # is why no caller had ever used the mode the .PARAMETER text offers. Real runs still fail closed below.
  [string]$Registry = '',
  [string[]]$Only = @(),
  [switch]$Force,
  [string]$RunCommand = 'pwsh -NoProfile -File scripts/selftest.ps1 -Only {gate}',
  [string]$Results = '',
  [string]$Root = '',
  [switch]$AllowDirty,
  [switch]$AsLibrary
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── T98-MUT-ROOT-GUARD: the one -Root combination that measures a tree it never mutated ──
# -Root rebinds where `target`/`guarded` resolve. It does NOT rebind where the probe runs: Invoke-Probe does
# Push-Location $RepoRoot, and $RepoRoot comes from this script's own location. Point -Root at a worktree from
# the MAIN checkout's copy and the batch plants mutations in the worktree while probing the main checkout -
# a tree that never saw the edit, so it stays green and every mutation is recorded [MUT-SURVIVED].
# Measured at T90-ADR-FORMAT: ok=0 survived=5 that way, ok=5 survived=0 from the worktree's own copy, same
# registry (TD144/L235). The pristine control group cannot catch it: that run executes in the same wrong tree
# and passes, so the one guard built for "the probe is not measuring what you think" is blind to this member.
#
# A caller who supplies -RunCommand has taken responsibility for what the probe runs, so the mismatch stops
# being a mistake - and that clause is load-bearing, not a convenience: selftest 17ac drives this runner
# against temp trees with stub gates exactly that way, and refusing every mismatch would take the sub-gate
# down with it. Hence the guard is the CONJUNCTION, never the mismatch alone.
#
# Rejected axis (owner decision): making -Root authoritative for the probe. Friendlier, but it silently
# changes what every existing registry measures with no run announcing the shift, and the artifact most at
# risk is the evidence record itself. Fail-closed refusal cannot change any existing verdict.
function Test-MutateRootProbeMismatch {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$RepoRoot,
    [switch]$RunCommandBound
  )
  if ($RunCommandBound) { return $false }
  return ($Root.TrimEnd('\', '/') -ne $RepoRoot.TrimEnd('\', '/'))
}

# Hoisted above the -AsLibrary early return (T209/TD205): the mode's own .PARAMETER text says it exists
# so card DoDs and 17ac can reuse this file, and this is the pure function they need - it was defined
# BELOW the return, so library mode never exposed it. A function definition has no side effect, and the
# region between the return and here does (it resolves paths and owns exit 2 paths), so the definition
# moves up rather than the return moving down.
function Get-MutatedVariant([string]$srcText, [hashtable]$m) {
  # 从原文推导「套用该枚变异后的文本」；锚不命中/不唯一返回 $null。**整物理行匹配**（R3 r4 #2）：
  # 命中必须始于行首（文首或紧随换行）——否则 `anchor+EOL` 会匹配到**更长行的尾部**、删掉半行；
  # 「行+EOL 形态」与「无尾随换行的 EOF 行形态」合并计数，总数恰 1 才动手（两形态并存 = 歧义，拒）。
  $eolV = if ($srcText.Contains("`r`n")) { "`r`n" } else { "`n" }
  $blockV = if ($m.ContainsKey('afterAnchor')) { [string]$m.afterAnchor + $eolV + [string]$m.anchor } else { [string]$m.anchor }
  $replV = if ($m.ContainsKey('afterAnchor')) { [string]$m.afterAnchor } else { '' }
  $lineStarts = @()
  foreach ($mm in [regex]::Matches($srcText, [regex]::Escape($blockV))) {
    $atLineStart = ($mm.Index -eq 0) -or ($srcText[$mm.Index - 1] -eq "`n")
    $tailIdx = $mm.Index + $mm.Length
    $atLineEnd = ($tailIdx -eq $srcText.Length) -or ($srcText.Substring($tailIdx).StartsWith($eolV))
    if ($atLineStart -and $atLineEnd) { $lineStarts += $mm.Index }
  }
  if ($lineStarts.Count -ne 1) { return $null }
  $idx = $lineStarts[0]
  $tail = $idx + $blockV.Length
  $hasEol = ($tail -lt $srcText.Length)
  if ($replV) {
    # afterAnchor 对：保留前行（含其原有换行形态），只删锚行。
    $keepEnd = $idx + $replV.Length
    # T209/TD205: $keepEnd stops at the END OF THE TEXT of the kept line, before its newline - so without
    # $eolV here the line after the anchor is glued onto the kept one and TWO lines change where the registry
    # declares one. The comment above is the contract; this is the code finally agreeing with it.
    if ($hasEol) { return $srcText.Substring(0, $keepEnd) + $eolV + $srcText.Substring($tail + $eolV.Length) }
    return $srcText.Substring(0, $keepEnd)
  }
  if ($hasEol) { return $srcText.Substring(0, $idx) + $srcText.Substring($tail + $eolV.Length) }
  # EOF 行（无尾随换行）：连同其前导换行一起摘除，保持原 EOF 形态。
  $lead = if ($idx -ge $eolV.Length) { $eolV.Length } else { 0 }
  return $srcText.Substring(0, $idx - $lead)
}

# ── T269/TD271: a stale .mutbak may only be self-healed when NOBODY OWNS IT ──────────────────────
# The self-heal below asks whether the bytes on disk LOOK like one of this registry's own mutations.
# That test does not require the state to have COME from this registry - only that some entry here
# reproduces the same bytes, which holds whenever two registries share a (target, anchor) pair.
# Measured 2026-09-02: 5 such pairs across the 93 live registries, and reproduced end to end - a
# registry that planted nothing restored a peer's in-flight mutant state and deleted its backup.
# The distinguishing fact is LIVENESS, not shape, so the stamp below records who owns the backup and
# the guard refuses while that owner is alive. It is asked BEFORE the identity test and never
# instead of it: when the owner is gone, control falls through and [MUT-STALE-BAK-RESTORED] still
# recovers a genuinely killed batch. Hoisted above the -AsLibrary return so card DoDs and 17ac can
# drive the pure decision without running a batch (T209/TD205, same reason as Get-MutatedVariant).
#
# Why a stamp and not a lock: a lock must be RELEASED, so a killed batch leaves it held and the next
# run deadlocks on exactly the residue the self-heal exists to clear. A stamp is READ, so when the
# owner dies the guard degrades to today's behaviour. That asymmetry is the whole argument, and the
# 'ignore-stamp' and 'malformed-blocks' rejected shapes below are each a way of accidentally
# rebuilding the lock's failure mode inside the stamp.

function Get-MutateBakOwnerVariant {
  <#
  .SYNOPSIS  The rejected shapes Get-MutateBakOwnerFinding declares, as data a dod can count.
  .DESCRIPTION
    Exists because a dod_command may contain no `$` (L95) and so cannot filter a mixed attribute
    collection, while `.Attributes.ValidValues` throws under this file's StrictMode. The list is
    checked against the real [ValidateSet] by the declared examples below, so the two cannot drift
    silently - that check is what makes this a second declaration rather than a second SOURCE.
  #>
  [CmdletBinding()]
  param()
  return @('ignore-liveness', 'ignore-stamp', 'self-owned-refused', 'malformed-blocks', 'spaced-registry', 'truncating-registry')
}

function Get-MutateBakStampText {
  <#
  .SYNOPSIS  The sidecar line written beside <target>.mutbak naming who owns it.
  .DESCRIPTION
    ONE physical line, pure printable ASCII (L165), so it can never decay a line-oriented matcher the
    way a multi-line value would. Registry is recorded as given rather than resolved: it is for a
    human reading the refusal, and an absolute path would leak the owner's checkout layout.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][int]$OwnerPid,
    [Parameter(Mandatory)][string]$Registry,
    [Parameter(Mandatory)][string]$StartedUtc
  )
  # registry LAST: it is the only free-form field, so it is the only one that can contain a space,
  # and at the end of the line it runs to EOL and round-trips. Measured before this order existed:
  # a path with a space produced a stamp this file's own parser rejected, and the guard then returned
  # ZERO findings for a live foreign owner - silently disabled (T272, R3 #9 on T269).
  return "pid=$OwnerPid started=$StartedUtc registry=$Registry"
}

function Read-MutateBakStamp {
  <#
  .SYNOPSIS  Parse a stamp line, or $null when it is absent or malformed.
  .DESCRIPTION
    $null means "no usable owner recorded" and the caller falls through to the identity test. It does
    NOT mean "owned": a corrupt sidecar must not become a permanent denial of service on a target,
    which is the 'malformed-blocks' rejected shape.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$Text, [switch]$Strict, [switch]$Truncating)
  # -Strict restores the REJECTED shape (registry as \S+), which cannot match a path containing a space.
  # It exists so the declared examples can drive that shape and see the guard go silent.
  $rx = if ($Strict) { '^pid=(?<pid>[0-9]+)[ \t]+started=(?<at>\S+)[ \t]+registry=(?<reg>\S+)[ \t]*$' }
        # -Truncating is the rejected shape a COUNT cannot see: the capture stops at the first SPACE while the
        # trailing .* absorbs the rest, so the line still PARSES, the finding count stays 1, and only an
        # equality comparison notices the value came back truncated. This is the wrong reader R3 named.
        elseif ($Truncating) { '^pid=(?<pid>[0-9]+)[ \t]+started=(?<at>\S+)[ \t]+registry=(?<reg>\S+).*$' }
        else { '^pid=(?<pid>[0-9]+)[ \t]+started=(?<at>\S+)[ \t]+registry=(?<reg>.+?)[ \t]*$' }
  $m = [regex]::Match([string]$Text, $rx)
  if (-not $m.Success) { return $null }
  # TryParse, never a cast: an unbounded digit run overflows Int32 and a cast THROWS out of a function
  # whose whole contract is to return $null for anything it cannot read (T272, R3 #9 on T269).
  $parsedPid = 0
  if (-not [int]::TryParse($m.Groups['pid'].Value, [ref]$parsedPid)) { return $null }
  if ($parsedPid -le 0) { return $null }
  return [pscustomobject]@{ OwnerPid = $parsedPid; Registry = $m.Groups['reg'].Value; StartedUtc = $m.Groups['at'].Value }
}

function Get-MutateBakOwnerFinding {
  <#
  .SYNOPSIS  May this invocation touch a stale .mutbak? Empty = yes, fall through to the identity test.
  .DESCRIPTION
    Liveness is passed IN rather than probed here, so the decision is a pure function the declared
    examples can drive in both directions without spawning a process - and so the caller decides
    liveness from the recorded pid alone. That last point is not a style choice: L278 measured, over
    three attempts, that any process-list guard whose pattern appears as a literal in its own command
    line matches itself, so matching command-line TEXT is the one implementation that must not be used.
    Each -Variant deletes exactly one narrowing, and each names a wrong design rather than a typo.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$StampText,
    [Parameter(Mandatory)][int]$SelfPid,
    [Parameter(Mandatory)][bool]$OwnerAlive,
    [ValidateSet('ignore-liveness', 'ignore-stamp', 'self-owned-refused', 'malformed-blocks', 'spaced-registry', 'truncating-registry')][string]$Variant = '',
    [string]$Target = 'the target'
  )
  $raw = [string]$StampText
  $s = if ($Variant -eq 'spaced-registry') { Read-MutateBakStamp $raw -Strict } elseif ($Variant -eq 'truncating-registry') { Read-MutateBakStamp $raw -Truncating } else { Read-MutateBakStamp $raw }
  $owned = "[MUT-BAK-OWNED] $Target has a .mutbak owned by pid=$(if ($s) { $s.OwnerPid } else { '(unreadable)' }) registry=$(if ($s) { $s.Registry } else { '(unreadable)' }) started=$(if ($s) { $s.StartedUtc } else { '(unreadable)' }). That batch is STILL ALIVE, so the backup and the planted mutation are not yours to restore or delete - restoring here would unplant a mutation under a running probe and it would bank a false survivor. Wait for that pid to exit, or kill it deliberately and re-run."

  # No stamp: every .mutbak written before this guard existed, plus any hand-made one. Fall through.
  if (-not $raw.Trim()) { if ($Variant -eq 'ignore-stamp') { return @($owned) } return @() }
  # Unparseable stamp: corrupt sidecar, not an owner. Fall through rather than block forever.
  if (-not $s) { if ($Variant -eq 'malformed-blocks') { return @($owned) } return @() }
  # Our own stamp: plant, interrupt, retry in the same process must still self-heal.
  if ($s.OwnerPid -eq $SelfPid) { if ($Variant -eq 'self-owned-refused') { return @($owned) } return @() }
  # Owner gone: this IS the killed-batch case [MUT-STALE-BAK-RESTORED] exists for.
  if (-not $OwnerAlive) { if ($Variant -eq 'ignore-liveness') { return @($owned) } return @() }
  return @($owned)
}

function Test-MutateBakOwnerExamples {
  <#
  .SYNOPSIS  The declared cases for Get-MutateBakOwnerFinding. Empty = the live decision agrees.
  .DESCRIPTION
    Default must return nothing. Each -Variant deletes one narrowing in the decision and must then
    produce at least one finding - without those arms the guard is indistinguishable from one that
    refuses everything, or from one that refuses nothing.
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-liveness', 'ignore-stamp', 'self-owned-refused', 'malformed-blocks', 'spaced-registry', 'truncating-registry')][string]$Variant)
  $live = Get-MutateBakStampText -OwnerPid 4242 -Registry 'specs/mutations/peer.psd1' -StartedUtc '2026-09-03T00:00:00Z'
  $mine = Get-MutateBakStampText -OwnerPid 999 -Registry 'specs/mutations/mine.psd1' -StartedUtc '2026-09-03T00:00:00Z'
  $cases = @(
    @{ what = 'no stamp at all, so this backup predates the guard and the identity test decides'; stamp = ''; self = 999; alive = $true; expect = 0 }
    @{ what = 'a live owner that is not us - the collision this guard exists for'; stamp = $live; self = 999; alive = $true; expect = 1 }
    @{ what = 'the owner is gone, which is the killed-batch case the self-heal exists for'; stamp = $live; self = 999; alive = $false; expect = 0 }
    @{ what = 'our own stamp, so a retry inside the same process is not blocked'; stamp = $mine; self = 999; alive = $true; expect = 0 }
    @{ what = 'a corrupt sidecar must not become a permanent block on the target'; stamp = 'pid=nope registry='; self = 999; alive = $true; expect = 0 }
    @{ what = 'a registry path containing a SPACE must still round-trip, or the guard goes silent'; stamp = (Get-MutateBakStampText -OwnerPid 4242 -Registry 'specs/my mutations/peer.psd1' -StartedUtc '2026-09-03T00:00:00Z'); self = 999; alive = $true; expect = 1 }
    @{ what = 'an oversized pid falls through as malformed rather than throwing'; stamp = 'pid=99999999999999 started=2026-09-03T00:00:00Z registry=specs/mutations/x.psd1'; self = 999; alive = $true; expect = 0 }
    # The old pid=nope fixture also carried the obsolete field order and no started=, so it failed the
    # regex on three counts and would still fail with the pid rule deleted. This one is well formed in
    # every other respect, so the pid is the only thing that can make it fall through (T278).
    @{ what = 'a NON-NUMERIC pid in a stamp well formed in every other respect'; stamp = 'pid=nope started=2026-09-03T00:00:00Z registry=specs/mutations/x.psd1'; self = 999; alive = $true; expect = 0 }
  )
  $findings = @()
  # ROUND-TRIP, not a count. Every count-shaped assertion in this table passes for a reader that
  # returns ANY non-empty registry, so the guard could fire on a spaced path while parsing it wrongly.
  # That is the gap T272 shipped and T278 closes: the value has to come back byte-identical.
  foreach ($rt in @(
    @{ what = 'a path with single spaces'; reg = 'specs/my mutations/peer.psd1' }
    @{ what = 'a path that is nothing but tokens and spaces'; reg = 'a b c d' }
    @{ what = 'a path with no space at all, the control'; reg = 'specs/mutations/x.psd1' }
  )) {
    $rtArgs = @{}
    if ($Variant -eq 'truncating-registry') { $rtArgs['Truncating'] = $true }
    if ($Variant -eq 'spaced-registry') { $rtArgs['Strict'] = $true }
    $back = Read-MutateBakStamp (Get-MutateBakStampText -OwnerPid 4242 -Registry $rt.reg -StartedUtc '2026-09-03T00:00:00Z') @rtArgs
    if ($null -eq $back) {
      if (-not $Variant) { $findings += "[MUT-BAK-OWNER-EXAMPLE] round-trip: '$($rt.what)' did not parse back at all" }
      elseif ($Variant -eq 'spaced-registry') { $findings += "[MUT-BAK-OWNER-EXAMPLE] variant '$Variant' broke the round-trip of '$($rt.what)'" }
    }
    elseif ($back.Registry -cne $rt.reg) {
      $findings += "[MUT-BAK-OWNER-EXAMPLE] round-trip: '$($rt.what)' came back as '$($back.Registry)' instead of '$($rt.reg)' under variant '$Variant'"
    }
  }
  if (-not $Variant) {
    $vs = @((Get-Command Test-MutateBakOwnerExamples).Parameters['Variant'].Attributes | Where-Object { $_ -is [System.Management.Automation.ValidateSetAttribute] })
    $declared = @(Get-MutateBakOwnerVariant)
    if ($vs.Count -ne 1) { $findings += "[MUT-BAK-OWNER-EXAMPLE] expected exactly one ValidateSet on -Variant, found $($vs.Count)" }
    elseif (@(Compare-Object @($vs[0].ValidValues) $declared).Count -ne 0) { $findings += "[MUT-BAK-OWNER-EXAMPLE] Get-MutateBakOwnerVariant and the [ValidateSet] on -Variant have drifted: declared '$($declared -join ",")' vs validated '$(@($vs[0].ValidValues) -join ",")'" }
  }
  foreach ($c in $cases) {
    $vArgs = @{}
    if ($Variant) { $vArgs['Variant'] = $Variant }
    $got = @(Get-MutateBakOwnerFinding -StampText $c.stamp -SelfPid $c.self -OwnerAlive $c.alive -Target 'fixture' @vArgs).Count
    $want = [int]$c.expect
    if ($Variant) { if ($got -eq $want) { continue } else { $findings += "[MUT-BAK-OWNER-EXAMPLE] variant '$Variant' changed case '$($c.what)' to findings=$got (declared $want)" } }
    elseif ($got -ne $want) { $findings += "[MUT-BAK-OWNER-EXAMPLE] case '$($c.what)' judged findings=$got, declared $want. [FIX] fix the rule, never the example." }
  }
  # The filter that used to stand here kept only findings whose TEXT said "changed case" - redundant
  # when it was written (T269) and lossy the moment a second finding CLASS arrived: it silently threw
  # away every round-trip finding, so the variant that exists to falsify the equality arms reported
  # nothing at all. A finding is a finding; the callers above only add one when something is wrong.
  return $findings
}

# ── T294/TD279: a green probe means two different things once meta sites can be skipped ──────────
# Since T279 moved 22 sub-gates behind -IncludeMeta, `exit 0` from a probe run WITHOUT the switch can
# mean "the assertion fired and passed" or "the assertion never ran". The verdict branch below reads
# the exit code alone, so it records [MUT-SURVIVED] either way - the strongest negative verdict this
# corpus can hold, banked against a gate nobody asked to run. Measured on T274 before T291 corrected
# it: five entries on 14i, and the default probe never executes 14i.
# The deciding fact is already captured. selftest.ps1 prints `  [META-SKIP] <id>` for every site it
# passes over, and Invoke-Probe below already holds that whole output in the local it scans for
# mustFind. This predicate reads that one line and nothing else, so a card DoD and sub-gate 17ac can
# both drive it without running a batch - hoisted above the -AsLibrary return for the same reason
# Get-MutatedVariant and Get-MutateBakOwnerFinding are (T209/TD205).
#
# Deliberately narrow, and each near miss is a shape a looser match would swallow: [META-RAN]
# announces the OPPOSITE outcome and shares the `[META-` prefix; the [META-ORPHAN] line names
# `-IncludeMeta` in prose and is printed on runs where nothing was skipped; empty output means the
# probe crashed, which is not a skipped site. Any of the three would fire this refusal on a batch
# whose evidence a skipped meta site cannot affect.
#
# The match is ANCHORED TO A WHOLE PHYSICAL LINE, and it is measured rather than defensive. A
# substring test read the literal wherever it appeared, including inside PROSE - and sub-gate 17ac's
# own success messages name `[META-SKIP]` mid-sentence, so a real `selftest.ps1 -Only 17ac
# -IncludeMeta` run (exactly the probe template a registry targeting 17ac must use) carried the
# literal on a green run with nothing skipped, and the id extraction then invented the next word as a
# site name. Found by R3 on this card's own first round; the control that holds it is 17ac(w5).
# ONE matcher serves both the decision and the ids, which is why the predicate delegates rather than
# testing the text a second time: two patterns are two chances to disagree about what an
# announcement is, and the disagreement would be silent.
function Get-MutateMetaSkippedSite {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$ProbeOutput)
  return @([regex]::Matches($ProbeOutput, '(?m)^[ \t]*\[META-SKIP\][ \t]+(?<site>\S+)[ \t]*\r?$') | ForEach-Object { $_.Groups['site'].Value } | Select-Object -Unique)
}
function Test-MutateMetaProbeMissing {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$ProbeOutput)
  return (@(Get-MutateMetaSkippedSite -ProbeOutput $ProbeOutput).Count -gt 0)
}

if ($AsLibrary) { return }
if (-not $Registry) { Write-Host '[MUT-REG-INVALID] -Registry is required (optional only for -AsLibrary, which returns above)' -ForegroundColor Red; exit 2 }

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $Root) { $Root = $RepoRoot }
$Root = (Resolve-Path $Root).Path

if (Test-MutateRootProbeMismatch -Root $Root -RepoRoot $RepoRoot -RunCommandBound:$PSBoundParameters.ContainsKey('RunCommand')) {
  Write-Host "[MUT-ROOT-PROBE-MISMATCH] -Root '$Root' is not this script's repo root '$RepoRoot', and -RunCommand was left at its default." -ForegroundColor Red
  Write-Host "  The default probe resolves scripts/selftest.ps1 against the REPO ROOT, not against -Root, so this batch would mutate one tree and measure another - every mutation would report [MUT-SURVIVED] and the pristine control could not catch it (TD144/L235)." -ForegroundColor Yellow
  Write-Host "  [FIX] Run the copy of mutate.ps1 that lives inside the tree you want measured: pwsh -File $Root\scripts\mutate.ps1 -Registry <registry>   (this is the inverse of L86, which mandates the MAIN checkout for task.ps1 phase commands)." -ForegroundColor Yellow
  Write-Host "  [FIX] Or, if you deliberately want targets and probe in different trees, pass -RunCommand explicitly and own what it resolves against." -ForegroundColor Yellow
  exit 2
}

if (-not (Test-Path $Registry)) { Write-Host "[MUT-REG-INVALID] registry=$Registry (file not found)" -ForegroundColor Red; exit 2 }
$reg = $null; $regLoadError = ''
try { $reg = Import-PowerShellDataFile -Path $Registry } catch { $regLoadError = $_.Exception.Message }
if ($regLoadError) { Write-Host "[MUT-REG-INVALID] registry parse failed: $regLoadError" -ForegroundColor Red; exit 2 }
if (-not $reg.ContainsKey('mutations') -or @($reg.mutations).Count -eq 0) {
  Write-Host "[MUT-REG-INVALID] registry=$Registry (no mutations entries)" -ForegroundColor Red; exit 2
}
if (-not $Results) {
  $Results = [IO.Path]::ChangeExtension((Resolve-Path $Registry).Path, $null).TrimEnd('.') + '-results.tsv'
}
$onlyIds = @($Only | ForEach-Object { $_ -split '[,\s]+' } | Where-Object { $_ })
# 模板必须携 {gate} 占位（缺了会对每枚跑**同一条**命令、探针悄悄测错对象；顶层引用同时让 PSSA 的
# PSReviewUnusedParameter 正确看到本参数被消费——它扫不进嵌套函数作用域）。
if ($RunCommand -notmatch '\{gate\}') { Write-Host "[MUT-REG-INVALID] -RunCommand must contain the {gate} placeholder (got: $RunCommand)" -ForegroundColor Red; exit 2 }

# ── 帮手 ──
function Test-AsciiPrintable([string]$s) { return ($s -match '^[\x20-\x7E]+\z') }   # \z 绝对串尾：.NET 的 $ 会在结尾 LF 前也匹配，尾随换行的“可打印”值会混进 TSV 行（R3 r4 #5）
function Resolve-ConfinedPath([string]$rel) {
  # 相对 Root 解析；规范化后必须仍在 Root 内（.. 穿越拒；绝对路径另由 rooted 检查单独拒）。
  $full = [IO.Path]::GetFullPath([IO.Path]::Combine($Root, $rel))
  if (-not ($full.StartsWith($Root + [IO.Path]::DirectorySeparatorChar) -or $full -eq $Root)) { return $null }
  return $full
}
function Get-FileSha([string]$p) { (Get-FileHash -Path $p -Algorithm SHA256).Hash }
function Get-FileIdentity([string]$p) {
  # File identity (NTFS file ID: hardlinks share one) — used when fsutil exists; without fsutil (Linux/minimal
  # hosts) returns $null and callers fall back to path-string comparison (probe existence first: under
  # ErrorActionPreference=Stop a bare call to a missing native command throws — CI ubuntu confirmed).
  if (-not (Test-Path -LiteralPath $p)) { return $null }
  if (-not (Get-Command fsutil -ErrorAction SilentlyContinue)) { return $null }
  $idOut = & fsutil file queryfileid $p 2>$null
  if ($LASTEXITCODE -eq 0 -and $idOut) { return ("$idOut".Trim()) }
  return $null
}
# ── 登记表整表校验（fail-closed；R3 r1 #3/#4/#5 + r2 #1/#2/#3 + r3 #2/#4/#5）──
$regErrors = @()
$seenIds = @{}
foreach ($m in $reg.mutations) {
  if ($m -isnot [hashtable]) { $regErrors += "entry is not a hashtable (psd1 shape error): $m"; continue }
  if (-not $m.ContainsKey('id') -or -not $m.id -or (([string]$m.id) -notmatch '^[A-Za-z0-9._-]+\z')) { $regErrors += "entry id missing/empty/illegal (must match ^[A-Za-z0-9._-]+ to absolute end, TSV-safe)"; continue }
  $mid = [string]$m.id
  if ($seenIds.ContainsKey($mid)) { $regErrors += "$mid duplicate id" } else { $seenIds[$mid] = $true }
  # TD133(3): unknown keys rejected — a typo'd channel name (e.g. mustFinds) would otherwise be silently
  # ignored, weakening the declared evidence with no trace.
  foreach ($mk in @($m.Keys)) { if ([string]$mk -notin @('id', 'target', 'anchor', 'afterAnchor', 'gate', 'mustFind', 'mustFindRegex', 'mustNotFind', 'mustNotFindRegex', 'guarded')) { $regErrors += "$mid unknown registry key: $mk" } }
  if (-not $m.ContainsKey('target') -or -not $m.target) { $regErrors += "$mid missing target"; continue }
  if ([IO.Path]::IsPathRooted([string]$m.target)) { $regErrors += "$mid target must be Root-relative (absolute path rejected): $($m.target)" }
  if ($null -eq (Resolve-ConfinedPath ([string]$m.target))) { $regErrors += "$mid target escapes Root: $($m.target)" }
  if ($m.ContainsKey('guarded')) { foreach ($g in @($m.guarded)) { if ([IO.Path]::IsPathRooted([string]$g) -or ($null -eq (Resolve-ConfinedPath ([string]$g)))) { $regErrors += "$mid guarded path invalid (must be Root-relative and confined): $g" } } }
  if (-not $m.ContainsKey('anchor') -or -not $m.anchor) { $regErrors += "$mid missing anchor" }
  foreach ($af in @('anchor', 'afterAnchor')) { if ($m.ContainsKey($af) -and ((([string]$m.$af).Contains("`r")) -or (([string]$m.$af).Contains("`n")))) { $regErrors += "$mid $af must be one physical line (embedded CR/LF rejected — single-line-mutation contract, R3 r3 #5)" } }
  if (-not $m.ContainsKey('gate') -or ([string]$m.gate) -notmatch '^[A-Za-z0-9._,-]+\z') { $regErrors += "$mid gate must match ^[A-Za-z0-9._,-]+ to absolute end (injection guard; $ would admit a trailing LF)" }
  $posCount = 0
  foreach ($ch in @('mustFind', 'mustFindRegex', 'mustNotFind', 'mustNotFindRegex')) {
    if ($m.ContainsKey($ch)) {
      foreach ($p in @($m.$ch)) {
        if (-not (Test-AsciiPrintable $p)) { $regErrors += "$mid $ch pattern not printable-ASCII (L165(5)): $p" }
      }
      if ($ch -in @('mustFind', 'mustFindRegex')) { $posCount += @($m.$ch).Count }
    }
  }
  foreach ($ch in @('mustFindRegex', 'mustNotFindRegex')) {
    if ($m.ContainsKey($ch)) {
      foreach ($p in @($m.$ch)) {
        try { [void][regex]::new($p) } catch { $regErrors += "$mid $ch pattern does not compile: $p" }
      }
    }
  }
  if ($posCount -eq 0) { $regErrors += "$mid needs at least one positive evidence pattern (mustFind/mustFindRegex) — bare nonzero exit is not evidence (L167)" }
}
foreach ($oid in $onlyIds) { if (-not $seenIds.ContainsKey($oid)) { $regErrors += "-Only id not in registry: $oid" } }
if ($onlyIds.Count -gt 0) {
  $sel = @($reg.mutations | Where-Object { $_ -is [hashtable] -and $_.ContainsKey('id') -and $_.id -in $onlyIds })
  if ($sel.Count -eq 0) { $regErrors += '-Only selected zero mutations' }
}
if ($regErrors.Count -gt 0) {
  foreach ($e in $regErrors) { Write-Host "[MUT-REG-INVALID] $e" -ForegroundColor Red }
  exit 2
}
# -Results must not be the SAME file as any target or the registry itself: path-string + NTFS file ID (hardlink alias) checks.
$resultsFull = [IO.Path]::GetFullPath([IO.Path]::Combine((Get-Location).Path, $Results))
$regFull = [IO.Path]::GetFullPath((Resolve-Path $Registry).Path)
$resultsId = Get-FileIdentity $resultsFull
$aliasHit = @($reg.mutations | Where-Object { $af = Resolve-ConfinedPath ([string]$_.target); $af -and (($af -eq $resultsFull) -or ($resultsId -and $resultsId -eq (Get-FileIdentity $af))) })
if ($aliasHit.Count -gt 0 -or $regFull -eq $resultsFull -or ($resultsId -and $resultsId -eq (Get-FileIdentity $regFull))) { Write-Host "[MUT-REG-INVALID] -Results aliases a target or the registry: $Results" -ForegroundColor Red; exit 2 }

# ── L196 自愈（R3 r3 #3 收紧）：陈旧 .mutbak 只在**能验明身份**时动作——当前字节 == 备份（清备份）或
#    == 备份套用本表某枚变异（死批遗留态，还原）；其余 [MUT-BAK-DIVERGENT] 双文件保留 fail-closed。──
foreach ($t in @($reg.mutations | ForEach-Object { $_.target } | Sort-Object -Unique)) {
  $tp = Resolve-ConfinedPath ([string]$t)
  $bak = "$tp.mutbak"
  if (Test-Path $bak) {
    # T269/TD271: LIVENESS before identity. The identity test below asks whether these bytes LOOK
    # like one of THIS registry's mutations, which is true of a peer's in-flight state whenever two
    # registries share a (target, anchor) pair - 5 such pairs exist today. Reproduced end to end:
    # a registry that planted nothing restored a peer's mutant state and deleted its backup.
    $stampPath = "$bak.owner"
    $stampText = if (Test-Path $stampPath) { [IO.File]::ReadAllText($stampPath) } else { '' }
    $ownerRec = Read-MutateBakStamp $stampText
    $ownerAlive = [bool]($ownerRec -and (Get-Process -Id $ownerRec.OwnerPid -ErrorAction SilentlyContinue))
    $ownedFindings = @(Get-MutateBakOwnerFinding -StampText $stampText -SelfPid $PID -OwnerAlive $ownerAlive -Target ([string]$t))
    if ($ownedFindings.Count -gt 0) { $ownedFindings | ForEach-Object { Write-Host $_ -ForegroundColor Red }; exit 4 }
    $bakBytes = [IO.File]::ReadAllBytes($bak)
    $curB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($tp))
    $bakB64 = [Convert]::ToBase64String($bakBytes)
    if ($curB64 -eq $bakB64) {
      Remove-Item $bak -Force
      Remove-Item "$bak.owner" -Force -ErrorAction SilentlyContinue
      Write-Host "[MUT-STALE-BAK-RESTORED] $t (target already equals backup; stale backup dropped)" -ForegroundColor Yellow
    } else {
      $bakText = [Text.Encoding]::UTF8.GetString($bakBytes)
      $known = $false
      foreach ($mm in @($reg.mutations | Where-Object { [string]$_.target -eq [string]$t })) {
        $variant = Get-MutatedVariant $bakText $mm
        if ($null -ne $variant -and [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($variant)) -eq $curB64) { $known = $true; break }
      }
      if ($known) {
        [IO.File]::WriteAllBytes($tp, $bakBytes)
        Remove-Item $bak -Force
      Remove-Item "$bak.owner" -Force -ErrorAction SilentlyContinue
        Write-Host "[MUT-STALE-BAK-RESTORED] $t (recognized killed-batch mutant state; restored from backup)" -ForegroundColor Yellow
      } else {
        Write-Host "[MUT-BAK-DIVERGENT] $t — target differs from backup in an unrecognized way (可能是你的新编辑)；两份文件均保留，请人工核对后删除 .mutbak 再跑" -ForegroundColor Red
        exit 3
      }
    }
  }
}

# ── 基线脏守卫（R3 r1 #2）：git 可判时，target 相对 HEAD 有未提交改动 ⇒ fail-closed（-AllowDirty 显式放行）──
if (-not $AllowDirty -and (Get-Command git -ErrorAction SilentlyContinue)) {
  foreach ($t in @($reg.mutations | ForEach-Object { $_.target } | Sort-Object -Unique)) {
    $tp = Resolve-ConfinedPath ([string]$t)
    $inside = (& git -C (Split-Path $tp) rev-parse --is-inside-work-tree 2>$null)
    if ("$inside".Trim() -ne 'true') { continue }
    & git -C (Split-Path $tp) ls-files --error-unmatch -- $tp *> $null; if ($LASTEXITCODE -ne 0) { continue }
    $dirty = (& git -C (Split-Path $tp) status --porcelain -- $tp 2>$null)
    if ("$dirty".Trim()) {
      Write-Host "[MUT-BASELINE-DIRTY] $t — 工作副本相对 git HEAD 有改动：可能是上一批死在变异态（先核对/还原），也可能是你未提交的实现改动（确认后加 -AllowDirty 放行）" -ForegroundColor Red
      exit 3
    }
  }
}

# ── 既有结果 + schema 版本化（R3 r2 #4）──
$resultsHeader = "id`tverdict`texit`tseconds`tevidence`tcacheKey`tat"
$prev = @{}
if (Test-Path $Results) {
  $curHeader = Get-Content $Results -TotalCount 1
  if ($curHeader -ne $resultsHeader) {
    Write-Host "[MUT-RESULTS-RESET] results schema changed (was: $curHeader) — rebuilding, prior rows dropped" -ForegroundColor Yellow
    Set-Content $Results ($resultsHeader + "`n") -Encoding utf8 -NoNewline
  } else {
    # TD135: rows are keyed by id and replaced in place, so an id REMOVED from the registry would keep its
    # old row forever — even a -Force batch never touched it. The TSV must stay a pure projection of the
    # current registry: orphan rows are dropped with a notice (the notice line is the audit trail).
    # TD133(5): a malformed row (wrong column count, unknown verdict, non-SHA256 cacheKey) is discarded,
    # never reused — reuse must only ever ride on rows this runner provably wrote.
    $keepRows = @($resultsHeader); $rowsDirty = $false
    foreach ($row in (Get-Content $Results | Select-Object -Skip 1)) {
      $c = $row -split "`t"
      if (($c.Count -ne 7) -or (-not $c[0]) -or ($c[1] -notin @('OK', 'SURVIVED', 'BAD-EVIDENCE', 'ANCHOR-MISS', 'RESTORE-FAIL')) -or ($c[5] -notmatch '^[0-9A-Fa-f]{64}$')) {
        Write-Host "[MUT-ROW-MALFORMED] dropped (not a row this runner writes): $($row.Substring(0, [Math]::Min(100, $row.Length)))" -ForegroundColor Yellow
        $rowsDirty = $true; continue
      }
      if (-not $seenIds.ContainsKey($c[0])) {
        Write-Host "[MUT-ORPHAN-DROPPED] $($c[0]) (id not in the current registry - stale evidence row removed)" -ForegroundColor Yellow
        $rowsDirty = $true; continue
      }
      $keepRows += $row
      $prev[$c[0]] = @{ verdict = $c[1]; cacheKey = $c[5] }
    }
    if ($rowsDirty) { Set-Content $Results (($keepRows -join "`n") + "`n") -Encoding utf8 -NoNewline }
  }
} else {
  New-Item -ItemType Directory -Force (Split-Path $Results) | Out-Null
  Set-Content $Results ($resultsHeader + "`n") -Encoding utf8 -NoNewline
}
function Write-ResultRow([string]$id, [string]$verdict, [string]$ec, [string]$sec, [string]$evidence, [string]$key) {
  # 逐枚即时落行（L196：续跑只补缺失枚）；同 id 旧行整行替换。evidence 列记具名命中/失配锚。
  $rows = @(Get-Content $Results | Where-Object { -not $_.StartsWith("$id`t") })
  $rows += "$id`t$verdict`t$ec`t$sec`t$evidence`t$key`t$(Get-Date -Format o)"
  Set-Content $Results (($rows -join "`n") + "`n") -Encoding utf8 -NoNewline
}

function Get-MutationCacheKey([hashtable]$m) {
  # 缓存键 = 枚定义规范化串 + RunCommand 模板 + 被守文件哈希集；序列化用**长度前缀 + 0x1E/0x1F**（R3 r2 #1）。
  $specParts = @()
  foreach ($k in @('id', 'target', 'anchor', 'afterAnchor', 'gate', 'mustFind', 'mustFindRegex', 'mustNotFind', 'mustNotFindRegex', 'guarded')) {
    if ($m.ContainsKey($k)) { $joinedV = @($m.$k) -join [char]0x1F; $specParts += ('{0}:{1}:{2}' -f $k, $joinedV.Length, $joinedV) }
  }
  $files = @((Resolve-ConfinedPath ([string]$m.target)), (Join-Path $RepoRoot 'scripts/selftest.ps1'))
  if ($m.ContainsKey('guarded')) { $files += @($m.guarded | ForEach-Object { Resolve-ConfinedPath ([string]$_) } | Where-Object { $_ }) }
  $fileShas = ($files | ForEach-Object { if ($_ -and (Test-Path $_)) { Get-FileSha $_ } else { 'MISSING' } }) -join '+'
  $concat = (($specParts -join [char]0x1E) + [char]0x1E + ('cmd:{0}:{1}' -f $RunCommand.Length, $RunCommand) + [char]0x1E + 'files:' + $fileShas)
  $sha = [System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($concat))
  ([BitConverter]::ToString($sha) -replace '-', '')
}
function Invoke-Probe([string]$gate) {
  # 探针经子 pwsh -Command 执行（不用 Invoke-Expression：PSSA 红线，且子进程隔离让探针崩溃不污染本进程状态）；
  # {gate} 已过 ^[A-Za-z0-9._,-]+$ 校验，模板替换不构成注入面。
  $cmd = $RunCommand.Replace('{gate}', $gate)
  Push-Location $RepoRoot
  try { $out = & pwsh -NoProfile -Command $cmd 2>&1 | Out-String; return @{ out = $out; exit = $LASTEXITCODE } } finally { Pop-Location }
}

# ── 选集切分：沿用枚先出账；其余先过未变异控制组，再逐枚变异（R3 r3 #1）──
$counts = @{ ok = 0; reuse = 0; survived = 0; bad = 0; anchormiss = 0; restorefail = 0 }
$toRun = @()
foreach ($m in $reg.mutations) {
  if ($onlyIds.Count -gt 0 -and $m.id -notin $onlyIds) { continue }
  $cacheKey = Get-MutationCacheKey $m
  if (-not $Force -and $prev.ContainsKey($m.id) -and $prev[$m.id].cacheKey -eq $cacheKey -and $prev[$m.id].verdict -eq 'OK') {
    Write-Host "[MUT-REUSE] $($m.id) (cache key unchanged, prior OK reused)" -ForegroundColor DarkGray
    $counts.reuse++; continue
  }
  $toRun += @{ m = $m; key = $cacheKey }
}
$pristineChecked = @{}
foreach ($item in $toRun) {
  $g = [string]$item.m.gate
  if ($pristineChecked.ContainsKey($g)) { continue }
  $pristine = Invoke-Probe $g
  if ($pristine.exit -ne 0) { Write-Host "[MUT-PRISTINE-RED] gate=$g — 未变异探针本来就红（exit=$($pristine.exit)）：任何证据都归因不到变异上，先修闸再跑批（R3 r3 #1）" -ForegroundColor Red; exit 4 }
  $pristineChecked[$g] = $true
}

foreach ($item in $toRun) {
  $m = $item.m; $cacheKey = $item.key
  $tp = Resolve-ConfinedPath ([string]$m.target)
  if (-not (Test-Path $tp)) { Write-Host "[MUT-ANCHOR-MISS] $($m.id) target=$($m.target) (not found)" -ForegroundColor Red; $counts.anchormiss++; Write-ResultRow $m.id 'ANCHOR-MISS' '-' '-' 'target-not-found' $cacheKey; continue }

  $baseBytes = [IO.File]::ReadAllBytes($tp)
  $baseSha = Get-FileSha $tp
  $text = [IO.File]::ReadAllText($tp)
  $hasBom = ($baseBytes.Length -ge 3 -and $baseBytes[0] -eq 0xEF -and $baseBytes[1] -eq 0xBB -and $baseBytes[2] -eq 0xBF)
  $enc = [Text.UTF8Encoding]::new($hasBom)

  # 行感知定位与删除（R3 r3 #5）：常规「行+EOL」恰唯一；或锚在**无尾随换行的末行**（EOF 形态）——两形态合计恰一处。
  $mutated = Get-MutatedVariant $text $m
  if ($null -eq $mutated) {
    Write-Host "[MUT-ANCHOR-MISS] $($m.id) (anchor not found exactly once, incl. EOF-line form; use afterAnchor to disambiguate)" -ForegroundColor Red
    $counts.anchormiss++; Write-ResultRow $m.id 'ANCHOR-MISS' '-' '-' 'anchor-not-unique-or-missing' $cacheKey; continue
  }

  $bak = "$tp.mutbak"
  [IO.File]::WriteAllBytes($bak, $baseBytes)
  # T269/TD271: name the owner beside the backup so a second runner can refuse instead of restoring.
  [IO.File]::WriteAllText("$bak.owner", (Get-MutateBakStampText -OwnerPid $PID -Registry $Registry -StartedUtc (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')), [Text.UTF8Encoding]::new($false))
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $verdict = ''; $probeExit = -1; $evidenceNote = ''
  try {
    [IO.File]::WriteAllText($tp, $mutated, $enc)
    $probe = Invoke-Probe ([string]$m.gate)
    $out = $probe.out; $probeExit = $probe.exit
    $hitList = @(); $missList = @()
    if ($m.ContainsKey('mustFind'))         { foreach ($p in @($m.mustFind))         { if ($out.Contains($p))          { $hitList += "found:$p" }     else { $missList += "MISS-find:$p" } } }
    if ($m.ContainsKey('mustFindRegex'))    { foreach ($p in @($m.mustFindRegex))    { if ([regex]::IsMatch($out, $p)) { $hitList += "found-rx:$p" }  else { $missList += "MISS-find-rx:$p" } } }
    if ($m.ContainsKey('mustNotFind'))      { foreach ($p in @($m.mustNotFind))      { if ($out.Contains($p))          { $missList += "HIT-absent:$p" } else { $hitList += "absent:$p" } } }
    if ($m.ContainsKey('mustNotFindRegex')) { foreach ($p in @($m.mustNotFindRegex)) { if ([regex]::IsMatch($out, $p)) { $missList += "HIT-absent-rx:$p" } else { $hitList += "absent-rx:$p" } } }
    $evOk = ($missList.Count -eq 0)
    $evidenceNote = if ($evOk) { $hitList -join '; ' } else { $missList -join '; ' }
    if ($probeExit -eq 0) {
      $verdict = 'SURVIVED'; Write-Host "[MUT-SURVIVED] $($m.id) (probe stayed green — the guard is dead or untested)" -ForegroundColor Red; $counts.survived++
      # T294/TD279: the survivor still counts and the batch still fails - what changes is what the
      # operator is TOLD. Confined to this branch on purpose: OK and BAD-EVIDENCE both require a
      # non-zero probe, so a skipped site cannot manufacture either, and firing there would red every
      # registry that legitimately uses the default template - 84 of the 110 live on 2026-09-08 - and
      # whose evidence a skipped meta site cannot affect.
      if (Test-MutateMetaProbeMissing -ProbeOutput $out) {
        $metaSkipIds = (Get-MutateMetaSkippedSite -ProbeOutput $out) -join ','
        Write-Host "[MUT-META-PROBE-MISSING] $($m.id) probe exited 0 but announced it SKIPPED meta sub-gate(s): $metaSkipIds. This survivor cannot tell 'the guard is dead' from 'the assertion never ran', so do not read it as either." -ForegroundColor Red
        Write-Host "  [FIX] add -IncludeMeta to this registry's probe (-RunCommand '... -Only {gate} -IncludeMeta') and re-run this entry (specs/mutations/README.md, L337)." -ForegroundColor Yellow
        $evidenceNote = "[MUT-META-PROBE-MISSING] meta-skipped=$metaSkipIds; $evidenceNote"
      }
    }
    elseif (-not $evOk)   { $verdict = 'BAD-EVIDENCE'; Write-Host "[MUT-BAD-EVIDENCE] $($m.id) (probe nonzero but named evidence mismatched — red for the wrong reason, L167): $evidenceNote" -ForegroundColor Red; $counts.bad++ }
    else                  { $verdict = 'OK'; Write-Host "[MUT-OK] $($m.id) (exit=$probeExit, evidence matched)" -ForegroundColor Green; $counts.ok++ }
  } finally {
    $sw.Stop()
    [IO.File]::WriteAllBytes($tp, $baseBytes)
    if ((Get-FileSha $tp) -ne $baseSha) {
      Write-Host "[MUT-RESTORE-FAIL] $($m.id) — 还原后 SHA 不等，.mutbak 已保留：$bak" -ForegroundColor Red
      $counts.restorefail++; $verdict = 'RESTORE-FAIL'
    } else {
      Remove-Item $bak -Force -ErrorAction SilentlyContinue
      Remove-Item "$bak.owner" -Force -ErrorAction SilentlyContinue
    }
  }
  Write-ResultRow $m.id $verdict "$probeExit" ([string][math]::Round($sw.Elapsed.TotalSeconds, 1)) $evidenceNote.Replace("`t", ' ') $cacheKey
}

$failTotal = $counts.survived + $counts.bad + $counts.anchormiss + $counts.restorefail
Write-Host ("[MUT-SUMMARY] ok={0} reuse={1} survived={2} bad={3} anchormiss={4} restorefail={5}" -f $counts.ok, $counts.reuse, $counts.survived, $counts.bad, $counts.anchormiss, $counts.restorefail) -ForegroundColor $(if ($failTotal -eq 0) { 'Green' } else { 'Red' })
if ($failTotal -gt 0) { exit 1 }
exit 0
