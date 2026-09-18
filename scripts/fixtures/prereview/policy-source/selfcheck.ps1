#requires -Version 7
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('policy-source-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot | Out-Null
try {
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Original committed source replay failed.' }
    foreach ($case in @('source-byte', 'source-byte-checklists', 'replacement-count', 'replayed-digest', 'candidate-body')) {
        $caseRoot = Join-Path $tempRoot $case
        New-Item -ItemType Directory -Path $caseRoot | Out-Null
        Get-ChildItem -LiteralPath $PSScriptRoot -File | Copy-Item -Destination $caseRoot
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'raw') -Destination $caseRoot -Recurse
        $rawRoot = Join-Path $caseRoot 'raw'
        $manifestPath = Join-Path $caseRoot 'manifest.json'
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        $arguments = @('-NoProfile', '-File', (Join-Path $caseRoot 'verify.ps1'))
        switch ($case) {
            'source-byte' { [IO.File]::AppendAllText((Join-Path $rawRoot $manifest.files[0].sourceFile), 'x', $utf8); $reason = 'Source digest mismatch' }
            'source-byte-checklists' { [IO.File]::AppendAllText((Join-Path $rawRoot $manifest.files[1].sourceFile), 'x', $utf8); $reason = "Source digest mismatch: $($manifest.files[1].path)" }
            'replacement-count' { $manifest.files[0].replacements[0].occurrences++; $reason = 'Replacement count mismatch' }
            'replayed-digest' { $manifest.files[0].replacements[0].new += 'x'; $reason = 'Adopted body digest mismatch' }
            'candidate-body' {
                foreach ($item in $manifest.files) {
                    $body = $utf8.GetString([IO.File]::ReadAllBytes((Join-Path $rawRoot $item.sourceFile)))
                    foreach ($replacement in $item.replacements) { $body = $body.Replace([string]$replacement.old, [string]$replacement.new) }
                    $path = Join-Path $caseRoot $item.path
                    New-Item -ItemType Directory -Path (Split-Path $path) -Force | Out-Null
                    [IO.File]::WriteAllText($path, $body + "`n<!-- remote-adoption-source-receipt -->`nfixture", $utf8)
                }
                & pwsh -NoProfile -File (Join-Path $caseRoot 'verify.ps1') -CandidateRoot $caseRoot
                if ($LASTEXITCODE -ne 0) { throw 'Unmodified candidate comparison failed.' }
                $path = Join-Path $caseRoot $manifest.files[0].path
                [IO.File]::WriteAllText($path, 'x' + [IO.File]::ReadAllText($path), $utf8)
                $arguments += @('-CandidateRoot', $caseRoot); $reason = 'Candidate body differs'
            }
        }
        $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8
        $output = (& pwsh @arguments *>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -or -not $output.Contains($reason)) { throw "Negative probe did not reach its named guard: $case" }
        Write-Output "[POLICY-SOURCE-NEGATIVE-PASS] $case"
    }
} finally {
    $resolved = [IO.Path]::GetFullPath($tempRoot)
    $parent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ((Split-Path $resolved) -ine $parent -or (Split-Path $resolved -Leaf) -notmatch '^policy-source-[a-f0-9]{32}$') { throw 'Unexpected temporary cleanup target.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
Write-Output '[POLICY-SOURCE-SELFCHECK-PASS] original replay, candidate comparison and five negative probes across four semantic classes'
