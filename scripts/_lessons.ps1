#requires -Version 7
<#
.SYNOPSIS
  Shared decision core for the Tier-1 lessons section: which rules are resident in CLAUDE.md's
  must-load block, how many distinct rules that actually is, and whether a lesson already has a
  mechanical guard.

.DESCRIPTION
  Two consumers make three invocations and need the same answer: lessons.ps1 `check` reads CLAUDE.md and,
  when present, CLAUDE.template.md; triage.ps1's lessons-cap probe reads CLAUDE.md. Both consumers used to
  compute it from the same regex, copied twice. They counted markdown bullets, so
  merging several lesson IDs into one bullet satisfied LessonsMustCap while the resident rule
  count - and the per-turn context it costs - kept growing. The one measurement quoted in this
  PR carries its commit and lives at its own call site (lessons.ps1 `check`); no unattributed
  before/after numbers live here.

  The unit is therefore the distinct lesson ID, not the bullet: a bullet carrying
  [L17][L162][L172][L177] is one bullet to a checker and four rules to the model, so it counts
  as four. A bullet that declares no ID is not a resident rule and is not returned - the
  section's prose (headers, blockquotes, notes about demoted lessons) costs bytes, which is
  T89-DOC-BUDGETS' unit, not this one.

  "The section" also has exactly one definition, and it is the Ids this parser returns: the cap,
  the id-existence check and the tier-drift check all read that same set. Duplicate occurrences fail
  closed before any consumer counts the set, so deduplication cannot hide context growth. While the cap counted
  bullets and the other two regexed the whole section text, a `tier: must` lesson mentioned only
  in the intro blockquote read as registered yet cost nothing against the cap - the merged-bullet
  loophole in miniature.

.EXAMPLE
  (Get-ScaffoldMustLayerSection -Path CLAUDE.md).Ids                                  # resident rules
.EXAMPLE
  (Get-ScaffoldMustLayerSection -Path CLAUDE.md).Bullets | Where-Object IdCount -gt 1 # merged bullets
#>

# 分节解析失败的 ASCII 哨兵（L165：机检认 ASCII，本地化文案只给人读）。两个消费者引用同一枚字面量。
$ScaffoldMustLayerNotFound = '[LESSONS-SECTION-NOT-FOUND]'
$ScaffoldDuplicateResidentId = '[LESSONS-DUPLICATE-RESIDENT-ID]'
# 「显式声明无守卫」的唯一形态。守卫判定与形态判定对它给相反答案，故字面量只写这一处。
$ScaffoldNoGuardDeclRe = '^none(?:（[^\r\n]+）|\([^\r\n]+\))$'
# Placeholder/negation prefixes win before any later path/extension/gate-looking substring can launder them.
$ScaffoldRejectedGuardPrefixRe = '^(?i:(?:TODO|TBD|FIXME|XXX|TBC|TK|WIP|PENDING|N/?A)(?=$|[^A-Za-z0-9_])|no[ \t]+gate(?=$|[^A-Za-z0-9_])|none(?=$|[^A-Za-z0-9_]))|^(?:待补|待定|未定|待议|暂无|无闸)'

function Get-ScaffoldLessonEnforcedBy {
  <#
  .SYNOPSIS
    Read one ledger block's enforced_by field. One extractor, three call sites (lessons.ps1 and both
    tier probes), because the answer decides whether a rule costs per-turn context forever.
  .DESCRIPTION
    The character class is [ \t]*, not \s*, and the capture is (.*), not (.+). `\s` matches the newline, so
    against an EMPTY `- enforced_by:` line the old expression walked on and captured the following line -
    typically `- refs:` - and an undeclared guard read as a declared one. That fails open in the one
    direction that matters: lessons.ps1 check stops demanding a guard from blocking lessons, and the promote
    probe stops proposing exactly the lessons that need it.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowEmptyString()][string]$Block)
  return ([regex]::Match($Block, '(?m)^- enforced_by:[ \t]*(.*)$')).Groups[1].Value.Trim()
}

function Test-ScaffoldLessonGuarded {
  <#
  .SYNOPSIS
    Does this lesson already have a real mechanical guard? The single definition of that question.
  .DESCRIPTION
    docs/HARNESS-REVIEW.md says it twice - in the gate stress-test table, row `lessons 必须层（Tier1）`
    ("有机械守卫的可降回按需层"), and again under 「与其它系统的衔接」 - but the promote probe judged on
    recurrence/severity alone and never read the field, so a working gate became a reason to ALSO spend
    context on the same rule (upstream issue #183). (Section/row text, not line numbers: those drift on
    the next edit of that document.)

    Allowlist, not denylist. "Guarded" requires the value to NAME a mechanical artifact: a repo path
    (>=2 characters either side of the separator, so `N/A` is not one), a file with a code/config
    extension, or a gate reference (`闸 <id>` / `gate <id>`). Everything else reads as UNGUARDED -
    the sanctioned `none（reason）` form, an empty field, and placeholders such as TODO / N/A / 待补 /
    见 PR 讨论 alike.

    Both of those two alternatives are spelled out character by character, because the generic spelling
    over-matches in the OPPOSITE direction and this ledger is Chinese prose:
      - the path side is [A-Za-z0-9._-], not [\w.-]. .NET's \w is Unicode-aware and matches CJK, so
        `[\w.-]{2,}[\\/][\w.-]{2,}` read any Chinese phrase carrying a slash as "a repo path" -
        `人工/评审`, `手动/人工核验`, `见 PR #183 的讨论/结论`. A repo path is ASCII here; a phrase is not.
      - the gate side demands an actual id after the keyword, not `\S`. `闸\s*\S` accepted `闸` followed
        by ANY character, so `无闸门（只能靠人）` - which says in as many words that there is no gate -
        read as GUARDED, and `gate 讨论` with it. An id is ASCII alphanumeric or one of the enclosed
        alphanumerics (U+2460-U+24FF) this repo already writes its gate numbers with (`闸⑯`, `gate ⑧`);
        that block contains no ideographs, so admitting it cannot reopen the CJK hole. `\s+` stays
        required after the ASCII keyword so `gateway` is not a gate reference.

    Fail-closed both ways, which is why it is an allowlist. Reading an unrecognised value as "guarded"
    drops that lesson from the promote probe (the one lesson most needing a guard vanishes from the
    heartbeat), and if it is already tier: must it hands the demote probe the line "机器已在守它：TODO" -
    or, worse, "机器已在守它：无闸门（只能靠人）" - the heartbeat arguing to delete a guardless iron rule
    while quoting the words "there is no gate" as its evidence. lessons.ps1 `check` rejects the same
    unrecognised forms outright, so they cannot accumulate in the ledger.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$EnforcedBy)
  if ([string]::IsNullOrWhiteSpace($EnforcedBy)) { return $false }
  $v = $EnforcedBy.Trim()
  if ($v -match $ScaffoldNoGuardDeclRe -or $v -match $ScaffoldRejectedGuardPrefixRe) { return $false }
  # 闸编号的字符类写成 \u 转义而非字面圈码（L193：不可见/易错码位只写转义形态，落盘可逐字节复核）。
  # ①-⓿ = Enclosed Alphanumerics，整块无表意文字，故收它进来不会重开中文那个口子。
  return ($v -match '(?i)(\.(ps1|psm1|mjs|cjs|js|ts|kts?|py|ya?ml|json|sqm?)\b|[A-Za-z0-9._-]{2,}[\\/][A-Za-z0-9._-]{2,}|\bgate\s+[0-9A-Za-z\u2460-\u24FF]|闸\s*[0-9A-Za-z\u2460-\u24FF])')
}

function Test-ScaffoldLessonEnforcedByWellFormed {
  <#
  .SYNOPSIS
    Is this enforced_by value a form the ledger accepts at all? lessons.ps1 `check` gates on it.
  .DESCRIPTION
    Three legal forms and nothing else: empty (a non-blocking lesson may leave it blank), the sanctioned
    `none（reason）` declaration, or a guard reference Test-ScaffoldLessonGuarded recognises. A value that
    is none of those - TODO, N/A, 待补, 见 PR 讨论, and equally the Chinese ones 无闸门（只能靠人） /
    人工/评审 / 未定/待议 - is a placeholder impersonating a declaration, and the ledger is where it would
    sit forever. Rejecting it at the gate is what keeps the guarded/unguarded judgement above meaningful.
    Note this gate inherits every character-class decision made above: it delegates, it does not re-judge.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$EnforcedBy)
  if ([string]::IsNullOrWhiteSpace($EnforcedBy)) { return $true }
  if ($EnforcedBy.Trim() -match $ScaffoldNoGuardDeclRe) { return $true }
  return (Test-ScaffoldLessonGuarded $EnforcedBy)
}

function Get-ScaffoldMustLayerSection {
  <#
  .SYNOPSIS
    Parse CLAUDE.md's must-load lessons section once: Found / Reason / Bullets / Ids / DuplicateIds.
  .DESCRIPTION
    Reason is 'OK' | 'FILE-MISSING' | 'HEADING-NOT-FOUND' | 'DUPLICATE-RESIDENT-ID'. Failure states are deliberately
    distinct and are NOT the same as "section present, zero bullets":
      FILE-MISSING      graceful - a repo with no CLAUDE.md has no must layer at all (empty-config rule).
      HEADING-NOT-FOUND drift - every consumer must fail closed, because the alternative reading is
                        "zero resident rules", which passes any cap while having measured nothing.
      DUPLICATE-RESIDENT-ID invalid - repeated resident occurrences must not collapse before cap accounting.
    The anchor is the section's own localized heading (the file it parses is Chinese prose); the ASCII
    sentinel $ScaffoldMustLayerNotFound is on the failure signal, which is the part machines match (L165).
  #>
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Path)
  $none = @()
  if (-not $Path -or -not (Test-Path -LiteralPath $Path)) {
    return [pscustomobject]@{ Found = $false; Reason = 'FILE-MISSING'; Sentinel = $ScaffoldMustLayerNotFound; Bullets = $none; Ids = $none }
  }
  $bullets = @()
  $residentOccurrences = [System.Collections.Generic.List[string]]::new()
  $items = [System.Collections.Generic.List[string]]::new()
  $currentItem = $null
  $inSection = $false
  foreach ($line in @(Get-Content -LiteralPath $Path)) {
    if (-not $inSection) {
      if ($line -match '^##\s+经验铁律') { $inSection = $true }
      continue
    }
    if ($line -match '^##\s') {
      if ($null -ne $currentItem) { $items.Add($currentItem) }
      $currentItem = $null
      break
    }
    if ($line -match '^\s*-\s+') {
      if ($null -ne $currentItem) { $items.Add($currentItem) }
      $currentItem = $line
      continue
    }
    # Inside this bounded section, every physical line after a list marker belongs to that item until the
    # next marker or section heading. This covers CommonMark lazy continuations and an indented paragraph
    # after a blank line; ending on either shape silently drops resident ids from the cap.
    if ($null -ne $currentItem) { $currentItem += "`n$line"; continue }
  }
  if ($null -ne $currentItem) { $items.Add($currentItem) }
  foreach ($item in $items) {
    $occurrences = @([regex]::Matches($item, '\[(L\d+)\]') | ForEach-Object { $_.Groups[1].Value })
    foreach ($id in $occurrences) { $residentOccurrences.Add($id) }
    $ids = @($occurrences | Sort-Object -Unique)
    if ($ids.Count -eq 0) { continue }
    $bullets += [pscustomobject]@{ Ids = $ids; IdCount = $ids.Count; Text = $item.Trim() }
  }
  if (-not $inSection) {
    return [pscustomobject]@{ Found = $false; Reason = 'HEADING-NOT-FOUND'; Sentinel = $ScaffoldMustLayerNotFound; Bullets = $none; Ids = $none }
  }
  $duplicateIds = @($residentOccurrences | Group-Object | Where-Object Count -gt 1 | ForEach-Object Name | Sort-Object)
  if ($duplicateIds.Count -ne 0) {
    return [pscustomobject]@{
      Found        = $false
      Reason       = 'DUPLICATE-RESIDENT-ID'
      Sentinel     = $ScaffoldDuplicateResidentId
      Bullets      = $bullets
      Ids          = @($residentOccurrences | Sort-Object -Unique)
      DuplicateIds = $duplicateIds
    }
  }
  return [pscustomobject]@{
    Found    = $true
    Reason   = 'OK'
    Sentinel = ''            # 解析成功没有失败信号；消费者照样只拼 .Sentinel，不必各自记住那枚字面量
    Bullets  = $bullets
    Ids      = @($bullets | ForEach-Object Ids | Sort-Object -Unique)
    DuplicateIds = $none
  }
}
