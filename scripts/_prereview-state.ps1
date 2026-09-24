#requires -Version 7.4
[CmdletBinding()]
param([switch]$AsLibrary, [switch]$SelfCheck)
# Runnable self-check entry for scripts/selftest.ps1 gate 1h (the shared-core contract adopted from origin in
# the 2026-09 local/origin reconcile). It runs this file's own -SelfCheck in a child process and returns one
# finding when that run does not end on its pass sentinel, and nothing otherwise.
function Test-ScaffoldPrereviewStateExamples {
  $out = & pwsh -NoProfile -File $PSCommandPath -SelfCheck *>&1 | Out-String
  $code = $LASTEXITCODE
  if ($code -eq 0 -and $out.Contains('[PREREVIEW-STATE-SELFCHECK-PASS]')) { return @() }
  return @("$(Split-Path -Leaf $PSCommandPath) -SelfCheck did not pass (exit $code): $(($out -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 3) -join ' | ')")
}
$stateLibrary = $AsLibrary; $stateCheck = $SelfCheck
. (Join-Path $PSScriptRoot '_prereview-records.ps1') -AsLibrary
. (Join-Path $PSScriptRoot '_prereview-facts.ps1') -AsLibrary
# RECORDS remains the normalization authority, including consistency replay before persistence. No gate is derived.
function Test-PrereviewState {
  [OutputType([bool])]
  param($State)
  try {
    $json = ConvertTo-Json -InputObject $State -Depth 64 -Compress
    $schema = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'fixtures/prereview/state/state.schema.json'))
    if (-not (Test-Json -Json $json -Schema $schema -ErrorAction SilentlyContinue)) { return $false }
    $s = ConvertFrom-Json -InputObject $json -AsHashtable -NoEnumerate
    # Traverse data keys, never prose. Provider usage objects can have nested metadata.
    $pending = [Collections.Generic.Stack[object]]::new(); $pending.Push($s)
    while ($pending.Count) {
      $node = $pending.Pop()
      if ($node -is [Collections.IDictionary]) {
        foreach ($key in $node.Keys) {
          if (Test-Ordinal $key 'verdict') { return $false }
          if ((Test-Ordinal $key 'status') -and ((Test-Ordinal $node[$key] 'pass') -or (Test-Ordinal $node[$key] 'block'))) { return $false }
          $pending.Push($node[$key])
        }
      } elseif ($node -is [array]) { foreach ($value in $node) { $pending.Push($value) } }
    }
    $unitIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $workerIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $candidateIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($u in $s.units) {
      if (-not $unitIds.Add($u.unit_id)) { return $false }
      $identity = if ($null -eq $u.hunk_header) { '\A' + [regex]::Escape($u.file + '#file') + '\z' }
      else { '\A' + [regex]::Escape($u.file + '#' + $u.body_sha256.Substring(0,12) + '-') + '[1-9][0-9]*\z' }
      if (-not [regex]::IsMatch($u.unit_id, $identity, [Text.RegularExpressions.RegexOptions]::CultureInvariant)) { return $false }
    }
    foreach ($w in $s.workers) { if (-not $workerIds.Add($w.id)) { return $false } }
    foreach ($c in $s.candidates) { if (-not $candidateIds.Add($c.id)) { return $false } }
    $recordSchema = ConvertFrom-Json ([IO.File]::ReadAllText((Join-Path $PSScriptRoot '../specs/prereview-record.schema.json'))) -AsHashtable
    $replay = [Collections.Generic.List[object]]::new(); $localIds = New-OrdinalMap
    foreach ($c in $s.candidates) {
      $content = ConvertFrom-Json (ConvertTo-Json $c -Depth 64) -AsHashtable
      foreach ($key in 'id','fingerprint','root_group','related_to','provenance') { $content.Remove($key) }
      $content.local_id = $c.id
      foreach ($u in $c.unit_ids) { if (-not $unitIds.Contains($u)) { return $false } }
      foreach ($id in @($c.root_group) + @($c.related_to)) { if (-not $candidateIds.Contains($id)) { return $false } }
      foreach ($p in $c.provenance) {
        if (-not $workerIds.Contains($p.worker_id) -or -not (Test-Ordinal $p.snapshot_tree $s.snapshot_tree)) { return $false }
        $worker = @($s.workers | Where-Object { Test-Ordinal $_.id $p.worker_id })[0]
        if (-not (Test-Ordinal $worker.model $p.model_id) -or $worker.lens -ne $p.lens -or (Test-Ordinal $worker.status 'skipped')) { return $false }
        if ($p.schema_version -ne $recordSchema.properties.schema_version.const -or $p.schema_revision -ne $recordSchema.properties.schema_revision.const) { return $false }
        $content.local_id = $p.local_id
        foreach ($key in 'kind','severity_guess','anchor','line_start','line_end') { $content[$key] = $p[$key] }
        $raw = ConvertFrom-Json (ConvertTo-Json $content -Depth 64) -AsHashtable
        foreach ($key in 'worker_id','snapshot_tree','model_id','lens','schema_version','schema_revision') { $raw[$key] = $p[$key] }
        $replay.Add($raw)
        $localIds[(Get-PrereviewLocalKey $p.worker_id $c.id)] = $p.local_id
      }
    }
    foreach ($v in $s.coverage) {
      if (-not $unitIds.Contains($v.unit_id)) { return $false }
      foreach ($id in $v.candidate_ids) { if (-not $candidateIds.Contains($id)) { return $false } }
      if (Test-Ordinal $v.status 'missing') { continue }
      if (-not $workerIds.Contains($v.worker_id)) { return $false }
      if (Test-Ordinal (@($s.workers | Where-Object { Test-Ordinal $_.id $v.worker_id })[0].status) 'skipped') { return $false }
      $content = [ordered]@{ unit_id = $v.unit_id; categories_checked = $v.categories_checked; status = $v.status; candidate_local_ids = $v.candidate_ids; missing_context = $v.missing_context }
      $content.candidate_local_ids = @(foreach ($id in $v.candidate_ids) {
        $key = Get-PrereviewLocalKey $v.worker_id $id
        if (-not $localIds.ContainsKey($key)) { return $false }
        $localIds[$key]
      })
      $content.worker_id = $v.worker_id; $replay.Add($content)
    }
    # Reconstruct contributor records, then ask the existing core to verify its own derived fields.
    $start = if ($s.candidates.Count) { [long]$s.candidates[0].id.Substring(2) } else { 1 }
    $normalized = ConvertTo-PrereviewCandidates -Records $replay.ToArray() -Units $s.units -NextId $start
    if (-not $normalized.Ok -or -not (Test-PrereviewJsonEqual @($s.candidates) @($normalized.Candidates))) { return $false }
    $discoverers = @($s.workers | Where-Object { $_.required -and -not $_.lens })
    if ($discoverers.Count -ne 1) { return $false }
    $coverage = ConvertTo-PrereviewCoverage -Records $replay.ToArray() -Units $s.units -Candidates $normalized -DiscovererWorkerId $discoverers[0].id
    if (-not $coverage.Ok -or -not (Test-PrereviewJsonEqual @($s.coverage) @($coverage.Coverage))) { return $false }
    foreach ($d in $s.disputes) { if ($null -ne $d.candidate_id -and -not $candidateIds.Contains($d.candidate_id)) { return $false } }
    return $true
  } catch { return $false }
}

function Test-PrereviewJsonEqual($Left, $Right) {
  $a = [Text.Json.Nodes.JsonNode]::Parse((ConvertTo-Json -InputObject $Left -Depth 64 -Compress))
  $b = [Text.Json.Nodes.JsonNode]::Parse((ConvertTo-Json -InputObject $Right -Depth 64 -Compress))
  return [Text.Json.Nodes.JsonNode]::DeepEquals($a, $b)
}

function New-PrereviewState {
  param([string]$TaskId, [Collections.IDictionary]$Facts, [object[]]$Workers, [object[]]$Units, [object[]]$Candidates, [object[]]$Coverage, [AllowNull()][string]$StopReason)
  $state = [ordered]@{ state_version = 1; task_id = $TaskId }
  foreach ($key in 'snapshot_tree','head_sha','base_oid','base_mode','merge_base','policy_hash','rubric_sha') { $state[$key] = $Facts[$key] }
  $state.workers = @($Workers); $state.units = @($Units); $state.candidates = @($Candidates); $state.coverage = @($Coverage)
  $state.disputes = @(); $state.stop_reason = $(if ($PSBoundParameters.ContainsKey('StopReason')) { $StopReason } else { $null })
  if (-not (Test-PrereviewState $state)) { throw 'Invalid prereview state.' }
  return $state
}

function Test-PrereviewPathWithin([string]$Path, [string]$Root) {
  $comparison = if ($IsWindows) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
  $full = [IO.Path]::GetFullPath($Path)
  $base = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
  return [string]::Equals($full, $base, $comparison) -or $full.StartsWith($base + [IO.Path]::DirectorySeparatorChar, $comparison)
}

function Assert-PrereviewOutputPath([string]$Path, [string]$Plane, [string]$WorktreePath) {
  if (-not (Test-PrereviewPathWithin $Path $Plane) -or (Test-PrereviewPathWithin $Path $WorktreePath)) { throw 'Prereview output must remain in the common-dir plane and outside the reviewed worktree.' }
  # Inspect the leaf and every ancestor, including ancestors of the common dir.
  $probe = [IO.Path]::GetFullPath($Path)
  while ($probe) {
    try {
      $attributes = [IO.File]::GetAttributes($probe)
      if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Prereview output path contains a reparse point.' }
    } catch [IO.FileNotFoundException] { } catch [IO.DirectoryNotFoundException] { }
    $parent = [IO.Directory]::GetParent($probe)
    $probe = if ($null -eq $parent) { $null } else { $parent.FullName }
  }
}

function Resolve-PrereviewStatePlane {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$WorktreePath,
    [Parameter(Mandatory)][ValidatePattern('\AT[0-9]+-[A-Z0-9]+(-[A-Z0-9]+)*\z', Options='CultureInvariant')][string]$TaskId)
  $common = Get-PrereviewGitText $RepoRoot @('rev-parse','--git-common-dir')
  if ([string]::IsNullOrWhiteSpace($common) -or $common.Contains("`n") -or $common.Contains("`r")) { throw 'Git common-dir must be one non-empty path.' }
  $common = [IO.Path]::GetFullPath($common, [IO.Path]::GetFullPath($RepoRoot))
  $plane = Join-Path $common "scaffold-prereview/$TaskId"
  Assert-PrereviewOutputPath $plane $plane $WorktreePath
  return $plane
}

function Write-PrereviewAtomicFile([string]$Path, [string]$Text, [string]$Plane, [string]$WorktreePath) {
  Assert-PrereviewOutputPath $Path $Plane $WorktreePath
  $directory = [IO.Path]::GetDirectoryName($Path)
  [IO.Directory]::CreateDirectory($directory) | Out-Null
  $temporary = Join-Path $directory ([guid]::NewGuid().ToString('N') + '.tmp')
  try {
    $stream = [IO.File]::Open($temporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text); $stream.Write($bytes); $stream.Flush($true) } finally { $stream.Dispose() }
    Assert-PrereviewOutputPath $Path $Plane $WorktreePath
    # Publish the fully flushed file in one same-directory rename.
    [IO.File]::Move($temporary, $Path, $true)
  } finally { if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) } }
}

function Write-PrereviewState {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$WorktreePath, [Parameter(Mandatory)][string]$TaskId, [Parameter(Mandatory)]$State)
  if (-not (Test-PrereviewState $State) -or -not (Test-Ordinal $TaskId $State.task_id)) { throw 'Invalid state or task identity.' }
  $plane = Resolve-PrereviewStatePlane -RepoRoot $RepoRoot -WorktreePath $WorktreePath -TaskId $TaskId
  Write-PrereviewAtomicFile (Join-Path $plane 'state.json') (ConvertTo-Json $State -Depth 64) $plane $WorktreePath
}

function Add-PrereviewDispute {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$WorktreePath, [Parameter(Mandatory)][string]$TaskId, [Parameter(Mandatory)]$Dispute)
  $plane = Resolve-PrereviewStatePlane -RepoRoot $RepoRoot -WorktreePath $WorktreePath -TaskId $TaskId
  $path = Join-Path $plane 'state.json'; Assert-PrereviewOutputPath $path $plane $WorktreePath
  $state = ConvertFrom-Json ([IO.File]::ReadAllText($path)) -AsHashtable
  if (-not (Test-PrereviewState $state)) { throw 'Cannot append to an invalid state.' }
  $state.disputes = @($state.disputes) + @($Dispute)
  Write-PrereviewState -RepoRoot $RepoRoot -WorktreePath $WorktreePath -TaskId $TaskId -State $state
}

function Write-PrereviewPacket {
  param([Parameter(Mandatory)]$State)
  if (-not (Test-PrereviewState $State)) { throw 'Cannot render an invalid state.' }
  $lines = [Collections.Generic.List[string]]::new()
  $lines.Add('# Advisory findings'); $lines.Add('')
  foreach ($section in 'Workers','Candidates','Coverage','Missing units') {
    $lines.Add("## $section"); $lines.Add('')
    $entries = switch ($section) {
      'Workers' { $State.workers }
      'Candidates' { $State.candidates }
      'Coverage' { $State.coverage }
      'Missing units' { @($State.coverage | Where-Object { Test-Ordinal $_.status 'missing' } | ForEach-Object { $_.unit_id }) }
    }
    # Indented JSON preserves every field and encodes HTML; model text cannot close a Markdown fence.
    foreach ($entry in $entries) {
      foreach ($line in (ConvertTo-Json -InputObject $entry -Depth 64 -EscapeHandling EscapeHtml).Split("`n")) { $lines.Add('    ' + $line.TrimEnd([char]13)) }
      $lines.Add('')
    }
  }
  return $lines -join "`n"
}

function Write-PrereviewView {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$WorktreePath, [Parameter(Mandatory)][string]$TaskId,
    [Parameter(Mandatory)][ValidatePattern('\A(packet\.md|workers/[A-Za-z0-9][A-Za-z0-9_-]*-[1-9][0-9]*\.[A-Za-z0-9][A-Za-z0-9._-]*)\z', Options='CultureInvariant')][string]$RelativePath,
    [Parameter(Mandatory)][AllowEmptyString()][string]$Content)
  $plane = Resolve-PrereviewStatePlane -RepoRoot $RepoRoot -WorktreePath $WorktreePath -TaskId $TaskId
  Write-PrereviewAtomicFile (Join-Path $plane $RelativePath) $Content $plane $WorktreePath
}
if ($stateLibrary) { return }
if ($stateCheck) {
  Remove-Item Env:PRE_LIVE, Env:PRE_LENS_ENDPOINT -ErrorAction SilentlyContinue
  & (Join-Path $PSScriptRoot 'fixtures/prereview/state/selfcheck.ps1') -LibraryPath $PSCommandPath
  exit $LASTEXITCODE
}
throw 'Use -AsLibrary or -SelfCheck.'
