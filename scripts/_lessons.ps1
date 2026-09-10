#requires -Version 7
<#
.SYNOPSIS
  Shared decision core for the Tier-1 lessons section: which rules are resident in CLAUDE.md's
  must-load block, and how many distinct rules that actually is.

.DESCRIPTION
  Two call sites need the same answer and used to compute it from the same regex, copied twice
  (lessons.ps1 `check` and triage.ps1's lessons-cap probe). Both counted markdown bullets, so
  merging several lesson IDs into one bullet satisfied LessonsMustCap while the resident rule
  count - and the per-turn context it costs - kept growing. Measured downstream over 24 hours:
  9 -> 10 bullets (compliant throughout), 9 -> 17 resident IDs, 5,075 -> 7,523 bytes.

  The unit is therefore the distinct lesson ID, not the bullet: a bullet carrying
  [L17][L162][L172][L177] is one bullet to a checker and four rules to the model, so it counts
  as four. A bullet that declares no ID is not a resident rule and is not returned - the
  section's prose (headers, blockquotes, notes about demoted lessons) costs bytes, which is
  T89-DOC-BUDGETS' unit, not this one.

  Pure function, no side effects, no config dependency: -Path reads a file, -Lines takes the
  text directly so the parser is testable without a fixture file, and -Heading defaults to the
  production section name so a test can drive it with an ASCII heading (machine anchors stay
  pure ASCII, L165).

.EXAMPLE
  Get-ScaffoldMustLayerBullet -Path CLAUDE.md | ForEach-Object Ids | Sort-Object -Unique
.EXAMPLE
  Get-ScaffoldMustLayerBullet -Path CLAUDE.md | Where-Object IdCount -gt 1    # merged bullets
#>

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

function Get-ScaffoldLessonMetaLine {
  <#
  .SYNOPSIS
    [MUST-ANCHOR] The one canonical metadata line of a lesson block, or empty.
  .DESCRIPTION
    Every scalar metadata field (date/tags/tier/kind/severity/recurrence/cost) lives on a SINGLE line,
    separated by a bar. Returning that line is what makes the field reads below anchored: a field can then
    only ever borrow from its own line, and a body sentence that happens to read like metadata is not on it.

    The line is identified by its LEADING FIELD NAME, not by assuming `date` comes first. Production
    records do open with `- date:`, but that is a convention of this repo's ledger rather than the format:
    triage's own selfcheck fixtures carry `- tier: must | severity: major | recurrence: 3` with no date at
    all, and anchoring on `date` silently returned nothing for them - every field empty, every probe blind.
    The whitelist is what keeps this anchored: `- symptom:` and `- rule:` are body lines and can never be
    mistaken for the metadata line, however metadata-like their prose is.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$Block)
  return ([regex]::Match([string]$Block, '(?m)^-[ \t]*(?:date|tags|tier|kind|severity|recurrence|cost):[^\r\n]*')).Value
}

function Get-ScaffoldLessonMetaField {
  <#
  .SYNOPSIS
    [MUST-ANCHOR] Read ONE scalar metadata field from the canonical line. Empty when missing or ambiguous.
  .DESCRIPTION
    Replaces a family of unanchored reads of the shape `tier:\s*(\w+)` searched over the WHOLE block.
    `\s` matches a newline, so against an empty or missing field those expressions walked past the line
    ending and captured the next field's value, or prose from symptom/root_cause/rule. The failure is
    silent and always in the same direction: an undeclared field reads as declared.

    Two rules make this anchored rather than merely narrower:
      - the search space is the canonical line only, so body prose can never satisfy a field;
      - a field must appear EXACTLY ONCE. Zero and two both return empty, because a duplicated field has
        no single correct answer and picking the first is a guess presented as a fact.
    The value class is [^｜|]* - it stops at either bar - and the separator prefix is required, so
    `recurrence:` embedded inside another field's value is not read as the field.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$Block,
    [Parameter(Mandatory, Position = 1)][string]$Name
  )
  $line = Get-ScaffoldLessonMetaLine $Block
  if (-not $line) { return '' }
  $hits = [regex]::Matches($line, ('(?:^-[ \t]*|｜|\|)[ \t]*' + [regex]::Escape($Name) + ':[ \t]*([^｜|]*)'))
  if ($hits.Count -ne 1) { return '' }
  return $hits[0].Groups[1].Value.Trim()
}

function Get-ScaffoldLessonRecurrence {
  <#
  .SYNOPSIS
    [MUST-ANCHOR] The recurrence counter as a decided value plus a reason, never a silent zero.
  .DESCRIPTION
    The old read cast the captured text straight to [int]. That conflated three different states - absent,
    malformed, and out of range - into 0, and 0 is also a legitimate value, so no caller could tell a
    missing counter from a real one.

    Int32.TryParse rather than a cast is the point: the bump path writes $old + 1, so a value at or above
    Int32.MaxValue mints a counter that the NEXT read cannot parse. Rejecting it before the write is the
    only place that can be caught, because after the write the record is already unreadable.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$Block)
  $raw = Get-ScaffoldLessonMetaField -Block $Block -Name 'recurrence'
  if ([string]::IsNullOrWhiteSpace($raw)) { return [pscustomobject]@{ Ok = $false; Value = 0; Raw = $raw; Reason = 'missing or duplicated on the canonical metadata line' } }
  if ($raw -notmatch '^\d+$') { return [pscustomobject]@{ Ok = $false; Value = 0; Raw = $raw; Reason = "not a bare non-negative integer: '$raw'" } }
  $parsed = 0
  if (-not [int]::TryParse($raw, [ref]$parsed)) { return [pscustomobject]@{ Ok = $false; Value = 0; Raw = $raw; Reason = "outside Int32: '$raw'" } }
  return [pscustomobject]@{ Ok = $true; Value = $parsed; Raw = $raw; Reason = '' }
}

# [ENFORCED-BY-GRAMMAR] The component names a bare reference may use. Static BY CONTRACT, never read from
# disk: upstream issue #244 rules out any filesystem or network existence check for the named guard, and a
# pure predicate is what lets the rule be driven from synthetic values in both directions. Adding a script
# to the repo does not silently widen the grammar; someone adds the name here and the fixture covers it.
$script:ScaffoldEnforcedByComponent = @(
  'selftest', 'check-cards', 'check-scope', 'check-secrets', 'check-licenses', 'check-adr',
  'review', 'task', 'lessons', 'triage', 'archive', 'mutate', 'handoff', 'verify',
  'init-scaffold', 'gh-bootstrap', 'scaffold-sync'
)

function Get-ScaffoldEnforcedByShape {
  <#
  .SYNOPSIS
    [ENFORCED-BY-GRAMMAR] Classify one enforced_by value: empty / none / guard / malformed.
  .DESCRIPTION
    The predicate this replaces was `nonempty and not starting with none`, which makes the field mean
    "is not literally the word none". `TODO`, `N/A`, `待补`, `planned future.ps1`, `manual only; docs/manual`,
    `there is no gate 1` and `没有闸1` all passed it and then drove the tier probes as if a machine guard
    existed. The last four matter most: a later file suffix, repo-looking path or gate number must not
    launder LEADING prose that says the guard is planned, manual, or absent.

    So the reference is judged at the START of the field and nowhere else. Explanatory prose may follow a
    valid leading reference - most real values are exactly that shape - but a valid-looking substring
    appearing only later can never make the whole declaration valid.

    FOUR states, one more than before. Malformed is the new one, and it reads as neither guarded NOR
    assessed: a placeholder is not a decision, so the promote probe must keep surfacing it rather than
    treating it as someone's considered "no machine can cover this".

    The accepted leading forms were derived from all 98 nonempty values live in this repo's ledger and
    cold store, not invented: a PATH with a known extension (`scripts/review.ps1`, `.claude/hooks/x.ps1`),
    a bare COMPONENT name (`check-cards 该断言自身` - real, and carries no extension and no gate), a
    component followed by a gate reference (`selftest 闸 10g`), a leading gate reference, or `none` plus a
    nonempty reason. `none` alone is refused: the reason IS the decision, and without it nothing was
    recorded.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$EnforcedBy,
    [string[]]$Component = $script:ScaffoldEnforcedByComponent
  )
  $v = ([string]$EnforcedBy).Trim()
  if (-not $v) { return [pscustomobject]@{ Kind = 'empty'; WellFormed = $true; Guarded = $false; Reason = 'unassessed: nobody has looked yet' } }
  if ($v -match '^none\b') {
    # Keep the established local rejection of placeholder wording (none TODO) while accepting
    # the upstream decision forms used by imported ledgers: parenthesized, prose after a dash,
    # or a measured sentence. A bare none is never a decision.
    if (-not ($v -match '^none(?:[ \t]*（[^\r\n]+）|[ \t]*\([^\r\n]+\)|[ \t]+(?!TODO\b).+|[.-].+)$')) { return [pscustomobject]@{ Kind = 'malformed'; WellFormed = $false; Guarded = $false; Reason = "none has no usable reason" } }
    return [pscustomobject]@{ Kind = 'none'; WellFormed = $true; Guarded = $false; Reason = '' }
  }
  # A path: optional directory segments, then a filename carrying a known extension. Must be followed by
  # end-of-field or a separator, so `future.ps1extra` is not a path.
  if ($v -match '^(?:[A-Za-z0-9_.\-]+[\\/])*[A-Za-z0-9_.\-]+\.(?:ps1|psd1|psm1|mjs|cjs|js|ts|kts?|py|json|ya?ml|md|toml|sqm?)(?=$|[\s(（,;:+])') {
    return [pscustomobject]@{ Kind = 'guard'; WellFormed = $true; Guarded = $true; Reason = '' }
  }
  # MyInspection also records directory-scoped frozen-contract tests before the concrete test file is
  # available. Keep that established two-segment ASCII reference form, while retaining the leading-token
  # rule above so prose such as “manual only; docs/manual” cannot be laundered into a guard.
  if ($v -match '^[A-Za-z0-9._-]{2,}[\\/][A-Za-z0-9._-]{2,}(?:[\\/][A-Za-z0-9._-]+)*(?=$|[\s(（,;:+])') {
    return [pscustomobject]@{ Kind = 'guard'; WellFormed = $true; Guarded = $true; Reason = '' }
  }
  # A leading gate reference, in either language.
  if ($v -match '^(?:gate[ \t]+[0-9A-Za-z\u2460-\u24FF]|闸[ \t]*[0-9A-Za-z\u2460-\u24FF])') { return [pscustomobject]@{ Kind = 'guard'; WellFormed = $true; Guarded = $true; Reason = '' } }
  # A bare component name, optionally followed by a gate reference. The FIRST token must be the component -
  # this is the clause that rejects `manual only; docs/manual` and `planned future.ps1`.
  $first = ($v -split '[\s(（,;:]', 2)[0]
  if ($Component -contains $first) { return [pscustomobject]@{ Kind = 'guard'; WellFormed = $true; Guarded = $true; Reason = '' } }
  return [pscustomobject]@{ Kind = 'malformed'; WellFormed = $false; Guarded = $false; Reason = "does not open with a script path, a known component, a gate reference, or 'none (reason)': '$v'" }
}

function Test-ScaffoldLessonGuarded {
  <#
  .SYNOPSIS
    Does this lesson already have a real mechanical guard? The single definition of that question.
  .DESCRIPTION
    docs/HARNESS-REVIEW.md says it twice - a pitfall already covered by a deterministic gate does not need
    per-turn context (line 31), and a mechanically covered reminder may leave the must layer (line 96) - but
    the promote probe judged on recurrence/severity alone and never read the field, so a working gate became
    a reason to ALSO spend context on the same rule (upstream issue #183).

    The sanctioned "no guard" form is `enforced_by: none（reason）`, which lessons.ps1 check already requires
    of every blocking lesson. An empty field is unguarded too: reading a missing declaration as "covered"
    would silence the promote probe for every lesson that never filled it in, which fails open in the one
    direction that matters.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$EnforcedBy)
  # [ENFORCED-BY-GRAMMAR]: was `nonempty and not starting with none`, which accepted TODO / N/A /
  # `planned future.ps1` as real machine guards. The truth table is unchanged for every WELL-FORMED value;
  # malformed simply stops counting as a guard.
  return (Get-ScaffoldEnforcedByShape $EnforcedBy).Guarded
}

function Test-ScaffoldLessonAssessed {
  <#
  .SYNOPSIS
    T105 [LESSON-ASSESSED]: has anyone DECIDED about this lesson's guard? A different question from
    Test-ScaffoldLessonGuarded above, which asks whether a machine actually has it.
  .DESCRIPTION
    Three states exist, and the two predicates split them differently because their consumers need
    different cuts:
      a real guard path  -> Guarded TRUE , Assessed TRUE
      `none (reason)`    -> Guarded FALSE, Assessed TRUE   <- the state that needed a name
      empty / missing    -> Guarded FALSE, Assessed FALSE
    Why this is not a smarter version of the other predicate (TD152): `Test-ScaffoldLessonGuarded` is
    load-bearing for two consumers that need `none` to keep reading as UNGUARDED - the demote probe
    (a must-tier lesson with no real guard must NOT be nominated for demotion; triage selfcheck pins
    that as case 5d) and the archived T92-LESSON-GUARD-AWARE card, whose dod_command asserts that exact
    truth table and is still executable. So this ADDS a question rather than changing the answer.
    What it fixes: the promote probe consumed "guarded" to decide whether to NOMINATE, so a lesson
    deliberately judged unguardable was re-nominated every run forever. Measured on this repo
    2026-08-23 after reviewing all 19 nominations as one batch - 3 earned real guards and left, and the
    remaining 16 were 100% explicit `none (reason)` with zero empty fields, i.e. every one assessed and
    none able to leave. Issue #185 bounded that producer's OUTPUT by batching; nothing bounded its LIFETIME.
    An EMPTY field is deliberately NOT assessed: reading a missing declaration as "decided" would
    silence the probe for every lesson nobody ever looked at, which is the one direction that fails open.

    [ENFORCED-BY-GRAMMAR] adds a FOURTH state on the same reasoning. A malformed value - `TODO`,
    `planned future.ps1` - is not a decision either, so it must read as UNassessed and keep the promote
    probe surfacing the lesson. Treating a placeholder as "someone concluded no machine can cover this"
    fails open in exactly the direction this predicate exists to keep closed.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$EnforcedBy)
  $shape = Get-ScaffoldEnforcedByShape $EnforcedBy
  return ($shape.WellFormed -and $shape.Kind -ne 'empty')
}

function Get-ScaffoldLessonPromoteFixture {
  # Declared examples for both probes' nomination decisions. `tier` and `enforced_by` are the only two
  # fields either decision reads, so the fixture carries exactly those plus an over-threshold recurrence.
  return [ordered]@{
    # ledger, over threshold, deliberately judged unguardable: MUST NOT be nominated for promotion (TD152)
    'ledger-none'    = @{ Tier = 'ledger'; Enf = 'none (judgment discipline; no gate can observe it)' }
    # ledger, over threshold, nobody ever filled it in: MUST still be nominated (fail-open direction)
    'ledger-empty'   = @{ Tier = 'ledger'; Enf = '' }
    # ledger, over threshold, a machine already has it: MUST NOT be nominated (T92 behaviour, preserved)
    'ledger-guarded' = @{ Tier = 'ledger'; Enf = 'scripts/selftest.ps1 gate 7' }
    # must-tier with only an honest none: MUST NOT be nominated for DEMOTION (selftest/selfcheck case 5d)
    'must-none'      = @{ Tier = 'must';   Enf = 'none (direction heuristic)' }
    # must-tier a machine already guards: MUST be nominated for demotion (case 5c)
    'must-guarded'   = @{ Tier = 'must';   Enf = 'scripts/selftest.ps1 gate 9' }
  }
}

function Test-ScaffoldLessonPromoteExamples {
  <#
  .SYNOPSIS  T105 arms 1-4. Default = the live predicates on the declared examples, and must return
             nothing. Each -Variant feeds the SAME decisions a deliberately wrong predicate - one per
             design this card considered and rejected - and must produce at least one finding. Those
             three variants are the only arms that can fail before this function exists, which is the
             property L239 requires of a working RED.
  #>
  [CmdletBinding()]
  param([ValidateSet('assessed-means-guarded', 'empty-counts-assessed', 'demote-uses-assessed')][string]$Variant)
  $fx = Get-ScaffoldLessonPromoteFixture
  $promote = [ordered]@{}
  $demote = [ordered]@{}
  foreach ($name in @($fx.Keys)) {
    $enf = [string]$fx[$name].Enf
    $tier = [string]$fx[$name].Tier
    # The promote probe nominates a ledger lesson over threshold that has NOT been assessed.
    $assessed = switch ($Variant) {
      'assessed-means-guarded' { Test-ScaffoldLessonGuarded $enf }                       # the pre-T105 behaviour
      'empty-counts-assessed'  { $true }                                                 # the fail-open design, rejected
      default                  { Test-ScaffoldLessonAssessed $enf }
    }
    $promote[$name] = ($tier -eq 'ledger' -and -not $assessed)
    # The demote probe nominates a must-tier lesson a machine already guards - untouched by this card.
    $demoteGuarded = if ($Variant -eq 'demote-uses-assessed') { Test-ScaffoldLessonAssessed $enf } else { Test-ScaffoldLessonGuarded $enf }
    $demote[$name] = ($tier -eq 'must' -and $demoteGuarded)
  }

  if ($Variant) {
    # Each finding IS the evidence: swapping in the rejected predicate flipped a case that must not flip.
    # Silence here would mean the live predicate is not what decides it and arm 1 passes for another reason.
    $f = @()
    if ($Variant -eq 'assessed-means-guarded' -and $promote['ledger-none']) {
      $f += "[LESSON-ASSESSED-EXAMPLE] judging promotion by Test-ScaffoldLessonGuarded re-nominates a lesson already declared 'none (reason)' - that is TD152, the nag that can never be retired."
    }
    if ($Variant -eq 'empty-counts-assessed' -and -not $promote['ledger-empty']) {
      $f += "[LESSON-ASSESSED-EXAMPLE] counting an EMPTY enforced_by as assessed silences the probe for every lesson nobody ever looked at - the one direction that fails open."
    }
    if ($Variant -eq 'demote-uses-assessed' -and $demote['must-none']) {
      $f += "[LESSON-ASSESSED-EXAMPLE] pointing the demote probe at the assessed predicate nominates a must-tier lesson carrying only 'none (reason)' for demotion - triage selfcheck case 5d going red, and the reason Test-ScaffoldLessonGuarded is left untouched."
    }
    return $f
  }

  $findings = @()
  if ($promote['ledger-none']) { $findings += "[LESSON-ASSESSED-EXAMPLE] a ledger lesson over threshold with an explicit 'none (reason)' was still nominated for promotion - it has been assessed and no further review can retire it. [FIX] fix the rule, never the example." }
  if (-not $promote['ledger-empty']) { $findings += "[LESSON-ASSESSED-EXAMPLE] a ledger lesson with an EMPTY enforced_by was NOT nominated - nobody has decided about it yet, so silence here fails open. [FIX] fix the rule, never the example." }
  if ($promote['ledger-guarded']) { $findings += "[LESSON-ASSESSED-EXAMPLE] a lesson a machine already guards was nominated for promotion - that is upstream issue #183, which T92 already closed." }
  if ($demote['must-none']) { $findings += "[LESSON-ASSESSED-EXAMPLE] a must-tier lesson carrying only 'none (reason)' was nominated for DEMOTION - it has no real guard, so demoting it would drop the rule with nothing left holding it." }
  if (-not $demote['must-guarded']) { $findings += "[LESSON-ASSESSED-EXAMPLE] a must-tier lesson a machine already guards was NOT nominated for demotion - that is the half that lets the resident set shrink (HARNESS-REVIEW:96)." }
  return $findings
}

function Get-ScaffoldMustLayerBullet {
  [CmdletBinding()]
  param(
    [string]$Path,
    [string[]]$Lines,
    [string]$Heading = '经验铁律'
  )
  if (-not $PSBoundParameters.ContainsKey('Lines')) {
    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) { return @() }
    $Lines = @(Get-Content -LiteralPath $Path)
  }
  # [MUST-LAYER-GRAMMAR] (T165, upstream issue #245). The parser this replaces was line-based where the
  # content is Markdown, and under-counted three ways, each silently:
  #   - only `-` was a list marker, so resident IDs on `*` or `+` items counted as ZERO;
  #   - every line stood alone, so an ID on an item's continuation line was dropped;
  #   - any `^##` ended the section, including one inside a fenced code block, so every real item after
  #     that fence was skipped.
  # This function is the SHARED source for `lessons.ps1 check` and triage's `lessons-cap` probe, so both
  # agreed on the same wrong answer. Deterministic, but not safe: the resident cap could be bypassed by
  # ordinary valid Markdown formatting.
  #
  # The grammar, stated once so it can be relied on:
  #   FENCE       a line of 3+ backticks or 3+ tildes opens a fenced block, and MAY carry an info string
  #               (```powershell). It is closed by a line of the same character, running at least as long,
  #               carrying NOTHING but trailing whitespace - a fence-shaped line with an info string is
  #               CONTENT, not a close (T238/TD242). Nothing inside a fence is parsed - no headings, no items.
  #   SECTION     starts at the level-2 heading, ends at the next level-2 heading OUTSIDE a fence.
  #   ITEM        `-`, `*` or `+` followed by whitespace. All three are equivalent, per CommonMark.
  #   CONTINUES   a non-blank line, indented past the item's own marker, that is not itself a new item.
  #   ENDS        a blank line closes the current item. This is what keeps a following paragraph or
  #               blockquote from being absorbed into it, which would silently move IDs between items.
  # Items accumulate into a List and are projected at the end. Deliberately NOT a `$flush = {...}`
  # scriptblock: `& $flush` would run in a CHILD scope, so `$out += ...` would append to a copy and
  # `$cur = $null` would not stick - the item state would silently never reset.
  $items = [System.Collections.Generic.List[object]]::new()
  $inSection = $false
  $fence = ''            # the open fence's marker text, or empty when not inside a fence
  $cur = $null           # the item being accumulated
  $lastClosedItem = $null # permits an indented continuation after one separator blank
  $curIndent = 0
  foreach ($line in $Lines) {
    # Fence tracking runs FIRST and everywhere - a fence opened before the section still applies inside it.
    # [FENCE-CLOSER] (T238/TD242) The OPENER and the CLOSER are recognised by SEPARATE rules, and that
    # asymmetry is the whole point: only the opener may carry an info string, so a fence-shaped line
    # carrying one INSIDE an open block is content. One matcher answering both questions inverts the
    # fence state from such a line onward. The closer rules below follow the FENCE grammar above, in its
    # order; they are not re-stated here.
    if ($line -match '^\s*(`{3,}|~{3,})(.*)$') {
      $mark = $Matches[1]
      if (-not $fence) { $fence = $mark; continue }
      # Must stay TOTAL: registry entry N4 deletes the opener above, and `$fence[0]` throws when empty.
      if (-not $fence.StartsWith($mark[0])) { continue }
      if ($mark.Length -lt $fence.Length) { continue }
      if ($Matches[2].Trim()) { continue }
      $fence = ''
      continue
    }
    if ($fence) { continue }
    if (-not $inSection) {
      if ($line -match ('^##\s+' + [regex]::Escape($Heading))) { $inSection = $true }
      continue
    }
    if ($line -match '^##\s') { if ($cur) { $items.Add($cur); $cur = $null }; break }   # next heading, outside any fence
    if (-not $line.Trim()) {
      if ($cur) { $items.Add($cur); $lastClosedItem = $cur; $cur = $null }
      continue
    }
    if ($line -match '^(\s*)([-*+])\s+') {
      if ($cur) { $items.Add($cur) }
      $lastClosedItem = $null
      $curIndent = $Matches[1].Length
      $cur = @{ Occurrences = [System.Collections.Generic.List[string]]::new(); Text = $line.Trim(); Lines = [System.Collections.Generic.List[string]]::new() }
    }
    elseif ($cur) {
      # CommonMark permits a lazy continuation directly after a list item.  A
      # blank line remains the boundary: only a following indented line may
      # rejoin that just-closed item (handled below).
    }
    elseif ($lastClosedItem) {
      $indent = ($line -replace '^(\s*).*$', '$1').Length
      if ($indent -gt 0) {
        $lastClosedItem.Lines.Add($line)
        foreach ($m in [regex]::Matches($line, '\[(L\d+)\]')) { $lastClosedItem.Occurrences.Add($m.Groups[1].Value) }
        continue
      }
      # An outside paragraph/blockquote after the blank cannot donate a later
      # indented line to the preceding list item.
      $lastClosedItem = $null
      continue
    }
    else { continue }
    # [TIER1-BODY] The item's FULL text, marker line plus every continuation line. `Text` is the first line
    # only, which is enough to count IDs but silently drops a continuation - so a two-face body comparison
    # built on `Text` would call a bullet identical while its second line diverged. Accumulated here rather
    # than re-parsed by a second reader, so both consumers share one grammar (the T165 lesson).
    # Stored RAW, deliberately: an earlier version did `.TrimEnd()` here, which made two bodies differing
    # only in trailing whitespace compare EQUAL. In Markdown two trailing spaces are a hard line break, so
    # that is a semantic difference silently normalised away - and it made the documented promise of an
    # exact comparison false. Emptiness is judged with .Trim() at the point of use; the STORED body is not
    # normalised, because normalising it is indistinguishable from not comparing it.
    $cur.Lines.Add($line)
    foreach ($m in [regex]::Matches($line, '\[(L\d+)\]')) { $cur.Occurrences.Add($m.Groups[1].Value) }
  }
  if ($cur) { $items.Add($cur) }
  $out = @()
  foreach ($it in $items) {
    if (-not $it.Occurrences.Count) { continue }
    $uniq = @($it.Occurrences | Select-Object -Unique)   # order-preserving, unlike Sort-Object -Unique
    $out += [pscustomobject]@{
      Ids         = $uniq
      IdCount     = $uniq.Count
      Occurrences = @($it.Occurrences)   # every occurrence, so duplicates are REPORTABLE not hidden
      Text        = $it.Text
      Body        = @($it.Lines) -join "`n"
    }
  }
  return $out
}

function Get-ScaffoldTierOneBodyException {
  <#
  .SYNOPSIS
    [TIER1-BODY] The reason-bearing exception map: lesson ID -> English reason a body is allowed to differ
    across the two faces. EMPTY on delivery, and meant to stay that way.
  .DESCRIPTION
    An allowlist with no reason attached rots into a list nobody can audit, so an entry must state WHY.
    The judgement below checks the map itself, not only what it excuses: an entry whose bodies no longer
    differ is stale, an entry naming a non-Tier-1 ID is a typo or a leftover, and an entry with an empty
    reason is an allowlist pretending to be a decision. All three are reported, so the map cannot quietly
    grow into the hole this card closes.
  #>
  [CmdletBinding()]
  param()
  return @{}
}

function Get-ScaffoldTierOneBodyPair {
  <#
  .SYNOPSIS
    [TIER1-BODY] Pair each resident Tier-1 lesson ID with its bullet body on both faces (CLAUDE.md and
    CLAUDE.template.md). The population the drift judgement actually compared, exposed so it can be counted.
  .DESCRIPTION
    Reads both faces through Get-ScaffoldMustLayerBullet - the SAME parser lessons.ps1 and triage's
    lessons-cap probe use - rather than a second reader, because the defect this closes was born of two
    readers disagreeing (T165). Pairs are keyed on the lesson ID and built over the UNION of both faces,
    so an ID resident on one face and missing from the other is a pair with a null side, not a silently
    skipped one.
    -MetaText / -TemplateText take the section text directly (a string, or an array of lines) so the
    judgement is testable without fixture files; -Heading lets a test drive it with an ASCII heading,
    since machine anchors stay pure ASCII (L165). There is deliberately NO path override: the two faces
    this contract binds are fixed by name, and an override nothing calls would be an untested path
    through the one function whose whole job is to not report green vacuously.
  #>
  [CmdletBinding()]
  param(
    [string[]]$MetaText,
    [string[]]$TemplateText,
    [string]$Heading = '经验铁律'
  )
  $repoRoot = Split-Path $PSScriptRoot -Parent
  $MetaPath = Join-Path $repoRoot 'CLAUDE.md'
  $TemplatePath = Join-Path $repoRoot 'CLAUDE.template.md'

  # A single blob and an array of lines both arrive as [string[]]; splitting on the '\r?\n' REGEX (single
  # quotes, so it reaches -split as a pattern and not as three literal characters) parses either form.
  $read = {
    param([string[]]$Text, [string]$Path, [string]$SectionHeading)
    if ($null -ne $Text) {
      $lines = @($Text | ForEach-Object { $_ -split '\r?\n' })
      return @(Get-ScaffoldMustLayerBullet -Lines $lines -Heading $SectionHeading)
    }
    return @(Get-ScaffoldMustLayerBullet -Path $Path -Heading $SectionHeading)
  }
  $metaBullets = & $read $MetaText $MetaPath $Heading
  $tplBullets  = & $read $TemplateText $TemplatePath $Heading

  # An ID declared more than once on a face is NOT comparable, and saying so is the whole point. An earlier
  # version kept the FIRST declaration and claimed the duplicate "stays visible" in
  # Get-ScaffoldMustLayerDuplicate - but this judgement never consulted that function, so a second bullet
  # carrying the same ID was silently never compared: a face could redeclare a rule with a different body
  # and read GREEN. Counts come from the existing helper rather than a second counter written here, so the
  # two cannot disagree about what "declared twice" means (it counts OCCURRENCES, which also catches an ID
  # written twice inside one bullet).
  # Guarded on non-empty, and the guard is load-bearing rather than defensive habit: the helper does
  # `foreach ($b in @($Bullet))`, and @($null) is a ONE-element array holding $null, so an empty face
  # reaches `$seen[$null]` and throws under StrictMode. lessons.ps1 runs with $ErrorActionPreference =
  # 'Stop', so that throw would abort the whole check - a degenerate face (the very case the POPULATION
  # axis exists to report) would crash the gate instead of being reported by it.
  $metaDup = @{}
  if ($metaBullets.Count) { foreach ($d in @(Get-ScaffoldMustLayerDuplicate $metaBullets)) { $metaDup[$d.Id] = $d.Count } }
  $tplDup = @{}
  if ($tplBullets.Count) { foreach ($d in @(Get-ScaffoldMustLayerDuplicate $tplBullets)) { $tplDup[$d.Id] = $d.Count } }

  $metaById = [ordered]@{}
  foreach ($b in $metaBullets) { foreach ($id in $b.Ids) { if (-not $metaById.Contains($id)) { $metaById[$id] = $b } } }
  $tplById = [ordered]@{}
  foreach ($b in $tplBullets) { foreach ($id in $b.Ids) { if (-not $tplById.Contains($id)) { $tplById[$id] = $b } } }

  $ids = @($metaById.Keys) + @($tplById.Keys | Where-Object { -not $metaById.Contains($_) })
  $out = @()
  foreach ($id in $ids) {
    $m = if ($metaById.Contains($id)) { $metaById[$id] } else { $null }
    $t = if ($tplById.Contains($id)) { $tplById[$id] } else { $null }
    $dupMeta = [int]$metaDup[$id]
    $dupTpl = [int]$tplDup[$id]
    $comparable = ($null -ne $m -and $null -ne $t -and $dupMeta -eq 0 -and $dupTpl -eq 0)
    $out += [pscustomobject]@{
      Id             = $id
      MetaBody       = if ($m) { $m.Body } else { $null }
      TemplateBody   = if ($t) { $t.Body } else { $null }
      OnBothFaces    = ($null -ne $m -and $null -ne $t)
      MetaDeclared   = if ($dupMeta) { $dupMeta } elseif ($m) { 1 } else { 0 }
      TemplateDeclared = if ($dupTpl) { $dupTpl } elseif ($t) { 1 } else { 0 }
      Comparable     = $comparable
      # -cne, not -ne: case-sensitive, over every decoded character of each extracted line, trailing
      # whitespace included; extracted lines are joined with LF. BOM and the source file's CRLF/LF are
      # therefore excluded. This is NOT byte identity, and NOT a claim of full CommonMark coverage - the
      # shared parser's fence grammar decides which lines are extracted in the first place.
      # Identical is FALSE whenever the pair is not comparable, so a duplicate can never present as green.
      Identical      = ($comparable -and -not ($m.Body -cne $t.Body))
    }
  }
  return $out
}

function Get-ScaffoldTierOneBodyDrift {
  <#
  .SYNOPSIS
    [TIER1-BODY] Findings where the resident Tier-1 bullet BODIES disagree across CLAUDE.md and
    CLAUDE.template.md. Empty result is green.
  .DESCRIPTION
    lessons.ps1's template-sync block checked only that each tier=must ID APPEARS in the template's
    iron-law section. Bodies were never compared, so a one-sided meta edit - a recurrence bump, a diet,
    a cut - silently narrowed the downstream face while `lessons.ps1 check` stayed green and selftest
    gate 2 transcluded that green. Three of five bullets had drifted under that permanently green check.

    Three axes, so no single one can go vacuous:
      POPULATION  requires at least one extracted resident ID per face. A parser that matched nothing
                  would otherwise report "no drift" - the failure mode this whole judgement exists to
                  deny (L239: an emptiness assertion must first prove the thing it inspects EXISTS).
                  It is NOT a numeric population floor, and it therefore cannot detect a common-mode
                  parser omission that drops the same bullets from BOTH faces.
      COVERAGE    uses the UNION of IDs extracted from the two faces to report one-sided IDs. A parser
                  that matched 1 of 5 on one face reports 4 missing counterparts, which a count written
                  once into the code could not do - but see POPULATION for what it still cannot see.
      UNIQUENESS  an ID declared more than once on a face has no single body, so it is reported and
                  excluded rather than compared against an arbitrary one of its declarations.
      EMPTY       an unextracted body is a finding BEFORE the comparison, because two empty bodies
                  compare identical and would report green having compared nothing.
      BODY        case-sensitive, over every decoded character of each extracted line, trailing
                  whitespace included; extracted lines are joined with LF. BOM and the source file's
                  CRLF/LF encoding are excluded. This is NOT byte identity and NOT a claim of complete
                  CommonMark coverage - the shared parser's grammar decides which lines are extracted.
                  Its fence handling models the opener/closer asymmetry as of T238/TD242, and still does
                  not model backticks inside an info string, the three-space opener indent, or closing at
                  the end of a containing block. Deliberately NOT described as 14e's shape: 14e compares raw section text with
                  line endings included, whereas this parses to lines and rejoins them with LF.
    -Variant 'ignore-body' drops the BODY axis and keeps the other two. It is a REJECTED shape kept as a
    negative control: a fixture differing only in body must yield findings by default and none under this
    variant, which proves a finding came from the body comparison rather than from a neighbouring axis
    (L188 - a negative anchor exists to prove the red did not come from the assertion next door).
  #>
  [CmdletBinding()]
  param(
    [string[]]$MetaText,
    [string[]]$TemplateText,
    [string]$Heading = '经验铁律',
    [ValidateSet('', 'ignore-body')]
    [string]$Variant = '',
    [hashtable]$Exception
  )
  if (-not $PSBoundParameters.ContainsKey('Exception')) { $Exception = Get-ScaffoldTierOneBodyException }

  $pairArgs = @{ Heading = $Heading }
  foreach ($k in 'MetaText', 'TemplateText') {
    if ($PSBoundParameters.ContainsKey($k)) { $pairArgs[$k] = $PSBoundParameters[$k] }
  }
  $pairs = @(Get-ScaffoldTierOneBodyPair @pairArgs)

  $findings = [System.Collections.Generic.List[string]]::new()

  # POPULATION. Reported per face and from live data - which face, and how many the other one has - rather
  # than from a hardcoded cause (L97).
  $metaCount = @($pairs | Where-Object { $null -ne $_.MetaBody }).Count
  $tplCount = @($pairs | Where-Object { $null -ne $_.TemplateBody }).Count
  if ($metaCount -lt 1) {
    $findings.Add("[TIER1-BODY-DRIFT] the CLAUDE.md face yielded NO resident Tier-1 bullet (the other face yielded $tplCount). Either the section heading moved or the parser stopped matching it - until that is fixed, 'no drift' means 'nothing was compared'. [FIX] check the '$Heading' heading on that face.")
  }
  if ($tplCount -lt 1) {
    $findings.Add("[TIER1-BODY-DRIFT] the CLAUDE.template.md face yielded NO resident Tier-1 bullet (the other face yielded $metaCount). Either the section heading moved or the parser stopped matching it - until that is fixed, 'no drift' means 'nothing was compared'. [FIX] check the '$Heading' heading on that face.")
  }

  foreach ($p in $pairs) {
    $excused = $Exception.ContainsKey($p.Id)
    # UNIQUENESS, before anything else. An ID declared twice on a face has no single body to compare, so
    # comparing an arbitrary one of them is not a weaker answer - it is a made-up one. Reported and skipped,
    # never excused: an exception cannot make an ambiguous declaration comparable.
    if ($p.MetaDeclared -gt 1 -or $p.TemplateDeclared -gt 1) {
      $face = if ($p.MetaDeclared -gt 1) { 'CLAUDE.md' } else { 'CLAUDE.template.md' }
      $n = [Math]::Max($p.MetaDeclared, $p.TemplateDeclared)
      $findings.Add("[TIER1-BODY-DUPLICATE] $($p.Id) is declared $n times on $face and is therefore not comparable - there is no single body to hold identical. [FIX] merge or remove the duplicate declaration so the ID appears exactly once on each face.")
      continue
    }
    if (-not $p.OnBothFaces) {
      $missing = if ($null -eq $p.MetaBody) { 'CLAUDE.md' } else { 'CLAUDE.template.md' }
      $findings.Add("[TIER1-BODY-DRIFT] $($p.Id) is resident on one face but absent from $missing, so its body could not be compared. [FIX] register the bullet on both faces, or demote it out of Tier-1 on both.")
      continue
    }
    # EMPTY. A resident bullet always has at least its marker and its ID, so an empty body means the body
    # was not extracted - and two empty bodies compare EQUAL, which is the one way this judgement could
    # report "identical" while comparing nothing at all. Checked before the comparison, never after (L239).
    if (-not $p.MetaBody.Trim() -or -not $p.TemplateBody.Trim()) {
      $findings.Add("[TIER1-BODY-EMPTY] $($p.Id) has an EMPTY body on at least one face (CLAUDE.md $($p.MetaBody.Length) chars, CLAUDE.template.md $($p.TemplateBody.Length)). Two empty bodies compare identical, so this would report green while comparing nothing. [FIX] the bullet text is not being extracted - check the section parser, not the files.")
      continue
    }
    if ($Variant -eq 'ignore-body') { continue }
    if (-not $p.Identical -and -not $excused) {
      $findings.Add("[TIER1-BODY-DRIFT] $($p.Id)'s Tier-1 body differs between the two faces (CLAUDE.md $($p.MetaBody.Length) chars, CLAUDE.template.md $($p.TemplateBody.Length)). The downstream face is not the rule the meta face teaches. [FIX] make the two bodies identical, or record an exception with a reason in Get-ScaffoldTierOneBodyException.")
    }
  }

  # The exception map is itself checked, so it cannot rot into a stale allowlist.
  foreach ($id in @($Exception.Keys)) {
    $p = $pairs | Where-Object Id -eq $id | Select-Object -First 1
    $reason = [string]$Exception[$id]
    if (-not $p) {
      $findings.Add("[TIER1-BODY-EXCEPTION] $id is excused from body identity but is not a resident Tier-1 ID on either face. [FIX] drop the entry - it excuses nothing.")
    }
    elseif ($p.Identical) {
      $findings.Add("[TIER1-BODY-EXCEPTION] $id is excused from body identity but its two bodies are now identical. [FIX] drop the entry; a stale exception hides the next real drift.")
    }
    elseif (-not $reason.Trim()) {
      $findings.Add("[TIER1-BODY-EXCEPTION] $id is excused from body identity with an EMPTY reason. [FIX] state why the two faces must differ, or drop the entry.")
    }
  }
  return @($findings)
}

function Get-ScaffoldMustLayerDuplicate {
  <#
  .SYNOPSIS
    [MUST-LAYER-GRAMMAR] Resident IDs declared more than once, with their occurrence counts.
  .DESCRIPTION
    The old parser collapsed IDs with `Sort-Object -Unique` per line, so a rule declared twice was
    indistinguishable from one declared once and simply vanished from view. The cap is a budget: a
    duplicate means either a real double-declaration to merge, or a typo pointing at the wrong lesson.
    Either way it must be VISIBLE. Reported, never auto-corrected.
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][object[]]$Bullet)
  $seen = @{}
  foreach ($b in @($Bullet)) {
    foreach ($id in @($b.Occurrences)) { $seen[$id] = 1 + [int]$seen[$id] }
  }
  return @($seen.Keys | Where-Object { $seen[$_] -gt 1 } | Sort-Object | ForEach-Object {
      [pscustomobject]@{ Id = $_; Count = $seen[$_] }
    })
}

# Compatibility projection for MyInspection's existing cold-recall consumers.  The upstream parser is
# the sole grammar; this retains the old object-shaped interface while lessons.ps1 and triage.ps1 move
# to Get-ScaffoldMustLayerBullet directly.
$ScaffoldMustLayerNotFound = '[LESSONS-SECTION-NOT-FOUND]'
$ScaffoldDuplicateResidentId = '[LESSONS-DUPLICATE-RESIDENT-ID]'
function Test-ScaffoldLessonEnforcedByWellFormed {
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$EnforcedBy)
  $shape = Get-ScaffoldEnforcedByShape $EnforcedBy
  if (-not $shape.WellFormed) { return $false }
  # A blocking lesson may record `none（reason）`, but placeholders are not a reason: accepting them
  # would make the check pass while its claimed future/manual enforcement remains absent.
  if ($shape.Kind -eq 'none' -and ([string]$EnforcedBy).Trim() -match '^none\s+(?:N/?A|待补|未定)(?:\s|$)') { return $false }
  return $true
}

function Get-ScaffoldMustLayerSection {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Path,
    [string]$Heading = '经验铁律',
    [object[]]$Bullets
  )
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    return [pscustomobject]@{
      Found = $false; Reason = 'FILE-MISSING'; Sentinel = $ScaffoldMustLayerNotFound
      Bullets = @(); Ids = @(); DuplicateIds = @()
    }
  }
  # `if` output is enumerated by assignment.  Keep the collection wrapped here:
  # a valid section with exactly one resident item otherwise becomes one custom
  # object, which has no `.Count` under StrictMode and makes the cap probe fail.
  $bullets = @(if ($PSBoundParameters.ContainsKey('Bullets')) { @($Bullets) } else { @(Get-ScaffoldMustLayerBullet -Path $Path -Heading $Heading) })
  if (-not $bullets.Count) {
    return [pscustomobject]@{
      Found = $false; Reason = 'SECTION-NOT-FOUND'; Sentinel = $ScaffoldMustLayerNotFound
      Bullets = @(); Ids = @(); DuplicateIds = @()
    }
  }
  $duplicates = @(Get-ScaffoldMustLayerDuplicate $bullets)
  $ids = @($bullets | ForEach-Object Ids | Select-Object -Unique)
  if ($duplicates.Count) {
    return [pscustomobject]@{
      Found = $false; Reason = 'DUPLICATE-RESIDENT-ID'; Sentinel = $ScaffoldDuplicateResidentId
      Bullets = $bullets; Ids = $ids; DuplicateIds = @($duplicates | ForEach-Object Id)
    }
  }
  return [pscustomobject]@{
    Found = $true; Reason = ''; Sentinel = ''; Bullets = $bullets; Ids = $ids; DuplicateIds = @()
  }
}
