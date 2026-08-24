#requires -Version 7
<#
.SYNOPSIS
  Fleet loop: the two-way link between this project and the scaffold it was generated from.

.DESCRIPTION
  `init-scaffold.ps1` is a one-time snapshot, so the relationship with the upstream scaffold is
  normally one-way and manual: the downstream pulls fixes by hand and nothing travels back. This
  script closes that into a loop with two halves that share one link.

    check  - Am I stale? Compares the version this project has evaluated up to (the decision ledger
             in docs/SCAFFOLD-SYNC.md, falling back to _config.ps1 ScaffoldVersion when the ledger
             has no rows yet) against the upstream release tags, and prints ONLY the CHANGELOG
             "Downstream action required" blocks for the versions in between. Those blocks carry the
             coupling groups, which a raw `git diff` can never tell you and which are the expensive
             half of a backfill. The CHANGELOG is read out of the fetched upstream tag, so this still
             works after `init-scaffold.ps1 -Cleanup` removed the local copy.

    report - I found a scaffold-level defect. Composes an upstream issue body carrying provenance
             (scaffold version, OS, PowerShell version, the surface at fault, the reproduction and
             any related lesson id), scans it for secrets because the issue is public, prints it and
             writes it to _local/. It STOPS there. Creating the issue needs an explicit -Send.

  Deciding NOT to take a version is a first-class outcome, not a failure: a change that would hurt
  this project is correctly skipped, and the ledger records the reason so the next session does not
  re-litigate it. See docs/SCAFFOLD-SYNC.md.

  Patch application is deliberately NOT automated. It stays three git commands under human decision
  (`git diff` into a file, `git apply --3way --check`, then apply) because which versions to take is
  a judgment call this script must not make for you.

  Network boundary: only `check -Fetch` and `report -Send` touch the network, and both are explicit.
  The triage probe `scaffold-stale` reads what is already on disk and never fetches, which keeps the
  heartbeat read-only, offline and deterministic (docs/LOOP-ENGINEERING.md).

.PARAMETER Verb      check (default) | report | selfcheck.
.PARAMETER Fetch     check only: refresh upstream tags first. This is the only network call in check.
.PARAMETER Remote    Name of the git remote pointing at the upstream scaffold. Default 'scaffold'.
.PARAMETER Title     report: one-line issue title.
.PARAMETER Summary   report: what is wrong, in prose.
.PARAMETER Repro     report: the smallest reproduction, ideally a command plus its observed output.
.PARAMETER Surface   report: the scaffold surface at fault, e.g. 'scripts/task.ps1' or 'selftest 14d'.
.PARAMETER LessonId  report: related lesson id (Lnn) if this project already recorded one.
.PARAMETER Send      report: actually create the issue. Without it the script only prints and writes.
.PARAMETER OutFile   report: where to write the composed body. Default _local/scaffold-issue.md.
.EXAMPLE
  pwsh -File scripts\scaffold-sync.ps1 check -Fetch
.EXAMPLE
  pwsh -File scripts\scaffold-sync.ps1 report -Title 'ship deadlocks when ...' -Surface 'scripts/task.ps1'
.EXAMPLE
  pwsh -File scripts\scaffold-sync.ps1 selfcheck
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][ValidateSet('check', 'report', 'selfcheck')][string]$Verb = 'check',
  [switch]$Fetch,
  [string]$Remote = 'scaffold',
  [string]$Title,
  [string]$Summary,
  [string]$Repro,
  [string]$Surface,
  [string]$LessonId,
  [switch]$Send,
  [string]$OutFile,
  [switch]$AsLibrary
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# The pure helpers below are reused by the triage probe through -AsLibrary, so nothing that runs at
# load time may live above the early return: no encoding prelude, no config read, no path resolution.

# ---------------------------------------------------------------------------
# Pure helpers (no git, no network). These carry the parsing risk, so selfcheck drives them directly.
# ---------------------------------------------------------------------------

function ConvertTo-ScaffoldVersion {
  <# Tag name or bare version -> [version], or $null when it is not a release tag. #>
  param([string]$Text)
  if (-not $Text) { return $null }
  $m = [regex]::Match($Text.Trim(), '^v?(\d+\.\d+\.\d+)$')
  if (-not $m.Success) { return $null }
  return [version]$m.Groups[1].Value
}

function Get-NewerVersion {
  <# Release tags strictly newer than $Since, oldest first. Junk refs are ignored, not fatal. #>
  param([string[]]$Tags, [string]$Since)
  $base = ConvertTo-ScaffoldVersion $Since
  $out = @()
  foreach ($t in $Tags) {
    $v = ConvertTo-ScaffoldVersion $t
    if (-not $v) { continue }
    if ($null -eq $base -or $v -gt $base) { $out += [pscustomobject]@{ Tag = $t; Version = $v } }
  }
  return @($out | Sort-Object Version)
}

function Get-DownstreamBlock {
  <#
    Extract the "Downstream action required / effect" blockquote for one version out of a CHANGELOG.
    Stateful line walk rather than index slicing (L94): the block runs from the first `> **Downstream`
    line to the first line that leaves the blockquote, and the section ends at the next `## [` heading.
    Returns '' when the version has no downstream block, which is itself worth reporting upstream -
    the release ritual requires one on every entry.
  #>
  param([string]$Changelog, [string]$Version)
  if (-not $Changelog) { return '' }
  $want = '## [' + $Version + ']'
  $inSection = $false
  $inBlock = $false
  $lines = [System.Collections.Generic.List[string]]::new()
  foreach ($line in ($Changelog -split "`r?`n")) {
    if ($line.StartsWith('## [')) {
      if ($inBlock) { break }
      $inSection = $line.StartsWith($want)
      continue
    }
    if (-not $inSection) { continue }
    if (-not $inBlock) {
      if ($line -match '^>\s*\*\*Downstream') { $inBlock = $true; $lines.Add($line) }
      continue
    }
    if ($line.StartsWith('>')) { $lines.Add($line); continue }
    break
  }
  return ($lines -join "`n")
}

function Get-SyncedVersion {
  <#
    The version this project has evaluated up to = the newest version row in the ledger, whatever the
    decision was. A row recorded as `skipped` still counts as evaluated: the point of writing the
    reason down is that the question is settled and must not be asked again. With no ledger rows the
    project has never evaluated anything, so provenance (_config ScaffoldVersion) is the floor.

    The ledger is only the region below the SCAFFOLD-SYNC-LEDGER sentinel, and a row must lead with a
    version plus an allowed decision. Without both bounds, an unrelated table can silently raise the
    high-water mark (#201). Missing sentinel fails closed to the provenance floor.
  #>
  param([string]$LedgerText, [string]$Fallback)
  $best = $null
  $inLedger = $false
  if ($LedgerText) {
    foreach ($line in ($LedgerText -split "`r?`n")) {
      if ($line -match 'SCAFFOLD-SYNC-LEDGER') { $inLedger = $true; continue }
      if (-not $inLedger) { continue }
      if ($line -notmatch '^\s*\|') { continue }
      $cells = @(($line -split '\|') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
      if ($cells.Count -lt 2 -or $cells[1] -notmatch '^(applied|partial|skipped)$') { continue }
      $v = ConvertTo-ScaffoldVersion $cells[0]
      if ($v -and ($null -eq $best -or $v -gt $best)) { $best = $v }
    }
  }
  if ($null -eq $best) { return $Fallback }
  return $best.ToString()
}

# ---------------------------------------------------------------------------
# git access (read-only unless -Fetch)
# ---------------------------------------------------------------------------

function Test-UpstreamRemote {
  param([string]$Name)
  $null = & git -C $RepoRoot remote get-url $Name 2>$null
  return ($LASTEXITCODE -eq 0)
}

function Get-UpstreamTag {
  param([string]$Namespace)
  $raw = & git -C $RepoRoot for-each-ref "--format=%(refname:strip=2)" $Namespace 2>$null
  if ($LASTEXITCODE -ne 0 -or -not $raw) { return @() }
  return @($raw | Where-Object { $_ })
}

function Get-UpstreamChangelog {
  param([string]$Tag)
  $text = & git -C $RepoRoot show ($UpstreamRefNs + '/' + $Tag + ':CHANGELOG.md') 2>$null
  if ($LASTEXITCODE -ne 0) { return '' }
  return ($text -join "`n")
}

# ---------------------------------------------------------------------------
# check
# ---------------------------------------------------------------------------

function Invoke-Check {
  param([string]$Remote, [switch]$Fetch)
  if (-not (Test-UpstreamRemote $Remote)) {
    Write-Host "[FLEET-NO-UPSTREAM] no git remote named '$Remote' - this project is not linked to its scaffold yet." -ForegroundColor Yellow
    Write-Host "  one-time setup : git remote add $Remote https://github.com/$UpstreamRepo.git"
    Write-Host "  then           : pwsh -File scripts\scaffold-sync.ps1 check -Fetch"
    return
  }
  if ($Fetch) {
    Write-Host "[FLEET-FETCH] refreshing upstream tags from '$Remote' into $UpstreamRefNs/ ..." -ForegroundColor DarkGray
    # --no-tags is load-bearing: without it git ALSO auto-follows the same tags into refs/tags/,
    # colliding with this project's own version tags. The dedicated namespace is the whole point.
    & git -C $RepoRoot fetch --no-tags $Remote ('+refs/tags/*:' + $UpstreamRefNs + '/*')
    if ($LASTEXITCODE -ne 0) {
      Write-Host "[FLEET-FETCH-FAILED] could not reach '$Remote'. Reporting on what is already on disk." -ForegroundColor Yellow
    }
  }
  $tags = @(Get-UpstreamTag ($UpstreamRefNs + '/'))
  if ($tags.Count -eq 0) {
    Write-Host "[FLEET-NO-UPSTREAM] remote '$Remote' is configured but no upstream release tags are on disk." -ForegroundColor Yellow
    Write-Host "  run: pwsh -File scripts\scaffold-sync.ps1 check -Fetch"
    return
  }
  $ledgerText = if (Test-Path $LedgerDoc) { Get-Content $LedgerDoc -Raw } else { '' }
  $synced = Get-SyncedVersion $ledgerText $LocalVersion
  $syncedV = ConvertTo-ScaffoldVersion $synced
  $syncedLabel = if ($syncedV) { "v$synced" } else { 'an unrecorded baseline (no ledger row and no ScaffoldVersion)' }
  $behind = @(Get-NewerVersion $tags $synced)

  if ($behind.Count -eq 0) {
    Write-Host "[FLEET-CURRENT] evaluated up to $syncedLabel; no newer upstream release on disk." -ForegroundColor Green
    if (-not $Fetch) { Write-Host '  (that is what was fetched last time - add -Fetch to refresh)' -ForegroundColor DarkGray }
    return
  }

  $newestTag = $behind[$behind.Count - 1].Tag
  $latest    = $behind[$behind.Count - 1].Version.ToString()
  Write-Host "[FLEET-BEHIND] $($behind.Count) upstream release(s) not yet evaluated: $syncedLabel -> v$latest" -ForegroundColor Yellow
  Write-Host ''
  $changelog = Get-UpstreamChangelog $newestTag
  if (-not $changelog) {
    Write-Host "[FLEET-NO-CHANGELOG] cannot read CHANGELOG.md out of $newestTag - the fetch may be shallow." -ForegroundColor Yellow
  }
  foreach ($item in $behind) {
    $v = $item.Version.ToString()
    Write-Host ("--- v$v " + ('-' * 40)) -ForegroundColor Cyan
    $block = Get-DownstreamBlock $changelog $v
    if ($block) { Write-Host $block }
    else { Write-Host '  (no downstream block for this version - read its diff in full, and consider reporting the omission upstream)' -ForegroundColor DarkGray }
    Write-Host ''
  }
  Write-Host 'Deciding NOT to take a version is a valid outcome. Record every version either way:' -ForegroundColor DarkGray
  Write-Host '  docs/SCAFFOLD-SYNC.md -> one row per version: applied | partial | skipped, plus the reason.'
  if (-not $syncedV) {
    Write-Host '  This project has no recorded baseline, so there is no range to diff against yet. Record the version'
    Write-Host '  you are actually on as the first ledger row (or set ScaffoldVersion in scripts/_config.ps1), then re-run.'
    return
  }
  Write-Host 'To inspect or apply one version (use the paths from that version''s block above, not these defaults):'
  Write-Host "  git diff $UpstreamRefNs/v$synced..$UpstreamRefNs/v$latest -- scripts/ .claude/ .github/ > _local/scaffold-backfill.patch"
  Write-Host '  git apply --3way --check _local/scaffold-backfill.patch'
  Write-Host '  git apply --3way _local/scaffold-backfill.patch'
}

# ---------------------------------------------------------------------------
# report
# ---------------------------------------------------------------------------

function Format-IssueBody {
  param([string]$BodySummary, [string]$BodyRepro, [string]$BodySurface, [string]$BodyLesson)
  $osText = if ($IsWindows) { 'Windows' } elseif ($IsMacOS) { 'macOS' } else { 'Linux' }
  $sb = [System.Text.StringBuilder]::new()
  [void]$sb.AppendLine('## What is wrong')
  [void]$sb.AppendLine('')
  [void]$sb.AppendLine($(if ($BodySummary) { $BodySummary } else { '_(fill in: what the scaffold did, and what it should have done)_' }))
  [void]$sb.AppendLine('')
  [void]$sb.AppendLine('## Reproduction')
  [void]$sb.AppendLine('')
  [void]$sb.AppendLine($(if ($BodyRepro) { $BodyRepro } else { '_(fill in: smallest command plus its observed output)_' }))
  [void]$sb.AppendLine('')
  [void]$sb.AppendLine('## Provenance')
  [void]$sb.AppendLine('')
  [void]$sb.AppendLine('| field | value |')
  [void]$sb.AppendLine('|---|---|')
  [void]$sb.AppendLine("| scaffold version | $LocalVersion |")
  [void]$sb.AppendLine("| surface at fault | $(if ($BodySurface) { $BodySurface } else { '(unspecified)' }) |")
  [void]$sb.AppendLine("| related lesson | $(if ($BodyLesson) { $BodyLesson } else { '(none)' }) |")
  [void]$sb.AppendLine("| os | $osText |")
  [void]$sb.AppendLine("| powershell | $($PSVersionTable.PSVersion) |")
  [void]$sb.AppendLine('')
  [void]$sb.AppendLine('_Filed from a downstream project generated by this scaffold. No downstream business content is included._')
  return $sb.ToString()
}

function Invoke-Report {
  param([string]$Title, [string]$Summary, [string]$Repro, [string]$Surface, [string]$LessonId, [switch]$Send, [string]$OutFile)
  $outPath = if ($OutFile) { $OutFile } else { Join-Path $RepoRoot '_local/scaffold-issue.md' }
  $issueTitle = if ($Title) { $Title } else { 'scaffold: <one-line summary>' }
  $body = Format-IssueBody $Summary $Repro $Surface $LessonId

  # The issue is public. Reuse the deterministic secret scanner rather than inventing a second one.
  $hits = @()
  try {
    . (Join-Path $PSScriptRoot 'check-secrets.ps1') -AsLibrary
    $n = 0
    foreach ($line in ($body -split "`r?`n")) {
      $n++
      $hit = Find-LineSecret $line
      if ($hit) { $hits += "line ${n}: $hit" }
    }
  } catch {
    Write-Host '[FLEET-SCAN-UNAVAILABLE] check-secrets.ps1 could not be loaded; read the body yourself before sending.' -ForegroundColor Yellow
  }
  if ($hits.Count -gt 0) {
    Write-Host '[FLEET-REPORT-BLOCKED] the composed body matches secret patterns - not writing, not sending:' -ForegroundColor Red
    foreach ($h in $hits) { Write-Host "  $h" -ForegroundColor Red }
    exit 1
  }

  $dir = Split-Path $outPath -Parent
  if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
  Set-Content -Path $outPath -Value $body -Encoding utf8 -NoNewline

  Write-Host "=== upstream issue -> $UpstreamRepo ===" -ForegroundColor Cyan
  Write-Host "title: $issueTitle"
  Write-Host ''
  Write-Host $body
  Write-Host "--- saved to $outPath ---" -ForegroundColor DarkGray

  if (-not $Send) {
    Write-Host '[FLEET-REPORT-DRYRUN] nothing was sent. Read the body above, then re-run with -Send to create the issue.' -ForegroundColor Yellow
    return
  }
  try { . (Join-Path $PSScriptRoot '_guard.ps1'); Assert-PersonalAccount } catch {
    Write-Host "[FLEET-REPORT-BLOCKED] account guard refused: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
  }
  $url = & gh issue create --repo $UpstreamRepo --title $issueTitle --body-file $outPath
  if ($LASTEXITCODE -ne 0) {
    Write-Host '[FLEET-REPORT-FAILED] gh issue create returned non-zero (see above).' -ForegroundColor Red
    exit 1
  }
  Write-Host "[FLEET-REPORT-SENT] $url" -ForegroundColor Green
  Write-Host '  Record the issue link in docs/SCAFFOLD-SYNC.md so the version that fixes it can be matched back.'
}

# ---------------------------------------------------------------------------
# selfcheck - hermetic; drives the parsing helpers that carry the real risk
# ---------------------------------------------------------------------------

function Invoke-Selfcheck {
  $fails = [System.Collections.Generic.List[string]]::new()
  $sampleChangelog = @'
# Changelog

## [0.43.0] - 2026-09-01

> **Downstream action required** - coupling group: a.ps1 + b.ps1 together.
>
> classification: behavior change.

### Fixed
- something

## [0.42.0] - 2026-08-25

body with no downstream block at all

## [0.41.0] - 2026-08-21

> **Downstream effect**: nothing reaches you.

### Changed
- other
'@

  # 1. version parsing accepts release tags and bare versions, rejects everything else.
  if ($null -ne (ConvertTo-ScaffoldVersion 'v1.2')) { $fails.Add('ConvertTo-ScaffoldVersion accepted a two-part version') }
  if ($null -ne (ConvertTo-ScaffoldVersion 'nightly')) { $fails.Add('ConvertTo-ScaffoldVersion accepted a non-release tag') }
  if ((ConvertTo-ScaffoldVersion 'v0.41.0') -ne [version]'0.41.0') { $fails.Add('ConvertTo-ScaffoldVersion mis-parsed a v-prefixed tag') }

  # 2. only strictly newer versions are reported, ascending, junk refs ignored.
  $newer = @(Get-NewerVersion @('v0.41.0', 'v0.43.0', 'v0.42.0', 'nightly') '0.41.0')
  if ($newer.Count -ne 2) { $fails.Add("Get-NewerVersion returned $($newer.Count) versions, expected 2") }
  elseif ($newer[0].Tag -ne 'v0.42.0' -or $newer[1].Tag -ne 'v0.43.0') { $fails.Add('Get-NewerVersion did not sort ascending') }
  if (@(Get-NewerVersion @('v0.41.0') '0.41.0').Count -ne 0) { $fails.Add('Get-NewerVersion reported the already-synced version as newer') }

  # 3. the downstream block is cut at both ends, not bled across sections.
  $b43 = Get-DownstreamBlock $sampleChangelog '0.43.0'
  if ($b43 -notmatch 'coupling group') { $fails.Add('Get-DownstreamBlock lost the 0.43.0 block') }
  if ($b43 -match 'nothing reaches you') { $fails.Add('Get-DownstreamBlock bled into a later section') }
  if ($b43 -match 'something') { $fails.Add('Get-DownstreamBlock swallowed prose after the blockquote') }
  if ((Get-DownstreamBlock $sampleChangelog '0.42.0') -ne '') { $fails.Add('Get-DownstreamBlock invented a block for a version that has none') }

  # 4. the ledger high-water mark wins over provenance, and a skipped row still counts as evaluated.
  $ledger = @'
<!-- SCAFFOLD-SYNC-LEDGER -->

| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | its new gate contradicts a deliberate local removal |
| v0.41.0 | applied | - |
'@
  if ((Get-SyncedVersion $ledger '0.30.0') -ne '0.42.0') { $fails.Add('Get-SyncedVersion ignored the newest ledger row') }
  if ((Get-SyncedVersion '' '0.30.0') -ne '0.30.0') { $fails.Add('Get-SyncedVersion did not fall back to provenance on an empty ledger') }

  # 4b. A ledger-shaped row above the sentinel is not part of the ledger.
  $decoyAbove = @'
| v0.99.0 | applied | decoy above the ledger |
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | settled locally |
'@
  if ((Get-SyncedVersion $decoyAbove '0.30.0') -ne '0.42.0') { $fails.Add('Get-SyncedVersion read a row above the ledger sentinel (#201)') }

  # 4c. A release mentioned in a different table below the ledger is not a ledger decision.
  $decoyBelow = @'
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | settled locally |
| follow-up | source release |
|---|---|
| local card | v0.99.0 |
'@
  if ((Get-SyncedVersion $decoyBelow '0.30.0') -ne '0.42.0') { $fails.Add('Get-SyncedVersion read a non-ledger row below the sentinel (#201)') }

  # 4d. Missing sentinel fails closed to the provenance floor.
  $noSentinel = @'
| version | decision | reason |
|---|---|---|
| v0.99.0 | applied | marker was deleted |
'@
  if ((Get-SyncedVersion $noSentinel '0.30.0') -ne '0.30.0') { $fails.Add('Get-SyncedVersion accepted a table with no ledger sentinel (#201)') }

  # 4e. A version-first row without an allowed decision is not a ledger decision.
  $versionFirstDecoy = @'
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | settled locally |
| v0.99.0 | 2026-09-09 | release history, not a decision |
'@
  if ((Get-SyncedVersion $versionFirstDecoy '0.30.0') -ne '0.42.0') { $fails.Add('Get-SyncedVersion accepted a version-first row without a ledger decision (#201)') }

  # 5. cross-surface wiring: the probe is registered and the heartbeat stayed offline.
  $triage = Join-Path $PSScriptRoot 'triage.ps1'
  if (Test-Path $triage) {
    $tRaw = Get-Content $triage -Raw
    if ($tRaw -notmatch 'scaffold-stale') { $fails.Add('triage.ps1 does not register the scaffold-stale probe') }
    if ($tRaw -match 'git fetch') { $fails.Add('triage.ps1 contains a fetch - the heartbeat must stay offline') }
  }

  if ($fails.Count -gt 0) {
    foreach ($f in $fails) { Write-Host "  FAIL $f" -ForegroundColor Red }
    Write-Host 'scaffold-sync selfcheck: FAIL'
    exit 1
  }
  Write-Host 'scaffold-sync selfcheck: PASS (version parse / newer-set / downstream-block cut / ledger high-water / ledger scope / offline heartbeat)' -ForegroundColor Green
}

# Library consumers (the triage probe) take only the pure helpers above and stop here, so that
# dot-sourcing never runs a scan and never leaks encoding settings into the caller scope (L85).
if ($AsLibrary) { return }

try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 stdout + native non-zero by code (TD54); missing = fail-open
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Upstream tags are fetched into a dedicated ref namespace instead of refs/tags/, so that the
# scaffold's release tags never collide with this project's own version tags.
$UpstreamRefNs = 'refs/scaffold-tags'
$LedgerDoc     = Join-Path $RepoRoot 'docs/SCAFFOLD-SYNC.md'

# _config is optional: an unconfigured or partially configured tree still runs (graceful degradation).
$UpstreamRepo = 'Asun28/claude-devops-scaffold'
$LocalVersion = 'unknown'
try {
  . (Join-Path $PSScriptRoot '_config.ps1')
  $LocalVersion = Get-ScaffoldVersion
  $configured = Get-ScaffoldUpstreamRepo
  if ($configured) { $UpstreamRepo = $configured }
} catch { }

switch ($Verb) {
  'check'     { Invoke-Check -Remote $Remote -Fetch:$Fetch }
  'report'    { Invoke-Report -Title $Title -Summary $Summary -Repro $Repro -Surface $Surface -LessonId $LessonId -Send:$Send -OutFile $OutFile }
  'selfcheck' { Invoke-Selfcheck }
}
