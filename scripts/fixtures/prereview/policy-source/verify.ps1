#requires -Version 7
# Historical policy source data: this verifier does not activate a protocol or worker.
[CmdletBinding()]
param([string]$CandidateRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$rawRoot = Join-Path $PSScriptRoot 'raw'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
if (@($manifest.files).Count -ne 2) { throw 'Expected exactly two approved source files.' }
foreach ($item in $manifest.files) {
    if ([IO.Path]::GetFileName($item.sourceFile) -cne $item.sourceFile) { throw 'Source file must be a leaf name.' }
    $source = [IO.File]::ReadAllBytes((Join-Path $rawRoot $item.sourceFile))
    $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($source)).ToLowerInvariant()
    if ($source.Length -ne $item.sourceBytes -or $sha -cne $item.sourceSha256) { throw "Source digest mismatch: $($item.path)" }
    $header = $utf8.GetBytes("blob $($source.Length)" + [char]0)
    [byte[]]$object = $header + $source
    $blob = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($object)).ToLowerInvariant()
    if ($blob -cne $item.sourceBlob) { throw "Source Git blob mismatch: $($item.path)" }
    $replayed = $utf8.GetString($source)
    foreach ($replacement in $item.replacements) {
        if (-not $replacement.old) { throw 'An empty replacement target is not permitted.' }
        $count = [regex]::Matches($replayed, [regex]::Escape($replacement.old)).Count
        if ($count -ne $replacement.occurrences) { throw "Replacement count mismatch: $($item.path)" }
        $replayed = $replayed.Replace([string]$replacement.old, [string]$replacement.new)
    }
    $body = $utf8.GetBytes($replayed)
    $bodySha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($body)).ToLowerInvariant()
    if ($body.Length -ne $item.adoptedBodyBytes -or $bodySha -cne $item.adoptedBodySha256) { throw "Adopted body digest mismatch: $($item.path)" }
    if ($CandidateRoot) {
        if ($item.path -notin @('docs/PREREVIEW-PROTOCOL.md', 'docs/PREREVIEW-CHECKLISTS.md')) { throw 'Unknown candidate path.' }
        $candidate = $utf8.GetString([IO.File]::ReadAllBytes((Join-Path $CandidateRoot $item.path)))
        $marker = "`n<!-- remote-adoption-source-receipt -->`n"
        $position = $candidate.IndexOf($marker, [StringComparison]::Ordinal)
        if ($position -lt 0 -or $candidate.LastIndexOf($marker, [StringComparison]::Ordinal) -ne $position) { throw 'Expected exactly one source receipt marker.' }
        $candidateBody = $utf8.GetBytes($candidate.Substring(0, $position))
        if (-not [Collections.StructuralComparisons]::StructuralEqualityComparer.Equals($candidateBody, $body)) { throw "Candidate body differs from disclosed source replay: $($item.path)" }
    }
    Write-Output "[POLICY-SOURCE-FILE-PASS] $($item.path) source=$sha adopted-body=$bodySha"
}
Write-Output '[POLICY-SOURCE-EVIDENCE-PASS] two approved sources and complete replay verified'
