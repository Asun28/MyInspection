#requires -Version 7.4
param([Parameter(Mandatory)][string]$LibraryPath)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:checks = 0
function Check([string]$Name, [bool]$Ok) {
  if (-not $Ok) { throw "[STATE-CHECK-FAILED] $Name" }
  $script:checks++; Write-Host "  OK $Name"
}
function Clone($Value) { ConvertFrom-Json -InputObject (ConvertTo-Json -InputObject $Value -Depth 64 -Compress) -AsHashtable -NoEnumerate }
function Same($A, $B) { [string]::Equals([string]$A, [string]$B, [StringComparison]::Ordinal) }
function Reject([string]$Name, [scriptblock]$Action, [type]$ExceptionType) {
  $rejected = $false
  try { & $Action | Out-Null } catch { $rejected = $null -eq $ExceptionType -or $_.Exception -is $ExceptionType }
  Check $Name $rejected
}
Check 'offline environment' ($null -eq $env:PRE_LIVE -and $null -eq $env:PRE_LENS_ENDPOINT)
. $LibraryPath -AsLibrary
$repo = Split-Path (Split-Path $LibraryPath -Parent) -Parent
$facts = Get-Content -LiteralPath (Join-Path $repo 'scripts/fixtures/prereview/schema/records/valid/facts.pack.json') -Raw | ConvertFrom-Json -AsHashtable
$units = Get-Content -LiteralPath (Join-Path $repo 'scripts/fixtures/prereview/records/units.json') -Raw | ConvertFrom-Json -AsHashtable
$records = @(Get-Content -LiteralPath (Join-Path $repo 'scripts/fixtures/prereview/records/valid/batch.jsonl') | ForEach-Object { ConvertFrom-Json -InputObject $_ -AsHashtable })
$minted = ConvertTo-PrereviewCandidates -Records $records -Units $units
$covered = ConvertTo-PrereviewCoverage -Records $records -Units $units -Candidates $minted
Check 'fixture core results' ($minted.Ok -and $covered.Ok)
$workers = @(
  @{ id = 'discoverer'; model = 'claude-opus-5'; effort = 'high'; lens = $false; required = $true; status = 'complete'; skip_code = $null; duration_s = 1.25; exit_code = 0; usage = $null },
  @{ id = 'lens'; model = 'deepseek-v4-flash'; effort = 'high'; lens = $true; required = $false; status = 'complete'; skip_code = $null; duration_s = 0.5; exit_code = 0; usage = @{ input_tokens = 20; output_tokens = 30 } }
)
$valid = [ordered]@{ state_version = 1; task_id = 'T0-STATE-FIXTURE' }
foreach ($key in 'snapshot_tree','head_sha','base_oid','base_mode','merge_base','policy_hash','rubric_sha') { $valid[$key] = $facts[$key] }
$valid.workers = $workers; $valid.units = $units; $valid.candidates = $minted.Candidates; $valid.coverage = $covered.Coverage
$valid.disputes = @(); $valid.stop_reason = $null
Check 'valid state' (Test-PrereviewState $valid)
$schema = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'state.schema.json') -Raw
Check 'single-file schema accepts state' (Test-Json -Json (ConvertTo-Json $valid -Depth 64) -Schema $schema)
$empty = Clone $valid; $empty.units = @(); $empty.candidates = @(); $empty.coverage = @()
$empty.workers[0].status = 'incomplete'; $empty.workers[0].exit_code = 124
$empty.workers[1].status = 'skipped'; $empty.workers[1].skip_code = '[PRE-WORKER-MISSING]'; $empty.workers[1].exit_code = $null
Check 'empty incomplete run and skipped lens' (Test-PrereviewState $empty)
$edits = [ordered]@{
  'unknown root field' = { param($s) $s.extra = 1 }
  'task trailing newline' = { param($s) $s.task_id += "`n" }
  'head sha trailing newline' = { param($s) $s.head_sha += "`n" }
  'policy hash trailing newline' = { param($s) $s.policy_hash += "`n" }
  'missing disputes' = { param($s) $s.Remove('disputes') }
  '1b review_status' = { param($s) $s.review_status = 'incomplete' }
  'nested verdict' = { param($s) $s.workers[1].usage.verdict = 'block' }
  'nested pass status' = { param($s) $s.workers[1].usage.status = 'pass' }
  'worker pass status' = { param($s) $s.workers[0].status = 'pass' }
  'worker block status' = { param($s) $s.workers[0].status = 'block' }
  'unknown worker status' = { param($s) $s.workers[0].status = 'success' }
  'coverage pass status' = { param($s) $s.coverage[0].status = 'pass' }
  'coverage block status' = { param($s) $s.coverage[0].status = 'block' }
  'invalid candidate content' = { param($s) $s.candidates[0].expected = 42 }
  'unknown candidate field' = { param($s) $s.candidates[0].extra = 1 }
  'invalid contributor content' = { param($s) $s.candidates[0].provenance[0].anchor = 'bad' }
  'stale contributor revision' = { param($s) $s.candidates[0].provenance[0].schema_revision = 0 }
  'wrong contributor snapshot' = { param($s) $s.candidates[0].provenance[0].snapshot_tree = 'a' * 40 }
  'unknown contributor worker' = { param($s) $s.candidates[0].provenance[0].worker_id = 'absent' }
  'worker model mismatch' = { param($s) $s.workers[0].model = 'different' }
  'contributor model mismatch' = { param($s) $s.candidates[0].provenance[0].model_id = 'different' }
  'contributor lens mismatch' = { param($s) $s.candidates[0].provenance[0].lens = $true }
  'skipped worker with records' = { param($s) $s.workers[0].status = 'skipped' }
  'duplicate candidate identity' = { param($s) $s.candidates[1].id = $s.candidates[0].id }
  'unknown unit reference' = { param($s) $s.candidates[0].unit_ids = @('absent#file') }
  'dangling coverage candidate' = { param($s) $s.coverage[0].candidate_ids = @('C-999') }
  'coverage from another worker' = { param($s) $s.coverage[0].candidate_ids = @('C-4') }
  'missing rows omitted' = { param($s) $s.coverage = @($s.coverage | Where-Object { $_.status -cne 'missing' }) }
  'duplicate coverage pair' = { param($s) $s.coverage = @($s.coverage) + @($s.coverage[0]) }
  'forged fingerprint' = { param($s) $s.candidates[0].fingerprint = 'arbitrary' }
  'forged root group' = { param($s) $s.candidates[0].root_group = 'C-3' }
  'blocked without context' = { param($s) $s.coverage[2].missing_context = @() }
  'missing with worker' = { param($s) $s.coverage[6].worker_id = 'discoverer' }
  'unknown coverage field' = { param($s) $s.coverage[0].extra = 1 }
  'invalid unit hash' = { param($s) $s.units[0].body_sha256 = 'invalid' }
  'extra unit field' = { param($s) $s.units[0].body_hash = 'invalid' }
  'unit hash identity' = { param($s) $s.units[0].body_sha256 = 'a' * 64 }
  'unit path identity' = { param($s) $s.units[0].file = 'another.ps1' }
  'unit kind identity' = { param($s) $s.units[0].hunk_header = $null }
}
foreach ($entry in $edits.GetEnumerator()) {
  $bad = Clone $valid; & $entry.Value $bad | Out-Null
  Check $entry.Key (-not (Test-PrereviewState $bad))
}
$made = New-PrereviewState -TaskId $valid.task_id -Facts $facts -Workers $workers -Units $units -Candidates $minted.Candidates -Coverage $covered.Coverage
Check 'new run has empty disputes' ($made.Contains('disputes') -and $made.disputes.Count -eq 0 -and (Test-PrereviewState $made))
$made.candidates[0].actual = "A pass result can block processing.`n<script>bad</script>`n~~~~"
$packet = Write-PrereviewPacket -State $made
$rendered = @(foreach ($block in $packet.Split("`n`n")) {
  if ($block.StartsWith('    ', [StringComparison]::Ordinal)) { ConvertFrom-Json -InputObject $block -AsHashtable -NoEnumerate }
})
$firstCandidate = @($rendered | Where-Object { $_ -is [Collections.IDictionary] -and $_.Contains('id') -and (Same $_.id 'C-1') })
Check 'candidate prose survives rendering' ($firstCandidate.Count -eq 1 -and (Same $firstCandidate[0].actual $made.candidates[0].actual))
Check 'packet sections and missing units' ($packet.Contains('## Workers') -and $packet.Contains('## Candidates') -and $packet.Contains('## Coverage') -and $packet.Contains('## Missing units') -and $packet.Contains('docs/y.md#file'))
$renderedCoverage = @($rendered | Where-Object { $_ -is [Collections.IDictionary] -and $_.Contains('unit_id') })
Check 'packet coverage preserved' ([Text.Json.Nodes.JsonNode]::DeepEquals([Text.Json.Nodes.JsonNode]::Parse((ConvertTo-Json -InputObject @($made.coverage) -Depth 64)), [Text.Json.Nodes.JsonNode]::Parse((ConvertTo-Json -InputObject $renderedCoverage -Depth 64))))
foreach ($entry in @($made.workers) + @($made.candidates)) {
  $match = @($rendered | Where-Object { $_ -is [Collections.IDictionary] -and $_.Contains('id') -and (Same $_.id $entry.id) })
  Check "packet includes $($entry.id)" ($match.Count -eq 1 -and [Text.Json.Nodes.JsonNode]::DeepEquals([Text.Json.Nodes.JsonNode]::Parse((ConvertTo-Json $entry -Depth 64)), [Text.Json.Nodes.JsonNode]::Parse((ConvertTo-Json $match[0] -Depth 64))))
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('prereview-state-' + [guid]::NewGuid().ToString('N'))
$main = Join-Path $temp 'main'; $wt = Join-Path $temp 'reviewed'
$junction = $null; $breakpoint = $null
try {
  [IO.Directory]::CreateDirectory($main) | Out-Null
  Invoke-PrereviewGit $main @('-c','init.templateDir=','init','--initial-branch=master') | Out-Null
  Invoke-PrereviewGit $main @('-c','user.name=State Fixture','-c','user.email=state@example.invalid','-c','core.hooksPath=','commit','--allow-empty','-m','fixture') | Out-Null
  Invoke-PrereviewGit $main @('worktree','add','--detach',$wt,'HEAD') | Out-Null
  $argsWrite = @{ RepoRoot = $main; WorktreePath = $wt; TaskId = $valid.task_id }
  $before = Get-PrereviewGitText $wt @('status','--porcelain','--ignored')
  $plane = Resolve-PrereviewStatePlane @argsWrite
  Check 'common-dir plane' (Same ([IO.Path]::GetFullPath($plane)) ([IO.Path]::GetFullPath((Join-Path $main '.git/scaffold-prereview/T0-STATE-FIXTURE'))))
  Reject 'state task identity mismatch' { Write-PrereviewState -RepoRoot $main -WorktreePath $wt -TaskId 'T0-OTHER' -State $valid }
  Check 'invalid write creates no state' (-not (Test-Path -LiteralPath (Join-Path $main '.git/scaffold-prereview/T0-OTHER')))
  Write-PrereviewState @argsWrite -State $valid
  $statePath = Join-Path $plane 'state.json'
  $first = [IO.File]::ReadAllText($statePath)
  Check 'state roundtrip' (Same (ConvertTo-Json (ConvertFrom-Json $first -AsHashtable) -Depth 64 -Compress) (ConvertTo-Json $valid -Depth 64 -Compress))
  # Withhold the completed temp file exactly before publication, simulating interruption.
  $moveLine = @(Select-String -LiteralPath $LibraryPath -SimpleMatch '# Publish the fully flushed file')
  Check 'one atomic publication point' ($moveLine.Count -eq 1)
  $script:interruptedContent = $null
  $breakpoint = Set-PSBreakpoint -Script $LibraryPath -Line $moveLine[0].LineNumber -Action {
    $script:interruptedContent = [IO.File]::ReadAllText($temporary)
    [IO.File]::Move($temporary, $temporary + '.interrupted')
  }
  $changed = Clone $valid; $changed.stop_reason = 'interrupted'
  $crash = ''
  try { Write-PrereviewState @argsWrite -State $changed } catch { $crash = $_.Exception.Message }
  Remove-PSBreakpoint $breakpoint; $breakpoint = $null
  Check 'injected pre-rename crash' ($crash.Length -gt 0 -and $null -ne $script:interruptedContent -and (Same (ConvertFrom-Json $script:interruptedContent).stop_reason 'interrupted'))
  Check 'crash preserves previous bytes' (Same $first ([IO.File]::ReadAllText($statePath)))
  Check 'crash cleans temporary file' (@(Get-ChildItem -LiteralPath $plane -Filter '*.tmp').Count -eq 0)
  Write-PrereviewState @argsWrite -State $changed
  Check 'atomic replacement publishes new state' ((ConvertFrom-Json ([IO.File]::ReadAllText($statePath))).stop_reason -ceq 'interrupted')
  $dispute = @{ candidate_id = 'C-1'; r3_round = 1; r3_sha = 'b' * 40; reason_index = 0; reason_sha256 = 'c' * 64; relation = 'same'; by = 'fixture'; at = '2026-09-17T00:00:00Z' }
  Add-PrereviewDispute @argsWrite -Dispute $dispute
  $dispute.candidate_id = $null; $dispute.relation = 'new'; $dispute.reason_index = 1
  Add-PrereviewDispute @argsWrite -Dispute $dispute
  $loaded = ConvertFrom-Json ([IO.File]::ReadAllText($statePath)) -AsHashtable
  Check 'disputes append and retain state' ($loaded.disputes.Count -eq 2 -and (Same $loaded.disputes[0].candidate_id 'C-1') -and $null -eq $loaded.disputes[1].candidate_id -and (Same $loaded.stop_reason 'interrupted'))
  $dispute.candidate_id = 'C-999'
  Reject 'reject dangling dispute' { Add-PrereviewDispute @argsWrite -Dispute $dispute }
  $dispute.candidate_id = $null; $dispute.relation = 'same'
  Reject 'reject unlinked same relation' { Add-PrereviewDispute @argsWrite -Dispute $dispute }
  $dispute.candidate_id = 'C-1'; $dispute.relation = 'new'
  Reject 'reject linked new relation' { Add-PrereviewDispute @argsWrite -Dispute $dispute }
  Check 'rejected append leaves state' (Same ([IO.File]::ReadAllText($statePath)) (ConvertTo-Json $loaded -Depth 64))
  Write-PrereviewView @argsWrite -RelativePath 'packet.md' -Content $packet
  Write-PrereviewView @argsWrite -RelativePath 'workers/discoverer-1.stdout.txt' -Content 'worker output'
  Check 'view and worker log bytes' ((Same $packet ([IO.File]::ReadAllText((Join-Path $plane 'packet.md')))) -and (Same 'worker output' ([IO.File]::ReadAllText((Join-Path $plane 'workers/discoverer-1.stdout.txt')))))
  foreach ($path in '../escape','workers/../../escape','/absolute','state.json','workers/x','PACKET.md',"packet.md`n") { Reject "reject view path $($path | ConvertTo-Json -Compress)" { Write-PrereviewView @argsWrite -RelativePath $path -Content 'bad' } }
  Reject 'refuse plane inside reviewed tree' { Resolve-PrereviewStatePlane -RepoRoot $main -WorktreePath $main -TaskId $valid.task_id }
  Reject 'refuse unsafe task id' { Resolve-PrereviewStatePlane -RepoRoot $main -WorktreePath $wt -TaskId '../escape' }
  Reject 'refuse lowercase task id' { Resolve-PrereviewStatePlane -RepoRoot $main -WorktreePath $wt -TaskId 't0-state-fixture' }
  Reject 'refuse task id newline' { Resolve-PrereviewStatePlane -RepoRoot $main -WorktreePath $wt -TaskId "T0-STATE-FIXTURE`n" } ([System.Management.Automation.ParameterBindingException])
  Reject 'refuse non-repository' { Resolve-PrereviewStatePlane -RepoRoot $temp -WorktreePath $wt -TaskId $valid.task_id }
  $linked = Join-Path $plane 'workers/linked-1.txt'; $junction = $linked
  $target = Join-Path $wt '.review'; [IO.Directory]::CreateDirectory($target) | Out-Null
  $linkType = if ($IsWindows) { 'Junction' } else { 'SymbolicLink' }
  New-Item -ItemType $linkType -Path $linked -Target $target | Out-Null
  Reject 'refuse reparse leaf' { Write-PrereviewView @argsWrite -RelativePath 'workers/linked-1.txt' -Content 'bad' }
  [IO.Directory]::Delete($linked); $junction = $null
  $junction = Join-Path $plane 'workers'
  [IO.File]::Delete((Join-Path $junction 'discoverer-1.stdout.txt')); [IO.Directory]::Delete($junction)
  New-Item -ItemType $linkType -Path $junction -Target $target | Out-Null
  Reject 'refuse reparse ancestor' { Write-PrereviewView @argsWrite -RelativePath 'workers/discoverer-2.txt' -Content 'bad' }
  Check 'no redirected output' (@(Get-ChildItem -LiteralPath $target -Force).Count -eq 0)
  [IO.Directory]::Delete($junction); $junction = $null; [IO.Directory]::Delete($target)
  Check 'reviewed worktree unchanged' (Same $before (Get-PrereviewGitText $wt @('status','--porcelain','--ignored')))
  Invoke-PrereviewGit $main @('worktree','remove',$wt) | Out-Null
  Check 'state survives worktree removal' ([IO.File]::Exists($statePath))
} finally {
  if ($null -ne $breakpoint) { Remove-PSBreakpoint $breakpoint }
  if ($null -ne $junction -and [IO.Directory]::Exists($junction)) { [IO.Directory]::Delete($junction) }
  $root = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
  if (-not [IO.Path]::GetFullPath($temp).StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture cleanup' }
  if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
Write-Host "[PREREVIEW-STATE-SELFCHECK-PASS] $script:checks checks"
exit 0
