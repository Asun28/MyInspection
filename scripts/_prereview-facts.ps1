#requires -Version 7.4
# PR review v2 shared facts. Import defines functions only; self-check uses a disposable repository.
[CmdletBinding()]
param([switch]$AsLibrary, [switch]$SelfCheck)

function Invoke-PrereviewGit {
  param([string]$RepoRoot, [string[]]$Arguments, [hashtable]$Environment = @{}, [switch]$AllowFailure, [switch]$Hash)
  $psi = [Diagnostics.ProcessStartInfo]::new('git')
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  foreach ($key in @($psi.Environment.Keys)) {
    if ($key.StartsWith('GIT_', [StringComparison]::OrdinalIgnoreCase)) { [void]$psi.Environment.Remove($key) }
  }
  $psi.Environment['GIT_OPTIONAL_LOCKS'] = '0'
  foreach ($key in $Environment.Keys) { $psi.Environment[$key] = $Environment[$key] }
  foreach ($arg in @('--no-pager', '-C', $RepoRoot, '-c', 'core.fsmonitor=false') + $Arguments) { $psi.ArgumentList.Add($arg) }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $psi
  $buffer = [IO.MemoryStream]::new()
  $started = $false
  try {
    $started = $process.Start()
    $errorRead = $process.StandardError.ReadToEndAsync()
    $digest = $null
    if ($Hash) {
      $hasher = [Security.Cryptography.SHA256]::Create()
      try { $digest = [Convert]::ToHexString($hasher.ComputeHash($process.StandardOutput.BaseStream)).ToLowerInvariant() }
      finally { $hasher.Dispose() }
    } else { $process.StandardOutput.BaseStream.CopyTo($buffer) }
    $process.WaitForExit()
    $errorText = $errorRead.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0 -and -not $AllowFailure) { throw "git $($Arguments[0]) failed ($($process.ExitCode)): $errorText" }
    return @{ Code = $process.ExitCode; Bytes = $buffer.ToArray(); Hash = $digest }
  } finally {
    if ($started -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
    $process.Dispose()
    $buffer.Dispose()
  }
}

function Get-PrereviewGitText {
  param([string]$RepoRoot, [string[]]$Arguments, [hashtable]$Environment = @{})
  $result = Invoke-PrereviewGit -RepoRoot $RepoRoot -Arguments $Arguments -Environment $Environment
  return [Text.UTF8Encoding]::new($false, $true).GetString($result.Bytes).TrimEnd([char[]]"`r`n")
}

function Get-PrereviewConfig {
  param([string]$RepoRoot)
  $ErrorActionPreference = 'Stop'
  $config = & { . (Join-Path $RepoRoot 'scripts/_config.ps1'); $ScaffoldConfig }
  if ($config -isnot [Collections.IDictionary]) { throw 'Prereview configuration is not a dictionary.' }
  return $config
}

function Resolve-PrereviewWorktree {
  param([Parameter(Mandatory)][ValidatePattern('^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$')][string]$TaskId,
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot))
  $records = Get-PrereviewGitText $RepoRoot @('worktree', 'list', '--porcelain', '-z')
  $path = $null
  foreach ($field in $records.Split([char]0)) {
    if ($field.StartsWith('worktree ', [StringComparison]::Ordinal)) { $path = $field.Substring(9) }
    if ([string]::Equals($field, "branch refs/heads/$TaskId", [StringComparison]::Ordinal)) {
      if (-not $path -or -not [IO.Directory]::Exists($path)) { throw "Worktree missing for $TaskId" }
      return [IO.Path]::GetFullPath($path)
    }
  }
  throw "No registered worktree for $TaskId"
}

function Resolve-PrereviewBase {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$Base, [switch]$Local)
  if ($Base.StartsWith('refs/', [StringComparison]::Ordinal) -or $Base.StartsWith('origin/', [StringComparison]::Ordinal)) { throw 'Base must be an unqualified branch name.' }
  # The shared resolver invokes git itself. Isolate its inherited routing, then restore the caller's environment.
  $saved = @(Get-ChildItem Env:GIT_*)
  try {
    foreach ($entry in $saved) { [Environment]::SetEnvironmentVariable($entry.Name, $null, 'Process') }
    . (Join-Path $PSScriptRoot '_gitbase.ps1')
    $ref = Resolve-ScaffoldBaseRef -GitDir $RepoRoot -BaseName $Base -PreferLocal:$Local
  } finally { foreach ($entry in $saved) { [Environment]::SetEnvironmentVariable($entry.Name, $entry.Value, 'Process') } }
  $expected = if ($Local) { "refs/heads/$Base" } else { "refs/remotes/origin/$Base" }
  if (-not [string]::Equals($ref, $expected, [StringComparison]::Ordinal)) { throw '[PRE-NO-MERGE-BASE] requested base is absent' }
  $oid = Get-PrereviewGitText $RepoRoot @('rev-parse', '--verify', "$ref^{commit}")
  $head = Get-PrereviewGitText $RepoRoot @('rev-parse', '--verify', 'HEAD^{commit}')
  $result = Invoke-PrereviewGit $RepoRoot @('merge-base', $oid, $head) -AllowFailure
  if ($result.Code -ne 0) { throw '[PRE-NO-MERGE-BASE] base and HEAD have no usable merge-base' }
  $mergeBase = [Text.Encoding]::UTF8.GetString($result.Bytes).Trim()
  return [ordered]@{ base_ref = $ref; base_oid = $oid; base_mode = $(if ($Local) { 'local' } else { 'remote' }); merge_base = $mergeBase }
}

function Get-PrereviewTempRoot { return [IO.Path]::GetTempPath() }

function Get-PrereviewSnapshotTree {
  param([Parameter(Mandatory)][string]$WorktreePath)
  $tempRoot = [IO.Path]::GetFullPath((Get-PrereviewTempRoot))
  $scratch = Join-Path $tempRoot ('prereview-index-' + [Guid]::NewGuid().ToString('N'))
  [void][IO.Directory]::CreateDirectory($scratch)
  try {
    $envIndex = @{ GIT_INDEX_FILE = (Join-Path $scratch 'index') }
    [void](Invoke-PrereviewGit $WorktreePath @('read-tree', 'HEAD') -Environment $envIndex)
    [void](Invoke-PrereviewGit $WorktreePath @('add', '-A') -Environment $envIndex)
    return Get-PrereviewGitText $WorktreePath @('write-tree') -Environment $envIndex
  } finally {
    $full = [IO.Path]::GetFullPath($scratch)
    $prefix = $tempRoot.TrimEnd([char[]]'\/') + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe snapshot cleanup path.' }
    if ([IO.Directory]::Exists($full)) { Remove-Item -LiteralPath $full -Recurse -Force -ErrorAction Stop }
  }
}

function Get-PrereviewPolicyHash {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$MergeBase)
  $checklist = Invoke-PrereviewGit $RepoRoot @('show', '--no-ext-diff', '--no-textconv', "${MergeBase}:docs/PREREVIEW-CHECKLISTS.md")
  $schema = Invoke-PrereviewGit $RepoRoot @('show', '--no-ext-diff', '--no-textconv', "${MergeBase}:specs/prereview-record.schema.json")
  return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([byte[]]($checklist.Bytes + $schema.Bytes))).ToLowerInvariant()
}

function Get-PrereviewLiveAllowed {
  return $null -eq [Environment]::GetEnvironmentVariable('CI') -and $null -eq [Environment]::GetEnvironmentVariable('GITHUB_ACTIONS')
}

function Get-PrereviewModelRoute {
  param([string[]]$AllowPaths, [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot))
  $config = Get-PrereviewConfig $RepoRoot
  foreach ($path in $AllowPaths) {
    $normal = $path.Replace('\', '/')
    foreach ($pattern in $config.FrozenPaths) {
      if ([regex]::IsMatch($normal, $pattern, [Text.RegularExpressions.RegexOptions]::CultureInvariant)) { return $config.PrereviewRiskyModel }
    }
    foreach ($prefix in $config.PrereviewRiskyExtraPaths) {
      if ($normal.StartsWith($prefix, [StringComparison]::Ordinal)) { return $config.PrereviewRiskyModel }
    }
  }
  return $config.PrereviewModel
}

function Read-PrereviewDiffPath {
  param([string]$Text)
  if (-not $Text.StartsWith('"', [StringComparison]::Ordinal)) { return $Text.TrimEnd([char]9) }
  $match = [regex]::Match($Text, '^"((?:[^"\\]|\\.)*)"\t?$')
  if (-not $match.Success) { throw 'Malformed quoted Git path.' }
  $inputBytes = [Text.Encoding]::UTF8.GetBytes($match.Groups[1].Value)
  $decoded = [Collections.Generic.List[byte]]::new()
  $escapes = @{ 97=7; 98=8; 116=9; 110=10; 118=11; 102=12; 114=13; 34=34; 92=92 }
  for ($i = 0; $i -lt $inputBytes.Length; $i++) {
    $value = [int]$inputBytes[$i]
    if ($value -eq 92) {
      $i++
      if ($i -ge $inputBytes.Length) { throw 'Incomplete Git path escape.' }
      $value = [int]$inputBytes[$i]
      if ($value -ge 48 -and $value -le 55) {
        $octal = $value - 48
        for ($n = 1; $n -lt 3 -and $i + 1 -lt $inputBytes.Length -and $inputBytes[$i+1] -ge 48 -and $inputBytes[$i+1] -le 55; $n++) { $i++; $octal = $octal * 8 + $inputBytes[$i] - 48 }
        if ($octal -gt 255) { throw 'Git path octal byte exceeds 255.' }
        $value = $octal
      } elseif ($escapes.ContainsKey($value)) { $value = $escapes[$value] }
      else { throw 'Unknown Git path escape.' }
    }
    $decoded.Add([byte]$value)
  }
  return [Text.UTF8Encoding]::new($false, $true).GetString($decoded.ToArray())
}

function Get-PrereviewSectionPath {
  param([string]$Metadata)
  $oldPath = $null; $newPath = $null
  foreach ($line in $Metadata.Split("`n")) {
    if ($line.StartsWith('--- ', [StringComparison]::Ordinal)) { $oldPath = Read-PrereviewDiffPath $line.Substring(4) }
    if ($line.StartsWith('+++ ', [StringComparison]::Ordinal)) { $newPath = Read-PrereviewDiffPath $line.Substring(4) }
    foreach ($prefix in @('rename to ', 'copy to ')) {
      if ($line.StartsWith($prefix, [StringComparison]::Ordinal)) { return Read-PrereviewDiffPath $line.Substring($prefix.Length) }
    }
  }
  if ($newPath -and -not [string]::Equals($newPath, '/dev/null', [StringComparison]::Ordinal)) {
    if (-not $newPath.StartsWith('b/', [StringComparison]::Ordinal)) { throw 'Expected b/ diff path.' }
    return $newPath.Substring(2)
  }
  if ($oldPath) {
    if (-not $oldPath.StartsWith('a/', [StringComparison]::Ordinal)) { throw 'Expected a/ deleted path.' }
    return $oldPath.Substring(2)
  }
  $header = $Metadata.Split("`n")[0]
  $match = [regex]::Match($header, '^diff --git a/(.*) b/\1$')
  if ($match.Success) { return $match.Groups[1].Value }
  $match = [regex]::Match($header, '^diff --git ("(?:[^"\\]|\\.)*") ("(?:[^"\\]|\\.)*")$')
  if ($match.Success) {
    $path = Read-PrereviewDiffPath $match.Groups[2].Value
    if ($path.StartsWith('b/', [StringComparison]::Ordinal)) { return $path.Substring(2) }
  }
  throw 'Cannot resolve diff file path.'
}

function Get-PrereviewUnits {
  param([Parameter(Mandatory)][string]$DiffPath, [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SnapshotTree,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$MergeBase)
  $limit = (Get-PrereviewConfig $RepoRoot).PrereviewMaxFileBytes
  if (($limit -isnot [int] -and $limit -isnot [long]) -or $limit -lt 1) { throw 'PrereviewMaxFileBytes must be a positive integer.' }
  # Latin1 maps each byte to one character: ASCII diff syntax stays parseable without recoding hunk bodies.
  $patch = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes($DiffPath)).Replace("`r`n", "`n")
  if ($patch.Length -eq 0) { return }
  $sections = [regex]::Matches($patch, '(?m)^diff --git ')
  if ($sections.Count -eq 0 -or $sections[0].Index -ne 0) { throw 'Expected a Git unified diff.' }
  $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  for ($s = 0; $s -lt $sections.Count; $s++) {
    $end = if ($s + 1 -lt $sections.Count) { $sections[$s+1].Index } else { $patch.Length }
    $section = $patch.Substring($sections[$s].Index, $end - $sections[$s].Index)
    $hunks = [regex]::Matches($section, '(?m)^@@ -\d+(?:,\d+)? \+\d+(?:,\d+)? @@[^\n]*\n')
    $metadata = if ($hunks.Count) { $section.Substring(0, $hunks[0].Index) } else { $section }
    $path = Get-PrereviewSectionPath ([Text.UTF8Encoding]::new($false, $true).GetString([Text.Encoding]::Latin1.GetBytes($metadata)))
    if (-not $seen.Add($path)) { throw "Repeated diff file: $path" }
    $deleted = [regex]::IsMatch($metadata, '(?m)^deleted file mode ') -or [regex]::IsMatch($metadata, '(?m)^\+\+\+ /dev/null$')
    $blob = if ($deleted) { "${MergeBase}:$path" } else { "${SnapshotTree}:$path" }
    if (-not [string]::Equals((Get-PrereviewGitText $RepoRoot @('cat-file', '-t', $blob)), 'blob', [StringComparison]::Ordinal)) { throw "Not a blob: $path" }
    $size = [long](Get-PrereviewGitText $RepoRoot @('cat-file', '-s', $blob))
    if ($hunks.Count -eq 0 -or $size -gt $limit -or [regex]::IsMatch($metadata, '(?m)^(Binary files |GIT binary patch)')) {
      $hash = (Invoke-PrereviewGit $RepoRoot @('cat-file', 'blob', $blob) -Hash).Hash
      [ordered]@{ unit_id = "$path#file"; file = $path; hunk_header = $null; body_sha256 = $hash }
      continue
    }
    for ($h = 0; $h -lt $hunks.Count; $h++) {
      $bodyStart = $hunks[$h].Index + $hunks[$h].Length
      $bodyEnd = if ($h + 1 -lt $hunks.Count) { $hunks[$h+1].Index } else { $section.Length }
      $body = $section.Substring($bodyStart, $bodyEnd - $bodyStart)
      $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Latin1.GetBytes($body))).ToLowerInvariant()
      $header = [Text.Encoding]::UTF8.GetString([Text.Encoding]::Latin1.GetBytes($hunks[$h].Value.TrimEnd([char]10)))
      [ordered]@{ unit_id = "$path#$($hash.Substring(0,12))-$($h+1)"; file = $path; hunk_header = $header; body_sha256 = $hash }
    }
  }
}

if ($AsLibrary) { return }
if ($SelfCheck) {
  Remove-Item Env:PRE_LIVE, Env:PRE_LENS_ENDPOINT -ErrorAction SilentlyContinue
  & (Join-Path $PSScriptRoot 'fixtures/prereview/facts-lib/selfcheck.ps1') -LibraryPath $PSCommandPath
  exit $LASTEXITCODE
}
throw 'Use -AsLibrary or -SelfCheck.'
