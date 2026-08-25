#requires -Version 7
<#
.SYNOPSIS
  Check upstream scaffold releases or compose a scrubbed upstream issue.

.DESCRIPTION
  `check` compares the canonical decision ledger with locally cached upstream tags; `-Fetch` is its
  only network path. `report` scans title/body, saves a draft, and sends only with explicit `-Send`.
  Patch application stays a human decision. The `scaffold-stale` heartbeat never fetches.

.PARAMETER Verb     check (default) | report | selfcheck.
.PARAMETER Fetch    Refresh upstream tags for check.
.PARAMETER Send     Create the public issue after all guards pass.
.PARAMETER OutFile  Draft path; defaults to _local/scaffold-issue.md.
.EXAMPLE
  pwsh -File scripts\scaffold-sync.ps1 check -Fetch
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
  param([string]$LedgerText, [string]$Fallback, [string[]]$KnownTags = @())
  $best = $null; $lines = @($LedgerText -split "`r?`n")
  $markers = @(0..($lines.Count - 1) | Where-Object { $lines[$_].Trim() -ceq '<!-- SCAFFOLD-SYNC-LEDGER -->' })
  if ($markers.Count -eq 0) { return $Fallback }
  if ($markers.Count -ne 1) { throw '[FLEET-LEDGER-INVALID] expected exactly one whole-line ledger marker' }
  $decisions = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::Ordinal)
  foreach ($line in $lines[($markers[0] + 1)..($lines.Count - 1)]) {
    $m = [regex]::Match($line, '^\s*\|\s*(?<version>v?\d+\.\d+\.\d+)\s*\|\s*(?<decision>[^|]*?)\s*\|')
    if (-not $m.Success) { continue }
    $v = ConvertTo-ScaffoldVersion $m.Groups['version'].Value; $decision = $m.Groups['decision'].Value
    if ($decision -cnotmatch '^(applied|partial|skipped)$') { throw "[FLEET-LEDGER-INVALID] $v has non-canonical decision '$decision'" }
    $key = $v.ToString()
    if ($decisions.ContainsKey($key)) { throw "[FLEET-LEDGER-INVALID] duplicate decision for v$key" }
    $decisions[$key] = $decision
    if ($null -eq $best -or $v -gt $best) { $best = $v }
  }
  $floor = ConvertTo-ScaffoldVersion $Fallback
  foreach ($tag in $KnownTags) {
    $v = ConvertTo-ScaffoldVersion $tag
    if ($v -and ($null -ne $floor) -and $v -gt $floor -and -not $decisions.ContainsKey($v.ToString())) {
      throw "[FLEET-LEDGER-INVALID] locally available v$v has no decision"
    }
  }
  if ($null -eq $best) { return $Fallback }
  return $best.ToString()
}

function Get-ScaffoldIssueSecretHit {
  param([string]$IssueTitle, [string]$IssueBody, [scriptblock]$Scanner)
  $hits = @(); $n = 0
  foreach ($entry in @(@{ Label='title'; Text=$IssueTitle }) + @($IssueBody -split "`r?`n" | ForEach-Object { $n++; @{ Label="body line $n"; Text=$_ } })) {
    $hit = & $Scanner $entry.Text
    if ($hit) { $hits += "$($entry.Label): $hit" }
  }
  return @($hits)
}

function Test-ScaffoldReportMaySend {
  param([bool]$ScannerAvailable, [int]$SecretHitCount, [string]$ConfigError)
  return ($ScannerAvailable -and $SecretHitCount -eq 0 -and -not $ConfigError)
}

function Get-ScaffoldStaleState {
  param([string]$OriginUrl, [string]$Upstream, [string[]]$Tags, [string]$Synced)
  if ($OriginUrl -and $Upstream -and $OriginUrl -match [regex]::Escape($Upstream)) { return [pscustomobject]@{ Status='self'; Behind=@(); Latest='' } }
  if (@($Tags).Count -eq 0) { return [pscustomobject]@{ Status='no-tags'; Behind=@(); Latest='' } }
  $behind = @(Get-NewerVersion $Tags $Synced)
  if ($behind.Count -eq 0) { return [pscustomobject]@{ Status='current'; Behind=@(); Latest='' } }
  return [pscustomobject]@{ Status='behind'; Behind=$behind; Latest=$behind[-1].Version.ToString() }
}

function Get-TriageScaffoldProbeContract {
  param([string]$Text)
  $tokens = $null; $errors = $null
  $ast = [System.Management.Automation.Language.Parser]::ParseInput($Text, [ref]$tokens, [ref]$errors)
  $fetch = @($ast.FindAll({
    param($node)
    if ($node -isnot [System.Management.Automation.Language.CommandAst] -or $node.GetCommandName() -notin @('git','git.exe')) { return $false }
    return @($node.CommandElements | Where-Object { $_.Extent.Text.Trim([char[]]@([char]39,[char]34)) -ceq 'fetch' }).Count -gt 0
  }, $true)).Count -gt 0
  return [pscustomobject]@{
    Registered = $Text -cmatch '(?m)^[ \t]*Invoke-ProbeScaffoldStale[ \t]*$'
    Documented = $Text -cmatch '(?m)^[ \t]*-[ \t]+scaffold-stale[ \t]*:'
    Offline = @($errors).Count -eq 0 -and -not $fetch
  }
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
  try { $synced = Get-SyncedVersion $ledgerText $LocalVersion $tags } catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
  }
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
  $hits = @(); $scannerAvailable = $false
  try {
    . (Join-Path $PSScriptRoot 'check-secrets.ps1') -AsLibrary
    if (-not (Get-Command Find-LineSecret -CommandType Function -ErrorAction SilentlyContinue)) { throw 'Find-LineSecret was not loaded' }
    $scannerAvailable = $true
    $hits = @(Get-ScaffoldIssueSecretHit $issueTitle $body { param($line) Find-LineSecret $line })
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
  if (-not (Test-ScaffoldReportMaySend $scannerAvailable $hits.Count $ConfigLoadError)) {
    if ($ConfigLoadError) { Write-Host "[FLEET-CONFIG-INVALID] refusing public send: $ConfigLoadError" -ForegroundColor Red }
    else { Write-Host '[FLEET-SCAN-UNAVAILABLE] refusing public send without the deterministic secret scanner.' -ForegroundColor Red }
    exit 1
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

  # 4e. A version-first row without an allowed decision makes the ledger invalid.
  $versionFirstDecoy = @'
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | settled locally |
| v0.99.0 | 2026-09-09 | release history, not a decision |
'@
  try { $null = Get-SyncedVersion $versionFirstDecoy '0.30.0'; $fails.Add('Get-SyncedVersion ignored a version-first malformed decision (#201)') } catch { }

  # 4f. Only one exact whole-line marker opens the ledger; prose mentions and duplicates cannot.
  $productionPreamble = @'
This prose names SCAFFOLD-SYNC-LEDGER before the real marker.
| v0.99.0 | applied | preamble decoy |
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | partial | settled |
| v0.41.0 | applied | settled |
'@
  if ((Get-SyncedVersion $productionPreamble '0.30.0' @('v0.41.0','v0.42.0')) -ne '0.42.0') { $fails.Add('Get-SyncedVersion accepted a production-preamble decoy before the exact marker') }
  $duplicateMarker = $productionPreamble + "`n<!-- SCAFFOLD-SYNC-LEDGER -->"
  try { $null = Get-SyncedVersion $duplicateMarker '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion accepted duplicate exact markers') } catch { }

  # 4g. Every locally available release needs one canonical decision; gaps/malformed/duplicates fail closed.
  $badDecision = $productionPreamble.Replace('| v0.41.0 | applied |', '| v0.41.0 | deferred |')
  try { $null = Get-SyncedVersion $badDecision '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion ignored a malformed decision') } catch { }
  $gap = $productionPreamble -replace '(?m)^\| v0\.41\.0 \| applied \| settled \|\r?\n?', ''
  try { $null = Get-SyncedVersion $gap '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion ignored a locally available release gap') } catch { }
  $duplicateRow = $productionPreamble + "`n| v0.42.0 | skipped | duplicate |"
  try { $null = Get-SyncedVersion $duplicateRow '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion ignored a duplicate release decision') } catch { }

  # 5. Public report gate scans title and body, and -Send fails closed without scanner/config trust.
  $fakeScanner = { param($line) if ($line -match 'TOKEN') { 'fake-secret' } }
  if (@(Get-ScaffoldIssueSecretHit 'TOKEN-in-title' 'safe body' $fakeScanner).Count -ne 1) { $fails.Add('report secret scan missed the public title') }
  if (@(Get-ScaffoldIssueSecretHit 'safe title' "safe`nTOKEN-in-body" $fakeScanner).Count -ne 1) { $fails.Add('report secret scan missed the public body') }
  if (Test-ScaffoldReportMaySend $false 0 '') { $fails.Add('report -Send allowed an unavailable scanner') }
  if (Test-ScaffoldReportMaySend $true 0 'malformed config') { $fails.Add('report -Send allowed a malformed configuration') }
  if (-not (Test-ScaffoldReportMaySend $true 0 '')) { $fails.Add('report -Send rejected trusted clean input') }

  # 6. Probe state is hermetic: self-repo/no-tags/current/behind are distinct and deterministic.
  $localOrigin = 'https://github.com/Asun28/project.git'; $upstream = 'Asun28/claude-devops-scaffold'
  if ((Get-ScaffoldStaleState "https://github.com/$upstream.git" $upstream @() '0.42.0').Status -ne 'self') { $fails.Add('scaffold-stale did not suppress the upstream repository itself') }
  if ((Get-ScaffoldStaleState $localOrigin $upstream @() '0.42.0').Status -ne 'no-tags') { $fails.Add('scaffold-stale no-tags fixture failed') }
  if ((Get-ScaffoldStaleState $localOrigin $upstream @('v0.42.0') '0.42.0').Status -ne 'current') { $fails.Add('scaffold-stale current fixture failed') }
  $stale = Get-ScaffoldStaleState $localOrigin $upstream @('v0.42.0','v0.43.0') '0.42.0'
  if ($stale.Status -ne 'behind' -or $stale.Behind.Count -ne 1 -or $stale.Latest -ne '0.43.0') { $fails.Add('scaffold-stale behind fixture failed') }

  # 7. Cross-surface wiring is anchored, documented, and offline even with `git -C ... fetch` mutations.
  $triage = Join-Path $PSScriptRoot 'triage.ps1'
  if (Test-Path $triage) {
    $tRaw = Get-Content $triage -Raw
    $contract = Get-TriageScaffoldProbeContract $tRaw
    if (-not $contract.Registered) { $fails.Add('triage.ps1 does not invoke scaffold-stale on its own anchored line') }
    if (-not $contract.Documented) { $fails.Add('triage.ps1 DESCRIPTION omits scaffold-stale') }
    if (-not $contract.Offline) { $fails.Add('triage.ps1 contains a git fetch - the heartbeat must stay offline') }
    $noInvoke = [regex]::Replace($tRaw, '(?m)^Invoke-ProbeScaffoldStale\s*$', '# invocation removed', 1)
    if ((Get-TriageScaffoldProbeContract $noInvoke).Registered) { $fails.Add('scaffold-stale registration assertion survived invocation deletion') }
    $fetchMutant = [regex]::Replace($tRaw, '\bfor-each-ref\b', 'fetch', 1)
    if ((Get-TriageScaffoldProbeContract $fetchMutant).Offline) { $fails.Add('offline assertion missed a git -C ... fetch mutation') }
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
$UpstreamRepo = 'Asun28/claude-devops-scaffold'; $LocalVersion = 'unknown'; $ConfigLoadError = ''
$configPath = Join-Path $PSScriptRoot '_config.ps1'
if (Test-Path $configPath) {
  try {
    . $configPath
    if (Get-Command Get-ScaffoldVersion -CommandType Function -ErrorAction SilentlyContinue) { $LocalVersion = Get-ScaffoldVersion }
    # Legacy generated trees may predate this optional key/getter; absence keeps the canonical upstream default.
    if (Get-Command Get-ScaffoldUpstreamRepo -CommandType Function -ErrorAction SilentlyContinue) {
      $configured = Get-ScaffoldUpstreamRepo
      if ($configured) { $UpstreamRepo = $configured }
    }
  } catch { $ConfigLoadError = $_.Exception.Message }
}

switch ($Verb) {
  'check'     { Invoke-Check -Remote $Remote -Fetch:$Fetch }
  'report'    { Invoke-Report -Title $Title -Summary $Summary -Repro $Repro -Surface $Surface -LessonId $LessonId -Send:$Send -OutFile $OutFile }
  'selfcheck' { Invoke-Selfcheck }
}
