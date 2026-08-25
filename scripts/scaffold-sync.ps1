#requires -Version 7
<# Check cached upstream releases or compose a secret-scanned upstream issue.
   Network access requires check -Fetch; issue creation requires report -Send. #>
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

function ConvertTo-ScaffoldVersion {
  param([string]$Text)
  if (-not $Text) { return $null }
  $m = [regex]::Match($Text.Trim(), '^v?(\d+\.\d+\.\d+)$')
  if (-not $m.Success) { return $null }
  return [version]$m.Groups[1].Value
}

function Get-NewerVersion {
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
  param([string]$LedgerText, [string]$Fallback, [string[]]$KnownTags = @())
  $best = $null; $lines = @($LedgerText -split "`r?`n")
  $markers = @(0..($lines.Count - 1) | Where-Object { $lines[$_].Trim() -ceq '<!-- SCAFFOLD-SYNC-LEDGER -->' })
  if ($markers.Count -eq 0) { return $Fallback }
  if ($markers.Count -ne 1) { throw '[FLEET-LEDGER-INVALID] expected exactly one whole-line ledger marker' }
  $decisions = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::Ordinal)
  for ($i = $markers[0] + 1; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
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
    if ($v -and $best -and ($null -ne $floor) -and $v -gt $floor -and $v -le $best -and -not $decisions.ContainsKey($v.ToString())) {
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

function Find-ScaffoldPublicSecret {
  param([string]$Line, [System.Collections.IDictionary]$Patterns)
  # Public text has no source-fixture exemptions.
  foreach ($name in $Patterns.Keys) {
    if ($Line -match $Patterns[$name]) { return $name }
  }
  return $null
}

function Test-ScaffoldReportMaySend {
  param([bool]$ScannerAvailable, [int]$SecretHitCount, [string]$ConfigError)
  return ($ScannerAvailable -and $SecretHitCount -eq 0 -and -not $ConfigError)
}

function Test-ScaffoldReportComplete {
  param([string]$IssueTitle, [string]$IssueSummary, [string]$IssueRepro, [string]$IssueSurface)
  return -not @(@($IssueTitle,$IssueSummary,$IssueRepro,$IssueSurface) | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count
}

function Resolve-ScaffoldConfiguration {
  param([string]$Path, [string]$DefaultUpstream)
  $version = 'unknown'; $upstream = $DefaultUpstream; $errorText = ''
  if (-not (Test-Path $Path)) { return [pscustomobject]@{ Version=$version; Upstream=$upstream; Error='' } }
  try {
    $tokens = $null; $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    if (@($errors).Count) { throw $errors[0].Message }
    $names = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] }, $true) | ForEach-Object Name)
    . $Path
    if ($names -contains 'Get-ScaffoldVersion') { $version = Get-ScaffoldVersion }
    if ($names -contains 'Get-ScaffoldUpstreamRepo') { $configured = Get-ScaffoldUpstreamRepo; if ($configured) { $upstream = $configured } }
  } catch { $errorText = $_.Exception.Message }
  return [pscustomobject]@{ Version=$version; Upstream=$upstream; Error=$errorText }
}

function ConvertTo-ScaffoldRepositoryIdentity {
  param([string]$Text)
  if (-not $Text) { return $null }
  $m = [regex]::Match($Text.Trim(), '^(?i)(?:https?://github\.com/|ssh://git@github\.com/|git@github\.com:)?(?<owner>[a-z0-9][a-z0-9-]{0,38})/(?<repo>[a-z0-9_.-]+?)(?:\.git)?/?$')
  if (-not $m.Success) { return $null }
  return ($m.Groups['owner'].Value + '/' + $m.Groups['repo'].Value).ToLowerInvariant()
}

function Test-ScaffoldRepositoryIdentity {
  param([string]$RemoteUrl, [string]$Upstream)
  $actual = ConvertTo-ScaffoldRepositoryIdentity $RemoteUrl
  $expected = ConvertTo-ScaffoldRepositoryIdentity $Upstream
  return ($actual -and $expected -and [string]::Equals($actual, $expected, [System.StringComparison]::Ordinal))
}

function Get-ScaffoldStaleState {
  param([string]$OriginUrl, [string]$Upstream, [string[]]$Tags, [string]$Synced)
  if (Test-ScaffoldRepositoryIdentity $OriginUrl $Upstream) { return [pscustomobject]@{ Status='self'; Behind=@(); Latest='' } }
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

function Test-UpstreamRemote {
  param([string]$Name, [string]$ExpectedUpstream, [scriptblock]$GetUrl)
  if ($GetUrl) { $url = & $GetUrl $Name; if (-not $url) { return $false } }
  else {
    $url = & git -C $RepoRoot remote get-url $Name 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $url) { return $false }
  }
  return (Test-ScaffoldRepositoryIdentity ([string]$url) $ExpectedUpstream)
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

function Invoke-Check {
  param([string]$Remote, [switch]$Fetch)
  if (-not (Test-UpstreamRemote $Remote $UpstreamRepo)) {
    Write-Host "[FLEET-NO-UPSTREAM] remote '$Remote' is missing or does not exactly match '$UpstreamRepo'." -ForegroundColor Yellow
    Write-Host "  one-time setup : git remote add $Remote https://github.com/$UpstreamRepo.git"
    Write-Host "  then           : pwsh -File scripts\scaffold-sync.ps1 check -Fetch"
    return
  }
  if ($Fetch) {
    Write-Host "[FLEET-FETCH] refreshing upstream tags from '$Remote' into $UpstreamRefNs/ ..." -ForegroundColor DarkGray
    # Keep scaffold releases out of this project's refs/tags/.
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
  if ($Send -and -not (Test-ScaffoldReportComplete $Title $Summary $Repro $Surface)) {
    Write-Host '[FLEET-REPORT-BLOCKED] -Send requires Title, Summary, Repro, and Surface.' -ForegroundColor Red
    exit 1
  }

  $hits = @(); $scannerAvailable = $false
  try {
    . (Join-Path $PSScriptRoot 'check-secrets.ps1') -AsLibrary
    if (-not (Get-Command Find-LineSecret -CommandType Function -ErrorAction SilentlyContinue)) { throw 'Find-LineSecret was not loaded' }
    $scannerAvailable = $true
    $hits = @(Get-ScaffoldIssueSecretHit $issueTitle $body { param($line) Find-ScaffoldPublicSecret $line $ContentSecretPatterns })
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

  if ($null -ne (ConvertTo-ScaffoldVersion 'v1.2')) { $fails.Add('ConvertTo-ScaffoldVersion accepted a two-part version') }
  if ($null -ne (ConvertTo-ScaffoldVersion 'nightly')) { $fails.Add('ConvertTo-ScaffoldVersion accepted a non-release tag') }
  if ((ConvertTo-ScaffoldVersion 'v0.41.0') -ne [version]'0.41.0') { $fails.Add('ConvertTo-ScaffoldVersion mis-parsed a v-prefixed tag') }

  $newer = @(Get-NewerVersion @('v0.41.0', 'v0.43.0', 'v0.42.0', 'nightly') '0.41.0')
  if ($newer.Count -ne 2) { $fails.Add("Get-NewerVersion returned $($newer.Count) versions, expected 2") }
  elseif ($newer[0].Tag -ne 'v0.42.0' -or $newer[1].Tag -ne 'v0.43.0') { $fails.Add('Get-NewerVersion did not sort ascending') }
  if (@(Get-NewerVersion @('v0.41.0') '0.41.0').Count -ne 0) { $fails.Add('Get-NewerVersion reported the already-synced version as newer') }

  $b43 = Get-DownstreamBlock $sampleChangelog '0.43.0'
  if ($b43 -notmatch 'coupling group') { $fails.Add('Get-DownstreamBlock lost the 0.43.0 block') }
  if ($b43 -match 'nothing reaches you') { $fails.Add('Get-DownstreamBlock bled into a later section') }
  if ($b43 -match 'something') { $fails.Add('Get-DownstreamBlock swallowed prose after the blockquote') }
  if ((Get-DownstreamBlock $sampleChangelog '0.42.0') -ne '') { $fails.Add('Get-DownstreamBlock invented a block for a version that has none') }

  $ledger = @'
<!-- SCAFFOLD-SYNC-LEDGER -->

| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | its new gate contradicts a deliberate local removal |
| v0.41.0 | applied | - |
'@
  if ((Get-SyncedVersion $ledger '0.30.0') -ne '0.42.0') { $fails.Add('Get-SyncedVersion ignored the newest ledger row') }
  if ((Get-SyncedVersion '' '0.30.0') -ne '0.30.0') { $fails.Add('Get-SyncedVersion did not fall back to provenance on an empty ledger') }

  $decoyAbove = @'
| v0.99.0 | applied | decoy above the ledger |
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | settled locally |
'@
  if ((Get-SyncedVersion $decoyAbove '0.30.0') -ne '0.42.0') { $fails.Add('Get-SyncedVersion read a row above the ledger sentinel (#201)') }

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

  $noSentinel = @'
| version | decision | reason |
|---|---|---|
| v0.99.0 | applied | marker was deleted |
'@
  if ((Get-SyncedVersion $noSentinel '0.30.0') -ne '0.30.0') { $fails.Add('Get-SyncedVersion accepted a table with no ledger sentinel (#201)') }
  if ((Get-SyncedVersion '<!-- SCAFFOLD-SYNC-LEDGER -->' '0.30.0') -ne '0.30.0') { $fails.Add('marker-only ledger did not return provenance fallback') }

  $versionFirstDecoy = @'
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | skipped | settled locally |
| v0.99.0 | 2026-09-09 | release history, not a decision |
'@
  $badRowError = ''
  try { $null = Get-SyncedVersion $versionFirstDecoy '0.30.0' } catch { $badRowError = $_.Exception.Message }
  if ($badRowError -notmatch '^\[FLEET-LEDGER-INVALID\].*non-canonical decision') { $fails.Add('Get-SyncedVersion did not reject the exact malformed decision class (#201)') }

  $productionPreamble = @'
This prose names SCAFFOLD-SYNC-LEDGER before the real marker.
| v0.99.0 | applied | preamble decoy |
<!-- SCAFFOLD-SYNC-LEDGER -->
| version | decision | reason |
|---|---|---|
| v0.42.0 | partial | settled |
| v0.41.0 | applied | settled |
'@
  $composedSynced = Get-SyncedVersion $productionPreamble '0.30.0' @('v0.41.0','v0.42.0','v0.43.0')
  if ($composedSynced -ne '0.42.0') { $fails.Add('Get-SyncedVersion accepted a production-preamble decoy before the exact marker') }
  $composedState = Get-ScaffoldStaleState 'https://github.com/Asun28/project.git' 'Asun28/claude-devops-scaffold' @('v0.41.0','v0.42.0','v0.43.0') $composedSynced
  if ($composedState.Status -ne 'behind' -or $composedState.Latest -ne '0.43.0') { $fails.Add('production ledger/tag composition did not report the first undecided release as behind') }
  $duplicateMarker = $productionPreamble + "`n<!-- SCAFFOLD-SYNC-LEDGER -->"
  try { $null = Get-SyncedVersion $duplicateMarker '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion accepted duplicate exact markers') } catch { }

  $badDecision = $productionPreamble.Replace('| v0.41.0 | applied |', '| v0.41.0 | deferred |')
  try { $null = Get-SyncedVersion $badDecision '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion ignored a malformed decision') } catch { }
  $gap = $productionPreamble -replace '(?m)^\| v0\.41\.0 \| applied \| settled \|\r?\n?', ''
  try { $null = Get-SyncedVersion $gap '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion ignored a locally available release gap') } catch { }
  $duplicateRow = $productionPreamble + "`n| v0.42.0 | skipped | duplicate |"
  try { $null = Get-SyncedVersion $duplicateRow '0.30.0' @('v0.41.0','v0.42.0'); $fails.Add('Get-SyncedVersion ignored a duplicate release decision') } catch { }

  $fakeScanner = { param($line) if ($line -match 'TOKEN') { 'fake-secret' } }
  if (@(Get-ScaffoldIssueSecretHit 'TOKEN-in-title' 'safe body' $fakeScanner).Count -ne 1) { $fails.Add('report secret scan missed the public title') }
  if (@(Get-ScaffoldIssueSecretHit 'safe title' "safe`nTOKEN-in-body" $fakeScanner).Count -ne 1) { $fails.Add('report secret scan missed the public body') }
  if (Test-ScaffoldReportMaySend $false 0 '') { $fails.Add('report -Send allowed an unavailable scanner') }
  if (Test-ScaffoldReportMaySend $true 0 'malformed config') { $fails.Add('report -Send allowed a malformed configuration') }
  if (-not (Test-ScaffoldReportMaySend $true 0 '')) { $fails.Add('report -Send rejected trusted clean input') }
  if (Test-ScaffoldReportComplete 'title' 'summary' '' 'surface') { $fails.Add('report -Send accepted a missing reproduction') }
  if (-not (Test-ScaffoldReportComplete 'title' 'summary' 'repro' 'surface')) { $fails.Add('report field gate rejected complete input') }
  try {
    . (Join-Path $PSScriptRoot 'check-secrets.ps1') -AsLibrary
    $fixtureEscapes = @('allowlist secret','{{TOKEN}}','${TOKEN}','<TOKEN>','xxxxxxxx','your-token','example','changeme','placeholder','dummy','sample','todo','fixme','redacted','***','...')
    foreach ($escape in $fixtureEscapes) {
      $line = ('API_KEY=super' + 'secretvalue123 ' + $escape)
      if (Find-LineSecret $line) { $fails.Add("source scanner exemption fixture changed: $escape") }
      if (-not (Find-ScaffoldPublicSecret $line $ContentSecretPatterns)) { $fails.Add("public report scanner accepted source exemption: $escape") }
    }
  } catch { $fails.Add("real public report scanner fixture failed: $($_.Exception.Message)") }

  $realLedger = Get-Content $LedgerDoc -Raw
  $realV44 = @($realLedger -split "`r?`n" | Where-Object { $_ -match '^\| v0\.44\.0 \| partial \|' })
  if ((Get-SyncedVersion $realLedger $LocalVersion) -ne '0.44.0' -or $LocalVersion -cne '0.29.0' -or $realV44.Count -ne 1 -or $realV44[0] -notmatch '31 live-card' -or $realV44[0] -notmatch 'seven shared-core' -or $realV44[0] -notmatch 'handoff') { $fails.Add('real v0.44 partial ledger row or 0.29.0 provenance drifted') }
  $legacyConfig = Join-Path ([IO.Path]::GetTempPath()) "scaffold-legacy-config-$PID.ps1"
  try {
    Set-Content $legacyConfig "function Get-ScaffoldVersion { '0.29.0' }" -Encoding utf8
    $legacy = Resolve-ScaffoldConfiguration $legacyConfig 'Asun28/claude-devops-scaffold'
    if ($legacy.Error -or $legacy.Version -cne '0.29.0' -or $legacy.Upstream -cne 'Asun28/claude-devops-scaffold') { $fails.Add('legacy config without Get-ScaffoldUpstreamRepo did not retain canonical defaults') }
  } finally { Remove-Item $legacyConfig -Force -ErrorAction SilentlyContinue }

  $localOrigin = 'https://github.com/Asun28/project.git'; $upstream = 'Asun28/claude-devops-scaffold'
  if ((Get-ScaffoldStaleState "https://github.com/$upstream.git" $upstream @() '0.42.0').Status -ne 'self') { $fails.Add('scaffold-stale did not suppress the upstream repository itself') }
  foreach ($spoof in @("https://github.com/$upstream-fork.git", 'https://github.com/Asun28/fork-claude-devops-scaffold.git')) {
    if ((Get-ScaffoldStaleState $spoof $upstream @() '0.42.0').Status -eq 'self') { $fails.Add("scaffold-stale accepted spoofed self repository: $spoof") }
  }
  if (Test-UpstreamRemote 'scaffold' $upstream { 'https://github.com/other/wrong-repository.git' }) { $fails.Add('check accepted a remote for the wrong upstream repository') }
  if (-not (Test-UpstreamRemote 'scaffold' $upstream { 'git@github.com:Asun28/claude-devops-scaffold.git' })) { $fails.Add('check rejected the exact upstream repository SSH identity') }
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

# Triage loads helpers only; it must not run a scan.
if ($AsLibrary) { return }

try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 stdout + native non-zero by code (TD54); missing = fail-open
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Isolate upstream release tags from project tags.
$UpstreamRefNs = 'refs/scaffold-tags'
$LedgerDoc     = Join-Path $RepoRoot 'docs/SCAFFOLD-SYNC.md'

# Legacy/absent config degrades safely.
$configPath = Join-Path $PSScriptRoot '_config.ps1'
$resolvedConfig = Resolve-ScaffoldConfiguration $configPath 'Asun28/claude-devops-scaffold'
$UpstreamRepo = $resolvedConfig.Upstream; $LocalVersion = $resolvedConfig.Version; $ConfigLoadError = $resolvedConfig.Error

switch ($Verb) {
  'check'     { Invoke-Check -Remote $Remote -Fetch:$Fetch }
  'report'    { Invoke-Report -Title $Title -Summary $Summary -Repro $Repro -Surface $Surface -LessonId $LessonId -Send:$Send -OutFile $OutFile }
  'selfcheck' { Invoke-Selfcheck }
}
