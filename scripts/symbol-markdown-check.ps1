#requires -Version 7
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$helperPath = Join-Path $PSScriptRoot '_symbol-markdown.ps1'
$cases = [Collections.Generic.List[object]]::new()
function Add-Case($Name, $Operation, $Text, $Want, $ErrorPrefix = '') {
    $cases.Add([pscustomobject]@{ Name=$Name; Operation=$Operation; Text=$Text; Want=$Want; ErrorPrefix=$ErrorPrefix })
}
$f = ([string][char]96)*3
$label = 'powershell symbol-contract'
$block = $f+$label+"`nreturn 42`n"+$f
foreach ($character in @([string][char]96, '~')) {
    foreach ($length in @(3,4,6)) {
        $delimiter = $character*$length
        Add-Case "closed-$character-$length" block ($delimiter+$label+"`nreturn 42`n"+$delimiter) 'return 42'
    }
}
Add-Case 'crlf-content-offsets' block ($block.Replace("`n","`r`n")) 'return 42'
Add-Case 'longer-closing-fence' block ($block+([string][char]96)) 'return 42'
Add-Case 'indented-fence-retains-original-body' block ("  "+$f+$label+"`n  return 42`n  "+$f) '  return 42'
Add-Case 'code-comment-literal-not-markup' block ($f+$label+"`n# <!-- not markup`n"+$f) '# <!-- not markup'
Add-Case 'missing-block' block '# Heading' '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'duplicate-block' block ($block+"`n`n"+$block) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'label-is-case-sensitive' block ($block.Replace('symbol-contract','SYMBOL-CONTRACT')) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'label-not-substring' block ($block.Replace('symbol-contract','symbol-contract-extra')) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'commented-block' block ("<!--`n"+$block+"`n-->") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'outer-backtick-fence' block (([string][char]96)*5+"text`n"+$block+"`n"+([string][char]96)*5) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'outer-tilde-fence' block ("~~~~text`n"+$block+"`n~~~~") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'quoted-block' block (($block -split "`n" | ForEach-Object { '> '+$_ }) -join "`n") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'indented-code-block' block (($block -split "`n" | ForEach-Object { '    '+$_ }) -join "`n") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'list-nested-block' block ("- item`n`n"+(($block -split "`n" | ForEach-Object { '    '+$_ }) -join "`n")) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'closed-hidden-plus-real' block ("<!--`n"+$block+"`n-->`n`n"+$block) 'return 42'
foreach ($badFence in @($f,'~~~~')) {
    Add-Case "unclosed-$badFence" block ($badFence+$label+"`nreturn 42") '' 'SYMBOL-MARKDOWN-CLOSURE:'
    Add-Case "mismatched-$badFence" block ($badFence+$label+"`nreturn 42`n"+ $(if($badFence -eq $f){'~~~'}else{$f})) '' 'SYMBOL-MARKDOWN-CLOSURE:'
}
Add-Case 'short-closing-fence' block (([string][char]96)*4+$label+"`nreturn 42`n"+$f) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'closing-fence-with-text' block ($block+' trailing') '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'unclosed-comment-before-block' block ("<!--`n"+$block) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'unclosed-comment-after-block' block ($block+"`n`n<!--") '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'unclosed-inline-comment' visible 'before <!-- unfinished' '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'nested-unclosed-fence' visible ("> "+$f+"ps`n> unfinished") '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'plain-headings-and-table' visible "## Real`n`n| A | B |`n|---|---|`n| one | two |" '## Real|| A | B |||---|---||| one | two |'
Add-Case 'closed-comment-masked' visible "## Real`n`n<!--`n## Fake`n-->" '## Real'
Add-Case 'tilde-fenced-heading-masked' visible "## Real`n`n~~~~`n## Fake`n~~~~" '## Real'
Add-Case 'backtick-fenced-heading-masked' visible ("## Real`n`n"+$f+"`n## Fake`n"+$f) '## Real'
Add-Case 'quoted-heading-masked' visible "## Real`n`n> ## Fake" '## Real'
Add-Case 'list-heading-masked' visible "## Real`n`n- ## Fake" '## Real'
Add-Case 'indented-heading-masked' visible "## Real`n`n    ## Fake" '## Real'
Add-Case 'html-block-masked' visible "## Real`n`n<div>`n## Fake`n</div>" '## Real'
Add-Case 'inline-comment-masked' visible 'before <!-- hidden --> after' 'before                 after'
Add-Case 'inline-code-comment-literal-retained' visible ('before '+[char]96+'<!-- literal'+[char]96+' after') ('before '+[char]96+'<!-- literal'+[char]96+' after')
Add-Case 'inline-html-excludes-containing-block' visible "## Real`n`nbefore <span hidden>Fake</span> after" '## Real'
Add-Case 'closed-inline-comment-then-unclosed' visible 'before <!-- closed --> after <!-- open' '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'escaped-comment-opener-is-literal' visible 'before \<!-- literal' 'before \<!-- literal'

function Invoke-Suite([string]$Source) {
    $module = New-Module -ScriptBlock ([scriptblock]::Create($Source))
    $failures = [Collections.Generic.List[string]]::new()
    foreach ($case in $cases) {
        try {
            $result = & $module {
                param($c,$blockLabel)
                if ($c.Operation -eq 'block') { Get-SymbolMarkdownBlock -Text $c.Text -Label $blockLabel }
                else { Get-SymbolMarkdownVisible -Text $c.Text }
            } $case $label
            if ($case.ErrorPrefix) { $failures.Add($case.Name+': expected '+$case.ErrorPrefix); continue }
            if ($case.Operation -eq 'block') {
                if ($result.Value.TrimEnd("`r","`n") -cne $case.Want) { $failures.Add($case.Name+': wrong content') }
                if ($result.Index -lt 0 -or $result.Length -lt 0 -or $case.Text.Substring($result.Index,$result.Length) -cne $result.Value) {
                    $failures.Add($case.Name+': incorrect original content span')
                }
            } else {
                if ($result.Length -ne $case.Text.Length) { $failures.Add($case.Name+': projection length changed') }
                for ($i=0; $i -lt $case.Text.Length; $i++) {
                    if ($case.Text[$i] -in @([char]10,[char]13) -and $result[$i] -cne $case.Text[$i]) {
                        $failures.Add($case.Name+': newline offset changed'); break
                    }
                }
                $actual = (@($result -split '\r?\n' | ForEach-Object {$_.Trim()} | Where-Object {$_}) -join '|')
                if ($actual -cne $case.Want) { $failures.Add($case.Name+": expected '$($case.Want)', got '$actual'") }
            }
        } catch {
            if (-not $case.ErrorPrefix -or -not $_.Exception.Message.StartsWith($case.ErrorPrefix,[StringComparison]::Ordinal)) {
                $failures.Add($case.Name+': unexpected failure: '+$_.Exception.Message)
            }
        }
    }
    Remove-Module -ModuleInfo $module -ErrorAction SilentlyContinue
    return $failures.ToArray()
}
if (-not (Test-Path -LiteralPath $helperPath -PathType Leaf)) {
    Write-Host 'SYMBOL-MARKDOWN-TEST FAIL: production helper unavailable; visible-contract behavior is not implemented'
    exit 1
}
$source = Get-Content -LiteralPath $helperPath -Raw
$errors = @(Invoke-Suite $source)
if ($errors.Count) { $errors | ForEach-Object { Write-Host "SYMBOL-MARKDOWN-TEST FAIL: $_" }; exit 1 }
Write-Host "SYMBOL-MARKDOWN-TEST PASS: $($cases.Count) real behavior cases"
