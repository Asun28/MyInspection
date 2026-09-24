---
id: T4-SYMBOL-REMOTE-CONTRACT
title: Recover the approved symbol chrome V2 delivery contract with explicit scope
status: merged
depends_on: []
parallelizable_with: []
branch: T4-SYMBOL-REMOTE-CONTRACT
worktree: C:\wt\T4-SYMBOL-REMOTE-CONTRACT
allow_paths:
  - specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md
  - specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md
forbid:
  - Changing design documents, Kotlin, Gradle, schema, tokens, dependencies, shared workflows or shared gate implementations
  - Claiming local historical tests or human adjudication as current remote implementation or R3 approval
  - Weakening the original acceptance obligations, discarding earlier review history or bypassing a failed gate
non_goals:
  - Publishing the symbol design itself, UI implementation, archive closeout or unrelated local work
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: PR260 lacked matching scope and row checks; PR263 exposed file-wide location blindness, syntax-marker counting, then raw-regex discovery of runners, manifests and checkpoint/delta inside hidden Markdown. This recovery preserves the exact obligations and uses the merged shared parser at every affected consumer.
acceptance:
  - "A1 the entire change is confined to this scope card and T4-DESIGN-SYMBOL-CHROME-V2, whose status remains todo pending design publication"
  - "A2 the target card preserves approved OD-1/OD-2 decisions and every original acceptance check, adds 15 unique row checks for all identified repairs, and distinguishes historical tables from final substitution deltas"
  - "A3 PR260 and its two BLOCK outcomes remain traceable; the user-approved additional review is not represented as a fresh unlimited review allowance"
  - "A4 card schema, archive index, secret scan, complete-diff budget, formal independent review and candidate CI pass before remote merge"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-SYMBOL-REMOTE-CONTRACT; if($LASTEXITCODE){exit 1}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-DESIGN-SYMBOL-CHROME-V2; if($LASTEXITCODE){exit 1}; $ErrorActionPreference='Stop'; . ./scripts/_symbol-markdown.ps1; $c=Get-Content -LiteralPath 'specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md' -Raw; & ([scriptblock]::Create((Get-SymbolMarkdownBlock $c 'powershell symbol-metadata').Value)); pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; exit $LASTEXITCODE
dod_exit: 0
dod_assert: both cards validate; target remains todo with exact original25+repair15 tuples and all12 final-delta rows; 248 adversarial metadata cases and46 real-consumer visibility cases enforce specific rejection categories; archive projection is unchanged
review_gate: codex {verdict:pass}
hygiene: card/index checks are reused; embedded acceptance and adversarial checks stay inside these two cards, with no product code, dependency or shared testing framework changes
doc_sync: include this management card's post-merge status projection in its own PR and retain PR260/history links; the symbol design card stays todo until its separate implementation PR succeeds
---

# T4-SYMBOL-REMOTE-CONTRACT

## Approved recovery scope

On 2026-09-08 the user approved correcting the formal task scope, preserving review history, and **one additional formal R3 review** after the two reviews of [PR #260](https://github.com/Asun28/MyInspection/pull/260). This card and its matching branch implement that recovery. They do not reset the substantive history or authorize repeated reviews after another BLOCK.

- First review, `5d2481a98f60c7d9c6816db5bed1ed2a511011ea`: missing row-level acceptance assertions. Fixed in `d09414d034f6d700a31953c8923ed750a9a0558b` by retaining the 25 existing checks and adding 15 unique row anchors. All 40 checks were independently challenged in an isolated fixture; each failed by its named assertion; the pinned candidate and restored candidate passed.
- Second review, `d09414d034f6d700a31953c8923ed750a9a0558b`: no matching scope card for the ad-hoc branch. This formal card supplies the exact two-path scope and valid task identity. Neither old verdict is relabelled as PASS.

The original local design merge `53673571` and its eight historical R3 rounds remain local-delivery history. This recovery publishes only the approved decisions, strengthened acceptance command and evidence distinctions. It changes no design rule or product behavior. Run the normal remote ship pipeline with `-SkipRed` because this is a non-TDD metadata registration, not a recovery shortcut for an implementation card. The target design's real baseline RED and 40 source-text mutation checks are recorded in that card and must be revalidated on its separate publication candidate.

If the authorized additional R3 returns BLOCK, stop for the user; do not use another branch, counter reset, automatic retry or manual merge to obtain a further verdict. PR260 remains the preserved historical review record and will be cross-linked with the correctly named recovery PR.

On 2026-09-08 that additional review of [PR #263](https://github.com/Asun28/MyInspection/pull/263) returned PASS with empty reasons at `4b42c03da86adbd7224d8e9030ab102b203dc569`; candidate CI run `34188280896` also succeeded. The normal ship pipeline nevertheless stopped at `CI-GATE-BASE-MOVED` because master advanced from `94dfbe58ee4c70e13581ae3eab957ad7f1e7f87c` to `11cf5899be81bb1511cde31bc34bfdceea5b31eb`. This was not a merge or a new review BLOCK.

The user then explicitly authorized one further complete delivery check including R3 ("加评审授权"). Recovery incorporates the subsequent XML/Manifest closeout baseline `2b130c745fb8ee25b0158908d8b72b1954f698ea` without rewriting published history, preserves the prior verdict, and re-runs the normal ship pipeline in a coordinated stable merge window. This authorization does not permit repeated extra reviews, resetting counters or bypassing any failed gate; the scope and the target design card's todo status remain unchanged.

## Location-bound recovery after the subsequent BLOCK

The next review at `da28f192` returned BLOCK despite CI passing: file-wide phrase counts did not enforce placement, and the scope DoD counted syntax markers rather than validating obligations. The user explicitly approved repairing both findings and one further R3. No earlier verdict or counter is reset. The repair retains all 40 obligations, scopes positive ones to their Markdown locations, and checks exact structured tuples and the 12 final-delta rows. The literal digests below freeze these approved data values, not Markdown formatting; their first 25 and last 15 phrase/count pairs were checked against the pre-repair card.

Verification on 2026-09-08: the pinned old predicates missed all34 relocated clauses/rows plus duplicate-tuple and marker-string cases. The repaired real DoD passed the approved `5594330d` source fixtures, rejected all34 external relocations and all97 embedded deletion/reinsertion/relocation/wrong-cell/hidden-text challenges; the metadata DoD rejected248 malformed, duplicate or changed tuple/final-row cases. All original25+15 document/phrase/count values were compared exactly with `da28f192`. The unpublished remote design still fails by named `SYMBOL-CHECK` assertions. Fixture restoration retained SHA-256 `D637672CF820E1BFD407FA51940D62DE1E7D499975053D8B282C59DD45DCF82F` (DESIGN) and `F1364E4823FA7E3ED41CCC8E7F5C4D51FE27E4822EC1EEF6E60AF4CDBF497BA8` (Elements); no source document was edited.

## Outer-Markdown recovery and separate authorization

The next formal review at `af230c16cd39c88c601a4af983c1a0f777f52c5e` returned BLOCK: both actual frontmatter loaders, both manifest consumers and the checkpoint/delta projection could discover text hidden by outer comments or fences. The user subsequently authorized one counter reset and ONE further formal review for this PR263 repair. This later scoped grant does not rewrite the earlier no-reset authorizations or permit another review after a new BLOCK. At preparation on 2026-09-09 the counter remains2 and the reset/review grant is unused; execution waits for its separately coordinated window. This card's merged status is the post-merge projection under DEVOPS-WORKFLOW section1, not a claim that the new review or merge already happened.

Shared parser prerequisite [PR274](https://github.com/Asun28/MyInspection/pull/274) and its R5 [PR285](https://github.com/Asun28/MyInspection/pull/285) are merged. Independently authorized [PR289](https://github.com/Asun28/MyInspection/pull/289) preserved the missing local history remotely at `1747a4de20e7e62c7205d83feb2a456ee242976e`. Its separate one-review grant is completed, not reused here. Normal base integration retains that exact history once, including its local-only note; no unique record or test is removed to reduce this PR's diff.

The repair uses the existing shared Markdown helper in all affected consumers, including source-record visibility. It retains the exact40 tuples,12 rows,97 source challenges and248 metadata cases. The46 new consumer cases run both real frontmatter commands in disposable card fixtures, replace only the tested runner body with an execution sentinel, exercise both real manifest consumers, and test checkpoint and delta separately. Hidden/comment/outer-fence/unclosed/quoted contexts reject; valid long-backtick and tilde blocks remain accepted. Expected categories are literal, not computed by the parser under test. This is consumer integration, not a new shared parser or framework.

On the actual normal base merge `5e2bba441f7e70243be9ec5cc21fd0ad8f08f6cf`, before migration, those tests returned nonzero and demonstrated both hidden runners executing (`exit=0`, `executed=True`) plus manifest/checkpoint failures. Earlier malformed test-fixture diagnostics were excluded from RED evidence; the corrected real-consumer counterexample is preserved. Product design bytes remain unpublished and are not modified by these tests.

Current local verification on 2026-09-09 after migration: the actual scope DoD passed both schemas,248 original metadata challenges,46 new consumer cases and archive-index validation. The actual embedded source runner passed40 obligations and97 challenges against the preserved approved source fixtures; their before/after hashes equal the two source hashes recorded above. The actual frontmatter DoD against unpublished remote design remains named-assertion RED. Both runner bodies and the manifest match the previously verified candidate exactly; this record adds no claim of a new formal R3, full project verify, CI or remote merge. Those gates remain required for this repaired candidate.

### Executable metadata verification

This validator parses data rather than evaluating a tuple-looking substring. It rejects duplicate, missing or altered obligations and final rows, including marker text in strings. Its adversarial cases execute the same validator and require the specific tuple/delta failure category. The design card's own runner checks real design documents and exercises wrong-location and wrong-cell cases without modifying those files.

```powershell symbol-metadata
param([string]$CardPath='specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md')
$ErrorActionPreference='Stop'
. ./scripts/_symbol-markdown.ps1
function Get-MetadataBlock([string]$Text) {
    return Get-SymbolMarkdownBlock $Text 'json symbol-checks'
}
function Get-MetadataDigest($Value) {
    $json=ConvertTo-Json -InputObject $Value -Depth 6 -Compress
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($json)))
}
function Assert-SymbolMetadata([string]$Text) {
    $front=[regex]::Match($Text,'\A---\r?\n(.*?)\r?\n---','Singleline').Groups[1].Value
    if(@($front -split '\r?\n' | Where-Object {$_ -ceq 'status: todo'}).Count -ne 1){throw 'SYMBOL-METADATA-STATUS: target must remain todo'}
    try { $checks=ConvertFrom-Json -InputObject (Get-MetadataBlock $Text).Value -NoEnumerate }
    catch { throw 'SYMBOL-METADATA-TUPLES: invalid JSON block' }
    if($checks -isnot [array] -or $checks.Count -ne 40){throw 'SYMBOL-METADATA-TUPLES: expected original25+repair15'}
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($c in $checks){
        if($c -isnot [array] -or $c.Count -ne 5){throw 'SYMBOL-METADATA-TUPLES: tuple shape'}
        foreach($j in 0..3){if($c[$j] -isnot [string]){throw 'SYMBOL-METADATA-TUPLES: string fields'}}
        if($c[4] -isnot [long] -or $c[4] -notin @(0,1) -or -not $seen.Add($c[0]+[char]0+$c[3])){throw 'SYMBOL-METADATA-TUPLES: count or duplicate'}
    }
    # Literal digest pins the reviewed, ordered tuples including locations, not source formatting.
    if((Get-MetadataDigest $checks) -cne 'BE7B7E61DED7A641AD6AE063D41A46642AC1EEA96C9527ED9CE67F985D259A47'){throw 'SYMBOL-METADATA-TUPLES: changed obligation/location/order'}
    $visible=Get-SymbolMarkdownVisible $Text
    $checkpoint=[regex]::Matches($visible,'(?ms)^## Remote publication checkpoint \(2026-09-08\)\r?\n(.*?)(?=^## |\z)')
    if($checkpoint.Count -ne 1){throw 'SYMBOL-METADATA-DELTA: checkpoint missing/ambiguous'}
    $delta=[regex]::Matches($checkpoint[0].Groups[1].Value,'(?ms)^### Final substitution deltas and remote-contract repair\r?\n(.*?)(?=^#{1,3} |\z)')
    if($delta.Count -ne 1){throw 'SYMBOL-METADATA-DELTA: section missing/ambiguous'}
    $lines=@($delta[0].Groups[1].Value -split '\r?\n' | Where-Object { $_.StartsWith('| ') })
    if($lines.Count -ne 13 -or $lines[0] -cne '| Instance | Final non-colour visual / announcement requirements | Classification |'){throw 'SYMBOL-METADATA-DELTA: table shape'}
    $rows=@($lines[1..12] | ForEach-Object { ,@($_.Trim('|').Split('|') | ForEach-Object {$_.Trim()}) })
    $seen.Clear()
    foreach($row in $rows){
        if($row.Count -ne 3 -or -not $seen.Add($row[0])){throw 'SYMBOL-METADATA-DELTA: duplicate/malformed row'}
    }
    if((Get-MetadataDigest $rows) -cne '974010BBA08E7DC8C3F3F5274B1AB703ED0A4FE4D75F7272AEA7CB489737D8E1'){throw 'SYMBOL-METADATA-DELTA: changed final requirements/classification'}
}
function Assert-MetadataRejected([string]$Text,[string]$Prefix) {
    try { Assert-SymbolMetadata $Text }
    catch {
        if($_.Exception.Message.StartsWith($Prefix,[StringComparison]::Ordinal)){return}
        throw "SYMBOL-METADATA-TEST: unexpected failure: $($_.Exception.Message)"
    }
    throw 'SYMBOL-METADATA-TEST: mutation survived'
}
function Test-SymbolMetadata([string]$Text) {
    $block=(Get-MetadataBlock $Text)
    $json=$block.Value
    $total=0
    foreach($i in 0..39){
        foreach($kind in @('missing','duplicate','phrase','location','marker-string')){
            $copy=ConvertFrom-Json -InputObject $json -NoEnumerate
            switch($kind){
                missing { $copy=@(for($j=0;$j -lt 40;$j++){if($j -ne $i){,$copy[$j]}}) }
                duplicate { $copy[$i]=$copy[($i+1)%40] }
                phrase { $copy[$i][3]='changed obligation' }
                location { $copy[$i][1]='wrong section' }
                marker-string { $copy[$i]="@('D','marker text is not a tuple',1)" }
            }
            $replacement=ConvertTo-Json -InputObject $copy -Depth 6 -Compress
            $mutant=$Text.Substring(0,$block.Index)+$replacement+$Text.Substring($block.Index+$block.Length)
            Assert-MetadataRejected $mutant 'SYMBOL-METADATA-TUPLES:'
            $total++
        }
    }
    $delta=[regex]::Match($Text,'(?ms)^### Final substitution deltas and remote-contract repair\r?\n(.*?)(?=^#{1,3} |\z)').Groups[1].Value
    $rows=@($delta -split '\r?\n' | Where-Object { $_.StartsWith('| ') -and -not $_.StartsWith('| Instance ') })
    foreach($row in $rows){
        $cells=@($row.Trim('|').Split('|') | ForEach-Object {$_.Trim()})
        $cells[1]='changed final requirement'
        foreach($replacement in @('',($row+[char]10+$row),($row.Replace('requires-restatement','changed-classification')),('| '+($cells -join ' | ')+' |'))){
            Assert-MetadataRejected ($Text.Replace($row,$replacement)) 'SYMBOL-METADATA-DELTA:'
            $total++
        }
    }
    return $total
}
function Test-SymbolVisibilityConsumers([string]$ScopeText,[string]$TargetText) {
    $failures=[Collections.Generic.List[string]]::new(); $total=0
    $fixture=Join-Path (Get-Location) ('.secrets/symbol-consumers-'+[guid]::NewGuid())
    New-Item -ItemType Directory -Force "$fixture/specs/tasks" | Out-Null
    Copy-Item -LiteralPath scripts -Destination "$fixture/scripts" -Recurse
    Copy-Item -LiteralPath specs/archive -Destination "$fixture/specs/archive" -Recurse
    $scopePath='specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md'
    $targetPath='specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md'
    $tick=([string][char]96)*3
    function Segment([string]$Text,[string]$Label) {
        $m=[regex]::Matches($Text,'(?ms)^'+$tick+[regex]::Escape($Label)+'\r?\n(.*?)^'+$tick+'[ \t]*\r?$')
        if($m.Count -ne 1){throw 'SYMBOL-VISIBILITY-TEST: invalid canonical fixture'}
        return $m[0]
    }
    function Variants([string]$Value,[bool]$IsBlock) {
        $cases=@(
            @('comment',("<!--`n"+$Value+"`n-->"),$false),
            @('tilde-wrapper',("~~~~text`n"+$Value+"`n~~~~"),$false),
            @('backtick-wrapper',(($tick+'`')+"text`n"+$Value+"`n"+($tick+'`')),$false),
            @('unclosed-comment',("<!--`n"+$Value),$false),
            @('unclosed-fence',("~~~~text`n"+$Value),$false),
            @('quote',(($Value -split '\r?\n' | ForEach-Object {'> '+$_}) -join "`n"),$false)
        )
        if($IsBlock){
            $cases+=,@('long-fence',($Value.Replace($tick,$tick+'`')),$true)
            $cases+=,@('tilde-fence',($Value.Replace($tick,'~~~~')),$true)
        }
        return $cases
    }
    try {
        [IO.File]::WriteAllText("$fixture/$scopePath",$ScopeText)
        [IO.File]::WriteAllText("$fixture/$targetPath",$TargetText)
        foreach($entry in @(@('metadata',$ScopeText,$scopePath,'powershell symbol-metadata'),@('design',$TargetText,$targetPath,'powershell symbol-contract'))){
            $text=$entry[1]; $block=Segment $text $entry[3]
            $probe=$block.Value.Replace($block.Groups[1].Value,"'SYMBOL-RUNNER-EXECUTED'`n")
            $dod=($text -split '\r?\n' | Where-Object {$_.StartsWith('dod_command: ')}).Substring(13)
            foreach($case in (@(,@('visible',$probe,$true))+(Variants $probe $true))){
                [IO.File]::WriteAllText("$fixture/$($entry[2])",$text.Replace($block.Value,$case[1]))
                Push-Location $fixture
                try {
                    $child=& {
                        # Expected child failures are judged below; keep ship's outer fail-fast policy.
                        $PSNativeCommandUseErrorActionPreference=$false
                        $captured=(& pwsh -NoProfile -Command $dod 2>&1 | Out-String)
                        [pscustomobject]@{Output=$captured;Exit=$LASTEXITCODE}
                    }
                    $output=$child.Output; $exit=$child.Exit
                }
                finally {Pop-Location}
                $ran=@($output -split '\r?\n' | Where-Object {$_ -ceq 'SYMBOL-RUNNER-EXECUTED'}).Count -eq 1
                $valid=if($case[2]){$exit -eq 0 -and $ran}else{$exit -ne 0 -and -not $ran -and $output -cmatch 'SYMBOL-(MARKDOWN-|BLOCK:)'}
                if(-not $valid){$failures.Add("$($entry[0])-runner/$($case[0]) exit=$exit executed=$ran")}
                $total++
            }
            [IO.File]::WriteAllText("$fixture/$($entry[2])",$text)
        }
        $body=(Segment $TargetText 'powershell symbol-contract').Groups[1].Value
        $design=New-Module -ScriptBlock {
            param($Body,$Path)
            . ([scriptblock]::Create($Body)) -CardPath $Path -LoadOnly
        } -ArgumentList $body,"$fixture/$targetPath"
        $manifest=Segment $TargetText 'json symbol-checks'
        foreach($case in (Variants $manifest.Value $true)){
            $mutant=$TargetText.Replace($manifest.Value,$case[1])
            foreach($consumer in @('design','metadata')){
                $errorText=''
                try {
                    if($consumer -ceq 'design'){ $null=& $design {param($Text) Read-SymbolChecks $Text} $mutant }
                    else {Assert-SymbolMetadata $mutant}
                } catch {$errorText=$_.Exception.Message}
                $expected=if($consumer -ceq 'design'){'^SYMBOL-(MARKDOWN-|BLOCK:)'}else{'^SYMBOL-METADATA-TUPLES:'}
                if(($case[2] -and $errorText) -or (-not $case[2] -and $errorText -cnotmatch $expected)){
                    $failures.Add("$consumer-manifest/$($case[0]): $errorText")
                }
                $total++
            }
        }
        foreach($heading in @('## Remote publication checkpoint \(2026-09-08\)','### Final substitution deltas and remote-contract repair')){
            $section=[regex]::Match($TargetText,'(?ms)^'+$heading+'\r?\n.*?(?=^### Executable location-bound acceptance)')
            if(-not $section.Success){throw 'SYMBOL-VISIBILITY-TEST: section fixture missing'}
            foreach($case in (Variants $section.Value $false)){
                $mutant=$TargetText.Replace($section.Value,($case[1]+"`n"))
                # Closed-wrapper tests keep the manifest outside the targeted region.
                $expected=if($case[0].StartsWith('unclosed-')){'SYMBOL-METADATA-TUPLES:'}else{'SYMBOL-METADATA-DELTA:'}
                $errorText=''
                try {Assert-SymbolMetadata $mutant} catch {$errorText=$_.Exception.Message}
                if(-not $errorText.StartsWith($expected,[StringComparison]::Ordinal)){$failures.Add("$heading/$($case[0]): $errorText")}
                $total++
            }
        }
    } finally {
        $safe=[IO.Path]::GetFullPath($fixture)
        $parent=[IO.Path]::GetFullPath((Join-Path (Get-Location) '.secrets'))+[IO.Path]::DirectorySeparatorChar
        if(-not $safe.StartsWith($parent,[StringComparison]::OrdinalIgnoreCase)){throw 'SYMBOL-VISIBILITY-TEST: unsafe fixture cleanup'}
        Remove-Item -LiteralPath $safe -Recurse -Force
    }
    if($failures.Count){throw ('SYMBOL-VISIBILITY-TEST: '+($failures -join '; '))}
    return $total
}

$card=Get-Content -LiteralPath $CardPath -Raw
Assert-SymbolMetadata $card
$challenged=Test-SymbolMetadata $card
$visibility=Test-SymbolVisibilityConsumers (Get-Content -LiteralPath 'specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md' -Raw) $card
Write-Host "SYMBOL-METADATA PASS: exact25+15 tuples,12 final rows; $challenged malformed/duplicate/changed cases rejected; $visibility visibility consumer cases"
```

## Reconcile note (2026-09-24)

A1's "status remains todo" describes origin before the 2026-09 local/origin reconcile. The reconcile lands the local design merged as `53673571` and sets `T4-DESIGN-SYMBOL-CHROME-V2` to merged; that card's own reconcile note records how this contract's checks fare on the landed design. For the same reason this card's DoD now fails its SYMBOL-METADATA-STATUS check (target must remain todo) by design, since the target is merged.
