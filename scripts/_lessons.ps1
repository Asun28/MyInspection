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
  if ([string]::IsNullOrWhiteSpace($EnforcedBy)) { return $false }
  return ($EnforcedBy.Trim() -notmatch '^none\b')
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
  $out = @()
  $inSection = $false
  foreach ($line in $Lines) {
    if (-not $inSection) {
      if ($line -match ('^##\s+' + [regex]::Escape($Heading))) { $inSection = $true }
      continue
    }
    if ($line -match '^##\s') { break }          # next level-2 heading ends the section
    if ($line -notmatch '^\s*-\s+') { continue }  # only list items can declare a resident rule
    $ids = @([regex]::Matches($line, '\[(L\d+)\]') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    if ($ids.Count -eq 0) { continue }
    $out += [pscustomobject]@{ Ids = $ids; IdCount = $ids.Count; Text = $line.Trim() }
  }
  return $out
}
