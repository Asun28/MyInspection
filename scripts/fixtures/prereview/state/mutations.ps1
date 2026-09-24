#requires -Version 7.4
# R4 runs against disposable copies. Parse failures, missing anchors and unrelated failures are not kills.
param([string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if (-not $OutputDirectory) { $OutputDirectory = Join-Path ([IO.Path]::GetTempPath()) ('prereview-state-evidence-' + [guid]::NewGuid().ToString('N')) }
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$library = 'scripts/_prereview-state.ps1'; $schema = 'scripts/fixtures/prereview/state/state.schema.json'
$inputs = @($library, $schema, 'scripts/fixtures/prereview/state/selfcheck.ps1')
$hashes = [ordered]@{}
foreach ($p in $inputs) { $hashes[$p] = (Get-FileHash -LiteralPath (Join-Path $repo $p) -Algorithm SHA256).Hash }
# Each row names one concrete break, its exact source edit and the required behavioral failure.
$mutants = @(
  @('open-root',$schema,"`n  `"type`": `"object`", `"additionalProperties`": false,","`n  `"type`": `"object`", `"additionalProperties`": true,",'unknown root field'),
  @('unknown-worker-status',$schema,'"status": { "enum": ["complete", "incomplete", "skipped"] }','"status": { "type": "string" }','unknown worker status'),
  @('oid-end',$schema,'^[0-9a-f]{40}(?![\\s\\S])','^[0-9a-f]{40}$','head sha trailing newline'),
  @('digest-end',$schema,'^[0-9a-f]{64}(?![\\s\\S])','^[0-9a-f]{64}$','policy hash trailing newline'),
  @('unlinked-same',$schema,'      "else": { "properties": { "candidate_id": { "$ref": "#/$defs/cid" } } },','','reject unlinked same relation'),
  @('verdict-key',$library,"if (Test-Ordinal `$key 'verdict') { return `$false }",'','nested verdict'),
  @('nested-status',$library,"if ((Test-Ordinal `$key 'status') -and ((Test-Ordinal `$node[`$key] 'pass') -or (Test-Ordinal `$node[`$key] 'block'))) { return `$false }",'','nested pass status'),
  @('candidate-replay',$library,' -or -not (Test-PrereviewJsonEqual @($s.candidates) @($normalized.Candidates))','','forged fingerprint'),
  @('coverage-replay',$library,' -or -not (Test-PrereviewJsonEqual @($s.coverage) @($coverage.Coverage))','','missing rows omitted'),
  @('revision-binding',$library,'if ($p.schema_version -ne $recordSchema.properties.schema_version.const -or $p.schema_revision -ne $recordSchema.properties.schema_revision.const) { return $false }','','stale contributor revision'),
  @('snapshot-binding',$library,' -or -not (Test-Ordinal $p.snapshot_tree $s.snapshot_tree)','','wrong contributor snapshot'),
  @('worker-binding',$library,"if (-not (Test-Ordinal `$worker.model `$p.model_id) -or `$worker.lens -ne `$p.lens -or (Test-Ordinal `$worker.status 'skipped')) { return `$false }",'','worker model mismatch'),
  @('unit-binding',$library,'if (-not [regex]::IsMatch($u.unit_id, $identity, [Text.RegularExpressions.RegexOptions]::CultureInvariant)) { return $false }','','unit hash identity'),
  @('run-disputes',$library,'$state.disputes = @(); ','','Invalid prereview state.'),
  @('direct-write',$library,'[IO.File]::Move($temporary, $Path, $true)','[IO.File]::WriteAllText($Path, $Text)','injected pre-rename crash'),
  @('premature-write',$library,'$stream.Write($bytes);','$stream.Write($bytes); [IO.File]::WriteAllBytes($Path, $bytes);','crash preserves previous bytes'),
  @('worktree-refusal',$library,' -or (Test-PrereviewPathWithin $Path $WorktreePath)','','refuse plane inside reviewed tree'),
  @('reparse-refusal',$library,'if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)','if ($false)','refuse reparse ancestor'),
  @('candidate-render',$library,"'Candidates' { `$State.candidates }","'Candidates' { @() }",'candidate prose survives rendering'),
  @('coverage-render',$library,"'Coverage' { `$State.coverage }","'Coverage' { @() }",'packet coverage preserved'),
  @('worker-render',$library,"'Workers' { `$State.workers }","'Workers' { @() }",'packet includes discoverer'),
  @('dispute-overwrite',$library,'$state.disputes = @($state.disputes) + @($Dispute)','$state.disputes = @($Dispute)','disputes append and retain state'),
  @('task-binding',$library,' -or -not (Test-Ordinal $TaskId $State.task_id)','','state task identity mismatch'),
  @('task-case',$library,"'\AT[0-9]+-[A-Z0-9]+(-[A-Z0-9]+)*\z', Options='CultureInvariant'","'\AT[0-9]+-[A-Z0-9]+(-[A-Z0-9]+)*\z', Options='IgnoreCase, CultureInvariant'",'refuse lowercase task id'),
  @('task-end',$library,"'\AT[0-9]+-[A-Z0-9]+(-[A-Z0-9]+)*\z'","'\AT[0-9]+-[A-Z0-9]+(-[A-Z0-9]+)*$'",'refuse task id newline')
)
foreach ($mutant in $mutants) {
  $original = [IO.File]::ReadAllText((Join-Path $repo $mutant[1]))
  if ([regex]::Matches($original, [regex]::Escape($mutant[2])).Count -ne 1) { throw "INVALID ANCHOR: $($mutant[0])" }
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('prereview-state-mutations-' + [guid]::NewGuid().ToString('N'))
$results = [Collections.Generic.List[object]]::new()
try {
  foreach ($file in @($library,'scripts/_prereview-records.ps1','scripts/_prereview-facts.ps1','specs/prereview-record.schema.json')) {
    $target = Join-Path $temp $file; [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $file) -Destination $target
  }
  foreach ($name in 'state','records','schema') { Copy-Item -LiteralPath (Join-Path $repo "scripts/fixtures/prereview/$name") -Destination (Join-Path $temp "scripts/fixtures/prereview/$name") -Recurse }
  $cases = [Collections.Generic.List[object]]::new(); $cases.Add(@('control','','','',''))
  foreach ($mutant in $mutants) { $cases.Add($mutant) }
  foreach ($case in $cases) {
    foreach ($p in $inputs) { Copy-Item -LiteralPath (Join-Path $repo $p) -Destination (Join-Path $temp $p) -Force }
    if ($case[1]) {
      $target = Join-Path $temp $case[1]; $original = [IO.File]::ReadAllText($target)
      if ([regex]::Matches($original, [regex]::Escape($case[2])).Count -ne 1) { throw "INVALID ANCHOR: $($case[0])" }
      [IO.File]::WriteAllText($target, $original.Replace($case[2],$case[3]), [Text.UTF8Encoding]::new($false))
    }
    $parseErrors = $null; $parseTokens = $null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $temp $library), [ref]$parseTokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw "INVALID POWERSHELL: $($case[0])" }
    [void](ConvertFrom-Json ([IO.File]::ReadAllText((Join-Path $temp $schema))))
    $log = (& pwsh -NoProfile -File (Join-Path $temp $library) -SelfCheck *>&1 | Out-String); $code = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $OutputDirectory ($case[0] + '.log')), $log)
    if ($case[0] -eq 'control') {
      if ($code -ne 0 -or -not $log.Contains('[PREREVIEW-STATE-SELFCHECK-PASS]')) { throw 'CONTROL FAILED' }
    } else {
      $expected = if ($case[0] -eq 'run-disputes') { $case[4] } else { '[STATE-CHECK-FAILED] ' + $case[4] }
      if ($code -eq 0 -or -not $log.Contains($expected)) { throw "SURVIVED OR UNRELATED FAILURE: $($case[0]), exit=$code; inspect its log" }
      $results.Add([ordered]@{ mutant = $case[0]; exit_code = $code; assertion = $case[4] })
    }
    Write-Host "[STATE-MUTATION] $($case[0]) exit=$code"
  }
  foreach ($p in $inputs) { if ($hashes[$p] -cne (Get-FileHash -LiteralPath (Join-Path $repo $p) -Algorithm SHA256).Hash) { throw "SOURCE CHANGED: $p" } }
  [ordered]@{ hashes = $hashes; control_exit = 0; mutations = @($results); source_unchanged = $true } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'receipt.json') -Encoding utf8
  Write-Host "[STATE-MUTATIONS-PASS] $($results.Count)/$($mutants.Count) evidence=$OutputDirectory"
} finally {
  $root = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
  if (-not [IO.Path]::GetFullPath($temp).StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe mutation cleanup' }
  if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
