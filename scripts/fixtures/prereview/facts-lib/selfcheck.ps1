#requires -Version 7
[CmdletBinding()]
param([Parameter(Mandatory)][string]$LibraryPath)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checks = 0
if ($null -ne $env:PRE_LIVE -or $null -ne $env:PRE_LENS_ENDPOINT) { throw '[FACTS-ENV] SelfCheck must clear inherited live settings.' }

function Fail([string]$Message) {
  Write-Host "[PREREVIEW-FACTS-LIB-SELFCHECK-FAIL] $Message" -ForegroundColor Red
  exit 1
}

function Check([string]$Name, [bool]$Condition) {
  $script:checks++
  if (-not $Condition) { Fail $Name }
  Write-Host "  OK $Name"
}

function Same([string]$Left, [string]$Right) {
  return [string]::Equals($Left, $Right, [StringComparison]::Ordinal)
}

function BytesSame([byte[]]$Left, [byte[]]$Right) {
  return [Linq.Enumerable]::SequenceEqual($Left, $Right)
}

function Sha256([byte[]]$Bytes) {
  return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function WriteBytes([string]$Path, [byte[]]$Bytes) {
  [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
  [IO.File]::WriteAllBytes($Path, $Bytes)
}

function WriteUtf8([string]$Path, [string]$Text) {
  WriteBytes $Path ([Text.UTF8Encoding]::new($false).GetBytes($Text))
}

function Invoke-FixtureGit([string]$Root, [string[]]$Arguments) {
  $out = & git -C $Root @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed: $out" }
  return @($out)
}

function Test-FixtureGit([string]$Root, [string[]]$Arguments) {
  & git -C $Root @Arguments 1>$null 2>$null
  return $LASTEXITCODE -eq 0
}

function Get-FixtureGitLine([string]$Root, [string[]]$Arguments) {
  $lines = @(Invoke-FixtureGit $Root $Arguments)
  if ($lines.Count -ne 1) { throw "git $($Arguments -join ' ') returned $($lines.Count) lines, expected one" }
  return ([string]$lines[0]).Trim()
}

function Get-FixtureGitBytes([string]$Root, [string[]]$Arguments) {
  $psi = [Diagnostics.ProcessStartInfo]::new()
  $psi.FileName = 'git'; $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
  [void]$psi.ArgumentList.Add('-C'); [void]$psi.ArgumentList.Add($Root)
  foreach ($argument in $Arguments) { [void]$psi.ArgumentList.Add($argument) }
  $process = $null; $stream = [IO.MemoryStream]::new()
  try {
    $process = [Diagnostics.Process]::Start($psi)
    $copy = $process.StandardOutput.BaseStream.CopyToAsync($stream)
    $stderr = $process.StandardError.ReadToEndAsync()
    [Threading.Tasks.Task]::WaitAll(@($copy, $stderr)); $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "git bytes $($Arguments -join ' ') failed: $($stderr.Result)" }
    return ,([byte[]]$stream.ToArray())
  } finally {
    $stream.Dispose()
    if ($null -ne $process) { $process.Dispose() }
  }
}

function GitTreeEntryBytes([string]$Root, [string]$Tree, [string]$Path) {
  return Get-FixtureGitBytes $Root @('show', "${Tree}:$Path")
}

function FindUnit($Units, [string]$File) {
  return @($Units | Where-Object { Same ([string]$_.file) $File })
}

function RemoveFixture([AllowEmptyString()][string]$Path) {
  if ([string]::IsNullOrWhiteSpace($Path)) { return }
  $full = [IO.Path]::GetFullPath($Path)
  $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
  $prefix = $temp + [IO.Path]::DirectorySeparatorChar
  if ([string]::Equals($full, $temp, [StringComparison]::OrdinalIgnoreCase) -or -not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "unsafe fixture cleanup: $full" }
  if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force }
}

function New-OracleSnapshot([string]$Root) {
  $index = Join-Path ([IO.Path]::GetTempPath()) ('prereview-facts-index-' + [guid]::NewGuid().ToString('N'))
  $old = $env:GIT_INDEX_FILE
  try {
    $env:GIT_INDEX_FILE = $index
    Invoke-FixtureGit $Root @('read-tree','HEAD') | Out-Null
    Invoke-FixtureGit $Root @('add','-A') | Out-Null
    return Get-FixtureGitLine $Root @('write-tree')
  } finally {
    if ($null -eq $old) { Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue } else { $env:GIT_INDEX_FILE = $old }
    RemoveFixture $index
  }
}

function ClearInheritedGitEnvironment {
  $saved = @{}
  foreach ($entry in @(Get-ChildItem Env: | Where-Object { $_.Name.StartsWith('GIT_', [StringComparison]::OrdinalIgnoreCase) })) {
    $saved[$entry.Name] = $entry.Value; Remove-Item ("Env:$($entry.Name)") -ErrorAction Stop
  }
  return $saved
}

function RestoreInheritedGitEnvironment($Saved) {
  foreach ($name in @($Saved.Keys)) { Set-Item ("Env:$name") $Saved[$name] }
}

if (-not (Test-Path -LiteralPath $LibraryPath -PathType Leaf)) { Fail "[FACTS-EXPORTS] library path is absent: $LibraryPath" }
$startDir = (Get-Location).Path; $preLive = $env:PRE_LIVE; $preLens = $env:PRE_LENS_ENDPOINT
try { $loadOutput = . $LibraryPath -AsLibrary 2>&1 } catch { Fail "[FACTS-EXPORTS] -AsLibrary threw: $($_.Exception.Message)" }
if (@($loadOutput).Count -ne 0) { Fail '[FACTS-EXPORTS] -AsLibrary wrote output' }
$exports = @('Resolve-PrereviewWorktree','Resolve-PrereviewBase','Get-PrereviewSnapshotTree','Get-PrereviewPolicyHash','Get-PrereviewUnits','Get-PrereviewLiveAllowed','Get-PrereviewModelRoute','Get-PrereviewTempRoot')
$missing = @($exports | Where-Object { $null -eq (Get-Command $_ -CommandType Function -ErrorAction SilentlyContinue) })
if ($missing.Count) { Fail "[FACTS-EXPORTS] missing: $($missing -join ', ')" }
Check 'A1 AsLibrary keeps cwd' (Same $startDir (Get-Location).Path)
Check 'A1 AsLibrary does not set PRE_LIVE' (Same ([string]$preLive) ([string]$env:PRE_LIVE))
Check 'A1 AsLibrary does not set PRE_LENS_ENDPOINT' (Same ([string]$preLens) ([string]$env:PRE_LENS_ENDPOINT))

$gitEnvironment = ClearInheritedGitEnvironment
$root = Join-Path ([IO.Path]::GetTempPath()) ('prereview-facts-lib-' + [guid]::NewGuid().ToString('N'))
$worktree = ''
try {
  [IO.Directory]::CreateDirectory($root) | Out-Null
  Invoke-FixtureGit $root @('init','-q','-b','master') | Out-Null
  Invoke-FixtureGit $root @('config','user.email','fixture@example.invalid') | Out-Null
  Invoke-FixtureGit $root @('config','user.name','fixture') | Out-Null
  Invoke-FixtureGit $root @('config','core.autocrlf','false') | Out-Null

  $config = @'
$script:ScaffoldConfig = @{
  FrozenPaths = @('frozen/')
  PrereviewRiskyExtraPaths = @('scripts/', '.claude/')
  PrereviewModel = 'standard-model'
  PrereviewRiskyModel = 'risky-model'
  PrereviewMaxFileBytes = 30
}
'@
  $checklistBytes = [byte[]]@(0xEF,0xBB,0xBF) + [Text.UTF8Encoding]::new($false).GetBytes("清单`r`n")
  $schemaBytes = [Text.UTF8Encoding]::new($false).GetBytes("schema-é`r`n")
  WriteUtf8 (Join-Path $root 'scripts/_config.ps1') $config
  WriteBytes (Join-Path $root 'docs/PREREVIEW-CHECKLISTS.md') $checklistBytes
  WriteBytes (Join-Path $root 'specs/prereview-record.schema.json') $schemaBytes
  WriteUtf8 (Join-Path $root 'src/two.txt') "one`nold`nmid`nmid`ntwo`nold`n"
  WriteUtf8 (Join-Path $root 'src/nonl.txt') 'old'
  WriteBytes (Join-Path $root 'src/nonutf8.txt') ([byte[]](0x6F,0x6C,0x64,0x0A,0xE9,0x0A))
  WriteUtf8 (Join-Path $root 'delete.txt') ('d' * 32)
  WriteUtf8 (Join-Path $root 'rename-old.txt') 'rename-base'
  WriteUtf8 (Join-Path $root 'large.txt') ('a' * 32)
  WriteUtf8 (Join-Path $root 'tracked.ignore') 'tracked-base'
  WriteBytes (Join-Path $root 'binary.bin') ([byte[]](0,1,2,3,4))
  Invoke-FixtureGit $root @('add','-A') | Out-Null; Invoke-FixtureGit $root @('commit','-qm','base') | Out-Null
  $base = Get-FixtureGitLine $root @('rev-parse','HEAD')
  Invoke-FixtureGit $root @('update-ref','refs/remotes/origin/master',$base) | Out-Null
  WriteUtf8 (Join-Path $root 'base-local.txt') 'local'
  Invoke-FixtureGit $root @('add','-A') | Out-Null; Invoke-FixtureGit $root @('commit','-qm','local-base') | Out-Null
  $localOid = Get-FixtureGitLine $root @('rev-parse','HEAD')
  $worktree = Join-Path ([IO.Path]::GetTempPath()) ('facts-lib-wt-' + [guid]::NewGuid().ToString('N'))
  Invoke-FixtureGit $root @('worktree','add','-q','-b','T0-FACTS-WT',$worktree,'HEAD') | Out-Null

  Check 'A1 resolves registered worktree by task id' (Same (Resolve-PrereviewWorktree -TaskId 'T0-FACTS-WT' -RepoRoot $root) $worktree)
  $remote = Resolve-PrereviewBase -RepoRoot $root -Base master
  $local = Resolve-PrereviewBase -RepoRoot $root -Base master -Local
  Check 'A2 remote base pins origin ref OID and merge-base' ((Same $remote.base_mode 'remote') -and (Same $remote.base_ref 'refs/remotes/origin/master') -and (Same $remote.base_oid $base) -and (Same $remote.merge_base $base))
  Check 'A2 local base pins local ref OID and merge-base' ((Same $local.base_mode 'local') -and (Same $local.base_ref 'refs/heads/master') -and (Same $local.base_oid $localOid) -and (Same $local.merge_base $localOid))
  $baseTree = Get-FixtureGitLine $root @('rev-parse',"${base}^{tree}")
  $orphan = Get-FixtureGitLine $root @('commit-tree',$baseTree,'-m','orphan')
  Invoke-FixtureGit $root @('update-ref','refs/remotes/origin/unrelated',$orphan) | Out-Null
  $noMerge = $false
  try { Resolve-PrereviewBase -RepoRoot $root -Base unrelated | Out-Null } catch { $noMerge = $_.Exception.Message.Contains('[PRE-NO-MERGE-BASE]', [StringComparison]::Ordinal) }
  Check 'A2 resolved unrelated remote fails with PRE code' $noMerge

  $policyGolden = '9ae1924171f02bc59bc958aef3957a1cc705cfdd24376d4eae10b23d7d0a51ad'
  WriteUtf8 (Join-Path $root 'docs/PREREVIEW-CHECKLISTS.md') 'working-copy-change'
  WriteUtf8 (Join-Path $root 'specs/prereview-record.schema.json') 'working-copy-change'
  Check 'A4 policy hash preserves BOM CRLF Unicode and ignores working copy' (Same (Get-PrereviewPolicyHash -RepoRoot $root -MergeBase $base) $policyGolden)

  WriteUtf8 (Join-Path $root 'scripts/_config.ps1') ($config -replace 'risky-model','runtime-risky')
  Check 'A7 reads risky model from runtime config' (Same (Get-PrereviewModelRoute -AllowPaths @('frozen/x') -RepoRoot $root) 'runtime-risky')
  WriteUtf8 (Join-Path $root 'scripts/_config.ps1') ($config -replace 'frozen/', 'changed/')
  Check 'A7 reads changed frozen paths at runtime' ((Same (Get-PrereviewModelRoute -AllowPaths @('changed/x') -RepoRoot $root) 'risky-model') -and (Same (Get-PrereviewModelRoute -AllowPaths @('frozen/x') -RepoRoot $root) 'standard-model'))
  WriteUtf8 (Join-Path $root 'scripts/_config.ps1') $config

  WriteUtf8 (Join-Path $root '.gitignore') "*.env`ntracked.ignore`n"
  WriteUtf8 (Join-Path $root '.env') 'ignored'
  WriteUtf8 (Join-Path $root 'untracked.txt') 'included'
  WriteUtf8 (Join-Path $root 'tracked.ignore') 'dirty-tracked'
  WriteUtf8 (Join-Path $root 'src/two.txt') "one`nnew`nmid`nmid`ntwo`nnew`n"
  WriteUtf8 (Join-Path $root 'src/nonl.txt') 'new'
  WriteBytes (Join-Path $root 'src/nonutf8.txt') ([byte[]](0x6F,0x6C,0x64,0x0A,0xEA,0x0A))
  WriteBytes (Join-Path $root 'binary.bin') ([byte[]](9,8,7,6,5))
  WriteUtf8 (Join-Path $root 'large.txt') ('b' + ('a' * 31))
  Remove-Item -LiteralPath (Join-Path $root 'delete.txt')
  Move-Item -LiteralPath (Join-Path $root 'rename-old.txt') -Destination (Join-Path $root 'rename-new.txt')
  WriteUtf8 (Join-Path $root 'empty.txt') ''
  $quotedPath = 'docs/空 格.txt'; WriteUtf8 (Join-Path $root $quotedPath) 'quoted'
  $literalQuotePath = ''
  if (-not [OperatingSystem]::IsWindows()) { $literalQuotePath = 'docs/odd "q".txt'; WriteUtf8 (Join-Path $root $literalQuotePath) 'quote' }

  $realIndex = [IO.File]::ReadAllBytes((Join-Path $root '.git/index'))
  $oracle = New-OracleSnapshot $root
  $snapshot = Get-PrereviewSnapshotTree -WorktreePath $root
  $again = Get-PrereviewSnapshotTree -WorktreePath $root
  $afterIndex = [IO.File]::ReadAllBytes((Join-Path $root '.git/index'))
  Check 'A3 snapshot equals independent temporary-index oracle' (Same $snapshot $oracle)
  Check 'A3 snapshot repeatable and real index byte-identical' ((Same $snapshot $again) -and (BytesSame $realIndex $afterIndex))
  Check 'A3 snapshot contains dirty tracked content' (BytesSame (GitTreeEntryBytes $root $snapshot 'tracked.ignore') ([Text.UTF8Encoding]::new($false).GetBytes('dirty-tracked')))
  Check 'A3 snapshot includes untracked nonignored exact content' (BytesSame (GitTreeEntryBytes $root $snapshot 'untracked.txt') ([Text.UTF8Encoding]::new($false).GetBytes('included')))
  Check 'A3 snapshot excludes untracked ignored content' (-not (Test-FixtureGit $root @('cat-file','-e',"${snapshot}:.env")))

  $patch = Join-Path $root 'diff.patch'
  $rawPatch = Get-FixtureGitBytes $root @('diff','--binary','-M','--unified=0',$base,$snapshot)
  $crlfPatch = [Collections.Generic.List[byte]]::new()
  foreach ($byte in $rawPatch) { if ($byte -eq 10) { $crlfPatch.Add(13) }; $crlfPatch.Add($byte) }
  WriteBytes $patch $crlfPatch.ToArray()
  try { $units = @(Get-PrereviewUnits -DiffPath $patch -RepoRoot $root -SnapshotTree $snapshot -MergeBase $base) } catch { Fail "A5 non-UTF-8 hunk must not be rejected: $($_.Exception.Message)" }
  $unitsAgain = @(Get-PrereviewUnits -DiffPath $patch -RepoRoot $root -SnapshotTree $snapshot -MergeBase $base)
  Check 'A5 units are deterministic' (Same ($units | ConvertTo-Json -Depth 8 -Compress) ($unitsAgain | ConvertTo-Json -Depth 8 -Compress))

  $sameBody = Sha256 ([Text.UTF8Encoding]::new($false).GetBytes("-old`n+new`n"))
  $two = FindUnit $units 'src/two.txt'
  Check 'A5 equal bodies use distinct positive hunk ordinals' (($two.Count -eq 2) -and (Same $two[0].body_sha256 $sameBody) -and (Same $two[1].body_sha256 $sameBody) -and (Same $two[0].unit_id "src/two.txt#$($sameBody.Substring(0,12))-1") -and (Same $two[1].unit_id "src/two.txt#$($sameBody.Substring(0,12))-2"))
  $nonlRows = @(FindUnit $units 'src/nonl.txt')
  Check 'A5 no-newline hunk is present once' ($nonlRows.Count -eq 1)
  $nonl = $nonlRows[0]
  $nonlExpected = Sha256 ([Text.UTF8Encoding]::new($false).GetBytes("-old`n\ No newline at end of file`n+new`n\ No newline at end of file`n"))
  Check 'A5 no-newline marker remains in normalised hunk bytes' (Same $nonl.body_sha256 $nonlExpected)
  $nonUtf8 = @(FindUnit $units 'src/nonutf8.txt')
  $nonUtf8Expected = Sha256 ([byte[]](0x2D,0xE9,0x0A,0x2B,0xEA,0x0A))
  Check 'A5 non-UTF-8 hunk preserves raw bytes' (($nonUtf8.Count -eq 1) -and (Same $nonUtf8[0].body_sha256 $nonUtf8Expected))

  $binary = @(FindUnit $units 'binary.bin')[0]
  $large = @(FindUnit $units 'large.txt')[0]
  $deleted = @(FindUnit $units 'delete.txt')[0]
  $binaryOk = (Same $binary.unit_id 'binary.bin#file') -and $null -eq $binary.hunk_header -and (Same $binary.body_sha256 (Sha256 ([byte[]](9,8,7,6,5))))
  Check 'A5 binary uses file unit and raw blob hash' $binaryOk
  $largeOk = (Same $large.unit_id 'large.txt#file') -and $null -eq $large.hunk_header -and (Same $large.body_sha256 (Sha256 ([Text.UTF8Encoding]::new($false).GetBytes(('b' + ('a' * 31))))))
  Check 'A5 oversized text uses file unit and snapshot blob hash' $largeOk
  $deletedOk = (Same $deleted.unit_id 'delete.txt#file') -and $null -eq $deleted.hunk_header -and (Same $deleted.body_sha256 (Sha256 ([Text.UTF8Encoding]::new($false).GetBytes(('d' * 32)))))
  Check 'A5 oversized deletion uses file unit and base blob hash' $deletedOk
  $renamed = @(FindUnit $units 'rename-new.txt'); $empty = @(FindUnit $units 'empty.txt')
  $metadataOk = $renamed.Count -eq 1 -and (Same $renamed[0].unit_id 'rename-new.txt#file') -and $null -eq $renamed[0].hunk_header -and (Same $renamed[0].body_sha256 (Sha256 ([Text.Encoding]::UTF8.GetBytes('rename-base')))) -and $empty.Count -eq 1 -and (Same $empty[0].unit_id 'empty.txt#file') -and $null -eq $empty[0].hunk_header -and (Same $empty[0].body_sha256 (Sha256 ([byte[]]@())))
  Check 'A5 metadata-only rename and empty file have full blob hashes' $metadataOk
  Check 'A5 parses a C-quoted space and Unicode path' (@(FindUnit $units $quotedPath).Count -eq 1)
  if ($literalQuotePath) { Check 'A5 parses a Unix literal quote path' (@(FindUnit $units $literalQuotePath).Count -eq 1) }

  Check 'A7 risky route matches configured extra prefix' (Same (Get-PrereviewModelRoute -AllowPaths @('scripts/x.ps1') -RepoRoot $root) 'risky-model')
  Check 'A7 route does not prefix-overmatch' (Same (Get-PrereviewModelRoute -AllowPaths @('scriptsx/no') -RepoRoot $root) 'standard-model')
  $saveCi = $env:CI; $saveGha = $env:GITHUB_ACTIONS
  try {
    $env:CI = '1'; Remove-Item Env:GITHUB_ACTIONS -ErrorAction SilentlyContinue
    Check 'A6 CI disables live' (-not (Get-PrereviewLiveAllowed))
    Remove-Item Env:CI -ErrorAction SilentlyContinue; $env:GITHUB_ACTIONS = '1'
    Check 'A6 GITHUB_ACTIONS disables live' (-not (Get-PrereviewLiveAllowed))
    Remove-Item Env:GITHUB_ACTIONS -ErrorAction SilentlyContinue
    Check 'A6 clear CI flags enables live' (Get-PrereviewLiveAllowed)
  } finally {
    if ($null -eq $saveCi) { Remove-Item Env:CI -ErrorAction SilentlyContinue } else { $env:CI = $saveCi }
    if ($null -eq $saveGha) { Remove-Item Env:GITHUB_ACTIONS -ErrorAction SilentlyContinue } else { $env:GITHUB_ACTIONS = $saveGha }
  }
  Check 'A7 temp root is the platform temp path' (Same (Get-PrereviewTempRoot) ([IO.Path]::GetTempPath()))
  Write-Host "[PREREVIEW-FACTS-LIB-SELFCHECK-PASS] checks=$checks"
} finally {
  if (-not [string]::IsNullOrWhiteSpace($worktree) -and (Test-Path -LiteralPath $worktree)) { & git -C $root worktree remove --force $worktree 2>$null }
  RemoveFixture $worktree; RemoveFixture $root; RestoreInheritedGitEnvironment $gitEnvironment
}
